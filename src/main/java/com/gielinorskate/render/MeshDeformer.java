package com.gielinorskate.render;

/**
* Applies a {@link BodyPose} to a player mesh's vertex arrays in place. Pure and allocation-free.
*
* <p>Puppet model space: x along the board (the puppet is drawn turned 90 degrees from the board), y down
* with the soles at y = 0, z out of the back (actors face -z). How it maps onto the board's own model
* space follows from RuneLite's {@code Perspective.modelToCanvas} (model x -> local x, model z -> local y,
* {@code x' = x cos + z sin}, {@code z' = z cos - x sin}) and the puppet being drawn 512 orientation units
* past the board: board x = puppet z, board z = -puppet x. So the same physical roll as the board's
* {@code BoardGeometry.rotate} roll is the (z, y) rotation below, and the same pitch as
* {@code BoardPlacement.pose} is the (x, y) rotation below.
*
* <p>Order per vertex: knees bend (legs squash below the hips, the upper body drops rigidly; in a grab the knees
* fold up toward the chest instead and the feet rise to the lifted board), the torso folds forward at the hips,
* then the whole body pitches about the contact truck and rolls about the board's long axis through the feet; in
* a grab the hand then reaches for the board and the free arm swings out; last, a front or back flip turns the
* whole body about the centre of mass ({@link BodyPose#flip}), the same rotation {@link BoardPlacement#pose} gives
* the board.
*/
public final class MeshDeformer
{
/** Foot height (ankle above the sole) as a fraction of the hip height: below it the foot stays flat on the board. */
private static final float FOOT_FRACTION = 0.1f;
/** Units below the hip line over which the push leg (or a grab's knee fold) blends in, per unit of hip height. */
private static final float LEG_BLEND = 0.25f;
/** Units either side of the knee over which the shin's extra bend blends in. */
private static final float KNEE_BAND = 8f;
/** Scratch (z, y) of a leg point being bent (client thread only). */
private static final float[] ZY = new float[2];

private MeshDeformer()
{
}

/**
* Hip height (positive units above the soles) of a mesh whose highest vertex is at {@code topY} (a model height
* of 200 when the mesh has no vertex above the soles).
*/
static float hipHeight(float topY)
{
return Math.max(50f, Math.min(120f, 0.45f * (topY < 0f ? -topY : 200f)));
}

/** Deforms the first {@code n} vertices of {@code xs, ys, zs} by {@code pose}. */
public static void deform(float[] xs, float[] ys, float[] zs, int n, BodyPose pose)
{
float top = 0f;
for (int i = 0; i < n; i++)
top = Math.min(top, ys[i]);
float hip = hipHeight(top);
Body body = Body.set(pose, hip, pose.legWeight > 1e-4f ? Leg.solve(xs, ys, zs, n, hip, pose) : null);
if (body.folding)
{
// the knees fold about the mean z of the lower legs (below the hanging hands)
float sum = 0f;
int count = 0;
for (int i = 0; i < n; i++)
{
if (ys[i] > -ArmLocator.LOWER_LEGS * hip)
{
sum += zs[i];
count++;
}
}
body.hz = count == 0 ? 0f : sum / count;
}
// a grab: both arms' weights and shoulders from the mesh as animated, before anything moves
boolean grabbing = pose.armWeight > 1e-4f || pose.kneeFold > 1e-4f;
// a board held in both hands (the tantrum): each hand to its side of the hold point; a grab wins over it
boolean lifting = !grabbing && pose.liftWeight > 1e-4f;
Arm arm = grabbing || lifting ? Arm.find(Arm.GRAB, xs, ys, zs, n, grabbing ? pose.armSide : -1f) : null;
Arm free = arm != null ? Arm.find(Arm.FREE, xs, ys, zs, n, grabbing ? -pose.armSide : 1f) : null;
// in a grab the arms go with the upper body, never squashed down with the legs: a hand hanging below the hip
// line stayed at the crotch while the chest folded forward
float hold = arm == null ? 0f : lifting ? PushCycle.clamp01(pose.liftWeight)
: Math.max(PushCycle.clamp01(pose.armWeight), PushCycle.clamp01(pose.kneeFold));
for (int i = 0; i < n; i++)
{
float rigid = arm == null ? 0f : hold * (free == null ? arm.weights[i] : Math.max(arm.weights[i], free.weights[i]));
body.apply(xs[i], ys[i], zs[i], rigid);
xs[i] = body.x;
ys[i] = body.y;
zs[i] = body.z;
}
if (arm != null && lifting)
{
arm.lift(xs, ys, zs, n, body, pose, hold);
if (free != null)
free.lift(xs, ys, zs, n, body, pose, hold);
}
// the hand reaches for the board where it is drawn, the other arm swings out for balance (before the flip,
// which turns body and board together)
if (arm != null && pose.armWeight > 1e-4f)
{
// a lifted board is that much nearer the hips: the opposite of the deck lift
float[] t = arm.target;
BoardPlacement.grabTarget(pose.grabAlong, pose.grabAcross, pose.grabBoardY, pose.boardRoll,
pose.boardPitch, pose.deckLift - pose.boardLift, t);
arm.reach(xs, ys, zs, n, body, t[0], t[1], t[2], PushCycle.clamp01(pose.armWeight), true);
if (free != null)
free.balance(xs, ys, zs, n, body, PushCycle.clamp01(pose.armWeight));
}
if (Math.abs(pose.flip) > 1e-6f)
{
// the whole body turns about the centre of mass with the board (BoardPlacement.pose's flip)
float cf = (float) Math.cos(pose.flip);
float sf = (float) Math.sin(pose.flip);
for (int i = 0; i < n; i++)
{
float ry = ys[i] - pose.flipPivotY;
float fx = xs[i] * cf - ry * sf;
ys[i] = xs[i] * sf + ry * cf + pose.flipPivotY;
xs[i] = fx;
}
}
}

/**
* Turns {@link #ZY} by {@code angle} times the smoothstep of its y over {@code pivot} +- {@code band}, about
* the height {@code pivot}.
*/
private static void bend(float angle, float pivot, float band)
{
float t = angle * PushCycle.smoothstep(pivot - band, pivot + band, ZY[1]);
ZY[1] -= pivot;
turn((float) Math.cos(t), (float) Math.sin(t));
ZY[1] += pivot;
}

/** Turns {@link #ZY} by the angle of cosine {@code c} and sine {@code s}. */
private static void turn(float c, float s)
{
float z = ZY[0] * c - ZY[1] * s;
ZY[1] = ZY[0] * s + ZY[1] * c;
ZY[0] = z;
}

/**
* The whole-body part of the pose for one point, flip excluded: the knees (or the push leg), the torso fold and
* lean above the hips, then the pitch about the contact truck and the roll about the board's long axis. One
* reused instance (client thread only).
*/
private static final class Body
{
private static final Body SCRATCH = new Body();

float hip;
float s;
float hipY;
float cb;
float sb;
float cl;
float sl;
float cp;
float sp;
float cr;
float sr;
float px;
Leg leg;
/** A grab's knee fold (or a lifted board): the legs reach from the hips to the feet at -lift. */
boolean folding;
float fold;
/** Hip-to-sole distance over the hip height. */
float k;
/** The leg's length factor, when the fold alone cannot reach (k above 1 or below the tightest fold). */
float len;
/** The thigh's angle from straight down (the shin turns back by the same), with its cos and sin. */
float knee;
float ck;
float sk;
/** Model z the legs fold about. */
float hz;
/** Units the soles are lifted (with the board): the pitch and roll turn about them there. */
float lift;
/** Output of {@link #apply}. */
float x;
float y;
float z;

static Body set(BodyPose pose, float hip, Leg leg)
{
Body b = SCRATCH;
b.hip = hip;
b.s = pose.legScale;
b.hipY = -hip * pose.legScale;
// the torso folds toward the chest (-z): the roll rotation by -bend
b.cb = (float) Math.cos(-pose.torsoBend);
b.sb = (float) Math.sin(-pose.torsoBend);
b.cl = (float) Math.cos(pose.torsoLean);
b.sl = (float) Math.sin(pose.torsoLean);
b.cp = (float) Math.cos(pose.pitch);
b.sp = (float) Math.sin(pose.pitch);
b.cr = (float) Math.cos(pose.roll);
b.sr = (float) Math.sin(pose.roll);
b.px = pose.pivotX;
b.leg = leg;
b.lift = Float.isNaN(pose.boardLift) ? 0f : Math.max(0f, Math.min(hip, pose.boardLift));
b.fold = PushCycle.clamp01(pose.kneeFold);
b.folding = b.fold > 1e-4f || b.lift > 1e-4f;
b.hz = 0f;
// the hips to the soles: the folded thigh and shin (to the ankle) plus the flat foot below it
b.k = (hip * pose.legScale - b.lift) / hip;
b.len = 1f;
float c = 1f;
if (b.k >= 1f)
b.len = b.k;
else
{
c = (b.k - FOOT_FRACTION) / (1f - FOOT_FRACTION);
// the knees never fold tighter than cos 0.15 of the thigh's angle from straight down; the legs
// shorten instead
if (!(c >= 0.15f))
{
c = 0.15f;
b.len = Math.max(0f, b.k) / ((1f - FOOT_FRACTION) * 0.15f + FOOT_FRACTION);
}
}
b.knee = (float) Math.acos(c);
b.ck = c;
b.sk = (float) Math.sin(b.knee);
return b;
}

/**
* Poses one point; {@code rigid} (0..1) moves it with the upper body even below the hip line (a grab's arms,
* whose hands hang lower than the hips).
*/
void apply(float x, float y, float z, float rigid)
{
// height above the hips (negative), the same before and after the knees bend
float dy = y + hip;
if (y > -hip)
{
float w = leg == null ? 0f : leg.weight(x, y);
float lx = x;
float ly;
float lz = z;
if (w > 0f)
{
leg.move(x, y, z, w);
lx = leg.x;
ly = leg.y;
lz = leg.z;
}
else if (folding)
{
foldLeg(z, dy);
ly = this.y;
lz = this.z;
}
else
ly = y * s;
torso(x, dy, z, 1f);
x = lx + (this.x - lx) * rigid;
y = ly + (this.y - ly) * rigid;
z = lz + (this.z - lz) * rigid;
}
else
{
// the fold and lean fade in over 30 units above the hips, so there is no crease at the hip line
float w = Math.min(1f, -dy / 30f);
torso(x, dy, z, w + (1f - w) * rigid);
x = this.x;
y = this.y;
z = this.z;
}
// about the soles: lifted with the board in a grab, so the feet stay on it
y += lift;
float dx = x - px;
x = dx * cp - y * sp + px;
y = dx * sp + y * cp;
this.x = x;
this.y = z * sr + y * cr - lift;
this.z = z * cr - y * sr;
}

/**
* The upper body's fold and roll (not its lean along the board, nor the pitch) of the direction (dx, dy, dz),
* into {@code out}.
*/
void turnDirection(float dx, float dy, float dz, float[] out)
{
float bz = dz * cb - dy * sb;
float by = dz * sb + dy * cb;
out[0] = dx;
out[1] = bz * sr + by * cr;
out[2] = bz * cr - by * sr;
}

/** The torso's fold and lean about the hips by weight {@code w}; {@code dy} is the height above the hips (y down). */
private void torso(float x, float dy, float z, float w)
{
float bz = z * cb - dy * sb;
float by = z * sb + dy * cb;
z += (bz - z) * w;
dy += (by - dy) * w;
float lx = x * cl - dy * sl;
float ly = x * sl + dy * cl;
this.x = x + (lx - x) * w;
this.y = dy + (ly - dy) * w + hipY;
this.z = z;
}

/**
* A grab's legs for a point {@code rest} below the hips: squashed straight down to the feet at hip * k below
* the hips, and by {@link #fold} with the knees folded up toward the chest instead: the thigh swings forward by
* the knee angle, the shin back by the same, the foot stays flat; the sole lands on the same spot either way.
* The fold fades in below the hips (no crease at the hip line). Writes y and z.
*/
private void foldLeg(float z, float rest)
{
float squash = hipY + rest * k;
float a = fold * PushCycle.smoothstep(0f, LEG_BLEND * hip, rest);
if (a <= 0f)
{
this.y = squash;
this.z = z;
return;
}
ZY[0] = z - hz;
ZY[1] = rest * len;
float ankleY = (1f - FOOT_FRACTION) * hip * len;
// the foot turns forward about the ankle (flat again once the shin has turned back; over 4 units either
// side of it), the shin back about the knee (halfway from the hip to the ankle, so the shin brings the foot
// back under the hip) by twice the knee angle, and the whole leg forward about the hip
bend(knee, ankleY, 4f);
bend(-2f * knee, 0.5f * ankleY, KNEE_BAND);
turn(ck, sk);
this.y = squash + (hipY + ZY[1] - squash) * a;
this.z = z + (hz + ZY[0] - z) * a;
}
}

/**
* A grab's arms ({@link ArmLocator}): the grabbing one turns about the shoulder so the hand points at the grab
* point on the board ({@link BoardPlacement#grabTarget}), stretching (capped) to reach it; the free one swings out
* for balance. Each vertex follows by its arm weight times the pose's arm weight, so the arm eases in and out and
* never tears from the shoulder. At arm weight 0 it is exactly the plain body. Two reused instances (client thread
* only).
*/
private static final class Arm
{
static final Arm GRAB = new Arm();
static final Arm FREE = new Arm();

final ArmLocator locator = new ArmLocator();
final float[] target = new float[3];
/** Each vertex's arm weight, from the mesh before it moved; grown, never shrunk. */
float[] weights = new float[0];

/** The arm on {@code side}, found into {@code a}, or null when the mesh has no arm there. */
static Arm find(Arm a, float[] xs, float[] ys, float[] zs, int n, float side)
{
if (!a.locator.locate(xs, ys, zs, n, side))
return null;
if (a.weights.length < n)
a.weights = new float[n];
for (int i = 0; i < n; i++)
a.weights[i] = a.locator.weight(xs[i], ys[i]);
return a;
}

/** Turns the (already posed) arm so the hand goes to its side of a held board's hold point, by {@code weight}. */
void lift(float[] xs, float[] ys, float[] zs, int n, Body body, BodyPose pose, float weight)
{
reach(xs, ys, zs, n, body, pose.liftX + locator.side * pose.liftSpread, pose.liftY, pose.liftZ, weight, true);
}

/**
* Swings the (already posed) free arm out for balance, by {@code weight}: out an arm's length in the upper
* body's frame, folded and rolled with it (not tipped with a nose or tail grab's lean, which would point it at
* the ground): out along the board on its own side (0.8), a little up (0.1) and back (0.35, against the fold,
* so it ends a little above level).
*/
void balance(float[] xs, float[] ys, float[] zs, int n, Body body, float weight)
{
float[] sh = locator.shoulder;
body.apply(sh[0], sh[1], sh[2], 1f);
float ox = body.x;
float oy = body.y;
float oz = body.z;
float l = locator.armLength;
body.turnDirection(locator.side * 0.8f * l, -0.1f * l, 0.35f * l, target);
reach(xs, ys, zs, n, body, ox + target[0], oy + target[1], oz + target[2], weight, false);
}

/**
* Turns this (already posed) arm's vertices about the shoulder so the hand points at (tx, ty, tz), each vertex
* by its arm weight times {@code weight}; with {@code stretch} the arm stretches by the distance ratio, at most
* 1.6 (blocky models hide a lot) and shortening to 0.7 at least when the board is nearer than the hand.
*/
void reach(float[] xs, float[] ys, float[] zs, int n, Body body, float tx, float ty, float tz, float weight,
boolean stretch)
{
// the shoulder goes where the body put the arm's root
float[] sh = locator.shoulder;
body.apply(sh[0], sh[1], sh[2], 1f);
float ox = body.x;
float oy = body.y;
float oz = body.z;
int h = locator.handIndex;
float ux = xs[h] - ox;
float uy = ys[h] - oy;
float uz = zs[h] - oz;
tx -= ox;
ty -= oy;
tz -= oz;
float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
float lt = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
if (!(lu > 1f) || !(lt > 1f) || Float.isInfinite(lu) || Float.isInfinite(lt))
return;
// axis u x t, angle between them
float kx = uy * tz - uz * ty;
float ky = uz * tx - ux * tz;
float kz = ux * ty - uy * tx;
float kl = (float) Math.sqrt(kx * kx + ky * ky + kz * kz);
float dot = ux * tx + uy * ty + uz * tz;
// (anti)parallel: no turn, or straight back about any axis across the arm
boolean flat = kl < 1e-4f * lu * lt;
boolean alongZ = flat && Math.abs(uz) > 0.9f * lu;
float angle = flat && dot > 0f ? 0f : (float) Math.atan2(kl, dot);
kx = flat ? alongZ ? 1f : 0f : kx / kl;
ky = flat ? 0f : ky / kl;
kz = flat ? alongZ ? 0f : 1f : kz / kl;
float s = stretch ? Math.max(0.7f, Math.min(1.6f, lt / lu)) : 1f;
for (int i = 0; i < n; i++)
{
float a = weights[i] * weight;
if (a <= 0f)
continue;
float vx = xs[i] - ox;
float vy = ys[i] - oy;
float vz = zs[i] - oz;
// Rodrigues: v cos + (k x v) sin + k (k . v)(1 - cos), by this vertex's share of the turn
float c = (float) Math.cos(angle * a);
float sn = (float) Math.sin(angle * a);
float kd = (kx * vx + ky * vy + kz * vz) * (1f - c);
float scale = 1f + (s - 1f) * a;
xs[i] = ox + (vx * c + (ky * vz - kz * vy) * sn + kx * kd) * scale;
ys[i] = oy + (vy * c + (kz * vx - kx * vz) * sn + ky * kd) * scale;
zs[i] = oz + (vz * c + (kx * vy - ky * vx) * sn + kz * kd) * scale;
}
}
}

/**
* The push leg: every vertex below the hips on the {@link BodyPose#legSide} side of the body's centre
* plane. Two-bone IK with the knee halfway down: the knee bends forward (toward the chest, -z) when the
* target is nearer than the leg is long, the leg stretches (at most 1.3: blocky models hide 30%) when it is
* further; the leg then swings about the hip in the board plane to point at the target, and shears sideways
* toward it. Each vertex follows by its weight, which fades in below the hip line and over 4 units past the
* centre plane (the crotch), so the leg never tears from the body; at weight 0 it is exactly the plain crouch.
*/
private static final class Leg
{
private static final Leg SCRATCH = new Leg();

float hip;
float s;
float side;
float cx;
float hx;
float hz;
float weight;
float stretch;
float knee;
float swing;
float footZ;
/** Output of {@link #move}. */
float x;
float y;
float z;

/** The solved leg, or null if no vertex is on the push side. Reuses one instance (client thread only). */
static Leg solve(float[] xs, float[] ys, float[] zs, int n, float hip, BodyPose pose)
{
float minX = Float.MAX_VALUE;
float maxX = -Float.MAX_VALUE;
for (int i = 0; i < n; i++)
{
if (ys[i] > -hip)
{
minX = Math.min(minX, xs[i]);
maxX = Math.max(maxX, xs[i]);
}
}
Leg l = SCRATCH;
l.side = pose.legSide < 0f ? -1f : 1f;
l.cx = (minX + maxX) / 2f;
float sx = 0f;
float sz = 0f;
int count = 0;
for (int i = 0; i < n; i++)
{
if (ys[i] > -hip && (xs[i] - l.cx) * l.side > 0f)
{
sx += xs[i];
sz += zs[i];
count++;
}
}
if (count == 0)
return null;
l.hip = hip;
l.s = pose.legScale;
l.hx = sx / count;
l.hz = sz / count;
l.weight = Math.min(1f, pose.legWeight);
l.footZ = pose.footZ;
// from the (crouched) hip to the target, y down; the leg is hip long
float dx = pose.footX;
float dy = pose.footY + hip * l.s;
float d = (float) Math.hypot(dx, dy);
l.stretch = d >= hip ? Math.min(1.3f, d / hip) : 1f;
l.knee = d >= hip ? 0f : (float) Math.acos(Math.max(0f, d / hip));
l.swing = (float) Math.atan2(dx, dy);
return l;
}

float weight(float vx, float vy)
{
float below = PushCycle.smoothstep(0f, LEG_BLEND * hip, vy + hip);
float across = PushCycle.smoothstep(0f, 4f, (vx - cx) * side);
return weight * below * across;
}

void move(float vx, float vy, float vz, float w)
{
float len = s + (stretch - s) * w;
float b = knee * w;
float rest = vy + hip;
ZY[0] = vz - hz;
ZY[1] = rest * len;
// the shin turns back about the knee by 2b, then the whole leg forward about the hip by b
bend(-2f * b, 0.5f * hip * len, KNEE_BAND);
turn((float) Math.cos(b), (float) Math.sin(b));
float ux = vx - hx;
float uy = ZY[1];
// swing in the board plane about the hip
float c = (float) Math.cos(swing * w);
float sn = (float) Math.sin(swing * w);
x = hx + ux * c + uy * sn;
y = -hip * s - ux * sn + uy * c;
z = hz + (ZY[0] + footZ * w * rest / hip);
}
}
}
