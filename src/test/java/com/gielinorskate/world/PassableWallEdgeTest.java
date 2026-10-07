package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Contact;
import org.junit.Test;

/**
 * A wall object that is ridden through (a vine or bush drawn as a wall, an open door). The game flags a
 * wall on both tiles it separates (the wall's own tile one side, the neighbour the opposite side), so zeroing
 * only the owning tile's wall height left the neighbour's mirrored flag blocking at the neighbour's height.
 */
public class PassableWallEdgeTest
{
	private static final float T = GridCollisionWorld.TILE;

	/** A wall on the west edge of (5, 5), flagged as the game does: WALL_W on (5, 5), WALL_E on (4, 5). */
	private static GridCollisionWorld westWall()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.setTile(5, 5, GridCollisionWorld.WALL_W, 80f);
		w.setTile(4, 5, GridCollisionWorld.WALL_E, 400f);
		return w;
	}

	@Test
	public void theMirroredNeighbourFlagStillBlocksWhenOnlyTheOwnerIsZeroed()
	{
		// what the builder did before: evidence for the bug
		GridCollisionWorld w = westWall();
		w.setWallHeight(5, 5, 0f);
		assertEquals(400f, w.blockerTop(4.9f * T, 5.5f * T, 5.1f * T, 5.5f * T), 1e-3f);
	}

	@Test
	public void openingTheEdgeClearsBothSidesOfIt()
	{
		GridCollisionWorld w = westWall();
		w.openWallEdges(5, 5, GridCollisionWorld.WALL_W);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(4.9f * T, 5.5f * T, 5.1f * T, 5.5f * T), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(5.1f * T, 5.5f * T, 4.9f * T, 5.5f * T), 0f);
		assertFalse("no wall box left to scrape", w.contact(5f * T, 5.5f * T, 12f, 0f, 24f, new Contact()));
		w.rebuildGrinds();
		assertTrue("no rail left either", w.getGrinds().segments().isEmpty());
	}

	@Test
	public void openingOneEdgeKeepsTheTilesOtherWalls()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.setTile(5, 5, GridCollisionWorld.WALL_W | GridCollisionWorld.WALL_N, 300f);
		w.setTile(4, 5, GridCollisionWorld.WALL_E | GridCollisionWorld.WALL_S, 300f);
		w.openWallEdges(5, 5, GridCollisionWorld.WALL_W);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(4.9f * T, 5.5f * T, 5.1f * T, 5.5f * T), 0f);
		// (5, 5)'s north wall and (4, 5)'s south wall are other walls: still there
		assertEquals(300f, w.blockerTop(5.5f * T, 5.9f * T, 5.5f * T, 6.1f * T), 1e-3f);
		assertEquals(300f, w.blockerTop(4.5f * T, 5.1f * T, 4.5f * T, 4.9f * T), 1e-3f);
	}

	@Test
	public void edgesOnTheGridBorderAreIgnoredSafely()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(0, 0, GridCollisionWorld.WALL_W | GridCollisionWorld.WALL_S, 300f);
		w.openWallEdges(0, 0, GridCollisionWorld.WALL_W | GridCollisionWorld.WALL_S);
		assertFalse(w.contact(0.5f, 0.5f * T, 12f, 0f, 24f, new Contact()));
	}
}
