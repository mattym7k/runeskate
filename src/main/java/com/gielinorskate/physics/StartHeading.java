package com.gielinorskate.physics;

/**
 * The heading to start skating with: the character's own facing, unless a wall is right ahead of it (a bank
 * booth, a counter), in which case the most open of the eight compass ways. Pure.
 */
public final class StartHeading
{
	/** A blocker closer than this ahead (1.5 tiles) makes the start turn to a more open way. */
	static final float START_CLEAR = 192f;
	private static final float PROBE_STEP = 16f;

	private StartHeading()
	{
	}

	/**
	 * @param heading the character's facing (radians, 0 = north, clockwise)
	 * @return {@code heading} when at least {@link #START_CLEAR} is free ahead, otherwise the compass way with the
	 * longest free run (up to 6 tiles), the one nearest {@code heading} among equals
	 */
	public static float choose(CollisionWorld world, float x, float y, float heading, float radius, float maxStepUp)
	{
		if (freeRun(world, x, y, heading, radius, maxStepUp, START_CLEAR) >= START_CLEAR)
			return heading;
		float best = heading;
		float bestRun = -1f;
		for (int i = 0; i < 8; i++)
		{
			float dir = Angles.wrap(i * Angles.PI / 4);
			float run = freeRun(world, x, y, dir, radius, maxStepUp, 768f);
			if (run > bestRun + 1e-3f
				|| (Math.abs(run - bestRun) <= 1e-3f && Angles.absDiff(dir, heading) < Angles.absDiff(best, heading)))
			{
				best = dir;
				bestRun = run;
			}
		}
		return best;
	}

	/** Distance a skater can roll from (x, y) along {@code dir} before something blocks it, up to {@code max}. */
	static float freeRun(CollisionWorld world, float x, float y, float dir, float radius, float maxStepUp, float max)
	{
		float dx = (float) Math.sin(dir) * PROBE_STEP;
		float dy = (float) Math.cos(dir) * PROBE_STEP;
		float h = world.groundHeight(x, y);
		float run = 0f;
		while (run < max)
		{
			float nx = x + dx;
			float ny = y + dy;
			if (world.blockerTop(x, y, nx, ny, radius) > h + maxStepUp)
				return run;
			float g = world.groundHeight(nx, ny);
			// a rise steeper than 1.5 per unit moved blocks, as in SkatePhysics
			if (g - h > maxStepUp && g - h > 1.5f * PROBE_STEP)
				return run;
			x = nx;
			y = ny;
			h = g;
			run += PROBE_STEP;
		}
		return max;
	}
}
