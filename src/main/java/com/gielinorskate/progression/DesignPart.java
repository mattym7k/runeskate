package com.gielinorskate.progression;

import java.util.Arrays;
import java.util.Locale;

/** The parts of the board a design paints. Pure. */
public enum DesignPart
{
	GRIP,
	DECK,
	WHEELS;

	/** As written in designs.json: "grip", "deck", "wheels". */
	public final String key = name().toLowerCase(Locale.ROOT);
	/** "Grip", "Deck", "Wheels". */
	public final String label = name().charAt(0) + key.substring(1);
	/** Starts its designs' ids (a deck design may also have none: the old ladder's names). */
	public final String prefix = name() + "_";
	/** The part's number in the baked board (render.BakedBoardGeometry: grip 0, deck 1, wheels 2). */
	public final int index = ordinal();

	/** The part written {@code key} in designs.json, or null. */
	public static DesignPart fromKey(String key)
	{
		return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst().orElse(null);
	}
}
