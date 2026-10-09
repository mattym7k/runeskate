package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

/** Sloped rails (review extra 2): a top at each end, interpolated along the segment. */
public class SlopedRailTest
{
	@Test
	public void topIsInterpolatedAlongTheSegment()
	{
		GrindSegment s = new GrindSegment(0, 0, 0, 400, 30, 130);
		assertEquals(30f, s.topAt(0f), 1e-4f);
		assertEquals(80f, s.topAt(0.5f), 1e-4f);
		assertEquals(130f, s.topAt(1f), 1e-4f);
		assertEquals(80f, s.top, 1e-4f);
		GrindSegment flat = new GrindSegment(0, 0, 0, 400, 50);
		assertEquals(50f, flat.top0, 0f);
		assertEquals(50f, flat.top1, 0f);
		assertEquals(50f, flat.topAt(0.3f), 0f);
	}

	@Test
	public void lockWindowUsesTheHeightUnderTheSkater()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 0, 400, 30, 230));
		// at y = 350 the rail is 205 high: the mean top 130 would put 200 in the window, the local top does too,
		// but at y = 50 (top 55) h 200 is far above it
		assertNotNull(m.nearest(10, 350, 200, 0f, null, GrindMap.SNAP_DISTANCE));
		assertNull(m.nearest(10, 50, 200, 0f, null, GrindMap.SNAP_DISTANCE));
	}

	@Test
	public void aGrindRunsOnFromTheHighEndOfASlopedRail()
	{
		GrindMap m = new GrindMap();
		GrindSegment ramp = new GrindSegment(0, 0, 0, 400, 30, 130);
		GrindSegment flat = new GrindSegment(0, 400, 0, 800, 130);
		m.add(ramp);
		m.add(flat);
		// the ramp's mean top (80) is 50 below the flat one, but at the joint both are 130
		assertSame(flat, m.connectedAt(0, 400, ramp.topAt(1f), 0, 1, ramp));
	}

	@Test
	public void mergeJoinsCollinearPiecesOnTheSameIncline()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 0, 128, 0, 64));
		m.add(new GrindSegment(0, 256, 0, 128, 128, 64)); // reversed, same incline
		m.add(new GrindSegment(0, 256, 0, 384, 128, 128)); // flattens out: kept apart
		m.merge();
		assertEquals(2, m.segments().size());
		GrindSegment ramp = m.segments().get(0).length() > 200 ? m.segments().get(0) : m.segments().get(1);
		assertEquals(256f, ramp.length(), 1e-3f);
		assertEquals(0f, Math.min(ramp.top0, ramp.top1), 1e-3f);
		assertEquals(128f, Math.max(ramp.top0, ramp.top1), 1e-3f);
	}

	@Test
	public void wallEdgeRailsFollowTheGroundAtEachEnd()
	{
		GridCollisionWorld w = new GridCollisionWorld(4);
		WorldTests.setCornerHeight(w, 1, 1, 0);
		WorldTests.setCornerHeight(w, 2, 1, 64);
		w.addEdgeGrind(1, 1, GridCollisionWorld.WALL_S, 40);
		w.rebuildGrinds();
		GrindSegment s = w.getGrinds().segments().get(0);
		float west = s.x0 < s.x1 ? s.top0 : s.top1;
		float east = s.x0 < s.x1 ? s.top1 : s.top0;
		assertEquals(40f, west, 1e-3f);
		assertEquals(104f, east, 1e-3f);
	}

	@Test
	public void objectRailsUseTheTerrainUnderEachEnd()
	{
		// a 256-long handrail (model x -128..128) on ground rising 0.25 per unit eastward
		GridCollisionWorld w = new GridCollisionWorld(6);
		for (int cx = 0; cx <= 6; cx++)
		{
			for (int cy = 0; cy <= 6; cy++)
			{
				WorldTests.setCornerHeight(w, cx, cy, cx * 32f);
			}
		}
		w.addObjectGrind(384, 384, new float[]{-128, 128, -4, 4}, 0, 50);
		w.rebuildGrinds();
		GrindSegment s = w.getGrinds().segments().get(0);
		float west = s.x0 < s.x1 ? s.top0 : s.top1;
		float east = s.x0 < s.x1 ? s.top1 : s.top0;
		assertEquals(256 * 0.25f + 50, west, 1e-2f);
		assertEquals(512 * 0.25f + 50, east, 1e-2f);
	}
}
