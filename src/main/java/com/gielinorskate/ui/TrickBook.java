package com.gielinorskate.ui;

import com.gielinorskate.camera.BoardOrbit;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.input.ButtonTricks;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.leaderboard.TimedRun;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.SessionGoals;
import com.gielinorskate.progression.SkateLevels;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.SpinNames;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The Trick Book's content: every move, grouped into sections, worded for the player's settings (keys, flick
 * button, mirrored flicks, keyboard tricks, controller mode). Built from the sources of truth, so it cannot drift:
 * the {@link Trick} catalogue through {@link TrickGuide} (flicks, "how" lines, points), {@link KeyboardTricks}
 * (keys), {@link Grabs} (aims and tweaks), {@link SpinNames}, the scoring and progression constants and the
 * board designs' level ladder (designs.json). Pure.
 */
public final class TrickBook
{
	/** The settings the wording depends on. A value. */
	public static final class Settings
	{
		final String toggleKey;
		final String boardKey;
		final String manualKey;
		/** "right", "left" or "middle". */
		final String flickButton;
		final boolean mirror;
		final boolean mouseTricks;
		final boolean keyboardTricks;
		final boolean controller;
		/** The extra brake key's name, or null / "Not set" when there is none. */
		final String brakeKey;
		final String leanForwardKey;
		final String leanBackKey;
		/** The controller layout whose buttons the book names (and lists in its Controller section). */
		final PadPreset preset;
		/** The preset's name ("Skate 3", "Custom"). */
		final String presetName;

		public Settings(String toggleKey, String boardKey, String manualKey, String flickButton, boolean mirror,
			boolean mouseTricks, boolean keyboardTricks, boolean controller, String brakeKey, String leanForwardKey,
			String leanBackKey)
		{
			this(toggleKey, boardKey, manualKey, flickButton, mirror, mouseTricks, keyboardTricks, controller, brakeKey,
				leanForwardKey, leanBackKey, PadPreset.skate3(), "Skate 3");
		}

		public Settings(String toggleKey, String boardKey, String manualKey, String flickButton, boolean mirror,
			boolean mouseTricks, boolean keyboardTricks, boolean controller, String brakeKey, String leanForwardKey,
			String leanBackKey, PadPreset preset, String presetName)
		{
			this.preset = preset;
			this.presetName = presetName;
			this.toggleKey = toggleKey;
			this.boardKey = boardKey;
			this.manualKey = manualKey;
			this.flickButton = flickButton;
			this.mirror = mirror;
			this.mouseTricks = mouseTricks;
			this.keyboardTricks = keyboardTricks;
			this.controller = controller;
			this.brakeKey = brakeKey;
			this.leanForwardKey = leanForwardKey;
			this.leanBackKey = leanBackKey;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Settings))
			{
				return false;
			}
			Settings s = (Settings) o;
			return mirror == s.mirror && mouseTricks == s.mouseTricks && keyboardTricks == s.keyboardTricks
				&& controller == s.controller && toggleKey.equals(s.toggleKey) && boardKey.equals(s.boardKey)
				&& manualKey.equals(s.manualKey) && flickButton.equals(s.flickButton)
				&& Objects.equals(brakeKey, s.brakeKey) && Objects.equals(leanForwardKey, s.leanForwardKey)
				&& Objects.equals(leanBackKey, s.leanBackKey) && preset.equals(s.preset)
				&& presetName.equals(s.presetName);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(toggleKey, boardKey, manualKey, flickButton, mirror, mouseTricks, keyboardTricks,
				controller, brakeKey, leanForwardKey, leanBackKey, preset, presetName);
		}
	}

	/** One move or fact: a name, what to do, and optionally its points, flick picture, key and pad buttons. */
	public static final class Entry
	{
		/** The catalogue trick this entry is, or null (a control, a spin, a rule). */
		public final Trick trick;
		public final String name;
		/** What to do, plain text. */
		public final String detail;
		/** "300", "200/s", "+150"; null when it scores nothing by itself. */
		public final String points;
		/** The flick to draw (already mirrored), or null. */
		public final Gesture gesture;
		/** Its keyboard-mode key ("1", "Shift+2"), or null. */
		public final String key;
		/** The pad buttons as glyph tokens ("{LT}"), to draw, or null. */
		public final String pad;

		Entry(Trick trick, String name, String detail, String points, Gesture gesture, String key, String pad)
		{
			this.trick = trick;
			this.name = name;
			this.detail = detail;
			this.points = points;
			this.gesture = gesture;
			this.key = key;
			this.pad = pad;
		}
	}

	/** A titled group of entries, with an optional intro line and an optional table (first row: headers). */
	public static final class Section
	{
		public final String title;
		/** A line under the title, or null. */
		public final String intro;
		public final List<Entry> entries;
		/** Rows of cells, the first the headers; empty when the section has no table. */
		public final List<List<String>> table;

		Section(String title, String intro, List<Entry> entries, List<List<String>> table)
		{
			this.title = title;
			this.intro = intro;
			this.entries = Collections.unmodifiableList(entries);
			this.table = Collections.unmodifiableList(table);
		}
	}

	private TrickBook()
	{
	}

	/** The whole book for {@code s}, in reading order. */
	public static List<Section> build(Settings s)
	{
		List<Section> out = new ArrayList<>();
		out.add(basics(s));
		out.add(flips(s, TrickGuide.Group.FLIPS, "Flip tricks", buttons(s)
			? "Hold a direction on the left stick (or the d-pad, for the diagonals) and press "
			+ ControllerGlyphs.plain(pad(s, "{@flip}")) + ", in the air or rolling (it pops straight into the flip)."
			: s.controller
			? "Pull the right stick down, then flick it (no button)."
			: s.mouseTricks ? "Hold the " + s.flickButton + " mouse button, drag down, then flick."
			: "Hold a trick key to crouch, let go to pop."));
		out.add(flips(s, TrickGuide.Group.SHIFT, "Hard flips (Shift)", s.controller
			? "Hold " + ControllerGlyphs.plain(pad(s, "{@mod}")) + " as the trick fires for the harder version."
			: "Hold Shift as the trick fires for the harder version."));
		out.add(spins(s));
		out.add(grabs(s));
		out.add(kind(s, TrickKind.MANUAL, "Manuals", "Balance on two wheels while rolling. Scores per second."));
		out.add(kind(s, TrickKind.GRIND, "Grinds",
			"Ollie onto a rail or ledge and land along it: the board locks on. Hold W or S as you land for the "
				+ "variations; jump to leave. \"Show grindable edges\" draws what you can grind. Scores per second."
				+ (buttons(s) ? " Hold " + ControllerGlyphs.plain(pad(s, "{@grind}")) + " near a rail to catch it "
				+ "from further away (rails still catch without it)." : "")));
		out.add(offTheBoard(s));
		out.add(controller(s));
		out.add(scoring());
		return out;
	}

	/** Controller mode with a button-trick layout (Tony Hawk's American Wasteland): tricks on buttons. */
	private static boolean buttons(Settings s)
	{
		return s.controller && s.preset.buttonTricks();
	}

	private static Entry control(String name, String detail)
	{
		return new Entry(null, name, detail, null, null, null, null);
	}

	/**
	 * A control, worded with keys, or with pad buttons in controller mode ({@code pad}'s action placeholders filled
	 * in from the preset, see {@link PadWords#resolve}).
	 */
	private static Entry control(Settings s, String name, String keys, String pad)
	{
		if (s.controller)
		{
			String text = pad(s, pad);
			return new Entry(null, name, ControllerGlyphs.plain(text), null, null, null, text);
		}
		return control(name, keys);
	}

	/** The preset's buttons in a pad text. */
	private static String pad(Settings s, String text)
	{
		return PadWords.resolve(text, s.preset, s.boardKey);
	}

	private static boolean hasKey(String key)
	{
		return key != null && !key.trim().isEmpty() && !"Not set".equalsIgnoreCase(key.trim());
	}

	/** "Up arrow" for the arrow keys, the key's own name otherwise. */
	static String keyName(String key)
	{
		switch (key)
		{
			case "Up":
			case "Down":
			case "Left":
			case "Right":
				return key + " arrow";
			default:
				return key;
		}
	}

	private static Section basics(Settings s)
	{
		List<Entry> e = new ArrayList<>();
		e.add(control(s, "Start and stop", "Stand still, then press " + s.toggleKey
			+ " (or Start skating in this panel). Esc stops.", "Stand still, then press " + s.toggleKey
			+ " (or Start skating in this panel). {@stop} stops."));
		boolean bt = buttons(s);
		e.add(control(s, "Push", "W, tap or hold (the Up arrow works too)",
			bt ? "{LS} up, tap or hold" : "{@push}, tap or hold"));
		e.add(control(s, "Carve", "A / D steer (or the arrow keys)", "{LS} left / right"));
		e.add(control(s, "Brake (powerslide)", "Hold Shift alone" + (hasKey(s.brakeKey) ? ", or " + s.brakeKey : ""),
			bt ? "Hold {@mod} alone" : "Hold {@brake}"));
		e.add(control(s, "Tight turn", "Hold Shift and steer: a sharp carve that scrubs speed",
			bt ? "Hold {@mod} and steer with {LS}" : "Hold {@brake} and steer with {LS}"));
		e.add(control(s, "Crouch", "Hold S. Crouching longer before a pop jumps higher", "Hold S"));
		e.add(trick(s, Trick.OLLIE));
		e.add(trick(s, Trick.NOLLIE));
		e.add(control(s, "Get up", "R after a bail: straight back on the board; or to stop rolling. Off in a Skate Duel",
			"{@reset} after a bail: straight back on the board; or to stop rolling. Off in a Skate Duel"));
		e.add(control(s, "Controls card", "H shows or hides it", "{@card} shows or hides it"));
		return new Section("Basics", null, e, Collections.emptyList());
	}

	/** A catalogue trick's entry, worded for the settings. */
	static Entry trick(Settings s, Trick t)
	{
		TrickGuide.Entry g = TrickGuide.entry(t);
		// with button tricks the flick pictures would mislead (the right stick turns the camera)
		Gesture gesture = g.gesture == null || !s.mouseTricks || buttons(s) ? null
			: s.mirror ? KeyboardTricks.mirror(g.gesture) : g.gesture;
		String key = s.keyboardTricks ? g.key : null;
		String pad = null;
		String detail;
		if (s.controller)
		{
			String how = TrickGuide.controllerHow(t, g.gesture, s.preset);
			detail = ControllerGlyphs.plain(how);
			pad = ControllerGlyphs.hasGlyphs(how) ? how : null;
		}
		else
		{
			detail = g.how;
		}
		if (!s.mouseTricks && !buttons(s) && (g.gesture != null || key != null))
		{
			// a flick trick (or a double) without flicks: its key is the way to do it
			detail = key != null ? "Press " + key : "";
			pad = null;
		}
		if (t.kind == TrickKind.MANUAL)
		{
			detail = detail.replace("the wheelie key", s.manualKey);
		}
		if (gesture != null && s.mirror)
		{
			detail = detail.replace(TrickGuide.directionName(g.gesture.direction),
				TrickGuide.directionName(gesture.direction));
		}
		return new Entry(t, t.displayName, detail, g.points(), gesture, key, pad);
	}

	private static Section flips(Settings s, TrickGuide.Group group, String title, String intro)
	{
		List<Entry> e = new ArrayList<>();
		for (TrickGuide.Entry g : TrickGuide.groups().get(group))
		{
			e.add(trick(s, g.trick));
		}
		if (group == TrickGuide.Group.FLIPS)
		{
			intro += buttons(s) ? " Press it again mid-flip for a double, then a triple."
				: " Flick again mid-flip for a double, then a triple.";
		}
		return new Section(title, intro, e, Collections.emptyList());
	}

	private static Section spins(Settings s)
	{
		List<Entry> e = new ArrayList<>();
		String how = buttons(s) ? "Turn with the left stick in the air (hold "
			+ ControllerGlyphs.plain(pad(s, "{@mod}")) + " to spin faster)"
			: s.controller ? "Turn with the left stick in the air" : "Turn with A / D in the air";
		for (int n = 1; n <= 4; n++)
		{
			e.add(new Entry(null, SpinNames.label(n) + " / " + SpinNames.label(-n), how, "+" + SpinNames.bonus(n),
				null, null, null));
		}
		for (Trick t : Trick.values())
		{
			if (t.bodyFlipTurns != 0f)
			{
				e.add(trick(s, t));
			}
		}
		return new Section("Spins & body flips", "Spin with any trick: the spin goes in front of its name "
			+ "(\"FS 180 Kickflip\"). Backside turns your back to where you are going first.", e,
			Collections.emptyList());
	}

	private static Section grabs(Settings s)
	{
		if (buttons(s))
		{
			return buttonGrabs(s);
		}
		String q = s.controller ? ControllerGlyphs.plain(pad(s, "{@grabL}")) : "Q";
		String e2 = s.controller ? ControllerGlyphs.plain(pad(s, "{@grabR}")) : "E";
		String aim = s.controller ? "right stick" : "mouse";
		String toe = s.mirror ? "Left" : "Right";
		String heel = s.mirror ? "Right" : "Left";
		List<List<String>> table = new ArrayList<>();
		table.add(Arrays.asList(s.controller ? "Aim (stick)" : "Aim (mouse)", q + " (left)", e2 + " (right)"));
		Map<String, Grabs.Aim> aims = new LinkedHashMap<>();
		aims.put("None", null);
		aims.put("Up (nose)", Grabs.Aim.NOSE);
		aims.put("Down (tail)", Grabs.Aim.TAIL);
		aims.put(toe + " (toe)", Grabs.Aim.TOE);
		aims.put(heel + " (heel)", Grabs.Aim.HEEL);
		for (Map.Entry<String, Grabs.Aim> a : aims.entrySet())
		{
			table.add(Arrays.asList(a.getKey(), Grabs.pick(true, a.getValue()).displayName,
				Grabs.pick(false, a.getValue()).displayName));
		}

		List<Entry> e = new ArrayList<>();
		for (Trick t : Trick.values())
		{
			if (t.kind == TrickKind.GRAB)
			{
				e.add(trick(s, t));
			}
		}
		String flip = s.controller
			? "Hold a grab, or " + ControllerGlyphs.plain(pad(s, "{@mod}"))
			+ ", and push the left stick up / down for a front / back flip."
			: "Hold a grab and press " + keyName(s.leanForwardKey) + " / " + keyName(s.leanBackKey)
			+ " for a front / back flip.";
		String intro = "In the air, hold " + q + " or " + e2 + " and move the " + aim + " just after to aim the grab. "
			+ "Held over " + Grabs.TWEAK_SECONDS + " s it turns into its tweak. " + flip + " Scores per second. "
			+ "Rolling, hold " + q + " or " + e2 + " to crouch and grab the board on the ground: style only (no points, "
			+ "no pushing while held). Pop with it held to grab in the air; land with it held to keep it.";
		return new Section("Grabs", intro, e, table);
	}

	/** Grabs with the grab button and a direction (button tricks). */
	private static Section buttonGrabs(Settings s)
	{
		String b = ControllerGlyphs.plain(pad(s, "{@grabBtn}"));
		List<List<String>> table = new ArrayList<>();
		table.add(Arrays.asList("Direction", b + " grab"));
		table.add(Arrays.asList("None", ButtonTricks.grab(null).displayName));
		for (Gesture.Direction d : new Gesture.Direction[]{Gesture.Direction.LEFT, Gesture.Direction.RIGHT,
			Gesture.Direction.UP, Gesture.Direction.DOWN, Gesture.Direction.UP_LEFT, Gesture.Direction.UP_RIGHT,
			Gesture.Direction.DOWN_LEFT, Gesture.Direction.DOWN_RIGHT})
		{
			String name = ButtonTricks.directionName(d);
			table.add(Arrays.asList(Character.toUpperCase(name.charAt(0)) + name.substring(1),
				ButtonTricks.grab(d).displayName));
		}
		List<Entry> e = new ArrayList<>();
		for (Trick t : Trick.values())
		{
			if (t.kind == TrickKind.GRAB)
			{
				e.add(trick(s, t));
			}
		}
		String intro = "In the air, hold a direction (the left stick, or the d-pad for the diagonals) and hold " + b
			+ ". Held over " + Grabs.TWEAK_SECONDS + " s it turns into its tweak. Hold a grab, or "
			+ ControllerGlyphs.plain(pad(s, "{@mod}")) + ", and push the left stick up / down for a front / back flip. "
			+ "Scores per second. Rolling, hold " + b + " to grab the board on the ground: style only.";
		return new Section("Grabs", intro, e, table);
	}

	private static Section kind(Settings s, TrickKind kind, String title, String intro)
	{
		List<Entry> e = new ArrayList<>();
		for (Trick t : Trick.values())
		{
			if (t.kind == kind)
			{
				e.add(trick(s, t));
			}
		}
		return new Section(title, intro, e, Collections.emptyList());
	}

	private static Section offTheBoard(Settings s)
	{
		String b = s.boardKey;
		List<Entry> e = new ArrayList<>();
		e.add(control(s, "Step off / get on", b + ": step off and carry the board; again to get on",
			"{@board}: step off and carry the board; again to get on"));
		e.add(control(s, "Walk", "WASD (W is away from the camera)", "{LS} (up is away from the camera)"));
		e.add(control(s, "Sprint", "Hold Shift", "Hold {@sprint}"));
		e.add(control(s, "Jump", "Space", "{@jump}"));
		e.add(control(s, "Drop or pick up", "Q / E: drop the board, or pick it up when close",
			"{@drop}: drop the board, or pick it up when close"));
		e.add(control(s, "Call it back", "Board far away: press " + b + " (no need to hold)",
			"Board far away: press {@board} (no need to hold)"));
		e.add(control(s, "Knocked off", "A bail knocks you off: a move key or Space gets you up sooner, " + b
			+ " gets the board back at once", "A bail knocks you off: {LS} or {@jump} gets you up sooner, {@board} gets the "
			+ "board back at once"));
		e.add(control(s, "Jump-mount", "Jump onto the board, or sprint and jump carrying it: you land rolling",
			"Jump onto the board, or sprint and jump carrying it: you land rolling"));
		e.add(buttons(s) ? control(s, "Turn the camera", "Middle mouse drag", "{RS}")
			: control("Turn the camera", "Middle mouse drag"));
		return new Section("Off the board", "No tricks or points on foot.", e, Collections.emptyList());
	}

	/**
	 * The pad layout of the controller preset (universal AntiMicroX profile), shown whether or not controller mode
	 * is on: the sticks, then each bound button with what it does.
	 */
	private static Section controller(Settings s)
	{
		List<Entry> e = new ArrayList<>();
		if (buttons(s))
		{
			e.add(pad("{LS}", "Steer; in the air, spin. Up: push. The direction for the trick buttons (the d-pad "
				+ "gives the diagonals). Up then down, quickly: manual (down then up: nose manual). On foot: walk"));
			e.add(pad("{RS}", "Turns the camera. On the board it eases back behind you " + BoardOrbit.IDLE_SECONDS
				+ " s after you let go"));
		}
		else
		{
			e.add(pad("{LS}", "Steer; in the air, spin. Up / down with a grab: flip. On foot: walk"));
			e.add(pad("{RS}", "Pull down, flick: tricks. Small tilt up / down, held: manual / nose manual. "
				+ "Aims grabs"));
		}
		for (Map.Entry<String, String> b : PadWords.layout(s.preset).entrySet())
		{
			e.add(pad(b.getKey(), b.getValue()));
		}
		return new Section("Controller", "Preset: " + s.presetName + ". Turn on Controller mode (RuneSkate section "
			+ "of the settings) and load the RuneSkate profile in AntiMicroX (side panel: Controller setup).", e,
			Collections.emptyList());
	}

	private static Entry pad(String tokens, String detail)
	{
		List<String> names = new ArrayList<>();
		for (Object piece : ControllerGlyphs.split(tokens))
		{
			if (piece instanceof ControllerGlyphs.Glyph)
			{
				names.add(ControllerGlyphs.name((ControllerGlyphs.Glyph) piece));
			}
		}
		return new Entry(null, String.join(" / ", names), detail, null, null, null, tokens);
	}

	private static String percent(float fraction)
	{
		return Math.round(fraction * 100f) + "%";
	}

	private static Section scoring()
	{
		List<Entry> e = new ArrayList<>();
		e.add(control("Combo", "Tricks, spins, grabs, grinds and manuals chained without stopping are one combo. "
			+ "It banks when you roll on without a new trick; a bail loses it."));
		e.add(control("Multiplier", "Each different trick in the combo adds 1 to the multiplier. The combo is worth "
			+ "its points times the multiplier."));
		e.add(control("Repeats", "Each repeat of a trick in a session is worth "
			+ percent(1f - ComboScorer.DECAY_PER_REPEAT) + " less, down to " + percent(ComboScorer.DECAY_FLOOR)
			+ " of its points."));
		e.add(control("Variety bonus", "+" + percent(ComboScorer.FIRST_LANDING_BONUS) + " for a trick's first "
			+ "landing in a session. A combo longer than " + Math.round(ComboScorer.LONG_COMBO_SECONDS)
			+ " s gets one more multiplier."));
		e.add(control("Clean landing", "+" + percent(ComboScorer.CLEAN_BONUS) + " for landing lined up with the "
			+ "board."));
		e.add(control("Skating XP", "A landed combo gives its value / " + SkateLevels.COMBO_VALUE_PER_XP
			+ " as Skating XP. Levels go to " + SkateLevels.MAX_LEVEL + "."));
		e.add(control("Daily goals", SessionGoals.GOALS_PER_SESSION + " goals each UTC day; each one done is worth "
			+ String.format("%,d", SessionGoals.BONUS_XP) + " XP."));
		e.add(control("Board designs", designLadder(BoardDesigns.bundled()) + ". Bronze to Torva each come as a grip, "
			+ "a deck and wheels; mix them freely under Board in this panel."));
		int minutes = Math.round(TimedRun.RUN_SECONDS / 60f);
		e.add(control("Timed run", "Start " + minutes + "-minute run in this panel while skating: land as much as you "
			+ "can in " + minutes + " minutes. Bails don't end it."));
		e.add(control("Leaderboards", "Opt in with \"Submit scores to the leaderboard\" (RuneSkate settings): this "
			+ "week's best combo, timed runs and Skating XP are ranked in the Leaderboards section."));
		return new Section("Scoring & levels", null, e, Collections.emptyList());
	}

	/** "Level 1: RuneSkate, Classic black, ... Level 99: Torva": each design name once, at its level, in order. */
	static String designLadder(BoardDesigns designs)
	{
		Map<Integer, List<String>> ladder = new TreeMap<>();
		for (BoardDesign d : designs.all())
		{
			List<String> names = ladder.computeIfAbsent(d.unlock, k -> new ArrayList<>());
			if (!names.contains(d.name))
			{
				names.add(d.name);
			}
		}
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, List<String>> rung : ladder.entrySet())
		{
			out.append(out.length() > 0 ? ". " : "").append("Level ").append(rung.getKey()).append(": ")
				.append(String.join(", ", rung.getValue()));
		}
		return out.toString();
	}
}
