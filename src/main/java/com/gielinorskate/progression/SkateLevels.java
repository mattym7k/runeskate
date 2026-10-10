package com.gielinorskate.progression;

import net.runelite.api.Experience;

/** Skate XP: earned from landed combos, levelled on the game's own XP table (1 to 99, no virtual levels). Pure. */
public final class SkateLevels
{
	/** XP for a landed combo is its value divided by this (a 20k combo is 2,000 XP). Tuning constant. */
	public static final int COMBO_VALUE_PER_XP = 10;
	public static final int MAX_LEVEL = 99;
	/** As the game's skills. */
	public static final int MAX_XP = 200_000_000;

	/** XP for landing a combo worth {@code value}. */
	public static int xpForCombo(int value)
	{
		return Math.max(0, value / COMBO_VALUE_PER_XP);
	}

	public static int level(int xp)
	{
		return Math.min(MAX_LEVEL, Experience.getLevelForXp(Math.max(0, Math.min(MAX_XP, xp))));
	}

	/** XP at which {@code level} starts. */
	public static int xpForLevel(int level)
	{
		return level <= 1 ? 0 : Experience.getXpForLevel(Math.min(MAX_LEVEL, level));
	}

	/** XP still needed for the next level; 0 at 99. */
	public static int xpToNext(int xp)
	{
		int level = level(xp);
		return level >= MAX_LEVEL ? 0 : xpForLevel(level + 1) - xp;
	}

	/** How far through the current level, 0..1 (1 at 99). */
	public static float progress(int xp)
	{
		int level = level(xp);
		if (level >= MAX_LEVEL)
			return 1f;
		int start = xpForLevel(level);
		return (xp - start) / (float) (xpForLevel(level + 1) - start);
	}

	/** {@code xp} plus a gain, clamped to 0..{@link #MAX_XP}; negative gains are ignored. */
	public static int add(int xp, int gain)
	{
		return (int) Math.min(MAX_XP, (long) xp + Math.max(0, gain));
	}

	/** Saved XP text to a value; anything missing, unreadable or negative is 0. */
	public static int parseXp(String saved)
	{
		try
		{
			return (int) Math.max(0, Math.min(MAX_XP, Long.parseLong(saved.trim())));
		}
		catch (RuntimeException e)
		{
			return 0;
		}
	}
}
