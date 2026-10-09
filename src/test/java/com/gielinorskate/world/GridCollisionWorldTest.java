package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GridCollisionWorldTest
{
	private static final float T = GridCollisionWorld.TILE;

	@Test
	public void flatWorldIsZero()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		assertEquals(0f, w.groundHeight(1.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void edgeDistanceIsUnlimitedUntilTheLoadedAreaIsSet()
	{
		GridCollisionWorld w = new GridCollisionWorld(104);
		assertEquals(Float.POSITIVE_INFINITY, w.edgeDistance(50 * T, 50 * T), 0f);
	}

	@Test
	public void edgeDistanceMeasuresToTheNearestSideOfTheLoadedArea()
	{
		// the client blocks tile 0 and tiles 99+ of a 104-tile scene: free tiles 1..98
		GridCollisionWorld w = new GridCollisionWorld(104);
		w.setLoadedTiles(1, 99);
		assertEquals(49 * T, w.edgeDistance(50 * T, 50 * T), 1e-3f);
		assertEquals(2 * T, w.edgeDistance(3 * T, 50 * T), 1e-3f);
		assertEquals(T, w.edgeDistance(50 * T, 98 * T), 1e-3f);
		assertTrue(w.edgeDistance(0.5f * T, 50 * T) < 0f);
	}

	@Test
	public void groundIsBilinearBetweenCorners()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		WorldTests.setCornerHeight(w, 1, 0, 100f);
		WorldTests.setCornerHeight(w, 1, 1, 100f);
		// tile (0,0): west corners 0, east corners 100 -> centre 50
		assertEquals(50f, w.groundHeight(0.5f * T, 0.5f * T), 1e-3f);
		assertEquals(100f, w.groundHeight(1.0f * T, 0.5f * T), 1e-3f);
	}

	@Test
	public void eastWallBlocksCrossingAndReportsTop()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_E, 300f);
		float top = w.blockerTop(1.9f * T, 1.5f * T, 2.05f * T, 1.5f * T);
		assertEquals(300f, top, 1e-3f);
	}

	@Test
	public void neighbourWestWallBlocksSameEdge()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(2, 1, GridCollisionWorld.WALL_W, 80f);
		assertEquals(80f, w.blockerTop(1.9f * T, 1.5f * T, 2.05f * T, 1.5f * T), 1e-3f);
	}

	@Test
	public void movingInsideATileIsNeverBlocked()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_N | GridCollisionWorld.WALL_E, 300f);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(1.2f * T, 1.2f * T, 1.4f * T, 1.4f * T), 0f);
	}

	@Test
	public void northWallUsesPositiveY()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 200f);
		assertEquals(200f, w.blockerTop(1.5f * T, 1.95f * T, 1.5f * T, 2.05f * T), 1e-3f);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(1.5f * T, 1.05f * T, 1.5f * T, 0.95f * T), 0f);
	}

	@Test
	public void tallFullTileBlocks()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(2, 2, GridCollisionWorld.FULL, 400f);
		assertEquals(400f, w.blockerTop(1.9f * T, 2.5f * T, 2.05f * T, 2.5f * T), 1e-3f);
		assertEquals(0f, w.groundHeight(2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void lowFullTileIsAPlatform()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(2, 2, GridCollisionWorld.FULL, 60f);
		assertEquals(60f, w.groundHeight(2.5f * T, 2.5f * T), 1e-3f);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(1.9f * T, 2.5f * T, 2.05f * T, 2.5f * T), 0f);
	}

	@Test
	public void diagonalMoveCannotCutThroughCornerWalls()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_E | GridCollisionWorld.WALL_N, 300f);
		float top = w.blockerTop(1.95f * T, 1.95f * T, 2.05f * T, 2.05f * T);
		assertEquals(300f, top, 1e-3f);
	}

	@Test
	public void diagonalMoveSlipsPastSingleWall()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_E, 300f);
		float top = w.blockerTop(1.95f * T, 1.95f * T, 2.05f * T, 2.05f * T);
		assertEquals(Float.NEGATIVE_INFINITY, top, 0f);
	}

	@Test
	public void diagonalMoveIgnoresUnrelatedWallOnTarget()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(2, 2, GridCollisionWorld.WALL_W, 300f);
		float top = w.blockerTop(1.95f * T, 1.95f * T, 2.05f * T, 2.05f * T);
		assertEquals(Float.NEGATIVE_INFINITY, top, 0f);
	}

	@Test
	public void leavingTheWorldIsInfinitelyBlocked()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		assertTrue(Float.isInfinite(w.blockerTop(0.05f * T, 1.5f * T, -0.05f * T, 1.5f * T)));
		assertTrue(w.blockerTop(3.95f * T, 1.5f * T, 4.05f * T, 1.5f * T) > 0);
	}

	@Test
	public void skaterInsideASolidTileCanMoveOut()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(2, 2, GridCollisionWorld.FULL, 400f);
		// moving within the solid tile and out of it is allowed; moving in is still blocked
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(2.4f * T, 2.5f * T, 2.6f * T, 2.5f * T), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(2.95f * T, 2.5f * T, 3.05f * T, 2.5f * T), 0f);
		assertEquals(400f, w.blockerTop(1.95f * T, 2.5f * T, 2.05f * T, 2.5f * T), 1e-3f);
	}
}
