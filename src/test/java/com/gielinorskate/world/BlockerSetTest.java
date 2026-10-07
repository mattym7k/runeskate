package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Contact;
import org.junit.Test;

public class BlockerSetTest
{
	private static final float T = 128f;
	private static final float R = BlockerSet.SKATER_R;
	private static final float S45 = (float) Math.sqrt(0.5);

	/** One axis-aligned 40x40 rock (half 20) centred in tile (2, 2), top 70. */
	private static BlockerSet rock(byte kind, int flags)
	{
		return new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 20, 20, 1, 0, 70, kind, flags).build(5);
	}

	@Test
	public void registersOnlyInTilesItsExpandedBoundsTouch()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		assertEquals(1, s.countAt(2, 2));
		assertEquals(0, s.countAt(1, 2));
		assertEquals(0, s.countAt(3, 3));
		// half 50 + skater radius 12 = 62 from the centre at 2.5 T reaches 2.5 T + 62 = 382 < 384: still one tile
		BlockerSet wide = new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 50, 50, 1, 0, 70, BlockerSet.SOLID, 0).build(5);
		assertEquals(0, wide.countAt(3, 2));
		// half 60 + 12 = 72 > 64: spills into all eight neighbours
		BlockerSet wider = new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 60, 60, 1, 0, 70, BlockerSet.SOLID, 0).build(5);
		assertEquals(1, wider.countAt(3, 3));
		assertEquals(1, wider.countAt(1, 2));
		assertEquals(0, wider.countAt(4, 2));
	}

	@Test
	public void boxesOutsideTheGridAreClipped()
	{
		BlockerSet s = new BlockerSet.Builder().add(-300, 2.5f * T, 20, 20, 1, 0, 70, BlockerSet.SOLID, 0).build(5);
		assertEquals(1, s.count());
		for (int tx = 0; tx < 5; tx++)
		{
			assertEquals(0, s.countAt(tx, 2));
		}
	}

	@Test
	public void sideContactPushesOutAlongTheFaceNormal()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		Contact c = new Contact();
		// 5 units east of the east face (x = 2.5 T + 20)
		assertTrue(s.contact(2.5f * T + 25, 2.5f * T + 3, R, 0, 24, c));
		assertEquals(1f, c.nx, 1e-5f);
		assertEquals(0f, c.ny, 1e-5f);
		assertEquals(R - 5, c.depth, 1e-4f);
		assertEquals(70f, c.top, 0f);
		assertFalse(s.contact(2.5f * T + 33, 2.5f * T, R, 0, 24, c));
	}

	@Test
	public void cornerContactNormalPointsFromTheCorner()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		Contact c = new Contact();
		assertTrue(s.contact(2.5f * T + 20 + 6, 2.5f * T + 20 + 6, R, 0, 24, c));
		assertEquals(S45, c.nx, 1e-5f);
		assertEquals(S45, c.ny, 1e-5f);
		assertEquals(R - 6 * (float) Math.sqrt(2), c.depth, 1e-3f);
	}

	@Test
	public void insideTheBoxPushesOutTheShortestWay()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		Contact c = new Contact();
		// 15 east and 2 north of centre: 5 from the east face, 18 from the north face
		assertTrue(s.contact(2.5f * T + 15, 2.5f * T + 2, R, 0, 24, c));
		assertEquals(1f, c.nx, 0f);
		assertEquals(0f, c.ny, 0f);
		assertEquals(5 + R, c.depth, 1e-4f);
		// 2 west and 17 south: out through the south face
		assertTrue(s.contact(2.5f * T - 2, 2.5f * T - 17, R, 0, 24, c));
		assertEquals(0f, c.nx, 0f);
		assertEquals(-1f, c.ny, 0f);
		assertEquals(3 + R, c.depth, 1e-4f);
	}

	@Test
	public void blockersAtOrBelowAStepAboveTheFeetAreIgnored()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		Contact c = new Contact();
		assertTrue(s.contact(2.5f * T + 25, 2.5f * T, R, 45, 24, c));
		assertFalse("top 70 <= 46 + 24", s.contact(2.5f * T + 25, 2.5f * T, R, 46, 24, c));
	}

	@Test
	public void passBlockersNeverCollide()
	{
		BlockerSet s = rock(BlockerSet.PASS, BlockerSet.LANDABLE);
		Contact c = new Contact();
		assertFalse(s.contact(2.5f * T, 2.5f * T, R, 0, 24, c));
		assertEquals(Float.NEGATIVE_INFINITY, s.landTop(2.5f * T, 2.5f * T), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(2.5f * T - 40, 2.5f * T, 2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void rotatedBoxUsesItsOwnAxes()
	{
		// 100 x 10 plank turned 45 degrees: its long (u) axis runs north-east, v = (-sin, cos) north-west
		BlockerSet s = new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 50, 5, S45, S45, 150, BlockerSet.SOLID, 0).build(5);
		Contact c = new Contact();
		// 40 along the plank then 10 off its north-west side: 5 clear of the face, inside the radius
		float ax = 40 * S45 - 10 * S45;
		float ay = 40 * S45 + 10 * S45;
		assertTrue(s.contact(2.5f * T + ax, 2.5f * T + ay, R, 0, 24, c));
		assertEquals(-S45, c.nx, 1e-4f);
		assertEquals(S45, c.ny, 1e-4f);
		assertEquals(R - 5, c.depth, 1e-3f);
		// 30 due east of the centre would be inside an unrotated 100 x 10 box; turned, it is 21 off the
		// plank's axis (16 clear of its face), out of reach
		assertFalse(s.contact(2.5f * T + 30, 2.5f * T, R, 0, 24, c));
	}

	@Test
	public void deepestOfSeveralContactsWins()
	{
		BlockerSet s = new BlockerSet.Builder()
			.add(2.5f * T, 2.5f * T, 20, 20, 1, 0, 70, BlockerSet.SOLID, 0)
			.add(2.5f * T + 50, 2.5f * T, 20, 20, 1, 0, 300, BlockerSet.SOLID, 0)
			.build(5);
		Contact c = new Contact();
		// 3 from the first box's east face (depth 9), 7 from the second's west face (depth 5)
		assertTrue(s.contact(2.5f * T + 23, 2.5f * T, R, 0, 24, c));
		assertEquals(1f, c.nx, 1e-5f);
		assertEquals(70f, c.top, 0f);
		assertEquals(9f, c.depth, 1e-4f);
	}

	@Test
	public void largeRadiusQueriesFindBoxesInNeighbouringTiles()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		Contact c = new Contact();
		// 45 east of the east face, in tile 3 (x = 385), with radius 50; the rock is registered in tile 2 only
		assertTrue(s.contact(2.5f * T + 65, 2.5f * T, 50, 0, 24, c));
		assertEquals(5f, c.depth, 1e-4f);
	}

	@Test
	public void landTopOnlyInsideALandableBoxNotItsSkaterMargin()
	{
		BlockerSet s = rock(BlockerSet.LOW, BlockerSet.LANDABLE);
		assertEquals(70f, s.landTop(2.5f * T + 19, 2.5f * T - 19), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, s.landTop(2.5f * T + 21, 2.5f * T), 0f);
		BlockerSet notLandable = rock(BlockerSet.SOLID, 0);
		assertEquals(Float.NEGATIVE_INFINITY, notLandable.landTop(2.5f * T, 2.5f * T), 0f);
	}

	@Test
	public void blockTopStopsEnteringSolidBoxesWithTheSkaterRadius()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		float face = 2.5f * T - 20;
		// 13 west of the west face -> 11 west: inside the 12-unit skater radius
		assertEquals(70f, s.blockTop(face - 13, 2.5f * T, face - 11, 2.5f * T), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(face - 30, 2.5f * T, face - 13, 2.5f * T), 0f);
	}

	@Test
	public void blockTopLetsATrappedSkaterMoveOutButNotDeeper()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		float cx = 2.5f * T;
		// 15 east of centre (5 inside the east face): east is out, along the face is no deeper, west is deeper
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(cx + 15, cx, cx + 18, cx), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(cx + 15, cx, cx + 15, cx + 3), 0f);
		assertEquals(70f, s.blockTop(cx + 15, cx, cx + 12, cx), 0f);
	}

	@Test
	public void blockTopCatchesAFastMoveSkippingOverAThinBox()
	{
		// 4 thick (half 2) box: expanded by the radius it is 28 thick; a 40-unit step jumps right over it
		BlockerSet s = new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 60, 2, 1, 0, 300, BlockerSet.SOLID, 0).build(5);
		assertEquals(300f, s.blockTop(2.5f * T, 2.5f * T - 20, 2.5f * T, 2.5f * T + 20), 0f);
	}

	@Test
	public void blockTopLetsAMoveGrazePastACornerWhenItEndsClear()
	{
		// a 45-degree rock (half 20): its east corner 20 sqrt 2 east of the centre. A move north 11.9 east of
		// that corner starts and ends 12.55 from it (clear of the radius) and passes within it only in the
		// middle: grazing the corner, not stepping over the rock
		BlockerSet s = new BlockerSet.Builder().add(2.5f * T, 2.5f * T, 20, 20, S45, S45, 70, BlockerSet.SOLID, 0).build(5);
		float x = 2.5f * T + 20 * (float) Math.sqrt(2) + 11.9f;
		float y = 2.5f * T;
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(x, y - 4, x, y + 4), 0f);
	}

	@Test
	public void blockTopIgnoresLowBoxesWhichBlockThroughTheirRaisedGround()
	{
		BlockerSet s = rock(BlockerSet.LOW, BlockerSet.LANDABLE);
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(2.5f * T - 40, 2.5f * T, 2.5f * T, 2.5f * T), 0f);
		Contact c = new Contact();
		assertTrue("LOW boxes still collide on their sides", s.contact(2.5f * T - 25, 2.5f * T, R, 0, 24, c));
	}

	@Test
	public void cornersFollowTheRotation()
	{
		BlockerSet s = new BlockerSet.Builder().add(100, 200, 10, 5, 0, 1, 50, BlockerSet.SOLID, 0).build(5);
		float[] out = new float[8];
		s.corners(0, out);
		// u = (0, 1) points north, v = (-1, 0) west; corner 0 = -hx u - hy v, corner 1 = +hx u - hy v
		assertEquals(105, out[0], 1e-4f);
		assertEquals(190, out[1], 1e-4f);
		assertEquals(105, out[2], 1e-4f);
		assertEquals(210, out[3], 1e-4f);
	}

	@Test
	public void blockTopUsesTheGivenSmallerRadius()
	{
		BlockerSet s = rock(BlockerSet.SOLID, 0);
		float face = 2.5f * T - 20;
		// ending 10 west of the west face: inside a 12 radius, clear of an 8 one
		assertEquals(70f, s.blockTop(face - 30, 2.5f * T, face - 10, 2.5f * T, 12f), 0f);
		assertEquals(Float.NEGATIVE_INFINITY, s.blockTop(face - 30, 2.5f * T, face - 10, 2.5f * T, 8f), 0f);
		assertEquals(70f, s.blockTop(face - 30, 2.5f * T, face - 7, 2.5f * T, 8f), 0f);
	}
}
