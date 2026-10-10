package com.gielinorskate.tricks;

import static com.gielinorskate.tricks.Trick.*;

import java.util.Arrays;
import java.util.List;

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
	/** Each plain grab, and at the same index its tweak. */
	private static final List<Trick> PLAIN = Arrays.asList(MELON, INDY, MUTE, STALEFISH, NOSEGRAB, TAILGRAB, CRAIL);
	private static final List<Trick> TWEAKS = Arrays.asList(METHOD, TWEAKED_INDY, JAPAN, TWEAKED_STALEFISH, NOSEBONE,
		TAILBONE, CRAIL_TWEAK);

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
			return left ? INDY : MELON;
		switch (aim)
		{
			case NOSE:
				return left ? NOSEGRAB : CRAIL;
			case TOE:
				return left ? MUTE : INDY;
			case HEEL:
				return left ? MELON : STALEFISH;
			default:
				return TAILGRAB;
		}
	}

	/** The tweak of {@code grab}, or null when it has none (a tweak already, or not a grab). */
	public static Trick tweaked(Trick grab)
	{
		return swap(grab, PLAIN, TWEAKS);
	}

	/** True for the tweaked form of a grab. */
	public static boolean isTweaked(Trick t)
	{
		return TWEAKS.contains(t);
	}

	/** The plain grab {@code t} is the tweak of, or null when it is not a tweak. */
	public static Trick untweaked(Trick t)
	{
		return swap(t, TWEAKS, PLAIN);
	}

	private static Trick swap(Trick t, List<Trick> from, List<Trick> to)
	{
		int i = from.indexOf(t);
		return i < 0 ? null : to.get(i);
	}
}
