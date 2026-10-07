package com.gielinorskate.tricks;

import java.util.Objects;

/** A completed mouse-flick gesture, ready to be matched against {@link TrickCatalog}. */
public final class Gesture
{
	/** 8-way final flick direction, in screen terms (y grows downward). */
	public enum Direction
	{
		UP,
		UP_LEFT,
		LEFT,
		DOWN_LEFT,
		DOWN,
		DOWN_RIGHT,
		RIGHT,
		UP_RIGHT
	}

	public final Direction direction;
	/** True when the path started by pushing up rather than pulling down. */
	public final boolean nollie;
	/** Absolute accumulated path turning, in degrees, since wind-up. */
	public final float turnDegrees;
	/**
	 * True when Shift was held at the moment the flick fired: picks the harder form of the trick (hardflip,
	 * inward heelflip, impossible, bigspin; see {@link TrickCatalog#forGesture}).
	 */
	public final boolean modified;

	public Gesture(Direction direction, boolean nollie, float turnDegrees)
	{
		this(direction, nollie, turnDegrees, false);
	}

	public Gesture(Direction direction, boolean nollie, float turnDegrees, boolean modified)
	{
		this.direction = direction;
		this.nollie = nollie;
		this.turnDegrees = turnDegrees;
		this.modified = modified;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Gesture))
		{
			return false;
		}
		Gesture g = (Gesture) o;
		return nollie == g.nollie && Float.compare(turnDegrees, g.turnDegrees) == 0 && direction == g.direction
			&& modified == g.modified;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(direction, nollie, turnDegrees, modified);
	}

	@Override
	public String toString()
	{
		return "Gesture{direction=" + direction + ", nollie=" + nollie + ", turnDegrees=" + turnDegrees
			+ (modified ? ", modified" : "") + '}';
	}
}
