package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.List;
import org.junit.Test;

public class GridAddedGrindsTest
{
	private static final float T = GridCollisionWorld.TILE;

	@Test
	public void addedSegmentAppearsInTheGrindMap()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.addGrindSegment(new GrindSegment(0, T, 2 * T, T, 40f));
		w.rebuildGrinds();
		List<GrindSegment> s = w.getGrinds().segments();
		assertEquals(1, s.size());
		assertEquals(40f, s.get(0).top, 1e-3f);
		assertEquals(2 * T, s.get(0).length(), 1e-3f);
	}

	@Test
	public void addedSegmentDuplicatingAFlagRailCollapses()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 50f);
		w.addEdgeGrind(1, 1, GridCollisionWorld.WALL_N, 50f);
		w.addEdgeGrind(1, 2, GridCollisionWorld.WALL_S, 50f); // same edge seen from the other tile
		w.rebuildGrinds();
		assertEquals(1, w.getGrinds().segments().size());
	}

	@Test
	public void addedSegmentExtendingAFlagRailMerges()
	{
		GridCollisionWorld w = new GridCollisionWorld(6);
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 40f);
		w.addEdgeGrind(2, 1, GridCollisionWorld.WALL_N, 40f);
		w.rebuildGrinds();
		List<GrindSegment> s = w.getGrinds().segments();
		assertEquals(1, s.size());
		assertEquals(2 * T, s.get(0).length(), 1e-3f);
	}

	@Test
	public void edgeGrindSitsOnTheEdgeGround()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setCornerHeight(1, 2, 10f);
		w.setCornerHeight(2, 2, 30f);
		w.addEdgeGrind(1, 1, GridCollisionWorld.WALL_N, 40f);
		w.rebuildGrinds();
		assertEquals(20f + 40f, w.getGrinds().segments().get(0).top, 1e-3f);
	}

	@Test
	public void oneTileFootprintGivesFourEdges()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.addFootprintGrind(1, 1, 1, 1, 40f);
		w.rebuildGrinds();
		List<GrindSegment> s = w.getGrinds().segments();
		assertEquals(4, s.size());
		for (GrindSegment g : s)
		{
			assertEquals(T, g.length(), 1e-3f);
			assertEquals(40f, g.top, 1e-3f);
		}
	}

	@Test
	public void longFootprintMergesIntoItsPerimeter()
	{
		GridCollisionWorld w = new GridCollisionWorld(8);
		w.addFootprintGrind(1, 2, 4, 2, 60f); // a 4x1 fence/bench run
		w.rebuildGrinds();
		List<GrindSegment> s = w.getGrinds().segments();
		assertEquals(4, s.size());
		float total = 0f;
		for (GrindSegment g : s)
		{
			total += g.length();
		}
		assertEquals(10 * T, total, 1e-3f); // 4 + 4 + 1 + 1 tiles
	}

	@Test
	public void footprintIsClippedToTheGrid()
	{
		GridCollisionWorld w = new GridCollisionWorld(2);
		w.addFootprintGrind(-1, 0, 5, 0, 40f);
		w.rebuildGrinds();
		for (GrindSegment g : w.getGrinds().segments())
		{
			assertEquals(true, g.x0 >= 0 && g.x1 <= 2 * T && g.y1 <= 2 * T);
		}
	}

	@Test
	public void addedGrindsSurviveARebuild()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.addEdgeGrind(1, 1, GridCollisionWorld.WALL_E, 40f);
		w.rebuildGrinds();
		w.rebuildGrinds();
		assertEquals(1, w.getGrinds().segments().size());
	}

	@Test
	public void benchFootprintIsCaughtFromAnOllieButNotWhileRollingPast()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.addFootprintGrind(1, 1, 1, 1, 40f); // bench, 40 high on flat ground
		w.rebuildGrinds();
		GrindMap m = w.getGrinds();
		// dropping onto its north edge from an ollie, travelling east along it
		assertEquals(true, m.nearest(1.5f * T, 2 * T + 10f, 35f, (float) (Math.PI / 2)) != null);
		// rolling along the ground beside it: 0 is below top - BELOW_TOP = 24, so no snap
		assertEquals(null, m.nearest(1.5f * T, 2 * T + 10f, 0f, (float) (Math.PI / 2)));
	}

	@Test
	public void wallOrientationBitsMapToSides()
	{
		assertEquals(GridCollisionWorld.WALL_W, SceneCollisionBuilder.wallSides(1));
		assertEquals(GridCollisionWorld.WALL_N, SceneCollisionBuilder.wallSides(2));
		assertEquals(GridCollisionWorld.WALL_E, SceneCollisionBuilder.wallSides(4));
		assertEquals(GridCollisionWorld.WALL_S, SceneCollisionBuilder.wallSides(8));
		assertEquals(GridCollisionWorld.WALL_W | GridCollisionWorld.WALL_N, SceneCollisionBuilder.wallSides(1 | 2));
		assertEquals(0, SceneCollisionBuilder.wallSides(16)); // diagonals are skipped
		assertEquals(0, SceneCollisionBuilder.wallSides(128));
	}

	@Test
	public void objectGrindIsACentrelineAtTerrainUnderItsCentrePlusHeight()
	{
		// a 128 x 10 fence centred in tile (1, 1) on a 20-high corner-raised tile that is also a FULL
		// platform: the rail sits at terrain + model height, not on top of the platform
		GridCollisionWorld w = new GridCollisionWorld(4);
		w.setCornerHeight(1, 1, 20f);
		w.setCornerHeight(2, 1, 20f);
		w.setCornerHeight(1, 2, 20f);
		w.setCornerHeight(2, 2, 20f);
		w.setTile(1, 1, GridCollisionWorld.FULL, 50f);
		w.addObjectGrind(192, 192, new float[]{-64, 64, -5, 5}, 0, 50f);
		w.rebuildGrinds();
		GrindSegment rail = null;
		for (GrindSegment g : w.getGrinds().segments())
		{
			if (Math.abs(g.y0 - 192) < 1e-3f && Math.abs(g.y1 - 192) < 1e-3f)
			{
				rail = g;
			}
		}
		assertNotNull(rail);
		assertEquals(70f, rail.top, 1e-3f);
		assertEquals(T, rail.length(), 1e-3f);
		assertEquals(20f, w.terrainHeight(192, 192), 1e-3f);
		assertEquals(70f, w.groundHeight(192, 192), 1e-3f);
	}
}
