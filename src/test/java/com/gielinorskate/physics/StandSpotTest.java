package com.gielinorskate.physics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Where a knocked-off board ends up, and where a skater can stand. */
public class StandSpotTest
{
	private static final float TILE = 128f;
	private static final float R = 12f;

	@Test
	public void aBoardRestingSomewhereReachableStaysThere()
	{
		float[] at = StandSpot.boardRest(TestWorlds.flat(), 300f, 400f, 0f, 0f, 0f, 0f, R);
		assertArrayEquals(new float[]{300f, 400f, 0f}, at, 0f);
	}

	@Test
	public void aBoardOutsideTheLoadedAreaComesBackWithinTwoTilesOfTheSkater()
	{
		CollisionWorld w = TestWorlds.edgeAtY(100f);
		float[] at = StandSpot.boardRest(w, 0f, 105f, 0f, 0f, 0f, 0f, R);
		assertTrue("inside: " + at[1], w.edgeDistance(at[0], at[1]) > R);
		assertTrue(Math.hypot(at[0], at[1]) <= 2 * TILE + 1e-3);
	}

	@Test
	public void aBoardOnAHighLedgeComesDownNearTheSkater()
	{
		// a 300-high platform from y = 200: the board landed up there, the skater lies below
		CollisionWorld w = TestWorlds.platformAtY(200f, 300f);
		float[] at = StandSpot.boardRest(w, 0f, 250f, 300f, 0f, 0f, 0f, R);
		assertEquals(0f, at[2], 0f);
		assertTrue(at[1] < 200f);
		assertTrue(Math.hypot(at[0], at[1]) <= 2 * TILE + 1e-3);
	}

	@Test
	public void withNowhereBetterTheBoardLiesAtTheSkatersFeet()
	{
		// nothing within two tiles is inside the loaded area
		CollisionWorld w = TestWorlds.edgeAtY(-5000f);
		float[] at = StandSpot.boardRest(w, 0f, 100f, 0f, 0f, 0f, 0f, R);
		assertArrayEquals(new float[]{0f, 0f, 0f}, at, 0f);
	}

	@Test
	public void aStandableSpotIsKept()
	{
		assertArrayEquals(new float[]{10f, 20f, 0f}, StandSpot.nearest(TestWorlds.flat(), 10f, 20f, R, 2 * TILE), 0f);
	}

	@Test
	public void outsideTheLoadedAreaTheNearestStandableSpotIsFound()
	{
		CollisionWorld w = TestWorlds.edgeAtY(100f);
		float[] at = StandSpot.nearest(w, 0f, 110f, R, 2 * TILE);
		assertTrue(w.edgeDistance(at[0], at[1]) > R);
		assertTrue(Math.hypot(at[0], at[1] - 110f) <= 2 * TILE);
	}

	@Test
	public void nothingStandableNearbyKeepsThePoint()
	{
		CollisionWorld w = TestWorlds.edgeAtY(-5000f);
		assertArrayEquals(new float[]{0f, 0f, 0f}, StandSpot.nearest(w, 0f, 0f, R, 2 * TILE), 0f);
	}
}
