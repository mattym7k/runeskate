package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Skate XP from combos, and levels on the OSRS XP table. */
public class SkateLevelsTest
{
	@Test
	public void xpIsTheLandedValueOverTen()
	{
		assertEquals(0, SkateLevels.xpForCombo(0));
		assertEquals(0, SkateLevels.xpForCombo(9));
		assertEquals(37, SkateLevels.xpForCombo(375));
		assertEquals(2_000, SkateLevels.xpForCombo(20_000));
		assertEquals(0, SkateLevels.xpForCombo(-50));
	}

	@Test
	public void levelsFollowTheOsrsTable()
	{
		assertEquals(1, SkateLevels.level(0));
		assertEquals(1, SkateLevels.level(82));
		assertEquals(2, SkateLevels.level(83));
		assertEquals(10, SkateLevels.level(1_154));
		assertEquals(9, SkateLevels.level(1_153));
		assertEquals(50, SkateLevels.level(101_333));
		assertEquals(92, SkateLevels.level(6_517_253));
		assertEquals(99, SkateLevels.level(13_034_431));
		// no virtual levels: 99 is the top
		assertEquals(99, SkateLevels.level(200_000_000));
	}

	@Test
	public void xpForLevelIsTheTableStart()
	{
		assertEquals(0, SkateLevels.xpForLevel(1));
		assertEquals(83, SkateLevels.xpForLevel(2));
		assertEquals(13_034_431, SkateLevels.xpForLevel(99));
	}

	@Test
	public void xpToNextAndProgressWithinALevel()
	{
		assertEquals(83, SkateLevels.xpToNext(0));
		assertEquals(0f, SkateLevels.progress(0), 1e-6);
		// level 2 runs 83..173
		assertEquals(91, SkateLevels.xpToNext(83));
		assertEquals(0.5f, SkateLevels.progress(83 + 45), 0.01f);
		assertEquals(0, SkateLevels.xpToNext(13_034_431));
		assertEquals(1f, SkateLevels.progress(13_034_431), 1e-6);
	}

	@Test
	public void addingClampsAtTheMaximum()
	{
		assertEquals(200_000_000, SkateLevels.add(199_999_990, 50));
		assertEquals(150, SkateLevels.add(100, 50));
		assertEquals(100, SkateLevels.add(100, -5));
	}

	@Test
	public void savedXpParsesSafely()
	{
		assertEquals(0, SkateLevels.parseXp(null));
		assertEquals(0, SkateLevels.parseXp(""));
		assertEquals(0, SkateLevels.parseXp("lots"));
		assertEquals(0, SkateLevels.parseXp("-4"));
		assertEquals(1234, SkateLevels.parseXp(" 1234 "));
		assertEquals(200_000_000, SkateLevels.parseXp("999999999999"));
	}
}
