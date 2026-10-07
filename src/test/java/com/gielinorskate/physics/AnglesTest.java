package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AnglesTest
{
	private static final float PI = (float) Math.PI;

	@Test
	public void wrapKeepsAnglesInRange()
	{
		assertEquals(0f, Angles.wrap(2 * PI), 1e-5f);
		assertEquals(-PI / 2, Angles.wrap(3 * PI / 2), 1e-5f);
		assertEquals(PI / 2, Angles.wrap(-3 * PI / 2), 1e-5f);
	}

	@Test
	public void absDiffIsShortestArc()
	{
		assertEquals(0.2f, Angles.absDiff(PI - 0.1f, -PI + 0.1f), 1e-5f);
		assertEquals(PI, Angles.absDiff(0f, PI), 1e-5f);
	}

	@Test
	public void jauConversionMatchesOsrsCompass()
	{
		// OSRS orientation: 0 = south, 512 = west, 1024 = north, 1536 = east
		assertEquals(1024, Angles.toJau(0f));
		assertEquals(1536, Angles.toJau(PI / 2));
		assertEquals(0, Angles.toJau(PI));
		assertEquals(512, Angles.toJau(-PI / 2));
		assertEquals(PI / 2, Angles.fromJau(1536), 1e-3f);
	}
}
