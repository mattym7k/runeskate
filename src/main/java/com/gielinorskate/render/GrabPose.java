package com.gielinorskate.render;

import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;

/**
 * Each grab's own pose: the body reaches for its part of the board (the upper body tipping along the board toward
 * the nose or tail, and rolling toward the toe or heel side), and the board tweaks toward the hand (pitch lifting the
 * grabbed end, see {@link #boardPitch}; roll, toe edge up > 0, the opposite of a carve's lean into the turn). A tweak holds the same pose
 * {@link #TWEAK} times further. Zero for anything that is not a grab. Pure; shared with party ghosts.
 */
public final class GrabPose
{
	/** A tweaked grab's pose, as a multiple of its plain grab's. */
	static final float TWEAK = 1.6f;

	/** torsoLean (+ toward the nose), body roll (- toward the chest / toes), board pitch, board roll. */
	private static final float[] INDY = {-0.1f, -0.18f, 0f, 0.25f};
	private static final float[] MELON = {0.1f, 0.18f, 0f, -0.25f};
	private static final float[] MUTE = {0.15f, -0.2f, -0.08f, 0.22f};
	private static final float[] STALEFISH = {-0.15f, 0.22f, 0.08f, -0.22f};
	private static final float[] NOSEGRAB = {0.45f, 0f, -0.3f, 0f};
	private static final float[] TAILGRAB = {-0.45f, 0f, 0.3f, 0f};
	private static final float[] CRAIL = {0.4f, -0.1f, -0.25f, 0.12f};
	private static final float[] NONE = {0f, 0f, 0f, 0f};

	private GrabPose()
	{
	}

	/** Radians the upper body tips along the board, + toward the nose (BodyPose.torsoLean for regular stance). */
	public static float torsoLean(Trick grab)
	{
		return value(grab, 0);
	}

	/** Radians of body roll, BodyPose's sense: negative leans the head toward the chest (the toe side). */
	public static float bodyRoll(Trick grab)
	{
		return value(grab, 1);
	}

	/**
	 * Extra board pitch, radians, in the drawn board's sense (BoardPlacement.pose: + turns the board's +z end up).
	 * Each grab lifts the end its hand holds: a spot toward the nose ({@link #grabAlong} > 0) sits at board -z for
	 * regular stance (GrabReach), so nose-end grabs pitch negative and tail-end grabs positive. (They were the other
	 * way round, which dipped the grabbed end away from the hand.)
	 */
	public static float boardPitch(Trick grab)
	{
		return value(grab, 2);
	}

	/** Extra board roll, radians, toe edge up > 0. */
	public static float boardRoll(Trick grab)
	{
		return value(grab, 3);
	}

	private static float value(Trick grab, int i)
	{
		Trick plain = Grabs.untweaked(grab);
		return plain != null ? TWEAK * pose(plain)[i] : pose(grab)[i];
	}

	private static float[] pose(Trick grab)
	{
		if (grab == null)
		{
			return NONE;
		}
		switch (grab)
		{
			case INDY:
				return INDY;
			case MELON:
				return MELON;
			case MUTE:
				return MUTE;
			case STALEFISH:
				return STALEFISH;
			case NOSEGRAB:
				return NOSEGRAB;
			case TAILGRAB:
				return TAILGRAB;
			case CRAIL:
				return CRAIL;
			default:
				return NONE;
		}
	}

	/** Board-model height (y up negative) of the deck's edge, where a hand grabs the side of the board. */
	static final float EDGE_Y = -14f;
	/** Board-model height of the deck near the nose or tail tip (the kicktails rise to -23). */
	static final float TIP_Y = -20f;
	/** Half the deck's width: a toe- or heel-edge grab holds the edge. */
	static final float EDGE = 13f;

	/**
	 * Where on the board the hand holds for each grab: units along the board (+ toward the nose), units across it
	 * (- toward the toe edge, the rider's chest side; + the heel edge), and the board-model height there.
	 */
	private static final float[] INDY_AT = {-6f, -EDGE, EDGE_Y};
	private static final float[] MELON_AT = {6f, EDGE, EDGE_Y};
	private static final float[] MUTE_AT = {18f, -EDGE, EDGE_Y};
	private static final float[] STALEFISH_AT = {-18f, EDGE, EDGE_Y};
	private static final float[] NOSEGRAB_AT = {43f, 0f, TIP_Y};
	private static final float[] TAILGRAB_AT = {-43f, 0f, TIP_Y};
	private static final float[] CRAIL_AT = {40f, -6f, TIP_Y};

	/** The grab point's distance along the board, + toward the nose (0 for anything that is not a grab). */
	public static float grabAlong(Trick grab)
	{
		float[] at = grabPoint(grab);
		return at == null ? 0f : at[0];
	}

	/** The grab point's distance across the board, - toward the toe edge (the chest side), + the heel edge. */
	public static float grabAcross(Trick grab)
	{
		float[] at = grabPoint(grab);
		return at == null ? 0f : at[1];
	}

	/** The grab point's board-model height (y down; the deck top is at -16). */
	public static float grabBoardY(Trick grab)
	{
		float[] at = grabPoint(grab);
		return at == null ? EDGE_Y : at[2];
	}

	/** True for a grab (or its tweak): the hand reaches for the board. */
	public static boolean reaches(Trick t)
	{
		return grabPoint(t) != null;
	}

	/**
	 * The hand a grab is usually done with when the grabbing key is not known (a party ghost): the front (left) hand
	 * for a melon, mute and nosegrab, the back (right) hand for the rest.
	 */
	public static boolean usualLeftHand(Trick grab)
	{
		Trick plain = Grabs.untweaked(grab);
		Trick g = plain != null ? plain : grab;
		return g == Trick.MELON || g == Trick.MUTE || g == Trick.NOSEGRAB;
	}

	/**
	 * Whether the left (front) hand holds {@code grab}: each grab's own hand (front for a melon, mute and nosegrab, back
	 * for an indy, stalefish and crail, and their tweaks), whichever key picked it, so the hand never reaches across the
	 * body. (The grab keys pick an Indy with Q, the left hand, and a Melon with E when not aimed; drawn with that hand
	 * the arm crossed in front of the crotch to the middle of the board.) A tailgrab is held with the key's hand
	 * ({@code keyHand} +1 left, -1 right; 0 not known: the back hand).
	 */
	public static boolean leftHand(Trick grab, int keyHand)
	{
		Trick plain = Grabs.untweaked(grab);
		Trick g = plain != null ? plain : grab;
		if (g == Trick.TAILGRAB && keyHand != 0)
		{
			return keyHand > 0;
		}
		return usualLeftHand(g);
	}

	private static float[] grabPoint(Trick grab)
	{
		Trick plain = Grabs.untweaked(grab);
		Trick g = plain != null ? plain : grab;
		if (g == null)
		{
			return null;
		}
		switch (g)
		{
			case INDY:
				return INDY_AT;
			case MELON:
				return MELON_AT;
			case MUTE:
				return MUTE_AT;
			case STALEFISH:
				return STALEFISH_AT;
			case NOSEGRAB:
				return NOSEGRAB_AT;
			case TAILGRAB:
				return TAILGRAB_AT;
			case CRAIL:
				return CRAIL_AT;
			default:
				return null;
		}
	}
}
