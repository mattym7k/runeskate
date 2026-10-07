package com.gielinorskate.physics;

/** Simple analytic worlds for physics tests. */
final class TestWorlds
{
	private TestWorlds()
	{
	}

	static CollisionWorld flat()
	{
		return of((x, y) -> 0f, null);
	}

	/** Ground descending northwards: height = -grade * y (a negative grade climbs northwards). */
	static CollisionWorld downhillNorth(float grade)
	{
		return of((x, y) -> -grade * y, null);
	}

	/** Flat ground with a blocker line at y = wallY of the given top height. */
	static CollisionWorld wallAtY(float wallY, float top)
	{
		return of((x, y) -> 0f, (y0, y1) -> (y0 < wallY && y1 >= wallY) || (y0 >= wallY && y1 < wallY) ? top : Float.NEGATIVE_INFINITY);
	}

	/**
	 * Ground descending northwards at {@code grade} with bumps of {@code amp} every 2 * PI * {@code scale}
	 * units on top: height = -grade * y + amp * sin(y / scale).
	 */
	static CollisionWorld bumpyDownhillNorth(float grade, float amp, float scale)
	{
		return of((x, y) -> -grade * y + amp * (float) Math.sin(y / scale), null);
	}

	/** Ground at height `low` for y < edgeY and `high` beyond it (terrain itself steps). */
	static CollisionWorld stepAtY(float edgeY, float low, float high)
	{
		return of((x, y) -> y < edgeY ? low : high, null);
	}

	/**
	 * Flat terrain at 0 with a platform (a curb, crate or ledge) of the given top for y >= edgeY: the
	 * skateable ground steps up there, the terrain does not.
	 */
	static CollisionWorld platformAtY(float edgeY, float top)
	{
		return of((x, y) -> y >= edgeY ? top : 0f, (x, y) -> 0f, null);
	}

	/**
	 * Flat ground ending at the edge of the loaded area at y = edgeY: an endless wall there, as the client's
	 * blocked outer tiles are, and {@link CollisionWorld#edgeDistance} measuring to it.
	 */
	static CollisionWorld edgeAtY(float edgeY)
	{
		CollisionWorld w = wallAtY(edgeY, Float.POSITIVE_INFINITY);
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return 0f;
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return 0f;
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return w.blockerTop(x0, y0, x1, y1);
			}

			@Override
			public float edgeDistance(float x, float y)
			{
				return edgeY - y;
			}
		};
	}

	interface Ground
	{
		float at(float x, float y);
	}

	interface WallY
	{
		float top(float y0, float y1);
	}

	private static CollisionWorld of(Ground ground, WallY wall)
	{
		return of(ground, ground, wall);
	}

	private static CollisionWorld of(Ground ground, Ground terrain, WallY wall)
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return ground.at(x, y);
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return terrain.at(x, y);
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return wall == null ? Float.NEGATIVE_INFINITY : wall.top(y0, y1);
			}
		};
	}
}
