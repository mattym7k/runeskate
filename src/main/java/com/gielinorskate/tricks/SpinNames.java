package com.gielinorskate.tricks;

/**
 * Names a body spin (the skater's heading change from take-off to landing) in 180-degree steps.
 *
 * <p>Rounding: a spin counts as n half turns once it reaches n * 180 - 30 degrees, so 150 is a 180,
 * 330 a 360 and 510 a 540 (anything short of that is the step below). Over-rotation within the landing
 * tolerance still counts as the step reached.
 *
 * <p>Direction: heading is clockwise from above, and the skater rides regular (left foot forward, facing
 * the right of the board's nose). A clockwise spin (heading increasing, + half turns) turns the back to
 * the direction of travel first: <b>Backside</b>. Counter-clockwise (-) is <b>Frontside</b>. The label
 * follows the rotation relative to the stance, so it is the same riding fakie (a fakie trick keeps its
 * "Fakie" prefix in front: "Fakie BS 180 Ollie").
 */
public final class SpinNames
{
	/** Bonus points per 180 degrees of spin. */
	public static final int POINTS_PER_HALF_TURN = 150;

	private SpinNames()
	{
	}

	/** Signed number of half turns in {@code spinRadians}: + = clockwise from above (backside). */
	public static int halfTurns(float spinRadians)
	{
		// a hair of tolerance so exactly 150 / 330 degrees given in float radians still count
		double deg = Math.abs(Math.toDegrees(spinRadians)) + 1e-3;
		int n = (int) Math.floor((deg + 30) / 180.0); // a spin 30 degrees short of the next 180 still counts as it
		return spinRadians < 0 ? -n : n;
	}

	/** "BS 180", "FS 540", ... or "" for no spin. */
	public static String label(int halfTurns)
	{
		return halfTurns == 0 ? "" : (halfTurns > 0 ? "BS " : "FS ") + Math.abs(halfTurns) * 180;
	}

	/** The spin label in front of {@code base} ("FS 180 Kickflip"), the label alone for an empty base. */
	public static String name(String base, int halfTurns)
	{
		String label = label(halfTurns);
		return label.isEmpty() ? base : base.isEmpty() ? label : label + " " + base;
	}

	/** Spin bonus points: {@link #POINTS_PER_HALF_TURN} per half turn either way. */
	public static int bonus(int halfTurns)
	{
		return POINTS_PER_HALF_TURN * Math.abs(halfTurns);
	}
}
