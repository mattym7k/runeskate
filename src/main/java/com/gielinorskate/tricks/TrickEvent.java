package com.gielinorskate.tricks;

import lombok.*;

/** A trick-related occurrence drained from {@code SkatePhysics} for scoring/UI/sound. */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public final class TrickEvent
{
	public enum Type
	{
		TRICK,
		HOLD_START,
		HOLD_END,
		LANDED,
		BAILED,
		/**
		 * The air just landed had a body spin of {@link #spinHalfTurns} half turns (see {@link SpinNames}).
		 * Emitted before that landing's LANDED (or, landing into a manual, before its HOLD_START); the scorer
		 * renames the air's trick ("BS 180 Kickflip") or adds the spin on its own ("FS 540"). Trick is null.
		 */
		SPIN
	}

	public final Type type;
	/** Null for LANDED/BAILED. */
	public final Trick trick;
	/** Hold length in seconds for HOLD_END, else 0. */
	public final float seconds;
	/**
	 * For a TRICK that upgrades the flip in progress (e.g. KICKFLIP -> DOUBLE_KICKFLIP): the trick it
	 * replaces, which the scorer swaps out instead of counting both. Null otherwise.
	 */
	public final Trick upgradedFrom;
	/** For a TRICK: popped while riding fakie (backwards), so it is named "Fakie ...". */
	public final boolean fakie;
	/**
	 * For a LANDED: the landing was clean (board within 15 degrees of the travel, flip finished), which earns
	 * the scorer's clean bonus. Carried on the event so it describes this landing, not whatever the physics
	 * state is when the frame's events are read. False otherwise.
	 */
	public final boolean clean;
	/** For a SPIN: signed half turns, + = clockwise from above (backside for the regular stance). 0 otherwise. */
	public final int spinHalfTurns;

	private static TrickEvent of(Type type, Trick trick, float seconds)
	{
		return new TrickEvent(type, trick, seconds, null, false, false, 0);
	}

	public static TrickEvent trick(Trick trick)
	{
		return trick(trick, false);
	}

	/** A TRICK event; {@code fakie} when it was popped riding backwards. */
	public static TrickEvent trick(Trick trick, boolean fakie)
	{
		return upgrade(trick, null, fakie);
	}

	public static TrickEvent holdStart(Trick trick)
	{
		return of(Type.HOLD_START, trick, 0f);
	}

	public static TrickEvent holdEnd(Trick trick, float seconds)
	{
		return of(Type.HOLD_END, trick, seconds);
	}

	/** A TRICK event for {@code trick} that replaces the flip {@code from} already in progress. */
	public static TrickEvent upgrade(Trick trick, Trick from)
	{
		return upgrade(trick, from, false);
	}

	/** As {@link #upgrade(Trick, Trick)}, for a flip popped riding fakie when {@code fakie}. */
	public static TrickEvent upgrade(Trick trick, Trick from, boolean fakie)
	{
		return new TrickEvent(Type.TRICK, trick, 0f, from, fakie, false, 0);
	}

	/** A LANDED event for a landing that was not clean. */
	public static TrickEvent landed()
	{
		return landed(false);
	}

	/** A LANDED event; {@code clean} when the landing was clean. */
	public static TrickEvent landed(boolean clean)
	{
		return new TrickEvent(Type.LANDED, null, 0f, null, false, clean, 0);
	}

	public static TrickEvent bailed()
	{
		return of(Type.BAILED, null, 0f);
	}

	/** A SPIN event of {@code halfTurns} signed half turns (+ = clockwise from above). */
	public static TrickEvent spin(int halfTurns)
	{
		return new TrickEvent(Type.SPIN, null, 0f, null, false, false, halfTurns);
	}

	/** The name shown for this event's trick: "Fakie " + the trick's name when popped fakie. */
	public String displayName()
	{
		return trick == null ? "" : fakie ? "Fakie " + trick.displayName : trick.displayName;
	}
}
