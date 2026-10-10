package com.gielinorskate.render;

import com.gielinorskate.Text;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;
import java.util.EnumMap;
import java.util.Map;

/**
 * Each grab's own pose: the body reaches for its part of the board (the upper body tipping along the board toward
 * the nose or tail, and rolling toward the toe or heel side), and the board tweaks toward the hand (pitch lifting the
 * grabbed end, see {@link #boardPitch}; roll, toe edge up > 0, the opposite of a carve's lean into the turn). A tweak holds the same pose
 * 1.6 times further. Zero for anything that is not a grab. Pure; shared with party ghosts.
 */
public final class GrabPose
{
	/** Board-model height (y up negative) of the deck's edge, where a hand grabs the side of the board. */
	static final float EDGE_Y = -14f;
	/**
	 * Per grab: its pose and where on the board the hand holds (the columns are described in text/data.properties,
	 * under grabpose).
	 */
	private static final Map<Trick, float[]> POSES = new EnumMap<>(Trick.class);

	static
	{
		for (String g : Text.get("grabpose.grabs").split(" "))
			POSES.put(Trick.valueOf(g), Text.floats("grabpose." + g));
	}

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

	/** The grab point's distance along the board, + toward the nose (0 for anything that is not a grab). */
	public static float grabAlong(Trick grab)
	{
		return at(grab, 4, 0f);
	}

	/** The grab point's distance across the board, - toward the toe edge (the chest side), + the heel edge. */
	public static float grabAcross(Trick grab)
	{
		return at(grab, 5, 0f);
	}

	/** The grab point's board-model height (y down; the deck top is at -16). */
	public static float grabBoardY(Trick grab)
	{
		return at(grab, 6, EDGE_Y);
	}

	/** True for a grab (or its tweak): the hand reaches for the board. */
	public static boolean reaches(Trick t)
	{
		return POSES.containsKey(plain(t));
	}

	/**
	 * The hand a grab is usually done with when the grabbing key is not known (a party ghost): the front (left) hand
	 * for a melon, mute and nosegrab, the back (right) hand for the rest.
	 */
	public static boolean usualLeftHand(Trick grab)
	{
		Trick g = plain(grab);
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
		return plain(grab) == Trick.TAILGRAB && keyHand != 0 ? keyHand > 0 : usualLeftHand(grab);
	}

	/** The grab a tweak is of, or {@code grab} itself. */
	private static Trick plain(Trick grab)
	{
		Trick plain = Grabs.untweaked(grab);
		return plain != null ? plain : grab;
	}

	private static float value(Trick grab, int i)
	{
		return (Grabs.untweaked(grab) != null ? 1.6f : 1f) * at(grab, i, 0f);
	}

	/** Entry {@code i} of the grab's (or its plain grab's) row, {@code none} for anything that is not a grab. */
	private static float at(Trick grab, int i, float none)
	{
		float[] p = POSES.get(plain(grab));
		return p == null ? none : p[i];
	}
}
