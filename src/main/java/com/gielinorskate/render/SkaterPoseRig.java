package com.gielinorskate.render;

import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.util.ArrayList;
import java.util.List;

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
*
* <p>Spring speeds (omega, 1/s) are about 4 / omega seconds to settle; "90% in t" is 3.9 / omega.
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
/** A push (SkateEvent.PUSH) this frame. */
public boolean pushed;
/** Push procedurally (no push animation is playing). */
public boolean proceduralPush = true;
/**
* Front/back flip of skater and board about the centre of mass, radians, + = front flip (head toward
* the nose). Raw, not wrapped.
*/
public float bodyFlipAngle;
/**
* Walking, not skating (FootBody fills the rest): a jump tucks lightly, a fall
* barely bends the knees.
*/
public boolean onFoot;
/**
* The grab key's hand: +1 the left (Q), -1 the right (E), 0 not known (a party ghost). Each grab is drawn
* with its own hand ({@link GrabPose#leftHand}); only a tailgrab, either hand's, follows the key.
*/
public int grabHand;
}

static final float MAX_LEAN = 0.5f;

/** Crouch amounts: 1 - legScale. Negative extends the legs. */
static final float CHARGE_CROUCH = 0.3f;
static final float MIN_CROUCH = -0.2f;
static final float MAX_CROUCH = 0.5f;

/**
* The landing compression's own spring: peaks 1 / 14 = 0.07 s after touchdown and is back to 30% of
* the peak by 0.25 s ((1 + 3.5) e^-3.5 / e^-1 = 0.29 of it).
*/
static final float LANDING_OMEGA = 14f;

static final float SWAY_OMEGA = 6f;

/**
* The body snaps into (and out of) a grab like Skate 3: 90% of the way in 3.9 / omega = 0.086 s. Also the knees'
* speed while a grab is held or let go in the air. (It was 12, a third of a second, with the knees on the slower
* crouch spring, so the hand only met the board once the body had caught up.) The hand slides between grab points
* (an aim renaming the grab) as fast.
*/
static final float GRAB_OMEGA = 45f;
/**
* The body's roll toward the toe or heel edge and the knees' tuck in a grab: a touch softer (90% in 0.11 s), so a
* swap to the other edge's grab or a tuck straight off the pop's extension (the biggest swings) stay smooth.
*/
static final float GRAB_ROLL_OMEGA = 36f;
/** Below this arm weight the hand counts as let go: a new grab's spot (or hand) is taken at once. */
static final float ARM_LET_GO = 0.05f;

private static final float TWO_PI = (float) (2 * Math.PI);

/** Every spring, for {@link #reset}. */
private final List<CriticalSpring> springs = new ArrayList<>();
private final CriticalSpring lean = spring();
private final CriticalSpring pitch = spring();
private final CriticalSpring crouch = spring();
private final CriticalSpring impact = spring();
private final CriticalSpring grindSway = spring();
private final CriticalSpring manualSway = spring();
private final CriticalSpring tumble = spring();
/** The grab's reach along the board (+ toward the nose) and its body roll (see GrabPose). */
private final CriticalSpring grabLean = spring();
private final CriticalSpring grabRoll = spring();
/** The grabbing arm: how far it has reached (0..1), where on the board it holds, and the extra fold. */
private final CriticalSpring arm = spring();
private final CriticalSpring armAlong = spring();
private final CriticalSpring armAcross = spring();
/** The grab point's board-model height: slides too (an edge grab renamed to a tip grab), never jumps. */
private final CriticalSpring armBoardY = spring();
private final CriticalSpring grabBend = spring();
/** The knees folding up toward the chest in a grab (0..1), and with them the feet and board pulled up. */
private final CriticalSpring kneeFold = spring();
/** The feet and board pulled up (0..1): an air grab's only; a rolling grab keeps the board on the ground. */
private final CriticalSpring liftFold = spring();
/** What a flip reset snapped away, easing back to 0. */
private final CriticalSpring flipCatchUp = spring();
private float armSide = -1f;
private SkaterState lastState = SkaterState.ROLLING;
private float airTime;
private boolean poppedFlight;
private float clock;
private float pivotX;
/** Phase of the current push cycle; 1 or more when none is playing. */
private float pushPhase = 1f;
private int pushCyclesStarted;
/**
* Model x of the travel direction, latched at the start of each push. The puppet is drawn at heading + 90
* degrees (regular stance only); with RuneLite's model-to-local rotation (MeshDeformer) at that orientation,
* model +x maps onto the heading (the board's nose): the body faces -z (to the right of travel) and its left
* (front) foot is the +x one.
*/
private float pushTravelX = 1f;
/** 1 while a push plays normally, easing to 0 when it is cancelled. */
private float pushFade = 1f;
private final PushCycle.Sample push = new PushCycle.Sample();
private float flipAngle;
private float lastFlipInput;

private CriticalSpring spring()
{
CriticalSpring s = new CriticalSpring();
springs.add(s);
return s;
}

public void reset()
{
springs.forEach(s -> s.reset(0f));
armBoardY.reset(GrabPose.EDGE_Y);
armSide = -1f;
lastState = SkaterState.ROLLING;
airTime = 0f;
poppedFlight = false;
clock = 0f;
pivotX = 0f;
pushPhase = 1f;
pushTravelX = 1f;
pushFade = 1f;
flipAngle = 0f;
lastFlipInput = 0f;
}

public void update(Signals s, float dt)
{
if (dt <= 0f)
return;
clock = (clock + dt) % 1000f;
SkaterState st = s.state;
boolean grounded = st == SkaterState.ROLLING || st == SkaterState.MANUAL;
boolean inAir = st == SkaterState.AIRBORNE;

if (s.popped || s.rolledOff)
{
airTime = 0f;
poppedFlight = s.popped;
}
else if (inAir)
airTime += dt;

lean.step(grounded ? leanFor(s.speed, s.carveRate) : 0f, 9f, dt);

if (s.boardPitch != 0f)
pivotX = -BoardPlacement.pivotZ(s.boardPitch);
boolean pitched = st == SkaterState.MANUAL || st == SkaterState.GRINDING;
// the body tips by 0.7 of the board's manual or grind pitch
pitch.step(pitched ? 0.7f * s.boardPitch : 0f, 10f, dt);

if (s.landed)
impact.kick(CriticalSpring.kickForPeak(landingCompression(s.landingSpeed), LANDING_OMEGA));
if (st == SkaterState.GRINDING && lastState == SkaterState.AIRBORNE)
// locking onto a rail
impact.kick(CriticalSpring.kickForPeak(0.1f, LANDING_OMEGA));
impact.step(0f, LANDING_OMEGA, dt);
if (s.popped)
// the legs snap straight from a deep charge crouch
crouch.kick(-8f);
// a grab tucks (and untucks when let go in the air) as fast as the arm reaches; landing keeps its own speed
// (about 0.2 s to settle, quick enough for the pop extension to read)
boolean grabSnap = inAir && (GrabPose.reaches(s.hold) || arm.value > ARM_LET_GO);
if (inAir && GrabPose.reaches(s.hold) && crouch.velocity < 0f)
// grabbing while the pop still kicks the legs straight: tuck from where they are, not after the kick
crouch.velocity = 0f;
crouch.step(crouchTarget(s, airTime, poppedFlight), grabSnap ? GRAB_ROLL_OMEGA : 20f, dt);
crouch.value = Math.max(MIN_CROUCH, Math.min(MAX_CROUCH, crouch.value));

// a balance sway on rails and in manuals
grindSway.step(st == SkaterState.GRINDING ? 0.05f : 0f, SWAY_OMEGA, dt);
manualSway.step(st == SkaterState.MANUAL ? 0.035f : 0f, SWAY_OMEGA, dt);

if (s.bailed)
tumble.kick(2f);
boolean tumbling = st == SkaterState.BAILED;
// the fall (1.35 rad onto the chest), and the get-up
tumble.step(tumbling ? 1.35f : 0f, tumbling ? 6f : 7f, dt);
Trick grab = inAir ? s.hold : rollingGrab(s);
grabLean.step(GrabPose.torsoLean(grab), GRAB_OMEGA, dt);
grabRoll.step(GrabPose.bodyRoll(grab), GRAB_ROLL_OMEGA, dt);
// rolling, a tailgrab is the back hand's whichever key: the front hand cannot reach the tail on the ground
updateArm(grab, inAir, inAir ? s.grabHand : 0, dt);
updatePush(s, dt);
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
// each grab's own hand (GrabPose.leftHand), so the arm never crosses the body to the board's middle
float side = GrabPose.leftHand(grab, hand) ? 1f : -1f;
boolean letGo = arm.value < ARM_LET_GO;
if (reaching && side != armSide && letGo)
armSide = side;
if (reaching && side == armSide)
{
if (letGo)
{
// a fresh grab: straight for its own spot, not from wherever the last one held
armAlong.reset(GrabPose.grabAlong(grab));
armAcross.reset(GrabPose.grabAcross(grab));
armBoardY.reset(GrabPose.grabBoardY(grab));
}
armAlong.step(GrabPose.grabAlong(grab), GRAB_OMEGA, dt);
armAcross.step(GrabPose.grabAcross(grab), GRAB_OMEGA, dt);
armBoardY.step(GrabPose.grabBoardY(grab), GRAB_OMEGA, dt);
}
// the arm reaches for the board (and lets go): 90% of the way in 0.08 s
arm.step(reaching && side == armSide ? 1f : 0f, 48f, dt);
arm.value = PushCycle.clamp01(arm.value);
// a grab folds the upper body 0.45 rad further over the board; a rolling grab 0.75 (the board is lower)
grabBend.step(reaching ? inAir ? 0.45f : 0.75f : 0f, GRAB_OMEGA, dt);
// the knees come up with the tuck's speed
kneeFold.step(reaching ? 1f : 0f, GRAB_ROLL_OMEGA, dt);
kneeFold.value = PushCycle.clamp01(kneeFold.value);
// in the air the same spring as the knees (the lift follows them exactly); landing into a rolling grab eases
// the board back down to the ground a little slower (90% in 0.2 s) while the knees stay folded
liftFold.step(reaching && inAir ? 1f : 0f, reaching && !inAir ? 18f : GRAB_ROLL_OMEGA, dt);
liftFold.value = PushCycle.clamp01(liftFold.value);
}

/**
* A kick plays on a PUSH while rolling, unless one is already playing: pushes that land mid-kick are not
* queued, so holding W (physics pushes every ~0.4 s) shows one longer stride about every other push instead
* of a kick on every push. Both ends of a kick are the plain stance, so starting a new one never jumps.
* Leaving the ground (or a push animation) cancels the kick: it fades out (time constant 0.08 s) instead of
* snapping back.
*/
private void updatePush(Signals s, float dt)
{
boolean canPush = s.state == SkaterState.ROLLING && s.proceduralPush;
if (s.pushed && canPush && pushPhase >= 1f)
{
pushTravelX = s.speed < 0f ? -1f : 1f;
pushPhase = 0f;
pushFade = 1f;
pushCyclesStarted++;
}
if (pushPhase >= 1f)
return;
pushPhase += dt / PushCycle.DURATION;
if (!canPush)
{
pushFade *= (float) Math.exp(-dt / 0.08f);
if (pushFade < 0.01f)
pushPhase = 1f;
}
pushPhase = Math.min(1f, pushPhase);
}

/** Kicks started since creation or reset (for tests). */
int pushCyclesStarted()
{
return pushCyclesStarted;
}

/**
* Passes the body flip angle through raw (whole turns never show), except that a jump which is not
* whole turns (e.g. a bail mid-flip resetting it to 0) is caught and eased out: a change faster than 60 rad/s
* (after taking out whole turns; a double flip in 0.5 s at twice its mean rate turns 4 PI / 0.5 * 2 = 50 rad/s),
* or on a landing or bail frame any change bigger than 0.05 rad.
*/
private void updateFlip(float input, boolean touchdown, float dt)
{
float delta = input - lastFlipInput;
float turns = Math.round(delta / TWO_PI) * TWO_PI;
float rest = delta - turns;
if (Math.abs(rest) > 60f * dt || (touchdown && Math.abs(rest) > 0.05f))
{
// keep the drawn angle where it was: rest is wrapped to (-PI, PI], so this is the short way back
flipCatchUp.value -= rest;
}
lastFlipInput = input;
flipCatchUp.step(0f, 14f, dt);
flipAngle = input + flipCatchUp.value;
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
// the upper body folds 0.9 rad per unit of crouch
out.torsoBend = 0.9f * Math.max(0f, c) + grabBend.value;
// the rear leg is the one on the trailing side; it plants behind, out to the toe (chest, -z) side
out.torsoLean = f * push.lean * pushTravelX + grabLean.value;
out.legWeight = f * push.legWeight;
out.legSide = -pushTravelX;
out.footX = -pushTravelX * push.back;
out.footY = push.footY;
out.footZ = -push.side;
// + = front flip: the head goes toward the nose, model +x for regular stance
out.flip = flipAngle;
out.flipPivotY = BoardPlacement.puppetFlipPivotY();
out.armWeight = arm.value;
out.armSide = armSide;
out.grabAlong = armAlong.value;
out.grabAcross = armAcross.value;
out.grabBoardY = armBoardY.value;
out.kneeFold = kneeFold.value;
// a grab pulls the feet and the board up 20 units toward the hips (the render pose only, physics keeps its
// place): with the knees folded up toward the chest this keeps the board near the hands instead of the hands
// going down to the board under the crotch
out.feetLift = 20f * liftFold.value;
}

/**
* Body roll for a carve: 0.6 of the physical lean atan(v w / g) toward the centre of the turn (the full angle
* looked like falling over; g the game gravity, SkateTuning.gravity's default 2000), capped at {@link #MAX_LEAN}.
* Carve rates above 8 rad/s are heading snaps (a reset or rail lock), not carving. A clockwise (right) carve rolls
* negative, the same sense as the board's steer lean; riding fakie (v < 0) the turn's centre is on the other side.
*/
static float leanFor(float speed, float carveRate)
{
float w = Math.max(-8f, Math.min(8f, carveRate));
float angle = 0.6f * (float) Math.atan(speed * w / 2000f);
return -Math.max(-MAX_LEAN, Math.min(MAX_LEAN, angle));
}

/**
* Peak landing compression for a landing at {@code speed} u/s downward: a full-charge ollie lands at about its
* take-off speed (ollieImpulse 820 u/s).
*/
static float landingCompression(float speed)
{
// 0.22 for that ollie, within 0.04 and 0.3
float c = 0.22f * Math.abs(speed) / 820f;
return Math.max(0.04f, Math.min(0.3f, c));
}

/** Where the knees want to be this frame (before the landing kick): 1 - legScale, negative extends the legs. */
static float crouchTarget(Signals s, float airTime, boolean poppedFlight)
{
switch (s.state)
{
case ROLLING:
case MANUAL:
// a rolling grab: low enough (with the grab's fold and knees forward) for the hand to reach the
// board on the ground, which is not lifted
return rollingGrab(s) != null ? MAX_CROUCH : CHARGE_CROUCH * PushCycle.clamp01(s.charge);
case GRINDING:
// a little crouch on rails
return 0.14f + CHARGE_CROUCH * 0.5f * PushCycle.clamp01(s.charge);
case BAILED:
return 0.25f;
case AIRBORNE:
boolean trick = s.flipping || (s.hold != null && s.hold.kind == TrickKind.GRAB)
|| Math.abs(s.bodyFlipAngle) > 0.01f;
if (GrabPose.reaches(s.hold) && !s.onFoot)
// a grab pulls the knees up deeper than a flip's tuck, so the hand can reach the board; straight off
// the pop it tucks at once: the pop's leg extension would hold it back 0.12 s
return 0.48f;
if (poppedFlight && airTime < 0.12f)
// the legs extend just after the pop
return -0.18f;
if (!s.onFoot && trick)
// a flip or grab tucks deeper
return 0.38f;
if (s.verticalSpeed < -350f)
// falling fast with no trick going: the legs reach for the ground (nearly straight)
return 0.05f;
// on foot a jump tucks 0.2 and a fall barely (0.05); on the board 0.26 after a pop, 0.1 off an edge
return s.onFoot ? poppedFlight ? 0.2f : 0.05f : poppedFlight ? 0.26f : 0.1f;
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
