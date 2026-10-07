package com.gielinorskate.progression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.JagexColor;

/**
 * The classic board's colours per deck: recolours of its five colour groups (grip, deck, wheels, graphic, metal;
 * see board.txt). The level ladder itself is now the board designs ({@link BoardDesigns}): each rung here is the
 * deck design of the same name (which is why constants are never renamed), and the classic board ("Board model:
 * Classic") draws a deck design of that name in these colours, any other deck design in {@link #DEFAULT}'s. An
 * unknown name is the {@link #DEFAULT}. Pure.
 */
public enum Deck
{
	CLASSIC("Classic", "Starter", 1, hsl(0, 0, 18), hsl(7, 3, 55), hsl(10, 2, 105), hsl(0, 7, 45), hsl(0, 0, 80)),
	BRONZE("Bronze", "Bronze", 1, hsl(0, 0, 18), hsl(6, 4, 48), hsl(8, 2, 95), hsl(5, 5, 30), hsl(6, 3, 62)),
	IRON("Iron", "Iron", 10, hsl(0, 0, 15), hsl(5, 1, 42), hsl(0, 0, 95), hsl(5, 1, 24), hsl(0, 0, 55)),
	STEEL("Steel", "Steel", 20, hsl(0, 0, 18), hsl(0, 0, 72), hsl(0, 0, 110), hsl(0, 0, 40), hsl(0, 0, 90)),
	MITHRIL("Mithril", "Mithril", 30, hsl(0, 0, 15), hsl(43, 3, 38), hsl(0, 0, 105), hsl(43, 4, 62), hsl(43, 2, 72)),
	ADAMANT("Adamant", "Adamant", 40, hsl(0, 0, 15), hsl(21, 3, 32), hsl(0, 0, 105), hsl(21, 4, 55), hsl(21, 2, 62)),
	RUNE("Rune", "Rune", 50, hsl(0, 0, 15), hsl(36, 4, 42), hsl(0, 0, 105), hsl(36, 5, 66), hsl(36, 2, 72)),
	DRAGON("Dragon", "Dragon", 60, hsl(0, 0, 12), hsl(0, 6, 35), hsl(0, 5, 90), hsl(0, 7, 18), hsl(0, 0, 35)),
	BANDOS("Bandos", "Gods", 70, hsl(0, 0, 12), hsl(8, 3, 38), hsl(10, 2, 88), hsl(17, 2, 48), hsl(18, 1, 62)),
	ARMADYL("Armadyl", "Gods", 70, hsl(0, 0, 15), hsl(0, 0, 112), hsl(29, 1, 92), hsl(29, 3, 52), hsl(29, 1, 72)),
	GUTHIX("Guthix", "Gods", 70, hsl(21, 3, 20), hsl(8, 3, 95), hsl(10, 6, 85), hsl(21, 5, 40), hsl(10, 5, 70)),
	ZAMORAK("Zamorak", "Gods", 70, hsl(0, 0, 10), hsl(0, 7, 35), hsl(10, 6, 85), hsl(0, 0, 16), hsl(10, 5, 70)),
	SARADOMIN("Saradomin", "Gods", 70, hsl(42, 4, 30), hsl(0, 0, 115), hsl(10, 6, 85), hsl(42, 6, 50),
		hsl(10, 5, 70)),
	TORVA("Torva", "Torva", 99, hsl(0, 0, 10), hsl(40, 1, 30), hsl(40, 1, 70), hsl(0, 6, 26), hsl(40, 1, 48));

	/** Shown before the player picks one, and for an unknown or locked saved deck. */
	public static final Deck DEFAULT = CLASSIC;
	/** Palette indices, as the colour index of each board.txt triangle. */
	public static final int GRIP = 0;
	public static final int DECK = 1;
	public static final int WHEELS = 2;
	public static final int GRAPHIC = 3;
	public static final int METAL = 4;
	public static final int COLOURS = 5;

	public final String displayName;
	/** The unlock tier's name ("Gods"): decks of one tier unlock together and are picked between. */
	public final String tier;
	public final int level;
	private final short[] palette;

	Deck(String displayName, String tier, int level, short grip, short deck, short wheels, short graphic, short metal)
	{
		this.displayName = displayName;
		this.tier = tier;
		this.level = level;
		this.palette = new short[]{grip, deck, wheels, graphic, metal};
	}

	private static short hsl(int hue, int saturation, int luminance)
	{
		return JagexColor.packHSL(hue, saturation, luminance);
	}

	/** The board's colours by palette index (a copy). */
	public short[] palette()
	{
		return palette.clone();
	}

	public boolean isUnlocked(int skatingLevel)
	{
		return skatingLevel >= level;
	}

	/** The deck saved (or sent) as {@code name}; {@link #DEFAULT} when null or unknown. */
	public static Deck fromName(String name)
	{
		if (name != null)
		{
			for (Deck d : values())
			{
				if (d.name().equals(name.trim()))
				{
					return d;
				}
			}
		}
		return DEFAULT;
	}

	/** The saved deck if it is unlocked at {@code skatingLevel}, otherwise {@link #DEFAULT}. */
	public static Deck usable(String savedName, int skatingLevel)
	{
		Deck d = fromName(savedName);
		return d.isUnlocked(skatingLevel) ? d : DEFAULT;
	}

	/** Decks first unlocked by going from level {@code from} to {@code to}, in order. */
	public static List<Deck> unlockedBetween(int from, int to)
	{
		List<Deck> out = new ArrayList<>();
		for (Deck d : values())
		{
			if (d.level > from && d.level <= to)
			{
				out.add(d);
			}
		}
		return out.isEmpty() ? Collections.emptyList() : out;
	}

	/** Each triangle's colour: {@code palette[colorIndex[i]]} (an index out of range uses the deck colour). */
	public static short[] faceColors(int[] colorIndex, short[] palette)
	{
		short[] out = new short[colorIndex.length];
		for (int i = 0; i < colorIndex.length; i++)
		{
			int c = colorIndex[i];
			out[i] = palette[c >= 0 && c < palette.length ? c : DECK];
		}
		return out;
	}
}
