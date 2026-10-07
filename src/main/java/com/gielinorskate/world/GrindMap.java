package com.gielinorskate.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * Airborne and rising, the skater locks on from this far below the top... (review P3 magnet; the review
	 * suggested 40, 32 keeps the band a little tighter under the rail)
	 */
	public static final float RISING_BELOW_TOP = 32f;
	/** ...up to this far above it (was 48: a high pop crossing a low rail now still catches it). */
	public static final float RISING_ABOVE_TOP = 90f;
	/** Airborne and falling: from this far below the top... */
	public static final float FALLING_BELOW_TOP = 28f;
	/** ...up to this far above it, so coming down from a big pop over a rail catches it. */
	public static final float FALLING_ABOVE_TOP = 160f;
	/**
	 * Largest angle between travel and the segment's line (either direction along it); was 80 (60 before
	 * that). 18% of the review's attempts were rejected at 80-85 degrees; physics redirects the velocity
	 * along the rail on lock (taking the direction from the steer when the travel is nearly square to it).
	 * Strictly below 90 so a skater crossing a rail exactly square-on still clears it.
	 */
	public static final float MAX_APPROACH = (float) Math.toRadians(88);
	/** A grind carries on to another segment whose end is within this of the current end... */
	public static final float CONNECT_DISTANCE = 24f;
	/** ...whose top at that end differs by at most this from the top where the grind leaves the current one... */
	public static final float CONNECT_TOP = 24f;
	/** ...and whose direction turns by at most this. */
	public static final float CONNECT_MAX_BEND = (float) Math.toRadians(75);
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
	public static final float MERGE_TOP_TOLERANCE = 4f;

	/** Merge bucketing: line angle in 0.001 rad bins, line offset in 0.5 unit bins; touching means a gap of at most 1 unit. */
	private static final float ANGLE_BIN = 0.001f;
	private static final float OFFSET_BIN = 0.5f;
	private static final float TOUCH_GAP = 1f;

	/** A lock-on candidate: the segment and the clamped parameter of the closest point on it. */
	public static final class Hit
	{
		public final GrindSegment segment;
		public final float t;

		Hit(GrindSegment segment, float t)
		{
			this.segment = segment;
			this.t = t;
		}
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

	private static long tileKey(int tx, int ty)
	{
		return ((long) tx << 32) ^ (ty & 0xffffffffL);
	}

	private static int tile(float v)
	{
		return (int) Math.floor(v / GridCollisionWorld.TILE);
	}

	/** Segments that may lie within {@link #BUCKET_REACH} of (x, y). */
	private List<GrindSegment> near(float x, float y)
	{
		List<GrindSegment> l = buckets().get(tileKey(tile(x), tile(y)));
		return l == null ? Collections.emptyList() : l;
	}

	private Map<Long, List<GrindSegment>> buckets()
	{
		if (buckets == null)
		{
			Map<Long, List<GrindSegment>> b = new java.util.HashMap<>();
			for (GrindSegment s : segments)
			{
				int x0 = tile(Math.min(s.x0, s.x1) - BUCKET_REACH);
				int x1 = tile(Math.max(s.x0, s.x1) + BUCKET_REACH);
				int y0 = tile(Math.min(s.y0, s.y1) - BUCKET_REACH);
				int y1 = tile(Math.max(s.y0, s.y1) + BUCKET_REACH);
				for (int tx = x0; tx <= x1; tx++)
				{
					for (int ty = y0; ty <= y1; ty++)
					{
						b.computeIfAbsent(tileKey(tx, ty), k -> new ArrayList<>()).add(s);
					}
				}
			}
			buckets = b;
		}
		return buckets;
	}

	public List<GrindSegment> segments()
	{
		return Collections.unmodifiableList(segments);
	}

	/**
	 * Joins collinear segments that touch or overlap and lie on the same height line: a flat or sloped run,
	 * extended, passes within {@link #MERGE_TOP_TOLERANCE} of both ends of the next piece. Identical duplicates
	 * collapse to one. Segments are bucketed by their line (angle and offset), sorted along it and swept, so
	 * this is O(n log n). A merged run keeps the height line (top and slope) of its first segment.
	 */
	public void merge()
	{
		Map<Long, List<GrindSegment>> lines = new LinkedHashMap<>();
		for (GrindSegment s : segments)
		{
			if (s.length() <= 0f)
			{
				continue;
			}
			float a = lineAngle(s);
			float ux = axis((float) Math.sin(a));
			float uy = axis((float) Math.cos(a));
			float offset = uy * s.x0 - ux * s.y0;
			long key = ((long) Math.round(a / ANGLE_BIN) << 32) ^ (Math.round(offset / OFFSET_BIN) & 0xffffffffL);
			lines.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
		}

		List<GrindSegment> out = new ArrayList<>();
		for (List<GrindSegment> line : lines.values())
		{
			GrindSegment ref = line.get(0);
			float a = lineAngle(ref);
			float ux = axis((float) Math.sin(a));
			float uy = axis((float) Math.cos(a));
			float base = ux * ref.x0 + uy * ref.y0;

			List<float[]> spans = new ArrayList<>(); // {lo, hi, top at lo, rise per unit}
			for (GrindSegment s : line)
			{
				float s0 = ux * s.x0 + uy * s.y0;
				float s1 = ux * s.x1 + uy * s.y1;
				float lo = Math.min(s0, s1);
				float hi = Math.max(s0, s1);
				float topLo = s0 <= s1 ? s.top0 : s.top1;
				float topHi = s0 <= s1 ? s.top1 : s.top0;
				spans.add(new float[]{lo, hi, topLo, hi > lo ? (topHi - topLo) / (hi - lo) : 0f});
			}
			spans.sort((p, q) -> Float.compare(p[0], q[0]));

			List<float[]> runs = new ArrayList<>();
			for (float[] sp : spans)
			{
				float[] into = null;
				for (float[] r : runs)
				{
					// same height line: the run, extended, passes within the tolerance of both of this span's ends
					if (sp[0] <= r[1] + TOUCH_GAP
						&& Math.abs(r[2] + r[3] * (sp[0] - r[0]) - sp[2]) <= MERGE_TOP_TOLERANCE
						&& Math.abs(r[2] + r[3] * (sp[1] - r[0]) - (sp[2] + sp[3] * (sp[1] - sp[0]))) <= MERGE_TOP_TOLERANCE)
					{
						into = r;
						break;
					}
				}
				if (into == null)
				{
					runs.add(sp.clone());
				}
				else
				{
					into[1] = Math.max(into[1], sp[1]);
				}
			}
			for (float[] r : runs)
			{
				out.add(new GrindSegment(
					ref.x0 + ux * (r[0] - base), ref.y0 + uy * (r[0] - base),
					ref.x0 + ux * (r[1] - base), ref.y0 + uy * (r[1] - base),
					r[2], r[2] + r[3] * (r[1] - r[0])));
			}
		}
		segments.clear();
		segments.addAll(out);
		buckets = null;
	}

	/**
	 * The closest segment within {@link #SNAP_DISTANCE} horizontally, with h in
	 * [top - {@link #BELOW_TOP}, top + {@link #ABOVE_TOP}] and travel within {@link #MAX_APPROACH} of its
	 * line in either direction; null if none qualifies.
	 */
	public Hit nearest(float x, float y, float h, float travelHeading)
	{
		return nearest(x, y, h, travelHeading, null);
	}

	/** As {@link #nearest(float, float, float, float)}, skipping {@code exclude} (may be null). */
	public Hit nearest(float x, float y, float h, float travelHeading, GrindSegment exclude)
	{
		return nearest(x, y, h, travelHeading, exclude, SNAP_DISTANCE);
	}

	/** As {@link #nearest(float, float, float, float, GrindSegment)}, within {@code snap} horizontally. */
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
		int tx = tile(x);
		int ty = tile(y);
		for (int i = -span; i <= span; i++)
		{
			for (int j = -span; j <= span; j++)
			{
				List<GrindSegment> l = buckets().get(tileKey(tx + i, ty + j));
				if (l == null)
				{
					continue;
				}
				for (GrindSegment s : l)
				{
					if (s.length() <= 0f)
					{
						continue;
					}
					float t = s.project(x, y);
					float top = s.topAt(t);
					if (top < h - BELOW_TOP || top > h + above)
					{
						continue;
					}
					if (Math.hypot(x - s.xAt(t), y - s.yAt(t)) <= radius)
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Lock-on query for an airborne skater with vertical speed {@code vh} (the grind magnet, review P3): h in
	 * [top - {@link #RISING_BELOW_TOP}, top + {@link #RISING_ABOVE_TOP}] while rising (vh &gt; 0), else in
	 * [top - {@link #FALLING_BELOW_TOP}, top + {@link #FALLING_ABOVE_TOP}]. Skips {@code exclude} always and
	 * {@code excludeRising} (the rail just popped off) while rising; once falling that rail only catches in
	 * the plain [top - {@link #BELOW_TOP}, top + {@link #ABOVE_TOP}] window, so a hop along it lands back on it
	 * instead of snapping down from the top of the hop (review P1). Both may be null. As {@link #nearest}
	 * otherwise, including {@link #MIN_RAIL_AHEAD}.
	 */
	public Hit nearestInAir(float x, float y, float h, float vh, float travelHeading, GrindSegment exclude,
		GrindSegment excludeRising)
	{
		return nearestInAir(x, y, h, vh, travelHeading, exclude, excludeRising, SNAP_DISTANCE);
	}

	/** As {@link #nearestInAir(float, float, float, float, float, GrindSegment, GrindSegment)}, within {@code snap}. */
	public Hit nearestInAir(float x, float y, float h, float vh, float travelHeading, GrindSegment exclude,
		GrindSegment excludeRising, float snap)
	{
		if (vh > 0f)
		{
			return query(x, y, h, travelHeading, RISING_BELOW_TOP, RISING_ABOVE_TOP, exclude, excludeRising, snap);
		}
		Hit hit = query(x, y, h, travelHeading, FALLING_BELOW_TOP, FALLING_ABOVE_TOP, exclude, excludeRising, snap);
		if (excludeRising == null || excludeRising == exclude)
		{
			return hit;
		}
		Hit back = query(x, y, h, travelHeading, BELOW_TOP, ABOVE_TOP, exclude, null, snap);
		if (back == null || back.segment != excludeRising)
		{
			return hit;
		}
		return hit == null || distance(back, x, y) < distance(hit, x, y) ? back : hit;
	}

	private static float distance(Hit hit, float x, float y)
	{
		return (float) Math.hypot(x - hit.segment.xAt(hit.t), y - hit.segment.yAt(hit.t));
	}

	/** Closest qualifying segment for a height window [top - below, top + above]; see {@link #nearest}. */
	private Hit query(float x, float y, float h, float travelHeading, float below, float above, GrindSegment exclude,
		GrindSegment exclude2, float snap)
	{
		GrindSegment best = null;
		float bestT = 0f;
		float bestDist = Float.POSITIVE_INFINITY;
		for (GrindSegment s : near(x, y))
		{
			if (s == exclude || s == exclude2 || s.length() <= 0f)
			{
				continue;
			}
			float t = s.project(x, y);
			float top = s.topAt(t);
			if (h < top - below || h > top + above)
			{
				continue;
			}
			float d = (float) Math.hypot(x - s.xAt(t), y - s.yAt(t));
			if (d > snap || d >= bestDist)
			{
				continue;
			}
			if (s.lineAngle(travelHeading) > MAX_APPROACH + 1e-4f || !enoughAhead(s, t, travelHeading))
			{
				continue;
			}
			best = s;
			bestT = t;
			bestDist = d;
		}
		return best == null ? null : new Hit(best, bestT);
	}

	/**
	 * True when at least {@link #MIN_RAIL_AHEAD} of {@code s} lies ahead of parameter {@code t} for a skater
	 * travelling along {@code travelHeading}. When the travel is nearly square to the rail
	 * ({@link #AMBIGUOUS_ALONG}) either way along it will do, since the steer picks the direction.
	 */
	public static boolean enoughAhead(GrindSegment s, float t, float travelHeading)
	{
		float len = s.length();
		float along = (float) Math.cos(travelHeading - s.heading());
		float ahead;
		if (Math.abs(along) < AMBIGUOUS_ALONG)
		{
			ahead = Math.max(t, 1f - t) * len;
		}
		else
		{
			ahead = along > 0f ? (1f - t) * len : t * len;
		}
		return ahead >= MIN_RAIL_AHEAD;
	}

	/**
	 * The segment a grind arriving at (x, y) at height {@code top}, travelling along (dirX, dirY), carries
	 * on to: one end within {@link #CONNECT_DISTANCE} of (x, y), its top at that end within
	 * {@link #CONNECT_TOP} of {@code top} (the height where the grind arrives, so sloped pieces compare where
	 * they meet), and its direction away from that end within {@link #CONNECT_MAX_BEND} of the travel
	 * direction. Skips
	 * {@code exclude} (the current segment). The straightest candidate wins, then the closest; null if none.
	 */
	public GrindSegment connectedAt(float x, float y, float top, float dirX, float dirY, GrindSegment exclude)
	{
		float dl = (float) Math.hypot(dirX, dirY);
		if (dl <= 0f)
		{
			return null;
		}
		float ux = dirX / dl;
		float uy = dirY / dl;
		float minCos = (float) Math.cos(CONNECT_MAX_BEND) - 1e-4f;
		GrindSegment best = null;
		float bestCos = Float.NEGATIVE_INFINITY;
		float bestDist = Float.POSITIVE_INFINITY;
		for (GrindSegment s : near(x, y))
		{
			float len = s.length();
			if (s == exclude || len <= 0f)
			{
				continue;
			}
			float d0 = (float) Math.hypot(s.x0 - x, s.y0 - y);
			float d1 = (float) Math.hypot(s.x1 - x, s.y1 - y);
			boolean fromStart = d0 <= d1;
			float d = fromStart ? d0 : d1;
			// heights compared where the pieces meet, so a sloped rail runs on into a flat one at its high end
			if (d > CONNECT_DISTANCE || Math.abs((fromStart ? s.top0 : s.top1) - top) > CONNECT_TOP)
			{
				continue;
			}
			float sign = fromStart ? 1f : -1f;
			float cos = ((s.x1 - s.x0) * ux + (s.y1 - s.y0) * uy) * sign / len;
			if (cos < minCos)
			{
				continue;
			}
			if (cos > bestCos + 1e-4f || (cos > bestCos - 1e-4f && d < bestDist))
			{
				best = s;
				bestCos = cos;
				bestDist = d;
			}
		}
		return best;
	}

	/** Line direction folded into [0, PI) so both orientations of a segment share a bucket. */
	private static float lineAngle(GrindSegment s)
	{
		float a = s.heading();
		if (a < 0f)
		{
			a += (float) Math.PI;
		}
		if (a >= (float) Math.PI - 1e-4f)
		{
			a -= (float) Math.PI;
		}
		return a;
	}

	/** Snaps float noise (e.g. cos(PI/2)) to an exact 0 so axis-aligned merges keep exact coordinates. */
	private static float axis(float c)
	{
		return Math.abs(c) < 1e-6f ? 0f : c;
	}
}
