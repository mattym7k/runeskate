package com.gielinorskate.world;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ClutterRulesTest
{
	@Test
	public void classifyByNameHeightAndFootprint()
	{
		assertEquals(BlockerSet.PASS, ClutterRules.classify(true, 300, 200));
		assertEquals("height 24 is stepped over", BlockerSet.PASS, ClutterRules.classify(false, 24, 100));
		assertEquals("thin slice", BlockerSet.PASS, ClutterRules.classify(false, 200, 29));
		assertEquals(BlockerSet.LOW, ClutterRules.classify(false, 25, 40));
		assertEquals(BlockerSet.LOW, ClutterRules.classify(false, 120, 110));
		assertEquals("too narrow to stand on", BlockerSet.SOLID, ClutterRules.classify(false, 100, 39));
		assertEquals(BlockerSet.SOLID, ClutterRules.classify(false, 121, 110));
	}

	@Test
	public void grindablesAreLowUpToThePlatformHeightAndAlwaysLandable()
	{
		assertEquals(BlockerSet.LOW, ClutterRules.classifyGrindable(16));
		assertEquals(BlockerSet.LOW, ClutterRules.classifyGrindable(120));
		assertEquals(BlockerSet.SOLID, ClutterRules.classifyGrindable(150));
	}

	@Test
	public void sliceKeepsOnlyVerticesAtSkaterHeight()
	{
		// tree: trunk 50 wide from the ground to 90 up, canopy 200 wide from 150 to 300 up (model y is negative-up)
		float[] xs = {-25, 25, -25, 25, -100, 100, -100, 100};
		float[] ys = {0, 0, -90, -90, -150, -150, -300, -300};
		float[] zs = {-25, 25, -25, 25, -100, 100, -100, 100};
		assertArrayEquals(new float[]{-25, 25, -25, 25}, ClutterRules.sliceExtents(xs, ys, zs, 8, 90), 0f);
		// a floating sign with nothing below 90 has no slice
		assertNull(ClutterRules.sliceExtents(new float[]{-5, 5}, new float[]{-100, -120}, new float[]{0, 0}, 2, 90));
		// the vertex count limits the arrays
		assertArrayEquals(new float[]{-25, 25, -25, 25}, ClutterRules.sliceExtents(xs, ys, zs, 4, 90), 0f);
	}

	@Test
	public void crateSliceIsItsWholeFootprint()
	{
		float[] xs = {-55, 55, 55, -55, -55, 55, 55, -55};
		float[] ys = {0, 0, 0, 0, -100, -100, -100, -100};
		float[] zs = {-55, -55, 55, 55, -55, -55, 55, 55};
		float[] ext = ClutterRules.sliceExtents(xs, ys, zs, 8, 90);
		assertArrayEquals(new float[]{-55, 55, -55, 55}, ext, 0f);
		assertEquals(110f, ClutterRules.minSide(ext), 0f);
	}

	@Test
	public void boxIsTheRotatedExtentsLikeTheRailShape()
	{
		// 100 x 20 model offset 10 along +x, at origin (1000, 2000), unrotated
		float[] b = ClutterRules.box(1000, 2000, new float[]{-40, 60, -10, 10}, 0, 0);
		assertArrayEquals(new float[]{1010, 2000, 50, 10, 1, 0}, b, 1e-4f);
		// orientation 512 (a quarter turn): model x maps to local -y (x' = x cos + z sin, y' = z cos - x sin)
		float[] r = ClutterRules.box(1000, 2000, new float[]{-40, 60, -10, 10}, 512, 0);
		assertEquals(1000f, r[0], 1e-3f);
		assertEquals(1990f, r[1], 1e-3f);
		assertEquals(50f, r[2], 0f);
		assertEquals(10f, r[3], 0f);
		assertEquals(0f, r[4], 1e-5f);
		assertEquals(-1f, r[5], 1e-5f);
	}

	@Test
	public void boxInflatesThinSidesToTheMinimumHalf()
	{
		float[] b = ClutterRules.box(0, 0, new float[]{-60, 60, -2, 2}, 0, 12);
		assertEquals(60f, b[2], 0f);
		assertEquals(12f, b[3], 0f);
	}
}
