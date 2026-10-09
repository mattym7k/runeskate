package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class GrindMapTest
{
	private static final float NORTH = 0f;
	private static final float EAST = (float) (Math.PI / 2);

	private static float deg(double d)
	{
		return (float) Math.toRadians(d);
	}

	@Test
	public void segmentLengthAndClampedProjection()
	{
		GrindSegment s = new GrindSegment(0, 0, 0, 200, 50);
		assertEquals(200f, s.length(), 1e-4f);
		assertEquals(0.25f, s.project(10, 50), 1e-5f);
		assertEquals(0f, s.project(0, -80), 0f);
		assertEquals(1f, s.project(5, 900), 0f);
	}

	@Test
	public void mergeJoinsCollinearTouchingSegmentsWithEqualTop()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(128, 0, 128, 128, 50));
		m.add(new GrindSegment(128, 256, 128, 128, 53)); // reversed direction, top within 4
		m.add(new GrindSegment(128, 256, 128, 384, 50));
		m.merge();
		assertEquals(1, m.segments().size());
		GrindSegment s = m.segments().get(0);
		assertEquals(384f, s.length(), 1e-3f);
		assertEquals(128f, s.x0, 1e-3f);
		assertEquals(128f, s.x1, 1e-3f);
	}

	@Test
	public void mergeDedupesIdenticalSegments()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 128, 128, 128, 40));
		m.add(new GrindSegment(0, 128, 128, 128, 40));
		m.merge();
		assertEquals(1, m.segments().size());
		assertEquals(128f, m.segments().get(0).length(), 1e-3f);
	}

	@Test
	public void mergeKeepsDifferentTopsGapsAndParallelLinesApart()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 128, 0, 40));
		m.add(new GrindSegment(128, 0, 256, 0, 60)); // touching but 20 higher
		m.add(new GrindSegment(300, 0, 400, 0, 40)); // collinear but a gap
		m.add(new GrindSegment(0, 128, 128, 128, 40)); // parallel, other line
		m.add(new GrindSegment(128, 0, 128, 128, 40)); // touching, perpendicular
		m.merge();
		assertEquals(5, m.segments().size());
	}

	@Test
	public void nearestFindsSegmentWithinSnapDistanceAndHeightWindow()
	{
		GrindMap m = new GrindMap();
		GrindSegment s = new GrindSegment(0, 0, 0, 400, 50);
		m.add(s);
		GrindMap.Hit hit = m.nearest(20, 100, 60, NORTH, null, GrindMap.SNAP_DISTANCE);
		assertNotNull(hit);
		assertSame(s, hit.segment);
		assertEquals(0.25f, hit.t, 1e-5f);

		assertNotNull("44 sideways", m.nearest(44, 100, 60, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNull("too far sideways", m.nearest(45, 100, 60, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNotNull("lowest h", m.nearest(0, 100, 34, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNull("below the window", m.nearest(0, 100, 33, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNotNull("highest h", m.nearest(0, 100, 98, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNull("above the window", m.nearest(0, 100, 99, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNull("past the end", m.nearest(0, 445, 50, NORTH, null, GrindMap.SNAP_DISTANCE));
	}

	/** MAX_APPROACH is 88 degrees (review P3; was 80). */
	@Test
	public void nearestRequiresTravelWithinEightyEightDegreesOfTheLineEitherWay()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 0, 400, 50));
		assertNotNull(m.nearest(0, 100, 50, deg(87), null, GrindMap.SNAP_DISTANCE));
		assertNotNull(m.nearest(0, 100, 50, deg(180 + 87), null, GrindMap.SNAP_DISTANCE));
		assertNotNull(m.nearest(0, 100, 50, deg(180), null, GrindMap.SNAP_DISTANCE));
		assertNull(m.nearest(0, 100, 50, deg(89), null, GrindMap.SNAP_DISTANCE));
		assertNull(m.nearest(0, 100, 50, EAST, null, GrindMap.SNAP_DISTANCE));
		assertNull(m.nearest(0, 100, 50, deg(-91), null, GrindMap.SNAP_DISTANCE));
	}

	@Test
	public void nearestPicksTheClosestCandidate()
	{
		GrindMap m = new GrindMap();
		GrindSegment far = new GrindSegment(20, 0, 20, 400, 50);
		GrindSegment near = new GrindSegment(-5, 0, -5, 400, 50);
		m.add(far);
		m.add(near);
		assertSame(near, m.nearest(0, 100, 50, NORTH, null, GrindMap.SNAP_DISTANCE).segment);
	}

	@Test
	public void emptyMapFindsNothing()
	{
		assertNull(new GrindMap().nearest(0, 0, 0, NORTH, null, GrindMap.SNAP_DISTANCE));
	}

	@Test
	public void connectedAtFindsTheNextPieceAroundAGentleBend()
	{
		GrindMap m = new GrindMap();
		GrindSegment a = new GrindSegment(0, 0, 0, 200, 30);
		GrindSegment b = new GrindSegment(10, 210, 110, 310, 40); // 45 degrees, a 14-unit gap, 10 higher
		m.add(a);
		m.add(b);
		assertSame(b, m.connectedAt(0, 200, 30, 0, 1, a));
		// travelling the other way along a, from its (0, 0) end: nothing there
		assertNull(m.connectedAt(0, 0, 30, 0, -1, a));
	}

	@Test
	public void connectedAtAcceptsEitherEndOfTheNextPiece()
	{
		GrindMap m = new GrindMap();
		GrindSegment a = new GrindSegment(0, 0, 0, 200, 30);
		GrindSegment b = new GrindSegment(110, 310, 10, 210, 30); // stored far end first
		m.add(a);
		m.add(b);
		assertSame(b, m.connectedAt(0, 200, 30, 0, 1, a));
	}

	@Test
	public void connectedAtLimitsBendGapTopAndExcludesTheCurrentPiece()
	{
		GrindSegment a = new GrindSegment(0, 0, 0, 200, 30);
		float c74 = (float) Math.cos(Math.toRadians(74));
		float s74 = (float) Math.sin(Math.toRadians(74));
		float c76 = (float) Math.cos(Math.toRadians(76));
		float s76 = (float) Math.sin(Math.toRadians(76));

		assertNotNull("74 degree bend", only(new GrindSegment(0, 200, 100 * s74, 200 + 100 * c74, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNull("76 degree bend", only(new GrindSegment(0, 200, 100 * s76, 200 + 100 * c76, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNull("90 degree corner", only(new GrindSegment(0, 200, 200, 200, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNull("doubling back", only(new GrindSegment(0, 200, 0, 50, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNotNull("24 gap", only(new GrindSegment(0, 224, 0, 400, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNull("25 gap", only(new GrindSegment(0, 225, 0, 400, 30)).connectedAt(0, 200, 30, 0, 1, a));
		assertNotNull("24 higher", only(new GrindSegment(0, 200, 0, 400, 54)).connectedAt(0, 200, 30, 0, 1, a));
		assertNull("25 higher", only(new GrindSegment(0, 200, 0, 400, 55)).connectedAt(0, 200, 30, 0, 1, a));
		GrindMap self = only(a);
		assertNull("itself", self.connectedAt(0, 200, 30, 0, 1, a));
	}

	@Test
	public void connectedAtPrefersTheStraighterContinuation()
	{
		GrindMap m = new GrindMap();
		GrindSegment a = new GrindSegment(0, 0, 0, 200, 30);
		GrindSegment bend = new GrindSegment(0, 200, 100, 300, 30);
		GrindSegment straight = new GrindSegment(0, 200, 0, 400, 30);
		m.add(a);
		m.add(bend);
		m.add(straight);
		assertSame(straight, m.connectedAt(0, 200, 30, 0, 1, a));
	}

	private static GrindMap only(GrindSegment s)
	{
		GrindMap m = new GrindMap();
		m.add(s);
		return m;
	}

	/** Brute force over every segment, as nearest() worked before the per-tile index. */
	private static GrindSegment bruteNearest(java.util.List<GrindSegment> segs, float x, float y, float h, float travel)
	{
		GrindSegment best = null;
		float bestD = Float.POSITIVE_INFINITY;
		for (GrindSegment s : segs)
		{
			float t = s.project(x, y);
			float d = (float) Math.hypot(x - s.xAt(t), y - s.yAt(t));
			if (h < s.top - GrindMap.BELOW_TOP || h > s.top + GrindMap.ABOVE_TOP || d > GrindMap.SNAP_DISTANCE
				|| d >= bestD || s.lineAngle(travel) > GrindMap.MAX_APPROACH + 1e-4f || !GrindMap.enoughAhead(s, t, travel))
			{
				continue;
			}
			best = s;
			bestD = d;
		}
		return best;
	}

	@Test
	public void tileIndexFindsTheSameSegmentsAsAFullScan()
	{
		java.util.Random r = new java.util.Random(7);
		GrindMap m = new GrindMap();
		java.util.List<GrindSegment> all = new java.util.ArrayList<>();
		for (int i = 0; i < 300; i++)
		{
			float x0 = r.nextFloat() * 3000 - 1000;
			float y0 = r.nextFloat() * 3000 - 1000;
			float a = r.nextFloat() * 6.28f;
			float len = 20 + r.nextFloat() * 600;
			GrindSegment s = new GrindSegment(x0, y0, x0 + len * (float) Math.sin(a), y0 + len * (float) Math.cos(a), 50);
			all.add(s);
			m.add(s);
		}
		for (int i = 0; i < 5000; i++)
		{
			float x = r.nextFloat() * 3200 - 1100;
			float y = r.nextFloat() * 3200 - 1100;
			float travel = r.nextFloat() * 6.28f;
			GrindMap.Hit hit = m.nearest(x, y, 50, travel, null, GrindMap.SNAP_DISTANCE);
			assertSame(bruteNearest(all, x, y, 50, travel), hit == null ? null : hit.segment);
		}
	}

	@Test
	public void segmentsAddedAfterAQueryAreFound()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 0, 400, 50));
		assertNotNull(m.nearest(10, 100, 50, NORTH, null, GrindMap.SNAP_DISTANCE));
		assertNull(m.nearest(-5000, 100, 50, NORTH, null, GrindMap.SNAP_DISTANCE));
		GrindSegment late = new GrindSegment(-5000, 0, -5000, 400, 50);
		m.add(late);
		assertSame(late, m.nearest(-5000, 100, 50, NORTH, null, GrindMap.SNAP_DISTANCE).segment);
	}
}
