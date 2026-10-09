package com.gielinorskate.party;

import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.*;

/**
* A light version of the local skater's procedural body for a party ghost: the same {@link SkaterPoseRig}, fed
* from what the ghost's updates carry. Lean from the turn rate, the pop charge's crouch, a push kick when a push
* is played back (GhostPredictor.pushCount), the air tuck after a pop, the
* landing squash and the bail fall from the update's events, grabs, manual and grind pitch, and the front / back
* flip; knocked down, the knockdown's body (KnockdownBody). Pure; one per ghost.
*/
final class GhostRig
{
private final SkaterPoseRig rig = new SkaterPoseRig();
private final SkaterPoseRig.Signals signals = new SkaterPoseRig.Signals();
private final FootBody foot = new FootBody();
private int pops;
private int lands;
private int bails;
private int pushes;
/** The last frame was a knockdown: the next one starts the rig from standing. */
private boolean knocked;
private SkaterState lastState;
/** Vertical speed (up > 0) last seen in the air, for the landing squash. */
private float airSpeed;

/** One render frame: steps the rig from {@code ghost} as drawn ({@code pose}) and writes it to {@code out}. */
void update(GhostPredictor ghost, RenderPose pose, float now, float dt, BodyPose out)
{
SkaterPoseRig.Signals s = signals;
startAfresh();
boolean first = lastState == null;
// a ghost first drawn just after a pop still extends and tucks
// Seconds: a pop this recent when the ghost is first drawn still counts (the rig extends for 0.12 s).
boolean popped = first ? ghost.sincePop(now) <= 0.1f : ghost.popCount() != pops;

s.state = pose.state;
s.speed = ghost.speed();
s.carveRate = ghost.turnRate(now);
s.charge = pose.state == SkaterState.AIRBORNE ? 0f : ghost.charge();
s.verticalSpeed = ghost.verticalSpeed(now);
// the hold's pitch only: an impossible's pitch is the board's, not the body's
s.boardPitch = SkatePhysics.boardPitchFor(pose.state, pose.hold);
s.hold = pose.hold;
s.flipping = ghost.flipping(now);
s.popped = popped;
s.rolledOff = !first && !popped && pose.state == SkaterState.AIRBORNE && lastState != SkaterState.AIRBORNE;
s.landed = !first && ghost.landCount() != lands;
s.bailed = !first && ghost.bailCount() != bails;
s.landingSpeed = Math.abs(airSpeed);
s.pushed = !first && ghost.pushCount() != pushes;
s.proceduralPush = true;
s.bodyFlipAngle = ghost.bodyFlip(now);
s.onFoot = false;
// the next step off starts from standing
foot.reset();
step(ghost, pose, dt, out);
}

/**
* One render frame of a ghost on foot: upright (the walk or run is the OSRS animation under it), a light tuck
* in a jump (a pop on the wire) and the landing squash, as the local walker's; the gait follows the speed.
*/
void updateOnFoot(GhostPredictor ghost, RenderPose pose, float now, float dt, BodyPose out)
{
startAfresh();
boolean airborne = pose.state == SkaterState.AIRBORNE;
// Seconds: a pop this recent when an on-foot ghost leaves the ground makes it a jump, not a fall.
boolean jumping = ghost.popCount() != pops || ghost.sincePop(now) <= 0.5f;
foot.fill(signals, ghost.speed(), airborne, jumping, ghost.verticalSpeed(now));
step(ghost, pose, dt, out);
}

/** The walker's gait (from its speed), for the walk or run animation. */
FootBody.Gait gait()
{
return foot.gait();
}

/**
* One frame knocked down: the knockdown's body (the tumble, or with an OSRS lie-down or get-up playing, the body
* as animated); the next skate or walk frame starts the rig from standing, as the local skater's does.
*/
void knockdown(KnockdownPose k, boolean animated, BodyPose out)
{
KnockdownBody.write(k, animated, out);
knocked = true;
}

/** The rig stepped and written; the ghost's events so far kept (the next frame's new ones are past these). */
private void step(GhostPredictor ghost, RenderPose pose, float dt, BodyPose out)
{
rig.update(signals, dt);
rig.writeTo(out);
if (pose.state == SkaterState.AIRBORNE)
airSpeed = signals.verticalSpeed;
lastState = pose.state;
pops = ghost.popCount();
lands = ghost.landCount();
bails = ghost.bailCount();
pushes = ghost.pushCount();
}

private void startAfresh()
{
if (knocked)
{
knocked = false;
rig.reset();
foot.reset();
lastState = null;
}
}
}
