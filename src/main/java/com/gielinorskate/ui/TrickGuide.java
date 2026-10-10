package com.gielinorskate.ui;

import com.gielinorskate.Text;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.input.ButtonTricks;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.tricks.*;
import com.gielinorskate.tricks.Gesture.Direction;
import java.util.*;
import lombok.AllArgsConstructor;

/**
 * How to do each trick, for the Trick Book ({@link TrickBook}): its group, the mouse flick that does it (found by
 * asking {@link TrickCatalog}, so it can never drift from the real mapping), a plain-language "how" line and its
 * points. Pure.
 */
public final class TrickGuide
{
	public enum Group
	{
		BASICS,
		FLIPS,
		/** Shift tricks. */
		SHIFT,
		AIR,
		/** Grinds and manuals. */
		GRINDS
	}

	/** One trick's entry. */
	@AllArgsConstructor
	public static final class Entry
	{
		public final Trick trick;
		public final Group group;
		/** The flick that does it, or null when it is not a single flick (grabs, grinds, doubles, ...). */
		public final Gesture gesture;
		/** "Pull down, flick up-left" and the like. */
		public final String how;
		/** Its keyboard-mode key ("1", "Shift+2"), or null. */
		public final String key;

		/** "300" for a flip; "200/s" for a hold, which scores per second. */
		public String points()
		{
			boolean hold = trick.kind == TrickKind.GRAB || trick.kind == TrickKind.MANUAL
				|| trick.kind == TrickKind.GRIND;
			return trick.points + (hold ? "/s" : "");
		}
	}

	/** Said of a trick the button layout has no button for. */
	static final String NOT_ON_BUTTONS = Text.get("guide.none");
	/** The fixed "how" lines by trick (mouse and keys; controller; button tricks) and the d-pad diagonals' words. */
	private static final Map<String, String> HOW = table(Text.lines("guide.how"));
	private static final Map<String, String> PAD_HOW = table(Text.lines("guide.padHow"));
	private static final Map<String, String> BUTTON_HOW = table(Text.lines("guide.buttonHow"));
	private static final Map<String, String> DIAGONALS = table(Text.lines("guide.diagonals"));

	/** The order the Trick Book names a trick's direction in: the stick's own first, then the d-pad's diagonals. */
	static final Direction[] BUTTON_DIRECTIONS = {Direction.LEFT, Direction.RIGHT, Direction.UP,
		Direction.DOWN, Direction.UP_LEFT, Direction.UP_RIGHT, Direction.DOWN_LEFT, Direction.DOWN_RIGHT};

	private TrickGuide()
	{
	}

	/** A bundled table, one "NAME|text" a line, by name. */
	static Map<String, String> table(List<String> lines)
	{
		Map<String, String> m = new HashMap<>();
		for (String line : lines)
		{
			int i = line.indexOf('|');
			m.put(line.substring(0, i), line.substring(i + 1));
		}
		return m;
	}

	public static Entry entry(Trick t)
	{
		Gesture g = gestureFor(t);
		return new Entry(t, groupOf(t, g), g, how(t, g), KeyboardTricks.keyFor(t));
	}

	static Group groupOf(Trick t, Gesture g)
	{
		switch (t.kind)
		{
			case POP:
				return Group.BASICS;
			case GRAB:
				return Group.AIR;
			case MANUAL:
			case GRIND:
				return Group.GRINDS;
			default:
				return t.bodyFlipTurns != 0f ? Group.AIR : g != null && g.modified ? Group.SHIFT : Group.FLIPS;
		}
	}

	/**
	 * The simplest flick that does {@code t}: plain before Shift, regular before nollie, straight before curved
	 * (path turns tried: straight, a varial curve, a full 360 curl). Null when no single flick does it.
	 */
	public static Gesture gestureFor(Trick t)
	{
		for (boolean modified : new boolean[]{false, true})
		{
			for (boolean nollie : new boolean[]{false, true})
			{
				for (float turn : new float[]{0f, 90f, 180f})
				{
					for (Direction d : Direction.values())
					{
						Gesture g = new Gesture(d, nollie, turn, modified);
						if (TrickCatalog.forGesture(g) == t)
							return g;
					}
				}
			}
		}
		return null;
	}

	/**
	 * The "how" line in controller mode, with drawn-button tokens (see {@link ControllerGlyphs}) naming the buttons
	 * of {@code preset} (an action it has no button for keeps its key). With the stick layouts the right stick
	 * flicks, the grab buttons grab (Q / E), the stick aims grabs and holds manuals, push is W, and the modifier
	 * (Shift) or a grab with the left stick up / down flips. S stays a key (there is no S on the pad).
	 */
	public static String controllerHow(Trick t, Gesture g, PadPreset preset)
	{
		return PadWords.resolve(preset.buttonTricks() ? buttonHowTemplate(t) : controllerHowTemplate(t, g), preset,
			"F");
	}

	/** A button trick's direction: "{LS} left", or for a diagonal the d-pad's two buttons. */
	static String buttonDirection(Direction d)
	{
		return DIAGONALS.getOrDefault(d.name(), "{LS} " + ButtonTricks.directionName(d));
	}

	/** The "how" line with button tricks (see {@link ButtonTricks}), with action placeholders. */
	private static String buttonHowTemplate(Trick t)
	{
		String fixed = BUTTON_HOW.get(t.name());
		if (fixed != null)
			return fixed;
		if (t.kind == TrickKind.GRAB && !Grabs.isTweaked(t))
		{
			for (Direction d : BUTTON_DIRECTIONS)
			{
				if (ButtonTricks.grab(d) == t)
					return "Hold {@grabBtn} + " + buttonDirection(d) + " in the air"
						+ (ButtonTricks.grab(null) == t ? " (or no direction)" : "");
			}
			return NOT_ON_BUTTONS;
		}
		if (t.kind == TrickKind.FLIP && t.bodyFlipTurns == 0f)
		{
			for (boolean hard : new boolean[]{false, true})
			{
				for (Direction d : BUTTON_DIRECTIONS)
				{
					if (ButtonTricks.flipTrick(d, hard) == t)
						return (hard ? "Hold {@mod}: " : "") + "{@flip} + " + buttonDirection(d)
							+ (!hard && ButtonTricks.flipTrick(null, false) == t ? " (or no direction)" : "");
				}
			}
			return NOT_ON_BUTTONS;
		}
		if (t.kind == TrickKind.POP)
			return NOT_ON_BUTTONS;
		// tweaks, body flips and grinds: as with the stick, the left stick up being W
		return controllerHowTemplate(t, null).replace("{@pushFirst}", "{LS} up");
	}

	private static String controllerHowTemplate(Trick t, Gesture g)
	{
		if (g != null)
		{
			String how = how(t, g);
			return "{RS} " + Character.toLowerCase(how.charAt(0)) + how.substring(1);
		}
		String fixed = PAD_HOW.get(t.name());
		return fixed != null ? fixed : how(t, null).replaceAll("\\bQ\\b", "{@grabL}").replaceAll("\\bE\\b", "{@grabR}")
			.replace("moving the mouse", "aiming {RS}").replace("holding W", "holding {@pushFirst}");
	}

	static String how(Trick t, Gesture g)
	{
		if (g != null)
		{
			// a varial curves the flick (60+ degrees of path turn), a 360 curls it (150+), as in TrickCatalog
			String s = (g.modified ? "Hold Shift: " : "") + (g.nollie ? "push up, " : "pull down, ")
				+ "flick " + ButtonTricks.directionName(g.direction)
				+ (g.turnDegrees >= 150f ? ", curling all the way round" : g.turnDegrees >= 60f ? ", curving it" : "");
			return Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}
		if (Grabs.isTweaked(t))
			return "Hold a " + Grabs.untweaked(t).displayName + " for over half a second";
		return HOW.getOrDefault(t.name(), "");
	}
}
