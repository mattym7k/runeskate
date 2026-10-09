package com.gielinorskate.world;

import java.util.ArrayList;
import java.util.List;

/**
* What a scene object's model footprint becomes for the skater. Pure; no client dependency.
* <p>
* Blockers: a {@link BlockerSet#SOLID} box, a {@link BlockerSet#LOW} box you can land on, or
* {@link BlockerSet#PASS} clutter you ride straight through ({@link #classify}), cut from the model slice at
* skater height ({@link #sliceExtents}).
* <p>
* Grinds: model space x maps to local x and model z to local y, rotated by the object's orientation in JAU
* (0..2047, 2048 = a full turn) the way the client draws it: x' = x cos + z sin, y' = z cos - x sin.
* <ul>
* <li>A long object (shorter extent below 0.75 of the longer, e.g. a fence or railing) gives one rail along
* its long axis through the middle of the model, as long as that extent: fences are drawn through the middle
* of their tile, not on its edges.</li>
* <li>A square-ish object (extents within 25% of each other: bench, crate, table) gives the four edges of its
* rotated bounding rectangle, so ledges sit on the object's real edges rather than the tile's.</li>
* </ul>
*/
public final class ObjectShapes
{
/** Vertices up to this height (local units above the model base) make the footprint: the skater's height. */
public static final float SLICE_HEIGHT = 90f;

private ObjectShapes()
{
}

/**
* Kind of a blocker cut from a model: PASS when {@code passByName} (plants, open doors), for anything
* that can be stepped over (height 24 or lower: the physics max step up) and for thin slices (posts, stalks:
* shorter side under 30); LOW for up to 120 tall (as full-tile platforms) with a shorter side of at least 40
* to stand on; SOLID otherwise.
*/
public static byte classify(boolean passByName, float height, float sliceMinSide)
{
return passByName || height <= 24f || sliceMinSide < 30f ? BlockerSet.PASS
: height <= GridCollisionWorld.PLATFORM_MAX_HEIGHT && sliceMinSide >= 40f ? BlockerSet.LOW : BlockerSet.SOLID;
}

/**
* {minX, maxX, minZ, maxZ} of the first {@code count} model vertices at most {@code maxHeight} above the
* base (RuneLite model y is negative-up, so height = -y), or null when none are that low.
*/
public static float[] sliceExtents(float[] xs, float[] ys, float[] zs, int count, float maxHeight)
{
float[] e = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
boolean any = false;
for (int i = 0; i < count; i++)
{
if (-ys[i] <= maxHeight)
{
any = true;
e[0] = Math.min(e[0], xs[i]);
e[1] = Math.max(e[1], xs[i]);
e[2] = Math.min(e[2], zs[i]);
e[3] = Math.max(e[3], zs[i]);
}
}
return any ? e : null;
}

/** Shorter side of extents {minX, maxX, minZ, maxZ}. */
public static float minSide(float[] ext)
{
return Math.min(ext[1] - ext[0], ext[3] - ext[2]);
}

/**
* Oriented box {cx, cy, hx, hy, cos, sin} for extents {minX, maxX, minZ, maxZ} of an object at local
* (originX, originY) drawn with the given orientation (JAU), using the grind rotation, so the model x axis
* (hx) runs along (cos, -sin) of the angle. Half extents below {@code minHalf} are inflated to it.
*/
public static float[] box(float originX, float originY, float[] ext, int orientation, float minHalf)
{
float[] c = centre(originX, originY, ext, orientation);
float a = radians(orientation);
return new float[]{c[0], c[1], Math.max(minHalf, (ext[1] - ext[0]) / 2f), Math.max(minHalf, (ext[3] - ext[2]) / 2f),
(float) Math.cos(a), (float) -Math.sin(a)};
}

/** Local (x, y) of the centre of the extents, for an object at (originX, originY). */
public static float[] centre(float originX, float originY, float[] ext, int orientation)
{
return toLocal(originX, originY, (ext[0] + ext[1]) / 2f, (ext[2] + ext[3]) / 2f, orientation);
}

/**
* The grind segments (top 0, the caller sets it) of an object at local (originX, originY) whose model has
* extents {@code ext} = {minX, maxX, minZ, maxZ}.
*/
public static List<GrindSegment> segments(float originX, float originY, float[] ext, int orientation)
{
float lenX = ext[1] - ext[0];
float lenZ = ext[3] - ext[2];
float longer = Math.max(lenX, lenZ);
float cx = (ext[0] + ext[1]) / 2f;
float cz = (ext[2] + ext[3]) / 2f;
// model-space points {x, z, x, z, ...} joined in order
float[] p = longer <= 0f ? new float[0]
: Math.min(lenX, lenZ) >= 0.75f * longer
? new float[]{ext[0], ext[2], ext[1], ext[2], ext[1], ext[3], ext[0], ext[3], ext[0], ext[2]}
: lenX >= lenZ ? new float[]{ext[0], cz, ext[1], cz} : new float[]{cx, ext[2], cx, ext[3]};
List<GrindSegment> out = new ArrayList<>();
for (int i = 0; i + 3 < p.length; i += 2)
{
float[] a = toLocal(originX, originY, p[i], p[i + 1], orientation);
float[] b = toLocal(originX, originY, p[i + 2], p[i + 3], orientation);
out.add(new GrindSegment(a[0], a[1], b[0], b[1], 0f));
}
return out;
}

/** An orientation in JAU (2048 to a full turn) in radians. */
private static float radians(int orientation)
{
return (orientation & 2047) * (float) (2 * Math.PI / 2048);
}

private static float[] toLocal(float ox, float oy, float mx, float mz, int orientation)
{
float a = radians(orientation);
float sin = (float) Math.sin(a);
float cos = (float) Math.cos(a);
return new float[]{ox + mx * cos + mz * sin, oy + mz * cos - mx * sin};
}
}
