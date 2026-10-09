package com.gielinorskate.world;

import static com.gielinorskate.world.GridCollisionWorld.tile;

import com.gielinorskate.physics.Contact;
import java.util.*;

/**
* Tight per-object collision boxes: oriented rectangles with an absolute top height and a kind, indexed per
* tile for constant-time lookups. Pure; no client dependency. Immutable once built; query from one thread.
* <p>
* A box is a row {cx, cy, hx, hy, cos, sin, top, kind, flags}: centred at (cx, cy) with half extents hx along
* its u axis (cos, sin, a unit vector) and hy along its v axis (-sin, cos). Each box is registered
* (compressed-sparse-row index) in every tile its axis-aligned bounds, grown by the skater radius
* {@link #SKATER_R}, overlap, so a query for a skater of radius up to {@link #SKATER_R} reads only the list of
* the tile it stands on.
* <ul>
* <li>{@link #SOLID}: collides on its sides; tall things (rocks, trees, walls).</li>
* <li>{@link #LOW}: collides on its sides; you can land on top when {@link #LANDABLE} (crates, platforms).
* Rolling into one is blocked by its raised ground, not by {@link #blockTop}.</li>
* <li>{@link #PASS}: never collides (plants, open doors).</li>
* </ul>
*/
public final class BlockerSet
{
/** Skater/board radius in local units. */
public static final float SKATER_R = 12f;
public static final byte SOLID = 0;
public static final byte LOW = 1;
public static final byte PASS = 2;
/** groundHeight is raised to the top inside the box (not its skater margin). */
public static final int LANDABLE = 1;
/** The box's top edges are grindable ledges. */
public static final int LEDGES = 2;

final float[][] boxes;
final int size;
/** Per tile (index tx * size + ty) the boxes registered in it, in box order. */
final float[][][] tileBoxes;
/** Scratch normal for {@link #depth}; queries run on the client thread only, so one is shared. */
private final float[] scratchN = new float[2];

BlockerSet(List<float[]> boxes, int size)
{
this.boxes = boxes.toArray(new float[0][]);
this.size = Math.max(0, size);
List<List<float[]>> tiles = new ArrayList<>();
for (int t = 0; t < this.size * this.size; t++)
tiles.add(new ArrayList<>());
for (float[] b : boxes)
{
float ac = Math.abs(b[4]);
float as = Math.abs(b[5]);
// the box's bounds plus the skater radius, clipped to the grid
float ex = ac * b[2] + as * b[3] + SKATER_R;
float ey = as * b[2] + ac * b[3] + SKATER_R;
for (int tx = Math.max(0, tile(b[0] - ex)); tx <= Math.min(this.size - 1, tile(b[0] + ex)); tx++)
{
for (int ty = Math.max(0, tile(b[1] - ey)); ty <= Math.min(this.size - 1, tile(b[1] + ey)); ty++)
tiles.get(tx * this.size + ty).add(b);
}
}
tileBoxes = tiles.stream().map(l -> l.toArray(new float[0][])).toArray(float[][][]::new);
}

public static BlockerSet empty()
{
return new BlockerSet(Collections.emptyList(), 0);
}

/**
* Deepest overlap of a circle of radius r at (x, y) with any non-PASS box whose top is above
* feetH + maxStep; see {@link com.gielinorskate.physics.CollisionWorld#contact}.
*/
public boolean contact(float x, float y, float r, float feetH, float maxStep, Contact out)
{
float spill = Math.max(0f, r - SKATER_R);
boolean hit = false;
float best = 0f;
for (int tx = Math.max(0, tile(x - spill)); tx <= Math.min(size - 1, tile(x + spill)); tx++)
{
for (int ty = Math.max(0, tile(y - spill)); ty <= Math.min(size - 1, tile(y + spill)); ty++)
{
for (float[] b : tileBoxes[tx * size + ty])
{
if (b[7] == PASS || b[6] <= feetH + maxStep)
continue;
float d = depth(b, x, y, r, scratchN);
if (d > 0f && (!hit || d > best))
{
hit = true;
best = d;
out.set(scratchN[0], scratchN[1], d, b[6]);
}
}
}
}
return hit;
}

/** Highest top of a {@link #LANDABLE} non-PASS box containing (x, y), or -infinity. */
public float landTop(float x, float y)
{
float best = Float.NEGATIVE_INFINITY;
int t = tileIndex(x, y);
if (t < 0)
return best;
for (float[] b : tileBoxes[t])
{
if (b[7] != PASS && ((int) b[8] & LANDABLE) != 0 && b[6] > best && Math.abs(u(b, x, y)) <= b[2]
&& Math.abs(v(b, x, y)) <= b[3])
best = b[6];
}
return best;
}

/**
* Highest top of a {@link #SOLID} box the skater (radius {@code r}, capped at the {@link #SKATER_R} the
* tile index is grown by) would push into when moving from (x0, y0) to (x1, y1), or -infinity. A move counts
* when its end is more deeply inside a box than its start, or when it starts outside a box and its midpoint is
* inside (so a fast step cannot skip a thin box): a skater already overlapping a box can always slide along it
* or move out of it, never further in (least-penetration escape).
*/
public float blockTop(float x0, float y0, float x1, float y1, float r)
{
r = Math.min(r, SKATER_R);
float best = Float.NEGATIVE_INFINITY;
float[] n = scratchN;
for (int pass = 0; pass < 2; pass++)
{
float px = pass == 0 ? x1 : (x0 + x1) / 2f;
float py = pass == 0 ? y1 : (y0 + y1) / 2f;
int t = tileIndex(px, py);
if (t < 0)
continue;
for (float[] b : tileBoxes[t])
{
if (b[7] != SOLID || b[6] <= best)
continue;
float d = depth(b, px, py, r, n);
if (d <= 0f)
continue;
float d0 = depth(b, x0, y0, r, n);
// the midpoint only catches a step over a thin box from outside it, so only a move whose path
// crosses the box itself counts: one that grazes past a corner within the skater radius, its end
// clear, is not stepping over anything (it used to block, and with no contact at the clear end to
// slide along the skater stopped dead against the corner). From inside, only where the move
// ends matters (crossing the middle of a box on the way out is fine). The 1e-3 is how much the
// penetration must grow to count as going deeper.
if (pass == 0 ? d > Math.max(0f, d0) + 1e-3f : d0 <= 0f && crossesBox(b, x0, y0, x1, y1))
best = b[6];
}
}
return best;
}

/** Box b's u coordinate of (x, y). */
private static float u(float[] b, float x, float y)
{
return (x - b[0]) * b[4] + (y - b[1]) * b[5];
}

/** Box b's v coordinate of (x, y). */
private static float v(float[] b, float x, float y)
{
return -(x - b[0]) * b[5] + (y - b[1]) * b[4];
}

/**
* Penetration of a circle of radius r at (x, y) into box b (positive = overlapping), with the push-out
* normal in n. Outside the box: r minus the distance to it. Inside: r plus the distance to the nearest
* face, along that face's normal.
*/
private static float depth(float[] b, float x, float y, float r, float[] n)
{
float c = b[4];
float s = b[5];
float lu = u(b, x, y);
float lv = v(b, x, y);
float du = lu - Math.max(-b[2], Math.min(b[2], lu));
float dv = lv - Math.max(-b[3], Math.min(b[3], lv));
if (du != 0f || dv != 0f)
{
float d = (float) Math.sqrt(du * du + dv * dv);
float nu = du / d;
float nv = dv / d;
n[0] = nu * c - nv * s;
n[1] = nu * s + nv * c;
return r - d;
}
float eu = b[2] - Math.abs(lu);
float ev = b[3] - Math.abs(lv);
if (eu < ev)
{
float su = lu < 0f ? -1f : 1f;
n[0] = su * c;
n[1] = su * s;
return r + eu;
}
float sv = lv < 0f ? -1f : 1f;
n[0] = -sv * s;
n[1] = sv * c;
return r + ev;
}

/** True when the segment (x0, y0)-(x1, y1) passes through box b itself (no skater radius): a slab clip. */
private static boolean crossesBox(float[] b, float x0, float y0, float x1, float y1)
{
float u0 = u(b, x0, y0);
float v0 = v(b, x0, y0);
float[] range = {0f, 1f};
return clip(u0, u(b, x1, y1) - u0, b[2], range) && clip(v0, v(b, x1, y1) - v0, b[3], range);
}

/** Narrows range {tMin, tMax} to where p + t * d lies within [-h, h]; false when that is empty. */
private static boolean clip(float p, float d, float h, float[] range)
{
if (Math.abs(d) < 1e-9f)
return Math.abs(p) <= h;
float ta = (-h - p) / d;
float tb = (h - p) / d;
range[0] = Math.max(range[0], Math.min(ta, tb));
range[1] = Math.min(range[1], Math.max(ta, tb));
return range[0] <= range[1];
}

/** Index of the tile holding (x, y), or -1 off the grid. */
private int tileIndex(float x, float y)
{
int tx = tile(x);
int ty = tile(y);
return tx < 0 || ty < 0 || tx >= size || ty >= size ? -1 : tx * size + ty;
}

/** The four corners of box b into out = {x0, y0, .. x3, y3}: -u-v, +u-v, +u+v, -u+v (counter-clockwise). */
static void corners(float[] b, float[] out)
{
float ux = b[4] * b[2];
float uy = b[5] * b[2];
float vx = -b[5] * b[3];
float vy = b[4] * b[3];
for (int k = 0; k < 4; k++)
{
float su = k == 1 || k == 2 ? 1f : -1f;
float sv = k < 2 ? -1f : 1f;
out[2 * k] = b[0] + su * ux + sv * vx;
out[2 * k + 1] = b[1] + su * uy + sv * vy;
}
}
}
