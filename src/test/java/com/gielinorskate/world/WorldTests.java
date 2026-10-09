package com.gielinorskate.world;

/** Test-only access to world internals: shared terrain corners, extra grinds and box index details. */
public final class WorldTests
{
	private WorldTests()
	{
	}

	/** Sets grid corner (cx, cy) for every tile that shares it (up to four). */
	public static void setCornerHeight(GridCollisionWorld w, int cx, int cy, float h)
	{
		for (int k = 0; k < 4; k++)
		{
			// corner k (SW, SE, NW, NE) of the tile k & 1 west and k >> 1 south of it
			int tx = cx - (k & 1);
			int ty = cy - (k >> 1);
			if (tx >= 0 && ty >= 0 && tx < w.size() && ty < w.size())
			{
				float[] c = w.tileCorners[tx][ty].clone();
				c[k] = h;
				w.setTileCorners(tx, ty, c[0], c[1], c[2], c[3]);
			}
		}
	}

	/**
	 * Adds a grind segment that does not come from the collision flags. It is included, and merged with the
	 * flag-derived segments, by every later rebuildGrinds. Never affects collision.
	 */
	public static void addGrindSegment(GridCollisionWorld w, GrindSegment s)
	{
		w.added.add(s);
	}

	/** Number of boxes registered in tile (tx, ty) (0 off the grid). */
	public static int countAt(BlockerSet s, int tx, int ty)
	{
		return tx < 0 || ty < 0 || tx >= s.size || ty >= s.size ? 0 : s.tileBoxes[tx * s.size + ty].length;
	}

	public static byte kind(BlockerSet s, int i)
	{
		return (byte) s.boxes[i][7];
	}

	public static int flags(BlockerSet s, int i)
	{
		return (int) s.boxes[i][8];
	}
}
