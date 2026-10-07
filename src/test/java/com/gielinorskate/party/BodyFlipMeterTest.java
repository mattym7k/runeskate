package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import org.junit.Test;

public class BodyFlipMeterTest
{
	/** SkatePhysics' body flip rate: a whole flip in 0.55 s. */
	private static final float HELD = Angles.TWO_PI / 0.55f;

	@Test
	public void aHeldFlipIsMeasuredAndItsStartAndReleaseAreFlagged()
	{
		BodyFlipMeter m = new BodyFlipMeter();
		m.update(0f, true, 0.02f);
		assertFalse(m.directionChanged());
		m.update(HELD * 0.04f, true, 0.04f);
		assertEquals(HELD, m.rate(), 1e-3f);
		assertTrue("the flip started", m.directionChanged());
		m.update(HELD * 0.06f, true, 0.02f);
		assertFalse(m.directionChanged());
		// released inside the assist window: easing at 3 rad/s is not a held flip
		m.update(HELD * 0.06f + 3f * 0.02f, true, 0.02f);
		assertEquals(3f, m.rate(), 1e-3f);
		assertTrue("the flip was released", m.directionChanged());
		// a frame without a physics step keeps the rate
		m.update(HELD * 0.06f + 3f * 0.02f, true, 0f);
		assertEquals(3f, m.rate(), 1e-3f);
		assertFalse(m.directionChanged());
	}

	@Test
	public void backflipsAreNegativeAndTheGroundIsZero()
	{
		BodyFlipMeter m = new BodyFlipMeter();
		m.update(0f, true, 0.02f);
		m.update(-HELD * 0.02f, true, 0.02f);
		assertEquals(-HELD, m.rate(), 1e-3f);
		assertEquals(-1, m.direction());
		// landing resets the angle to 0: not a rotation
		m.update(0f, false, 0.02f);
		assertEquals(0f, m.rate(), 0f);
		assertEquals(0, m.direction());
		assertTrue(m.directionChanged());
	}
}
