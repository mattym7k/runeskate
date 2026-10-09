package com.gielinorskate.render;

import com.gielinorskate.physics.*;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import java.util.*;
import lombok.Setter;
import net.runelite.api.*;

/** Positions the puppet and board from physics state each frame. Client thread only. */
public final class SkaterRenderer
{
/** Further than this (a call-back) the board appears in the hand instead of flying there. */
static final float BOARD_MOVE_REACH = 2 * 128f;

private final Client client;
private PuppetController puppet;
/**
* One object per part of the board drawn (the baked board has up to four), all placed identically each frame;
* the first {@link #boardCount} are in use (none when not spawned).
*/
private final BoardController[] boards = new BoardController[4];
private int boardCount;
/** The board model drawn now; null when not spawned. */
private BakedBoardModel poser;
private int worldViewId;
private int plane;
/**
* The baked (RuneSkate) board in high (true) and in low ("Normal") detail, each built on first use and kept. A
* detail that failed to build (null) isn't tried again.
*/
private final Map<Boolean, BakedBoardModel> baked = new HashMap<>();
private boolean highDetail = true;
/** The chosen designs, for the next board built and applied to the current one. */
private BoardLook look = BoardLook.defaults(BoardDesigns.bundled());
/** The procedural body pose the puppet draws with (neutral until set). */
@Setter
private BodyPose bodyPose = new BodyPose();
/** Told by the puppet whether each draw had a model (null until set). */
@Setter
private DrawnModelReport drawnReport;
private final RidingBoard riding = new RidingBoard();
/** Seconds since the last pop (large when none recently). */
private float sincePop;
/** Seconds since the last stumble (large when none recently): the board wobbles for a moment. */
private float sinceStumble = Float.MAX_VALUE;
private boolean popNollie;
private float lean;
private float chargeDip;
/** The board objects are registered. */
private boolean boardShown;

/** This frame's body and board placements, and the ones drawn last frame (a blend starts from those). */
private final Placement bodyTarget = new Placement();
private final Placement boardTarget = new Placement();
private final Placement drawnBody = new Placement();
private final Placement drawnBoard = new Placement();
private final Placement feetBoard = new Placement();
/** The walker's right hand read off the drawn mesh: the carried board follows it through the walk cycle. */
private final HandAnchor hand = new HandAnchor();
private final SwapBlend swap = new SwapBlend();
private final SwapBlend boardMove = new SwapBlend();
private float boardMoveTime;
private BoardTransition transition = BoardTransition.NONE;
private float transitionProgress = 1f;
private BoardTransition lastTransition = BoardTransition.NONE;
private float lastProgress = 1f;
/** The board's state last drawn on foot; null on the board. */
private BoardState lastBoardState;
/** A duel tantrum's snapped board: its two halves, flying and lying on whatever the skater does next. */
private final SnappedBoard halves;
/** Where the board was drawn when the tantrum began: the grab takes it from there to the hands. */
private final Placement tantrumFrom = new Placement();
private final float[] tantrumHold = new float[4];

public SkaterRenderer(Client client)
{
this.client = client;
this.halves = new SnappedBoard(client);
}

/**
* Draws the board in {@code look}'s designs, now if skating (only the colours change: the parts are lit again
* at their next pose) and on every board built after. Client thread.
*/
public void setLook(BoardLook look)
{
this.look = look;
for (BakedBoardModel m : baked.values())
{
if (m != null)
m.setLook(look);
}
for (int i = 0; i < boardCount; i++)
boards[i].repaint();
}

/**
* The baked board's detail for the local skater: high (each part fills the renderers' per-model budget) or
* normal (the low variant party ghosts use too). Applied now if the baked board is drawn. Client thread.
*/
public void setBoardDetail(boolean high)
{
if (high == highDetail)
return;
highDetail = high;
BakedBoardModel m = boardCount > 0 ? bakedModel() : null;
if (m != null)
// a detail that can't be built leaves the board drawn as it was
setPoser(m);
}

/** The baked board at the chosen detail, built on first use; null if it can't be built. */
private BakedBoardModel bakedModel()
{
if (!baked.containsKey(highDetail))
baked.put(highDetail, BakedBoardModel.create(client, highDetail, look));
BakedBoardModel m = baked.get(highDetail);
if (m != null)
m.setLook(look);
return m;
}

/**
* Draws the board with {@code poser}: one object per part, the extra ones created, placed where the board was
* last drawn and shown (or hidden and dropped) to match.
*/
private void setPoser(BakedBoardModel poser)
{
this.poser = poser;
int n = poser.partCount();
for (int i = 0; i < boards.length; i++)
{
if (i < n && boards[i] == null)
{
boards[i] = new BoardController(i);
boards[i].setWorldView(worldViewId);
boards[i].setLevel(plane);
if (drawnBoard.valid)
place(boards[i], drawnBoard);
}
if (i < n)
boards[i].setBoard(poser, bodyPose);
if (boardShown && i < n != i < boardCount)
show(boards[i], i < n);
if (i >= n)
boards[i] = null;
}
boardCount = n;
}

private static void place(RuneLiteObjectController c, Placement p)
{
c.setX(Math.round(p.x));
c.setY(Math.round(p.y));
c.setZ(Math.round(p.z));
c.setOrientation(p.orientation());
}

private void show(RuneLiteObjectController c, boolean show)
{
if (show)
client.registerRuneLiteObject(c);
else
client.removeRuneLiteObject(c);
}

/** Spawns the puppet and board; false (nothing spawned) when the board can't be built. */
public boolean spawn(WorldView wv, int plane)
{
BakedBoardModel model = bakedModel();
if (model == null)
return false;
hand.setActive(false);
puppet = new PuppetController(client, bodyPose, hand);
puppet.setDrawnReport(drawnReport);
puppet.setWorldView(wv.getId());
puppet.setLevel(plane);
worldViewId = wv.getId();
this.plane = plane;
Arrays.fill(boards, null);
boardCount = 0;
boardShown = false;
riding.reset();
sincePop = Float.MAX_VALUE;
sinceStumble = Float.MAX_VALUE;
popNollie = false;
lean = 0f;
chargeDip = 0f;
drawnBody.invalidate();
drawnBoard.invalidate();
swap.cancel();
boardMove.cancel();
transition = BoardTransition.NONE;
transitionProgress = 1f;
lastTransition = BoardTransition.NONE;
lastProgress = 1f;
lastBoardState = null;
setPoser(model);
client.registerRuneLiteObject(puppet);
showBoard(true);
return true;
}

/**
* Draws {@code p}, the (possibly interpolated) pose of this frame.
*
* @param nolliePop a pop on this frame was a nollie (popped off the nose)
*/
public void update(RenderPose p, float steer, boolean crouching, List<SkateEvent> events, boolean nolliePop, float dt)
{
if (puppet == null)
return;
showBoard(true);
boolean popped = events.contains(SkateEvent.POP);
sincePop = popped ? 0f : sincePop + dt;
popNollie = popped ? nolliePop : popNollie;
sinceStumble = events.contains(SkateEvent.STUMBLE) ? 0f : sinceStumble + dt;

boolean bailed = p.state == SkaterState.BAILED;

// trick board pose: carve lean (0.15 rad per unit of steer) only while rolling on the ground, plus the
// physics-driven flip roll / nose-pop pitch / manual-and-grind pitch; the mesh is rebuilt only when roll or
// pitch actually changes by more than 0.01 rad or the body flip turns (read by the board when drawn).
// Lean and hold pitch ease in and out; the pop pitch follows its own rise-and-fall curve.
boolean carving = p.state == SkaterState.ROLLING;
lean = PoseSmoothing.approach(lean, carving ? -steer * 0.15f : 0f, dt, PoseSmoothing.POSE_TAU);
boolean charging = crouching && (p.state == SkaterState.ROLLING || p.state == SkaterState.MANUAL);
// the board (and skater) dip 4 units while crouched to charge a pop
chargeDip = PoseSmoothing.approach(chargeDip, charging ? 4f : 0f, dt,
PoseSmoothing.POSE_TAU);
// a stumble off a wall wobbles the board for a moment
riding.update(p, Math.round(p.x), Math.round(p.y), lean + p.boardRoll,
PoseSmoothing.stumbleWobble(sinceStumble), PoseSmoothing.popPitch(sincePop, popNollie), p.boardPitch, 0f,
p.hold, chargeDip, bodyPose, dt, boardTarget, bodyTarget);
// just got on: eased over from the walker and the carried board (no blend otherwise)
lastBoardState = null;
hand.setActive(false);
boardMove.cancel();
blendSwap();
// the skater's centre of mass is that much lower against a lifted board
finish(riding.roll, riding.pitch, riding.deckLift - riding.lift, riding.lift, !bailed && puppet.canDeform(),
dt);
}

/**
* The latest mount or dismount and how far through it is (0..1), read by both {@link #update} and
* {@link #updateOnFoot}: a new one is blended from what was drawn just before.
*/
public void setTransition(BoardTransition transition, float progress)
{
this.transition = transition;
this.transitionProgress = progress;
}

/**
* On foot: the puppet stands at the walker facing the way it walks (its OSRS walk, run and idle animations are
* the animator's); the board is in the right hand while carried (CarryPose) and lies flat where it was left when
* dropped. Getting off eases the body down and round and the board up into the hand; dropping or picking up
* the board nearby moves it there over the same 0.25 s.
*/
public void updateOnFoot(OffBoardPose p, float dt)
{
if (puppet == null)
return;
setTransition(p.transition, p.transitionProgress);
showBoard(true);
bodyTarget.set(Math.round(p.walkerX), Math.round(p.walkerY), -Math.round(p.walkerH),
Angles.toJau(p.walkerHeading), 0f, 0f);
hand.setActive(p.board != BoardState.DROPPED);
if (p.board == BoardState.DROPPED)
boardTarget.set(Math.round(p.boardX), Math.round(p.boardY), -Math.round(p.boardH),
Angles.toJau(p.boardHeading), 0f, 0f);
else
{
// in the hand as the walk or run animation swings it (the fixed spot until the hand is read)
hand.step(dt);
CarryPose.placeAt(p.walkerX, p.walkerY, p.walkerH, p.walkerHeading, p.walkerSpeed, hand.x(), hand.y(),
hand.z(), boardTarget);
// the hop onto the board: it comes out of the hand and under the feet on the way down
float k = p.mountJump && p.airborne ? CarryPose.mountDrop(p.verticalSpeed) : 0f;
if (k > 0f)
{
CarryPose.underFeet(p.walkerX, p.walkerY, p.walkerH, p.walkerHeading, feetBoard);
SwapBlend.lerp(boardTarget, feetBoard, k);
boardTarget.copyFrom(feetBoard);
}
}
blendSwap();
// on foot, the board dropped or picked up within reach moves between hand and ground instead of jumping
if (lastBoardState != null && p.board != lastBoardState && !swap.isActive() && drawnBoard.valid
&& Math.hypot(boardTarget.x - drawnBoard.x, boardTarget.y - drawnBoard.y) <= BOARD_MOVE_REACH)
{
boardMove.begin(null, drawnBoard, false);
boardMoveTime = 0f;
}
lastBoardState = p.board;
if (boardMove.isActive())
{
boardMoveTime += dt;
// over 0.25 s (BoardSwap.TRANSITION_SECONDS)
boardMove.apply(boardMoveTime / 0.25f, null, boardTarget);
}
finishOffBoard(dt);
}

/**
* Knocked off the board: the body where the knockdown puts it, facing along its tumble (the tumble itself, the
* lie-down and the get-up are the body pose's, written by the animator first: its flip angle decides how far the
* body comes down so its lowest point stays on the ground), and the board on its own, spinning and flipping
* until it rests. A mount or dismount blend in progress is dropped; getting back on with R blends from here.
*/
public void updateKnockdown(KnockdownPose k, float dt)
{
if (puppet == null)
return;
showBoard(true);
stopBlends();
float down = TumbleBody.groundOffset(bodyPose.flip);
bodyTarget.set(Math.round(k.bodyX), Math.round(k.bodyY), -Math.round(k.bodyH + down),
Angles.toJau(k.facing + Angles.PI / 2), 0f, 0f);
boardTarget.set(Math.round(k.boardX), Math.round(k.boardY),
-Math.round(k.boardH + KnockdownPose.boardLift(k.boardRoll)), Angles.toJau(k.boardYaw), k.boardRoll,
k.boardPitch);
finishOffBoard(dt);
}

/**
* A duel tantrum starts: the board's two halves are copied now (in its designs as they are), so the snap costs
* nothing, and the grab starts from where the board is drawn. False when the board can't snap (it then just
* disappears at the slam). Client thread.
*/
public boolean prepareTantrum()
{
tantrumFrom.copyFrom(drawnBoard);
if (!boardShown || !drawnBody.valid
|| Math.hypot(drawnBoard.x - drawnBody.x, drawnBoard.y - drawnBody.y) > BOARD_MOVE_REACH)
// lying far away (or not drawn): the board is simply in the hands
tantrumFrom.invalidate();
return halves.prepare(poser);
}

/**
* The tantrum {@code t} seconds in ({@link TantrumSequence}): the body standing at the walker's feet ({@code x},
* {@code y}, up-positive {@code h}) facing {@code heading} (its arms and fold are the body pose's, written by the
* animator); the board taken from where it was into both hands, stamped, heaved overhead and slammed down in
* front; gone once it snapped (its halves are drawn on their own).
*/
public void updateTantrum(float t, float x, float y, float h, float heading, float dt)
{
if (puppet == null)
return;
stopBlends();
bodyTarget.set(Math.round(x), Math.round(y), -Math.round(h), Angles.toJau(heading), 0f, 0f);
boolean holding = TantrumSequence.holding(t);
showBoard(holding);
if (holding)
TantrumSequence.placeHeld(t, x, y, h, heading, tantrumFrom, tantrumHold, boardTarget);
finishOffBoard(dt);
}

/**
* The slammed board breaks in front of the body at the walker's feet ({@code x}, {@code y}, ground {@code h})
* facing {@code heading}: its halves fly apart along it, bounced by {@code world}. The break point goes to
* {@code at}.
*/
public void snapBoard(CollisionWorld world, float gravity, float x, float y, float h, float heading,
Random random, float[] at)
{
halves.snap(world, gravity, x, y, h, heading, worldViewId, plane, random, at);
}

/** A knockdown or tantrum: no blend runs, and a mount or dismount after it is new. */
private void stopBlends()
{
hand.setActive(false);
boardMove.cancel();
swap.cancel();
lastBoardState = null;
lastTransition = transition;
lastProgress = transitionProgress;
}

/** A new mount or dismount starts easing from the last drawn placements; one in progress eases on. */
private void blendSwap()
{
if (SwapBlend.starts(lastTransition, lastProgress, transition, transitionProgress))
{
swap.begin(drawnBody, drawnBoard, transition == BoardTransition.MOUNT);
boardMove.cancel();
}
lastTransition = transition;
lastProgress = transitionProgress;
swap.apply(transitionProgress, bodyTarget, boardTarget);
}

/** Off the board: back on it, its trick pose is rebuilt from scratch. */
private void finishOffBoard(float dt)
{
riding.offBoard();
finish(0f, 0f, 0, 0, false, dt);
}

/**
* Poses every board part, tells the body the board as drawn ({@code roll}, {@code pitch}, the deck lift and the
* grab's lift: a grabbing hand goes to it), moves the body and board to this frame's placements and remembers
* them for the next blend.
*/
private void finish(float roll, float pitch, int pivotLift, int lift, boolean flip, float dt)
{
for (int i = 0; i < boardCount; i++)
{
boards[i].setPose(boardTarget.roll, boardTarget.pitch, BoardPlacement.boardFlipPivotY(pivotLift), flip);
// every part of the board in exactly the same place
place(boards[i], boardTarget);
}
bodyPose.boardRoll = roll;
bodyPose.boardPitch = pitch;
bodyPose.deckLift = pivotLift + lift;
bodyPose.boardLift = lift;
place(puppet, bodyTarget);
drawnBody.copyFrom(bodyTarget);
drawnBoard.copyFrom(boardTarget);
halves.update(dt);
}

/** Shows or hides the board objects (hidden while the tantrum's halves fly). */
private void showBoard(boolean show)
{
if (show != boardShown && boardCount > 0)
{
boardShown = show;
for (int i = 0; i < boardCount; i++)
show(boards[i], show);
}
}

public void despawn()
{
halves.despawn();
if (puppet != null)
client.removeRuneLiteObject(puppet);
showBoard(false);
puppet = null;
Arrays.fill(boards, null);
boardCount = 0;
poser = null;
}
}
