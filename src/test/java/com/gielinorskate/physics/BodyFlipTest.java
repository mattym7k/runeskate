package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import org.junit.Test;

public class BodyFlipTest
{
	static final float TWO_PI = (float) (2 * Math.PI);

	private static float deg(double d)
	{
		return (float) Math.toRadians(d);
	}

	@Test
	public void landingNeedsTheBodyWithin40DegreesOfAWholeRotation()
	{
		assertTrue(BodyFlip.landable(0f));
		assertTrue(BodyFlip.landable(TWO_PI + deg(40)));
		assertFalse(BodyFlip.landable(TWO_PI + deg(41)));
		assertTrue(BodyFlip.landable(TWO_PI - deg(40)));
		assertFalse(BodyFlip.landable(TWO_PI - deg(41)));
		assertTrue(BodyFlip.landable(-TWO_PI - deg(40)));
		assertFalse(BodyFlip.landable(-TWO_PI + deg(41)));
		assertTrue(BodyFlip.landable(deg(40)));
		assertFalse(BodyFlip.landable(deg(41)));
		assertFalse(BodyFlip.landable(deg(180)));
	}

	@Test
	public void rotationsRoundToTheNearestWholeTurn()
	{
		assertEquals(0, BodyFlip.rotations(deg(30)));
		assertEquals(1, BodyFlip.rotations(deg(330)));
		assertEquals(1, BodyFlip.rotations(deg(390)));
		assertEquals(2, BodyFlip.rotations(deg(700)));
		assertEquals(-1, BodyFlip.rotations(deg(-350)));
	}

	@Test
	public void residualIsTheSignedDistanceFromTheNearestWholeTurn()
	{
		assertEquals(deg(20), BodyFlip.residual(TWO_PI + deg(20)), 1e-5f);
		assertEquals(deg(-20), BodyFlip.residual(TWO_PI - deg(20)), 1e-5f);
	}

	@Test
	public void forwardIsAFrontflipBackwardABackflip()
	{
		assertNull(BodyFlip.trick(0));
		assertEquals(Trick.FRONTFLIP, BodyFlip.trick(1));
		assertEquals(Trick.BACKFLIP, BodyFlip.trick(-1));
		assertEquals(Trick.DOUBLE_FRONTFLIP, BodyFlip.trick(2));
		assertEquals(Trick.DOUBLE_BACKFLIP, BodyFlip.trick(-2));
		assertEquals(Trick.DOUBLE_FRONTFLIP, BodyFlip.trick(3));
	}

	@Test
	public void bodyFlipTricksScoreAsSpecified()
	{
		assertEquals(1000, Trick.FRONTFLIP.points);
		assertEquals(1000, Trick.BACKFLIP.points);
		assertEquals(2200, Trick.DOUBLE_FRONTFLIP.points);
		assertEquals(2200, Trick.DOUBLE_BACKFLIP.points);
		assertEquals("Frontflip", Trick.FRONTFLIP.displayName);
		assertEquals("Double Backflip", Trick.DOUBLE_BACKFLIP.displayName);
		assertEquals(TrickKind.FLIP, Trick.FRONTFLIP.kind);
		assertEquals(1f, Trick.FRONTFLIP.bodyFlipTurns, 0f);
		assertEquals(-2f, Trick.DOUBLE_BACKFLIP.bodyFlipTurns, 0f);
		assertEquals(0f, Trick.KICKFLIP.bodyFlipTurns, 0f);
	}
}
