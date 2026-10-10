package com.gielinorskate.feedback;

import com.gielinorskate.Text;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The word shouted on landing a combo worth at least {@link #minValue} points. Pure. */
public enum Callout
{
	NICE,
	GZ,
	SPICY,
	/** Raid-loot purple. */
	PURPLE,
	PET_DROP;

	public final int minValue;
	public final String word;
	public final Color color;

	/** Its threshold and colour ("callout." + name) and word ("callout.word." + name) from text/overlay.properties. */
	Callout()
	{
		int[] v = Text.ints("callout." + name());
		minValue = v[0];
		color = new Color(v[1], v[2], v[3]);
		word = Text.get("callout.word." + name());
	}

	/** The biggest callout {@code value} earns, or null below {@link #NICE}. */
	public static Callout forValue(int value)
	{
		return Stream.of(values()).filter(c -> value >= c.minValue).reduce((lower, higher) -> higher).orElse(null);
	}

	/**
	 * Smaller lines under the callout word: "Clean" (only under a word: the HUD already says "Clean landing"),
	 * "xN Multi" from 10 distinct tricks, and "Max combo!" from 99 tricks in one combo.
	 */
	public static List<String> extras(Callout word, boolean clean, int multiplier, int trickCount)
	{
		List<String> lines = new ArrayList<>(3);
		if (word != null && clean)
			lines.add("Clean");
		if (multiplier >= 10)
			lines.add("x" + multiplier + " Multi");
		if (trickCount >= 99)
			lines.add("Max combo!");
		return lines;
	}

	/**
	 * Lines for the variety bonuses of a landed combo: "New trick +25%" ("3 new tricks +25%") for first landings
	 * this session, and "Long combo +1" for one longer than 8 s.
	 */
	public static List<String> varietyLines(int newTricks, boolean longCombo)
	{
		List<String> lines = new ArrayList<>(2);
		if (newTricks > 0)
			lines.add(newTricks == 1 ? "New trick +25%" : newTricks + " new tricks +25%");
		if (longCombo)
			lines.add("Long combo +1");
		return lines;
	}
}
