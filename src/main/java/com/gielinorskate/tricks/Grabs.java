package com.gielinorskate.tricks;

/**
 * Which grab a grab key picks, and its tweak. Pure.
 *
 * <p>A grab key held in the air grabs with one hand: Q the left (front) hand, E the right (back) hand. The way
 * the mouse (or right stick) moved just after the press aims it at a part of the board; with no aim Q is an
 * Indy and E a Melon, as before directional grabs. Held past {@link #TWEAK_SECONDS} a grab turns into its
 * tweak once.
 */
public final class Grabs
{
	/** Seconds after the grab key press during which an aim still picks the grab. */
	public static final float AIM_SECONDS = 0.15f;
	/** A grab held longer than this (seconds) turns into its tweak. */
	public static final float TWEAK_SECONDS = 0.5f;

	/** The part of the board a grab is aimed at, relative to the board and the rider's stance. */
	public enum Aim
	{
		/** Toward the nose (mouse up). */
		NOSE,
		/** Toward the tail (mouse down). */
		TAIL,
		/** Toward the toe (chest) side. */
		TOE,
		/** Toward the heel (back) side. */
		HEEL
	}

	private Grabs()
	{
	}

	/** The grab for the left hand (Q) when {@code left}, else the right hand (E), aimed at {@code aim} (null: none). */
	public static Trick pick(boolean left, Aim aim)
	{
		if (aim == null)
		{
			return left ? Trick.INDY : Trick.MELON;
		}
		switch (aim)
		{
			case NOSE:
				return left ? Trick.NOSEGRAB : Trick.CRAIL;
			case TOE:
				return left ? Trick.MUTE : Trick.INDY;
			case HEEL:
				return left ? Trick.MELON : Trick.STALEFISH;
			default:
				return Trick.TAILGRAB;
		}
	}

	/** The tweak of {@code grab}, or null when it has none (a tweak already, or not a grab). */
	public static Trick tweaked(Trick grab)
	{
		if (grab == null)
		{
			return null;
		}
		switch (grab)
		{
			case MELON:
				return Trick.METHOD;
			case INDY:
				return Trick.TWEAKED_INDY;
			case MUTE:
				return Trick.JAPAN;
			case STALEFISH:
				return Trick.TWEAKED_STALEFISH;
			case NOSEGRAB:
				return Trick.NOSEBONE;
			case TAILGRAB:
				return Trick.TAILBONE;
			case CRAIL:
				return Trick.CRAIL_TWEAK;
			default:
				return null;
		}
	}

	/** True for the tweaked form of a grab. */
	public static boolean isTweaked(Trick t)
	{
		return untweaked(t) != null;
	}

	/** The plain grab {@code t} is the tweak of, or null when it is not a tweak. */
	public static Trick untweaked(Trick t)
	{
		if (t == null)
		{
			return null;
		}
		switch (t)
		{
			case METHOD:
				return Trick.MELON;
			case TWEAKED_INDY:
				return Trick.INDY;
			case JAPAN:
				return Trick.MUTE;
			case TWEAKED_STALEFISH:
				return Trick.STALEFISH;
			case NOSEBONE:
				return Trick.NOSEGRAB;
			case TAILBONE:
				return Trick.TAILGRAB;
			case CRAIL_TWEAK:
				return Trick.CRAIL;
			default:
				return null;
		}
	}
}
