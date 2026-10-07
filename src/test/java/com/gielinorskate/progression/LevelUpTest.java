package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LevelUpTest
{
	@Test
	public void messageNamesTheFinalLevelInRuneSkateWording()
	{
		assertEquals("You reached Skating level 5!",
			new LevelUp(2, 5).message());
	}

	@Test
	public void bannerEveryTenLevelsAndAt99()
	{
		assertNull(new LevelUp(1, 9).banner());
		assertEquals("Skating level 10!", new LevelUp(9, 10).banner());
		// jumping past a multiple of ten still gets one
		assertEquals("Skating level 23!", new LevelUp(18, 23).banner());
		assertNull(new LevelUp(91, 98).banner());
		assertEquals("99 Skating!", new LevelUp(98, 99).banner());
	}

	@Test
	public void maxedAt99()
	{
		assertTrue(new LevelUp(98, 99).isMax());
		assertFalse(new LevelUp(97, 98).isMax());
	}
}
