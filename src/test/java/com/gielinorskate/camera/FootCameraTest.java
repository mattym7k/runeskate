package com.gielinorskate.camera;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FootCameraTest
{
	private static float rad(float deg)
	{
		return (float) Math.toRadians(deg);
	}

	@Test
	public void walkingAwayOrDiagonallyTurnsTheCamera()
	{
		assertEquals(rad(10), FootCamera.targetHeading(0f, rad(10), true), 1e-6f);
		assertEquals(rad(-45), FootCamera.targetHeading(0f, rad(-45), true), 1e-6f);
	}

	@Test
	public void walkingTowardOrSidewaysHoldsTheCamera()
	{
		assertEquals(rad(30), FootCamera.targetHeading(rad(30), rad(30 + 180), true), 1e-6f);
		assertEquals(rad(30), FootCamera.targetHeading(rad(30), rad(30 + 90), true), 1e-6f);
	}

	@Test
	public void standingStillHoldsTheCamera()
	{
		assertEquals(rad(30), FootCamera.targetHeading(rad(30), rad(40), false), 1e-6f);
	}
}
