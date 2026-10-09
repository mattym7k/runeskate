package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class ObjectRailShapeTest
{
	private static final float EPS = 1e-3f;

	/** True when s runs between (ax, ay) and (bx, by), either way round. */
	private static boolean runs(GrindSegment s, float ax, float ay, float bx, float by)
	{
		boolean fwd = near(s.x0, ax) && near(s.y0, ay) && near(s.x1, bx) && near(s.y1, by);
		boolean rev = near(s.x0, bx) && near(s.y0, by) && near(s.x1, ax) && near(s.y1, ay);
		return fwd || rev;
	}

	private static boolean near(float a, float b)
	{
		return Math.abs(a - b) < EPS;
	}

	@Test
	public void extentsOfVertices()
	{
		float[] e = ObjectShapes.sliceExtents(new float[]{-64, 10, 64, 99}, new float[4], new float[]{-5, 5, 0, 1000}, 3,
			Float.POSITIVE_INFINITY);
		assertEquals(-64f, e[0], 0f);
		assertEquals(64f, e[1], 0f);
		assertEquals(-5f, e[2], 0f);
		assertEquals(5f, e[3], 0f);
		assertNull(ObjectShapes.sliceExtents(new float[0], new float[0], new float[0], 0, Float.POSITIVE_INFINITY));
	}

	@Test
	public void longFenceAtOrientationZeroIsOneRailAlongXThroughItsCentre()
	{
		float[] ext = {-64, 64, -5, 5};
		List<GrindSegment> s = ObjectShapes.segments(192, 320, ext, 0);
		assertEquals(1, s.size());
		assertTrue(s.get(0).toString(), runs(s.get(0), 128, 320, 256, 320));
		assertEquals(128f, s.get(0).length(), EPS);
	}

	@Test
	public void longFenceAtOrientation512RunsAlongY()
	{
		float[] ext = {-64, 64, -5, 5};
		List<GrindSegment> s = ObjectShapes.segments(192, 320, ext, 512);
		assertEquals(1, s.size());
		assertTrue(s.get(0).toString(), runs(s.get(0), 192, 256, 192, 384));
	}

	@Test
	public void longAxisAlongModelZAndAnOffCentreModel()
	{
		// a fence 10 x 100 along model z, centred at model (20, 30)
		float[] ext = {15, 25, -20, 80};
		List<GrindSegment> s = ObjectShapes.segments(0, 0, ext, 0);
		assertEquals(1, s.size());
		assertTrue(s.get(0).toString(), runs(s.get(0), 20, -20, 20, 80));
		float[] c = ObjectShapes.centre(0, 0, ext, 0);
		assertEquals(20f, c[0], EPS);
		assertEquals(30f, c[1], EPS);
		// rotated 90 degrees: model (x, z) -> local (z, -x)
		c = ObjectShapes.centre(100, 100, ext, 512);
		assertEquals(130f, c[0], EPS);
		assertEquals(80f, c[1], EPS);
	}

	@Test
	public void diagonalFenceRailFollowsTheRotation()
	{
		float[] ext = {-90, 90, -4, 4};
		List<GrindSegment> s = ObjectShapes.segments(0, 0, ext, 256);
		assertEquals(1, s.size());
		float d = 90f * (float) Math.sqrt(0.5);
		// 45 degrees: model (x, 0) -> local (x cos, -x sin)
		assertTrue(s.get(0).toString(), runs(s.get(0), -d, d, d, -d));
	}

	@Test
	public void squareCrateGivesTheFourEdgesOfItsOwnBounds()
	{
		float[] ext = {-20, 20, -20, 20};
		List<GrindSegment> s = ObjectShapes.segments(64, 64, ext, 0);
		assertEquals(4, s.size());
		boolean south = false;
		boolean north = false;
		boolean west = false;
		boolean east = false;
		for (GrindSegment g : s)
		{
			assertEquals(40f, g.length(), EPS);
			south |= runs(g, 44, 44, 84, 44);
			north |= runs(g, 44, 84, 84, 84);
			west |= runs(g, 44, 44, 44, 84);
			east |= runs(g, 84, 44, 84, 84);
		}
		assertTrue(south && north && west && east);
	}

	@Test
	public void squareIsWithinTwentyFivePercent()
	{
		// 100 x 76: shorter is 76% of longer, square-ish -> perimeter
		assertEquals(4, ObjectShapes.segments(0, 0, new float[]{-50, 50, -38, 38}, 0).size());
		// 100 x 74: a rail
		assertEquals(1, ObjectShapes.segments(0, 0, new float[]{-50, 50, -37, 37}, 0).size());
	}

	@Test
	public void degenerateModelGivesNothing()
	{
		assertTrue(ObjectShapes.segments(0, 0, new float[]{0, 0, 0, 0}, 0).isEmpty());
	}
}
