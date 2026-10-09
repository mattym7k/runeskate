package com.gielinorskate.render;

import com.gielinorskate.Text;
import com.gielinorskate.physics.*;
import com.gielinorskate.render.AnimationPicker.Action;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.AnimationID;

/**
* Drives the hidden local player's animations from physics state, and the procedural body pose layered on
* top of them ({@link SkaterPoseRig} into {@link #getBodyPose()}, which the puppet applies to the mesh).
* The stance animation stays on all the time and every move is procedural, so nothing cuts. Client thread only.
*/
@Slf4j
@RequiredArgsConstructor
public final class SkaterAnimator
{
private static final int NO_ANIMATION = -1;
/**
* The riding stance, also while grinding and in a manual. Crouching, pushing, the pop, the air tuck and the bail
* fall are procedural ({@link SkaterPoseRig}) on it: OSRS animations cannot blend, so switching cut hard
* (HUMAN_CRATE_SQUAT made the skater vanish, the walk cycle looked like walking on the board).
*/
private static final int STANCE = AnimationID.HUMAN_SKI_IDLE;

private final Client client;
private final PoseGuard guard = new PoseGuard();
private final SkaterPoseRig rig = new SkaterPoseRig();
private final SkaterPoseRig.Signals signals = new SkaterPoseRig.Signals();
/** The procedural pose for the puppet, updated every frame. */
@Getter
private final BodyPose bodyPose = new BodyPose();
/**
* Handed to the puppet, which reports whether each draw had a model: spares fetching the animated model again for
* the guard.
*/
@Getter
private final DrawnModelReport drawnReport = new DrawnModelReport();
private final FootBody footBody = new FootBody();
/** False once a walk or run animation left the player with no model: on foot stands in the idle pose. */
private boolean footGaitAnimations = true;
/** Plays the walk or run animation as much faster as the walker is faster than the game's walk or run. */
private final GaitPlayback gaitPlayback = new GaitPlayback();
/** Classic animations' definitions (frame lengths) by id, loaded once; null for one that can't be stepped. */
private final Map<Integer, Animation> classic = new HashMap<>();
/** False once stepping the gait animation threw: it plays at the game's speed from then on. */
private boolean gaitSpeedUp = true;
private float lastHeading = Float.NaN;
private String warning;

private boolean started;
/** The player's own idle pose, given back on exit; follows the game's changes while skating. */
private final IdlePoseTracker ownIdlePose = new IdlePoseTracker();
private Action currentAction = Action.NONE;
private float actionTimeLeft;
private int lastIdlePose = Integer.MIN_VALUE;
/** The last frame was on foot: the idle pose is a walk / run / own idle, which the board must replace. */
private boolean idleFromFoot;
/** The knockdown's lie-down and get-up animations play (false once one left the skater with no model). */
private boolean knockdownAnimations = true;
/** The knockdown animation playing now, or NO_ANIMATION. */
private int knockAnimation = NO_ANIMATION;
/** A duel ending's emote playing now (the tantrum's stamp, the cheer), or NO_ANIMATION. */
private int emoteAnimation = NO_ANIMATION;
/** The duel endings' emotes play (false once one left the skater with no model: the pose alone then). */
private boolean emoteAnimations = true;

public void start()
{
resetBody();
drawnReport.clear();
Player p = client.getLocalPlayer();
if (p == null)
return;
ownIdlePose.start(p.getIdlePoseAnimation());
footGaitAnimations = true;
emoteAnimations = true;
emoteAnimation = NO_ANIMATION;
knockdownAnimations = true;
knockAnimation = NO_ANIMATION;
setIdlePose(p, STANCE);
started = true;
noAction();
lastIdlePose = Integer.MIN_VALUE;
idleFromFoot = false;
}

/** @param pose the (interpolated) pose drawn this frame */
public void update(RenderPose pose, SkatePhysics physics, List<SkateEvent> events, float dt)
{
Player p = client.getLocalPlayer();
if (p == null)
return;
noteGameIdlePose(p);
// carve rate from the drawn (interpolated) heading, so it is smooth between physics steps; a reset
// teleports the heading, which is not a carve
float rate = 0f;
if (!Float.isNaN(lastHeading) && dt > 0f && !events.contains(SkateEvent.RESET))
rate = Angles.wrap(pose.heading - lastHeading) / dt;
lastHeading = pose.heading;
SkaterPoseRig.Signals s = signals;
s.state = pose.state;
s.speed = physics.getSpeed();
s.carveRate = rate;
s.charge = physics.getPopCharge();
s.verticalSpeed = physics.getVerticalVelocity();
s.boardPitch = pose.boardPitch;
// rolling with a grab key held the body grabs the board on the ground (pose only, the physics' ground grab)
s.hold = pose.hold != null ? pose.hold : physics.getGroundGrab();
s.flipping = pose.boardRoll != 0f || pose.boardYaw != 0f;
s.popped = events.contains(SkateEvent.POP);
s.rolledOff = events.contains(SkateEvent.ROLL_OFF);
s.landed = events.contains(SkateEvent.LAND);
s.bailed = events.contains(SkateEvent.BAIL);
s.landingSpeed = physics.getLastLandingSpeed();
s.pushed = events.contains(SkateEvent.PUSH);
s.proceduralPush = true;
// front/back flips: the body turns head over heels by the physics body-flip angle (0 when upright)
s.bodyFlipAngle = physics.getBodyFlipAngle();
s.onFoot = false;
// the grabbing hand: Q the left, E the right
s.grabHand = physics.isGrabLeftHand() ? 1 : -1;
rig.update(s, dt);
rig.writeTo(bodyPose);
// the next step off starts from standing
footBody.reset();
SkaterState st = physics.getState();
// just got on: back to the stance at once, even airborne (a jump-mount off an edge), where the idle pose is
// otherwise left alone and the walk or run legs would keep cycling until the landing
if (idleFromFoot || (st == SkaterState.ROLLING || st == SkaterState.MANUAL || st == SkaterState.GRINDING)
&& lastIdlePose != STANCE)
{
idleFromFoot = false;
setIdlePose(p, STANCE);
}

actionTimeLeft = Math.max(0f, actionTimeLeft - dt);
Action next = AnimationPicker.pick(st, events, currentAction, actionTimeLeft);
if (next != currentAction)
{
currentAction = next;
actionTimeLeft = next == Action.PUSH ? AnimationPicker.PUSH_SECONDS : 0f;
play(p, NO_ANIMATION);
}

if (guard.frame(hasModel(p)))
{
// an animation that doesn't work on player models makes the skater vanish: fall back to the stance
setIdlePose(p, STANCE);
p.setAnimation(NO_ANIMATION);
noAction();
warning = Text.get("skanim.poseVanished");
}
}

/**
* On foot: the player's own OSRS idle, walk or run pose animation by the walker's speed (the real character
* stands still, so its idle pose is what plays: the walk cycles in place while the puppet moves), no action
* animation, and the procedural body for jumps (a tuck, then the landing squash).
*/
public void updateOnFoot(OffBoardPose pose, float dt)
{
Player p = client.getLocalPlayer();
if (p == null)
return;
if (currentAction != Action.NONE)
{
noAction();
play(p, NO_ANIMATION);
}
noteGameIdlePose(p);
idleFromFoot = true;
footBody.fill(signals, pose.walkerSpeed, pose.airborne, pose.jumping, pose.verticalSpeed);
FootBody.Gait gait = footBody.gait();
// the player's own pose animation for the gait (the idle one if the game has none for it)
int run = gait == FootBody.Gait.RUN ? p.getRunAnimation() : -1;
int walk = gait != FootBody.Gait.IDLE ? p.getWalkAnimation() : -1;
int idlePose = !footGaitAnimations ? realIdlePose(p) : run >= 0 ? run : walk >= 0 ? walk : realIdlePose(p);
if (idlePose != lastIdlePose)
{
setIdlePose(p, idlePose);
gaitPlayback.reset();
}
float rate = GaitPlayback.rate(gait, pose.walkerSpeed);
if (footGaitAnimations && gait != FootBody.Gait.IDLE && gaitSpeedUp && rate > 1f)
{
// the walker is faster than the game's walk and run, so the gait animation is stepped on by the frames
// the game's own playback leaves out, keeping the feet from sliding. Only a classic (frame-based)
// animation that is the one playing now is touched; anything unexpected leaves it at the game's speed.
try
{
Animation a = classic(idlePose);
int frame = a == null || p.getPoseAnimation() != idlePose ? -1
: gaitPlayback.advance(p.getPoseAnimationFrame(), a.getFrameLengths(), rate, dt);
if (frame >= 0)
p.setPoseAnimationFrame(frame);
}
catch (RuntimeException ex)
{
gaitSpeedUp = false;
log.warn(Text.get("skanim.gaitSpeedFailed"), ex);
}
}
rig.update(signals, dt);
rig.writeTo(bodyPose);
// getting back on is no carve
lastHeading = Float.NaN;

if (guard.frame(hasModel(p)))
{
// a walk or run animation that leaves no model: stand in the player's own idle for the session
footGaitAnimations = false;
setIdlePose(p, realIdlePose(p));
warning = Text.get("skanim.walkVanished");
}
}

/**
* Knocked off the board: the tumble in the air is procedural (the whole body turned about its centre of mass);
* once down the OSRS lie-down loop plays (the no-delay knockdown loop lies down from its first frame: the cut to it
* comes at the impact, where the smoke and the camera kick hide it), then the get-up animation squeezed into the
* get-up's time (each falls back to the procedural pose for the session when one leaves the skater with no
* model). Animations go on the hidden local player only, as every skate animation does.
*/
public void updateKnockdown(KnockdownPose k, float dt)
{
Player p = client.getLocalPlayer();
if (p == null)
return;
noteGameIdlePose(p);
noAction();
int want = !knockdownAnimations ? NO_ANIMATION : k.stage == KnockdownPose.Stage.LIE
? AnimationID.HUMAN_KNOCKDOWN_LOOP_NODELAY : k.stage == KnockdownPose.Stage.GET_UP ? AnimationID.HUMAN_GETUP
: NO_ANIMATION;
if (want != knockAnimation)
{
knockAnimation = want;
play(p, want);
}
if (want != NO_ANIMATION && k.stage == KnockdownPose.Stage.GET_UP)
scrub(p, want, k.progress);
KnockdownBody.write(k, want != NO_ANIMATION, bodyPose);
// carving starts afresh once back on
lastHeading = Float.NaN;

if (guard.frame(hasModel(p)))
{
knockdownAnimations = false;
knockAnimation = NO_ANIMATION;
p.setAnimation(NO_ANIMATION);
warning = Text.get("skanim.knockdownVanished");
}
}

/** The knockdown is over (standing, or back on the board with R): its animation stops, the body starts afresh. */
public void endKnockdown()
{
Player p = client.getLocalPlayer();
if (p != null && knockAnimation != NO_ANIMATION)
play(p, NO_ANIMATION);
knockAnimation = NO_ANIMATION;
noAction();
resetBody();
}

/**
* A duel tantrum {@code t} seconds in ({@link TantrumSequence}): standing in the player's own idle pose, the
* stamp-feet emote squeezed into the wind-up, both arms holding the board (the procedural pose). The emote goes on
* the hidden local player only, as every skate animation does.
*/
public void updateTantrum(float t, float dt)
{
Player p = client.getLocalPlayer();
if (p == null)
return;
noteGameIdlePose(p);
noAction();
// standing: back in the stance once on the board again
idleFromFoot = true;
if (realIdlePose(p) != lastIdlePose)
setIdlePose(p, realIdlePose(p));
int want = emoteAnimations ? TantrumSequence.animation(t) : NO_ANIMATION;
if (want != emoteAnimation)
{
emoteAnimation = want;
play(p, want);
}
if (want != NO_ANIMATION)
scrub(p, want, TantrumSequence.progress(t));
resetBody();
TantrumSequence.writeBody(t, bodyPose);
// an emote that leaves the skater with no model is not played again this session
if (guard.frame(hasModel(p)))
{
emoteAnimations = false;
emoteAnimation = NO_ANIMATION;
p.setAnimation(NO_ANIMATION);
warning = Text.get("skanim.tantrumVanished");
}
}

/** The tantrum is over (or cut short): its emote stops, the body starts afresh. */
public void endTantrum()
{
stopEmote();
resetBody();
}

/** The winner's celebration starts: {@code animationId} (the cheer on the board, jump for joy on foot). */
public void startCelebration(int animationId)
{
Player p = client.getLocalPlayer();
if (p != null && emoteAnimations)
{
noAction();
emoteAnimation = animationId;
play(p, animationId);
}
}

/**
* The celebration {@code progress} (0..1) of the way through: its emote is paced to the celebration's length
* (left to the game's speed if it can't be). Call after the frame's own update; a trick's animation that took over
* is left alone.
*/
public void paceCelebration(float progress)
{
Player p = client.getLocalPlayer();
if (p != null && emoteAnimation != NO_ANIMATION)
scrub(p, emoteAnimation, progress);
}

/** A duel ending's emote stops (skipped or over), unless something else already replaced it. */
public void stopEmote()
{
Player p = client.getLocalPlayer();
if (p != null && emoteAnimation != NO_ANIMATION && p.getAnimation() == emoteAnimation)
play(p, NO_ANIMATION);
emoteAnimation = NO_ANIMATION;
}

/** Shows the frame of a classic animation {@code progress} of the way through it (left alone otherwise). */
private void scrub(Player p, int animationId, float progress)
{
try
{
Animation a = classic(animationId);
int frame = a == null || p.getAnimation() != animationId ? -1
: AnimClock.frameAt(progress, a.getFrameLengths());
if (frame >= 0)
p.setAnimationFrame(frame);
}
catch (RuntimeException ex)
{
classic.put(animationId, null);
log.debug(Text.get("skanim.paceFailed"), ex);
}
}

/** {@code id}'s definition, loaded once; null when it is no classic (frame-based) animation. */
private Animation classic(int id)
{
if (!classic.containsKey(id))
{
Animation a = id >= 0 ? client.loadAnimation(id) : null;
classic.put(id, a != null && !a.isMayaAnim() ? a : null);
}
return classic.get(id);
}

/** Plays {@code id} (NO_ANIMATION: none) from its first frame. */
private static void play(Player p, int id)
{
p.setAnimation(id);
p.setAnimationFrame(0);
}

private void noAction()
{
currentAction = Action.NONE;
actionTimeLeft = 0f;
}

/** The idle pose the player had before skating (the skate stance replaced it). */
private int realIdlePose(Player p)
{
return started ? ownIdlePose.restorePose() : p.getIdlePoseAnimation();
}

/** Sets the player's idle pose, noting it was ours. */
private void setIdlePose(Player p, int pose)
{
p.setIdlePoseAnimation(pose);
ownIdlePose.wrote(pose);
lastIdlePose = pose;
}

/**
* The game may have set a new idle pose (a weapon wielded while skating): it becomes the one given back on
* exit, and ours is written again.
*/
private void noteGameIdlePose(Player p)
{
if (started && ownIdlePose.observe(p.getIdlePoseAnimation()))
lastIdlePose = Integer.MIN_VALUE;
}

/** A message for the player if the animator had to recover, else null. Clears it. */
public String takeWarning()
{
String w = warning;
warning = null;
return w;
}

public void stop()
{
Player p = client.getLocalPlayer();
if (started && p != null)
{
noteGameIdlePose(p);
setIdlePose(p, ownIdlePose.restorePose());
p.setAnimation(NO_ANIMATION);
}
started = false;
resetBody();
knockAnimation = NO_ANIMATION;
emoteAnimation = NO_ANIMATION;
noAction();
lastIdlePose = Integer.MIN_VALUE;
}

/**
* Whether the player has a model, for the guard: the puppet's latest draw when it reported one since the last
* check (the model it fetched anyway), else a fetch of our own.
*/
private boolean hasModel(Player p)
{
return drawnReport.isFresh() ? drawnReport.take() : p.getModel() != null;
}

private void resetBody()
{
rig.reset();
bodyPose.neutral();
footBody.reset();
lastHeading = Float.NaN;
}
}
