package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BailReasonTest
{
	@Test
	public void landingReasonsInTheOrderPhysicsChecksThem()
	{
		assertNull(BailReason.forLanding(false, false, false));
		assertEquals(BailReason.SIDEWAYS, BailReason.forLanding(true, true, true));
		assertEquals(BailReason.FLIP_NOT_CAUGHT, BailReason.forLanding(false, true, true));
		assertEquals(BailReason.BODY_FLIP, BailReason.forLanding(false, false, true));
	}

	@Test
	public void wordingForTheHud()
	{
		assertEquals("Landed sideways - finish the spin", BailReason.SIDEWAYS.text);
		assertEquals("Board still flipping - jump higher", BailReason.FLIP_NOT_CAUGHT.text);
		assertEquals("Hit a wall", BailReason.WALL.text);
	}
}
