package com.gielinorskate.feedback;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The word shouted on landing a combo worth at least {@link #minValue} points. Pure. */
public enum Callout
{
	NICE(5_000, "Nice!", new Color(255, 255, 255)),
	GZ(15_000, "Gz!", new Color(90, 230, 110)),
	SPICY(40_000, "Spicy!", new Color(255, 140, 40)),
	/** Raid-loot purple. */
	PURPLE(100_000, "Purple!", new Color(190, 90, 255)),
	PET_DROP(250_000, "Pet drop!", new Color(255, 205, 50));

	/** Distinct tricks for the "x10 Multi" line. */
	public static final int MULTI_LINE_AT = 10;
	/** Tricks in one combo for the "Max combo!" line. */
	public static final int MAX_COMBO_AT = 99;

	public final int minValue;
	public final String word;
	public final Color color;

	Callout(int minValue, String word, Color color)
	{
		this.minValue = minValue;
		this.word = word;
		this.color = color;
	}

	/** The biggest callout {@code value} earns, or null below {@link #NICE}. */
	public static Callout forValue(int value)
	{
		Callout best = null;
		for (Callout c : values())
		{
			if (value >= c.minValue)
			{
				best = c;
			}
		}
		return best;
	}

	/**
	 * Smaller lines under the callout word: "Clean" (only under a word: the HUD already says "Clean landing"),
	 * "xN Multi" from {@link #MULTI_LINE_AT} distinct tricks, and "Max combo!" from {@link #MAX_COMBO_AT} tricks.
	 */
	public static List<String> extras(Callout word, boolean clean, int multiplier, int trickCount)
	{
		List<String> lines = new ArrayList<>(3);
		if (word != null && clean)
		{
			lines.add("Clean");
		}
		if (multiplier >= MULTI_LINE_AT)
		{
			lines.add("x" + multiplier + " Multi");
		}
		if (trickCount >= MAX_COMBO_AT)
		{
			lines.add("Max combo!");
		}
		return lines.isEmpty() ? Collections.emptyList() : lines;
	}

	/**
	 * Lines for the variety bonuses of a landed combo: "New trick +25%" ("3 new tricks +25%") for first landings
	 * this session, and "Long combo +1" for one longer than 8 s.
	 */
	public static List<String> varietyLines(int newTricks, boolean longCombo)
	{
		List<String> lines = new ArrayList<>(2);
		if (newTricks == 1)
		{
			lines.add("New trick +25%");
		}
		else if (newTricks > 1)
		{
			lines.add(newTricks + " new tricks +25%");
		}
		if (longCombo)
		{
			lines.add("Long combo +1");
		}
		return lines.isEmpty() ? Collections.emptyList() : lines;
	}
}
