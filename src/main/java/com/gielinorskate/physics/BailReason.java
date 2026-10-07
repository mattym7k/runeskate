package com.gielinorskate.physics;

/** Why the skater bailed, shown under "Bailed" on the HUD. */
public enum BailReason
{
	/** Landed with the board too far across the direction of travel. */
	SIDEWAYS("Landed sideways - finish the spin"),
	/** Touched down before a board flip was far enough along to catch. */
	FLIP_NOT_CAUGHT("Board still flipping - jump higher"),
	/** Touched down more than 40 degrees off a whole front or back flip. */
	BODY_FLIP("Flip not finished - jump higher"),
	/** A hard, head-on hit. */
	WALL("Hit a wall");

	public final String text;

	BailReason(String text)
	{
		this.text = text;
	}

	/**
	 * The reason a landing bails, or null when it does not: sideways first (the landing angle is checked
	 * before the flips), then an uncaught board flip, then an unfinished body flip.
	 */
	public static BailReason forLanding(boolean sideways, boolean flipUncaught, boolean bodyFlipOff)
	{
		if (sideways)
		{
			return SIDEWAYS;
		}
		if (flipUncaught)
		{
			return FLIP_NOT_CAUGHT;
		}
		return bodyFlipOff ? BODY_FLIP : null;
	}
}
