package com.gielinorskate.input;

import static com.gielinorskate.tricks.Gesture.Direction.*;

import com.gielinorskate.tricks.*;
import com.gielinorskate.tricks.Gesture.Direction;

/**
 * Button tricks (the Tony Hawk's American Wasteland preset): a flip or grab button with a direction held on the left
 * stick or the d-pad. Each flip is turned into the {@link Gesture} its flick (or trick key) would make, so physics,
 * {@link TrickCatalog}, scoring and the animations treat it exactly like that flick; a second press mid-flip is a
 * re-flick (a double, then a triple). Each grab is a hand and an aim for {@link Grabs#pick}, so it is the same grab,
 * with the same tweak, as Q / E aimed with the mouse. Pure.
 *
 * <p>Directions are in stick terms: up is the stick pushed away from the player. With the hard tricks modifier held
 * a flip is the hard version, as a Shift flick is (kickflip: hardflip, heelflip: inward heelflip, shove-it: bigspin).
 */
public final class ButtonTricks
{
	/** How long a flip or grab press waits for its direction (two d-pad buttons for a diagonal never come at once). */
	public static final long DIRECTION_WAIT_MS = 50;

	private ButtonTricks()
	{
	}

	/** The direction held (opposites cancel), or null when none. */
	public static Direction direction(boolean up, boolean down, boolean left, boolean right)
	{
		int v = (up ? 1 : 0) - (down ? 1 : 0);
		int h = (right ? 1 : 0) - (left ? 1 : 0);
		if (v > 0)
			return h < 0 ? UP_LEFT : h > 0 ? UP_RIGHT : UP;
		if (v < 0)
			return h < 0 ? DOWN_LEFT : h > 0 ? DOWN_RIGHT : DOWN;
		return h < 0 ? LEFT : h > 0 ? RIGHT : null;
	}

	/**
	 * The flick a flip button press stands for: left kickflip, right heelflip, up impossible (the Shift ollie), down
	 * pop shove-it, up-left / up-right varial kickflip / heelflip, down-left 360 flip, down-right hardflip (the Shift
	 * kickflip), none a kickflip.
	 *
	 * @param modified the hard tricks modifier is held as the trick fires
	 */
	public static Gesture flip(Direction d, boolean modified)
	{
		Direction flick = d == UP ? UP : d == DOWN ? LEFT : d == RIGHT || d == UP_RIGHT ? UP_RIGHT : UP_LEFT;
		float turn = d == UP_LEFT || d == UP_RIGHT ? KeyboardTricks.VARIAL_TURN
			: d == DOWN_LEFT ? KeyboardTricks.FULL_TURN : 0f;
		return new Gesture(flick, false, turn, modified || d == UP || d == DOWN_RIGHT);
	}

	/** The trick a flip button press does (as a fresh flip; mid-flip see {@link TrickCatalog#upgrade}). */
	public static Trick flipTrick(Direction d, boolean modified)
	{
		return TrickCatalog.forGesture(flip(d, modified));
	}

	/**
	 * The hand a grab button grabs with: the left (front) hand for melon, nosegrab, tailgrab, mute and the default
	 * indy; the right (back) hand for indy, crail and stalefish. With {@link #grabAim} it picks the grab.
	 */
	public static boolean grabLeftHand(Direction d)
	{
		return d != RIGHT && d != UP_LEFT && d != DOWN_LEFT && d != DOWN_RIGHT;
	}

	/**
	 * Where a grab button's grab is aimed: left melon, right indy, up nosegrab, down tailgrab, up-left crail, up-right
	 * mute, down-left stalefish, down-right indy, none the default indy. The aim never moves after the press.
	 */
	public static Grabs.Aim grabAim(Direction d)
	{
		return d == null ? null : d == LEFT || d == DOWN_LEFT ? Grabs.Aim.HEEL
			: d == RIGHT || d == UP_RIGHT || d == DOWN_RIGHT ? Grabs.Aim.TOE
			: d == UP || d == UP_LEFT ? Grabs.Aim.NOSE : Grabs.Aim.TAIL;
	}

	/** The grab a grab button press does (held past half a second it turns into its tweak, as always). */
	public static Trick grab(Direction d)
	{
		return Grabs.pick(grabLeftHand(d), grabAim(d));
	}

	/** The direction's name for the Trick Book and the controls card ("up-left"), or "no direction". */
	public static String directionName(Direction d)
	{
		return d == null ? "no direction" : d.name().toLowerCase().replace('_', '-');
	}
}
