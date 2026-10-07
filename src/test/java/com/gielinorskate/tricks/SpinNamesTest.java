package com.gielinorskate.tricks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpinNamesTest
{
	private static float deg(double d)
	{
		return (float) Math.toRadians(d);
	}

	@Test
	public void rotationsRoundInHalfTurnsWithThirtyDegreesOfSlack()
	{
		assertEquals(0, SpinNames.halfTurns(0f));
		assertEquals(0, SpinNames.halfTurns(deg(149)));
		assertEquals(1, SpinNames.halfTurns(deg(150)));
		assertEquals(1, SpinNames.halfTurns(deg(180)));
		assertEquals(1, SpinNames.halfTurns(deg(260)));
		assertEquals(1, SpinNames.halfTurns(deg(329)));
		assertEquals(2, SpinNames.halfTurns(deg(330)));
		assertEquals(2, SpinNames.halfTurns(deg(360)));
		assertEquals(3, SpinNames.halfTurns(deg(510)));
		assertEquals(3, SpinNames.halfTurns(deg(540)));
	}

	@Test
	public void theSignFollowsTheSpinDirection()
	{
		// heading is clockwise from above, so a negative spin is counter-clockwise
		assertEquals(-1, SpinNames.halfTurns(deg(-150)));
		assertEquals(-2, SpinNames.halfTurns(deg(-360)));
		assertEquals(-3, SpinNames.halfTurns(deg(-540)));
		assertEquals(0, SpinNames.halfTurns(deg(-149)));
	}

	@Test
	public void regularStanceClockwiseIsBacksideCounterClockwiseIsFrontside()
	{
		assertEquals("BS 180", SpinNames.label(1));
		assertEquals("FS 180", SpinNames.label(-1));
		assertEquals("BS 360", SpinNames.label(2));
		assertEquals("FS 540", SpinNames.label(-3));
		assertEquals("", SpinNames.label(0));
	}

	@Test
	public void spinsPrefixTheTrickName()
	{
		assertEquals("FS 180 Kickflip", SpinNames.name("Kickflip", -1));
		assertEquals("BS 360 Ollie", SpinNames.name("Ollie", 2));
		assertEquals("FS 540", SpinNames.name("", -3));
		assertEquals("Ollie", SpinNames.name("Ollie", 0));
	}

	@Test
	public void eachHalfTurnIsWorth150()
	{
		assertEquals(150, SpinNames.POINTS_PER_HALF_TURN);
		assertEquals(450, SpinNames.bonus(-3));
		assertEquals(0, SpinNames.bonus(0));
	}
}
