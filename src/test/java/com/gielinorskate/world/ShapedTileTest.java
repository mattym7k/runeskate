package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A FULL-flagged tile with a modelled object on it collides only through that object's box. The builder
 * marked such tiles "modelled" (no fallback box) but never shaped in the world, so every object tile still
 * blocked as a whole 128 x 128 tile: a plant's PASS box sat inside a solid whole-tile box.
 */
public class ShapedTileTest
{
	private static final float T = GridCollisionWorld.TILE;

	private static GridCollisionWorld plantTile()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		// a bush 150 tall flags its tile as blocked whole; its box is ridden through by name
		w.setTile(5, 5, GridCollisionWorld.FULL, 150f);
		w.addObjectBlocker(5.5f * T, 5.5f * T, new float[]{-40, 40, -40, 40}, 0, 150f, true);
		return w;
	}

	@Test
	public void anUnshapedPlantTileStillBlocksWhole()
	{
		// evidence: the state the builder left plant tiles in
		GridCollisionWorld w = plantTile();
		assertEquals(150f, w.blockerTop(4.9f * T, 5.5f * T, 5.1f * T, 5.5f * T), 1e-3f);
	}

	@Test
	public void markingTheTileModelledShapesItSoThePlantIsRiddenThrough()
	{
		GridCollisionWorld w = plantTile();
		boolean[][] shapeable = new boolean[10][10];
		boolean[][] modelled = new boolean[10][10];
		shapeable[5][5] = true;
		assertTrue(SceneCollisionBuilder.markModelled(w, shapeable, modelled, 5, 5, 5, 5));
		assertTrue(modelled[5][5]);
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(4.9f * T, 5.5f * T, 5.5f * T, 5.5f * T), 0f);
		assertEquals("no platform rise either", 0f, w.groundHeight(5.5f * T, 5.5f * T), 1e-3f);
	}

	@Test
	public void aShapedTileWithASolidObjectBlocksOnlyAtTheObject()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.setTile(5, 5, GridCollisionWorld.FULL, 300f);
		w.addObjectBlocker(5.5f * T, 5.5f * T, new float[]{-20, 20, -20, 20}, 0, 300f, false);
		boolean[][] shapeable = new boolean[10][10];
		shapeable[5][5] = true;
		SceneCollisionBuilder.markModelled(w, shapeable, new boolean[10][10], 5, 5, 5, 5);
		// the tile's edge is free, the rock in the middle is not
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(4.9f * T, 5.1f * T, 5.1f * T, 5.1f * T), 0f);
		assertEquals(300f, w.blockerTop(5.5f * T - 40, 5.5f * T, 5.5f * T - 25, 5.5f * T), 1e-3f);
	}
}
