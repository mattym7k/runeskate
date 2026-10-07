package com.gielinorskate.tricks;

import com.gielinorskate.tricks.Gesture.Direction;

/** Maps a recognized {@link Gesture} to the {@link Trick} it performs. */
public final class TrickCatalog
{
	private static final float VARIAL_TURN = 60f;
	private static final float TRIPLE_TURN = 150f;

	private TrickCatalog()
	{
	}

	/** Returns the trick for a fresh gesture, or null when nothing matches. */
	public static Trick forGesture(Gesture g)
	{
		if (g.modified)
		{
			Trick hard = modifiedTrick(g);
			if (hard != null)
			{
				return hard;
			}
		}
		if (g.nollie)
		{
			switch (g.direction)
			{
				case DOWN:
					return Trick.NOLLIE;
				case DOWN_LEFT:
					if (g.turnDegrees >= TRIPLE_TURN)
					{
						return Trick.NOLLIE_TRE_FLIP;
					}
					return Trick.NOLLIE_KICKFLIP;
				case DOWN_RIGHT:
					return Trick.NOLLIE_HEELFLIP;
				case LEFT:
					return Trick.NOLLIE_SHOVE_IT;
				case RIGHT:
					return Trick.NOLLIE_FS_SHOVE_IT;
				default:
					return null;
			}
		}

		switch (g.direction)
		{
			case UP:
				return Trick.OLLIE;
			case UP_LEFT:
				if (g.turnDegrees >= TRIPLE_TURN)
				{
					return Trick.TRE_FLIP;
				}
				if (g.turnDegrees >= VARIAL_TURN)
				{
					return Trick.VARIAL_KICKFLIP;
				}
				return Trick.KICKFLIP;
			case UP_RIGHT:
				if (g.turnDegrees >= TRIPLE_TURN)
				{
					return Trick.LASER_FLIP;
				}
				if (g.turnDegrees >= VARIAL_TURN)
				{
					return Trick.VARIAL_HEELFLIP;
				}
				return Trick.HEELFLIP;
			case LEFT:
				return g.turnDegrees >= TRIPLE_TURN ? Trick.SHOVE_IT_360 : Trick.POP_SHOVE_IT;
			case RIGHT:
				return g.turnDegrees >= TRIPLE_TURN ? Trick.FS_SHOVE_IT_360 : Trick.FS_POP_SHOVE_IT;
			default:
				return null;
		}
	}

	/**
	 * The Shift form of a flick, whatever its curve: ollie -> impossible, kickflip family -> hardflip, heelflip
	 * family -> inward heelflip, shove-its -> bigspins, and the nollie kickflip / heelflip -> nollie hardflip /
	 * nollie inward heelflip. Null when the flick has no Shift form (it is then the plain trick).
	 */
	private static Trick modifiedTrick(Gesture g)
	{
		if (g.nollie)
		{
			switch (g.direction)
			{
				case DOWN_LEFT:
					return Trick.NOLLIE_HARDFLIP;
				case DOWN_RIGHT:
					return Trick.NOLLIE_INWARD_HEELFLIP;
				default:
					return null;
			}
		}
		switch (g.direction)
		{
			case UP:
				return Trick.IMPOSSIBLE;
			case UP_LEFT:
				return Trick.HARDFLIP;
			case UP_RIGHT:
				return Trick.INWARD_HEELFLIP;
			case LEFT:
				return Trick.BIGSPIN;
			case RIGHT:
				return Trick.FS_BIGSPIN;
			default:
				return null;
		}
	}

	/** Returns the upgraded trick for a quick re-flick following {@code current}, or null. */
	public static Trick upgrade(Trick current, Gesture g)
	{
		if (current == Trick.KICKFLIP && g.direction == Direction.UP_LEFT)
		{
			return Trick.DOUBLE_KICKFLIP;
		}
		if (current == Trick.HEELFLIP && g.direction == Direction.UP_RIGHT)
		{
			return Trick.DOUBLE_HEELFLIP;
		}
		if (current == Trick.DOUBLE_KICKFLIP && g.direction == Direction.UP_LEFT)
		{
			return Trick.TRIPLE_KICKFLIP;
		}
		if (current == Trick.DOUBLE_HEELFLIP && g.direction == Direction.UP_RIGHT)
		{
			return Trick.TRIPLE_HEELFLIP;
		}
		return null;
	}
}
