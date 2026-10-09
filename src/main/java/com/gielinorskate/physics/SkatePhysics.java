package com.gielinorskate.physics;

import com.gielinorskate.tricks.*;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Fixed-step skateboard simulation. Positions in local units, heights up-positive. */
public final class SkatePhysics
{
private static final float SLOPE_SAMPLE = 16f;
private static final float SLOW_LANDING_SPEED = 50f;
/**
* A flip at least this far through may lock onto a rail (review P3: the 75% gate cost 18-27 points of
* kickflip lock rate); it keeps turning on the rail until done.
*/
private static final float FLIP_LOCK_FRACTION = 0.5f;
/** Board pitch while balancing a manual (nose up) / nose manual (nose down), radians. */
private static final float MANUAL_PITCH = 0.25f;
private static final float TWO_PI = (float) (2 * Math.PI);
/** Slack on the grab aim / tweak times: the hold time is a sum of float steps (25 * 0.02f is not 0.5f). */
private static final float HOLD_TIME_EPSILON = 1e-4f;
/** Horizontal speed needed to lock onto a rail at all. */
private static final float GRIND_LOCK_MIN_SPEED = 150f;
/** In the air with the spin assist held (button tricks), steering spins this much faster. */
static final float SPIN_FAST_MULT = 1.5f;
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
/** Rolling back slower than this (and not a fakie landing) counts as stalled: a push goes forward. */
private static final float STALL_SPEED = 400f;
/** Wall contacts closer together than this are one scrape (the first-contact speed loss applies once). */
private static final float WALL_CONTACT_MEMORY = 0.25f;
/** Steer magnitudes below this count as released (as for the spin assist). */
private static final float STEER_DEADZONE = 0.1f;
private final SkateTuning t;
private final CollisionWorld world;
private final GrindMap grinds;
private final List<SkateEvent> events = new ArrayList<>();
private final List<TrickEvent> trickEvents = new ArrayList<>();

@Getter
private SkaterState state = SkaterState.ROLLING;
@Getter
private float x, y, h, heading;
/**
* speed: signed speed along heading while grounded; negative = rolling backwards (fakie). sinceWall: seconds since
* the last wall contact while rolling; a contact counts as a new hit (costs speed) only after WALL_CONTACT_MEMORY.
* stumbleTimer: steering is locked while this runs down after a hard (non-bail) wall hit. After an off-axis landing
* the travel direction is heading + landSlip (radians), easing onto the board at landSlipRate rad/s instead of
* snapping.
*/
private float speed, sinceWall = WALL_CONTACT_MEMORY, stumbleTimer, landSlip, landSlipRate;
/**
* fakie: true after landing backwards on purpose: pushes then go fakie too; cleared once the speed is >= 0.
* bailWall: set when a bail ran into a wall: the recovery faces along (bailWallNx, bailWallNy), away from it.
* airSteerHeld: a steer already held when leaving the ground does not spin the board until it is released.
* airCameraHeld: after a wall hit in the air the camera holds airCameraHold until the landing, turned only as far
* as the spin assist turns the board (see stepAir). rollOffFlight: true while in the air from a plain roll-off
* (cleared by a flip or grab started in the air). airFakie: the current flight left the ground riding fakie: its
* flips are named "Fakie ...". resetQueued: R pressed while bailed.
*/
private boolean fakie, bailWall, airSteerHeld, airCameraHeld, rollOffFlight, airFakie, resetQueued;
@Getter
private float bailWallNx, bailWallNy;
/**
* airCameraRef: camera heading on the ground just before the current flight (see {@link #getCameraHeading()}).
* airTime: seconds in the air so far. rampRun: distance rolled up the current ramp approach (see rampLaunch); 0 off
* the ground or on flatter ground. airAutoSpin: the part of airSpin turned by tricks themselves (a bigspin's body
* 180): not named as a spin. flipAutoSpin: body turn the current flip has applied to the heading so far (see
* {@link Trick#bodyYawTurns}). recoverTimer: time left until the skater gets back on automatically.
*/
private float airCameraRef, airCameraHold, airTime, rampRun, airAutoSpin, flipAutoSpin, recoverTimer;
/** Seconds in the air of the flight that ended in the latest landing (for the camera's landing dip). */
@Getter
private float lastAirtime;
/**
* Signed body yaw (radians, + = clockwise from above) accumulated since the latest take-off: steering,
* the spin assist and a bigspin's automatic body turn. Kept after landing until the next take-off.
* Read-only, for the renderer and HUD.
*/
float airSpin;
/**
* Front / back flip angle of the whole skater and board about the centre of mass, radians: + = frontflip
* (forward pitch), - = backflip. Accumulates through the air (2 pi per whole flip) and returns to 0 on
* landing, a rail lock or a bail; always 0 on the ground and on rails. Read-only, for the renderer.
*/
@Getter
private float bodyFlipAngle;
/**
* W / S already held at take-off: they cannot drive a body flip until released and pressed again. The lean
* keys likewise for a grab-flip.
*/
private boolean flipForwardHeld, flipBackHeld, leanForwardHeld, leanBackHeld;
/**
* Quality of the latest landing: CLEAN when the board touched down within 15 degrees of the travel (either
* way round) with its flip finished, else SLOPPY. Null before any landing, while airborne, after a bail and
* after a plain roll-off (no trick in the air). For the HUD; the scorer reads the LANDED event's own flag.
*/
LandingQuality lastLandingQuality;
private float vx, vy, vh, crouchTime, pushCooldown, bailTimer;
/** Downward speed (u/s, >= 0) of the latest landing, for the renderer's landing compression. */
@Getter
private float lastLandingSpeed;
/** True when the latest pop (from the ground or a rail) was off the nose (a nollie-family trick), for the renderer's pop tilt. */
@Getter
private boolean lastPopNollie;
/** Why the latest bail happened (kept after recovery); null before any bail. */
@Getter
private BailReason lastBailReason;

/**
* Flip currently rotating the board (null when none; shared with the party), seconds since it started, and the
* seconds it takes (the trick's duration, or less for a late flick compressed to fit the air left). A flip
* caught before it finished keeps turning on the ground or rail until done.
*/
@Getter
private Trick flipTrick;
private float flipTime, flipDuration;
/** Current grab, manual or grind (null when none), and seconds it has been held. */
@Getter
private Trick activeHold;
private float holdTime;
/**
* For a grab: held with the left hand (Q) rather than the right (E) (the latest grab, also read by the
* renderer); aimed already (an aim only picks the grab once, within Grabs.AIM_SECONDS of the press); tweaked
* already (once, past Grabs.TWEAK_SECONDS).
*/
@Getter
private boolean grabLeftHand;
private boolean grabAimed, grabTweaked;
/**
* The rolling grab: Q / E held while ROLLING grabs the board on the ground (null when none), and seconds held. Pose
* only: never a hold, no trick events, no points, no combo; it only stops pushes. The grab's hand and aim share
* grabLeftHand / grabAimed with the air grab.
*/
private Trick groundGrab;
private float groundGrabTime;

/** Segment being ground (null when not grinding), distance along it from end 0, and +1/-1 travel direction. */
private GrindSegment grindSeg;
private float grindPos, grindDir, grindSpeed, grindCooldown;
/**
* The kind of grind picked at lock; W/S switch between the tricks of the same family mid-grind.
* STRAIGHT (50-50/nosegrind/5-0) lines the board up with the rail; the others keep it across.
*/
private GrindFamily grindFamily = GrindFamily.STRAIGHT;

/** Grind families and the trick each gives with no key, W held and S held. */
@RequiredArgsConstructor
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

final Trick plain, nose, tail;

/** W held: the nose variation; S held: the tail one; otherwise the plain trick (W wins if both). */
Trick trick(SkateInput in)
{
return in.pushHeld ? nose : in.leanBack ? tail : plain;
}
}
/**
* The segment dropped off (rail end or too slow; it cannot be locked onto again until the skater lands) and the
* segment popped off (it cannot be locked onto again while still rising, but can once falling).
*/
private GrindSegment noRelockSeg, risingRelockSeg;
/**
* True in the air after a grind ended without a pop (rail end or too slow): no rail at all can be locked
* until touchdown or a bail. Without it a slow drop-off's 150 u/s side hop carried the skater onto a
* parallel rail 40-88 units away (a table or crate's other top edge), the lock lifted the speed back to
* the 300 u/s lock minimum and the two rails ping-ponged forever.
*/
private boolean dropOffFlight;
/** +1 when the rail was approached from the right of the grind travel direction, -1 from the left. */
private float approachRight = 1f;

/** No grindable edges. */
public SkatePhysics(SkateTuning tuning, CollisionWorld world, float x, float y, float heading)
{
this(tuning, world, new GrindMap(), x, y, heading);
}

public SkatePhysics(SkateTuning t, CollisionWorld world, GrindMap grinds, float x, float y, float heading)
{
this.t = t;
this.world = world;
this.grinds = grinds;
this.x = x;
this.y = y;
this.heading = Angles.wrap(heading);
h = world.groundHeight(x, y);
}

public void step(float dt, SkateInput in)
{
grindCooldown = Math.max(0f, grindCooldown - dt);
// a stumble's steering lock runs out in the air or on a rail too, not only while rolling
stumbleTimer = Math.max(0f, stumbleTimer - dt);
if (grounded())
stepRolling(dt, in);
else
{
rampRun = 0f;
if (state == SkaterState.AIRBORNE)
stepAir(dt, in);
else if (state == SkaterState.BAILED)
stepBailed(dt, in);
else
stepGrind(dt, in);
}
if (state != SkaterState.ROLLING)
// popped, rolled off, locked onto a rail, bailed or into a manual: the rolling grab is let go
groundGrab = null;
in.clearEdges();
}

/** ROLLING and MANUAL share this: a manual moves exactly like rolling, only the board pose differs. */
private void stepRolling(float dt, SkateInput in)
{
// R while rolling slower than 150 u/s stands the skater still (and unwedged), as after a bail
if (in.resetRequested && state == SkaterState.ROLLING && Math.abs(speed) < 150f)
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
crouchTime += dt;
else if (pop == null)
// the flick that pops arrives with the wind-up already released: keep its charge for the pop
crouchTime = Math.max(0f, crouchTime - 2 * dt);
sinceWall = Math.min(WALL_CONTACT_MEMORY + 1f, sinceWall + dt);
landSlip = towardZero(landSlip, landSlipRate * dt);
// a stumble off a wall hit locks steering for a moment
float steer = stumbleTimer > 0f ? 0f : in.steer;

if (speed >= 0f)
fakie = false;
// W never pushes in a manual (Space + W is a nose manual) nor while grabbing the board on the ground
if (state == SkaterState.ROLLING && (in.pushPressed || in.pushHeld) && pushCooldown == 0f && !in.crouch
&& groundGrab == null)
push();

boolean tightCarve = in.powerslide && steer != 0f;
if (in.powerslide && !tightCarve && !in.brakeBlocked && speed != 0f)
// Shift alone: powerslide brake
speed = towardZero(speed, t.powerslideDecel * dt);
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
startPop(pop, ridingFakie());
velocityAlongMotion();
// the board already rises (or sinks) with the ground: speed * slope
vh = t.ollieImpulse * clamp(crouchTime / t.crouchChargeTime, t.minPopFraction, 1f) + speed * slope;
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
float dx = (float) Math.sin(heading + landSlip) * speed * sdt;
float dy = (float) Math.cos(heading + landSlip) * speed * sdt;
float px = x;
float py = y;
if (!blocked(x + dx, y + dy, true) || momentumClimb(x + dx, y + dy))
{
x += dx;
y += dy;
}
else if (!scrapeRolling(dx, dy, sdt, in.steer != 0f))
{
return; // bailed
}
float g = world.groundHeight(x, y);
float drop = h - g;
if (drop > t.rollOffDrop && drop > MAX_GROUND_GRADIENT * Math.abs(speed * sdt) && !dropIn(px, py, g))
rollOff = true;
else
{
// small rises and drops (bumps, hills, curbs) are followed exactly
h = g;
// judged every substep: a crest is only a few units wide at full speed
launchVh = rampLaunch(sdt);
}
}

if (state == SkaterState.ROLLING && tryLockGrind(in, getTravelHeading(), Math.abs(speed), true))
return;

if (rollOff || launchVh > 0f)
{
velocityAlongMotion();
vh = launchVh;
endHold();
takeOff(in, rollingCameraHeading());
// no snapping onto the ledge just rolled off (that edge is not known as a segment): after 0.45 s the
// drop is 1000 * 0.45^2 = 202, far below the edge's lock window
grindCooldown = 0.45f;
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
// below 50 u/s forward a push counts as a push from rest
if (!fakie && speed < 50f && speed > -STALL_SPEED)
// stalled on a hill or barely moving: plant a foot and push off forwards
speed = Math.max(t.pushMinImpulse, speed + t.pushFromRest);
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
* none below 150 u/s (below 120 u/s once already in a manual: 30 u/s of hysteresis, so a speed swinging a few
* u/s either side of 150 on bumps does not start and end the hold every few frames).
*/
private Trick wantedManual(SkateInput in)
{
float min = state == SkaterState.MANUAL ? 120f : 150f;
return !in.manualHeld || Math.abs(speed) < min ? null : in.pushHeld || in.noseManualHeld ? Trick.NOSE_MANUAL : Trick.MANUAL;
}

/**
* Enters, keeps or leaves a manual from the grounded states. Balance is automatic: a manual ends
* (HOLD_END, no bail) when the manual key is released or the skater slows below 120 u/s;
* pressing or releasing W mid-manual ends one manual and starts the other.
*/
private void updateManual(float dt, SkateInput in)
{
Trick wanted = wantedManual(in);
if (state == SkaterState.MANUAL && activeHold != wanted)
endHold();
if (state == SkaterState.ROLLING && wanted != null)
{
state = SkaterState.MANUAL;
startHold(wanted);
}
if (state == SkaterState.MANUAL)
holdTime += dt;
}

/**
* The rolling grab (ROLLING only): a held grab key grabs the board as in the air (Q unaimed an Indy, E a Melon, the
* first aim within Grabs.AIM_SECONDS picking the grab) but never tweaks; letting the key go lets go. A manual,
* a pop or anything else that leaves ROLLING ends it (see {@link #step}).
*/
private void updateGroundGrab(float dt, SkateInput in)
{
if (state != SkaterState.ROLLING || groundGrab != null && !grabHeld(in))
groundGrab = null;
else if (groundGrab != null)
{
groundGrabTime += dt;
if (aims(in, groundGrabTime))
groundGrab = Grabs.pick(grabLeftHand, in.grabAim);
}
else if (in.grabLeft || in.grabRight)
{
groundGrab = grabStart(in);
groundGrabTime = dt;
}
}

/** A grab key went down (no aim: Q is an Indy and E a Melon, as before directional grabs): the grab it picks. */
private Trick grabStart(SkateInput in)
{
grabLeftHand = in.grabLeft;
grabAimed = in.grabAim != null;
grabTweaked = false;
return Grabs.pick(grabLeftHand, in.grabAim);
}

/** The first aim within Grabs.AIM_SECONDS of the grab key going down: true (once) when it picks the grab now. */
private boolean aims(SkateInput in, float heldFor)
{
if (grabAimed || in.grabAim == null || !(heldFor <= Grabs.AIM_SECONDS + HOLD_TIME_EPSILON))
return false;
grabAimed = true;
return true;
}

/**
* Pops {@code pop} off the ground or a rail: ends any hold (popping out of a manual ends it; the scorer links it
* into the same combo), then starts its flip, or for a plain pop clears a flip still finishing from the last catch.
*/
private void startPop(Trick pop, boolean fakie)
{
endHold();
lastPopNollie = pop.name().startsWith("NOLLIE"); // the nollie family pops off the nose
trickEvents.add(TrickEvent.trick(pop, fakie));
clearFlip();
if (pop.kind == TrickKind.FLIP)
{
flipTrick = pop;
flipDuration = pop.duration;
}
}

/** Grounded: the velocity along the motion (heading + landSlip) at the signed speed. */
private void velocityAlongMotion()
{
float motion = heading + landSlip;
vx = (float) Math.sin(motion) * speed;
vy = (float) Math.cos(motion) * speed;
}

/** First gesture that pops (an ollie/nollie or any flip), or null. */
private static Trick popTrick(List<Gesture> gestures)
{
for (Gesture g : gestures)
{
Trick trick = TrickCatalog.forGesture(g);
if (trick != null && (trick.kind == TrickKind.POP || trick.kind == TrickKind.FLIP))
return trick;
}
return null;
}

/** Touchdown, a bail or a rail lock: any rail may be locked onto again. */
private void clearRelock()
{
dropOffFlight = false;
noRelockSeg = null;
risingRelockSeg = null;
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
clearFlip();
}
}

/**
* True when the current flip is less than 75% done (it cannot be landed or locked): a flip at least that far
* through its rotation at touchdown is caught (forgiving landing).
*/
private boolean flipUncatchable()
{
return flipTrick != null && flipTime < 0.75f * flipDuration;
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
return;
trickEvents.add(TrickEvent.holdEnd(activeHold, holdTime));
activeHold = null;
holdTime = 0f;
if (state == SkaterState.MANUAL)
state = SkaterState.ROLLING;
}

/**
* Result of {@link #findContact}: the wall normal (toward the skater) and the part of the blocked move that
* is still free (contactFreeDx, contactFreeDy; zero when nothing is).
*/
private float contactNx, contactNy, contactFreeDx, contactFreeDy;
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
hitPending = true;
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
// The slide along the face may still clip something: the far side of a rounded box corner, the end
// of the next wall-edge box at a tile joint (whose least-penetration normal points along the
// wall), or a second blocker. Stopping dead there wedged the skater for good: every later push
// met the same corner. Push the slide out of what it clips, else fall back to the free axis (which
// keeps the contact normal: the speed loss and alignment follow the real face).
if (!freeSlide(tx, ty, gradient) && !pushedOutSlide(tx, ty, dx, dy, gradient) && !axisSlide(dx, dy, gradient))
{
contactFreeDx = 0f;
contactFreeDy = 0f;
}
return;
}
}
if (axisSlide(dx, dy, gradient))
{
contactNx = contactFreeDx != 0f ? 0f : -Math.signum(dx);
contactNy = contactFreeDx != 0f ? -Math.signum(dy) : 0f;
return;
}
contactFreeDx = 0f;
contactFreeDy = 0f;
float d = (float) Math.hypot(dx, dy);
contactNx = d > 0f ? -dx / d : 0f;
contactNy = d > 0f ? -dy / d : 0f;
}

private boolean hitPending;

/** True when the skater ran into (or bailed on) a blocker since {@link #clearHit()} (the wall hit sound). */
public boolean hasHit()
{
return hitPending;
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
if (k > 0 && px * dx + py * dy > 0f && freeSlide(px, py, gradient))
return true;
if (!world.contact(x + px, y + py, t.skaterRadius, h, t.maxStepUp, slideHit))
break;
px += slideHit.nx * (slideHit.depth + SLIDE_CLEARANCE);
py += slideHit.ny * (slideHit.depth + SLIDE_CLEARANCE);
}
if (px * dx + py * dy > 0f && freeSlide(px, py, gradient))
return true;
}
return false;
}

/** A non-zero slide (tx, ty) from the current position that nothing blocks: taken as contactFreeDx/Dy. */
private boolean freeSlide(float tx, float ty, boolean gradient)
{
if (Math.hypot(tx, ty) > 1e-4f && !blocked(x + tx, y + ty, gradient))
{
contactFreeDx = tx;
contactFreeDy = ty;
return true;
}
return false;
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
return false;
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
// regular or fakie, whichever way along the wall the board already points
heading = Angles.turnToward(heading, closerHeading(along, heading), t.wallAlignRate * sdt);
return true;
}

/**
* Near the edge of the loaded area (within 32 beyond the skater's radius), where hits never bail: the edge is
* the end of the loaded map, not a wall the player can see, so it stops or scrapes the skater instead.
*/
private boolean atLoadedEdge()
{
return world.edgeDistance(x, y) < t.skaterRadius + 32f;
}

/** Bails off the current contact; the recovery later pushes the skater out along its normal. */
private void bailAgainstWall()
{
bail(BailReason.WALL);
vx = 0f;
vy = 0f;
rememberBailWall(contactNx, contactNy);
}

/** Out of a shape the skater overlaps (a deep or slanted bail, a wedge) along its own normal, just clear of it. */
private boolean pushOut()
{
if (!world.contact(x, y, t.skaterRadius, h, t.maxStepUp, contactHit))
return false;
x += contactHit.nx * (contactHit.depth + 0.5f);
y += contactHit.ny * (contactHit.depth + 0.5f);
return true;
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
* Front and back flips: Shift + W turns the whole skater forward, Shift + S backward, one whole flip in 0.55 s
* while held. W or S alone does nothing here, and a W / S already held at take-off (a push or crouch
* into the pop) counts only once released and pressed again; Shift may be held from the ground. Released
* (or both held), the flip stops, easing onto a whole rotation at 3 rad/s (like the spin assist) when within
* 60 degrees of one; further out it stays where it is (no auto-continue). 40 degrees short takes 0.23 s; the
* landing itself accepts up to 40 degrees (BodyFlip.LAND_TOLERANCE).
* In controller mode the left stick sends the lean keys instead of W / S, so Shift + lean up / down turns
* the flip the same way (grab-flips: a lean key with a grab held turns it regardless of Shift or mode).
*/
private void updateBodyFlip(float dt, SkateInput in)
{
flipForwardHeld &= in.pushHeld;
flipBackHeld &= in.leanBack;
leanForwardHeld &= in.leanForwardKey;
leanBackHeld &= in.leanBackKey;
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
bodyFlipAngle += dir * (TWO_PI / 0.55f) * dt;
rollOffFlight = false;
return;
}
float r = BodyFlip.residual(bodyFlipAngle);
if (r != 0f && Math.abs(r) <= (float) Math.toRadians(60))
bodyFlipAngle -= Math.copySign(Math.min(Math.abs(r), 3f * dt), r);
}

/** Emits the front / back flip completed this air (if any) and resets the angle: on landing or a rail lock. */
private void finishBodyFlip()
{
Trick trick = BodyFlip.trick(BodyFlip.rotations(bodyFlipAngle));
bodyFlipAngle = 0f;
if (trick != null)
trickEvents.add(TrickEvent.trick(trick));
}

private void stepAir(float dt, SkateInput in)
{
airTricks(dt, in);
updateBodyFlip(dt, in);
airTime += dt;
vh -= t.gravity * dt;
if (airSteerHeld && Math.abs(in.steer) < STEER_DEADZONE)
airSteerHeld = false;
float steer = airSteerHeld ? 0f : in.steer;
float headingBefore = heading;
heading = Angles.wrap(heading + steer * t.airSpinRate * (in.spinFast ? SPIN_FAST_MULT : 1f) * dt);

// no spin assist while a bigspin turns the body: it would fight the trick's own 180. Nor below
// SLOW_LANDING_SPEED (the sliver of flight a wall hit leaves): such a landing ignores the travel direction,
// and chasing the sliver turned the board (and the camera behind it) toward whichever way it pointed
if (Math.abs(steer) < STEER_DEADZONE && !bodySpinning() && Math.hypot(vx, vy) >= SLOW_LANDING_SPEED)
{
// the travel or its opposite, whichever is nearer
float target = closerHeading(flightHeading(Math.hypot(vx, vy)), heading);
if (Angles.absDiff(heading, target) <= (float) Math.toRadians(70))
{
float assisted = Angles.turnToward(heading, target, t.spinAssistRate * dt);
if (airCameraHeld)
// the assist turns the board onto the flight a wall left it with: the held camera turns
// with it, so it is behind the board again on landing instead of snapping there
airCameraHold = Angles.wrap(airCameraHold + Angles.wrap(assisted - heading));
heading = assisted;
}
}
// per-step changes are far below PI, so the wrapped difference is the true signed turn
airSpin += Angles.wrap(heading - headingBefore);

float dx = vx * dt;
float dy = vy * dt;
if (blocked(x + dx, y + dy, false))
{
// running into the box of a rail while inside its lock window catches the rail rather than
// bouncing off (or bailing on) the thing it sits on
if (tryLockInAir(in))
return;
// in the air the velocity is projected onto the wall; the board's heading is left alone
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
dx = contactFreeDx;
dy = contactFreeDy;
vx = dx / dt;
vy = dy / dt;
}
x += dx;
y += dy;
h += vh * dt;

// rising or falling: the higher pop often carries the skater past a fence before it starts to fall
if (tryLockInAir(in))
return;

float g = world.groundHeight(x, y);
if (h <= g)
land(g, in);
}

/** In the air: {@link #tryLockGrind} along the flight. */
private boolean tryLockInAir(SkateInput in)
{
float hs = (float) Math.hypot(vx, vy);
return tryLockGrind(in, flightHeading(hs), hs, false);
}

/** Direction of the flight (vx, vy) at horizontal speed {@code hs}; the heading when barely moving. */
private float flightHeading(double hs)
{
return hs > 1 ? (float) Math.atan2(vx, vy) : heading;
}

/**
* Snaps onto the nearest qualifying grind segment and enters GRINDING (HOLD_START, no LANDED: the
* combo carries on). Checked while rising and falling. The velocity turns along the rail (see
* grindSpeed below). The type comes from the board heading versus the rail: straight grinds line the
* board up with the rail, crooked grinds and boardslides keep the approach heading. A flip less than
* FLIP_LOCK_FRACTION done cannot lock on; a later one keeps turning on the rail, and a grab is
* released first (HOLD_END).
*/
private boolean tryLockGrind(SkateInput in, float travel, float hs, boolean fromRolling)
{
if (dropOffFlight || grindCooldown > 0f || hs < GRIND_LOCK_MIN_SPEED)
return false;
// the grind button held (button tricks) catches from further to the side; without it, as always
float snap = in.grindHeld ? GrindMap.ASSIST_SNAP_DISTANCE : GrindMap.SNAP_DISTANCE;
GrindMap.Hit hit = fromRolling ? grinds.nearest(x, y, h, travel, noRelockSeg, snap)
: grinds.nearestInAir(x, y, h, vh, travel, noRelockSeg, risingRelockSeg, snap);
// from ROLLING, only a rail at least 4 above the riding surface (a curb rolled into) locks on; a flip not
// yet FLIP_LOCK_FRACTION through, or upside down or sideways mid front / back flip: no rail
if (hit == null || fromRolling && hit.segment.topAt(hit.t) - h < 4f
|| flipTrick != null && flipTime < FLIP_LOCK_FRACTION * flipDuration || !BodyFlip.landable(bodyFlipAngle))
return false;
GrindSegment seg = hit.segment;
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
grindDir = -grindDir;
}
float boardAngle = seg.lineAngle(heading);
// board-to-rail angles: below 25 degrees a straight grind, below 55 a crooked grind
if (boardAngle < (float) Math.toRadians(25))
{
grindFamily = GrindFamily.STRAIGHT;
// line the board up with the rail, keeping whichever way it was pointing (regular or fakie)
heading = Angles.absDiff(heading, segHeading) <= Angles.PI / 2
? Angles.wrap(segHeading) : Angles.wrap(segHeading + Angles.PI);
}
else if (boardAngle < (float) Math.toRadians(55))
grindFamily = GrindFamily.CROOKED;
else
{
// how far the board has turned from the way it was riding into the air (fakie riding counts
// backwards): past square it went the far way round, a lipslide
float riding = airFakie ? travel + Angles.PI : travel;
grindFamily = Angles.absDiff(heading, riding) > Angles.PI / 2 ? GrindFamily.LIP : GrindFamily.SLIDE;
}
grindSeg = seg;
clearRelock();
approachRight = approachSide(seg, hit.t, travel);
grindPos = hit.t * seg.length();
// on lock the velocity is turned along the rail at max(along-rail component, 0.6 of the horizontal speed,
// 300), so a steep catch still slides
grindSpeed = Math.max(Math.abs(along), Math.max(0.6f * hs, 300f));
x = seg.xAt(hit.t);
y = seg.yAt(hit.t);
h = seg.topAt(hit.t);
vh = 0f;
speed = 0f;
state = SkaterState.GRINDING;
startHold(grindFamily.trick(in));
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
side = -((float) Math.sin(flight) * rx + (float) Math.cos(flight) * ry);
return side >= 0f ? 1f : -1f;
}

/**
* Slides along the rail at the current speed less a 60 u/s^2 friction, sped up going down a sloped
* rail and slowed going up it as on a hill. W/S switch between the grinds of the current
* {@link GrindFamily} (and never push here). A pop gesture leaves with that trick; the rail end or
* dropping below 100 u/s (was 150) leaves with a small hop. No balance bail.
*/
private void stepGrind(float dt, SkateInput in)
{
finishCaughtFlip(dt);
Trick pop = popTrick(in.gestures);
if (pop != null)
{
startPop(pop, false);
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
grindSpeed = clamp(grindSpeed - 60f * dt, 0f, t.maxSpeed);
grindPos += grindDir * grindSpeed * dt;
followConnectedSegments();
float len = grindSeg.length();
float u = grindPos / len;
x = grindSeg.xAt(u);
y = grindSeg.yAt(u);
h = grindSeg.topAt(clamp(u, 0f, 1f));
boolean slow = grindSpeed < 100f;
if (grindPos < 0f || grindPos > len || slow)
{
endHold();
// the hop was a flat 200; 100 + 0.2 * 500 keeps that at a typical 500 u/s, a slow drop-off (below
// 100 u/s) gets 150 and a 1100+ u/s grind flies off at 320
float hop = clamp(100f + 0.2f * grindSpeed, 150f, 320f);
// steer picks the side to land on; a slow drop-off with no steer falls back to the side the rail
// was approached from, never straight down beside it: the 150 u/s hop off a 60 rail lasts about
// 0.33 s, carrying the skater 50 units off the rail line instead of dropping beside (or inside) it
float side = Math.abs(in.steer) >= STEER_DEADZONE ? in.steer * GRIND_POP_SIDE_SPEED
: slow ? approachRight * 150f : 0f;
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
return;
float endX = grindDir > 0 ? grindSeg.x1 : grindSeg.x0;
float endY = grindDir > 0 ? grindSeg.y1 : grindSeg.y0;
float travel = grindTravelHeading();
GrindSegment next = grinds.connectedAt(endX, endY, grindSeg.topAt(grindDir > 0 ? 1f : 0f),
(float) Math.sin(travel), (float) Math.cos(travel), grindSeg);
if (next == null)
return;
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
heading = closerHeading(travel, heading);
noRelockSeg = popped ? null : grindSeg;
dropOffFlight = !popped;
risingRelockSeg = popped ? grindSeg : null;
grindSeg = null;
grindSpeed = 0f;
// no lock-on onto any rail for 0.1 s (was 0.45 s, which also blocked rail-to-rail transfers); the rail
// just left is guarded separately (noRelockSeg, risingRelockSeg), so this only covers the step of leaving
grindCooldown = 0.1f;
takeOff(in, travel);
}

private float grindTravelHeading()
{
float segHeading = grindSeg.heading();
return Angles.wrap(grindDir > 0 ? segHeading : segHeading + Angles.PI);
}

/**
* Advances the flip in progress, starts flips from gestures or upgrades the flip in progress on a
* re-flick, and starts/ends grabs from the held Q/E keys. A late flick is sped up to finish by the
* estimated touchdown, down to 0.6 of its own duration (0.6 * 0.35 = 0.21 s for a kickflip); any later it is ignored
* rather than started as a certain bail.
*/
private void airTricks(float dt, SkateInput in)
{
if (flipTrick != null)
flipTime = Math.min(flipDuration, flipTime + dt);
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
if (duration < 0.6f * next.duration)
{
continue; // too late to land: ignore the flick
}
Trick replaced = flipping ? flipTrick : null;
flipTrick = next;
rollOffFlight = false;
flipDuration = duration;
flipTime = u * duration;
if (replaced == null)
flipAutoSpin = 0f;
trickEvents.add(replaced == null ? TrickEvent.trick(next, airFakie)
: TrickEvent.upgrade(next, replaced, airFakie));
}
applyBodySpin();

if (activeHold == null && (in.grabLeft || in.grabRight))
{
startHold(grabStart(in));
rollOffFlight = false;
holdTime += dt;
}
else if (activeHold != null && grabHeld(in))
{
holdTime += dt;
aimAndTweakGrab(in);
}
else
endHold();
}

/** The key of the grab's hand is still held. */
private boolean grabHeld(SkateInput in)
{
return grabLeftHand ? in.grabLeft : in.grabRight;
}

/**
* The grab held: the first aim within Grabs.AIM_SECONDS of the press renames it (a HOLD_START for the new
* grab, the hold time carrying on), and held past Grabs.TWEAK_SECONDS it turns into its tweak once.
*/
private void aimAndTweakGrab(SkateInput in)
{
Trick grab = aims(in, holdTime) ? Grabs.pick(grabLeftHand, in.grabAim) : activeHold;
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
clearRelock();
h = g;
lastLandingSpeed = Math.max(0f, -vh);
vh = 0f;
float hs = (float) Math.hypot(vx, vy);
if (hs < SLOW_LANDING_SPEED)
{
speed = (float) Math.sin(heading) * vx + (float) Math.cos(heading) * vy;
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
// keep travelling where the skater was going; the travel turns onto the board over 0.15 s
landSlip = Angles.wrap(travel - heading);
}
else if (diff >= Angles.PI - tol)
{
speed = -hs;
landSlip = Angles.wrap(travel - heading - Angles.PI);
}
else
{
bail(BailReason.SIDEWAYS);
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
: Math.abs(landSlip) <= (float) Math.toRadians(15) && flipTrick == null ? LandingQuality.CLEAN : LandingQuality.SLOPPY;
// the flip is caught; one not quite done keeps turning on the ground (finishCaughtFlip) instead of
// snapping to neutral
// the air grab, if its key is still held: after its release the skater keeps it as a rolling grab
Trick keptGrab = activeHold != null && activeHold.kind == TrickKind.GRAB && grabHeld(in) ? activeHold : null;
endHold(); // a grab auto-releases on touchdown, no bail
if (spinHalfTurns != 0)
// named before the LANDED (or the manual's HOLD_START) so the scorer renames this air's trick
trickEvents.add(TrickEvent.spin(spinHalfTurns));
finishBodyFlip(); // its own combo entry, after the spin
// a near-vertical landing (below SLOW_LANDING_SPEED, e.g. dropping back off a wall hit in the air) is
// never fakie: its speed is a sliver either way, and a fakie flag there made every W push go backwards,
// pinning the skater against the wall it had just bounced off
fakie = speed < 0f && hs >= SLOW_LANDING_SPEED;
landSlipRate = Math.abs(landSlip) / 0.15f;
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
if (blocked(x + vx * dt, y + vy * dt, false))
{
// slid into something: remember it so the recovery faces away from it
float d = (float) Math.hypot(vx, vy);
if (d > 0f)
rememberBailWall(-vx / d, -vy / d);
vx = 0f;
vy = 0f;
}
else
{
x += vx * dt;
y += vy * dt;
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
h = g;
if (h == g)
vh = 0f;

if (bailTimer == 0f && resetQueued || recoverTimer <= 0f)
{
if (bailWall)
recoverFromWall();
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
// at most 24 out along the wall's normal
for (float d = 24f; d > 0f; d -= GROUND_SUBSTEP)
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
// still overlapping a shape (a deep or slanted bail): out along its own normal
pushOut();
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
if (pushOut())
h = world.groundHeight(x, y);
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
if (blockerAbove(nx, ny))
return true;
float rise = world.groundHeight(nx, ny) - h;
return !(rise <= t.maxStepUp) && (!gradient || rise > MAX_GROUND_GRADIENT * (float) Math.hypot(nx - x, ny - y));
}

/**
* A ground move to (nx, ny) that {@link #blocked} refused: true (and the speed paid) when it was refused only
* for the terrain itself rising more than maxStepUp, and the skater is fast enough to carry up it. Blockers,
* platforms, landable boxes and anything past the loaded area (or at an infinite height) stay walls.
*/
/** A blocker taller than a step is crossed moving from here to (nx, ny). */
private boolean blockerAbove(float nx, float ny)
{
return world.blockerTop(x, y, nx, ny, t.skaterRadius) > h + t.maxStepUp;
}

private boolean momentumClimb(float nx, float ny)
{
if (blockerAbove(nx, ny) || !(world.edgeDistance(nx, ny) > 0f))
return false;
float g = world.groundHeight(nx, ny);
float rise = g - h;
if (g != world.terrainHeight(nx, ny) || !(rise < Float.POSITIVE_INFINITY)
|| Math.abs(speed) < t.momentumClimbSpeedPerRise * rise)
return false;
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
// on a platform or landable box: its flat top is no ramp, whatever the terrain underneath does
return 0f;
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
return 0f;
float ahead = (world.terrainHeight(x + tx * d, y + ty * d) - here) / d;
if (!(approach - ahead >= t.rampLaunchSlopeDrop))
return 0f;
// a bank climbed on momentum can be far steeper than any hill: launch at most as a maxSlope ramp would
return Math.abs(speed) * Math.min(approach, t.maxSlope);
}

private void bail(BailReason reason)
{
lastBailReason = reason;
// a hold cut short by a bail still reports its length before the BAILED that ends the combo
endHold();
clearFlip();
clearRelock();
if (grounded())
velocityAlongMotion();
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


/** ROLLING or MANUAL: on the ground, moving by signed {@code speed} along the heading. */
private boolean grounded()
{
return state == SkaterState.ROLLING || state == SkaterState.MANUAL;
}

public List<SkateEvent> drainEvents()
{
return drain(events);
}

public List<TrickEvent> drainTrickEvents()
{
return drain(trickEvents);
}

private static <E> List<E> drain(List<E> list)
{
List<E> out = new ArrayList<>(list);
list.clear();
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
return hold == Trick.NOSEGRIND ? -GRIND_PITCH : hold == Trick.FIVE_O ? GRIND_PITCH : 0f;
return state != SkaterState.MANUAL ? 0f : hold == Trick.NOSE_MANUAL ? -MANUAL_PITCH : MANUAL_PITCH;
}


/** Progress 0..1 of the current flip (0 when none). */
public float getFlipProgress()
{
return flipTrick == null || flipDuration <= 0f ? 0f : clamp(flipTime / flipDuration, 0f, 1f);
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
return;
float target = flipTrick.bodyYawTurns * TWO_PI * flipEased();
float delta = target - flipAutoSpin;
flipAutoSpin = target;
heading = Angles.wrap(heading + delta);
airSpin += delta;
airAutoSpin += delta;
}


/**
* The rolling grab held on the ground (pose only, never scored), or null: the grab the body crouches into while
* ROLLING with a grab key held.
*/
public Trick getGroundGrab()
{
return state == SkaterState.ROLLING ? groundGrab : null;
}


/** Step and horizon of the flight prediction in {@link #getPredictedGrind()} (the session's fixed step). */
private static final float PREDICT_STEP = 0.02f;

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
return null;
float travel = flightHeading(hs);
float px = x;
float py = y;
float ph = h;
float pvh = vh;
float cooldown = grindCooldown;
float ft = flipTime;
for (int k = 0; k < 100; k++)
{
cooldown = Math.max(0f, cooldown - PREDICT_STEP);
if (flipTrick != null)
ft = Math.min(flipDuration, ft + PREDICT_STEP);
pvh -= t.gravity * PREDICT_STEP;
px += vx * PREDICT_STEP;
py += vy * PREDICT_STEP;
ph += pvh * PREDICT_STEP;
if (cooldown <= 0f && (flipTrick == null || ft >= FLIP_LOCK_FRACTION * flipDuration))
{
GrindMap.Hit hit = grinds.nearestInAir(px, py, ph, pvh, travel, noRelockSeg, risingRelockSeg);
if (hit != null)
return hit.segment;
}
if (ph <= world.groundHeight(px, py))
return null;
}
return null;
}

/** Eased progress of the current flip, 0..1. */
private float flipEased()
{
return flipEase(flipTime / flipDuration);
}

/**
* Ease-out 1 - (1 - u)^2 of a progress fraction (clamped to 0..1): the board leaves the foot fast and
* slows into the catch. Smoothstep turned only 5% in the first 50 ms and was 84% round at the 75%
* catch point, so a caught flip jumped 58 degrees; ease-out is 94% round there. Public for drawing other
* skaters' flips the same way.
*/
public static float flipEase(float u)
{
u = clamp(u, 0f, 1f);
return 1f - (1f - u) * (1f - u);
}

/**
* On a re-flick upgrade (e.g. KICKFLIP -> DOUBLE_KICKFLIP) the new trick has a different rollTurns,
* so keeping the progress literally would jump the board roll. This is the progress u of {@code next}
* whose eased roll equals {@code targetRoll}: the inverse of the ease-out, u = 1 - sqrt(1 - e).
*/
private static float progressForRoll(Trick next, float targetRoll)
{
if (next.rollTurns == 0f)
return 0f;
float e = clamp(targetRoll / (next.rollTurns * TWO_PI), 0f, 1f);
return 1f - (float) Math.sqrt(1f - e);
}


/**
* True when a rail is near the skater (within 192 horizontally, its top at most 256 above the board): the grind
* button grinds there rather than stepping off.
*/
public boolean isRailNear()
{
return grinds.railWithin(x, y, h, 192f, 256f);
}


/** Signed speed along heading while rolling or in a manual; rail speed while grinding; horizontal speed when airborne or bailed. */
public float getSpeed()
{
return state == SkaterState.GRINDING ? grindSpeed : grounded() ? speed : (float) Math.hypot(vx, vy);
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
return airCameraHeld ? airCameraHold
: Math.hypot(vx, vy) > 1 ? closerHeading((float) Math.atan2(vx, vy), airCameraRef) : airCameraRef;
case BAILED:
return heading;
default:
return rollingCameraHeading();
}
}


/** Direction of actual movement (heading when stationary). */
public float getTravelHeading()
{
if (state == SkaterState.GRINDING)
return grindTravelHeading();
if (grounded())
{
float motion = heading + landSlip;
return Angles.wrap(speed < 0 ? motion + Angles.PI : motion);
}
return flightHeading(Math.hypot(vx, vy));
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

/**
* Off-board mode: a freshly made skater (still ROLLING) gets on rolling forward at {@code s} u/s, e.g. a
* jump-mount carrying the jump's speed. Tests also set the rolling speed with it.
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

private static float towardZero(float v, float amount)
{
return v > 0 ? Math.max(0f, v - amount) : Math.min(0f, v + amount);
}

private static float clamp(float v, float lo, float hi)
{
return Math.max(lo, Math.min(hi, v));
}
}
