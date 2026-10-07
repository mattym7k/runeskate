package com.gielinorskate.render;

import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;

/**
 * Procedural body motion on top of a constant stance animation (OSRS animations cannot blend, so every
 * animation switch is a hard cut; these channels move continuously instead). Pure: feed it one
 * {@link Signals} sample per frame and read the result with {@link #writeTo}. Every channel is a
 * {@link CriticalSpring}, so nothing jumps in one frame whatever the frame rate.
 *
 * <ul>
 * <li>lean into carves: roll toward the inside of the turn, from the centripetal acceleration
 * speed * turn rate (a Shift tight carve turns faster, so it leans further);</li>
 * <li>crouch: knees bend with the pop charge, a little on rails;</li>
 * <li>pop: a quick extension, then a tuck (deeper during flips and grabs), extending again when falling
 * toward the ground;</li>
 * <li>landing: a compression kick scaled by the landing speed that springs back in about 0.25 s;</li>
 * <li>manuals and pitched grinds: the body tips with the board about the contact truck; a balance sway
 * on rails and in manuals;</li>
 * <li>grabs: the body tucks and folds over the board and the grabbing hand reaches for its part of it
 * ({@link GrabPose}, the arm moved by {@link MeshDeformer}); rolling with a grab held (a grab hold while ROLLING, the
 * physics' ground grab) it is a low crouch with the hand down on the board, which stays on the ground;</li>
 * <li>bail: a continuous fall onto the chest and a smooth get-up.</li>
 * </ul>
 */
public final class SkaterPoseRig
{
	/** Mutable per-frame input, reused to avoid allocations. */
	public static final class Signals
	{
		public SkaterState state = SkaterState.ROLLING;
		/** Signed ground speed (negative riding fakie), u/s. */
		public float speed;
		/** Heading change rate, rad/s, clockwise > 0. */
		public float carveRate;
		/** Pop charge 0..1. */
		public float charge;
		/** Vertical speed in the air, u/s, up > 0. */
		public float verticalSpeed;
		/** Board pitch, radians (nose up > 0). */
		public float boardPitch;
		public Trick hold;
		/** The board is mid flip or shove-it. */
		public boolean flipping;
		/** Events of this frame. */
		public boolean popped;
		public boolean rolledOff;
		public boolean landed;
		public boolean bailed;
		/** Downward speed at this frame's landing, u/s. */
		public float landingSpeed;
		/** Fall procedurally on a bail (no bail animation is playing). */
		public boolean proceduralBail = true;
		/** A push (SkateEvent.PUSH) this frame. */
		public boolean pushed;
		/** Push procedurally (no push animation is playing). */
		public boolean proceduralPush = true;
		/**
		 * Goofy stance (right foot forward): mirrors the push. The renderer draws regular stance only for now;
		 * set this together with a heading - 90 degree puppet orientation.
		 */
		public boolean goofy;
		/**
		 * Front/back flip of skater and board about the centre of mass, radians, + = front flip (head toward
		 * the nose). Raw, not wrapped.
		 */
		public float bodyFlipAngle;
		/**
		 * Walking, not skating (FootBody fills the rest): a jump tucks lightly ({@link #FOOT_JUMP_TUCK}), a fall
		 * barely bends the knees.
		 */
		public boolean onFoot;
		/**
		 * The grab key's hand: +1 the left (Q), -1 the right (E), 0 not known (a party ghost). Each grab is drawn
		 * with its own hand ({@link GrabPose#leftHand}); only a tailgrab, either hand's, follows the key.
		 */
		public int grabHand;
	}

	/** The game gravity the lean is measured against (SkateTuning.gravity default). */
	static final float LEAN_GRAVITY = 2000f;
	/** Fraction of the physical lean angle atan(v w / g): the full angle looked like falling over. */
	static final float LEAN_GAIN = 0.6f;
	static final float MAX_LEAN = 0.5f;
	/** Carve rates above this are heading snaps (a reset or rail lock), not carving. */
	static final float MAX_CARVE_RATE = 8f;
	static final float LEAN_OMEGA = 9f;

	/** Body pitch as a fraction of the board's manual/grind pitch. */
	static final float BODY_PITCH_FRACTION = 0.7f;
	static final float PITCH_OMEGA = 10f;

	/** Crouch amounts: 1 - legScale. Negative extends the legs. */
	static final float CHARGE_CROUCH = 0.3f;
	static final float GRIND_CROUCH = 0.14f;
	static final float POP_EXTEND = -0.18f;
	/** Seconds after a pop the legs stay extended before tucking. */
	static final float POP_EXTEND_TIME = 0.12f;
	/** Crouch velocity (1/s) given at the pop, so the legs snap straight from a deep charge crouch. */
	static final float POP_KICK = -8f;
	static final float AIR_TUCK = 0.26f;
	static final float TRICK_TUCK = 0.38f;
	static final float ROLL_OFF_TUCK = 0.1f;
	/** On foot: a jump's knee tuck, and a fall's. */
	static final float FOOT_JUMP_TUCK = 0.2f;
	static final float FOOT_FALL_TUCK = 0.05f;
	/** Legs reach for the ground (nearly straight) when falling faster than this with no trick going. */
	static final float LAND_REACH_SPEED = 350f;
	static final float LAND_REACH = 0.05f;
	static final float BAIL_CROUCH = 0.25f;
	static final float MIN_CROUCH = -0.2f;
	static final float MAX_CROUCH = 0.5f;
	/** About 4 / omega = 0.2 s to settle: quick enough for the pop extension to read. */
	static final float CROUCH_OMEGA = 20f;
	static final float TORSO_BEND_PER_CROUCH = 0.9f;

	/** A full-charge ollie lands at about its take-off speed: ollieImpulse 820 u/s. */
	static final float LANDING_REF_SPEED = 820f;
	static final float LANDING_COMPRESSION = 0.22f;
	static final float MIN_LANDING_COMPRESSION = 0.04f;
	static final float MAX_LANDING_COMPRESSION = 0.3f;
	static final float GRIND_LOCK_COMPRESSION = 0.1f;
	/**
	 * The landing compression's own spring: peaks 1 / 14 = 0.07 s after touchdown and is back to 30% of
	 * the peak by 0.25 s ((1 + 3.5) e^-3.5 / e^-1 = 0.29 of it).
	 */
	static final float LANDING_OMEGA = 14f;

	static final float GRIND_SWAY = 0.05f;
	static final float MANUAL_SWAY = 0.035f;
	static final float SWAY_OMEGA = 6f;

	static final float BAIL_TUMBLE = 1.35f;
	static final float BAIL_TUMBLE_OMEGA = 6f;
	static final float BAIL_TUMBLE_KICK = 2f;
	static final float GET_UP_OMEGA = 7f;

	/**
	 * Model x of the riding direction (the board's nose) in regular stance. The puppet is drawn at heading
	 * + 90 degrees; with RuneLite's model-to-local rotation (MeshDeformer) at that orientation, model +x
	 * maps onto the heading: the body faces -z (to the right of travel) and its left (front) foot is the +x
	 * one. Goofy stance (body at heading - 90 degrees) flips it.
	 */
	static final float REGULAR_FORWARD_X = 1f;
	/** Seconds (time constant) a push cancelled by leaving the ground fades out over. */
	static final float PUSH_CANCEL_TAU = 0.08f;
	/** A body flip angle above this (radians) tucks the knees in the air. */
	static final float FLIP_TUCK_ANGLE = 0.01f;
	/**
	 * A flip change faster than this (rad/s, after taking out whole turns) is a reset, not rotation: it
	 * eases out instead of snapping. A double flip in 0.5 s at twice its mean rate turns 4 PI / 0.5 * 2 =
	 * 50 rad/s.
	 */
	static final float FLIP_SNAP_RATE = 60f;
	/** On a landing or bail frame any change bigger than this (radians, after whole turns) is a reset. */
	static final float FLIP_RESET_ANGLE = 0.05f;
	static final float FLIP_SETTLE_OMEGA = 14f;
	/**
	 * The body snaps into (and out of) a grab like Skate 3: 90% of the way in 3.9 / omega = 0.086 s. Also the knees'
	 * speed while a grab is held or let go in the air. (It was 12, a third of a second, with the knees on the slower
	 * crouch spring, so the hand only met the board once the body had caught up.)
	 */
	static final float GRAB_OMEGA = 45f;
	/**
	 * The body's roll toward the toe or heel edge and the knees' tuck in a grab: a touch softer (90% in 0.11 s), so a
	 * swap to the other edge's grab or a tuck straight off the pop's extension (the biggest swings) stay smooth.
	 */
	static final float GRAB_ROLL_OMEGA = 36f;
	/** The grabbing arm reaches for the board (and lets go): 90% of the way in 0.08 s. */
	static final float ARM_OMEGA = 48f;
	/** The hand slides between grab points (an aim renaming the grab) as fast as the body changes pose. */
	static final float GRAB_SPOT_OMEGA = 45f;
	/** Below this arm weight the hand counts as let go: a new grab's spot (or hand) is taken at once. */
	static final float ARM_LET_GO = 0.05f;
	/** A grab pulls the knees up this far (deeper than a flip's tuck), so the hand can reach the board. */
	static final float GRAB_TUCK = 0.48f;
	/** Radians the upper body folds further over the board in a grab. */
	static final float GRAB_BEND = 0.45f;
	/**
	 * Units a grab pulls the feet and the board up toward the hips (the render pose only, physics keeps its place):
	 * with the knees folded up toward the chest (BodyPose.kneeFold) this is what keeps the board near the hands
	 * instead of the hands going down to the board under the crotch.
	 */
	static final float GRAB_LIFT = 20f;
	/**
	 * A rolling grab's crouch: low enough (with the grab's fold and knees forward) for the hand to reach the board on
	 * the ground, which is not lifted.
	 */
	static final float ROLL_GRAB_CROUCH = MAX_CROUCH;
	/** Radians the upper body folds over the board in a rolling grab (more than in the air: the board is lower). */
	static final float ROLL_GRAB_BEND = 0.75f;
	/** Landing into a rolling grab the board eases back down to the ground: 90% of the way in 0.2 s. */
	static final float LIFT_SETTLE_OMEGA = 18f;

	private static final float TWO_PI = (float) (2 * Math.PI);

	private final CriticalSpring lean = new CriticalSpring();
	private final CriticalSpring pitch = new CriticalSpring();
	private final CriticalSpring crouch = new CriticalSpring();
	private final CriticalSpring impact = new CriticalSpring();
	private final CriticalSpring grindSway = new CriticalSpring();
	private final CriticalSpring manualSway = new CriticalSpring();
	private final CriticalSpring tumble = new CriticalSpring();
	/** The grab's reach along the board (+ toward the nose) and its body roll (see GrabPose). */
	private final CriticalSpring grabLean = new CriticalSpring();
	private final CriticalSpring grabRoll = new CriticalSpring();
	/** The grabbing arm: how far it has reached (0..1), where on the board it holds, and the extra fold. */
	private final CriticalSpring arm = new CriticalSpring();
	private final CriticalSpring armAlong = new CriticalSpring();
	private final CriticalSpring armAcross = new CriticalSpring();
	/** The grab point's board-model height: slides too (an edge grab renamed to a tip grab), never jumps. */
	private final CriticalSpring armBoardY = new CriticalSpring();
	private final CriticalSpring grabBend = new CriticalSpring();
	/** The knees folding up toward the chest in a grab (0..1), and with them the feet and board pulled up. */
	private final CriticalSpring kneeFold = new CriticalSpring();
	/** The feet and board pulled up (0..1): an air grab's only; a rolling grab keeps the board on the ground. */
	private final CriticalSpring liftFold = new CriticalSpring();
	private float armSide = -1f;
	private SkaterState lastState = SkaterState.ROLLING;
	private float airTime;
	private boolean poppedFlight;
	private float clock;
	private float pivotX;
	/** Phase of the current push cycle; 1 or more when none is playing. */
	private float pushPhase = 1f;
	private int pushCyclesStarted;
	/** Model x of the travel direction, latched at the start of each push. */
	private float pushTravelX = REGULAR_FORWARD_X;
	/** 1 while a push plays normally, easing to 0 when it is cancelled. */
	private float pushFade = 1f;
	private final PushCycle.Sample push = new PushCycle.Sample();
	private float flipAngle;
	private float flipForwardX = REGULAR_FORWARD_X;
	private float lastFlipInput;
	/** What a flip reset snapped away, easing back to 0. */
	private final CriticalSpring flipCatchUp = new CriticalSpring();

	public void reset()
	{
		lean.reset(0f);
		pitch.reset(0f);
		crouch.reset(0f);
		impact.reset(0f);
		grindSway.reset(0f);
		manualSway.reset(0f);
		tumble.reset(0f);
		grabLean.reset(0f);
		grabRoll.reset(0f);
		arm.reset(0f);
		armAlong.reset(0f);
		armAcross.reset(0f);
		armBoardY.reset(GrabPose.EDGE_Y);
		grabBend.reset(0f);
		kneeFold.reset(0f);
		liftFold.reset(0f);
		armSide = -1f;
		lastState = SkaterState.ROLLING;
		airTime = 0f;
		poppedFlight = false;
		clock = 0f;
		pivotX = 0f;
		pushPhase = 1f;
		pushTravelX = REGULAR_FORWARD_X;
		pushFade = 1f;
		flipAngle = 0f;
		lastFlipInput = 0f;
		flipCatchUp.reset(0f);
	}

	public void update(Signals s, float dt)
	{
		if (dt <= 0f)
		{
			return;
		}
		clock = (clock + dt) % 1000f;
		SkaterState st = s.state;
		boolean grounded = st == SkaterState.ROLLING || st == SkaterState.MANUAL;

		if (s.popped || s.rolledOff)
		{
			airTime = 0f;
			poppedFlight = s.popped;
		}
		else if (st == SkaterState.AIRBORNE)
		{
			airTime += dt;
		}

		lean.step(grounded ? leanFor(s.speed, s.carveRate) : 0f, LEAN_OMEGA, dt);

		if (s.boardPitch != 0f)
		{
			pivotX = -BoardPlacement.pivotZ(s.boardPitch);
		}
		boolean pitched = st == SkaterState.MANUAL || st == SkaterState.GRINDING;
		pitch.step(pitched ? BODY_PITCH_FRACTION * s.boardPitch : 0f, PITCH_OMEGA, dt);

		if (s.landed)
		{
			impact.kick(CriticalSpring.kickForPeak(landingCompression(s.landingSpeed), LANDING_OMEGA));
		}
		if (st == SkaterState.GRINDING && lastState == SkaterState.AIRBORNE)
		{
			impact.kick(CriticalSpring.kickForPeak(GRIND_LOCK_COMPRESSION, LANDING_OMEGA));
		}
		impact.step(0f, LANDING_OMEGA, dt);
		if (s.popped)
		{
			crouch.kick(POP_KICK);
		}
		// a grab tucks (and untucks when let go in the air) as fast as the arm reaches; landing keeps its own speed
		boolean grabSnap = st == SkaterState.AIRBORNE && (GrabPose.reaches(s.hold) || arm.value > ARM_LET_GO);
		if (grabSnap && GrabPose.reaches(s.hold) && crouch.velocity < 0f)
		{
			// grabbing while the pop still kicks the legs straight: tuck from where they are, not after the kick
			crouch.velocity = 0f;
		}
		crouch.step(crouchTarget(s, airTime, poppedFlight), grabSnap ? GRAB_ROLL_OMEGA : CROUCH_OMEGA, dt);
		crouch.value = Math.max(MIN_CROUCH, Math.min(MAX_CROUCH, crouch.value));

		grindSway.step(st == SkaterState.GRINDING ? GRIND_SWAY : 0f, SWAY_OMEGA, dt);
		manualSway.step(st == SkaterState.MANUAL ? MANUAL_SWAY : 0f, SWAY_OMEGA, dt);

		if (s.bailed && s.proceduralBail)
		{
			tumble.kick(BAIL_TUMBLE_KICK);
		}
		boolean tumbling = st == SkaterState.BAILED && s.proceduralBail;
		tumble.step(tumbling ? BAIL_TUMBLE : 0f, tumbling ? BAIL_TUMBLE_OMEGA : GET_UP_OMEGA, dt);
		Trick grab = st == SkaterState.AIRBORNE ? s.hold : rollingGrab(s);
		grabLean.step(GrabPose.torsoLean(grab), GRAB_OMEGA, dt);
		grabRoll.step(GrabPose.bodyRoll(grab), GRAB_ROLL_OMEGA, dt);
		boolean inAir = st == SkaterState.AIRBORNE;
		// rolling, a tailgrab is the back hand's whichever key: the front hand cannot reach the tail on the ground
		updateArm(grab, inAir, inAir ? s.grabHand : 0, dt);
		updatePush(s, dt);
		flipForwardX = s.goofy ? -REGULAR_FORWARD_X : REGULAR_FORWARD_X;
		updateFlip(s.bodyFlipAngle, s.landed || s.bailed, dt);
		lastState = st;
	}

	/** The rolling grab: a grab held while ROLLING on the board (the physics' ground grab), else null. */
	static Trick rollingGrab(Signals s)
	{
		return s.state == SkaterState.ROLLING && !s.onFoot && GrabPose.reaches(s.hold) ? s.hold : null;
	}

	/**
	 * The grabbing hand reaches for its spot on the board while a grab is held (in the air, or rolling) and lets go
	 * after. A new grab with the other hand first lets the old arm go back, so an arm never jumps across the body. Only
	 * an air grab ({@code inAir}) pulls the feet and board up.
	 */
	private void updateArm(Trick grab, boolean inAir, int hand, float dt)
	{
		boolean reaching = GrabPose.reaches(grab);
		float target = 0f;
		if (reaching)
		{
			// each grab's own hand (GrabPose.leftHand), so the arm never crosses the body to the board's middle
			boolean left = GrabPose.leftHand(grab, hand);
			float side = left ? 1f : -1f;
			boolean letGo = arm.value < ARM_LET_GO;
			if (side != armSide && letGo)
			{
				armSide = side;
			}
			if (side == armSide)
			{
				target = 1f;
				float along = GrabPose.grabAlong(grab);
				float across = GrabPose.grabAcross(grab);
				float boardY = GrabPose.grabBoardY(grab);
				if (letGo)
				{
					// a fresh grab: straight for its own spot, not from wherever the last one held
					armAlong.reset(along);
					armAcross.reset(across);
					armBoardY.reset(boardY);
				}
				armAlong.step(along, GRAB_SPOT_OMEGA, dt);
				armAcross.step(across, GRAB_SPOT_OMEGA, dt);
				armBoardY.step(boardY, GRAB_SPOT_OMEGA, dt);
			}
		}
		arm.step(target, ARM_OMEGA, dt);
		arm.value = Math.max(0f, Math.min(1f, arm.value));
		grabBend.step(reaching ? inAir ? GRAB_BEND : ROLL_GRAB_BEND : 0f, GRAB_OMEGA, dt);
		// the knees come up with the tuck's speed
		kneeFold.step(reaching ? 1f : 0f, GRAB_ROLL_OMEGA, dt);
		kneeFold.value = Math.max(0f, Math.min(1f, kneeFold.value));
		// in the air the same spring as the knees (the lift follows them exactly); landing into a rolling grab eases
		// the board back down to the ground a little slower while the knees stay folded
		liftFold.step(reaching && inAir ? 1f : 0f, reaching && !inAir ? LIFT_SETTLE_OMEGA : GRAB_ROLL_OMEGA, dt);
		liftFold.value = Math.max(0f, Math.min(1f, liftFold.value));
	}

	/**
	 * A kick plays on a PUSH while rolling, unless one is already playing: pushes that land mid-kick are not
	 * queued, so holding W (physics pushes every ~0.4 s) shows one longer stride about every other push instead
	 * of a kick on every push. Both ends of a kick are the plain stance, so starting a new one never jumps.
	 * Leaving the ground (or a push animation) cancels the kick: it fades out instead of snapping back.
	 */
	private void updatePush(Signals s, float dt)
	{
		boolean canPush = s.state == SkaterState.ROLLING && s.proceduralPush;
		if (s.pushed && canPush)
		{
			if (pushPhase >= 1f)
			{
				startPush(s, 0f);
			}
		}
		if (pushPhase >= 1f)
		{
			return;
		}
		pushPhase += dt / PushCycle.DURATION;
		if (!canPush)
		{
			pushFade *= (float) Math.exp(-dt / PUSH_CANCEL_TAU);
			if (pushFade < 0.01f)
			{
				pushPhase = 1f;
			}
		}
		if (pushPhase >= 1f)
		{
			pushPhase = 1f;
		}
	}

	/** Kicks started since creation or reset (for tests). */
	int pushCyclesStarted()
	{
		return pushCyclesStarted;
	}

	/**
	 * Passes the body flip angle through raw (whole turns never show), except that a jump which is not
	 * whole turns (e.g. a bail mid-flip resetting it to 0) is caught and eased out.
	 */
	private void updateFlip(float input, boolean touchdown, float dt)
	{
		float delta = input - lastFlipInput;
		float turns = Math.round(delta / TWO_PI) * TWO_PI;
		float rest = delta - turns;
		if (Math.abs(rest) > FLIP_SNAP_RATE * dt || (touchdown && Math.abs(rest) > FLIP_RESET_ANGLE))
		{
			// keep the drawn angle where it was: rest is wrapped to (-PI, PI], so this is the short way back
			flipCatchUp.value -= rest;
		}
		lastFlipInput = input;
		flipCatchUp.step(0f, FLIP_SETTLE_OMEGA, dt);
		flipAngle = input + flipCatchUp.value;
	}

	private void startPush(Signals s, float phase)
	{
		float forward = s.goofy ? -REGULAR_FORWARD_X : REGULAR_FORWARD_X;
		pushTravelX = s.speed < 0f ? -forward : forward;
		pushPhase = phase;
		pushFade = 1f;
		pushCyclesStarted++;
	}

	/** Writes the current pose. */
	public void writeTo(BodyPose out)
	{
		// lean (into the turn) and sway, minus the tumble: falling onto the chest is a negative roll
		out.roll = lean.value + grindSway.value * sway(clock) - tumble.value + grabRoll.value;
		out.pitch = pitch.value + manualSway.value * sway(clock * 1.3f + 0.4f);
		out.pivotX = pivotX;
		PushCycle.sample(pushPhase, push);
		float f = pushFade;
		float c = Math.max(MIN_CROUCH, Math.min(MAX_CROUCH, crouch.value + impact.value + f * push.squash));
		out.legScale = 1f - c;
		out.torsoBend = TORSO_BEND_PER_CROUCH * Math.max(0f, c) + grabBend.value;
		// the rear leg is the one on the trailing side; it plants behind, out to the toe (chest, -z) side
		out.torsoLean = f * push.lean * pushTravelX + flipForwardX * grabLean.value;
		out.legWeight = f * push.legWeight;
		out.legSide = -pushTravelX;
		out.footX = -pushTravelX * push.back;
		out.footY = push.footY;
		out.footZ = -push.side;
		// + = front flip: the head goes toward the nose, model +x for regular stance
		out.flip = flipForwardX * flipAngle;
		out.flipPivotY = BoardPlacement.puppetFlipPivotY();
		out.armWeight = arm.value;
		out.armSide = armSide;
		out.grabAlong = armAlong.value;
		out.grabAcross = armAcross.value;
		out.grabBoardY = armBoardY.value;
		out.forwardX = flipForwardX;
		out.kneeFold = kneeFold.value;
		out.feetLift = GRAB_LIFT * liftFold.value;
	}

	/**
	 * Body roll for a carve: the physical lean atan(v w / g) toward the centre of the turn, scaled by
	 * {@link #LEAN_GAIN} and capped at {@link #MAX_LEAN}. A clockwise (right) carve rolls negative, the same
	 * sense as the board's steer lean; riding fakie (v < 0) the turn's centre is on the other side.
	 */
	static float leanFor(float speed, float carveRate)
	{
		float w = Math.max(-MAX_CARVE_RATE, Math.min(MAX_CARVE_RATE, carveRate));
		float angle = LEAN_GAIN * (float) Math.atan(speed * w / LEAN_GRAVITY);
		return -Math.max(-MAX_LEAN, Math.min(MAX_LEAN, angle));
	}

	/** Peak landing compression for a landing at {@code speed} u/s downward. */
	static float landingCompression(float speed)
	{
		float c = LANDING_COMPRESSION * Math.abs(speed) / LANDING_REF_SPEED;
		return Math.max(MIN_LANDING_COMPRESSION, Math.min(MAX_LANDING_COMPRESSION, c));
	}

	/** Where the knees want to be this frame (before the landing kick). */
	static float crouchTarget(Signals s, float airTime, boolean poppedFlight)
	{
		switch (s.state)
		{
			case ROLLING:
			case MANUAL:
				if (rollingGrab(s) != null)
				{
					return ROLL_GRAB_CROUCH;
				}
				return CHARGE_CROUCH * Math.max(0f, Math.min(1f, s.charge));
			case GRINDING:
				return GRIND_CROUCH + CHARGE_CROUCH * 0.5f * Math.max(0f, Math.min(1f, s.charge));
			case BAILED:
				return BAIL_CROUCH;
			case AIRBORNE:
				boolean trick = s.flipping || (s.hold != null && s.hold.kind == TrickKind.GRAB)
					|| Math.abs(s.bodyFlipAngle) > FLIP_TUCK_ANGLE;
				if (GrabPose.reaches(s.hold) && !s.onFoot)
				{
					// a grab straight off the pop tucks at once: the pop's leg extension would hold it back 0.12 s
					return GRAB_TUCK;
				}
				if (poppedFlight && airTime < POP_EXTEND_TIME)
				{
					return POP_EXTEND;
				}
				if (s.onFoot)
				{
					if (s.verticalSpeed < -LAND_REACH_SPEED)
					{
						return LAND_REACH;
					}
					return poppedFlight ? FOOT_JUMP_TUCK : FOOT_FALL_TUCK;
				}
				if (trick)
				{
					return TRICK_TUCK;
				}
				if (s.verticalSpeed < -LAND_REACH_SPEED)
				{
					return LAND_REACH;
				}
				return poppedFlight ? AIR_TUCK : ROLL_OFF_TUCK;
			default:
				return 0f;
		}
	}

	/** A smooth, non-repeating-looking balance wobble in about -1..1. */
	static float sway(float t)
	{
		return ((float) Math.sin(TWO_PI * 0.8f * t) + 0.5f * (float) Math.sin(TWO_PI * 1.9f * t + 1.3f)) / 1.5f;
	}
}
