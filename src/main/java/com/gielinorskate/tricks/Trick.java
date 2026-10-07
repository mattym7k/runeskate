package com.gielinorskate.tricks;

/**
 * Catalog of every trick the skater can perform. {@code rollTurns} and {@code yawTurns} are the
 * board's rotation around its long axis and the vertical axis respectively, in full turns
 * (1.0 = 360 degrees); {@code duration} is the number of seconds the board takes to complete that
 * rotation, or 0 for holds (grabs, manuals, grinds) that don't rotate the board on their own.
 */
public enum Trick
{
	OLLIE("Ollie", TrickKind.POP, 100, 0f, 0f, 0f),
	NOLLIE("Nollie", TrickKind.POP, 150, 0f, 0f, 0f),
	KICKFLIP("Kickflip", TrickKind.FLIP, 300, 1f, 0f, 0.35f),
	HEELFLIP("Heelflip", TrickKind.FLIP, 300, -1f, 0f, 0.35f),
	POP_SHOVE_IT("Pop Shove-it", TrickKind.FLIP, 250, 0f, 0.5f, 0.30f),
	FS_POP_SHOVE_IT("FS Pop Shove-it", TrickKind.FLIP, 250, 0f, -0.5f, 0.30f),
	SHOVE_IT_360("360 Shove-it", TrickKind.FLIP, 500, 0f, 1f, 0.40f),
	FS_SHOVE_IT_360("FS 360 Shove-it", TrickKind.FLIP, 500, 0f, -1f, 0.40f),
	VARIAL_KICKFLIP("Varial Kickflip", TrickKind.FLIP, 600, 1f, 0.5f, 0.40f),
	VARIAL_HEELFLIP("Varial Heelflip", TrickKind.FLIP, 600, -1f, -0.5f, 0.40f),
	TRE_FLIP("360 Flip", TrickKind.FLIP, 900, 1f, 1f, 0.45f),
	LASER_FLIP("Laser Flip", TrickKind.FLIP, 950, -1f, -1f, 0.45f),
	DOUBLE_KICKFLIP("Double Kickflip", TrickKind.FLIP, 800, 2f, 0f, 0.50f),
	DOUBLE_HEELFLIP("Double Heelflip", TrickKind.FLIP, 800, -2f, 0f, 0.50f),
	NOLLIE_KICKFLIP("Nollie Kickflip", TrickKind.FLIP, 350, 1f, 0f, 0.35f),
	NOLLIE_HEELFLIP("Nollie Heelflip", TrickKind.FLIP, 350, -1f, 0f, 0.35f),
	// nollie forms of the shove-its and the 360 flip: 50 more than the regular trick, like the other nollies
	NOLLIE_SHOVE_IT("Nollie Shove-it", TrickKind.FLIP, 300, 0f, 0.5f, 0.30f),
	NOLLIE_FS_SHOVE_IT("Nollie FS Shove-it", TrickKind.FLIP, 300, 0f, -0.5f, 0.30f),
	NOLLIE_TRE_FLIP("Nollie 360 Flip", TrickKind.FLIP, 1000, 1f, 1f, 0.45f),
	INDY("Indy", TrickKind.GRAB, 300, 0f, 0f, 0f),
	MELON("Melon", TrickKind.GRAB, 300, 0f, 0f, 0f),
	// directional grabs (a grab key held while the mouse moves; see Grabs) and their tweaks: a grab held past
	// 0.5 s turns into its tweak, worth half as much again per second
	MUTE("Mute", TrickKind.GRAB, 300, 0f, 0f, 0f),
	STALEFISH("Stalefish", TrickKind.GRAB, 350, 0f, 0f, 0f),
	NOSEGRAB("Nosegrab", TrickKind.GRAB, 300, 0f, 0f, 0f),
	TAILGRAB("Tailgrab", TrickKind.GRAB, 300, 0f, 0f, 0f),
	CRAIL("Crail", TrickKind.GRAB, 350, 0f, 0f, 0f),
	METHOD("Method", TrickKind.GRAB, 450, 0f, 0f, 0f),
	TWEAKED_INDY("Tweaked Indy", TrickKind.GRAB, 450, 0f, 0f, 0f),
	JAPAN("Japan", TrickKind.GRAB, 450, 0f, 0f, 0f),
	TWEAKED_STALEFISH("Tweaked Stalefish", TrickKind.GRAB, 525, 0f, 0f, 0f),
	NOSEBONE("Nosebone", TrickKind.GRAB, 450, 0f, 0f, 0f),
	TAILBONE("Tailbone", TrickKind.GRAB, 450, 0f, 0f, 0f),
	CRAIL_TWEAK("Crail Tweak", TrickKind.GRAB, 525, 0f, 0f, 0f),
	MANUAL("Manual", TrickKind.MANUAL, 150, 0f, 0f, 0f),
	NOSE_MANUAL("Nose Manual", TrickKind.MANUAL, 200, 0f, 0f, 0f),
	FIFTY_FIFTY("50-50", TrickKind.GRIND, 200, 0f, 0f, 0f),
	FIVE_O("5-0", TrickKind.GRIND, 250, 0f, 0f, 0f),
	NOSEGRIND("Nosegrind", TrickKind.GRIND, 250, 0f, 0f, 0f),
	BOARDSLIDE("Boardslide", TrickKind.GRIND, 250, 0f, 0f, 0f),
	CROOKED("Crooked Grind", TrickKind.GRIND, 300, 0f, 0f, 0f),
	// slide and crooked variations (W / S while locking or mid-grind) and the lipslide (board turned past
	// square to the approach): each a little harder to hold than the plain one, so worth a little more
	NOSESLIDE("Noseslide", TrickKind.GRIND, 300, 0f, 0f, 0f),
	TAILSLIDE("Tailslide", TrickKind.GRIND, 300, 0f, 0f, 0f),
	SMITH("Smith Grind", TrickKind.GRIND, 350, 0f, 0f, 0f),
	FEEBLE("Feeble Grind", TrickKind.GRIND, 350, 0f, 0f, 0f),
	LIPSLIDE("Lipslide", TrickKind.GRIND, 300, 0f, 0f, 0f),
	// Shift held as the flick fires. (Party ghosts send tricks by name: renaming a constant breaks the wire.)
	// Impossible: the board wraps end over end around the back foot (pitch, nose up and over)
	IMPOSSIBLE("Impossible", TrickKind.FLIP, 450, 0f, 0f, 0.40f, 1f, 0f),
	// hardflip = kickflip + frontside shove; inward heelflip = heelflip + backside shove
	HARDFLIP("Hardflip", TrickKind.FLIP, 500, 1f, -0.5f, 0.40f),
	INWARD_HEELFLIP("Inward Heelflip", TrickKind.FLIP, 500, -1f, 0.5f, 0.40f),
	// bigspin: a 360 shove-it with the body turning 180 the same way (applied by physics over the duration)
	BIGSPIN("Bigspin", TrickKind.FLIP, 550, 0f, 1f, 0.45f, 0f, 0.5f),
	FS_BIGSPIN("FS Bigspin", TrickKind.FLIP, 550, 0f, -1f, 0.45f, 0f, -0.5f),
	NOLLIE_HARDFLIP("Nollie Hardflip", TrickKind.FLIP, 550, 1f, -0.5f, 0.40f),
	NOLLIE_INWARD_HEELFLIP("Nollie Inward Heelflip", TrickKind.FLIP, 550, -1f, 0.5f, 0.40f),
	// a re-flick during a double
	TRIPLE_KICKFLIP("Triple Kickflip", TrickKind.FLIP, 1300, 3f, 0f, 0.65f),
	TRIPLE_HEELFLIP("Triple Heelflip", TrickKind.FLIP, 1300, -3f, 0f, 0.65f),
	// body flips (Shift + W / Shift + S in the air): the whole skater turns end over end, scored on landing
	FRONTFLIP("Frontflip", TrickKind.FLIP, 1000, 1f),
	BACKFLIP("Backflip", TrickKind.FLIP, 1000, -1f),
	DOUBLE_FRONTFLIP("Double Frontflip", TrickKind.FLIP, 2200, 2f),
	DOUBLE_BACKFLIP("Double Backflip", TrickKind.FLIP, 2200, -2f);

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

	Trick(String displayName, TrickKind kind, int points, float rollTurns, float yawTurns, float duration)
	{
		this(displayName, kind, points, rollTurns, yawTurns, duration, 0f, 0f, 0f);
	}

	Trick(String displayName, TrickKind kind, int points, float rollTurns, float yawTurns, float duration,
		float pitchTurns, float bodyYawTurns)
	{
		this(displayName, kind, points, rollTurns, yawTurns, duration, pitchTurns, bodyYawTurns, 0f);
	}

	/** A body flip of {@code bodyFlipTurns} whole turns. */
	Trick(String displayName, TrickKind kind, int points, float bodyFlipTurns)
	{
		this(displayName, kind, points, 0f, 0f, 0f, 0f, 0f, bodyFlipTurns);
	}

	Trick(String displayName, TrickKind kind, int points, float rollTurns, float yawTurns, float duration,
		float pitchTurns, float bodyYawTurns, float bodyFlipTurns)
	{
		this.bodyFlipTurns = bodyFlipTurns;
		this.displayName = displayName;
		this.kind = kind;
		this.points = points;
		this.rollTurns = rollTurns;
		this.yawTurns = yawTurns;
		this.duration = duration;
		this.pitchTurns = pitchTurns;
		this.bodyYawTurns = bodyYawTurns;
	}
}
