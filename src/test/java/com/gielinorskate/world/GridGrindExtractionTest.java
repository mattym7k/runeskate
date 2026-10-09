package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class GridGrindExtractionTest
{
	private static final float T = GridCollisionWorld.TILE;

	private static List<GrindSegment> grinds(GridCollisionWorld w)
	{
		w.rebuildGrinds();
		return w.getGrinds().segments();
	}

	@Test
	public void newWorldHasAnEmptyGrindMap()
	{
		assertTrue(new GridCollisionWorld(4).getGrinds().segments().isEmpty());
	}

	@Test
	public void lowEastWallBecomesARailAlongThatEdge()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		WorldTests.setCornerHeight(w, 2, 1, 10f);
		WorldTests.setCornerHeight(w, 2, 2, 30f);
		w.setTile(1, 1, GridCollisionWorld.WALL_E, 50f);
		List<GrindSegment> s = grinds(w);
		assertEquals(1, s.size());
		GrindSegment g = s.get(0);
		assertEquals(2 * T, g.x0, 1e-3f);
		assertEquals(2 * T, g.x1, 1e-3f);
		assertEquals(T, Math.min(g.y0, g.y1), 1e-3f);
		assertEquals(2 * T, Math.max(g.y0, g.y1), 1e-3f);
		assertEquals(20f + 50f, g.top, 1e-3f); // mean edge ground (10, 30) + blocker height
	}

	@Test
	public void sameEdgeFlaggedOnBothNeighboursGivesOneRail()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_E, 50f);
		w.setTile(2, 1, GridCollisionWorld.WALL_W, 50f);
		assertEquals(1, grinds(w).size());
	}

	@Test
	public void adjacentFenceTilesMergeIntoOneLongRail()
	{
		GridCollisionWorld w = new GridCollisionWorld(6);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 40f);
		w.setTile(2, 1, GridCollisionWorld.WALL_N, 40f);
		w.setTile(3, 1, GridCollisionWorld.WALL_N, 40f);
		List<GrindSegment> s = grinds(w);
		assertEquals(1, s.size());
		assertEquals(3 * T, s.get(0).length(), 1e-3f);
		assertEquals(2 * T, s.get(0).y0, 1e-3f);
	}

	@Test
	public void tallFenceUpToTheNewMaximumIsARail()
	{
		// GRIND_WALL_MAX raised 100 -> 180 so taller fences can be ground with a charged ollie
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 160f);
		List<GrindSegment> s = grinds(w);
		assertEquals(1, s.size());
		assertEquals(160f, s.get(0).top, 1e-3f);
	}

	@Test
	public void wallsOutsideTheGrindableHeightRangeAreIgnored()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_S, 400f);
		w.setTile(2, 2, GridCollisionWorld.WALL_S, 10f);
		assertTrue(grinds(w).isEmpty());
	}

	@Test
	public void lonePlatformContributesAllFourEdgesAtItsTop()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.FULL, 60f);
		List<GrindSegment> s = grinds(w);
		assertEquals(4, s.size());
		for (GrindSegment g : s)
		{
			assertEquals(60f, g.top, 1e-3f);
			assertEquals(T, g.length(), 1e-3f);
		}
	}

	@Test
	public void platformRowOnlyGrindsItsPerimeter()
	{
		GridCollisionWorld w = new GridCollisionWorld(5);
		w.setTile(1, 1, GridCollisionWorld.FULL, 60f);
		w.setTile(2, 1, GridCollisionWorld.FULL, 60f);
		List<GrindSegment> s = grinds(w);
		// north and south edges merge to 2 tiles long, plus the west and east ends; no inner edge
		assertEquals(4, s.size());
		float total = 0f;
		for (GrindSegment g : s)
		{
			total += g.length();
		}
		assertEquals(6 * T, total, 1e-3f);
	}

	@Test
	public void tallFullBlockersAreNotLedges()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.FULL, 300f);
		assertTrue(grinds(w).isEmpty());
	}
}
