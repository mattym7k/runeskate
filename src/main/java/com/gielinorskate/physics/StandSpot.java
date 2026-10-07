package com.gielinorskate.physics;

/**
 * Where a knocked-off skater can stand and where its board may come to rest: inside the loaded area, out of any
 * blocker, and (for the board) where the skater can walk to. Pure. Points are {x, y, h} (h the ground there).
 */
public final class StandSpot
{
	/** A board resting further above or below the skater than this is out of reach (a roof, a ledge, a pit). */
	static final float REACH_HEIGHT = 96f;
	/** A misplaced board is put back within this of the skater. */
	public static final float BOARD_FALLBACK_RADIUS = 2 * 128f;
	/** A foot steps up this much (FootPhysics.STEP_UP). */
	static final float STEP_UP = FootPhysics.STEP_UP;
	/** Rings searched every this many units, with this many points on each. */
	static final float RING_STEP = 8f;
	static final int RING_POINTS = 16;

	private StandSpot()
	{
	}

	/** Inside the loaded area and clear of every blocker for a body of radius {@code r}. */
	public static boolean standable(CollisionWorld w, float x, float y, float r)
	{
		float g = w.groundHeight(x, y);
		if (Float.isNaN(g) || Float.isInfinite(g) || !(w.edgeDistance(x, y) > r))
		{
			return false;
		}
		return !w.contact(x, y, r, g, STEP_UP, new Contact());
	}

	/** (x, y) when standable, else the nearest standable point within {@code maxDist}; (x, y) when there is none. */
	public static float[] nearest(CollisionWorld w, float x, float y, float r, float maxDist)
	{
		if (standable(w, x, y, r))
		{
			return new float[]{x, y, w.groundHeight(x, y)};
		}
		for (float d = RING_STEP; d <= maxDist; d += RING_STEP)
		{
			for (int i = 0; i < RING_POINTS; i++)
			{
				double a = 2 * Math.PI * i / RING_POINTS;
				float cx = x + (float) Math.sin(a) * d;
				float cy = y + (float) Math.cos(a) * d;
				if (standable(w, cx, cy, r))
				{
					return new float[]{cx, cy, w.groundHeight(cx, cy)};
				}
			}
		}
		return new float[]{x, y, ground(w, x, y)};
	}

	/**
	 * Where the board that came to rest at (bx, by, bh) lies for the skater at (sx, sy, sh): there, unless that is
	 * outside the loaded area, in a blocker or out of reach above or below; then the reachable standable spot within
	 * {@link #BOARD_FALLBACK_RADIUS} of the skater nearest to where it came to rest; with none, at the skater's feet.
	 */
	public static float[] boardRest(CollisionWorld w, float bx, float by, float bh, float sx, float sy, float sh,
		float r)
	{
		if (standable(w, bx, by, r) && Math.abs(bh - sh) <= REACH_HEIGHT)
		{
			return new float[]{bx, by, bh};
		}
		float[] best = null;
		double bestDist = Double.MAX_VALUE;
		for (float d = RING_STEP; d <= BOARD_FALLBACK_RADIUS; d += RING_STEP)
		{
			for (int i = 0; i < RING_POINTS; i++)
			{
				double a = 2 * Math.PI * i / RING_POINTS;
				float cx = sx + (float) Math.sin(a) * d;
				float cy = sy + (float) Math.cos(a) * d;
				double dist = Math.hypot(cx - bx, cy - by);
				if (dist >= bestDist || !standable(w, cx, cy, r))
				{
					continue;
				}
				float g = w.groundHeight(cx, cy);
				if (Math.abs(g - sh) > REACH_HEIGHT || w.blockerTop(sx, sy, cx, cy, r) > sh + STEP_UP)
				{
					continue;
				}
				best = new float[]{cx, cy, g};
				bestDist = dist;
			}
		}
		return best != null ? best : new float[]{sx, sy, sh};
	}

	private static float ground(CollisionWorld w, float x, float y)
	{
		float g = w.groundHeight(x, y);
		return Float.isNaN(g) || Float.isInfinite(g) ? 0f : g;
	}
}
