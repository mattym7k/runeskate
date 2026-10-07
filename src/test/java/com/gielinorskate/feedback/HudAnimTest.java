package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HudAnimTest
{
	@Test
	public void popStartsBigAndSettlesToOneAfter120ms()
	{
		assertEquals(1.35f, HudAnim.popScale(0f), 1e-4f);
		assertEquals(1f, HudAnim.popScale(0.12f), 0f);
		assertEquals(1f, HudAnim.popScale(5f), 0f);
		assertEquals("never drawn before its time", 1f, HudAnim.popScale(-1f), 0f);
		// ease-out-back: most of the way there early, with a small dip below 1 before it settles
		float mid = HudAnim.popScale(0.06f);
		assertTrue("mid " + mid, mid < 1.1f);
		float min = Float.MAX_VALUE;
		for (float t = 0f; t < 0.12f; t += 0.001f)
		{
			min = Math.min(min, HudAnim.popScale(t));
		}
		assertTrue("overshoot stays small: " + min, min > 0.95f && min < 1f);
	}

	@Test
	public void easeOutBackHitsBothEnds()
	{
		assertEquals(0f, HudAnim.easeOutBack(0f), 1e-5f);
		assertEquals(1f, HudAnim.easeOutBack(1f), 1e-5f);
	}

	@Test
	public void ringPulsesFortyPercentThickerFor150ms()
	{
		assertEquals(1.4f, HudAnim.ringPulse(0f), 1e-5f);
		assertEquals(1.2f, HudAnim.ringPulse(0.075f), 1e-4f);
		assertEquals(1f, HudAnim.ringPulse(0.15f), 0f);
		assertEquals(1f, HudAnim.ringPulse(-Float.MAX_VALUE), 0f);
		assertEquals(1f, HudAnim.ringFlash(0f), 0f);
		assertEquals(0f, HudAnim.ringFlash(0.2f), 0f);
	}

	@Test
	public void landedScoreCountsUpOver300ms()
	{
		assertEquals(0, HudAnim.countUp(1000, 0f));
		// ease-out cubic: at half time 1 - 0.5^3 = 0.875 of the value
		assertEquals(875, HudAnim.countUp(1000, 0.15f));
		assertEquals(1000, HudAnim.countUp(1000, 0.3f));
		assertEquals(1000, HudAnim.countUp(1000, Float.MAX_VALUE));
		int last = -1;
		for (float t = 0f; t <= 0.31f; t += 0.01f)
		{
			int v = HudAnim.countUp(1000, t);
			assertTrue("never counts down", v >= last);
			last = v;
		}
	}

	@Test
	public void bailShakeStaysWithinThreePixelsAndStopsAfter200ms()
	{
		boolean moved = false;
		for (float t = 0f; t < 0.2f; t += 0.005f)
		{
			assertTrue(Math.abs(HudAnim.shakeX(t)) <= 3);
			assertTrue(Math.abs(HudAnim.shakeY(t)) <= 3);
			moved |= HudAnim.shakeX(t) != 0 || HudAnim.shakeY(t) != 0;
		}
		assertTrue(moved);
		assertEquals(0, HudAnim.shakeX(0.2f));
		assertEquals(0, HudAnim.shakeY(0.25f));
		assertEquals(0, HudAnim.shakeX(-1f));
	}

	@Test
	public void xpDropRisesAndFadesOverItsLife()
	{
		assertEquals(0f, HudAnim.xpDropRise(0f), 0f);
		assertEquals(1f, HudAnim.xpDropRise(1.2f), 0f);
		// ease-out: 1 - 0.5^2 = 0.75 of the way at half time
		assertEquals(0.75f, HudAnim.xpDropRise(0.6f), 1e-4f);
		assertEquals(1f, HudAnim.xpDropAlpha(0.5f), 0f);
		assertEquals(0.5f, HudAnim.xpDropAlpha(1.0f), 1e-4f);
		assertEquals(0f, HudAnim.xpDropAlpha(1.2f), 0f);
	}

	@Test
	public void calloutHoldsThenFades()
	{
		assertEquals(1f, HudAnim.calloutAlpha(0f), 0f);
		assertEquals(1f, HudAnim.calloutAlpha(1.2f), 0f);
		assertEquals(0.5f, HudAnim.calloutAlpha(1.4f), 1e-4f);
		assertEquals(0f, HudAnim.calloutAlpha(1.6f), 0f);
		assertEquals(1.35f, HudAnim.calloutScale(0f), 1e-4f);
		assertEquals(1f, HudAnim.calloutScale(0.18f), 0f);
	}

	@Test
	public void bannerHoldsThenFades()
	{
		assertEquals(0f, HudAnim.bannerAlpha(-1f), 0f);
		assertEquals(1f, HudAnim.bannerAlpha(3f), 0f);
		assertEquals(0.5f, HudAnim.bannerAlpha(3.6f), 1e-4f);
		assertEquals(0f, HudAnim.bannerAlpha(4f), 0f);
		assertEquals(1f, HudAnim.goalFlashAlpha(2f), 0f);
		assertEquals(0f, HudAnim.goalFlashAlpha(3f), 0f);
	}
}
