package com.gielinorskate.progression;

/**
 * The developer-mode {@code ::skatelevel [1-99]} command's arguments: none shows the level and XP, a level 1..99
 * sets it, anything else gets the usage line. Pure.
 */
public final class SkateLevelCommand
{
	public static final String USAGE = "Usage: ::skatelevel <1-99> sets your Skating level (developer mode only); "
		+ "::skatelevel shows it.";

	public enum Kind
	{
		SHOW,
		SET,
		INVALID
	}

	public final Kind kind;
	/** The level to set, for {@link Kind#SET}. */
	public final int level;

	private SkateLevelCommand(Kind kind, int level)
	{
		this.kind = kind;
		this.level = level;
	}

	public static SkateLevelCommand parse(String[] args)
	{
		if (args == null || args.length == 0)
		{
			return new SkateLevelCommand(Kind.SHOW, 0);
		}
		if (args.length > 1)
		{
			return new SkateLevelCommand(Kind.INVALID, 0);
		}
		try
		{
			int level = Integer.parseInt(args[0].trim());
			if (level >= 1 && level <= SkateLevels.MAX_LEVEL)
			{
				return new SkateLevelCommand(Kind.SET, level);
			}
		}
		catch (NumberFormatException e)
		{
			// falls through to the usage line
		}
		return new SkateLevelCommand(Kind.INVALID, 0);
	}

	/** "Skating level 12 (2,000 XP)." */
	public static String describe(int level, int xp)
	{
		return "Skating level " + level + " (" + String.format("%,d", xp) + " XP).";
	}
}
