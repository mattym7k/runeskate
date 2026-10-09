package com.gielinorskate.party;

import com.gielinorskate.Text;
import com.gielinorskate.render.*;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.AnimationID;

/**
* A party ghost's body, drawn as the local skater's is (the riding stance, walk and run, the lie-down and get-up,
* with the procedural {@link BodyPose} on top) without ever touching the member's real character on this client:
* its animations, frames and model are only read. Each draw takes the member's model as the client hands it out
* and, only when that is provably the per-request buffer the client rebuilds on every request ({@link LayeredBody}),
* draws our own {@link MeshSnapshot} of the member standing (taken once while their character stands in its plain
* idle, again when their appearance changes) in it, with our OSRS animation applied by the client to that canvas
* ({@code Client.applyTransformations}: it animates the model it is given, not an actor) and the pose deformed in.
* Without that proof the member's model is drawn as is; any failure falls back for this ghost (the layer alone, or
* the whole body: then the member's model is drawn plain). Client thread only; no allocation per frame.
*/
@Slf4j
@RequiredArgsConstructor
final class GhostBodyController extends RuneLiteObjectController
{
/** The body as animated, no procedural pose (a far ghost's, see GhostDetail). */
private static final BodyPose NEUTRAL = new BodyPose();

private final Client client;
private final BodyPose pose;
private final MeshSnapshot snapshot = new MeshSnapshot();
private final BufferProbe.Probe<Model> probe = new BufferProbe.Probe<>();
/** Reads the drawn body's right hand while the board is carried. */
final HandAnchor hand = new HandAnchor();
private final AnimClock clock = new AnimClock();
private final LayeredBody.Layer<Model> layer = this::applyLayer;
private final BufferProbe.Source<Model> source = () ->
{
Player q = this.player;
return q == null ? null : q.getModel();
};
Player player;

/** The animation playing now and its frame (-1: none), set each frame by {@link #animate}. */
private GhostAnim anim = GhostAnim.NONE;
private int animId = -1;
private Animation animation;
/** Its frame lengths: a classic animation's own, or one tick per frame for a Maya one (AnimClock.tickFrames). */
private int[] lengths;
private int frame = -1;
/** The procedural pose is drawn (the ghost is near enough, GhostDetail). */
boolean full = true;
/** The last draw had our animation on it (a lie-down or get-up then needs no procedural fall). */
boolean layered;
/** The OSRS layer failed once: this ghost plays none from then on. */
private boolean layerFailed;
/** A Maya animation failed once: this ghost plays only classic ones from then on (walk, run, the get-up). */
private boolean mayaFailed;
/** The body failed once: this ghost is drawn as the member's plain model from then on. */
private boolean failed;

void setPlayer(Player player)
{
if (player != this.player)
snapshot.clear();
this.player = player;
}

/**
* This frame's animation: {@code a} at {@code rate} times the game's pace for a looping one, else at
* {@code progress} (0..1) through it. Client thread, before the draw.
*/
void animate(GhostAnim a, float rate, float progress, float dt)
{
Player p = player;
int id = p == null || layerFailed ? -1 : a.id(p.getWalkAnimation(), p.getRunAnimation());
if (a != anim || id != animId)
{
anim = a;
animId = id;
animation = null;
clock.reset();
}
if (id >= 0 && animation == null)
{
try
{
Animation loaded = client.loadAnimation(id);
animation = loaded != null && loaded.isMayaAnim() && mayaFailed ? null : loaded;
// a Maya (skeletal) animation's frame is the tick within its duration
lengths = animation == null ? null : animation.isMayaAnim()
? AnimClock.tickFrames(animation.getDuration()) : animation.getFrameLengths();
}
catch (RuntimeException ex)
{
failLayer(ex);
return;
}
}
frame = id < 0 || animation == null ? -1
: a.loops ? clock.loop(lengths, rate, dt) : clock.seek(lengths, progress);
}

@Override
public Model getModel()
{
Player p = player;
if (p == null)
return null;
if (failed)
{
layered = false;
return p.getModel();
}
try
{
return draw(p);
}
catch (RuntimeException ex)
{
failed = true;
layered = false;
log.warn(Text.get("gb.body"), ex);
return p.getModel();
}
}

private Model draw(Player p)
{
PlayerComposition c = p.getPlayerComposition();
int idle = p.getIdlePoseAnimation();
int npc = c == null ? -1 : c.getTransformedNpcId();
int key = c == null ? 0 : key(c.getEquipmentIds(), c.getColors(), c.getGender(), npc, idle);
boolean mayTake = c != null && mayTake(p.getAnimation(), p.getPoseAnimation(), idle, npc);
boolean useLayer = !layerFailed && frame >= 0 && animation != null && layerable(idle);
layered = false;
Model m = LayeredBody.drawable(source, PuppetController.MESH, snapshot, key, mayTake, useLayer ? layer : null,
full ? pose : NEUTRAL, probe);
readHand(m);
return m;
}

/** Our animation on the canvas (the snapshot in the proven per-request buffer); null when not applied. */
private Model applyLayer(Model canvas)
{
Animation a = animation;
int f = frame;
if (a == null || f < 0 || layerFailed)
return null;
try
{
Model r = client.applyTransformations(canvas, a, f, null, 0);
layered = r == canvas;
return r;
}
catch (RuntimeException ex)
{
if (a.isMayaAnim())
{
// only the skeletal kind failed: the classic animations (walk, run, the get-up) still play
mayaFailed = true;
animation = null;
frame = -1;
log.warn(Text.get("gb.maya"), ex);
return null;
}
failLayer(ex);
return null;
}
}

private void failLayer(RuntimeException ex)
{
layerFailed = true;
animation = null;
frame = -1;
log.warn(Text.get("gb.anim"), ex);
}

/** Reads (never writes) the drawn body's hand; a failure stops the reading (the board then hangs at its spot). */
private void readHand(Model m)
{
if (m == null || !hand.isActive())
return;
try
{
hand.sample(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount());
}
catch (RuntimeException ex)
{
hand.setActive(false);
log.debug("Could not read a party ghost's hand", ex);
}
}

// ---- when a ghost's copy of the member's model (MeshSnapshot) is taken and still fits

/** A key for the member's appearance; equal appearances give equal keys. */
static int key(int[] equipment, int[] colours, int gender, int npcTransform, int idlePose)
{
int h = Arrays.hashCode(equipment);
h = 31 * h + Arrays.hashCode(colours);
h = 31 * h + gender;
h = 31 * h + npcTransform;
return 31 * h + idlePose;
}

/**
* The real character stands in its plain idle (no action animation, its idle pose playing, not an NPC), so the
* frame it is drawn in now is a standing body to copy.
*/
static boolean mayTake(int animation, int poseAnimation, int idlePose, int npcTransform)
{
return animation == -1 && poseAnimation == idlePose && npcTransform == -1;
}

/**
* An OSRS animation goes on top of a snapshot taken in this idle pose: only the game's own standing frame, which
* is near the model's base pose (a weapon's stance would add its own arm swing to every animation).
*/
static boolean layerable(int idlePose)
{
return idlePose == AnimationID.HUMAN_READY;
}
}
