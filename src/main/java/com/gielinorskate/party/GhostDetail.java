package com.gielinorskate.party;

/**
 * The per-ghost body budget: only the nearest {@link #MAX_FULL} ghosts within {@link #FULL_RANGE} get the full body
 * (the procedural pose on top of the OSRS animation, about 0.3 ms a frame for a 5,000-vertex character); the rest
 * play the animation only. Pure.
 */
final class GhostDetail
{
	static final int MAX_FULL = 4;
	/** Local units: 32 tiles, about where a body's lean and crouch stop being readable. */
	static final float FULL_RANGE = 32 * 128f;

	private GhostDetail()
	{
	}

	/**
	 * A ghost's ground distance from the camera's focal point. The client's focal point keeps the scene's y in its
	 * Z ({@code getCameraFocalPointZ}); its Y is the height, so measuring against Y put every ghost ~50 tiles away.
	 */
	static float focusDistance(float x, float y, float focalX, float focalZ)
	{
		return (float) Math.hypot(x - focalX, y - focalZ);
	}

	/** The ghost {@code rank}-th nearest (0 the nearest) at {@code distance} gets the full body. */
	static boolean full(int rank, float distance)
	{
		return rank < MAX_FULL && distance <= FULL_RANGE;
	}

	/** {@code rank[i]}: how many of the first {@code n} distances come before {@code d[i]} (nearest first, ties in order). */
	static void rank(float[] d, int n, int[] rank)
	{
		for (int i = 0; i < n; i++)
		{
			int r = 0;
			for (int j = 0; j < n; j++)
			{
				if (d[j] < d[i] || (d[j] == d[i] && j < i))
				{
					r++;
				}
			}
			rank[i] = r;
		}
	}
}
