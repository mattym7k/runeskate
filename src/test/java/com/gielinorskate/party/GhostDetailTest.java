package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GhostDetailTest
{
	@Test
	public void theGroundDistanceUsesTheFocalPointsZNotItsHeight()
	{
		// the client's focal point: X east, Y the height (down-negative), Z north; a ghost under the focus is 0 away
		float focalX = 6656f;
		float focalHeight = -350f;
		float focalZ = 6400f;
		assertEquals(0f, GhostVisibility.focusDistance(6656f, 6400f, focalX, focalZ), 1e-3f);
		assertEquals(128f, GhostVisibility.focusDistance(6656f, 6528f, focalX, focalZ), 1e-3f);
		// measured against the height instead, a ghost right at the focus would be ~53 tiles off: never full
		assertFalse(GhostVisibility.full(0, GhostVisibility.focusDistance(6656f, 6400f, focalX, focalHeight)));
	}

	@Test
	public void theNearestFourWithin32TilesAreFull()
	{
		assertTrue(GhostVisibility.full(0, GhostVisibility.focusDistance(100f, 100f, 100f, 100f)));
		assertTrue(GhostVisibility.full(3, 32 * 128f));
		assertFalse(GhostVisibility.full(4, 0f));
		assertFalse(GhostVisibility.full(0, 32 * 128f + 1f));
	}
}
