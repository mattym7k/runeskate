package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Contact;
import org.junit.Test;

/** Tight object boxes and wall-edge boxes inside {@link GridCollisionWorld}. */
public class GridBlockersTest
{
	private static final float T = GridCollisionWorld.TILE;
	private static final float NEG = Float.NEGATIVE_INFINITY;

	/** 6x6 world, terrain at `ground` everywhere. */
	private static GridCollisionWorld world(float ground)
	{
		GridCollisionWorld w = new GridCollisionWorld(6);
		for (int x = 0; x <= 6; x++)
		{
			for (int y = 0; y <= 6; y++)
			{
				WorldTests.setCornerHeight(w, x, y, ground);
			}
		}
		return w;
	}

	/** A grind segment passing within 1 unit of (x, y), or null. */
	private static GrindSegment near(GridCollisionWorld w, float x, float y)
	{
		for (GrindSegment s : w.getGrinds().segments())
		{
			float dx = s.x1 - s.x0;
			float dy = s.y1 - s.y0;
			float t = Math.max(0f, Math.min(1f, ((x - s.x0) * dx + (y - s.y0) * dy) / (dx * dx + dy * dy)));
			if (Math.hypot(s.x0 + t * dx - x, s.y0 + t * dy - y) < 1f)
			{
				return s;
			}
		}
		return null;
	}

	/** A FULL tile (2, 2) holding one model whose slice is a centred square of the given half size. */
	private static GridCollisionWorld oneObject(float half, float height, boolean pass)
	{
		GridCollisionWorld w = world(0);
		w.setTile(2, 2, GridCollisionWorld.FULL, height);
		w.markShaped(2, 2);
		w.addObjectBlocker(2.5f * T, 2.5f * T, new float[]{-half, half, -half, half}, 0, height, pass, true);
		return w;
	}

	@Test
	public void aRockBlocksOnlyAroundItselfNotTheWholeTile()
	{
		GridCollisionWorld w = oneObject(32, 200, false);
		// crossing into the tile far from the 64x64 rock: free (the whole tile used to block)
		assertEquals(NEG, w.blockerTop(1.95f * T, 2.1f * T, 2.05f * T, 2.1f * T), 0f);
		// rolling into the rock's west face (2.5 T - 32), within the 12 skater radius
		float face = 2.5f * T - 32;
		assertEquals(200f, w.blockerTop(face - 14, 2.5f * T, face - 10, 2.5f * T), 0f);
		Contact c = new Contact();
		assertTrue(w.contact(face - 10, 2.5f * T, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(-1f, c.nx, 1e-5f);
		assertEquals(2f, c.depth, 1e-4f);
	}

	@Test
	public void aRotatedRockTurnsWithItsModel()
	{
		GridCollisionWorld w = world(0);
		w.setTile(2, 2, GridCollisionWorld.FULL, 200);
		w.markShaped(2, 2);
		// 120 x 40 slab turned an eighth (256 JAU): it runs from south-east to north-west
		w.addObjectBlocker(2.5f * T, 2.5f * T, new float[]{-60, 60, -20, 20}, 256, 200, false, true);
		Contact c = new Contact();
		float d = 40 * (float) Math.sqrt(0.5);
		assertTrue("on the slab's axis, 40 from the centre", w.contact(2.5f * T - d, 2.5f * T + d, 1, 0, 24, c));
		assertFalse("40 along the unrotated x axis is off the slab", w.contact(2.5f * T + 40, 2.5f * T, 1, 0, 24, c));
	}

	@Test
	public void aCrateIsLandableOnlyInsideItsOwnBox()
	{
		GridCollisionWorld w = world(20);
		w.setTile(2, 2, GridCollisionWorld.FULL, 100);
		w.markShaped(2, 2);
		assertEquals(BlockerSet.LOW, w.addObjectBlocker(2.5f * T, 2.5f * T, new float[]{-40, 40, -40, 40}, 0, 100, false, true));
		assertEquals(120f, w.groundHeight(2.5f * T + 39, 2.5f * T), 1e-3f);
		assertEquals("just outside the crate is plain ground (was the whole tile)", 20f, w.groundHeight(2.5f * T + 41, 2.5f * T), 1e-3f);
		assertEquals(20f, w.groundHeight(2.1f * T, 2.1f * T), 1e-3f);
	}

	@Test
	public void lowObjectsAreStillHitOnTheirSides()
	{
		GridCollisionWorld w = oneObject(40, 100, false);
		Contact c = new Contact();
		assertTrue(w.contact(2.5f * T, 2.5f * T - 45, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(-1f, c.ny, 1e-5f);
		assertFalse("airborne above the top", w.contact(2.5f * T, 2.5f * T - 45, BlockerSet.SKATER_R, 80, 24, c));
	}

	@Test
	public void lowObjectEdgesAreLedges()
	{
		GridCollisionWorld w = oneObject(40, 100, false);
		w.rebuildGrinds();
		GrindSegment s = near(w, 2.5f * T, 2.5f * T - 40);
		assertTrue(s != null);
		assertEquals(100f, s.top, 1e-3f);
		assertEquals(80f, s.length(), 1e-3f);
	}

	@Test
	public void objectsUpToAStepTallAreIgnored()
	{
		GridCollisionWorld w = oneObject(40, 24, false);
		assertEquals(0f, w.groundHeight(2.5f * T, 2.5f * T), 0f);
		assertFalse(w.contact(2.5f * T, 2.5f * T, BlockerSet.SKATER_R, 0, 0, new Contact()));
		assertEquals(NEG, w.blockerTop(2.5f * T - 60, 2.5f * T, 2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void plantsAreRiddenThrough()
	{
		GridCollisionWorld w = oneObject(40, 90, true);
		assertEquals(BlockerSet.PASS, WorldTests.kind(w.getBlockers(), 0));
		assertEquals(0f, w.groundHeight(2.5f * T, 2.5f * T), 0f);
		assertFalse(w.contact(2.5f * T, 2.5f * T, BlockerSet.SKATER_R, 0, 24, new Contact()));
		assertEquals(NEG, w.blockerTop(2.5f * T - 60, 2.5f * T, 2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void aModelLessFullTileFallsBackToA96Box()
	{
		GridCollisionWorld w = world(0);
		w.setTile(2, 2, GridCollisionWorld.FULL, 32);
		w.addFallbackBlocker(2, 2);
		assertEquals(NEG, w.blockerTop(1.95f * T, 2.05f * T, 2.05f * T, 2.05f * T), 0f);
		float face = 2.5f * T - 48;
		assertEquals(400f, w.blockerTop(face - 13, 2.5f * T, face - 11, 2.5f * T), 0f);
		assertEquals("no platform rise", 0f, w.groundHeight(2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void unshapedFullTilesKeepWholeTileCollisionInContact()
	{
		GridCollisionWorld w = world(0);
		w.setTile(2, 2, GridCollisionWorld.FULL, 400);
		Contact c = new Contact();
		assertTrue(w.contact(2 * T - 5, 2.1f * T, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(-1f, c.nx, 1e-5f);
		assertEquals(7f, c.depth, 1e-4f);
		assertEquals(400f, c.top, 0f);
	}

	@Test
	public void wallEdgesAreThinBoxesInContact()
	{
		GridCollisionWorld w = world(0);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 60);
		Contact c = new Contact();
		// 10 south of the north edge (y = 2 T); the wall is 2 thick on this side
		assertTrue(w.contact(1.5f * T, 2 * T - 10, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(0f, c.nx, 1e-5f);
		assertEquals(-1f, c.ny, 1e-5f);
		assertEquals(4f, c.depth, 1e-4f);
		assertEquals(60f, c.top, 0f);
		assertFalse("hopping over: 60 <= 40 + 24", w.contact(1.5f * T, 2 * T - 10, BlockerSet.SKATER_R, 40, 24, c));
		// the east wall runs north-south
		w.setTile(3, 3, GridCollisionWorld.WALL_E, 300);
		assertTrue(w.contact(4 * T + 8, 3.5f * T, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(1f, c.nx, 1e-5f);
		assertEquals(300f, c.top, 0f);
	}

	@Test
	public void wallHeightIsKeptApartFromTheTallestObjectOnTheTile()
	{
		GridCollisionWorld w = world(0);
		// a fence (60) on the north edge of a tile that also has a lamp post (300)
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 300);
		w.setWallHeight(1, 1, 60);
		assertEquals(60f, w.blockerTop(1.5f * T, 1.95f * T, 1.5f * T, 2.05f * T), 1e-3f);
		w.rebuildGrinds();
		GrindSegment s = near(w, 1.5f * T, 2 * T);
		assertTrue(s != null);
		assertEquals(60f, s.top, 1e-3f);
	}

	@Test
	public void aSkaterInsideATallBoxCanOnlyLeaveTheShortestWay()
	{
		GridCollisionWorld w = oneObject(40, 300, false);
		Contact c = new Contact();
		// 30 east of centre: 10 inside the east face
		assertTrue(w.contact(2.5f * T + 30, 2.5f * T + 5, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(1f, c.nx, 0f);
		assertEquals(10f + BlockerSet.SKATER_R, c.depth, 1e-4f);
		assertEquals(NEG, w.blockerTop(2.5f * T + 30, 2.5f * T, 2.5f * T + 34, 2.5f * T), 0f);
		assertEquals(300f, w.blockerTop(2.5f * T + 30, 2.5f * T, 2.5f * T + 26, 2.5f * T), 0f);
	}

	@Test
	public void aGrindableBenchIsInflatedBlocksAndIsLandable()
	{
		GridCollisionWorld w = world(0);
		// 200 x 8 bench, 100 tall, on a tile the collision flags leave open
		w.addGrindableBlocker(2.5f * T, 2.5f * T, new float[]{-100, 100, -4, 4}, 0, 100);
		assertEquals(BlockerSet.LOW, WorldTests.kind(w.getBlockers(), 0));
		assertEquals("thin side inflated to 12 either side", 100f, w.groundHeight(2.5f * T, 2.5f * T + 11), 1e-3f);
		assertEquals(0f, w.groundHeight(2.5f * T, 2.5f * T + 13), 1e-3f);
		Contact c = new Contact();
		assertTrue(w.contact(2.5f * T, 2.5f * T - 20, BlockerSet.SKATER_R, 0, 24, c));
		assertEquals(-1f, c.ny, 1e-5f);
		// a 150-tall counter is SOLID (blocks in blockerTop) but still landable
		GridCollisionWorld w2 = world(0);
		w2.addGrindableBlocker(2.5f * T, 2.5f * T, new float[]{-100, 100, -4, 4}, 0, 150);
		assertEquals(BlockerSet.SOLID, WorldTests.kind(w2.getBlockers(), 0));
		assertEquals(150f, w2.groundHeight(2.5f * T, 2.5f * T), 1e-3f);
		assertEquals(150f, w2.blockerTop(2.5f * T, 2.5f * T - 30, 2.5f * T, 2.5f * T - 22), 0f);
	}
}
