package com.gielinorskate.tricks;

import com.gielinorskate.Text;

/**
 * Catalog of every trick the skater can perform. {@code rollTurns} and {@code yawTurns} are the
 * board's rotation around its long axis and the vertical axis respectively, in full turns
 * (1.0 = 360 degrees); {@code duration} is the number of seconds the board takes to complete that
 * rotation, or 0 for holds (grabs, manuals, grinds) that don't rotate the board on their own. Entries without a
 * kind are FLIPs. Each constant's display name, kind, points and turns are bundled under trick.NAME
 * (text/data.properties).
 */
public enum Trick
{
	OLLIE,
	NOLLIE,
	KICKFLIP,
	HEELFLIP,
	POP_SHOVE_IT,
	FS_POP_SHOVE_IT,
	SHOVE_IT_360,
	FS_SHOVE_IT_360,
	VARIAL_KICKFLIP,
	VARIAL_HEELFLIP,
	TRE_FLIP,
	LASER_FLIP,
	DOUBLE_KICKFLIP,
	DOUBLE_HEELFLIP,
	NOLLIE_KICKFLIP,
	NOLLIE_HEELFLIP,
	// nollie forms of the shove-its and the 360 flip: 50 more than the regular trick, like the other nollies
	NOLLIE_SHOVE_IT,
	NOLLIE_FS_SHOVE_IT,
	NOLLIE_TRE_FLIP,
	INDY,
	MELON,
	// directional grabs (a grab key held while the mouse moves; see Grabs) and their tweaks: a grab held past
	// 0.5 s turns into its tweak, worth half as much again per second
	MUTE,
	STALEFISH,
	NOSEGRAB,
	TAILGRAB,
	CRAIL,
	METHOD,
	TWEAKED_INDY,
	JAPAN,
	TWEAKED_STALEFISH,
	NOSEBONE,
	TAILBONE,
	CRAIL_TWEAK,
	MANUAL,
	NOSE_MANUAL,
	FIFTY_FIFTY,
	FIVE_O,
	NOSEGRIND,
	BOARDSLIDE,
	CROOKED,
	// slide and crooked variations (W / S while locking or mid-grind) and the lipslide (board turned past
	// square to the approach): each a little harder to hold than the plain one, so worth a little more
	NOSESLIDE,
	TAILSLIDE,
	SMITH,
	FEEBLE,
	LIPSLIDE,
	// Shift held as the flick fires. (Party ghosts send tricks by name: renaming a constant breaks the wire.)
	// Impossible: the board wraps end over end around the back foot (pitch, nose up and over)
	IMPOSSIBLE,
	// hardflip = kickflip + frontside shove; inward heelflip = heelflip + backside shove
	HARDFLIP,
	INWARD_HEELFLIP,
	// bigspin: a 360 shove-it with the body turning 180 the same way (applied by physics over the duration)
	BIGSPIN,
	FS_BIGSPIN,
	NOLLIE_HARDFLIP,
	NOLLIE_INWARD_HEELFLIP,
	// a re-flick during a double
	TRIPLE_KICKFLIP,
	TRIPLE_HEELFLIP,
	// body flips (Shift + W / Shift + S in the air): the whole skater turns end over end, scored on landing
	FRONTFLIP,
	BACKFLIP,
	DOUBLE_FRONTFLIP,
	DOUBLE_BACKFLIP;

	public final String displayName;
	public final TrickKind kind;
	/** Flat points for POP/FLIP; points per second for GRAB/MANUAL/GRIND. */
	public final int points;
	/** Board flips around its long axis; + = kickflip direction. */
	public final float rollTurns;
	/** Board spins around the vertical axis; + = backside/clockwise from above. */
	public final float yawTurns;
	/** Seconds to complete the board rotation (0 for holds). */
	public final float duration;
	/** Board flips end over end (around its width); + = nose up and over (impossible). */
	public final float pitchTurns;
	/**
	 * Turns of the skater's own body (heading) that are part of the trick, applied by the physics over its
	 * duration; + = clockwise from above. A bigspin's body 180. The board's {@link #yawTurns} are its turn in
	 * the world, so relative to the turning body it turns yawTurns - bodyYawTurns.
	 */
	public final float bodyYawTurns;
	/**
	 * Whole-body flips of a body-flip trick (skater and board about the centre of mass); + = frontflip
	 * (forward pitch). 0 for every board trick. A body flip has no board rotation or duration of its own.
	 */
	public final float bodyFlipTurns;

	Trick()
	{
		// display name|kind|points|rollTurns|yawTurns|duration|pitchTurns|bodyYawTurns|bodyFlipTurns
		String[] p = Text.get("trick." + name()).split("\\|");
		displayName = p[0];
		kind = TrickKind.valueOf(p[1]);
		points = Integer.parseInt(p[2]);
		rollTurns = Float.parseFloat(p[3]);
		yawTurns = Float.parseFloat(p[4]);
		duration = Float.parseFloat(p[5]);
		pitchTurns = Float.parseFloat(p[6]);
		bodyYawTurns = Float.parseFloat(p[7]);
		bodyFlipTurns = Float.parseFloat(p[8]);
	}
}
