package com.gielinorskate.physics;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.SpinNames;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.tricks.TrickKind;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import java.util.ArrayList;
import java.util.List;

/** Fixed-step skateboard simulation. Positions in local units, heights up-positive. */
public final class SkatePhysics
{
	private static final float SLOPE_SAMPLE = 16f;
	private static final float SLOW_LANDING_SPEED = 50f;
	/** A flip at least this far through its rotation at touchdown is caught (forgiving landing). */
	private static final float FLIP_CATCH_FRACTION = 0.75f;
	/**
	 * A flip at least this far through may lock onto a rail (review P3: the 75% gate cost 18-27 points of
	 * kickflip lock rate); it keeps turning on the rail until done.
	 */
	private static final float FLIP_LOCK_FRACTION = 0.5f;
	/**
	 * A late flick may be sped up to at most 1 / this of its normal pace to finish before touchdown
	 * (0.6 * 0.35 = 0.21 s for a kickflip); with less air left than that it is ignored.
	 */
	private static final float LATE_FLIP_MIN_FRACTION = 0.6f;
	/** Board pitch while balancing a manual (nose up) / nose manual (nose down), radians. */
	private static final float MANUAL_PITCH = 0.25f;
	/** A manual needs at least this speed (u/s) to start. */
	private static final float MANUAL_MIN_SPEED = 150f;
	/**
	 * A manual already held ends only below this speed (u/s): 30 u/s of hysteresis, so a speed swinging a few
	 * u/s either side of 150 on bumps does not start and end the hold every few frames.
	 */
	private static final float MANUAL_KEEP_SPEED = 120f;
	private static final float TWO_PI = (float) (2 * Math.PI);
	/** Slack on the grab aim / tweak times: the hold time is a sum of float steps (25 * 0.02f is not 0.5f). */
	private static final float HOLD_TIME_EPSILON = 1e-4f;

	/** Grind slowdown along the rail, u/s^2. */
	private static final float GRIND_FRICTION = 60f;
	/** Below this grind speed the skater drops off the rail (was 150). */
	private static final float GRIND_MIN_SPEED = 100f;
	/** Horizontal speed needed to lock onto a rail at all. */
	private static final float GRIND_LOCK_MIN_SPEED = 150f;
	/** In the air with the spin assist held (button tricks), steering spins this much faster. */
	static final float SPIN_FAST_MULT = 1.5f;
	/** A rail this close (horizontally) is near: the grind button grinds rather than stepping off. */
	static final float RAIL_NEAR_DISTANCE = 192f;
	/** ...with its top at most this far above the board. */
	static final float RAIL_NEAR_ABOVE = 256f;
	/**
	 * On lock the velocity is turned along the rail at max(along-rail component, this fraction of the
	 * horizontal speed, {@link #GRIND_LOCK_MIN_RAIL_SPEED}), so a steep catch still slides.
	 */
	private static final float GRIND_LOCK_KEEP_FRACTION = 0.6f;
	private static final float GRIND_LOCK_MIN_RAIL_SPEED = 300f;
	/**
	 * Hop when a grind ends on its own (rail end or too slow): GRIND_EXIT_POP_BASE + GRIND_EXIT_POP_PER_SPEED *
	 * rail speed, clamped to [GRIND_EXIT_POP_MIN, GRIND_EXIT_POP_MAX]. It was a flat 200; 100 + 0.2 * 500 keeps
	 * that at a typical 500 u/s, a slow drop-off (below 100 u/s) gets 150 and a 1100+ u/s grind flies off at 320.
	 */
	private static final float GRIND_EXIT_POP_BASE = 100f;
	private static final float GRIND_EXIT_POP_PER_SPEED = 0.2f;
	private static final float GRIND_EXIT_POP_MIN = 150f;
	private static final float GRIND_EXIT_POP_MAX = 320f;
	/**
	 * No lock-on onto any rail for this long after leaving a grind (was 0.45 s, which also blocked rail-to-rail
	 * transfers). The rail just left is guarded separately: until touchdown after a drop-off, and while still
	 * rising after a pop (see {@link #noRelockSeg} and {@link #risingRelockSeg}), so 0.1 s only has to cover
	 * the step of leaving.
	 */
	private static final float GRIND_RELOCK_COOLDOWN = 0.1f;
	/**
	 * No lock-on for this long after rolling off a ledge, so the skater does not snap onto the ledge edge just
	 * rolled off (that edge is not known as a segment): after 0.45 s the drop is 1000 * 0.45^2 = 202, far
	 * below the edge's lock window.
	 */
	private static final float ROLL_OFF_LOCK_COOLDOWN = 0.45f;
	/**
	 * Sideways speed toward the side the rail was approached from on a slow drop-off with no steer, u/s. The
	 * 150 u/s hop off a 60 rail lasts about 0.33 s, carrying the skater 50 units off the rail line instead of
	 * dropping straight down beside (or inside) it.
	 */
	private static final float GRIND_DROP_SIDE_SPEED = 150f;
	/** Board-to-rail angles: below CROOKED_MIN a straight grind, below BOARDSLIDE_MIN a crooked grind. */
	private static final float CROOKED_MIN = (float) Math.toRadians(25);
	private static final float BOARDSLIDE_MIN = (float) Math.toRadians(55);
	/** From ROLLING, only a rail at least this far above the riding surface (a curb rolled into) locks on. */
	private static final float ROLL_LOCK_MIN_RISE = 4f;
	/**
	 * Sideways speed added by steering (A/D) while popping off a rail, u/s. An uncharged pop is in the
	 * air for 2 * 754.4 / 2000 = 0.75 s, so full steer carries the skater 200 * 0.75 = 151 units off the
	 * rail line, well clear of the 44-unit GrindMap.SNAP_DISTANCE.
	 */
	private static final float GRIND_POP_SIDE_SPEED = 200f;
	/** Board pitch during a nosegrind (nose down) / 5-0 (tail down), radians. */
	private static final float GRIND_PITCH = 0.2f;

	/** Longest ground move per substep, units: bumps and step-ups are judged over this distance. */
	private static final float GROUND_SUBSTEP = 8f;
	/**
	 * A rise or drop only blocks / rolls off when it is both over maxStepUp / rollOffDrop and steeper than
	 * this (rise per unit moved); anything gentler is ridden over with the board glued to the ground.
	 */
	private static final float MAX_GROUND_GRADIENT = 1.5f;
	/** Below this forward speed a push counts as a push from rest. */
	private static final float REST_SPEED = 50f;
	/** Rolling back slower than this (and not a fakie landing) counts as stalled: a push goes forward. */
	private static final float STALL_SPEED = 400f;
	/** How far (at most) a skater who bailed into a wall is nudged out along its normal on recovery. */
	private static final float BAIL_PUSH_OUT = 24f;
	/**
	 * Within this of the loaded area's edge (beyond the skater's radius) a hit never bails: the edge is the end
	 * of the loaded map, not a wall the player can see, so it stops or scrapes the skater instead.
	 */
	private static final float EDGE_SCRAPE_MARGIN = 32f;
	/** R while rolling slower than this stands the skater still (and unwedged), as after a bail. */
	private static final float RESET_MAX_SPEED = 150f;
	/** Wall contacts closer together than this are one scrape (the first-contact speed loss applies once). */
	private static final float WALL_CONTACT_MEMORY = 0.25f;
	/** Seconds an off-axis landing takes to turn the travel onto the board. */
	private static final float LAND_BLEND_TIME = 0.15f;
	/** Steer magnitudes below this count as released (as for the spin assist). */
	private static final float STEER_DEADZONE = 0.1f;
	/** A landing within this of the travel (either way round), with its flip finished, is clean. */
	private static final float CLEAN_LANDING_ANGLE = (float) Math.toRadians(15);
	/** Body flip rate while Shift + W / Shift + S is held: one whole flip in 0.55 s. */
	private static final float BODY_FLIP_RATE = TWO_PI / 0.55f;
	/**
	 * Released within this of a whole rotation, the body eases onto it at BODY_FLIP_ASSIST_RATE (like the
	 * spin assist); further out it stays where it is (no auto-continue). 40 degrees short at 3 rad/s takes
	 * 0.23 s; the landing itself accepts up to 40 degrees (BodyFlip.LAND_TOLERANCE).
	 */
	private static final float BODY_FLIP_ASSIST_WINDOW = (float) Math.toRadians(60);
	private static final float BODY_FLIP_ASSIST_RATE = 3f;

	private final SkateTuning t;
	private final CollisionWorld world;
	private final GrindMap grinds;
	private final List<SkateEvent> events = new ArrayList<>();
	private final List<TrickEvent> trickEvents = new ArrayList<>();

	private SkaterState state = SkaterState.ROLLING;
	private float x;
	private float y;
	private float h;
	private float heading;
	/** Signed speed along heading while grounded; negative = rolling backwards (fakie). */
	private float speed;
	/** True after landing backwards on purpose: pushes then go fakie too. Cleared once the speed is >= 0. */
	private boolean fakie;
	/** Seconds since the last wall contact while rolling; a contact counts as a new hit (costs speed) only after WALL_CONTACT_MEMORY. */
	private float sinceWall = WALL_CONTACT_MEMORY;
	/** Steering is locked while this runs down after a hard (non-bail) wall hit. */
	private float stumbleTimer;
	/** Set when a bail ran into a wall: the recovery faces along this normal, away from it. */
	private boolean bailWall;
	private float bailWallNx;
	private float bailWallNy;
	/**
	 * After an off-axis landing the travel direction is heading + landSlip (radians), easing onto the board
	 * at landSlipRate rad/s instead of snapping.
	 */
	private float landSlip;
	private float landSlipRate;
	/** A steer already held when leaving the ground does not spin the board until it is released. */
	private boolean airSteerHeld;
	/** Camera heading on the ground just before the current flight (see {@link #getCameraHeading()}). */
	private float airCameraRef;
	/**
	 * After a wall hit in the air the camera holds airCameraHold until the landing, turned only as far as the
	 * spin assist turns the board (see stepAir).
	 */
	private boolean airCameraHeld;
	private float airCameraHold;
	/** Seconds in the air so far, and the length of the flight that ended in the latest landing. */
	private float airTime;
	private float lastAirtime;
	/** True while in the air from a plain roll-off (cleared by a flip or grab started in the air). */
	private boolean rollOffFlight;
	/** Distance rolled up the current ramp approach (see rampLaunch); 0 off the ground or on flatter ground. */
	private float rampRun;
	/** Signed heading change (radians, + = clockwise from above) accumulated since the current take-off. */
	private float airSpin;
	/** The part of airSpin turned by tricks themselves (a bigspin's body 180): not named as a spin. */
	private float airAutoSpin;
	/** Body turn the current flip has applied to the heading so far (see {@link Trick#bodyYawTurns}). */
	private float flipAutoSpin;
	/** Front/back flip angle of the whole skater this air, radians, + = frontflip. 0 off the air. */
	private float bodyFlipAngle;
	/** W / S already held at take-off: they cannot drive a body flip until released and pressed again. */
	private boolean flipForwardHeld;
	private boolean flipBackHeld;
	/** The lean keys already held at take-off: as for W / S, they cannot drive a grab-flip until pressed again. */
	private boolean leanForwardHeld;
	private boolean leanBackHeld;
	/** Quality of the latest landing; null before any, in the air, after a bail or after a plain roll-off. */
	private LandingQuality lastLandingQuality;
	private float vx;
	private float vy;
	private float vh;
	private float crouchTime;
	/** Downward speed (u/s, >= 0) of the latest landing, for the renderer's landing compression. */
	private float lastLandingSpeed;
	/** True when the latest pop was off the nose (a nollie-family trick), for the renderer's pop tilt. */
	private boolean lastPopNollie;
	/** True when the current flight left the ground riding fakie: its flips are named "Fakie ...". */
	private boolean airFakie;
	private float pushCooldown;
	private float bailTimer;
	/** Why the latest bail happened; null before any. */
	private BailReason lastBailReason;
	/** Time left until the skater gets back on automatically. */
	private float recoverTimer;
	private boolean resetQueued;

	/**
	 * Flip currently rotating the board (null when none), seconds since it started, and the seconds it
	 * takes (the trick's duration, or less for a late flick compressed to fit the air left). A flip caught
	 * before it finished keeps turning on the ground or rail until done.
	 */
	private Trick flipTrick;
	private float flipTime;
	private float flipDuration;
	/** Current grab or manual (null when none), and seconds it has been held. */
	private Trick activeHold;
	private float holdTime;
	/**
	 * For a grab: held with the left hand (Q) rather than the right (E); aimed already (an aim only picks the grab
	 * once, within Grabs.AIM_SECONDS of the press); tweaked already (once, past Grabs.TWEAK_SECONDS).
	 */
	private boolean grabLeftHand;
	private boolean grabAimed;
	private boolean grabTweaked;
	/**
	 * The rolling grab: Q / E held while ROLLING grabs the board on the ground (null when none), and seconds held. Pose
	 * only: never a hold, no trick events, no points, no combo; it only stops pushes. The grab's hand and aim share
	 * grabLeftHand / grabAimed with the air grab.
	 */
	private Trick groundGrab;
	private float groundGrabTime;

	/** Segment being ground (null when not grinding), distance along it from end 0, and +1/-1 travel direction. */
	private GrindSegment grindSeg;
	private float grindPos;
	private float grindDir;
	private float grindSpeed;
	/**
	 * The kind of grind picked at lock; W/S switch between the tricks of the same family mid-grind.
	 * STRAIGHT (50-50/nosegrind/5-0) lines the board up with the rail; the others keep it across.
	 */
	private GrindFamily grindFamily = GrindFamily.STRAIGHT;

	/** Grind families and the trick each gives with no key, W held and S held. */
	private enum GrindFamily
	{
		STRAIGHT(Trick.FIFTY_FIFTY, Trick.NOSEGRIND, Trick.FIVE_O),
		/**
		 * Board 25-55 degrees off the rail. Crooked rides the nose truck already, so W picks the feeble (back
		 * truck on the rail, nose hung over its far side) and S the smith (back truck on, nose dipped below the
		 * near side).
		 */
		CROOKED(Trick.CROOKED, Trick.FEEBLE, Trick.SMITH),
		/** Board across the rail (55+ degrees): boardslide, W noseslide, S tailslide. */
		SLIDE(Trick.BOARDSLIDE, Trick.NOSESLIDE, Trick.TAILSLIDE),
		/** Across the rail but turned past square to the approach: lipslide whatever the keys. */
		LIP(Trick.LIPSLIDE, Trick.LIPSLIDE, Trick.LIPSLIDE);

		final Trick plain;
		final Trick nose;
		final Trick tail;

		GrindFamily(Trick plain, Trick nose, Trick tail)
		{
			this.plain = plain;
			this.nose = nose;
			this.tail = tail;
		}

		/** W held: the nose variation; S held: the tail one; otherwise the plain trick (W wins if both). */
		Trick trick(SkateInput in)
		{
			return in.pushHeld ? nose : in.leanBack ? tail : plain;
		}
	}
	private float grindCooldown;
	/** The segment dropped off (rail end or too slow); it cannot be locked onto again until the skater lands. */
	private GrindSegment noRelockSeg;
	/** The segment popped off; it cannot be locked onto again while still rising, but can once falling. */
	private GrindSegment risingRelockSeg;
	/**
	 * True in the air after a grind ended without a pop (rail end or too slow): no rail at all can be locked
	 * until touchdown or a bail. Without it a slow drop-off's 150 u/s side hop carried the skater onto a
	 * parallel rail 40-88 units away (a table or crate's other top edge), the lock lifted the speed back to
	 * GRIND_LOCK_MIN_RAIL_SPEED and the two rails ping-ponged forever.
	 */
	private boolean dropOffFlight;
	/** +1 when the rail was approached from the right of the grind travel direction, -1 from the left. */
	private float approachRight = 1f;

	/** No grindable edges. */
	public SkatePhysics(SkateTuning tuning, CollisionWorld world, float x, float y, float heading)
	{
		this(tuning, world, new GrindMap(), x, y, heading);
	}

	public SkatePhysics(SkateTuning tuning, CollisionWorld world, GrindMap grinds, float x, float y, float heading)
	{
		this.t = tuning;
		this.world = world;
		this.grinds = grinds;
		this.x = x;
		this.y = y;
		this.heading = Angles.wrap(heading);
		this.h = world.groundHeight(x, y);
	}

	public void step(float dt, SkateInput in)
	{
		grindCooldown = Math.max(0f, grindCooldown - dt);
		// a stumble's steering lock runs out in the air or on a rail too, not only while rolling
		stumbleTimer = Math.max(0f, stumbleTimer - dt);
		switch (state)
		{
			case ROLLING:
			case MANUAL:
				stepRolling(dt, in);
				break;
			case AIRBORNE:
				rampRun = 0f;
				stepAir(dt, in);
				break;
			case BAILED:
				rampRun = 0f;
				stepBailed(dt, in);
				break;
			case GRINDING:
				rampRun = 0f;
				stepGrind(dt, in);
				break;
		}
		if (state != SkaterState.ROLLING)
		{
			// popped, rolled off, locked onto a rail, bailed or into a manual: the rolling grab is let go
			groundGrab = null;
		}
		in.clearEdges();
	}

	/** ROLLING and MANUAL share this: a manual moves exactly like rolling, only the board pose differs. */
	private void stepRolling(float dt, SkateInput in)
	{
		if (in.resetRequested && state == SkaterState.ROLLING && Math.abs(speed) < RESET_MAX_SPEED)
		{
			standStill();
			return;
		}
		finishCaughtFlip(dt);
		updateManual(dt, in);
		updateGroundGrab(dt, in);
		pushCooldown = Math.max(0f, pushCooldown - dt);
		Trick pop = popTrick(in.gestures);
		if (in.crouch || in.charge)
		{
			crouchTime += dt;
		}
		else if (pop == null)
		{
			// the flick that pops arrives with the wind-up already released: keep its charge for the pop
			crouchTime = Math.max(0f, crouchTime - 2 * dt);
		}
		sinceWall = Math.min(WALL_CONTACT_MEMORY + 1f, sinceWall + dt);
		landSlip = towardZero(landSlip, landSlipRate * dt);
		// a stumble off a wall hit locks steering for a moment
		float steer = stumbleTimer > 0f ? 0f : in.steer;

		if (speed >= 0f)
		{
			fakie = false;
		}
		// W never pushes in a manual (Space + W is a nose manual) nor while grabbing the board on the ground
		if (state == SkaterState.ROLLING && (in.pushPressed || in.pushHeld) && pushCooldown == 0f && !in.crouch
			&& groundGrab == null)
		{
			push();
		}

		boolean tightCarve = in.powerslide && steer != 0f;
		if (in.powerslide && !tightCarve && !in.brakeBlocked && speed != 0f)
		{
			// Shift alone: powerslide brake
			speed = towardZero(speed, t.powerslideDecel * dt);
		}
		else
		{
			float s = Math.abs(speed);
			float carve = t.carveRate / (1f + s / t.carveSpeedFalloff);
			if (tightCarve)
			{
				// Shift + steer: a tight carve that scrubs speed
				carve *= t.tightCarveMult;
				speed = towardZero(speed, (t.tightCarveBleed + t.tightCarveBleedFraction * s) * dt);
			}
			if (s < t.pivotSpeed)
			{
				// slow: blend toward a kick-turn pivot
				float k = s / t.pivotSpeed;
				carve = t.pivotRate * (1f - k) + carve * k;
			}
			heading = Angles.wrap(heading + steer * carve * dt);
		}

		float fx = (float) Math.sin(heading);
		float fy = (float) Math.cos(heading);
		// terrain only: a curb or platform edge next to the skater is not a hill
		float slope = clamp((world.terrainHeight(x + fx * SLOPE_SAMPLE, y + fy * SLOPE_SAMPLE)
			- world.terrainHeight(x - fx * SLOPE_SAMPLE, y - fy * SLOPE_SAMPLE)) / (2 * SLOPE_SAMPLE),
			-t.maxSlope, t.maxSlope);
		speed -= t.gravity * t.slopeGravityScale * slope * dt;
		speed = towardZero(speed, (t.rollingFriction + t.drag * speed * speed) * dt);
		speed = clamp(speed, -t.maxSpeed, t.maxSpeed);

		if (pop != null)
		{
			// popping out of a manual ends the hold; the scorer links it into the same combo
			endHold();
			lastPopNollie = isNollie(pop);
			trickEvents.add(TrickEvent.trick(pop, ridingFakie()));
			if (pop.kind == TrickKind.FLIP)
			{
				startFlip(pop);
			}
			else
			{
				clearFlip(); // a plain pop: a flip still finishing from the last catch is done
			}
			float charge = clamp(crouchTime / t.crouchChargeTime, t.minPopFraction, 1f);
			float motion = heading + landSlip;
			vx = (float) Math.sin(motion) * speed;
			vy = (float) Math.cos(motion) * speed;
			// the board already rises (or sinks) with the ground: speed * slope
			vh = t.ollieImpulse * charge + speed * slope;
			crouchTime = 0f;
			takeOff(in, rollingCameraHeading());
			events.add(SkateEvent.POP);
			return;
		}

		// substeps of at most GROUND_SUBSTEP units, so step-up blocking and roll-off judge the ground
		// over a short distance instead of a whole (speed-dependent) 0.02 s step
		int n = Math.max(1, (int) Math.ceil(Math.abs(speed) * dt / GROUND_SUBSTEP));
		float sdt = dt / n;
		boolean rollOff = false;
		float launchVh = 0f;
		for (int k = 0; k < n && !rollOff && launchVh == 0f; k++)
		{
			// the board may ease along a wall between substeps
			float mx = (float) Math.sin(heading + landSlip);
			float my = (float) Math.cos(heading + landSlip);
			float nx = x + mx * speed * sdt;
			float ny = y + my * speed * sdt;
			float px = x;
			float py = y;
			if (blocked(nx, ny, true))
			{
				if (momentumClimb(nx, ny))
				{
					x = nx;
					y = ny;
				}
				else if (!scrapeRolling(mx * speed * sdt, my * speed * sdt, sdt, in.steer != 0f))
				{
					return; // bailed
				}
			}
			else
			{
				x = nx;
				y = ny;
			}
			float g = world.groundHeight(x, y);
			float drop = h - g;
			if (drop > t.rollOffDrop && drop > MAX_GROUND_GRADIENT * Math.abs(speed * sdt) && !dropIn(px, py, g))
			{
				rollOff = true;
			}
			else
			{
				// small rises and drops (bumps, hills, curbs) are followed exactly
				h = g;
				// judged every substep: a crest is only a few units wide at full speed
				launchVh = rampLaunch(sdt);
			}
		}

		if (state == SkaterState.ROLLING && tryLockGrind(in, getTravelHeading(), Math.abs(speed), true))
		{
			return;
		}

		if (rollOff || launchVh > 0f)
		{
			float fx2 = (float) Math.sin(heading + landSlip);
			float fy2 = (float) Math.cos(heading + landSlip);
			vx = fx2 * speed;
			vy = fy2 * speed;
			vh = launchVh;
			endHold();
			takeOff(in, rollingCameraHeading());
			// no snapping onto the ledge just rolled off
			grindCooldown = ROLL_OFF_LOCK_COOLDOWN;
			rollOffFlight = true;
			events.add(SkateEvent.ROLL_OFF);
		}
	}

	/**
	 * One push. From a standstill (or a slow roll back that is not a fakie landing) it pushes forward
	 * with {@link SkateTuning#pushFromRest}; otherwise along the roll with an impulse that tapers toward
	 * {@link SkateTuning#maxPushSpeed}.
	 */
	private void push()
	{
		if (!fakie && speed < REST_SPEED && speed > -STALL_SPEED)
		{
			// stalled on a hill or barely moving: plant a foot and push off forwards
			speed = Math.max(t.pushMinImpulse, speed + t.pushFromRest);
		}
		else
		{
			float dir = speed < 0 ? -1f : 1f;
			float s = Math.abs(speed);
			if (s < t.maxPushSpeed)
			{
				float taper = (float) Math.sqrt(1f - s / t.maxPushSpeed);
				s = Math.min(t.maxPushSpeed, s + Math.max(t.pushMinImpulse, t.pushImpulse * taper));
			}
			speed = dir * s;
		}
		pushCooldown = t.pushCooldown;
		events.add(SkateEvent.PUSH);
	}

	/**
	 * The manual the held keys ask for: Space = manual, Space + W (or the controller's tilt down) = nose manual;
	 * none below MANUAL_MIN_SPEED
	 * (below MANUAL_KEEP_SPEED once already in a manual).
	 */
	private Trick wantedManual(SkateInput in)
	{
		float min = state == SkaterState.MANUAL ? MANUAL_KEEP_SPEED : MANUAL_MIN_SPEED;
		if (!in.manualHeld || Math.abs(speed) < min)
		{
			return null;
		}
		return in.pushHeld || in.noseManualHeld ? Trick.NOSE_MANUAL : Trick.MANUAL;
	}

	/**
	 * Enters, keeps or leaves a manual from the grounded states. Balance is automatic: a manual ends
	 * (HOLD_END, no bail) when the manual key is released or the skater slows below MANUAL_KEEP_SPEED;
	 * pressing or releasing W mid-manual ends one manual and starts the other.
	 */
	private void updateManual(float dt, SkateInput in)
	{
		Trick wanted = wantedManual(in);
		if (state == SkaterState.MANUAL && activeHold != wanted)
		{
			endHold();
		}
		if (state == SkaterState.ROLLING && wanted != null)
		{
			state = SkaterState.MANUAL;
			startHold(wanted);
		}
		if (state == SkaterState.MANUAL)
		{
			holdTime += dt;
		}
	}

	/**
	 * The rolling grab (ROLLING only): a held grab key grabs the board as in the air (Q unaimed an Indy, E a Melon, the
	 * first aim within Grabs.AIM_SECONDS picking the grab) but never tweaks; letting the key go lets go. A manual,
	 * a pop or anything else that leaves ROLLING ends it (see {@link #step}).
	 */
	private void updateGroundGrab(float dt, SkateInput in)
	{
		if (state != SkaterState.ROLLING)
		{
			groundGrab = null;
			return;
		}
		if (groundGrab != null)
		{
			if (!(grabLeftHand ? in.grabLeft : in.grabRight))
			{
				groundGrab = null;
				return;
			}
			groundGrabTime += dt;
			if (!grabAimed && in.grabAim != null && groundGrabTime <= Grabs.AIM_SECONDS + HOLD_TIME_EPSILON)
			{
				grabAimed = true;
				groundGrab = Grabs.pick(grabLeftHand, in.grabAim);
			}
			return;
		}
		if (in.grabLeft || in.grabRight)
		{
			grabLeftHand = in.grabLeft;
			grabAimed = in.grabAim != null;
			grabTweaked = false;
			groundGrab = Grabs.pick(grabLeftHand, in.grabAim);
			groundGrabTime = dt;
		}
	}

	/** True for tricks popped off the nose (the nollie family). */
	private static boolean isNollie(Trick trick)
	{
		return trick.name().startsWith("NOLLIE");
	}

	/** First gesture that pops (an ollie/nollie or any flip), or null. */
	private static Trick popTrick(List<Gesture> gestures)
	{
		for (Gesture g : gestures)
		{
			Trick trick = TrickCatalog.forGesture(g);
			if (trick != null && (trick.kind == TrickKind.POP || trick.kind == TrickKind.FLIP))
			{
				return trick;
			}
		}
		return null;
	}

	private void startFlip(Trick trick)
	{
		startFlip(trick, trick.duration);
	}

	private void startFlip(Trick trick, float duration)
	{
		flipTrick = trick;
		flipTime = 0f;
		flipDuration = duration;
		flipAutoSpin = 0f;
	}

	/** Clears the flip (board back to neutral). */
	private void clearFlip()
	{
		flipTrick = null;
		flipTime = 0f;
		flipAutoSpin = 0f;
	}

	/** On the ground or a rail: a caught flip that was not quite done keeps turning until it is. */
	private void finishCaughtFlip(float dt)
	{
		if (flipTrick != null)
		{
			flipTime += dt;
			if (flipTime >= flipDuration)
			{
				clearFlip();
			}
		}
	}

	/** True when the current flip is less than FLIP_CATCH_FRACTION done (it cannot be landed or locked). */
	private boolean flipUncatchable()
	{
		return flipTrick != null && flipTime < FLIP_CATCH_FRACTION * flipDuration;
	}

	/** Estimated seconds until touchdown on the ground below, from the current height and vertical speed. */
	private float timeToGround()
	{
		float above = Math.max(0f, h - world.groundHeight(x, y));
		return (vh + (float) Math.sqrt(vh * vh + 2f * t.gravity * above)) / t.gravity;
	}

	private void startHold(Trick trick)
	{
		activeHold = trick;
		holdTime = 0f;
		trickEvents.add(TrickEvent.holdStart(trick));
	}

	/** Ends the current grab or manual (if any) with HOLD_END; a manual drops back to ROLLING. */
	private void endHold()
	{
		if (activeHold == null)
		{
			return;
		}
		trickEvents.add(TrickEvent.holdEnd(activeHold, holdTime));
		activeHold = null;
		holdTime = 0f;
		if (state == SkaterState.MANUAL)
		{
			state = SkaterState.ROLLING;
		}
	}

	/**
	 * Result of {@link #findContact}: the wall normal (toward the skater) and the part of the blocked move that
	 * is still free (contactFreeDx, contactFreeDy; zero when nothing is).
	 */
	private float contactNx;
	private float contactNy;
	private float contactFreeDx;
	private float contactFreeDy;
	private final Contact contactHit = new Contact();

	/**
	 * Works out the wall a blocked move (dx, dy) ran into. Where the world has a shape there
	 * ({@link CollisionWorld#contact}: tight object boxes and wall-edge boxes, skater radius
	 * {@link SkateTuning#skaterRadius}) its real normal is used and the move slides along the face (its part
	 * across the normal removed), so a rotated box is scraped along its own side. A normal pointing along the
	 * move (the step ended past a thin wall) is turned back against it. Otherwise (a steep terrain rise, or no
	 * shape in reach) the blocked axis gives the normal: if only the y part is blocked the wall runs along x
	 * (normal +-y), and the other way round; a diagonal move blocked only as a whole (a corner) slides along
	 * its larger component; with both axes blocked the normal is straight back against the move and nothing
	 * is free.
	 */
	private void findContact(float dx, float dy, boolean gradient)
	{
		boolean shaped = world.contact(x + dx, y + dy, t.skaterRadius, h, t.maxStepUp, contactHit);
		recordHit(shaped ? contactHit.box : Contact.UNKNOWN_BOX, shaped ? contactHit.label : UNSHAPED_HIT);
		if (shaped)
		{
			float nx = contactHit.nx;
			float ny = contactHit.ny;
			if (nx * dx + ny * dy > 0f)
			{
				nx = -nx;
				ny = -ny;
			}
			float into = -(nx * dx + ny * dy);
			if (into > 1e-6f)
			{
				contactNx = nx;
				contactNy = ny;
				float tx = dx + into * nx;
				float ty = dy + into * ny;
				if (slideFree(tx, ty, gradient))
				{
					contactFreeDx = tx;
					contactFreeDy = ty;
					return;
				}
				// The slide along the face still clips something: the far side of a rounded box corner, the end
				// of the next wall-edge box at a tile joint (whose least-penetration normal points along the
				// wall), or a second blocker. Stopping dead there wedged the skater for good: every later push
				// met the same corner. Push the slide out of what it clips, else fall back to the free axis.
				if (pushedOutSlide(tx, ty, dx, dy, gradient))
				{
					return;
				}
				if (axisSlide(dx, dy, gradient))
				{
					return; // keeps the contact normal: the speed loss and alignment follow the real face
				}
				contactFreeDx = 0f;
				contactFreeDy = 0f;
				return;
			}
		}
		if (axisSlide(dx, dy, gradient))
		{
			if (contactFreeDx != 0f)
			{
				contactNx = 0f;
				contactNy = -Math.signum(dy);
			}
			else
			{
				contactNx = -Math.signum(dx);
				contactNy = 0f;
			}
			return;
		}
		contactFreeDx = 0f;
		contactFreeDy = 0f;
		float d = (float) Math.hypot(dx, dy);
		contactNx = d > 0f ? -dx / d : 0f;
		contactNy = d > 0f ? -dy / d : 0f;
	}

	/** Debug name of a blocked move with no shape in reach: a steep rise or a whole-tile edge. */
	static final String UNSHAPED_HIT = "Steep ground or tile edge (no shape)";
	private boolean hitPending;
	private int hitBox = Contact.UNKNOWN_BOX;
	private String hitLabel;

	private void recordHit(int box, String label)
	{
		hitPending = true;
		hitBox = box;
		hitLabel = label;
	}

	/** True when the skater ran into (or bailed on) a blocker since {@link #clearHit()}; for the debug view. */
	public boolean hasHit()
	{
		return hitPending;
	}

	/** The latest blocker run into: its number in the world ({@link Contact#box}), for the debug view. */
	public int getHitBox()
	{
		return hitBox;
	}

	/** The latest blocker run into: its debug name ("Rocks (game object)"), or null. */
	public String getHitLabel()
	{
		return hitLabel;
	}

	public void clearHit()
	{
		hitPending = false;
	}

	/** Extra distance a clipped slide is pushed out by, so the next substep starts just clear. */
	private static final float SLIDE_CLEARANCE = 0.05f;
	private final Contact slideHit = new Contact();

	/**
	 * The slide (tx, ty) along a face, or a half or quarter of it, pushed out of whatever its end still
	 * overlaps (a few times, for a corner between two blockers) until nothing blocks it: past a rounded box
	 * corner the whole slide clips the corner on its way even where its end is clear. Only a slide that still
	 * goes forward (along the blocked move dx, dy) counts. Sets contactFreeDx/Dy and returns true on success.
	 */
	private boolean pushedOutSlide(float tx, float ty, float dx, float dy, boolean gradient)
	{
		for (float scale = 1f; scale >= 0.25f; scale *= 0.5f)
		{
			float px = tx * scale;
			float py = ty * scale;
			for (int k = 0; k < 3; k++)
			{
				if (k > 0 && px * dx + py * dy > 0f && slideFree(px, py, gradient))
				{
					contactFreeDx = px;
					contactFreeDy = py;
					return true;
				}
				if (!world.contact(x + px, y + py, t.skaterRadius, h, t.maxStepUp, slideHit))
				{
					break;
				}
				px += slideHit.nx * (slideHit.depth + SLIDE_CLEARANCE);
				py += slideHit.ny * (slideHit.depth + SLIDE_CLEARANCE);
			}
			if (px * dx + py * dy > 0f && slideFree(px, py, gradient))
			{
				contactFreeDx = px;
				contactFreeDy = py;
				return true;
			}
		}
		return false;
	}

	/** A non-zero slide (tx, ty) from the current position that nothing blocks. */
	private boolean slideFree(float tx, float ty, boolean gradient)
	{
		return Math.hypot(tx, ty) > 1e-4f && !blocked(x + tx, y + ty, gradient);
	}

	/**
	 * The grid-axis slide: the x or y part of the blocked move (dx, dy) alone, whichever is free (the larger
	 * when both are). Sets contactFreeDx/Dy and returns true when one is; leaves them untouched otherwise.
	 */
	private boolean axisSlide(float dx, float dy, boolean gradient)
	{
		boolean freeX = Math.abs(dx) > 1e-4f && !blocked(x + dx, y, gradient);
		boolean freeY = Math.abs(dy) > 1e-4f && !blocked(x, y + dy, gradient);
		if (freeX && freeY)
		{
			freeX = Math.abs(dx) >= Math.abs(dy);
			freeY = !freeX;
		}
		if (!freeX && !freeY)
		{
			return false;
		}
		contactFreeDx = freeX ? dx : 0f;
		contactFreeDy = freeY ? dy : 0f;
		return true;
	}

	/** How directly a move (dx, dy) runs into the contact normal: cos(incidence), 1 = head-on, 0 = grazing. */
	private float contactInto(float dx, float dy)
	{
		float d = (float) Math.hypot(dx, dy);
		return d > 0f ? Math.max(0f, -(dx * contactNx + dy * contactNy) / d) : 0f;
	}

	/** A hit bails only when hard (speed * into above wallBailSpeed) AND within wallBailAngleDeg of head-on. */
	private boolean bailsOnWall(float moveSpeed, float into)
	{
		float cone = (float) Math.cos(Math.toRadians(t.wallBailAngleDeg));
		return moveSpeed * into > t.wallBailSpeed && into >= cone - 1e-4f;
	}

	/**
	 * A grounded substep (dx, dy) was blocked. The skater keeps the speed and slides along the wall
	 * (only the free part of the move, see {@link #findContact}), losing wallContactLoss * into of the speed on first contact and
	 * wallScrape * into u/s^2 while pressed against it. The heading is never touched while steering;
	 * otherwise the board eases along the wall at no more than wallAlignRate. A hard, near head-on hit
	 * bails; a softer hard hit stumbles (steering locked for stumbleTime, the combo goes on); a head-on
	 * hit that does not bail stops dead. Returns false when the skater bailed.
	 */
	private boolean scrapeRolling(float dx, float dy, float sdt, boolean steering)
	{
		findContact(dx, dy, true);
		// a wall ends any landing blend: the board scrapes along it as it points
		landSlip = 0f;
		float into = contactInto(dx, dy);
		float s = Math.abs(speed);
		if (bailsOnWall(s, into) && !atLoadedEdge())
		{
			bailAgainstWall();
			return false;
		}
		if (sinceWall > WALL_CONTACT_MEMORY)
		{
			if (s * into > t.wallStumbleSpeed)
			{
				stumbleTimer = t.stumbleTime;
				events.add(SkateEvent.STUMBLE);
			}
			speed *= 1f - t.wallContactLoss * into;
		}
		sinceWall = 0f;
		if (contactFreeDx == 0f && contactFreeDy == 0f)
		{
			speed = 0f;
			return true;
		}
		x += contactFreeDx;
		y += contactFreeDy;
		float along = (float) Math.atan2(contactFreeDx, contactFreeDy);
		speed = towardZero(speed, t.wallScrape * into * sdt);
		if (!steering)
		{
			// regular or fakie, whichever way along the wall the board already points
			heading = rotateToward(heading, closerHeading(along, heading), t.wallAlignRate * sdt);
		}
		return true;
	}

	/** Near the edge of the loaded area, where hits scrape instead of bailing. */
	private boolean atLoadedEdge()
	{
		return world.edgeDistance(x, y) < t.skaterRadius + EDGE_SCRAPE_MARGIN;
	}

	/** Bails off the current contact; the recovery later pushes the skater out along its normal. */
	private void bailAgainstWall()
	{
		bail(BailReason.WALL);
		vx = 0f;
		vy = 0f;
		rememberBailWall(contactNx, contactNy);
	}

	private void rememberBailWall(float nx, float ny)
	{
		bailWall = true;
		bailWallNx = nx;
		bailWallNy = ny;
	}

	/** {@code along} or its opposite, whichever is closer to {@code current}. */
	private static float closerHeading(float along, float current)
	{
		float opposite = Angles.wrap(along + Angles.PI);
		return Angles.absDiff(current, along) <= Angles.absDiff(current, opposite) ? along : opposite;
	}

	/** Eases `from` toward `to` by at most `maxStep` radians, without overshooting. */
	private static float rotateToward(float from, float to, float maxStep)
	{
		float diff = Angles.wrap(to - from);
		if (Math.abs(diff) <= maxStep)
		{
			return Angles.wrap(to);
		}
		return Angles.wrap(from + Math.copySign(maxStep, diff));
	}

	private static final float SPIN_ASSIST_WINDOW = (float) Math.toRadians(70);

	/**
	 * Leaves the ground (pop, roll-off or off a rail). A steer already held at that moment (a carve into
	 * the pop) is ignored in the air until it is released and pressed again, so it cannot spin an
	 * uncommitted ollie round into a bail. {@code cameraRef} is the camera heading on the ground just
	 * before: in the air the camera follows the flight, on whichever side of it the camera already was.
	 */
	private void takeOff(SkateInput in, float cameraRef)
	{
		airFakie = grounded() && ridingFakie();
		state = SkaterState.AIRBORNE;
		airSteerHeld = Math.abs(in.steer) >= STEER_DEADZONE;
		landSlip = 0f;
		airCameraRef = cameraRef;
		airCameraHeld = false;
		airTime = 0f;
		airSpin = 0f;
		airAutoSpin = 0f;
		bodyFlipAngle = 0f;
		flipForwardHeld = in.pushHeld;
		flipBackHeld = in.leanBack;
		leanForwardHeld = in.leanForwardKey;
		leanBackHeld = in.leanBackKey;
		rollOffFlight = false;
		lastLandingQuality = null;
	}

	/**
	 * Riding fakie (backwards) for trick names: after a backwards landing (the fakie flag), or rolling
	 * backwards faster than STALL_SPEED; a slow roll back on a hill is not fakie.
	 */
	private boolean ridingFakie()
	{
		return fakie || speed < -STALL_SPEED;
	}

	/** Board heading, or its opposite when riding fakie for real (faster than STALL_SPEED backwards). */
	private float rollingCameraHeading()
	{
		return speed < -STALL_SPEED ? Angles.wrap(heading + Angles.PI) : heading;
	}

	/**
	 * Front and back flips: Shift + W turns the whole skater forward, Shift + S backward, at BODY_FLIP_RATE
	 * while held. W or S alone does nothing here, and a W / S already held at take-off (a push or crouch
	 * into the pop) counts only once released and pressed again; Shift may be held from the ground. Released
	 * (or both held), the flip stops, easing onto a whole rotation when within BODY_FLIP_ASSIST_WINDOW of one.
	 * In controller mode the left stick sends the lean keys instead of W / S, so Shift + lean up / down turns
	 * the flip the same way (grab-flips: a lean key with a grab held turns it regardless of Shift or mode).
	 */
	private void updateBodyFlip(float dt, SkateInput in)
	{
		if (flipForwardHeld && !in.pushHeld)
		{
			flipForwardHeld = false;
		}
		if (flipBackHeld && !in.leanBack)
		{
			flipBackHeld = false;
		}
		if (leanForwardHeld && !in.leanForwardKey)
		{
			leanForwardHeld = false;
		}
		if (leanBackHeld && !in.leanBackKey)
		{
			leanBackHeld = false;
		}
		// grab-flips: a lean key with a grab held turns the same flip as Shift + W / Shift + S; in controller
		// mode, Shift + the lean stick (no grab needed) does too, since the pad has no separate W / S
		boolean grabbing = activeHold != null && activeHold.kind == TrickKind.GRAB;
		boolean leanFlips = grabbing || (in.controllerMode && in.powerslide);
		boolean forward = in.powerslide && in.pushHeld && !flipForwardHeld
			|| leanFlips && in.leanForwardKey && !leanForwardHeld;
		boolean back = in.powerslide && in.leanBack && !flipBackHeld
			|| leanFlips && in.leanBackKey && !leanBackHeld;
		float dir = (forward ? 1f : 0f) - (back ? 1f : 0f);
		if (dir != 0f)
		{
			bodyFlipAngle += dir * BODY_FLIP_RATE * dt;
			rollOffFlight = false;
			return;
		}
		float r = BodyFlip.residual(bodyFlipAngle);
		if (r != 0f && Math.abs(r) <= BODY_FLIP_ASSIST_WINDOW)
		{
			bodyFlipAngle -= Math.copySign(Math.min(Math.abs(r), BODY_FLIP_ASSIST_RATE * dt), r);
		}
	}

	/** Emits the front / back flip completed this air (if any) and resets the angle: on landing or a rail lock. */
	private void finishBodyFlip()
	{
		Trick trick = BodyFlip.trick(BodyFlip.rotations(bodyFlipAngle));
		bodyFlipAngle = 0f;
		if (trick != null)
		{
			trickEvents.add(TrickEvent.trick(trick));
		}
	}

	private void stepAir(float dt, SkateInput in)
	{
		airTricks(dt, in);
		updateBodyFlip(dt, in);
		airTime += dt;
		vh -= t.gravity * dt;
		if (airSteerHeld && Math.abs(in.steer) < STEER_DEADZONE)
		{
			airSteerHeld = false;
		}
		float steer = airSteerHeld ? 0f : in.steer;
		float headingBefore = heading;
		heading = Angles.wrap(heading + steer * t.airSpinRate * (in.spinFast ? SPIN_FAST_MULT : 1f) * dt);

		// no spin assist while a bigspin turns the body: it would fight the trick's own 180. Nor below
		// SLOW_LANDING_SPEED (the sliver of flight a wall hit leaves): such a landing ignores the travel direction,
		// and chasing the sliver turned the board (and the camera behind it) toward whichever way it pointed
		if (Math.abs(steer) < STEER_DEADZONE && !bodySpinning() && Math.hypot(vx, vy) >= SLOW_LANDING_SPEED)
		{
			float travel = Math.hypot(vx, vy) > 1 ? (float) Math.atan2(vx, vy) : heading;
			float travelFlip = Angles.wrap(travel + Angles.PI);
			float diffTravel = Angles.absDiff(heading, travel);
			float diffFlip = Angles.absDiff(heading, travelFlip);
			float target = diffTravel <= diffFlip ? travel : travelFlip;
			float diff = Math.min(diffTravel, diffFlip);
			if (diff <= SPIN_ASSIST_WINDOW)
			{
				float assisted = rotateToward(heading, target, t.spinAssistRate * dt);
				if (airCameraHeld)
				{
					// the assist turns the board onto the flight a wall left it with: the held camera turns
					// with it, so it is behind the board again on landing instead of snapping there
					airCameraHold = Angles.wrap(airCameraHold + Angles.wrap(assisted - heading));
				}
				heading = assisted;
			}
		}
		// per-step changes are far below PI, so the wrapped difference is the true signed turn
		airSpin += Angles.wrap(heading - headingBefore);

		float nx = x + vx * dt;
		float ny = y + vy * dt;
		if (blocked(nx, ny, false))
		{
			// running into the box of a rail while inside its lock window catches the rail rather than
			// bouncing off (or bailing on) the thing it sits on
			float hs0 = (float) Math.hypot(vx, vy);
			if (tryLockGrind(in, hs0 > 1 ? (float) Math.atan2(vx, vy) : heading, hs0, false))
			{
				return;
			}
			// in the air the velocity is projected onto the wall; the board's heading is left alone
			float dx = vx * dt;
			float dy = vy * dt;
			findContact(dx, dy, false);
			if (bailsOnWall((float) Math.hypot(vx, vy), contactInto(dx, dy)) && !atLoadedEdge())
			{
				bailAgainstWall();
				return;
			}
			// the camera keeps the heading it had before the wall: what is left of the flight along it (a sliver
			// on a near head-on hit, either way along the wall) said nothing about where the skater is going, and
			// following it swung the camera up to 90 degrees aside in the air and straight back on landing
			if (!airCameraHeld)
			{
				airCameraHold = getCameraHeading();
				airCameraHeld = true;
			}
			// the part of the move along the wall carries on (the velocity loses its part into it)
			nx = x + contactFreeDx;
			ny = y + contactFreeDy;
			vx = contactFreeDx / dt;
			vy = contactFreeDy / dt;
		}
		x = nx;
		y = ny;
		h += vh * dt;

		float hs = (float) Math.hypot(vx, vy);
		// rising or falling: the higher pop often carries the skater past a fence before it starts to fall
		if (tryLockGrind(in, hs > 1 ? (float) Math.atan2(vx, vy) : heading, hs, false))
		{
			return;
		}

		float g = world.groundHeight(x, y);
		if (h <= g)
		{
			land(g, in);
		}
	}

	/**
	 * Snaps onto the nearest qualifying grind segment and enters GRINDING (HOLD_START, no LANDED: the
	 * combo carries on). Checked while rising and falling. The velocity turns along the rail (see
	 * {@link #GRIND_LOCK_KEEP_FRACTION}). The type comes from the board heading versus the rail: straight grinds line the
	 * board up with the rail, crooked grinds and boardslides keep the approach heading. A flip less than
	 * {@link #FLIP_CATCH_FRACTION} done cannot lock on; a later one keeps turning on the rail, and a grab is
	 * released first (HOLD_END).
	 */
	private boolean tryLockGrind(SkateInput in, float travel, float hs, boolean fromRolling)
	{
		if (dropOffFlight || grindCooldown > 0f || hs < GRIND_LOCK_MIN_SPEED)
		{
			return false;
		}
		// the grind button held (button tricks) catches from further to the side; without it, as always
		float snap = in.grindHeld ? GrindMap.ASSIST_SNAP_DISTANCE : GrindMap.SNAP_DISTANCE;
		GrindMap.Hit hit = fromRolling ? grinds.nearest(x, y, h, travel, noRelockSeg, snap)
			: grinds.nearestInAir(x, y, h, vh, travel, noRelockSeg, risingRelockSeg, snap);
		if (hit == null)
		{
			return false;
		}
		GrindSegment seg = hit.segment;
		if (fromRolling && seg.topAt(hit.t) - h < ROLL_LOCK_MIN_RISE)
		{
			return false;
		}
		if (flipTrick != null && flipTime < FLIP_LOCK_FRACTION * flipDuration)
		{
			return false;
		}
		if (!BodyFlip.landable(bodyFlipAngle))
		{
			return false; // upside down or sideways mid front / back flip: no rail
		}
		finishCaughtFlip(0f); // clears a flip that is already done
		endHold();
		finishBodyFlip(); // a whole front / back flip finished onto the rail scores before the grind

		float segHeading = seg.heading();
		// redirect the velocity along the rail, keeping the sign of its along-rail component
		float along = hs * (float) Math.cos(Angles.absDiff(travel, segHeading));
		grindDir = along >= 0f ? 1f : -1f;
		if (Math.abs(along) < GrindMap.AMBIGUOUS_ALONG * hs)
		{
			// nearly square to the rail: the steer picks the way (right = the rail direction clockwise from
			// the travel), else the along sign; either way only where there is rail ahead
			if (Math.abs(in.steer) >= STEER_DEADZONE)
			{
				boolean cw = Angles.wrap(segHeading - travel) > 0f;
				grindDir = (in.steer > 0f) == cw ? 1f : -1f;
			}
			float ahead = grindDir > 0 ? (1f - hit.t) * seg.length() : hit.t * seg.length();
			if (ahead < GrindMap.MIN_RAIL_AHEAD)
			{
				grindDir = -grindDir;
			}
		}
		float boardAngle = seg.lineAngle(heading);
		if (boardAngle < CROOKED_MIN)
		{
			grindFamily = GrindFamily.STRAIGHT;
			// line the board up with the rail, keeping whichever way it was pointing (regular or fakie)
			heading = Angles.absDiff(heading, segHeading) <= Angles.PI / 2
				? Angles.wrap(segHeading) : Angles.wrap(segHeading + Angles.PI);
		}
		else if (boardAngle < BOARDSLIDE_MIN)
		{
			grindFamily = GrindFamily.CROOKED;
		}
		else
		{
			// how far the board has turned from the way it was riding into the air (fakie riding counts
			// backwards): past square it went the far way round, a lipslide
			float riding = airFakie ? travel + Angles.PI : travel;
			grindFamily = Angles.absDiff(heading, riding) > Angles.PI / 2 ? GrindFamily.LIP : GrindFamily.SLIDE;
		}
		Trick trick = grindFamily.trick(in);

		grindSeg = seg;
		noRelockSeg = null;
		risingRelockSeg = null;
		approachRight = approachSide(seg, hit.t, travel);
		grindPos = hit.t * seg.length();
		grindSpeed = Math.max(Math.abs(along), Math.max(GRIND_LOCK_KEEP_FRACTION * hs, GRIND_LOCK_MIN_RAIL_SPEED));
		x = seg.xAt(hit.t);
		y = seg.yAt(hit.t);
		h = seg.topAt(hit.t);
		vh = 0f;
		speed = 0f;
		state = SkaterState.GRINDING;
		startHold(trick);
		return true;
	}

	/**
	 * +1 when the skater, about to snap onto {@code seg} at parameter {@code t}, is on the right of the grind
	 * travel direction (seg heading, flipped when grindDir is -1), -1 when on the left. Exactly on the line
	 * it is the side the flight came from: the opposite of its sideways component.
	 */
	private float approachSide(GrindSegment seg, float t, float flight)
	{
		float rail = grindDir > 0 ? seg.heading() : seg.heading() + Angles.PI;
		float rx = (float) Math.cos(rail);
		float ry = (float) -Math.sin(rail);
		float side = (x - seg.xAt(t)) * rx + (y - seg.yAt(t)) * ry;
		if (Math.abs(side) < 1e-3f)
		{
			side = -((float) Math.sin(flight) * rx + (float) Math.cos(flight) * ry);
		}
		return side >= 0f ? 1f : -1f;
	}

	/**
	 * Slides along the rail at the current speed less {@link #GRIND_FRICTION}, sped up going down a sloped
	 * rail and slowed going up it as on a hill. W/S switch between the grinds of the current
	 * {@link GrindFamily} (and never push here). A pop gesture leaves with that trick; the rail end or
	 * dropping below {@link #GRIND_MIN_SPEED} leaves with a small hop. No balance bail.
	 */
	private void stepGrind(float dt, SkateInput in)
	{
		finishCaughtFlip(dt);
		Trick pop = popTrick(in.gestures);
		if (pop != null)
		{
			endHold();
			lastPopNollie = isNollie(pop);
			trickEvents.add(TrickEvent.trick(pop));
			if (pop.kind == TrickKind.FLIP)
			{
				startFlip(pop);
			}
			else
			{
				clearFlip(); // a plain pop: a flip still finishing from the last catch is done
			}
			// a pop off a rail is an uncharged ollie: tall enough to finish any flip before touchdown;
			// steering pushes it off to the side
			leaveGrind(in, t.ollieImpulse * t.minPopFraction, in.steer * GRIND_POP_SIDE_SPEED, true);
			events.add(SkateEvent.POP);
			return;
		}

		Trick wanted = grindFamily.trick(in);
		if (wanted != activeHold)
		{
			endHold();
			startHold(wanted);
		}
		holdTime += dt;

		// a sloped rail pulls like a hill does (the same scaled, clamped gravity as rolling)
		float slope = clamp(grindSeg.slope() * grindDir, -t.maxSlope, t.maxSlope);
		grindSpeed -= t.gravity * t.slopeGravityScale * slope * dt;
		// a long downhill rail tops out at the same cap as rolling
		grindSpeed = clamp(grindSpeed - GRIND_FRICTION * dt, 0f, t.maxSpeed);
		grindPos += grindDir * grindSpeed * dt;
		followConnectedSegments();
		float len = grindSeg.length();
		float u = grindPos / len;
		x = grindSeg.xAt(u);
		y = grindSeg.yAt(u);
		h = grindSeg.topAt(clamp(u, 0f, 1f));
		boolean slow = grindSpeed < GRIND_MIN_SPEED;
		if (grindPos < 0f || grindPos > len || slow)
		{
			endHold();
			float hop = clamp(GRIND_EXIT_POP_BASE + GRIND_EXIT_POP_PER_SPEED * grindSpeed, GRIND_EXIT_POP_MIN, GRIND_EXIT_POP_MAX);
			// steer picks the side to land on; a slow drop-off with no steer falls back to the side the rail
			// was approached from, never straight down beside it
			float side = Math.abs(in.steer) >= STEER_DEADZONE ? in.steer * GRIND_POP_SIDE_SPEED
				: slow ? approachRight * GRIND_DROP_SIDE_SPEED : 0f;
			leaveGrind(in, hop, side, false);
		}
	}

	/**
	 * Past the end of the current segment, carries the grind on to a connected segment (see
	 * {@link GrindMap#connectedAt}): the overshoot continues along the new segment, and the travel and the
	 * board turn by the bend. The hold (trick and time) carries on with no new HOLD_START. Stops at the
	 * first end with no continuation, leaving grindPos outside the segment so the caller leaves the rail.
	 */
	private void followConnectedSegments()
	{
		for (int hop = 0; hop < 8; hop++)
		{
			float len = grindSeg.length();
			float over = grindPos < 0f ? -grindPos : grindPos > len ? grindPos - len : 0f;
			if (over <= 0f)
			{
				return;
			}
			float endX = grindDir > 0 ? grindSeg.x1 : grindSeg.x0;
			float endY = grindDir > 0 ? grindSeg.y1 : grindSeg.y0;
			float travel = grindTravelHeading();
			GrindSegment next = grinds.connectedAt(endX, endY, grindSeg.topAt(grindDir > 0 ? 1f : 0f),
				(float) Math.sin(travel), (float) Math.cos(travel), grindSeg);
			if (next == null)
			{
				return;
			}
			boolean fromStart = Math.hypot(next.x0 - endX, next.y0 - endY) <= Math.hypot(next.x1 - endX, next.y1 - endY);
			grindSeg = next;
			grindDir = fromStart ? 1f : -1f;
			grindPos = fromStart ? over : next.length() - over;
			heading = Angles.wrap(heading + Angles.wrap(grindTravelHeading() - travel));
		}
	}

	/**
	 * Goes AIRBORNE along the rail at the grind speed with the given upward velocity, plus
	 * {@code sideSpeed} to the right of the travel direction (negative = left). A crooked grind or
	 * boardslide (board across the rail) snaps its heading to the closer of travel or travel + PI,
	 * since the landing tolerance would otherwise always bail it. After a pop ({@code popped}) the rail just
	 * left can be caught again once falling (a hop along the rail lands back on it); after a drop-off it
	 * cannot be re-caught until touchdown.
	 */
	private void leaveGrind(SkateInput in, float popVh, float sideSpeed, boolean popped)
	{
		float travel = grindTravelHeading();
		float side = travel + Angles.PI / 2;
		vx = (float) Math.sin(travel) * grindSpeed + (float) Math.sin(side) * sideSpeed;
		vy = (float) Math.cos(travel) * grindSpeed + (float) Math.cos(side) * sideSpeed;
		vh = popVh;
		if (grindFamily != GrindFamily.STRAIGHT)
		{
			heading = closerHeading(travel, heading);
		}
		noRelockSeg = popped ? null : grindSeg;
		dropOffFlight = !popped;
		risingRelockSeg = popped ? grindSeg : null;
		grindSeg = null;
		grindSpeed = 0f;
		grindCooldown = GRIND_RELOCK_COOLDOWN;
		takeOff(in, travel);
	}

	private float grindTravelHeading()
	{
		float segHeading = grindSeg.heading();
		return grindDir > 0 ? Angles.wrap(segHeading) : Angles.wrap(segHeading + Angles.PI);
	}

	/**
	 * Advances the flip in progress, starts flips from gestures or upgrades the flip in progress on a
	 * re-flick, and starts/ends grabs from the held Q/E keys. A late flick is sped up to finish by the
	 * estimated touchdown, down to LATE_FLIP_MIN_FRACTION of its own duration; any later it is ignored
	 * rather than started as a certain bail.
	 */
	private void airTricks(float dt, SkateInput in)
	{
		if (flipTrick != null)
		{
			flipTime = Math.min(flipDuration, flipTime + dt);
		}
		for (Gesture g : in.gestures)
		{
			boolean flipping = flipTrick != null && flipTime < flipDuration;
			Trick next = flipping ? TrickCatalog.upgrade(flipTrick, g) : TrickCatalog.forGesture(g);
			if (next == null || next.kind != TrickKind.FLIP)
			{
				continue; // nothing to upgrade to, or a plain pop gesture: no effect in the air
			}
			float airLeft = timeToGround();
			// carry the rotation already done over to the new flip's own scale (0 for a fresh flip), so the
			// roll does not jump: the new flip starts at the progress u whose eased roll matches it
			float u = flipping ? progressForRoll(next, flipTrick.rollTurns * TWO_PI * flipEased()) : 0f;
			float duration = Math.min(next.duration, airLeft / Math.max(1e-3f, 1f - u));
			if (duration < LATE_FLIP_MIN_FRACTION * next.duration)
			{
				continue; // too late to land: ignore the flick
			}
			Trick replaced = flipping ? flipTrick : null;
			flipTrick = next;
			rollOffFlight = false;
			flipDuration = duration;
			flipTime = u * duration;
			if (replaced == null)
			{
				flipAutoSpin = 0f;
			}
			trickEvents.add(replaced == null ? TrickEvent.trick(next, airFakie)
				: TrickEvent.upgrade(next, replaced, airFakie));
		}
		applyBodySpin();

		if (activeHold != null)
		{
			boolean held = grabLeftHand ? in.grabLeft : in.grabRight;
			if (held)
			{
				holdTime += dt;
				aimAndTweakGrab(in);
			}
			else
			{
				endHold();
			}
		}
		else if (in.grabLeft || in.grabRight)
		{
			// no aim: Q is an Indy and E a Melon, as before directional grabs
			grabLeftHand = in.grabLeft;
			grabAimed = in.grabAim != null;
			grabTweaked = false;
			startHold(Grabs.pick(grabLeftHand, in.grabAim));
			rollOffFlight = false;
			holdTime += dt;
		}
	}

	/**
	 * The grab held: the first aim within Grabs.AIM_SECONDS of the press renames it (a HOLD_START for the new
	 * grab, the hold time carrying on), and held past Grabs.TWEAK_SECONDS it turns into its tweak once.
	 */
	private void aimAndTweakGrab(SkateInput in)
	{
		Trick grab = activeHold;
		if (!grabAimed && in.grabAim != null && holdTime <= Grabs.AIM_SECONDS + HOLD_TIME_EPSILON)
		{
			grabAimed = true;
			grab = Grabs.pick(grabLeftHand, in.grabAim);
		}
		if (!grabTweaked && holdTime > Grabs.TWEAK_SECONDS + HOLD_TIME_EPSILON)
		{
			grabTweaked = true;
			Trick tweak = Grabs.tweaked(grab);
			grab = tweak != null ? tweak : grab;
		}
		if (grab != activeHold)
		{
			activeHold = grab;
			trickEvents.add(TrickEvent.holdStart(grab));
		}
	}

	private void land(float g, SkateInput in)
	{
		dropOffFlight = false;
		noRelockSeg = null;
		risingRelockSeg = null;
		h = g;
		lastLandingSpeed = Math.max(0f, -vh);
		vh = 0f;
		float fx = (float) Math.sin(heading);
		float fy = (float) Math.cos(heading);
		float hs = (float) Math.hypot(vx, vy);
		if (hs < SLOW_LANDING_SPEED)
		{
			speed = fx * vx + fy * vy;
			landSlip = 0f;
		}
		else
		{
			float travel = (float) Math.atan2(vx, vy);
			float diff = Angles.absDiff(heading, travel);
			float tol = (float) Math.toRadians(t.landingToleranceDeg);
			if (diff <= tol)
			{
				speed = hs;
				// keep travelling where the skater was going; the travel turns onto the board over LAND_BLEND_TIME
				landSlip = Angles.wrap(travel - heading);
			}
			else if (diff >= Angles.PI - tol)
			{
				speed = -hs;
				landSlip = Angles.wrap(travel - heading - Angles.PI);
			}
			else
			{
				bail(BailReason.forLanding(true, false, false));
				return;
			}
		}
		BailReason flipBail = BailReason.forLanding(false, flipUncatchable(), !BodyFlip.landable(bodyFlipAngle));
		if (flipBail != null)
		{
			// a board flip not caught, or the body more than 40 degrees off a whole front / back flip
			bail(flipBail);
			return;
		}
		finishCaughtFlip(0f);
		int spinHalfTurns = SpinNames.halfTurns(namedAirSpin());
		if (spinHalfTurns != 0)
		{
			rollOffFlight = false; // a spun roll-off is a trick
		}
		// a plain roll-off (no trick in the air) has no quality; otherwise clean = lined up and the flip done
		lastLandingQuality = rollOffFlight ? null
			: Math.abs(landSlip) <= CLEAN_LANDING_ANGLE && flipTrick == null ? LandingQuality.CLEAN : LandingQuality.SLOPPY;
		// the flip is caught; one not quite done keeps turning on the ground (finishCaughtFlip) instead of
		// snapping to neutral
		// the air grab, if its key is still held: after its release the skater keeps it as a rolling grab
		Trick keptGrab = activeHold != null && activeHold.kind == TrickKind.GRAB
			&& (grabLeftHand ? in.grabLeft : in.grabRight) ? activeHold : null;
		endHold(); // a grab auto-releases on touchdown, no bail
		if (spinHalfTurns != 0)
		{
			// named before the LANDED (or the manual's HOLD_START) so the scorer renames this air's trick
			trickEvents.add(TrickEvent.spin(spinHalfTurns));
		}
		finishBodyFlip(); // its own combo entry, after the spin
		// a near-vertical landing (below SLOW_LANDING_SPEED, e.g. dropping back off a wall hit in the air) is
		// never fakie: its speed is a sliver either way, and a fakie flag there made every W push go backwards,
		// pinning the skater against the wall it had just bounced off
		fakie = speed < 0f && hs >= SLOW_LANDING_SPEED;
		landSlipRate = Math.abs(landSlip) / LAND_BLEND_TIME;
		lastAirtime = airTime;
		state = SkaterState.ROLLING;
		events.add(SkateEvent.LAND);
		Trick manual = wantedManual(in);
		if (manual != null)
		{
			// landed clean with the manual key held: straight into a manual, and the combo carries on
			// (no LANDED; releasing the key rolls out and the scorer resolves the combo then)
			state = SkaterState.MANUAL;
			startHold(manual);
			return;
		}
		trickEvents.add(TrickEvent.landed(lastLandingQuality == LandingQuality.CLEAN));
		if (keptGrab != null)
		{
			// pose only (no hold, nothing scored): the body settles from the air grab into the rolling one
			groundGrab = keptGrab;
			groundGrabTime = Grabs.AIM_SECONDS + 1f;
			grabAimed = true;
		}
	}

	private void stepBailed(float dt, SkateInput in)
	{
		bailTimer = Math.max(0f, bailTimer - dt);
		recoverTimer -= dt;
		resetQueued |= in.resetRequested;
		float hs = (float) Math.hypot(vx, vy);
		if (hs > 0f)
		{
			float ns = Math.max(0f, hs - t.bailFriction * dt);
			vx *= ns / hs;
			vy *= ns / hs;
		}
		float nx = x + vx * dt;
		float ny = y + vy * dt;
		if (blocked(nx, ny, false))
		{
			// slid into something: remember it so the recovery faces away from it
			float d = (float) Math.hypot(vx, vy);
			if (d > 0f)
			{
				rememberBailWall(-vx / d, -vy / d);
			}
			vx = 0f;
			vy = 0f;
		}
		else
		{
			x = nx;
			y = ny;
		}
		// a bail above the ground (a wall hit in the air, sliding off a ledge) falls under gravity; it used to be
		// put on the ground in one step, a snap of up to the whole pop height under the bail animation
		float g = world.groundHeight(x, y);
		if (h > g)
		{
			vh -= t.gravity * dt;
			h = Math.max(g, h + vh * dt);
		}
		else
		{
			h = g;
		}
		if (h == g)
		{
			vh = 0f;
		}

		if ((bailTimer == 0f && resetQueued) || recoverTimer <= 0f)
		{
			if (bailWall)
			{
				recoverFromWall();
			}
			state = SkaterState.ROLLING;
			speed = 0f;
			vx = 0f;
			vy = 0f;
			rampRun = 0f;
			events.add(SkateEvent.RESET);
		}
	}

	/**
	 * Gets back on facing along the wall bailed against, whichever way is nearer the heading before the bail
	 * (a turn of 90 degrees at most, so the chase camera never swings round 180), nudged off it so the skater
	 * is not wedged.
	 */
	private void recoverFromWall()
	{
		bailWall = false;
		float along = Angles.wrap((float) Math.atan2(bailWallNx, bailWallNy) + Angles.PI / 2);
		heading = closerHeading(along, heading);
		for (float d = BAIL_PUSH_OUT; d > 0f; d -= GROUND_SUBSTEP)
		{
			float px = x + bailWallNx * d;
			float py = y + bailWallNy * d;
			if (!blocked(px, py, false))
			{
				x = px;
				y = py;
				break;
			}
		}
		// still overlapping a shape (a deep or slanted bail): out along its own normal, just clear of it
		if (world.contact(x, y, t.skaterRadius, h, t.maxStepUp, contactHit))
		{
			x += contactHit.nx * (contactHit.depth + 0.5f);
			y += contactHit.ny * (contactHit.depth + 0.5f);
		}
		h = world.groundHeight(x, y);
	}

	/** R while rolling slowly: stop, standing on the board, out of anything the skater is wedged against. */
	private void standStill()
	{
		speed = 0f;
		fakie = false;
		landSlip = 0f;
		// a ramp needs a fresh run-up after a reset
		rampRun = 0f;
		crouchTime = 0f;
		clearFlip();
		if (world.contact(x, y, t.skaterRadius, h, t.maxStepUp, contactHit))
		{
			x += contactHit.nx * (contactHit.depth + 0.5f);
			y += contactHit.ny * (contactHit.depth + 0.5f);
			h = world.groundHeight(x, y);
		}
		events.add(SkateEvent.RESET);
	}

	/**
	 * True when moving from the current position to (nx, ny) hits a blocker taller than a step, or the
	 * ground rises more than {@link SkateTuning#maxStepUp}. On the ground ({@code gradient}) a rise also has
	 * to be steeper than {@link #MAX_GROUND_GRADIENT} over the (substep) distance, so a steep hill is climbed
	 * rather than hit like a wall.
	 */
	private boolean blocked(float nx, float ny, boolean gradient)
	{
		if (world.blockerTop(x, y, nx, ny, t.skaterRadius) > h + t.maxStepUp)
		{
			return true;
		}
		float rise = world.groundHeight(nx, ny) - h;
		if (rise <= t.maxStepUp)
		{
			return false;
		}
		return !gradient || rise > MAX_GROUND_GRADIENT * (float) Math.hypot(nx - x, ny - y);
	}

	/**
	 * A ground move to (nx, ny) that {@link #blocked} refused: true (and the speed paid) when it was refused only
	 * for the terrain itself rising more than maxStepUp, and the skater is fast enough to carry up it. Blockers,
	 * platforms, landable boxes and anything past the loaded area (or at an infinite height) stay walls.
	 */
	private boolean momentumClimb(float nx, float ny)
	{
		if (world.blockerTop(x, y, nx, ny, t.skaterRadius) > h + t.maxStepUp || !(world.edgeDistance(nx, ny) > 0f))
		{
			return false;
		}
		float g = world.groundHeight(nx, ny);
		float rise = g - h;
		if (g != world.terrainHeight(nx, ny) || !(rise < Float.POSITIVE_INFINITY)
			|| Math.abs(speed) < t.momentumClimbSpeedPerRise * rise)
		{
			return false;
		}
		// v^2 - 2 g rise: gravity's work on the climb (the threshold keeps this well above zero)
		float v2 = speed * speed - 2f * t.gravity * t.momentumClimbGravityScale * rise;
		speed = Math.copySign((float) Math.sqrt(Math.max(0f, v2)), speed);
		return true;
	}

	/**
	 * A drop to ground {@code g} at the current position, from (px, py), that would roll off: true when it is a
	 * terrain slope (both ends bare terrain, not a platform or box edge) no steeper than dropInMaxGradient, which
	 * the board follows down instead.
	 */
	private boolean dropIn(float px, float py, float g)
	{
		float run = (float) Math.hypot(x - px, y - py);
		return run > 0f
			&& h - g <= t.dropInMaxGradient * run
			&& g == world.terrainHeight(x, y)
			&& h == world.terrainHeight(px, py);
	}

	/**
	 * Vertical speed to leave the ground with over a crest (0 to stay down): rolling uphill fast on bare terrain
	 * (never on a platform or box top), with the terrain slope just ahead dropping by rampLaunchSlopeDrop or more
	 * from the approach. Slopes are sampled in the direction of travel over SLOPE_SAMPLE * 2 behind and ahead of
	 * the skater.
	 */
	private float rampLaunch(float dt)
	{
		if (h != world.terrainHeight(x, y))
		{
			// on a platform or landable box: its flat top is no ramp, whatever the terrain underneath does
			return 0f;
		}
		float dir = speed < 0f ? -1f : 1f;
		float tx = dir * (float) Math.sin(heading + landSlip);
		float ty = dir * (float) Math.cos(heading + landSlip);
		float d = 2 * SLOPE_SAMPLE;
		float here = world.terrainHeight(x, y);
		float approach = (here - world.terrainHeight(x - tx * d, y - ty * d)) / d;
		if (!(approach >= t.rampLaunchMinSlope))
		{
			rampRun = 0f;
			return 0f;
		}
		// a ramp needs a run-up: a lone steep tile (rough ground) is a bump, not a ramp
		boolean runUp = rampRun >= t.rampLaunchMinRun;
		rampRun += Math.abs(speed) * dt;
		if (!runUp || Math.abs(speed) < t.rampLaunchSpeedFraction * t.maxPushSpeed)
		{
			return 0f;
		}
		float ahead = (world.terrainHeight(x + tx * d, y + ty * d) - here) / d;
		if (!(approach - ahead >= t.rampLaunchSlopeDrop))
		{
			return 0f;
		}
		// a bank climbed on momentum can be far steeper than any hill: launch at most as a maxSlope ramp would
		return Math.abs(speed) * Math.min(approach, t.maxSlope);
	}

	private void bail(BailReason reason)
	{
		lastBailReason = reason;
		// a hold cut short by a bail still reports its length before the BAILED that ends the combo
		endHold();
		clearFlip();
		dropOffFlight = false;
		noRelockSeg = null;
		risingRelockSeg = null;
		if (grounded())
		{
			vx = (float) Math.sin(heading + landSlip) * speed;
			vy = (float) Math.cos(heading + landSlip) * speed;
		}
		state = SkaterState.BAILED;
		bailTimer = t.bailDuration;
		recoverTimer = t.bailDuration + t.bailAutoReset;
		resetQueued = false;
		vh = 0f;
		speed = 0f;
		crouchTime = 0f;
		fakie = false;
		stumbleTimer = 0f;
		landSlip = 0f;
		bailWall = false;
		lastLandingQuality = null;
		bodyFlipAngle = 0f;
		events.add(SkateEvent.BAIL);
		trickEvents.add(TrickEvent.bailed());
	}

	/** The latest bail was into a wall (until the recovery): {@link #getBailWallNx()} is its outward normal. */
	public boolean isBailWall()
	{
		return state == SkaterState.BAILED && bailWall;
	}

	public float getBailWallNx()
	{
		return bailWallNx;
	}

	public float getBailWallNy()
	{
		return bailWallNy;
	}

	/** Horizontal velocity, u/s: meaningful in the air and bailed only. Read-only, for the knockdown. */
	public float getVelocityX()
	{
		return vx;
	}

	public float getVelocityY()
	{
		return vy;
	}

	/**
	 * Knockdown: keeps a bailed skater that is no longer stepped where the knocked-off body is, moving as it moves,
	 * for whatever still reads a skater meanwhile (party ghosts, the edge hint). Not for a skater being stepped.
	 */
	public void placeBailed(float x, float y, float h, float vx, float vy, float vh)
	{
		this.x = x;
		this.y = y;
		this.h = h;
		this.vx = vx;
		this.vy = vy;
		this.vh = vh;
	}

	/** Why the latest bail happened (kept after recovery); null before any bail. */
	public BailReason getLastBailReason()
	{
		return lastBailReason;
	}

	/** ROLLING or MANUAL: on the ground, moving by signed {@code speed} along the heading. */
	private boolean grounded()
	{
		return state == SkaterState.ROLLING || state == SkaterState.MANUAL;
	}

	public List<SkateEvent> drainEvents()
	{
		List<SkateEvent> out = new ArrayList<>(events);
		events.clear();
		return out;
	}

	public List<TrickEvent> drainTrickEvents()
	{
		List<TrickEvent> out = new ArrayList<>(trickEvents);
		trickEvents.clear();
		return out;
	}

	/** Extra board roll in radians from the current flip (+ = kickflip direction). */
	public float getBoardRoll()
	{
		return flipTrick == null ? 0f : flipTrick.rollTurns * TWO_PI * flipEased();
	}

	/**
	 * Extra board yaw in radians relative to the heading, from shove-its (+ = clockwise from above). During a
	 * bigspin the heading itself turns with the body, so this is the board's world turn less the body's.
	 */
	public float getBoardYawOffset()
	{
		return flipTrick == null ? 0f : flipTrick.yawTurns * TWO_PI * flipEased() - flipAutoSpin;
	}

	/**
	 * Board pitch in radians, nose up > 0: manuals, nosegrinds and 5-0s, plus an impossible's end-over-end
	 * wrap (up to 2 pi, nose up and over) while it turns.
	 */
	public float getBoardPitch()
	{
		float flipPitch = flipTrick == null ? 0f : flipTrick.pitchTurns * TWO_PI * flipEased();
		return flipPitch + boardPitchFor(state, activeHold);
	}

	/**
	 * Board pitch in radians (nose up > 0) of a skater in {@code state} holding {@code hold}, without any flip
	 * pitch; see {@link #getBoardPitch}. Shared with party ghosts.
	 */
	public static float boardPitchFor(SkaterState state, Trick hold)
	{
		if (state == SkaterState.GRINDING)
		{
			return hold == Trick.NOSEGRIND ? -GRIND_PITCH : hold == Trick.FIVE_O ? GRIND_PITCH : 0f;
		}
		if (state != SkaterState.MANUAL)
		{
			return 0f;
		}
		return hold == Trick.NOSE_MANUAL ? -MANUAL_PITCH : MANUAL_PITCH;
	}

	/** The flip turning the board now (null when none), for sharing with the party. */
	public Trick getFlipTrick()
	{
		return flipTrick;
	}

	/** Progress 0..1 of the current flip (0 when none). */
	public float getFlipProgress()
	{
		return flipTrick == null || flipDuration <= 0f ? 0f : clamp(flipTime / flipDuration, 0f, 1f);
	}

	/**
	 * Signed body yaw (radians, + = clockwise from above) accumulated since the latest take-off: steering,
	 * the spin assist and a bigspin's automatic body turn. Kept after landing until the next take-off.
	 * Read-only, for the renderer and HUD.
	 */
	public float getAirSpin()
	{
		return airSpin;
	}

	/**
	 * Front / back flip angle of the whole skater and board about the centre of mass, radians: + = frontflip
	 * (forward pitch), - = backflip. Accumulates through the air (2 pi per whole flip) and returns to 0 on
	 * landing, a rail lock or a bail; always 0 on the ground and on rails. Read-only, for the renderer.
	 */
	public float getBodyFlipAngle()
	{
		return bodyFlipAngle;
	}

	/** The part of {@link #airSpin} that is named as a spin (a bigspin's own body turn is part of that trick). */
	private float namedAirSpin()
	{
		return airSpin - airAutoSpin;
	}

	/** True while the current flip is still turning the body itself (a bigspin's 180). */
	private boolean bodySpinning()
	{
		return flipTrick != null && flipTrick.bodyYawTurns != 0f && flipTime < flipDuration;
	}

	/**
	 * In the air: turns the heading by the current flip's body turn ({@link Trick#bodyYawTurns}, eased like the
	 * board) so far, counting it in the air spin but not in the named spin.
	 */
	private void applyBodySpin()
	{
		if (flipTrick == null || flipTrick.bodyYawTurns == 0f)
		{
			return;
		}
		float target = flipTrick.bodyYawTurns * TWO_PI * flipEased();
		float delta = target - flipAutoSpin;
		flipAutoSpin = target;
		heading = Angles.wrap(heading + delta);
		airSpin += delta;
		airAutoSpin += delta;
	}

	/** Current grab, manual or grind, or null. */
	public Trick getActiveHold()
	{
		return activeHold;
	}

	/**
	 * The rolling grab held on the ground (pose only, never scored), or null: the grab the body crouches into while
	 * ROLLING with a grab key held.
	 */
	public Trick getGroundGrab()
	{
		return state == SkaterState.ROLLING ? groundGrab : null;
	}

	/** The latest grab is (or was) held with the left hand (Q), not the right (E). */
	public boolean isGrabLeftHand()
	{
		return grabLeftHand;
	}

	/**
	 * Quality of the latest landing: CLEAN when the board touched down within 15 degrees of the travel (either
	 * way round) with its flip finished, else SLOPPY. Null before any landing, while airborne, after a bail and
	 * after a plain roll-off (no trick in the air). For the HUD; the scorer reads the LANDED event's own flag.
	 */
	public LandingQuality getLastLandingQuality()
	{
		return lastLandingQuality;
	}

	/** Step and horizon of the flight prediction in {@link #getPredictedGrind()} (the session's fixed step). */
	private static final float PREDICT_STEP = 0.02f;
	private static final int PREDICT_MAX_STEPS = 100;

	/**
	 * While airborne, the rail this flight will lock onto if nothing changes: the current flight is stepped
	 * forward as {@link #step} would (gravity, relock cooldown, flip progress, the same lock query) until a rail
	 * would catch it (returned) or it would touch down (null). Walls, steering and new tricks are not foreseen.
	 * Null when not airborne. Read-only, for the HUD's rail highlight.
	 */
	public GrindSegment getPredictedGrind()
	{
		float hs = (float) Math.hypot(vx, vy);
		if (state != SkaterState.AIRBORNE || dropOffFlight || hs < GRIND_LOCK_MIN_SPEED)
		{
			return null;
		}
		float travel = hs > 1 ? (float) Math.atan2(vx, vy) : heading;
		float px = x;
		float py = y;
		float ph = h;
		float pvh = vh;
		float cooldown = grindCooldown;
		float ft = flipTime;
		for (int k = 0; k < PREDICT_MAX_STEPS; k++)
		{
			cooldown = Math.max(0f, cooldown - PREDICT_STEP);
			if (flipTrick != null)
			{
				ft = Math.min(flipDuration, ft + PREDICT_STEP);
			}
			pvh -= t.gravity * PREDICT_STEP;
			px += vx * PREDICT_STEP;
			py += vy * PREDICT_STEP;
			ph += pvh * PREDICT_STEP;
			if (cooldown <= 0f && (flipTrick == null || ft >= FLIP_LOCK_FRACTION * flipDuration))
			{
				GrindMap.Hit hit = grinds.nearestInAir(px, py, ph, pvh, travel, noRelockSeg, risingRelockSeg);
				if (hit != null)
				{
					return hit.segment;
				}
			}
			if (ph <= world.groundHeight(px, py))
			{
				return null;
			}
		}
		return null;
	}

	/** Eased progress of the current flip, 0..1. */
	private float flipEased()
	{
		return eased(flipTime / flipDuration);
	}

	/**
	 * Ease-out 1 - (1 - u)^2 of a progress fraction (clamped to 0..1): the board leaves the foot fast and
	 * slows into the catch. Smoothstep turned only 5% in the first 50 ms and was 84% round at the 75%
	 * catch point, so a caught flip jumped 58 degrees; ease-out is 94% round there.
	 */
	private static float eased(float u)
	{
		u = clamp(u, 0f, 1f);
		return 1f - (1f - u) * (1f - u);
	}

	/** The flip ease-out of the local board (see {@link #eased}), for drawing other skaters' flips the same way. */
	public static float flipEase(float u)
	{
		return eased(u);
	}

	/**
	 * On a re-flick upgrade (e.g. KICKFLIP -> DOUBLE_KICKFLIP) the new trick has a different rollTurns,
	 * so keeping the progress literally would jump the board roll. This is the progress u of {@code next}
	 * whose eased roll equals {@code targetRoll}: the inverse of the ease-out, u = 1 - sqrt(1 - e).
	 */
	private static float progressForRoll(Trick next, float targetRoll)
	{
		if (next.rollTurns == 0f)
		{
			return 0f;
		}
		float e = clamp(targetRoll / (next.rollTurns * TWO_PI), 0f, 1f);
		return 1f - (float) Math.sqrt(1f - e);
	}

	public float getX()
	{
		return x;
	}

	public float getY()
	{
		return y;
	}

	public float getH()
	{
		return h;
	}

	public float getHeading()
	{
		return heading;
	}

	/** True when a rail is near the skater (see {@link #RAIL_NEAR_DISTANCE}): the grind button grinds there. */
	public boolean isRailNear()
	{
		return grinds.railWithin(x, y, h, RAIL_NEAR_DISTANCE, RAIL_NEAR_ABOVE);
	}

	public SkaterState getState()
	{
		return state;
	}

	/** Signed speed along heading while rolling or in a manual; rail speed while grinding; horizontal speed when airborne or bailed. */
	public float getSpeed()
	{
		if (state == SkaterState.GRINDING)
		{
			return grindSpeed;
		}
		return grounded() ? speed : (float) Math.hypot(vx, vy);
	}

	/**
	 * Heading for the chase camera to follow: the board heading while rolling (so stalling and rolling back
	 * slowly on a hill, or pivoting at a standstill, does not spin the camera round), its opposite only when
	 * riding fakie faster than 400 u/s; along the rail while grinding; along the flight in the air (on the
	 * same side as the camera was at take-off, so spins and slow roll-back pops do not swing it), held where it
	 * was once a wall hit in the air has cut the flight down to what is left along the wall.
	 */
	public float getCameraHeading()
	{
		switch (state)
		{
			case GRINDING:
				return grindTravelHeading();
			case AIRBORNE:
				if (airCameraHeld)
				{
					return airCameraHold;
				}
				return Math.hypot(vx, vy) > 1 ? closerHeading((float) Math.atan2(vx, vy), airCameraRef) : airCameraRef;
			case BAILED:
				return heading;
			default:
				return rollingCameraHeading();
		}
	}

	/** Seconds in the air of the flight that ended in the latest landing (for the camera's landing dip). */
	public float getLastAirtime()
	{
		return lastAirtime;
	}

	/** Direction of actual movement (heading when stationary). */
	public float getTravelHeading()
	{
		if (state == SkaterState.GRINDING)
		{
			return grindTravelHeading();
		}
		if (grounded())
		{
			float motion = heading + landSlip;
			return Angles.wrap(speed < 0 ? motion + Angles.PI : motion);
		}
		return Math.hypot(vx, vy) > 1 ? (float) Math.atan2(vx, vy) : heading;
	}

	/** True when the latest pop (from the ground or a rail) was a nollie-family trick, popped off the nose. */
	public boolean isLastPopNollie()
	{
		return lastPopNollie;
	}

	/** Pop charge 0..1 being wound up on the ground (crouch or wind-up held); 0 off the ground. Read-only, for the renderer. */
	public float getPopCharge()
	{
		return grounded() ? clamp(crouchTime / t.crouchChargeTime, 0f, 1f) : 0f;
	}

	/** Vertical speed, u/s (up > 0): non-zero only in the air. Read-only, for the renderer. */
	public float getVerticalVelocity()
	{
		return vh;
	}

	/** Downward speed (u/s, >= 0) at the latest landing on the ground. Read-only, for the renderer. */
	public float getLastLandingSpeed()
	{
		return lastLandingSpeed;
	}

	void setSpeed(float s)
	{
		speed = s;
	}

	/**
	 * Off-board mode: a freshly made skater (still ROLLING) gets on rolling forward at {@code s} u/s, e.g. a
	 * jump-mount carrying the jump's speed.
	 */
	public void setRollingSpeed(float s)
	{
		speed = s;
	}

	/**
	 * Off-board mode: keeps a freshly made, standing skater (never stepped) at the walker, for whatever still
	 * reads a skater while on foot (party ghosts, effects). Not for a skater being stepped.
	 */
	public void placeStanding(float x, float y, float h, float heading)
	{
		this.x = x;
		this.y = y;
		this.h = h;
		this.heading = Angles.wrap(heading);
	}

	/** Vertical speed in the air, u/s (up > 0). For tests. */
	float getVerticalSpeed()
	{
		return vh;
	}

	private static float towardZero(float v, float amount)
	{
		if (v > 0)
		{
			return Math.max(0f, v - amount);
		}
		return Math.min(0f, v + amount);
	}

	private static float clamp(float v, float lo, float hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
