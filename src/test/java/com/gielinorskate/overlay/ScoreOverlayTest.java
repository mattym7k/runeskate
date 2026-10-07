package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The HUD XP-drop text: it must show Skating XP, not the raw combo score. */
public class ScoreOverlayTest
{
	@Test
	public void showsSkatingXpNotComboPoints()
	{
		// a 12,340-point combo grants 1,234 Skating XP (SkateLevels.COMBO_VALUE_PER_XP = 10)
		assertEquals("+1,234", ScoreOverlay.xpDropText(12_340));
	}

	@Test
	public void roundsDownLikeTheRealAward()
	{
		assertEquals("+123", ScoreOverlay.xpDropText(1_239));
	}

	@Test
	public void neverNegative()
	{
		assertEquals("+0", ScoreOverlay.xpDropText(0));
	}
}
