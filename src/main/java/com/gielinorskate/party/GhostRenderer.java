package com.gielinorskate.party;

import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.physics.*;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.render.*;
import com.gielinorskate.tricks.Trick;
import java.util.*;
import java.util.function.LongFunction;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

/**
* Draws party ghosts: per visible ghost its own board (mesh included) and, when the member's real character is in the scene, a body
* drawn from our own copy of that character's model (else a board-only ghost), animated as the local skater is
* (GhostBodyController: the riding stance, walk and run, the knockdown, with the procedural pose on top) without
* touching the character itself. Placement mirrors the local skater's renderer. The board
* is the RuneSkate (baked) board at Normal detail in the member's designs, one object per part like the local
* board; when it can't be built no ghost is drawn this session.
* Client thread only.
*/
final class GhostRenderer
{
/** A board further than this from where it was drawn (a call-back) appears instead of flying there. */
static final float BOARD_MOVE_REACH = 2 * 128f;

/** One drawn ghost, and what it is drawn from this frame. */
private final class Ghost
{
/** The ghost's body pose: the body is deformed by it and the board reads its front / back flip, when drawn. */
final BodyPose pose = new BodyPose();
final GhostRig rig = new GhostRig();
/** One object per board part, all placed and posed the same. */
BoardController[] boards;
/** The ghost's own board mesh (never shared: posing rewrites it), and its designs. */
BakedBoardModel baked;
BoardLook look;
final GhostBodyController body = new GhostBodyController(client, pose);
boolean bodyRegistered;
final RidingBoard riding = new RidingBoard();
float nextLookup;
/** The board object is registered (it is not while it lies outside this scene). */
boolean boardRegistered;
/** This frame's placements, the ones drawn last frame, and the get-on / get-off blend between. */
final Placement bodyT = new Placement();
final Placement boardT = new Placement();
final Placement drawnBody = new Placement();
final Placement drawnBoard = new Placement();
final SwapBlend swap = new SwapBlend();
float swapTime;
boolean drawnOnce;
/** The board's state last drawn: null on the board. */
BoardState lastOff;
/** This frame's board flip pivot and whether the board turns with a flip (for a blended re-pose). */
float pivotY;
boolean flipAllowed;
int feetZ;
/** The last frame was a knockdown (its board eases between updates). */
boolean knocked;
/** Duel endings already taken from the predictor, and when the playing ones started (our clock; NaN: none). */
int tantrumsSeen;
int celebrationsSeen;
float tantrumAt = Float.NaN;
float celebrateAt = Float.NaN;
/** Where the board was drawn as the tantrum began (the grab starts there), and its hold this frame. */
final Placement tantrumFrom = new Placement();
final float[] tantrumHold = new float[4];
/** Scratch: a sent board's local position. */
final float[] boardAt = new float[2];
/** The tantrum's board halves (made from this ghost's own board), and whether it snapped yet. */
SnappedBoard halves;
boolean snapped;
/** This frame's distance from the camera's focus, for the detail budget. */
float distance;

// this frame: the member's ghost, its pose, local position, the update drawn and the scene
GhostPredictor predictor;
RenderPose p;
int x;
int y;
GhostState latest;
float now;
float dt;
WorldView wv;

/** Builds the board in {@code look}'s designs; false when it can't be built. */
boolean build(BoardLook look)
{
this.look = look;
baked = BakedBoardModel.create(client, false, look);
if (baked == null)
return false;
// at most one object per baked part
boards = new BoardController[Math.max(1, Math.min(4, baked.partCount()))];
for (int i = 0; i < boards.length; i++)
{
boards[i] = new BoardController(i);
boards[i].setBoard(baked, pose);
}
return true;
}

/** Every part of the board in the same pose. */
void poseBoard(float roll, float pitch, float pivot, boolean flip)
{
for (BoardController b : boards)
b.setPose(roll, pitch, pivot, flip);
}

/** Off the board: the trick pose is rebuilt from scratch back on it, and the board is posed with no flip. */
void offBoard(float roll, float pitch)
{
riding.offBoard();
pose.boardLift = 0f;
pivotY = BoardPlacement.boardFlipPivotY(0);
flipAllowed = false;
poseBoard(roll, pitch, pivotY, false);
}

/**
* Positions board and body, on the board or on foot, easing over 0.25 s when the member gets on or off, or
* drops or picks up the board nearby; returns the feet's z (RuneLite, down-negative).
*/
int place()
{
BoardState off = latest.offBoard;
boolean boardVisible = true;
boolean knock = latest.knockStage != null;
float tantrumAge = now - tantrumAt;
// the member shares a walker standing with the board in hand while throwing it
boolean tantrum = !knock && off != null && tantrumAge >= 0f && tantrumAge < TantrumSequence.DURATION;
if (knock)
boardVisible = placeKnockdown();
else if (tantrum)
boardVisible = placeTantrum(tantrumAge);
else if (off == null)
placeOnBoard();
else
boardVisible = placeOnFoot();
knocked = knock;
// getting on or off eases from what was drawn; so does a board dropped or picked up within reach (a
// knockdown starts where the bail is, as the local skater's does: no blend into it)
if (knock || tantrum)
// (the tantrum's grab takes the board from where it was drawn itself)
swap.cancel();
else if (drawnOnce && (off == null) != (lastOff == null))
{
swap.begin(drawnBody, boardRegistered ? drawnBoard : null, off == null);
swapTime = 0f;
}
else if (drawnOnce && off != lastOff && boardRegistered && boardVisible
&& Math.hypot(boardT.x - drawnBoard.x, boardT.y - drawnBoard.y) <= BOARD_MOVE_REACH)
{
swap.begin(null, drawnBoard, false);
swapTime = 0f;
}
lastOff = off;
if (swap.isActive())
{
swapTime += dt;
// Getting on or off, or a board dropped or picked up, eases over this long
// (BoardSwap.TRANSITION_SECONDS).
swap.apply(swapTime / 0.25f, bodyT, boardT);
// the board turns between hand and wheels with the rest of the blend
poseBoard(boardT.roll, boardT.pitch, pivotY, flipAllowed);
}
showBoard(boardVisible);
// every part of the board in exactly the same place
for (BoardController b : boards)
put(b, boardT);
put(body, bodyT);
drawnBody.copyFrom(bodyT);
drawnBoard.copyFrom(boardT);
drawnOnce = true;
return feetZ;
}

private void put(RuneLiteObjectController o, Placement at)
{
o.setWorldView(wv.getId());
o.setLevel(wv.getPlane());
o.setX(Math.round(at.x));
o.setY(Math.round(at.y));
o.setZ(Math.round(at.z));
o.setOrientation(at.orientation());
}

/** Standing upright at the feet, facing {@code heading}, the hand reading the board while {@code carrying}. */
private void stand(float heading, boolean carrying)
{
feetZ = -Math.round(p.h);
bodyT.set(x, y, feetZ, Angles.toJau(heading), 0f, 0f);
body.hand.setActive(carrying);
}

/** The sent board position in this scene's local units into {@link #boardAt}; whether it lies in the scene. */
private boolean boardHere()
{
boardAt[0] = GhostCodec.toLocal(latest.boardX, wv.getBaseX());
boardAt[1] = GhostCodec.toLocal(latest.boardY, wv.getBaseY());
return GhostVisibility.inScene(boardAt[0], boardAt[1], wv.getSizeX(), wv.getSizeY());
}

/**
* On foot: upright, facing the way it walks (walking or running on our copy of the member's model, see
* {@link #animate}), with a jump tuck; the board in the right hand as the walk swings it (read off the drawn
* body), or lying where it was dropped (false when that is outside this scene: the board is not drawn).
*/
boolean placeOnFoot()
{
rig.updateOnFoot(predictor, p, now, dt, pose);
boolean dropped = latest.offBoard == BoardState.DROPPED;
stand(p.heading, !dropped);
boolean visible = true;
if (dropped)
{
visible = boardHere();
boardT.set(Math.round(boardAt[0]), Math.round(boardAt[1]), -Math.round(latest.boardH),
Angles.toJau(latest.boardHeading), 0f, 0f);
}
else
{
// in the hand as the walk or run swings it (the fixed spot until the hand is read)
HandAnchor hand = body.hand;
hand.step(dt);
CarryPose.placeAt(x, y, p.h, p.heading, predictor.speed(), hand.x(), hand.y(), hand.z(), boardT);
}
offBoard(boardT.roll, boardT.pitch);
return visible;
}

/**
* Knocked off: the body tumbling onto its lying angle, lying, and getting up (the OSRS lie-down and get-up when
* our animation shows, else the procedural fall), its lowest point on the ground, facing along the tumble; the
* board where it was sent, eased between updates (false when that is outside this scene).
*/
boolean placeKnockdown()
{
KnockdownPose.Stage stage = latest.knockStage;
float age = predictor.knockStageAge(now);
KnockdownPose k = new KnockdownPose(stage, GhostKnockdown.progress(stage, age), p.x, p.y, p.h, p.heading,
GhostKnockdown.angle(stage, latest.knockLie, age), 0f, latest.boardX, latest.boardY, latest.boardH,
latest.boardHeading, 0f, 0f);
rig.knockdown(k, stage != KnockdownPose.Stage.AIR && body.layered, pose);
stand(p.heading + Angles.PI / 2, false);
bodyT.z = -Math.round(p.h + TumbleBody.groundOffset(pose.flip));
boolean visible = boardHere();
float bx = boardAt[0];
float by = boardAt[1];
float bz = -latest.boardH;
if (knocked && drawnOnce && Math.hypot(bx - drawnBoard.x, by - drawnBoard.y) <= BOARD_MOVE_REACH)
{
// Seconds (time constant): a knocked-off ghost's board eases toward each new place instead of
// jumping there.
float f = 1f - (float) Math.exp(-dt / 0.08f);
bx = drawnBoard.x + (bx - drawnBoard.x) * f;
by = drawnBoard.y + (by - drawnBoard.y) * f;
bz = drawnBoard.z + (bz - drawnBoard.z) * f;
}
boardT.set(Math.round(bx), Math.round(by), Math.round(bz), Angles.toJau(latest.boardHeading), 0f, 0f);
offBoard(0f, 0f);
return visible;
}

/**
* A duel tantrum {@code t} seconds in ({@link TantrumSequence}), the local skater's own: the body standing
* where the member stands (both arms holding the board, the fold of the slam: the body pose), the ghost's own
* board grabbed from where it was drawn, stamped, heaved up and slammed down in front, where it snaps into
* halves made from that board (with the crack and dust); false once snapped (the board is not drawn until the
* member's fresh one is in hand).
*/
boolean placeTantrum(float t)
{
rig.updateOnFoot(predictor, p, now, dt, pose);
TantrumSequence.writeBody(t, pose);
stand(p.heading, false);
boolean holding = TantrumSequence.holding(t);
if (holding)
TantrumSequence.placeHeld(t, x, y, p.h, p.heading, tantrumFrom, tantrumHold, boardT);
else if (!snapped)
{
snapped = true;
snap();
}
offBoard(boardT.roll, boardT.pitch);
return holding;
}

/** The tantrum board breaks in front of the body. */
private void snap()
{
float[] at = new float[2];
// the local collision world when it covers this scene, else flat ground at the feet
CollisionWorld w = world != null && worldBaseX == wv.getBaseX() && worldBaseY == wv.getBaseY() ? world
: null;
float ground = halves.snap(w, GhostPredictor.GRAVITY, x, y, p.h, p.heading, wv.getId(), wv.getPlane(),
random, at);
cues.play(SkateFeedback.EndingCue.SNAP, at[0], at[1], ground, now);
}

/**
* Takes a duel ending the predictor reached since the last frame: a tantrum (still early enough to show its
* slam) gets its halves made from the ghost's own board now; a celebration sparkles at the ghost.
*/
void takeEndings()
{
if (predictor.tantrumCount() != tantrumsSeen)
{
tantrumsSeen = predictor.tantrumCount();
float age = predictor.tantrumAge(now);
if (age >= 0f && age < TantrumSequence.SNAP_AT)
{
tantrumAt = now - age;
celebrateAt = Float.NaN;
snapped = false;
tantrumFrom.copyFrom(drawnBoard);
if (!boardRegistered || !drawnOnce
|| Math.hypot(drawnBoard.x - x, drawnBoard.y - y) > BOARD_MOVE_REACH)
// far away (or not drawn): the board is simply in the hands
tantrumFrom.invalidate();
if (halves == null)
halves = new SnappedBoard(client);
try
{
halves.prepare(baked);
}
catch (RuntimeException ex)
{
// no halves: the board just goes at the slam
halves.despawn();
}
}
}
if (predictor.celebrateCount() != celebrationsSeen)
{
celebrationsSeen = predictor.celebrateCount();
float age = predictor.celebrateAge(now);
if (age >= 0f && CelebrationSequence.playing(age))
{
celebrateAt = now - age;
cues.play(SkateFeedback.EndingCue.SPARKLE, x, y, p.h, now);
}
}
}

/**
* The body's OSRS animation this frame: the riding stance on the board, walk or run on foot (at the walker's
* pace), lying and getting up when knocked down; a duel ending's emote over it (the tantrum's stamp on foot;
* the cheer or jump for joy on the ground, cut short once the member is off it: they skipped it).
*/
void animate()
{
KnockdownPose.Stage knock = latest.knockStage;
boolean onFoot = latest.offBoard != null;
FootBody.Gait gait = rig.gait();
GhostAnim a = GhostAnim.pick(onFoot, gait, knock);
float rate = a == GhostAnim.WALK || a == GhostAnim.RUN ? GaitPlayback.rate(gait, predictor.speed()) : 1f;
float progress = knock == null ? 0f : GhostKnockdown.progress(knock, predictor.knockStageAge(now));
float tantrumAge = now - tantrumAt;
boolean grounded = onFoot ? p.state != SkaterState.AIRBORNE
: p.state == SkaterState.ROLLING || p.state == SkaterState.MANUAL;
if (knock != null || !grounded)
celebrateAt = Float.NaN;
float celebrateAge = now - celebrateAt;
GhostAnim ending = knock != null ? null : GhostAnim.ending(onFoot ? tantrumAge : Float.NaN, celebrateAge,
onFoot);
if (ending != null)
{
a = ending;
rate = 1f;
progress = GhostAnim.endingProgress(ending, tantrumAge, celebrateAge);
}
body.animate(a, rate, progress, dt);
}

/** Shows or hides the board (hidden when it lies outside this scene). */
void showBoard(boolean show)
{
if (show != boardRegistered)
{
boardRegistered = show;
register(show, boards);
}
}

void showBody(boolean show)
{
if (show != bodyRegistered)
{
bodyRegistered = show;
register(show, body);
}
}

/** On the board, placed as the local skater is. */
void placeOnBoard()
{
// the body's lean, crouch, push, landing squash, bail fall and front / back flip; the board turns with the
// skater's flip about the centre of mass (both read the pose when drawn)
rig.update(predictor, p, now, dt, pose);
body.hand.setActive(false);
// manual and grind pitch ease in and out; an impossible's end-over-end pitch is drawn as predicted; a
// rolling grab (a grab hold while ROLLING) keeps the board flat on the ground
float holdPitchFor = SkatePhysics.boardPitchFor(p.state, p.hold);
feetZ = riding.update(p, x, y, p.boardRoll, 0f, PoseSmoothing.popPitch(predictor.sincePop(now),
predictor.popNollie()), holdPitchFor, p.boardPitch - holdPitchFor,
p.state == SkaterState.ROLLING ? null : p.hold, 0f, pose, dt, boardT, bodyT);
pivotY = BoardPlacement.boardFlipPivotY(riding.deckLift - riding.lift);
flipAllowed = p.state != SkaterState.BAILED;
poseBoard(riding.roll, riding.pitch, pivotY, flipAllowed);
}

/** Finds the member's real character in the scene now and then (each second); none: a board-only ghost. */
void updateBody(String name)
{
if (now >= nextLookup)
{
nextLookup = now + 1f;
Player found = null;
for (Player pl : wv.players())
{
if (name != null && found == null && pl != null && GhostVisibility.sameName(pl.getName(), name))
found = pl;
}
body.setPlayer(found);
}
showBody(body.player != null);
}

/** Takes the ghost out of the scene; true. */
boolean remove()
{
if (halves != null)
halves.despawn();
showBoard(false);
showBody(false);
body.setPlayer(null);
return true;
}
}

private final Client client;
private final Map<Long, Ghost> drawn = new HashMap<>();
/** A board mesh could not be built (cache model unavailable): not retried every frame. */
private boolean boardModelFailed;
private volatile List<GhostLabel> labels = Collections.emptyList();
/** The local skater's collision world and the scene base it is in (tantrum halves bounce on it), or null. */
private CollisionWorld world;
private int worldBaseX;
private int worldBaseY;
/** Plays a duel ending's crack or sparkle at a ghost (sounds and particles, by their settings). */
private final EndingCues cues;
private final Random random = new Random();

/** Where a ghost's duel ending cues go. */
interface EndingCues
{
void play(SkateFeedback.EndingCue cue, float x, float y, float h, float now);
}

GhostRenderer(Client client, EndingCues cues)
{
this.client = client;
this.cues = cues;
}

/**
* The ground the halves of a ghost's snapped board bounce on: the local skater's collision world (local units of
* the scene based at {@code baseX}, {@code baseY}), or null (flat ground where it breaks).
*/
void setCollisionWorld(CollisionWorld world, int baseX, int baseY)
{
this.world = world;
worldBaseX = baseX;
worldBaseY = baseY;
}

/** This frame's labels, for the overlay. */
List<GhostLabel> labels()
{
return labels;
}

/**
* Draws {@code ghosts} for this frame, or nothing when {@code show} is false.
*
* @param names display name of a party member by ID, or null
* @param ground the ground ghosts on this plane follow (null: none)
*/
void update(Map<Long, GhostPredictor> ghosts, LongFunction<String> names, boolean show, float now, float dt,
GhostPredictor.Ground ground)
{
WorldView wv = client.getTopLevelWorldView();
if (!show || wv == null || ghosts.isEmpty() || boardModelFailed)
{
// keeps boardModelFailed: no retry every frame within the session
removeAll();
return;
}
List<GhostLabel> out = new ArrayList<>(ghosts.size());
List<Ghost> seen = new ArrayList<>();
for (Map.Entry<Long, GhostPredictor> e : ghosts.entrySet())
{
GhostPredictor predictor = e.getValue();
if (!GhostVisibility.sameSpace(client.getWorld(), wv.getPlane(), predictor.current()))
continue;
RenderPose p = predictor.pose(now, ground);
int lx = Math.round(GhostCodec.toLocal(p.x, wv.getBaseX()));
int ly = Math.round(GhostCodec.toLocal(p.y, wv.getBaseY()));
if (!GhostVisibility.inScene(lx, ly, wv.getSizeX(), wv.getSizeY()))
continue;
long id = e.getKey();
Ghost g = drawn.get(id);
if (g == null)
{
// each ghost has its own board mesh, built on spawn and dropped with the ghost: posing rewrites the
// model data, so ghosts sharing one would draw each other's flips and pitch (the baked board's
// merged templates are shared: a spawn only copies them)
g = new Ghost();
if (!g.build(predictor.look()))
{
boardModelFailed = true;
continue;
}
g.nextLookup = now;
// endings from before it was drawn are not played
g.tantrumsSeen = predictor.tantrumCount();
g.celebrationsSeen = predictor.celebrateCount();
g.showBoard(true);
drawn.put(id, g);
}
seen.add(g);
if (!g.look.equals(predictor.look()))
{
// the member picked other designs: only the colours change, lit again at the next pose
g.look = predictor.look();
g.baked.setLook(g.look);
for (BoardController b : g.boards)
b.repaint();
}
g.predictor = predictor;
g.p = p;
g.x = lx;
g.y = ly;
g.latest = predictor.current();
g.now = now;
g.dt = dt;
g.wv = wv;
String name = names.apply(id);
g.takeEndings();
int feetZ = g.place();
if (g.halves != null)
g.halves.update(dt);
g.updateBody(name);
g.animate();
// the focal point's Z is the scene's y (its Y is the height)
g.distance = GhostVisibility.focusDistance(lx, ly, client.getCameraFocalPointX(),
client.getCameraFocalPointZ());
Trick trick = predictor.labelTrick();
float alpha = trick == null ? 0f : GhostVisibility.labelAlpha(predictor.labelAge(now));
// always one per drawn ghost: a duel draws its HP bar and hitsplats at it even without a name; the label a
// little over a character's height above the feet
out.add(new GhostLabel(id, lx, ly, feetZ - 240, name, alpha > 0f ? trick.displayName : null, alpha));
}
drawn.values().removeIf(g -> !seen.contains(g) && g.remove());
// the nearest few get the full body (ties in order); the rest play the animation only
seen.sort(Comparator.comparingDouble(g -> g.distance));
for (int i = 0; i < seen.size(); i++)
seen.get(i).body.full = GhostVisibility.full(i, seen.get(i).distance);
labels = out;
}

private void register(boolean on, RuneLiteObjectController... objects)
{
for (RuneLiteObjectController o : objects)
{
if (on)
client.registerRuneLiteObject(o);
else
client.removeRuneLiteObject(o);
}
}

/** A player left the scene: no ghost may keep drawing its model. */
void forgetPlayer(Player player)
{
for (Ghost g : drawn.values())
{
if (g.body.player == player)
{
g.body.setPlayer(null);
g.nextLookup = 0f;
g.showBody(false);
}
}
}

/**
* Skate end, scene lost, shutdown or ghost failure: removes every ghost, and a later session (or scene) gets
* a fresh try at building the board model.
*/
void despawnAll()
{
boardModelFailed = false;
removeAll();
}

private void removeAll()
{
drawn.values().forEach(Ghost::remove);
drawn.clear();
labels = Collections.emptyList();
}

/**
* The ground of the loaded scene at absolute coordinates, from its tile heights (up-positive; NaN outside
* the scene). Client thread.
*/
static GhostPredictor.Ground sceneGround(Client client, WorldView wv)
{
int plane = wv.getPlane();
// RuneLite heights grow downward
return localGround(wv.getBaseX(), wv.getBaseY(), wv.getSizeX(), wv.getSizeY(),
(lx, ly) -> -Perspective.getTileHeight(client, new LocalPoint(Math.round(lx), Math.round(ly), wv), plane));
}

/**
* The ground {@code local} gives at local coordinates of the scene based at ({@code baseX}, {@code baseY}),
* {@code sizeX} by {@code sizeY} tiles, at absolute coordinates; NaN outside it.
*/
static GhostPredictor.Ground localGround(int baseX, int baseY, int sizeX, int sizeY, GhostPredictor.Ground local)
{
return (x, y) ->
{
float lx = GhostCodec.toLocal(x, baseX);
float ly = GhostCodec.toLocal(y, baseY);
return GhostVisibility.inScene(lx, ly, sizeX, sizeY) ? local.heightAt(lx, ly) : Float.NaN;
};
}
}
