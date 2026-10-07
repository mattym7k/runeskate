package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BodyFlip;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.PoseSmoothing;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;

/**
 * A party member's ghost played back on the member's own clock: every received position (the updates' own and the
 * positions batched into them, {@link GhostTrajectory}) is kept in time order, and the ghost is drawn at the
 * playback time, a delay behind the member's clock ({@link GhostClockSync#delay}), between the positions on either
 * side of it instead of guessing ahead of the last one:
 * <ul>
 * <li>position and heading along cubic Hermite curves through them with their velocities and turn rates (sent ones,
 * else from their neighbours), so neither position nor velocity jumps at a sample;</li>
 * <li>in the air, height along the gravity parabola through the two samples (split at the pop or landing when that
 * falls between them); on the ground, the height above the receiver's ground follows the samples and the ground
 * under the ghost is added back, so slopes, steps and stairs look right here;</li>
 * <li>events (pops, landings, tricks...) and flips happen when the playback time reaches when they happened.</li>
 * </ul>
 * The playback clock runs at the speed of ours, within a few percent: it catches up or falls back gently to the
 * target delay, slows down when it is about to run past the last sample, and only past the last sample does the ghost
 * dead-reckon (for {@link #MAX_EXTRAPOLATION}, then it settles to a stop). Whatever changes what is drawn at once (late or
 * reordered samples, a resumed stream) is blended out ({@link #BLEND_TAU}), never jumped, unless it is a teleport.
 * Pure; nothing is allocated per frame.
 */
final class GhostTimeline
{
	/** Events reach the predictor through this as the playback time passes them. */
	interface Sink
	{
		/** EV_* {@code bits} happened; {@code at} is our clock's time they were played at. */
		void event(int bits, Trick trick, boolean nollie, float at);

		/** The drawn update changed to {@code key}, which was played at {@code at} (our clock). */
		void keyChanged(GhostState key, float at);
	}

	/**
	 * Past the last sample, dead reckoning lasts this long (one lost update), then the ghost settles to a stop
	 * (SETTLE_TIME).
	 */
	static final float MAX_EXTRAPOLATION = 0.5f;
	/** Past MAX_EXTRAPOLATION, playback (and the ghost with it) slows to a stop within this much more member time. */
	static final float SETTLE_TIME = 0.3f;
	/** Seconds (time constant) a correction of what is drawn fades out over. */
	static final float BLEND_TAU = 0.12f;
	/** Played samples are kept this long (seconds) for the curves' tangents. */
	static final float HISTORY = 2f;
	/** Playback speeds toward the target delay by this much per second of error... */
	static final float GAIN = 0.5f;
	/**
	 * ...but never more than this much slower or faster than our clock while it is near the target (an error under
	 * MAX_RATE_CHANGE / GAIN = 0.12 s: the usual jitter), so no speed change shows...
	 */
	static final float MAX_RATE_CHANGE = 0.06f;
	/** ...and never more than this much faster catching up after the buffer ran dry (a slight fast-forward). */
	static final float MAX_CATCH_UP = 0.25f;
	/**
	 * About to run past the last sample of a moving ghost: playback slows to this, and once dead reckoning is over
	 * it slows to a stop with the ghost (SETTLE_TIME), so when updates come again it carries on from about where the
	 * ghost was drawn and catches up, instead of the ghost sliding there.
	 */
	static final float UNDERRUN_RATE = 0.9f;
	/** The playback speed changes by at most this much per second (outside a dry buffer's slowdown). */
	static final float RATE_SLEW = 0.6f;
	/** Further than this (seconds) from the target, the playback time is set to it (after a long silence). */
	static final float RESYNC = 3f;
	/** A ghost moving slower than this (u/s) on the ground is standing: it can be held without slowing playback. */
	static final float MOVING_SPEED = 20f;
	/** A sample closer than this (seconds) to one already kept is the same one. */
	static final double SAME_TIME = 0.008;
	/** Neighbours further apart than this (seconds) say nothing about a sample's velocity. */
	static final double MAX_TANGENT_SPAN = 1.0;
	/** A step faster than this (u/s) and longer than SNAP_DISTANCE between two samples is a teleport. */
	static final float TELEPORT_SPEED = 4000f;
	/** Events this late when received still play from their start, at most this far in. */
	static final float LATE_EVENT = 0.1f;

	private static final int CAP = 64;
	private static final int KEYS = 32;
	private static final int EVENTS = 48;
	private static final int FLIPS = 8;
	private static final int MARKS = 16;
	private static final SkaterState[] STATES = SkaterState.values();
	private static final int AIR = SkaterState.AIRBORNE.ordinal();
	private static final int ROLL = SkaterState.ROLLING.ordinal();
	private static final int MANUAL = SkaterState.MANUAL.ordinal();
	private static final int BAIL = SkaterState.BAILED.ordinal();

	// samples, in time order
	private final double[] t = new double[CAP];
	private final float[] x = new float[CAP];
	private final float[] y = new float[CAP];
	private final float[] h = new float[CAP];
	/** Unwrapped against the sample before. */
	private final float[] hd = new float[CAP];
	private final float[] vx = new float[CAP];
	private final float[] vy = new float[CAP];
	private final float[] vh = new float[CAP];
	private final float[] turn = new float[CAP];
	/** Height above the receiver's ground on the ground; NaN when unknown or not on the ground. */
	private final float[] clear = new float[CAP];
	/** The sample is an update's own: its velocity was sent. */
	private final boolean[] exact = new boolean[CAP];
	/** Its turn rate was sent and is the heading's (no bigspin turning the body as well). */
	private final boolean[] exactTurn = new boolean[CAP];
	private final byte[] st = new byte[CAP];
	private int n;
	private int seg;

	// updates (keys), in time order
	private final double[] kt = new double[KEYS];
	private final GhostState[] ks = new GhostState[KEYS];
	private int kn;
	private GhostState current;

	// events waiting to be played, in time order
	private final double[] et = new double[EVENTS];
	private final int[] eb = new int[EVENTS];
	private final Trick[] etr = new Trick[EVENTS];
	private final boolean[] enl = new boolean[EVENTS];
	private int en;

	// board flips by start time
	private final double[] fs = new double[FLIPS];
	private final Trick[] ft = new Trick[FLIPS];
	private int fn;

	// pops and landings (a ring), splitting the height curve between samples
	private final double[] mt = new double[MARKS];
	private final int[] mb = new int[MARKS];
	private int mNext;
	private int mCount;

	private final GhostClockSync sync = new GhostClockSync();
	private double tp;
	private boolean started;
	private float lastNow;
	private float rate = 1f;
	/** The last advance ran past the last sample, or was held back from it. */
	private boolean dry;
	private boolean evaluated;

	// corrections still blending out
	private float offX;
	private float offY;
	private float offH;
	private float offHd;
	/** ...and their velocities (the corrections are springs: drawn velocity never jumps either). */
	private float offVX;
	private float offVY;
	private float offVH;
	private float offVHd;

	// the raw curve at a time (scratch of evalRaw)
	private float rx;
	private float ry;
	private float rh;
	private float rhd;
	private float rvx;
	private float rvy;
	private float rvh;
	private float rturn;
	private int rstate;

	// what is drawn now
	private float ox;
	private float oy;
	private float oh;
	private float ohd;
	private float ovx;
	private float ovy;
	private float ovh;
	private float oturn;
	private int ostate;
	private GhostPredictor.Ground lastGround;

	/**
	 * Takes in update {@code s}, received at {@code now}, with its timeline {@code tj}, or none (an update whose
	 * timeline did not fit: placed by its arrival, as early as it can have been sent). Returns false, taking nothing
	 * in, for an update without a timeline before any with one.
	 */
	boolean receive(GhostState s, GhostTrajectory tj, float now, GhostPredictor.Ground ground, Sink sink)
	{
		if (tj == null && !sync.has())
		{
			return false;
		}
		advance(now, ground, sink);
		boolean had = n > 0 && evaluated;
		if (kn > 0 && (ks[kn - 1].world != s.world || ks[kn - 1].plane != s.plane))
		{
			// another world or plane: nothing to blend from
			clearBuffers();
			had = false;
		}
		double ts;
		if (tj != null)
		{
			ts = sync.unwrap(tj.timeMs);
			if (!sync.observe(now, ts))
			{
				// the sender started again: a new clock
				sync.clear();
				clearBuffers();
				started = false;
				ts = sync.unwrap(tj.timeMs);
				sync.observe(now, ts);
			}
		}
		else
		{
			ts = now - sync.floor();
			if (kn > 0)
			{
				// never before (nor at the same time as) the update before it
				ts = Math.max(ts, kt[kn - 1] + 2 * SAME_TIME);
			}
		}
		insertKey(ts, s);
		boolean bigspin = s.trick != null && s.trick.bodyYawTurns != 0f && s.trick.kind == TrickKind.FLIP;
		insertSample(ts, s.x, s.y, s.h, s.heading, s.vx, s.vy, s.vh, s.turnRate, true, !bigspin, s.state, ground);
		if (tj != null)
		{
			for (int i = 0; i < tj.count; i++)
			{
				insertSample(ts - tj.ago[i], tj.x[i], tj.y[i], tj.h[i], tj.heading[i], 0f, 0f, 0f, 0f, false, false,
					tj.state[i], ground);
			}
		}
		if (s.trick != null && s.trick.kind == TrickKind.FLIP && s.trick.duration > 0f)
		{
			insertFlip(ts - s.flipTime, s.trick);
		}
		for (int bit = 0; bit < GhostTrajectory.EVENT_BITS; bit++)
		{
			int b = 1 << bit;
			if ((s.events & b) == 0)
			{
				continue;
			}
			float ago = tj == null || Float.isNaN(tj.eventAgo[bit]) ? 0f : tj.eventAgo[bit];
			double at = ts - ago;
			Trick named = b == GhostCodec.EV_TRICK ? (s.trick != null ? s.trick : s.hold) : null;
			insertEvent(at, b, named, b == GhostCodec.EV_POP && PoseSmoothing.isNollie(s.trick));
			if (b == GhostCodec.EV_POP || b == GhostCodec.EV_LAND)
			{
				mt[mNext] = at;
				mb[mNext] = b;
				mNext = (mNext + 1) % MARKS;
				mCount = Math.min(MARKS, mCount + 1);
			}
		}
		if (!started)
		{
			started = true;
			tp = target(now);
			lastNow = now;
		}
		float px = ox;
		float py = oy;
		float ph = oh;
		float phd = ohd;
		float pvx = ovx;
		float pvy = ovy;
		float pturn = oturn + offVHd;
		evalRaw(tp, ground);
		if (had)
		{
			// what was drawn a moment ago stays drawn, moving as it was: the difference settles out
			rebase(px, py, ph, phd, pvx, pvy, pturn);
		}
		output();
		evaluated = true;
		return true;
	}

	/** Starts from what was drawn at ({@code x}, {@code y}, {@code h}, {@code heading}) (by dead reckoning). */
	void blendFrom(float px, float py, float ph, float pheading)
	{
		if (!evaluated)
		{
			return;
		}
		rebase(px, py, ph, pheading, rvx, rvy, rturn);
		output();
	}

	/**
	 * The corrections that keep drawing ({@code px}, {@code py}, {@code ph}, {@code phd}) moving at
	 * ({@code pvx}, {@code pvy}) and turning at {@code pturn} over the curve as now evaluated (r*), so neither the
	 * position nor the velocity jumps; a correction further than a teleport is dropped (the ghost snaps).
	 */
	private void rebase(float px, float py, float ph, float phd, float pvx, float pvy, float pturn)
	{
		offX = px - rx;
		offY = py - ry;
		offH = ph - rh;
		offHd = Angles.wrap(phd - rhd);
		offVX = pvx - rvx * rate;
		offVY = pvy - rvy * rate;
		// height eases in from rest: a landing's stop is real, and must not carry the ghost through the ground
		offVH = 0f;
		offVHd = pturn - rturn * rate;
		if (Math.hypot(offX, offY) > GhostPredictor.SNAP_DISTANCE || Math.abs(offH) > GhostPredictor.SNAP_DISTANCE)
		{
			clearOffsets();
		}
	}

	/**
	 * Moves the playback time on to our clock's {@code now} (once per {@code now}), plays the events it passes and
	 * works out what is drawn.
	 */
	void advance(float now, GhostPredictor.Ground ground, Sink sink)
	{
		if (n == 0 || !started || evaluated && now == lastNow)
		{
			return;
		}
		float dt = Math.max(0f, now - lastNow);
		lastNow = now;
		double target = target(now);
		if (Math.abs(target - tp) > RESYNC)
		{
			// a long silence or a new clock: on to the target, what was drawn blending into it
			float px = ox;
			float py = oy;
			float ph = oh;
			float phd = ohd;
			float pvx = ovx;
			float pvy = ovy;
			float pturn = oturn + offVHd;
			tp = target;
			evalRaw(tp, ground);
			rebase(px, py, ph, phd, pvx, pvy, pturn);
			rate = 1f;
			dry = false;
		}
		else
		{
			float err = (float) (target - tp);
			// near the target a few percent either way; well behind it (after a dry buffer) a slight fast-forward
			float fast = Math.max(MAX_RATE_CHANGE, Math.min(MAX_CATCH_UP, (err - MAX_RATE_CHANGE / GAIN) * GAIN));
			float desired = 1f + Math.max(-MAX_RATE_CHANGE, Math.min(fast, err * GAIN));
			// the speed itself changes gently (a new delay target is a step), so the ghost's velocity never jumps
			float step = RATE_SLEW * dt;
			rate += Math.max(-step, Math.min(step, desired - rate));
			float cap = dryRate();
			dry = cap < Float.MAX_VALUE;
			rate = Math.min(rate, cap);
			tp += rate * dt;
		}
		settle(dt);
		playEvents(now, sink);
		GhostState key = keyAt(tp);
		if (key != null && key != current)
		{
			current = key;
			int k = keyIndex(tp);
			sink.keyChanged(key, now - (float) Math.min(LATE_EVENT, Math.max(0.0, tp - kt[Math.max(0, k)])));
		}
		evalRaw(tp, ground);
		output();
		evaluated = true;
		prune();
	}

	/**
	 * The fastest playback may run past the last sample of a moving ghost: easing from our clock's speed down to
	 * UNDERRUN_RATE over the dead reckoning, then to a stop over SETTLE_TIME (so the ghost glides to a halt where its
	 * dead reckoning took it, and carries on from there when updates come again); unlimited otherwise.
	 */
	private float dryRate()
	{
		if (n == 0 || !moving(n - 1))
		{
			return Float.MAX_VALUE;
		}
		float past = (float) (tp - t[n - 1]);
		if (past < 0f)
		{
			return Float.MAX_VALUE;
		}
		if (past < MAX_EXTRAPOLATION)
		{
			return 1f - (1f - UNDERRUN_RATE) * past / MAX_EXTRAPOLATION;
		}
		return UNDERRUN_RATE * Math.max(0f, 1f - (past - MAX_EXTRAPOLATION) / SETTLE_TIME);
	}

	private double target(float now)
	{
		return now - sync.floor() - sync.delay();
	}

	/**
	 * {@code dt} seconds of the corrections settling: each a critically damped spring ({@link #BLEND_TAU}), solved
	 * exactly, so a correction starts at the velocity it was given and comes to rest without overshoot.
	 */
	private void settle(float dt)
	{
		float w = 1f / BLEND_TAU;
		float e = (float) Math.exp(-w * dt);
		float a = offVX + w * offX;
		offX = (offX + a * dt) * e;
		offVX = (offVX - w * a * dt) * e;
		a = offVY + w * offY;
		offY = (offY + a * dt) * e;
		offVY = (offVY - w * a * dt) * e;
		a = offVH + w * offH;
		offH = (offH + a * dt) * e;
		offVH = (offVH - w * a * dt) * e;
		a = offVHd + w * offHd;
		offHd = (offHd + a * dt) * e;
		offVHd = (offVHd - w * a * dt) * e;
	}

	private void output()
	{
		ox = rx + offX;
		oy = ry + offY;
		oh = rh + offH;
		ohd = Angles.wrap(rhd + offHd);
		// as drawn on our clock: the curve's velocity at the playback speed, plus the corrections'
		ovx = rvx * rate + offVX;
		ovy = rvy * rate + offVY;
		ovh = rvh * rate + offVH;
		oturn = rturn * rate;
		ostate = rstate;
	}

	private void clearOffsets()
	{
		offX = 0f;
		offY = 0f;
		offH = 0f;
		offHd = 0f;
		offVX = 0f;
		offVY = 0f;
		offVH = 0f;
		offVHd = 0f;
	}

	private void clearBuffers()
	{
		n = 0;
		kn = 0;
		en = 0;
		fn = 0;
		mCount = 0;
		seg = 0;
		current = null;
		evaluated = false;
		clearOffsets();
	}

	private boolean moving(int i)
	{
		return st[i] == AIR || exact[i] && Math.hypot(vx[i], vy[i]) > MOVING_SPEED
			|| !exact[i] && Math.hypot(tangent(x, i), tangent(y, i)) > MOVING_SPEED;
	}

	private void playEvents(float now, Sink sink)
	{
		int played = 0;
		while (played < en && et[played] <= tp)
		{
			float late = (float) Math.min(LATE_EVENT, tp - et[played]);
			sink.event(eb[played], etr[played], enl[played], now - late);
			played++;
		}
		if (played > 0)
		{
			System.arraycopy(et, played, et, 0, en - played);
			System.arraycopy(eb, played, eb, 0, en - played);
			System.arraycopy(etr, played, etr, 0, en - played);
			System.arraycopy(enl, played, enl, 0, en - played);
			for (int i = en - played; i < en; i++)
			{
				etr[i] = null;
			}
			en -= played;
		}
	}

	private void prune()
	{
		double old = tp - HISTORY;
		int drop = 0;
		while (n - drop > 2 && t[drop + 1] < old)
		{
			drop++;
		}
		if (drop > 0)
		{
			shiftSamples(drop);
		}
		if (n > 0 && Math.abs(hd[0]) > 32 * Angles.TWO_PI)
		{
			// a long carve's unwrapped headings back near zero (whole turns), before floats lose their precision
			float turns = Math.round(hd[0] / Angles.TWO_PI) * Angles.TWO_PI;
			for (int k = 0; k < n; k++)
			{
				hd[k] -= turns;
			}
			rhd -= turns;
		}
		int kd = 0;
		while (kn - kd > 1 && kt[kd + 1] < old)
		{
			kd++;
		}
		if (kd > 0)
		{
			System.arraycopy(kt, kd, kt, 0, kn - kd);
			System.arraycopy(ks, kd, ks, 0, kn - kd);
			for (int i = kn - kd; i < kn; i++)
			{
				ks[i] = null;
			}
			kn -= kd;
		}
	}

	private void shiftSamples(int drop)
	{
		int m = n - drop;
		System.arraycopy(t, drop, t, 0, m);
		System.arraycopy(x, drop, x, 0, m);
		System.arraycopy(y, drop, y, 0, m);
		System.arraycopy(h, drop, h, 0, m);
		System.arraycopy(hd, drop, hd, 0, m);
		System.arraycopy(vx, drop, vx, 0, m);
		System.arraycopy(vy, drop, vy, 0, m);
		System.arraycopy(vh, drop, vh, 0, m);
		System.arraycopy(turn, drop, turn, 0, m);
		System.arraycopy(clear, drop, clear, 0, m);
		System.arraycopy(exact, drop, exact, 0, m);
		System.arraycopy(exactTurn, drop, exactTurn, 0, m);
		System.arraycopy(st, drop, st, 0, m);
		n = m;
		seg = 0;
	}

	private void insertSample(double time, float px, float py, float ph, float heading, float pvx, float pvy,
		float pvh, float pturn, boolean isExact, boolean isExactTurn, SkaterState state, GhostPredictor.Ground ground)
	{
		int j = n;
		while (j > 0 && t[j - 1] > time)
		{
			j--;
		}
		if (j > 0 && time - t[j - 1] < SAME_TIME)
		{
			if (isExact && !exact[j - 1])
			{
				// the update's own sample is better than a batched one at the same time
				j--;
				setSample(j, time, px, py, ph, heading, pvx, pvy, pvh, pturn, true, isExactTurn, state, ground);
				rewrap(j + 1);
			}
			return;
		}
		if (j < n && t[j] - time < SAME_TIME)
		{
			if (isExact && !exact[j])
			{
				setSample(j, time, px, py, ph, heading, pvx, pvy, pvh, pturn, true, isExactTurn, state, ground);
				rewrap(j + 1);
			}
			return;
		}
		if (n == CAP)
		{
			if (j == 0)
			{
				return;
			}
			shiftSamples(1);
			j--;
		}
		int move = n - j;
		if (move > 0)
		{
			System.arraycopy(t, j, t, j + 1, move);
			System.arraycopy(x, j, x, j + 1, move);
			System.arraycopy(y, j, y, j + 1, move);
			System.arraycopy(h, j, h, j + 1, move);
			System.arraycopy(hd, j, hd, j + 1, move);
			System.arraycopy(vx, j, vx, j + 1, move);
			System.arraycopy(vy, j, vy, j + 1, move);
			System.arraycopy(vh, j, vh, j + 1, move);
			System.arraycopy(turn, j, turn, j + 1, move);
			System.arraycopy(clear, j, clear, j + 1, move);
			System.arraycopy(exact, j, exact, j + 1, move);
			System.arraycopy(exactTurn, j, exactTurn, j + 1, move);
			System.arraycopy(st, j, st, j + 1, move);
		}
		n++;
		seg = 0;
		setSample(j, time, px, py, ph, heading, pvx, pvy, pvh, pturn, isExact, isExactTurn, state, ground);
		rewrap(j + 1);
	}

	/** The headings from sample {@code from} on, unwrapped again: each the short way from the one before. */
	private void rewrap(int from)
	{
		for (int k = Math.max(1, from); k < n; k++)
		{
			hd[k] = hd[k - 1] + Angles.wrap(hd[k] - hd[k - 1]);
		}
	}

	private void setSample(int j, double time, float px, float py, float ph, float heading, float pvx, float pvy,
		float pvh, float pturn, boolean isExact, boolean isExactTurn, SkaterState state, GhostPredictor.Ground ground)
	{
		t[j] = time;
		x[j] = px;
		y[j] = py;
		h[j] = ph;
		float ref = j > 0 ? hd[j - 1] : j + 1 < n ? hd[j + 1] : heading;
		hd[j] = ref + Angles.wrap(heading - ref);
		vx[j] = pvx;
		vy[j] = pvy;
		vh[j] = pvh;
		turn[j] = pturn;
		exact[j] = isExact;
		exactTurn[j] = isExactTurn;
		st[j] = (byte) state.ordinal();
		float g = Float.NaN;
		if (ground != null && (st[j] == ROLL || st[j] == MANUAL))
		{
			g = ground.heightAt(px, py);
		}
		clear[j] = Float.isNaN(g) ? Float.NaN : ph - g;
	}

	private void insertKey(double time, GhostState s)
	{
		int j = kn;
		while (j > 0 && kt[j - 1] > time)
		{
			j--;
		}
		if (j > 0 && time - kt[j - 1] < SAME_TIME)
		{
			return;
		}
		if (kn == KEYS)
		{
			if (j == 0)
			{
				return;
			}
			System.arraycopy(kt, 1, kt, 0, kn - 1);
			System.arraycopy(ks, 1, ks, 0, kn - 1);
			kn--;
			j--;
		}
		System.arraycopy(kt, j, kt, j + 1, kn - j);
		System.arraycopy(ks, j, ks, j + 1, kn - j);
		kt[j] = time;
		ks[j] = s;
		kn++;
	}

	private void insertEvent(double time, int bits, Trick trick, boolean nollie)
	{
		if (en == EVENTS)
		{
			return;
		}
		int j = en;
		while (j > 0 && et[j - 1] > time)
		{
			j--;
		}
		System.arraycopy(et, j, et, j + 1, en - j);
		System.arraycopy(eb, j, eb, j + 1, en - j);
		System.arraycopy(etr, j, etr, j + 1, en - j);
		System.arraycopy(enl, j, enl, j + 1, en - j);
		et[j] = time;
		eb[j] = bits;
		etr[j] = trick;
		enl[j] = nollie;
		en++;
	}

	private void insertFlip(double start, Trick trick)
	{
		for (int i = 0; i < fn; i++)
		{
			if (ft[i] == trick && Math.abs(fs[i] - start) < 0.05)
			{
				return;
			}
		}
		if (fn == FLIPS)
		{
			// the oldest goes
			int oldest = 0;
			for (int i = 1; i < fn; i++)
			{
				if (fs[i] < fs[oldest])
				{
					oldest = i;
				}
			}
			fs[oldest] = start;
			ft[oldest] = trick;
			return;
		}
		fs[fn] = start;
		ft[fn] = trick;
		fn++;
	}

	private int keyIndex(double time)
	{
		int k = kn - 1;
		while (k > 0 && kt[k] > time)
		{
			k--;
		}
		return k;
	}

	private GhostState keyAt(double time)
	{
		return kn == 0 ? null : ks[keyIndex(time)];
	}

	/** The velocity-like slope of {@code a} at sample {@code i} from its neighbours (exact for a parabola). */
	private float tangent(float[] a, int i)
	{
		boolean left = i > 0 && t[i] - t[i - 1] <= MAX_TANGENT_SPAN && !teleport(i - 1);
		boolean right = i + 1 < n && t[i + 1] - t[i] <= MAX_TANGENT_SPAN && !teleport(i);
		if (left && right)
		{
			double h0 = t[i] - t[i - 1];
			double h1 = t[i + 1] - t[i];
			double s0 = (a[i] - a[i - 1]) / h0;
			double s1 = (a[i + 1] - a[i]) / h1;
			return (float) ((h1 * s0 + h0 * s1) / (h0 + h1));
		}
		if (left)
		{
			return (float) ((a[i] - a[i - 1]) / (t[i] - t[i - 1]));
		}
		if (right)
		{
			return (float) ((a[i + 1] - a[i]) / (t[i + 1] - t[i]));
		}
		return 0f;
	}

	/** Samples i and i + 1 are too far apart for the time between them. */
	private boolean teleport(int i)
	{
		double d = Math.hypot(x[i + 1] - x[i], y[i + 1] - y[i]);
		return d > GhostPredictor.SNAP_DISTANCE && d > TELEPORT_SPEED * (t[i + 1] - t[i]);
	}

	private float velX(int i)
	{
		return exact[i] ? vx[i] : tangent(x, i);
	}

	private float velY(int i)
	{
		return exact[i] ? vy[i] : tangent(y, i);
	}

	private float turnAt(int i)
	{
		if (st[i] == BAIL)
		{
			return 0f;
		}
		return exactTurn[i] ? turn[i] : tangent(hd, i);
	}

	/** The segment [i, i + 1] holding {@code time} (t[0] <= time < t[n - 1]). */
	private int segment(double time)
	{
		int i = Math.max(0, Math.min(seg, n - 2));
		if (t[i] > time)
		{
			i = 0;
		}
		while (i + 1 < n - 1 && t[i + 1] <= time)
		{
			i++;
		}
		seg = i;
		return i;
	}

	/** The curve at {@code time} into the r* scratch. */
	private void evalRaw(double time, GhostPredictor.Ground ground)
	{
		lastGround = ground;
		if (n == 0)
		{
			return;
		}
		if (time <= t[0])
		{
			hold(0);
			return;
		}
		if (time >= t[n - 1])
		{
			extrapolate(n - 1, (float) (time - t[n - 1]), ground);
			return;
		}
		int i = segment(time);
		int j = i + 1;
		if (teleport(i))
		{
			hold(i);
			return;
		}
		double span = t[j] - t[i];
		float dt = (float) span;
		float u = (float) ((time - t[i]) / span);
		float u2 = u * u;
		float u3 = u2 * u;
		// Hermite basis (the h00 and h01 terms sum to 1, d00 and d01 to 0: used relative to sample i below) and its
		// derivative per unit u
		float h10 = u3 - 2 * u2 + u;
		float h01 = -2 * u3 + 3 * u2;
		float h11 = u3 - u2;
		float d10 = 3 * u2 - 4 * u + 1;
		float d01 = -6 * u2 + 6 * u;
		float d11 = 3 * u2 - 2 * u;
		float mx0 = velX(i) * dt;
		float mx1 = velX(j) * dt;
		float my0 = velY(i) * dt;
		float my1 = velY(j) * dt;
		// relative to sample i (h00 + h01 = 1): world coordinates are large, their float sums would round
		float dx = x[j] - x[i];
		float dy = y[j] - y[i];
		rx = x[i] + (h10 * mx0 + h01 * dx + h11 * mx1);
		ry = y[i] + (h10 * my0 + h01 * dy + h11 * my1);
		rvx = (d10 * mx0 + d01 * dx + d11 * mx1) / dt;
		rvy = (d10 * my0 + d01 * dy + d11 * my1) / dt;
		float mh0 = turnAt(i) * dt;
		float mh1 = turnAt(j) * dt;
		float dhd = hd[j] - hd[i];
		rhd = hd[i] + (h10 * mh0 + h01 * dhd + h11 * mh1);
		rturn = st[i] == BAIL ? 0f
			: Math.max(-TurnRateMeter.MAX_RATE, Math.min(TurnRateMeter.MAX_RATE,
			(d10 * mh0 + d01 * dhd + d11 * mh1) / dt));
		rstate = st[i];
		height(i, j, time, u, h10, h01, h11, d10, d01, d11, ground);
	}

	/** The height (rh, rvh) between samples i and j at {@code time}. */
	private void height(int i, int j, double time, float u, float h10, float h01, float h11,
		float d10, float d01, float d11, GhostPredictor.Ground ground)
	{
		boolean airI = st[i] == AIR;
		boolean airJ = st[j] == AIR;
		if (airI && airJ)
		{
			parabola(t[i], h[i], t[j], h[j], time);
		}
		else if (airI)
		{
			// landing between the two: the arc ends at the landing, on the ground there, then the ground
			double land = landTime(i, j);
			if (time < land)
			{
				parabola(t[i], h[i], land, groundHeightAt(i, j, j, land, ground), time);
			}
			else
			{
				onGround(j, ground);
			}
		}
		else if (airJ)
		{
			// a pop between the two: the ground until it, then the arc from the ground there
			double pop = popTime(i, j);
			if (time >= pop)
			{
				parabola(pop, groundHeightAt(i, j, i, pop, ground), t[j], h[j], time);
			}
			else
			{
				onGround(i, ground);
			}
		}
		else
		{
			float g = Float.NaN;
			if (ground != null && !Float.isNaN(clear[i]) && !Float.isNaN(clear[j]))
			{
				g = ground.heightAt(rx, ry);
			}
			if (!Float.isNaN(g))
			{
				// above the ground here, as far above it as the samples were above theirs
				rh = g + clear[i] + (clear[j] - clear[i]) * u;
				rvh = (float) ((h[j] - h[i]) / (t[j] - t[i]));
			}
			else
			{
				float dt = (float) (t[j] - t[i]);
				float m0 = heightSlope(i) * dt;
				float m1 = heightSlope(j) * dt;
				rh = h[i] + (h10 * m0 + h01 * (h[j] - h[i]) + h11 * m1);
				rvh = (d10 * m0 + d01 * (h[j] - h[i]) + d11 * m1) / dt;
			}
			return;
		}
		if (rstate == AIR && ground != null)
		{
			float g = ground.heightAt(rx, ry);
			if (!Float.isNaN(g) && rh < g)
			{
				rh = g;
			}
		}
	}

	private float heightSlope(int i)
	{
		return exact[i] && st[i] == AIR ? vh[i] : tangent(h, i);
	}

	/** Sample {@code i}'s height on the ground at (rx, ry): above the ground here as it was there, else as sent. */
	private void onGround(int i, GhostPredictor.Ground ground)
	{
		rh = h[i];
		rvh = 0f;
		rstate = st[i];
		if (ground != null && !Float.isNaN(clear[i]))
		{
			float g = ground.heightAt(rx, ry);
			if (!Float.isNaN(g))
			{
				rh = g + clear[i];
			}
		}
	}

	/** The latest pop or landing ({@code bit}) in [from, to], or NaN. */
	private double mark(int bit, double from, double to)
	{
		double best = Double.NaN;
		for (int k = 0; k < mCount; k++)
		{
			if (mb[k] == bit && mt[k] >= from && mt[k] <= to && (Double.isNaN(best) || mt[k] > best))
			{
				best = mt[k];
			}
		}
		return best;
	}

	/**
	 * When the ghost landed between air sample {@code i} and ground sample {@code j}: the landing's own time, else
	 * where the arc leaving {@code i} comes down to {@code j}'s height (its update with the landing was lost).
	 */
	private double landTime(int i, int j)
	{
		double m = mark(GhostCodec.EV_LAND, t[i], t[j]);
		if (!Double.isNaN(m))
		{
			return m;
		}
		float v = Float.NaN;
		if (exact[i])
		{
			v = vh[i];
		}
		else if (i > 0 && st[i - 1] == AIR)
		{
			float span = (float) (t[i] - t[i - 1]);
			v = (h[i] - h[i - 1]) / span - GhostPredictor.GRAVITY / 2f * span;
		}
		double g = GhostPredictor.GRAVITY;
		double disc = (double) v * v + 2 * g * (h[i] - h[j]);
		if (Float.isNaN(v) || disc < 0)
		{
			return t[j];
		}
		return Math.max(t[i], Math.min(t[j], t[i] + (v + Math.sqrt(disc)) / g));
	}

	/**
	 * When the ghost left the ground between ground sample {@code i} and air sample {@code j}: the pop's own time,
	 * else where the arc reaching {@code j} rose from {@code i}'s height (a roll off a ledge, or a lost update).
	 */
	private double popTime(int i, int j)
	{
		double m = mark(GhostCodec.EV_POP, t[i], t[j]);
		if (!Double.isNaN(m))
		{
			return m;
		}
		float v = Float.NaN;
		if (exact[j])
		{
			v = vh[j];
		}
		else if (j + 1 < n && st[j + 1] == AIR)
		{
			float span = (float) (t[j + 1] - t[j]);
			v = (h[j + 1] - h[j]) / span + GhostPredictor.GRAVITY / 2f * span;
		}
		double g = GhostPredictor.GRAVITY;
		double disc = (double) v * v - 2 * g * (h[i] - h[j]);
		if (Float.isNaN(v) || disc < 0)
		{
			return t[i];
		}
		return Math.max(t[i], Math.min(t[j], t[j] - (-v + Math.sqrt(disc)) / g));
	}

	/**
	 * The ground height (plus ground sample {@code k}'s clearance) under the ghost at {@code time} between samples
	 * {@code i} and {@code j}, or {@code k}'s own height when the ground is unknown.
	 */
	private float groundHeightAt(int i, int j, int k, double time, GhostPredictor.Ground ground)
	{
		if (ground == null || Float.isNaN(clear[k]))
		{
			return h[k];
		}
		float span = (float) (t[j] - t[i]);
		float u = (float) ((time - t[i]) / span);
		float px = hermite(x[i], velX(i) * span, x[j], velX(j) * span, u);
		float py = hermite(y[i], velY(i) * span, y[j], velY(j) * span, u);
		float g = ground.heightAt(px, py);
		return Float.isNaN(g) ? h[k] : g + clear[k];
	}

	private static float hermite(float p0, float m0, float p1, float m1, float u)
	{
		float u2 = u * u;
		float u3 = u2 * u;
		return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1;
	}

	/** The gravity parabola through (t0, h0) and (t1, h1) at {@code time}. */
	private void parabola(double t0, float h0, double t1, float h1, double time)
	{
		double span = Math.max(1e-4, t1 - t0);
		double s = Math.max(0.0, Math.min(span, time - t0));
		float g = GhostPredictor.GRAVITY;
		rh = (float) (h0 + (h1 - h0) * s / span + g / 2.0 * s * (span - s));
		rvh = (float) ((h1 - h0) / span + g / 2.0 * (span - 2 * s));
		rstate = AIR;
	}

	private void hold(int i)
	{
		rx = x[i];
		ry = y[i];
		rh = h[i];
		rhd = hd[i];
		rvx = 0f;
		rvy = 0f;
		rvh = 0f;
		rturn = 0f;
		rstate = st[i];
	}

	/** Dead reckoning from the last sample {@code i}, {@code past} seconds after it (at most as far as playback goes). */
	private void extrapolate(int i, float past, GhostPredictor.Ground ground)
	{
		// the playback itself slows to a stop past the dead reckoning (dryRate): the ghost goes where its clock goes
		float dt = Math.min(past, MAX_EXTRAPOLATION + SETTLE_TIME);
		float svx = velX(i);
		float svy = velY(i);
		float w = turnAt(i);
		int state = st[i];
		boolean carving = state == ROLL || state == MANUAL;
		float px = x[i];
		float py = y[i];
		float pvx = svx;
		float pvy = svy;
		if (carving && Math.abs(w) > 1e-3f)
		{
			// the velocity turns with the heading: along the arc, not its tangent
			double theta = Math.atan2(svx, svy);
			double r = Math.hypot(svx, svy) / w;
			px += (float) (r * (Math.cos(theta) - Math.cos(theta + w * dt)));
			py += (float) (r * (Math.sin(theta + w * dt) - Math.sin(theta)));
			double speed = Math.hypot(svx, svy);
			pvx = (float) (Math.sin(theta + w * dt) * speed);
			pvy = (float) (Math.cos(theta + w * dt) * speed);
		}
		else
		{
			px += svx * dt;
			py += svy * dt;
		}
		rx = px;
		ry = py;
		rhd = hd[i] + w * dt;
		rstate = state;
		float svh = heightSlope(i);
		if (state == AIR)
		{
			rh = h[i] + svh * dt - GhostPredictor.GRAVITY / 2f * dt * dt;
			rvh = svh - GhostPredictor.GRAVITY * dt;
			if (ground != null)
			{
				float g = ground.heightAt(px, py);
				if (!Float.isNaN(g) && rh < g)
				{
					rh = g;
					rvh = 0f;
				}
			}
		}
		else if (carving)
		{
			onGround(i, ground);
		}
		else
		{
			rh = h[i] + svh * dt;
			rvh = svh;
		}
		rvx = pvx;
		rvy = pvy;
		rturn = Math.max(-TurnRateMeter.MAX_RATE, Math.min(TurnRateMeter.MAX_RATE, w));
	}

	// ---- what is drawn now (after advance)

	float x()
	{
		return ox;
	}

	float y()
	{
		return oy;
	}

	float h()
	{
		return oh;
	}

	/** Wrapped to (-PI, PI]. */
	float heading()
	{
		return ohd;
	}

	float vx()
	{
		return ovx;
	}

	float vy()
	{
		return ovy;
	}

	float vh()
	{
		return ovh;
	}

	/** Heading turn rate at the playback time, rad/s (the curve's own: smooth through the samples). */
	float turnRate()
	{
		return oturn;
	}

	SkaterState state()
	{
		return STATES[ostate];
	}

	/** The update being played: the latest one sent at or before the playback time. */
	GhostState current()
	{
		return current != null ? current : kn > 0 ? ks[0] : null;
	}

	/** The playback time, in seconds of the sender's (unwrapped) clock. */
	double playbackTime()
	{
		return tp;
	}

	/** The playback delay aimed at now, seconds behind the sender's clock's floor. */
	float delay()
	{
		return sync.delay();
	}

	GhostClockSync sync()
	{
		return sync;
	}

	/** The playback clock's speed over the last frame (1: as ours). */
	float rate()
	{
		return rate;
	}

	/** The playback time is past the last sample (dead reckoning or holding). */
	boolean underrun()
	{
		return dry || n > 0 && tp > t[n - 1];
	}

	/** The board flip playing at the playback time, or null. */
	Trick flipTrick()
	{
		int f = flipIndex();
		return f < 0 ? null : ft[f];
	}

	/** Seconds into {@link #flipTrick} at the playback time. */
	float flipTime()
	{
		int f = flipIndex();
		return f < 0 ? 0f : (float) (tp - fs[f]);
	}

	private int flipIndex()
	{
		int best = -1;
		for (int i = 0; i < fn; i++)
		{
			if (fs[i] <= tp && tp < fs[i] + ft[i].duration && (best < 0 || fs[i] > fs[best]))
			{
				best = i;
			}
		}
		return best;
	}

	/**
	 * The front / back flip angle at the playback time (raw, whole turns kept): between two updates in the air along
	 * the curve through both angles and rates, else on from the update being played as the predictor would.
	 */
	float bodyFlip()
	{
		if (kn == 0)
		{
			return 0f;
		}
		int k = keyIndex(tp);
		GhostState a = ks[k];
		if (a.state != SkaterState.AIRBORNE)
		{
			return 0f;
		}
		float since = (float) Math.max(0.0, tp - kt[k]);
		if (k + 1 < kn && ks[k + 1].state == SkaterState.AIRBORNE && kt[k] <= tp)
		{
			GhostState b = ks[k + 1];
			float span = (float) (kt[k + 1] - kt[k]);
			float u = since / span;
			float u2 = u * u;
			float u3 = u2 * u;
			return (2 * u3 - 3 * u2 + 1) * a.bodyFlip + (u3 - 2 * u2 + u) * a.bodyFlipRate * span
				+ (-2 * u3 + 3 * u2) * b.bodyFlip + (u3 - u2) * b.bodyFlipRate * span;
		}
		float dt = Math.min(GhostPredictor.MAX_EXTRAPOLATION, since);
		float angle = a.bodyFlip;
		float w = a.bodyFlipRate;
		if (Math.abs(w) >= BodyFlipMeter.HELD_RATE)
		{
			angle += w * dt;
		}
		else if (w != 0f)
		{
			float r = BodyFlip.residual(angle);
			angle -= Math.copySign(Math.min(Math.abs(r), Math.abs(w) * dt), r);
		}
		return angle;
	}

	/** Our clock's time of the last {@link #advance}. */
	float lastNow()
	{
		return lastNow;
	}

	GhostPredictor.Ground lastGround()
	{
		return lastGround;
	}
}
