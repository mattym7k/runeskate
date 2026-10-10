package com.gielinorskate.progression;

import lombok.AllArgsConstructor;

/** One or more Skating levels gained at once: reported with the final level. Pure. */
@AllArgsConstructor
public final class LevelUp
{
	public final int from;
	public final int to;

	/** The level-up chat line (sent tagged as a RuneSkate message). */
	public String message()
	{
		return "You reached Skating level " + to + "!";
	}

	/** The HUD banner for reaching a multiple of ten (or 99), or null. */
	public String banner()
	{
		return to >= SkateLevels.MAX_LEVEL ? "99 Skating!" : to / 10 > from / 10 ? "Skating level " + to + "!" : null;
	}
}
