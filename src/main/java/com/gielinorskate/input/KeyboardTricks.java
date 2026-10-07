package com.gielinorskate.input;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import java.awt.event.KeyEvent;

/**
 * The "Keyboard" trick controls: each key stands for one mouse flick, turned into the same {@link Gesture} the
 * recognizer would produce, so physics and {@link com.gielinorskate.tricks.TrickCatalog} treat both alike.
 *
 * <ul>
 * <li>Space: ollie. 1: kickflip, 2: heelflip, 3: pop shove-it, 4: FS pop shove-it.</li>
 * <li>5 / 6: varial kickflip / heelflip, 7 / 8: 360 flip / laser flip, 9 / 0: 360 / FS 360 shove-it.</li>
 * <li>Shift held: the hard version (impossible, hardflip, inward heelflip, bigspins), as with a Shift flick.</li>
 * <li>Alt held when the key goes down: the nollie version (the flick mirrored top to bottom).</li>
 * <li>The same key again mid-flip: double (then triple) kickflip / heelflip, as a mouse re-flick.</li>
 * </ul>
 * The number-pad digits work too. Pure.
 */
public final class KeyboardTricks
{
	/** Path turn, in degrees, standing in for a curved flick: a varial (60-150) and a full 360 (150+). */
	static final float VARIAL_TURN = 90f;
	static final float FULL_TURN = 180f;

	private KeyboardTricks()
	{
	}

	/** True when {@code code} does a trick in keyboard mode (so it cannot be the wheelie key then). */
	public static boolean isTrickKey(int code)
	{
		return flick(code) != null;
	}

	/**
	 * The gesture for a trick key, or null when {@code code} is not one.
	 *
	 * @param nollie Alt was held as the key went down
	 * @param modified Shift is held as the trick fires
	 */
	public static Gesture gestureFor(int code, boolean nollie, boolean modified)
	{
		Flick f = flick(code);
		if (f == null)
		{
			return null;
		}
		return new Gesture(nollie ? flipVertically(f.direction) : f.direction, nollie, f.turn, modified);
	}

	/** A regular (not nollie) flick: its direction and path turn. */
	private static final class Flick
	{
		final Direction direction;
		final float turn;

		Flick(Direction direction, float turn)
		{
			this.direction = direction;
			this.turn = turn;
		}
	}

	/** The regular flick a key stands for, or null. */
	private static Flick flick(int code)
	{
		switch (code)
		{
			case KeyEvent.VK_SPACE:
				return new Flick(Direction.UP, 0f);
			case KeyEvent.VK_1:
			case KeyEvent.VK_NUMPAD1:
				return new Flick(Direction.UP_LEFT, 0f);
			case KeyEvent.VK_2:
			case KeyEvent.VK_NUMPAD2:
				return new Flick(Direction.UP_RIGHT, 0f);
			case KeyEvent.VK_3:
			case KeyEvent.VK_NUMPAD3:
				return new Flick(Direction.LEFT, 0f);
			case KeyEvent.VK_4:
			case KeyEvent.VK_NUMPAD4:
				return new Flick(Direction.RIGHT, 0f);
			case KeyEvent.VK_5:
			case KeyEvent.VK_NUMPAD5:
				return new Flick(Direction.UP_LEFT, VARIAL_TURN);
			case KeyEvent.VK_6:
			case KeyEvent.VK_NUMPAD6:
				return new Flick(Direction.UP_RIGHT, VARIAL_TURN);
			case KeyEvent.VK_7:
			case KeyEvent.VK_NUMPAD7:
				return new Flick(Direction.UP_LEFT, FULL_TURN);
			case KeyEvent.VK_8:
			case KeyEvent.VK_NUMPAD8:
				return new Flick(Direction.UP_RIGHT, FULL_TURN);
			case KeyEvent.VK_9:
			case KeyEvent.VK_NUMPAD9:
				return new Flick(Direction.LEFT, FULL_TURN);
			case KeyEvent.VK_0:
			case KeyEvent.VK_NUMPAD0:
				return new Flick(Direction.RIGHT, FULL_TURN);
			default:
				return null;
		}
	}

	/** A nollie flick goes the other way vertically: up becomes down; left and right stay. */
	static Direction flipVertically(Direction d)
	{
		switch (d)
		{
			case UP:
				return Direction.DOWN;
			case UP_LEFT:
				return Direction.DOWN_LEFT;
			case UP_RIGHT:
				return Direction.DOWN_RIGHT;
			case DOWN:
				return Direction.UP;
			case DOWN_LEFT:
				return Direction.UP_LEFT;
			case DOWN_RIGHT:
				return Direction.UP_RIGHT;
			default:
				return d;
		}
	}

	/**
	 * The key label for a trick in keyboard mode ("1", "Shift+1", "Alt+Space"), or null when no single key does
	 * it (grabs, grinds, doubles, ...). Used by the side panel's trick list.
	 */
	public static String keyFor(Trick trick)
	{
		switch (trick)
		{
			case OLLIE:
				return "Space";
			case NOLLIE:
				return "Alt+Space";
			case IMPOSSIBLE:
				return "Shift+Space";
			case KICKFLIP:
				return "1";
			case HEELFLIP:
				return "2";
			case POP_SHOVE_IT:
				return "3";
			case FS_POP_SHOVE_IT:
				return "4";
			case VARIAL_KICKFLIP:
				return "5";
			case VARIAL_HEELFLIP:
				return "6";
			case TRE_FLIP:
				return "7";
			case LASER_FLIP:
				return "8";
			case SHOVE_IT_360:
				return "9";
			case FS_SHOVE_IT_360:
				return "0";
			case NOLLIE_KICKFLIP:
				return "Alt+1";
			case NOLLIE_HEELFLIP:
				return "Alt+2";
			case NOLLIE_SHOVE_IT:
				return "Alt+3";
			case NOLLIE_FS_SHOVE_IT:
				return "Alt+4";
			case NOLLIE_TRE_FLIP:
				return "Alt+7";
			case HARDFLIP:
				return "Shift+1";
			case INWARD_HEELFLIP:
				return "Shift+2";
			case BIGSPIN:
				return "Shift+3";
			case FS_BIGSPIN:
				return "Shift+4";
			case NOLLIE_HARDFLIP:
				return "Shift+Alt+1";
			case NOLLIE_INWARD_HEELFLIP:
				return "Shift+Alt+2";
			case DOUBLE_KICKFLIP:
				return "1, 1";
			case DOUBLE_HEELFLIP:
				return "2, 2";
			case TRIPLE_KICKFLIP:
				return "1, 1, 1";
			case TRIPLE_HEELFLIP:
				return "2, 2, 2";
			default:
				return null;
		}
	}

	/** The mirror image (left and right swapped) of a mouse flick, for the "Mirror flicks" setting. */
	public static Gesture mirror(Gesture g)
	{
		return new Gesture(mirror(g.direction), g.nollie, g.turnDegrees, g.modified);
	}

	static Direction mirror(Direction d)
	{
		switch (d)
		{
			case LEFT:
				return Direction.RIGHT;
			case RIGHT:
				return Direction.LEFT;
			case UP_LEFT:
				return Direction.UP_RIGHT;
			case UP_RIGHT:
				return Direction.UP_LEFT;
			case DOWN_LEFT:
				return Direction.DOWN_RIGHT;
			case DOWN_RIGHT:
				return Direction.DOWN_LEFT;
			default:
				return d;
		}
	}
}
