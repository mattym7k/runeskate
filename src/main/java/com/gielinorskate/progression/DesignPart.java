package com.gielinorskate.progression;

/**
 * The parts of the board a design paints. {@link #index} is the part's number in the baked board
 * (render.BakedBoardGeometry: grip 0, deck 1, wheels 2); {@link #prefix} starts its designs' ids (a deck design
 * may also have none: the old ladder's names). Pure.
 */
public enum DesignPart
{
	GRIP("grip", "Grip", "GRIP_", 0),
	DECK("deck", "Deck", "DECK_", 1),
	WHEELS("wheels", "Wheels", "WHEELS_", 2);

	/** As written in designs.json. */
	public final String key;
	public final String label;
	public final String prefix;
	public final int index;

	DesignPart(String key, String label, String prefix, int index)
	{
		this.key = key;
		this.label = label;
		this.prefix = prefix;
		this.index = index;
	}

	/** The part written {@code key} in designs.json, or null. */
	public static DesignPart fromKey(String key)
	{
		for (DesignPart p : values())
		{
			if (p.key.equals(key))
			{
				return p;
			}
		}
		return null;
	}
}
