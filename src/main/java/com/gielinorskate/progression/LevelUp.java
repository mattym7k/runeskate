package com.gielinorskate.progression;

/** One or more Skating levels gained at once: reported with the final level. Pure. */
public final class LevelUp
{
	/** A banner shows for every this many levels (and at 99). */
	public static final int BANNER_EVERY = 10;

	public final int from;
	public final int to;

	public LevelUp(int from, int to)
	{
		this.from = from;
		this.to = to;
	}

	/** The game's own level-up wording. */
	public String message()
	{
		return "Congratulations, you've just advanced your Skating level. You are now level " + to + ".";
	}

	/** The HUD banner for reaching a multiple of ten (or 99), or null. */
	public String banner()
	{
		if (to >= SkateLevels.MAX_LEVEL)
		{
			return "99 Skating!";
		}
		return to / BANNER_EVERY > from / BANNER_EVERY ? "Skating level " + to + "!" : null;
	}

	public boolean isMax()
	{
		return to >= SkateLevels.MAX_LEVEL;
	}
}
