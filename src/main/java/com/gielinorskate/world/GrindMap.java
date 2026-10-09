package com.gielinorskate.world;

import static com.gielinorskate.world.GridCollisionWorld.tile;

import java.util.*;
import lombok.AllArgsConstructor;

/** Every grindable edge in the loaded scene. Pure data; no client dependency. */
public final class GrindMap
{
/**
* Horizontal distance from the segment within which the skater snaps on (was 28). 44 is about a
* third of a tile: at 500 u/s a 0.02 s step moves 10 units, so a skater crossing a rail at the
* 80-degree approach limit spends (2 * 44) / (500 * sin 80) = 0.18 s, about 9 steps, inside the
* catch band instead of about 5.
*/
public static final float SNAP_DISTANCE = 44f;
/**
* The snap distance while the grind button is held (the Tony Hawk's American Wasteland preset's Y): a rail
* catches from further to the side. Never used without the button, so grinding is never harder than without it.
*/
public static final float ASSIST_SNAP_DISTANCE = 72f;
/** The tile buckets reach this far, so every segment within either snap distance is in a point's tile list. */
private static final float BUCKET_REACH = Math.max(SNAP_DISTANCE, ASSIST_SNAP_DISTANCE);
/** Rolling into a rail (and a pop coming back down onto the rail it left): this far below the top... */
public static final float BELOW_TOP = 16f;
/** ...or this far above it. */
public static final float ABOVE_TOP = 48f;
/**
* Largest angle between travel and the segment's line (either direction along it); was 80 (60 before
* that). 18% of the review's attempts were rejected at 80-85 degrees; physics redirects the velocity
* along the rail on lock (taking the direction from the steer when the travel is nearly square to it).
* Strictly below 90 so a skater crossing a rail exactly square-on still clears it.
*/
public static final float MAX_APPROACH = (float) Math.toRadians(88);
/**
* A lock needs at least this much rail left in the travel direction (review P5): the closest point is
* clamped to the segment, so a skater flying past the end it is heading for would otherwise lock for
* one step and hop straight off again.
*/
public static final float MIN_RAIL_AHEAD = 32f;
/**
* Below this |cos| between travel and the rail (about 78 degrees and steeper) the travel does not say
* which way along the rail to go; physics picks it from the steer (see {@link #enoughAhead}).
*/
public static final float AMBIGUOUS_ALONG = 0.2f;
/** Collinear touching segments whose tops differ by at most this merge into one. */
private static final float MERGE_TOP_TOLERANCE = 4f;

/** A lock-on candidate: the segment and the clamped parameter of the closest point on it. */
@AllArgsConstructor
public static final class Hit
{
public final GrindSegment segment;
public final float t;
}

private final List<GrindSegment> segments = new ArrayList<>();
/**
* Per-tile buckets for the lock-on and connection queries (review extra 7: the old linear scan ran every
* air step): each segment is listed on every tile its bounds, grown by {@link #BUCKET_REACH}, touch, so
* every segment within either snap distance of a point is in that point's tile list. Built lazily, dropped on
* any change.
*/
private Map<Long, List<GrindSegment>> buckets;

public void add(GrindSegment s)
{
segments.add(s);
buckets = null;
}

private static long key(int a, int b)
{
return ((long) a << 32) ^ (b & 0xffffffffL);
}

/** Segments that may lie within {@link #BUCKET_REACH} of the tile (tx, ty). */
private List<GrindSegment> near(int tx, int ty)
{
if (buckets == null)
{
buckets = new HashMap<>();
for (GrindSegment s : segments)
{
for (int x = tile(Math.min(s.x0, s.x1) - BUCKET_REACH); x <= tile(Math.max(s.x0, s.x1) + BUCKET_REACH); x++)
{
for (int y = tile(Math.min(s.y0, s.y1) - BUCKET_REACH); y <= tile(Math.max(s.y0, s.y1) + BUCKET_REACH); y++)
buckets.computeIfAbsent(key(x, y), k -> new ArrayList<>()).add(s);
}
}
}
return buckets.getOrDefault(key(tx, ty), Collections.emptyList());
}

public List<GrindSegment> segments()
{
return Collections.unmodifiableList(segments);
}

/**
* Joins collinear segments that touch or overlap and lie on the same height line: a flat or sloped run,
* extended, passes within {@link #MERGE_TOP_TOLERANCE} of both ends of the next piece. Identical duplicates
* collapse to one. Segments are bucketed by their line (angle in 0.001 rad bins, offset in 0.5 unit bins),
* sorted along it and swept, so this is O(n log n). A merged run keeps the height line (top and slope) of
* its first segment.
*/
public void merge()
{
Map<Long, List<GrindSegment>> lines = new LinkedHashMap<>();
for (GrindSegment s : segments)
{
float[] u = axis(s);
if (s.length() > 0f)
lines.computeIfAbsent(key(Math.round(lineAngle(s) / 0.001f), Math.round((u[1] * s.x0 - u[0] * s.y0) / 0.5f)),
k -> new ArrayList<>()).add(s);
}

segments.clear();
buckets = null;
for (List<GrindSegment> line : lines.values())
{
GrindSegment ref = line.get(0);
float[] u = axis(ref);
float base = u[0] * ref.x0 + u[1] * ref.y0;
List<float[]> runs = new ArrayList<>();
// sweep the pieces in order along the line: one joins the first run that it touches (a gap of at most
// 1 unit) and whose height line, extended, passes within the tolerance of both of its ends
line.stream().map(s -> span(s, u)).sorted((p, q) -> Float.compare(p[0], q[0])).forEach(sp ->
{
float[] into = runs.stream().filter(r -> sp[0] <= r[1] + 1f
&& Math.abs(r[2] + r[3] * (sp[0] - r[0]) - sp[2]) <= MERGE_TOP_TOLERANCE
&& Math.abs(r[2] + r[3] * (sp[1] - r[0]) - (sp[2] + sp[3] * (sp[1] - sp[0]))) <= MERGE_TOP_TOLERANCE)
.findFirst().orElse(null);
if (into == null)
runs.add(sp);
else
into[1] = Math.max(into[1], sp[1]);
});
for (float[] r : runs)
segments.add(new GrindSegment(ref.x0 + u[0] * (r[0] - base), ref.y0 + u[1] * (r[0] - base),
ref.x0 + u[0] * (r[1] - base), ref.y0 + u[1] * (r[1] - base), r[2], r[2] + r[3] * (r[1] - r[0])));
}
}

/** Segment s as a span {lo, hi, top at lo, rise per unit} along the line direction u. */
private static float[] span(GrindSegment s, float[] u)
{
float s0 = u[0] * s.x0 + u[1] * s.y0;
float s1 = u[0] * s.x1 + u[1] * s.y1;
float lo = Math.min(s0, s1);
float hi = Math.max(s0, s1);
float topLo = s0 <= s1 ? s.top0 : s.top1;
return new float[]{lo, hi, topLo, hi > lo ? ((s0 <= s1 ? s.top1 : s.top0) - topLo) / (hi - lo) : 0f};
}

/**
* The closest segment within {@code snap} horizontally, with h in [top - {@link #BELOW_TOP}, top +
* {@link #ABOVE_TOP}] and travel within {@link #MAX_APPROACH} of its line in either direction, skipping
* {@code exclude} (may be null); null if none qualifies.
*/
public Hit nearest(float x, float y, float h, float travelHeading, GrindSegment exclude, float snap)
{
return query(x, y, h, travelHeading, BELOW_TOP, ABOVE_TOP, exclude, null, snap);
}

/**
* True when a rail lies within {@code radius} horizontally of (x, y) with its top from {@link #BELOW_TOP} below
* {@code h} to {@code above} over it, whichever way it runs: there is a rail to grind near the skater.
*/
public boolean railWithin(float x, float y, float h, float radius, float above)
{
int span = (int) Math.ceil(Math.max(0f, radius - BUCKET_REACH) / GridCollisionWorld.TILE);
for (int i = -span; i <= span; i++)
{
for (int j = -span; j <= span; j++)
{
for (GrindSegment s : near(tile(x) + i, tile(y) + j))
{
float t = s.project(x, y);
float top = s.topAt(t);
if (s.length() > 0f && top >= h - BELOW_TOP && top <= h + above
&& Math.hypot(x - s.xAt(t), y - s.yAt(t)) <= radius)
return true;
}
}
}
return false;
}

/** {@link #nearestInAir(float, float, float, float, float, GrindSegment, GrindSegment, float)} at {@link #SNAP_DISTANCE}. */
public Hit nearestInAir(float x, float y, float h, float vh, float travelHeading, GrindSegment exclude,
GrindSegment excludeRising)
{
return nearestInAir(x, y, h, vh, travelHeading, exclude, excludeRising, SNAP_DISTANCE);
}

/**
* Lock-on query for an airborne skater with vertical speed {@code vh} (the grind magnet, review P3): h in
* [top - 32, top + 90] while rising (vh &gt; 0; the review suggested 40 below, 32 keeps the band a little
* tighter under the rail; 90 above, was 48, so a high pop crossing a low rail still catches it), else in
* [top - 28, top + 160] so coming down from a big pop over a rail catches it. Skips {@code exclude} always
* and {@code excludeRising} (the rail just popped off) while rising; once falling that rail only catches in
* the plain [top - {@link #BELOW_TOP}, top + {@link #ABOVE_TOP}] window, so a hop along it lands back on it
* instead of snapping down from the top of the hop (review P1). Both may be null. As {@link #nearest}
* otherwise, including {@link #MIN_RAIL_AHEAD}.
*/
public Hit nearestInAir(float x, float y, float h, float vh, float travelHeading, GrindSegment exclude,
GrindSegment excludeRising, float snap)
{
if (vh > 0f)
return query(x, y, h, travelHeading, 32f, 90f, exclude, excludeRising, snap);
Hit hit = query(x, y, h, travelHeading, 28f, 160f, exclude, excludeRising, snap);
if (excludeRising == null || excludeRising == exclude)
return hit;
Hit back = query(x, y, h, travelHeading, BELOW_TOP, ABOVE_TOP, exclude, null, snap);
if (back == null || back.segment != excludeRising)
return hit;
return hit == null || back.segment.distance(x, y, back.t) < hit.segment.distance(x, y, hit.t) ? back : hit;
}

/** Closest qualifying segment for a height window [top - below, top + above]; see {@link #nearest}. */
private Hit query(float x, float y, float h, float travelHeading, float below, float above, GrindSegment exclude,
GrindSegment exclude2, float snap)
{
Hit best = null;
float bestDist = Float.POSITIVE_INFINITY;
for (GrindSegment s : near(tile(x), tile(y)))
{
if (s == exclude || s == exclude2 || s.length() <= 0f)
continue;
float t = s.project(x, y);
float top = s.topAt(t);
float d = s.distance(x, y, t);
if (h >= top - below && h <= top + above && d <= snap && d < bestDist
&& s.lineAngle(travelHeading) <= MAX_APPROACH + 1e-4f && enoughAhead(s, t, travelHeading))
{
best = new Hit(s, t);
bestDist = d;
}
}
return best;
}

/**
* True when at least {@link #MIN_RAIL_AHEAD} of {@code s} lies ahead of parameter {@code t} for a skater
* travelling along {@code travelHeading}. When the travel is nearly square to the rail
* ({@link #AMBIGUOUS_ALONG}) either way along it will do, since the steer picks the direction.
*/
public static boolean enoughAhead(GrindSegment s, float t, float travelHeading)
{
float along = (float) Math.cos(travelHeading - s.heading());
float ahead = Math.abs(along) < AMBIGUOUS_ALONG ? Math.max(t, 1f - t) : along > 0f ? 1f - t : t;
return ahead * s.length() >= MIN_RAIL_AHEAD;
}

/**
* The segment a grind arriving at (x, y) at height {@code top}, travelling along (dirX, dirY), carries
* on to: one end within 24 of (x, y), its top at that end within 24 of {@code top} (the height where the
* grind arrives, so sloped pieces compare where they meet) and its direction away from that end within 75
* degrees of the travel direction. Skips {@code exclude} (the current segment). The straightest candidate
* wins, then the closest; null if none.
*/
public GrindSegment connectedAt(float x, float y, float top, float dirX, float dirY, GrindSegment exclude)
{
float dl = (float) Math.hypot(dirX, dirY);
if (dl <= 0f)
return null;
float ux = dirX / dl;
float uy = dirY / dl;
float minCos = (float) Math.cos((float) Math.toRadians(75)) - 1e-4f;
GrindSegment best = null;
float bestCos = Float.NEGATIVE_INFINITY;
float bestDist = Float.POSITIVE_INFINITY;
for (GrindSegment s : near(tile(x), tile(y)))
{
float d0 = (float) Math.hypot(s.x0 - x, s.y0 - y);
float d1 = (float) Math.hypot(s.x1 - x, s.y1 - y);
boolean fromStart = d0 <= d1;
float d = fromStart ? d0 : d1;
float cos = ((s.x1 - s.x0) * ux + (s.y1 - s.y0) * uy) * (fromStart ? 1f : -1f) / s.length();
// heights compared where the pieces meet, so a sloped rail runs on into a flat one at its high end
if (s != exclude && s.length() > 0f && d <= 24f && Math.abs((fromStart ? s.top0 : s.top1) - top) <= 24f && cos >= minCos
&& (cos > bestCos + 1e-4f || cos > bestCos - 1e-4f && d < bestDist))
{
best = s;
bestCos = cos;
bestDist = d;
}
}
return best;
}

/**
* Unit vector {sin, cos} of the segment's line direction folded into [0, PI), so both orientations of a
* segment share a bucket, with float noise (e.g. cos(PI/2)) snapped to an exact 0 so axis-aligned merges
* keep exact coordinates.
*/
private static float[] axis(GrindSegment s)
{
float a = lineAngle(s);
float sin = (float) Math.sin(a);
float cos = (float) Math.cos(a);
return new float[]{Math.abs(sin) < 1e-6f ? 0f : sin, Math.abs(cos) < 1e-6f ? 0f : cos};
}

/** Line direction folded into [0, PI). */
private static float lineAngle(GrindSegment s)
{
float a = s.heading();
if (a < 0f)
a += (float) Math.PI;
return a >= (float) Math.PI - 1e-4f ? a - (float) Math.PI : a;
}
}
