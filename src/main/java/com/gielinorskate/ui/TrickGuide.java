package com.gielinorskate.ui;

import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import com.gielinorskate.tricks.TrickKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How to do each trick, for the Trick Book ({@link TrickBook}): its group, the mouse flick that does it (found by
 * asking {@link TrickCatalog}, so it can never drift from the real mapping), a plain-language "how" line and its
 * points. Pure.
 */
public final class TrickGuide
{
	public enum Group
	{
		BASICS("Basics"),
		FLIPS("Flips"),
		SHIFT("Shift tricks"),
		AIR("Air"),
		GRINDS("Grinds & manuals");

		public final String title;

		Group(String title)
		{
			this.title = title;
		}
	}

	/** One trick's entry. */
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

		Entry(Trick trick, Group group, Gesture gesture, String how, String key)
		{
			this.trick = trick;
			this.group = group;
			this.gesture = gesture;
			this.how = how;
			this.key = key;
		}

		/** "300" for a flip; "200/s" for a hold, which scores per second. */
		public String points()
		{
			boolean hold = trick.kind == TrickKind.GRAB || trick.kind == TrickKind.MANUAL
				|| trick.kind == TrickKind.GRIND;
			return trick.points + (hold ? "/s" : "");
		}
	}

	/** Path turns tried when looking for a trick's flick: straight, a varial curve, a full 360 curl. */
	private static final float[] TURNS = {0f, 90f, 180f};

	private TrickGuide()
	{
	}

	/** Every trick, grouped in {@link Group} order, each group in catalogue order. */
	public static Map<Group, List<Entry>> groups()
	{
		Map<Group, List<Entry>> out = new EnumMap<>(Group.class);
		for (Group g : Group.values())
		{
			out.put(g, new ArrayList<>());
		}
		for (Trick t : Trick.values())
		{
			Entry e = entry(t);
			out.get(e.group).add(e);
		}
		for (Group g : Group.values())
		{
			out.put(g, Collections.unmodifiableList(out.get(g)));
		}
		return out;
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
				if (t.bodyFlipTurns != 0f)
				{
					return Group.AIR;
				}
				return g != null && g.modified ? Group.SHIFT : Group.FLIPS;
		}
	}

	/**
	 * The simplest flick that does {@code t}: plain before Shift, regular before nollie, straight before curved.
	 * Null when no single flick does it.
	 */
	public static Gesture gestureFor(Trick t)
	{
		for (boolean modified : new boolean[]{false, true})
		{
			for (boolean nollie : new boolean[]{false, true})
			{
				for (float turn : TURNS)
				{
					for (Direction d : Direction.values())
					{
						Gesture g = new Gesture(d, nollie, turn, modified);
						if (TrickCatalog.forGesture(g) == t)
						{
							return g;
						}
					}
				}
			}
		}
		return null;
	}

	static String directionName(Direction d)
	{
		return d.name().toLowerCase().replace('_', '-');
	}

	/**
	 * The "how" line in controller mode, with drawn-button tokens (see {@link ControllerGlyphs}): the right stick
	 * flicks, LT / RT grab (Q / E), the stick aims grabs and holds manuals, A pushes (W), and LB / RB (Shift) or a
	 * grab with the left stick up / down flips. S stays a key (there is no S on the pad). Skate 3's buttons.
	 */
	public static String controllerHow(Trick t, Gesture g)
	{
		return controllerHow(t, g, PadPreset.skate3());
	}

	/** As above, naming the buttons of {@code preset} (an action it has no button for keeps its key). */
	public static String controllerHow(Trick t, Gesture g, PadPreset preset)
	{
		if (preset.buttonTricks())
		{
			return PadWords.resolve(buttonHowTemplate(t), preset, "F");
		}
		return PadWords.resolve(controllerHowTemplate(t, g), preset, "F");
	}

	/** Said of a trick the button layout has no button for. */
	static final String NOT_ON_BUTTONS = "Not on the buttons: a mouse flick or a trick key";

	/** The order the Trick Book names a trick's direction in: the stick's own first, then the d-pad's diagonals. */
	private static final Direction[] BUTTON_DIRECTIONS = {Direction.LEFT, Direction.RIGHT, Direction.UP,
		Direction.DOWN, Direction.UP_LEFT, Direction.UP_RIGHT, Direction.DOWN_LEFT, Direction.DOWN_RIGHT};

	/** A button trick's direction: "{LS} left", or for a diagonal the d-pad's two buttons. */
	static String buttonDirection(Direction d)
	{
		switch (d)
		{
			case UP_LEFT:
				return "{DUP}{DLEFT} (d-pad up-left)";
			case UP_RIGHT:
				return "{DUP}{DRIGHT} (d-pad up-right)";
			case DOWN_LEFT:
				return "{DDOWN}{DLEFT} (d-pad down-left)";
			case DOWN_RIGHT:
				return "{DDOWN}{DRIGHT} (d-pad down-right)";
			default:
				return "{LS} " + directionName(d);
		}
	}

	/** The "how" line with button tricks (see com.gielinorskate.input.ButtonTricks), with action placeholders. */
	private static String buttonHowTemplate(Trick t)
	{
		switch (t)
		{
			case OLLIE:
				return "Hold {@ollie} to crouch, let go to pop";
			case DOUBLE_KICKFLIP:
				return "Kickflip, then {@flip} again mid-flip";
			case DOUBLE_HEELFLIP:
				return "Heelflip, then {@flip} again mid-flip";
			case TRIPLE_KICKFLIP:
				return "Double kickflip, then {@flip} again";
			case TRIPLE_HEELFLIP:
				return "Double heelflip, then {@flip} again";
			case MANUAL:
				return "{LS} up, then down within 0.3 s while rolling: it holds until you pop";
			case NOSE_MANUAL:
				return "{LS} down, then up within 0.3 s while rolling";
			case FRONTFLIP:
				return "Hold {@mod} and {LS} up in the air (or hold a grab and {LS} up)";
			case BACKFLIP:
				return "Hold {@mod} and {LS} down in the air (or hold a grab and {LS} down)";
			case DOUBLE_FRONTFLIP:
				return "Hold {@mod} and {LS} up through a big air";
			case DOUBLE_BACKFLIP:
				return "Hold {@mod} and {LS} down through a big air";
			default:
				break;
		}
		if (t.kind == TrickKind.GRAB)
		{
			if (Grabs.isTweaked(t))
			{
				return "Hold a " + Grabs.untweaked(t).displayName + " for over half a second";
			}
			for (Direction d : BUTTON_DIRECTIONS)
			{
				if (com.gielinorskate.input.ButtonTricks.grab(d) == t)
				{
					return "Hold {@grabBtn} + " + buttonDirection(d) + " in the air"
						+ (com.gielinorskate.input.ButtonTricks.grab(null) == t ? " (or no direction)" : "");
				}
			}
			return NOT_ON_BUTTONS;
		}
		if (t.kind == TrickKind.FLIP && t.bodyFlipTurns == 0f)
		{
			for (boolean hard : new boolean[]{false, true})
			{
				for (Direction d : BUTTON_DIRECTIONS)
				{
					if (com.gielinorskate.input.ButtonTricks.flipTrick(d, hard) == t)
					{
						return (hard ? "Hold {@mod}: " : "") + "{@flip} + " + buttonDirection(d)
							+ (!hard && com.gielinorskate.input.ButtonTricks.flipTrick(null, false) == t
							? " (or no direction)" : "");
					}
				}
			}
			return NOT_ON_BUTTONS;
		}
		if (t.kind == TrickKind.POP)
		{
			return NOT_ON_BUTTONS;
		}
		// grinds: as with the stick, the left stick up being W
		return controllerHowTemplate(t, null).replace("{@pushFirst}", "{LS} up");
	}

	private static String controllerHowTemplate(Trick t, Gesture g)
	{
		if (g != null)
		{
			return "{RS} " + Character.toLowerCase(how(t, g).charAt(0)) + how(t, g).substring(1);
		}
		switch (t)
		{
			case MANUAL:
				return "Hold {RS} tilted up a little while rolling";
			case NOSE_MANUAL:
				return "Hold {RS} tilted down a little while rolling";
			case FRONTFLIP:
				return "Hold {@mod} and {LS} up in the air (or hold a grab and {LS} up)";
			case BACKFLIP:
				return "Hold {@mod} and {LS} down in the air (or hold a grab and {LS} down)";
			case DOUBLE_FRONTFLIP:
				return "Hold {@mod} and {LS} up through a big air";
			case DOUBLE_BACKFLIP:
				return "Hold {@mod} and {LS} down through a big air";
			default:
				break;
		}
		String s = how(t, null);
		s = s.replaceAll("\\bQ\\b", "{@grabL}").replaceAll("\\bE\\b", "{@grabR}");
		s = s.replace("moving the mouse", "aiming {RS}");
		s = s.replace("holding W", "holding {@pushFirst}");
		return s;
	}

	static String how(Trick t, Gesture g)
	{
		if (g != null)
		{
			String s = (g.modified ? "Hold Shift: " : "") + (g.nollie ? "push up, " : "pull down, ")
				+ "flick " + directionName(g.direction);
			if (g.turnDegrees >= GestureGlyph.FULL_TURN)
			{
				s += ", curling all the way round";
			}
			else if (g.turnDegrees >= GestureGlyph.VARIAL_TURN)
			{
				s += ", curving it";
			}
			return Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}
		switch (t)
		{
			case DOUBLE_KICKFLIP:
				return "Kickflip, then flick up-left again";
			case DOUBLE_HEELFLIP:
				return "Heelflip, then flick up-right again";
			case TRIPLE_KICKFLIP:
				return "Double kickflip, then flick up-left again";
			case TRIPLE_HEELFLIP:
				return "Double heelflip, then flick up-right again";
			case INDY:
				return "Hold Q in the air (or E, moving the mouse to the toe side)";
			case MELON:
				return "Hold E in the air (or Q, moving the mouse to the heel side)";
			case MUTE:
				return "Hold Q in the air, moving the mouse to the toe side";
			case STALEFISH:
				return "Hold E in the air, moving the mouse to the heel side";
			case NOSEGRAB:
				return "Hold Q in the air, moving the mouse up";
			case TAILGRAB:
				return "Hold Q or E in the air, moving the mouse down";
			case CRAIL:
				return "Hold E in the air, moving the mouse up";
			case METHOD:
			case TWEAKED_INDY:
			case JAPAN:
			case TWEAKED_STALEFISH:
			case NOSEBONE:
			case TAILBONE:
			case CRAIL_TWEAK:
				return "Hold a " + Grabs.untweaked(t).displayName + " for over half a second";
			case FRONTFLIP:
				return "Hold Shift+W in the air";
			case BACKFLIP:
				return "Hold Shift+S in the air";
			case DOUBLE_FRONTFLIP:
				return "Hold Shift+W through a big air";
			case DOUBLE_BACKFLIP:
				return "Hold Shift+S through a big air";
			case MANUAL:
				return "Hold the wheelie key while rolling";
			case NOSE_MANUAL:
				return "Hold the wheelie key and W";
			case FIFTY_FIFTY:
				return "Land along a rail or edge";
			case NOSEGRIND:
				return "Land along a rail, holding W";
			case FIVE_O:
				return "Land along a rail, holding S";
			case CROOKED:
				return "Land a little across a rail";
			case FEEBLE:
				return "Land a little across a rail, holding W";
			case SMITH:
				return "Land a little across a rail, holding S";
			case BOARDSLIDE:
				return "Land across a rail";
			case NOSESLIDE:
				return "Land across a rail, holding W";
			case TAILSLIDE:
				return "Land across a rail, holding S";
			case LIPSLIDE:
				return "Land across a rail, turned past square";
			default:
				return "";
		}
	}
}
