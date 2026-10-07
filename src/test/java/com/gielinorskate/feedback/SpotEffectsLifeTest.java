package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpotEffectsLifeTest
{
	private static final float EPS = 1e-4f;

	@Test
	public void classicAnimationLivesForItsSummedFrameLengths()
	{
		// 175 client cycles of 20 ms = 3.5 s: longer than the old 2 s cap, so the whole burst plays
		assertEquals(3.5f, SpotEffects.lifeSeconds(false, 0, new int[]{50, 50, 75}), EPS);
	}

	@Test
	public void mayaAnimationLivesForItsDuration()
	{
		assertEquals(4f, SpotEffects.lifeSeconds(true, 200, null), EPS);
	}

	@Test
	public void unknownLengthFallsBackToTheDefault()
	{
		assertEquals(0.8f, SpotEffects.lifeSeconds(false, 0, null), EPS);
		assertEquals(0.8f, SpotEffects.lifeSeconds(false, 0, new int[0]), EPS);
		assertEquals(0.8f, SpotEffects.lifeSeconds(true, 0, null), EPS);
	}

	@Test
	public void lifeIsBoundedBothWays()
	{
		assertEquals(0.3f, SpotEffects.lifeSeconds(false, 0, new int[]{1}), EPS);
		assertEquals(SpotEffects.MAX_LIFE, SpotEffects.lifeSeconds(false, 0, new int[]{100000}), EPS);
		assertEquals(SpotEffects.MAX_LIFE, SpotEffects.lifeSeconds(true, Integer.MAX_VALUE, null), EPS);
		// garbage negative frame lengths do not shorten it below the floor
		assertEquals(0.3f, SpotEffects.lifeSeconds(false, 0, new int[]{-500}), EPS);
	}
}
