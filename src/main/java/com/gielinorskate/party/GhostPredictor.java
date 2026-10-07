package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BodyFlip;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.PoseSmoothing;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;

/**
 * One party member's ghost: the last authoritative state plus when it arrived, dead-reckoned to any later
 * time. Updates come at most a few times a second, so in between the ghost keeps moving along its velocity
 * (and falls under gravity in the air), and a new state is blended in over {@link #BLEND_TIME} instead of
 * jumping. Positions are absolute (world tile * 128 + sub-tile); the returned {@link RenderPose} uses them as
 * its x and y. Pure: no client dependency.
 */
public final class GhostPredictor
{
	/** Corrections fade out over this many seconds. */
	public static final float BLEND_TIME = 0.15f;
	/** A correction larger than 3 tiles is a teleport (or a lost update): snap instead of sliding there. */
	public static final float SNAP_DISTANCE = 3 * 128f;
	/** No message for this long: the member is gone (keep-alives come every 10 s). */
	public static final float EXPIRY = 15f;
	/**
	 * Dead reckoning stops after this long without an update. A moving skater sends a snapshot every 0.6 s
	 * game tick, so 1.2 s covers one lost or rate-limited snapshot without running off far.
	 */
	public static final float MAX_EXTRAPOLATION = 1.2f;
	/** The skater's gravity (skate-tuning.json "gravity", u/s^2). */
	public static final float GRAVITY = 2000f;
	/**
	 * An update with an old sequence number is stale, unless the member has been silent this long: then the
	 * sender has restarted and counts again from a lower number.
	 */
	public static final float RESYNC_SILENCE = 2f;

	/**
	 * u/s: an update on the board, rolling, this much faster than the one before it (also rolling on the board) shows
	 * a push. The wire has no push of its own; a push adds at least pushMinImpulse (60) to the speed.
	 */
	public static final float PUSH_GAIN = 30f;

	/** Below this turn rate (rad/s) the ghost moves straight: the arc's radius would be huge. */
	private static final float MIN_ARC_RATE = 1e-3f;

	/** Height (up-positive) of the receiver's ground at an absolute (x, y), or NaN when unknown. */
	public interface Ground
	{
		float heightAt(float x, float y);
	}

	private GhostState auth;
	private float authTime;
	/** The correction still being blended out: predicted-before minus authoritative at blendStart. */
	private float offX;
	private float offY;
	private float offH;
	private float offHeading;
	/** Body flip correction, without whole turns (which look the same). */
	private float offFlip;
	private float blendStart;
	private Trick labelTrick;
	private float labelTime;
	private float popTime = Float.NaN;
	private boolean popNollie;
	/** The member's board designs. */
	private BoardLook look = BoardLook.defaults(BoardDesigns.bundled());
	/** The Skate Duel version the member advertises; 0 when none (an older version, or duels off). */
	private int duelVersion;
	/** Pops, landings and bails accepted so far, so a renderer can tell new ones each frame. */
	private int popCount;
	private int landCount;
	private int bailCount;
	private int pushCount;
	/** When the current knockdown stage was first seen. */
	private float knockSince;
	/** Duel endings seen so far and when the latest of each was played (our clock; NaN before any). */
	private int tantrumCount;
	private float tantrumAt = Float.NaN;
	private int celebrateCount;
	private float celebrateAt = Float.NaN;

	/**
	 * Snapshot interpolation, once the member's updates carry a timeline (GhostTrajectory): the ghost is played back
	 * a delay behind instead of dead-reckoned ahead. Null for members on older versions (no timeline), whose ghosts
	 * are predicted exactly as before.
	 */
	private GhostTimeline timeline;
	/** When the latest update with a timeline arrived. */
	private float timedAt = Float.NEGATIVE_INFINITY;
	/** The knockdown stage of the update last played (timeline). */
	private KnockdownPose.Stage playedKnock;
	private final GhostTimeline.Sink sink = new GhostTimeline.Sink()
	{
		@Override
		public void event(int bits, Trick trick, boolean nollie, float at)
		{
			played(bits, trick, nollie, at);
		}

		@Override
		public void keyChanged(GhostState key, float at)
		{
			if (key.knockStage != null && key.knockStage != playedKnock)
			{
				knockSince = at;
			}
			playedKnock = key.knockStage;
		}
	};

	/**
	 * After the last update with a timeline, updates without one (their timeline did not fit) are placed on the
	 * timeline by their arrival for this long; after it the member is taken to be on an older version again.
	 */
	public static final float TIMELINE_LINGER = 3f;

	/** {@link #accept(GhostState, GhostTrajectory, float, Ground)} without a timeline (an older version's update). */
	public boolean accept(GhostState s, float now, Ground ground)
	{
		return accept(s, null, now, ground);
	}

	/**
	 * Takes in a new authoritative state received at {@code now}, with its timeline {@code tj} (null from older
	 * versions, or when it did not fit). Returns false when it is stale: not newer than the last one accepted
	 * (with a timeline, its positions still fill in the timeline: they say where the member was).
	 */
	public boolean accept(GhostState s, GhostTrajectory tj, float now, Ground ground)
	{
		boolean newer = auth == null || s.seq > auth.seq || now - authTime >= RESYNC_SILENCE;
		if (tj != null || timeline != null && now - timedAt < TIMELINE_LINGER)
		{
			if (tj != null || newer)
			{
				boolean switching = timeline == null && auth != null && auth.world == s.world && auth.plane == s.plane;
				RenderPose before = switching ? pose(now, ground) : null;
				if (timeline == null)
				{
					timeline = new GhostTimeline();
				}
				if (timeline.receive(s, tj, now, ground, sink))
				{
					if (tj != null)
					{
						timedAt = now;
					}
					if (before != null)
					{
						// from dead reckoning onto the timeline: what was drawn blends into it
						timeline.blendFrom(before.x, before.y, before.h, before.heading);
					}
					if (newer)
					{
						auth = s;
						authTime = now;
					}
					return newer;
				}
			}
			if (!newer)
			{
				return false;
			}
		}
		return acceptPredicted(s, now, ground);
	}

	/** The update {@code s} for dead reckoning (from older versions), as ghosts were always predicted. */
	private boolean acceptPredicted(GhostState s, float now, Ground ground)
	{
		if (auth != null && s.seq <= auth.seq && now - authTime < RESYNC_SILENCE)
		{
			return false;
		}
		if (auth != null && auth.world == s.world && auth.plane == s.plane)
		{
			RenderPose before = pose(now, ground);
			float flipBefore = bodyFlip(now);
			float dx = before.x - s.x;
			float dy = before.y - s.y;
			float dh = before.h - predictedHeight(s, 0f, s.x, s.y, ground);
			boolean snap = Math.hypot(dx, dy) > SNAP_DISTANCE || Math.abs(dh) > SNAP_DISTANCE;
			offX = snap ? 0f : dx;
			offY = snap ? 0f : dy;
			offH = snap ? 0f : dh;
			offHeading = snap ? 0f : Angles.wrap(before.heading - s.heading);
			// a whole turn apart looks the same: a landing (angle reset to 0) never spins the ghost back
			offFlip = BodyFlip.residual(flipBefore - (s.state == SkaterState.AIRBORNE ? s.bodyFlip : 0f));
		}
		else
		{
			offX = 0f;
			offY = 0f;
			offH = 0f;
			offHeading = 0f;
			offFlip = 0f;
		}
		// the member went back to an older version: dead reckoning from here (blending from what was drawn)
		timeline = null;
		GhostState before = auth;
		if (before != null && isRolling(before) && isRolling(s) && speedOf(s) - speedOf(before) > PUSH_GAIN)
		{
			pushCount++;
		}
		if (s.knockStage != null && (before == null || before.knockStage != s.knockStage))
		{
			knockSince = now;
		}
		blendStart = now;
		auth = s;
		authTime = now;
		if ((s.events & GhostCodec.EV_TRICK) != 0)
		{
			Trick named = s.trick != null ? s.trick : s.hold;
			if (named != null)
			{
				labelTrick = named;
				labelTime = now;
			}
		}
		if ((s.events & GhostCodec.EV_POP) != 0)
		{
			popTime = now;
			popNollie = PoseSmoothing.isNollie(s.trick);
			popCount++;
		}
		if ((s.events & GhostCodec.EV_LAND) != 0)
		{
			landCount++;
		}
		ending(s.events, now);
		if ((s.events & GhostCodec.EV_BAIL) != 0)
		{
			bailCount++;
		}
		return true;
	}

	/** Timeline: EV_* {@code bits} reached by the playback at our clock's {@code at}. */
	private void played(int bits, Trick trick, boolean nollie, float at)
	{
		if ((bits & GhostCodec.EV_TRICK) != 0 && trick != null)
		{
			labelTrick = trick;
			labelTime = at;
		}
		if ((bits & GhostCodec.EV_POP) != 0)
		{
			popTime = at;
			popNollie = nollie;
			popCount++;
		}
		if ((bits & GhostCodec.EV_LAND) != 0)
		{
			landCount++;
		}
		if ((bits & GhostCodec.EV_BAIL) != 0)
		{
			bailCount++;
		}
		if ((bits & GhostCodec.EV_PUSH) != 0)
		{
			pushCount++;
		}
		ending(bits, at);
	}

	/** A duel ending in EV_* {@code bits}, played at {@code at}. */
	private void ending(int bits, float at)
	{
		if ((bits & GhostCodec.EV_TANTRUM) != 0)
		{
			tantrumCount++;
			tantrumAt = at;
		}
		if ((bits & GhostCodec.EV_CELEBRATE) != 0)
		{
			celebrateCount++;
			celebrateAt = at;
		}
	}

	/** Timeline: on to {@code now} (once per now), with the ground last given. */
	private GhostTimeline played(float now)
	{
		GhostTimeline tl = timeline;
		if (tl != null)
		{
			tl.advance(now, tl.lastGround(), sink);
		}
		return tl;
	}

	/** The ghost is played back from its timeline (the member's version sends one), not dead-reckoned. */
	public boolean isTimed()
	{
		return timeline != null;
	}

	/** Timeline: the playback time (sender seconds) as of the last frame; NaN when dead reckoning. */
	public double playbackTime()
	{
		return timeline == null ? Double.NaN : timeline.playbackTime();
	}

	/** Timeline: the playback delay aimed at (seconds behind the sender's clock floor); 0 when dead reckoning. */
	public float playbackDelay()
	{
		return timeline == null ? 0f : timeline.delay();
	}

	/**
	 * For ::skateghosts: how the ghost is moved, e.g. "played back 0.78 s behind (jitter 0.14 s, updates every
	 * 0.60 s)", or "dead reckoning (an older version: no timeline)".
	 */
	public String playbackStatus()
	{
		GhostTimeline tl = timeline;
		if (tl == null)
		{
			return "dead reckoning (an older version: no timeline)";
		}
		GhostClockSync s = tl.sync();
		return String.format("played back %.2f s behind (jitter %.2f s, updates every %.2f s)%s", tl.delay(),
			s.jitter(), s.gap(), tl.underrun() ? ", waiting for updates" : "");
	}

	/** Timeline, for tests: the playback is past the last sample (dead reckoning or settling). */
	boolean playbackUnderrun()
	{
		return timeline != null && timeline.underrun();
	}

	/** Timeline, for tests: the playback clock's speed over the last frame. */
	float playbackRate()
	{
		return timeline == null ? 1f : timeline.rate();
	}

	/**
	 * The front / back flip angle of the whole skater at {@code now}, radians, + = frontflip; raw (whole turns
	 * kept), 0 on the ground apart from a correction still blending out. A held flip keeps turning at its rate;
	 * a released one eases onto the nearest whole turn at its rate, as SkatePhysics' assist does.
	 */
	public float bodyFlip(float now)
	{
		GhostTimeline tl = played(now);
		if (tl != null)
		{
			return tl.bodyFlip();
		}
		GhostState s = auth;
		if (s == null)
		{
			return 0f;
		}
		float blend = Math.max(0f, 1f - (now - blendStart) / BLEND_TIME);
		float a = 0f;
		if (s.state == SkaterState.AIRBORNE)
		{
			float dt = Math.max(0f, Math.min(MAX_EXTRAPOLATION, now - authTime));
			a = s.bodyFlip;
			float w = s.bodyFlipRate;
			if (Math.abs(w) >= BodyFlipMeter.HELD_RATE)
			{
				a += w * dt;
			}
			else if (w != 0f)
			{
				float r = BodyFlip.residual(a);
				a -= Math.copySign(Math.min(Math.abs(r), Math.abs(w) * dt), r);
			}
		}
		return a + offFlip * blend;
	}

	/**
	 * The heading turn rate (rad/s) at {@code now}: the sent one while dead reckoning lasts, 0 after it (or when
	 * bailed). A flip's own body turn is not included.
	 */
	public float turnRate(float now)
	{
		GhostTimeline tl = played(now);
		if (tl != null)
		{
			// the curve's own turn rate: smooth through the samples, so the carve lean is too
			return tl.state() == SkaterState.BAILED ? 0f : tl.turnRate();
		}
		GhostState s = auth;
		if (s == null || s.state == SkaterState.BAILED || now - authTime > MAX_EXTRAPOLATION)
		{
			return 0f;
		}
		return s.turnRate;
	}

	/** The member's board designs (from their updates; each part's default when unknown). */
	public BoardLook look()
	{
		return look;
	}

	/** The Skate Duel version the member takes challenges with (from their latest update); 0 for none. */
	public int duelVersion()
	{
		return duelVersion;
	}

	void setDuelVersion(int version)
	{
		duelVersion = Math.max(0, version);
	}

	void setLook(BoardLook look)
	{
		if (look != null)
		{
			this.look = look;
		}
	}

	/** The latest authoritative state, or null before the first. */
	public GhostState latest()
	{
		return auth;
	}

	/**
	 * The state being drawn: with a timeline the update the playback has reached (its board, knockdown, hold...),
	 * else the latest.
	 */
	public GhostState current()
	{
		GhostTimeline tl = timeline;
		GhostState c = tl == null ? null : tl.current();
		return c != null ? c : auth;
	}

	/** No message for {@link #EXPIRY} seconds. */
	public boolean expired(float now)
	{
		return auth == null || now - authTime > EXPIRY;
	}

	/** The ghost's pose at {@code now}; {@code ground} may be null (no ground clamp or following). */
	public RenderPose pose(float now, Ground ground)
	{
		GhostTimeline tl = timeline;
		if (tl != null)
		{
			return timedPose(tl, now, ground);
		}
		GhostState s = auth;
		float dt = Math.max(0f, Math.min(MAX_EXTRAPOLATION, now - authTime));
		float blend = Math.max(0f, 1f - (now - blendStart) / BLEND_TIME);
		boolean carving = s.state == SkaterState.ROLLING || s.state == SkaterState.MANUAL;
		float w = s.state == SkaterState.BAILED ? 0f : s.turnRate;
		float x = s.x + offX * blend;
		float y = s.y + offY * blend;
		if (carving && Math.abs(w) > MIN_ARC_RATE)
		{
			// carving: the velocity turns with the heading, so the ghost follows the arc, not its tangent
			double theta = Math.atan2(s.vx, s.vy);
			double r = Math.hypot(s.vx, s.vy) / w;
			x += (float) (r * (Math.cos(theta) - Math.cos(theta + w * dt)));
			y += (float) (r * (Math.sin(theta + w * dt) - Math.sin(theta)));
		}
		else
		{
			x += s.vx * dt;
			y += s.vy * dt;
		}
		float h = predictedHeight(s, dt, x, y, ground) + offH * blend;
		if (ground != null)
		{
			float g = ground.heightAt(x, y);
			if (!Float.isNaN(g) && s.state == SkaterState.AIRBORNE)
			{
				h = Math.max(h, g);
			}
		}
		float roll = 0f;
		float yaw = 0f;
		float spin = 0f;
		float pitch = SkatePhysics.boardPitchFor(s.state, s.hold);
		Trick t = s.trick;
		if (t != null && t.kind == TrickKind.FLIP && t.duration > 0f)
		{
			float u0 = s.flipTime / t.duration;
			float u = (s.flipTime + Math.max(0f, now - authTime)) / t.duration;
			float e = SkatePhysics.flipEase(u);
			roll = t.rollTurns * Angles.TWO_PI * e;
			// an impossible wraps the board end over end, as SkatePhysics.getBoardPitch
			pitch += t.pitchTurns * Angles.TWO_PI * e;
			// a bigspin's body turn since the update (the sent heading has the part before)
			spin = GhostCodec.bodySpin(t, u) - GhostCodec.bodySpin(t, u0);
			// relative to the body, which turns too: as SkatePhysics.getBoardYawOffset
			yaw = t.yawTurns * Angles.TWO_PI * e - GhostCodec.bodySpin(t, u);
		}
		float heading = Angles.wrap(s.heading + w * dt + spin + offHeading * blend);
		return new RenderPose(x, y, h, heading, heading, roll, yaw, pitch, s.state, s.hold);
	}

	/**
	 * Timeline: the pose at the playback time. Position, height and heading from the timeline's curves; the board
	 * flip from when it started on the member's clock (so it starts at the pop on the path), the hold and pitch from
	 * the update being played.
	 */
	private RenderPose timedPose(GhostTimeline tl, float now, Ground ground)
	{
		tl.advance(now, ground, sink);
		GhostState key = tl.current();
		SkaterState state = tl.state();
		Trick hold = key == null ? null : key.hold;
		float roll = 0f;
		float yaw = 0f;
		float pitch = SkatePhysics.boardPitchFor(state, hold);
		Trick t = tl.flipTrick();
		if (t != null)
		{
			float u = tl.flipTime() / t.duration;
			float e = SkatePhysics.flipEase(u);
			roll = t.rollTurns * Angles.TWO_PI * e;
			pitch += t.pitchTurns * Angles.TWO_PI * e;
			// relative to the body, whose bigspin turn is in the played-back heading already
			yaw = t.yawTurns * Angles.TWO_PI * e - GhostCodec.bodySpin(t, u);
		}
		float heading = tl.heading();
		return new RenderPose(tl.x(), tl.y(), tl.h(), heading, heading, roll, yaw, pitch, state, hold);
	}

	/**
	 * Height of {@code s} after {@code dt} at (x, y), before the correction: in the air a ballistic arc; on the
	 * ground it follows the receiver's ground, relative to where the state was sent (the two clients' grounds
	 * can differ by a constant, e.g. on a bridge); on a rail or bailed it keeps its vertical speed.
	 */
	private static float predictedHeight(GhostState s, float dt, float x, float y, Ground ground)
	{
		switch (s.state)
		{
			case AIRBORNE:
				return s.h + s.vh * dt - GRAVITY / 2f * dt * dt;
			case ROLLING:
			case MANUAL:
				if (ground != null)
				{
					float g0 = ground.heightAt(s.x, s.y);
					float g1 = ground.heightAt(x, y);
					if (!Float.isNaN(g0) && !Float.isNaN(g1))
					{
						return s.h + g1 - g0;
					}
				}
				return s.h;
			default:
				return s.h + s.vh * dt;
		}
	}

	/** The trick (or grab, manual, grind) of the latest TRICK event, or null; see {@link #labelAge}. */
	public Trick labelTrick()
	{
		return labelTrick;
	}

	/** Seconds since {@link #labelTrick} was set. */
	public float labelAge(float now)
	{
		return now - labelTime;
	}

	/** Seconds since the latest pop, or Float.MAX_VALUE when none was seen. */
	public float sincePop(float now)
	{
		return Float.isNaN(popTime) ? Float.MAX_VALUE : now - popTime;
	}

	/** Duel tantrums seen so far (a new one: the ghost throws it). */
	public int tantrumCount()
	{
		return tantrumCount;
	}

	/** Seconds since the latest tantrum was played (it may be a moment ago: a late update); NaN before any. */
	public float tantrumAge(float now)
	{
		return now - tantrumAt;
	}

	/** Duel celebrations seen so far. */
	public int celebrateCount()
	{
		return celebrateCount;
	}

	/** Seconds since the latest celebration was played; NaN before any. */
	public float celebrateAge(float now)
	{
		return now - celebrateAt;
	}

	/** Pops accepted so far. */
	public int popCount()
	{
		return popCount;
	}

	/** Landings accepted so far. */
	public int landCount()
	{
		return landCount;
	}

	/** Bails accepted so far. */
	public int bailCount()
	{
		return bailCount;
	}

	/** Pushes seen so far (speed gained between two rolling updates, {@link #PUSH_GAIN}). */
	public int pushCount()
	{
		return pushCount;
	}

	/** The pop charge (0..1) of the latest update; 0 when none. */
	public float charge()
	{
		GhostState s = current();
		return s == null ? 0f : s.charge;
	}

	/** Knocked off the board: the stage of the update drawn ({@link #current}); null otherwise. */
	public KnockdownPose.Stage knockStage()
	{
		GhostState s = current();
		return s == null ? null : s.knockStage;
	}

	/** Seconds since the current knockdown stage was first seen. */
	public float knockStageAge(float now)
	{
		return Math.max(0f, now - knockSince);
	}

	private static boolean isRolling(GhostState s)
	{
		return s.state == SkaterState.ROLLING && s.offBoard == null && s.knockStage == null;
	}

	private static float speedOf(GhostState s)
	{
		return (float) Math.hypot(s.vx, s.vy);
	}

	/** Ground speed of the latest state, u/s (unsigned: the wire has no fakie). */
	public float speed()
	{
		GhostTimeline tl = timeline;
		if (tl != null)
		{
			// as played back: the curve's own speed
			return (float) Math.hypot(tl.vx(), tl.vy());
		}
		GhostState s = auth;
		return s == null ? 0f : (float) Math.hypot(s.vx, s.vy);
	}

	/** Vertical speed (up > 0) at {@code now}: falling under gravity in the air, the sent one otherwise. */
	public float verticalSpeed(float now)
	{
		GhostTimeline tl = played(now);
		if (tl != null)
		{
			return tl.vh();
		}
		GhostState s = auth;
		if (s == null)
		{
			return 0f;
		}
		if (s.state != SkaterState.AIRBORNE)
		{
			return s.vh;
		}
		return s.vh - GRAVITY * Math.max(0f, Math.min(MAX_EXTRAPOLATION, now - authTime));
	}

	/** A board flip (roll, shove-it or impossible) is still turning at {@code now}. */
	public boolean flipping(float now)
	{
		GhostTimeline tl = played(now);
		if (tl != null)
		{
			return tl.flipTrick() != null;
		}
		GhostState s = auth;
		Trick t = s == null ? null : s.trick;
		return t != null && t.kind == TrickKind.FLIP && t.duration > 0f
			&& s.flipTime + Math.max(0f, now - authTime) < t.duration;
	}

	/** The latest pop was off the nose. */
	public boolean isPopNollie()
	{
		return popNollie;
	}
}
