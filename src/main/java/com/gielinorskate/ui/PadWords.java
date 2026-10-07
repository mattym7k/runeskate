package com.gielinorskate.ui;

import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Words for the pad's buttons under a preset, as glyph tokens ("{A} or {X}"), so the controls card, the Trick Book
 * and the side panel name the buttons the player's layout actually uses. Pure.
 */
public final class PadWords
{
	private PadWords()
	{
	}

	/** The drawn glyph for a pad button. */
	public static Glyph glyph(PadButton b)
	{
		switch (b)
		{
			case A:
				return Glyph.A;
			case B:
				return Glyph.B;
			case X:
				return Glyph.X;
			case Y:
				return Glyph.Y;
			case LB:
				return Glyph.LB;
			case RB:
				return Glyph.RB;
			case LT:
				return Glyph.LT;
			case RT:
				return Glyph.RT;
			case BACK:
				return Glyph.BACK;
			case START:
				return Glyph.START;
			case L3:
				return Glyph.L3;
			case R3:
				return Glyph.R3;
			case DPAD_UP:
				return Glyph.DUP;
			case DPAD_DOWN:
				return Glyph.DDOWN;
			case DPAD_LEFT:
				return Glyph.DLEFT;
			default:
				return Glyph.DRIGHT;
		}
	}

	/** A button's token: "{A}". */
	public static String token(PadButton b)
	{
		return ControllerGlyphs.token(glyph(b));
	}

	/** The buttons doing {@code action} in {@code context}, joined by {@code joiner}; null when none does. */
	public static String buttons(PadPreset p, PadAction action, PadContext context, String joiner)
	{
		List<PadButton> bs = p.buttonsFor(action, context);
		if (bs.isEmpty())
		{
			return null;
		}
		List<String> tokens = new ArrayList<>();
		for (PadButton b : bs)
		{
			tokens.add(token(b));
		}
		return String.join(joiner, tokens);
	}

	/** As {@link #buttons}, or {@code fallback} (the keyboard key, which still works) when no button does it. */
	public static String or(PadPreset p, PadAction action, PadContext context, String joiner, String fallback)
	{
		String s = buttons(p, action, context, joiner);
		return s != null ? s : fallback;
	}

	/** The first button doing {@code action} in {@code context}, or {@code fallback}. */
	public static String first(PadPreset p, PadAction action, PadContext context, String fallback)
	{
		List<PadButton> bs = p.buttonsFor(action, context);
		return bs.isEmpty() ? fallback : token(bs.get(0));
	}

	private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{@(\\w+)\\}");

	/**
	 * Fills in the action placeholders of a text with the preset's buttons: {@code {@push}} (push, "or"),
	 * {@code {@pushFirst}}, {@code {@brake}}, {@code {@mod}} (the hard tricks modifier, "or"), {@code {@modSlash}},
	 * {@code {@grabL}}, {@code {@grabR}}, {@code {@grabs}} ("{LT} / {RT}"), {@code {@reset}}, {@code {@card}},
	 * {@code {@stop}}, {@code {@board}}, and on foot {@code {@sprint}}, {@code {@jump}}, {@code {@drop}}. An action
	 * no button does is named by its keyboard key ({@code boardKey} for the board key).
	 */
	public static String resolve(String text, PadPreset p, String boardKey)
	{
		java.util.regex.Matcher m = PLACEHOLDER.matcher(text);
		StringBuffer out = new StringBuffer();
		while (m.find())
		{
			m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(placeholder(m.group(1), p, boardKey)));
		}
		m.appendTail(out);
		return out.toString();
	}

	private static String placeholder(String name, PadPreset p, String boardKey)
	{
		PadContext b = PadContext.BOARD;
		PadContext f = PadContext.FOOT;
		switch (name)
		{
			case "push":
				return or(p, PadAction.PUSH, b, " or ", "W");
			case "pushFirst":
				return first(p, PadAction.PUSH, b, "W");
			case "brake":
				return or(p, PadAction.BRAKE, b, " or ", "the brake key");
			case "mod":
				return modifier(p, " or ");
			case "modSlash":
				return modifier(p, "/");
			case "ollie":
				return or(p, PadAction.OLLIE, b, "/", "Space");
			case "flip":
				return or(p, PadAction.FLIP_BUTTON, b, "/", "the flip button");
			case "grabBtn":
				return or(p, PadAction.GRAB_BUTTON, b, "/", "the grab button");
			case "grind":
				return or(p, PadAction.GRIND_BUTTON, b, "/", "the grind button");
			case "grabL":
				return or(p, PadAction.GRAB_LEFT, b, "/", "Q");
			case "grabR":
				return or(p, PadAction.GRAB_RIGHT, b, "/", "E");
			case "grabs":
				return or(p, PadAction.GRAB_LEFT, b, "/", "Q") + " / " + or(p, PadAction.GRAB_RIGHT, b, "/", "E");
			case "reset":
				return or(p, PadAction.RESET, b, "/", "R");
			case "card":
				return or(p, PadAction.CONTROLS_CARD, b, "/", "H");
			case "stop":
				return or(p, PadAction.STOP, b, "/", "Esc");
			case "board":
				return or(p, PadAction.BOARD_TOGGLE, f, "/", boardKey);
			case "sprint":
				return first(p, PadAction.SPRINT, f, "Shift");
			case "jump":
				return or(p, PadAction.JUMP, f, "/", "Space");
			case "drop":
				return or(p, PadAction.DROP_PICKUP, f, " / ", "Q / E");
			default:
				throw new IllegalArgumentException("no pad placeholder " + name);
		}
	}

	/**
	 * The hard tricks buttons: the modifier, and the spin assist, which is the modifier too ("{LB} or {RB}"), or
	 * Shift when the pad has neither.
	 */
	private static String modifier(PadPreset p, String joiner)
	{
		List<String> tokens = new ArrayList<>();
		for (PadButton b : PadButton.values())
		{
			PadAction a = p.action(b, PadContext.BOARD);
			if (a == PadAction.HARD_MODIFIER || a == PadAction.SPIN_ASSIST)
			{
				tokens.add(token(b));
			}
		}
		return tokens.isEmpty() ? "Shift" : String.join(joiner, tokens);
	}

	/** What an action does, for the pad layout lists (board wording; on-foot wording with {@code onFoot}). */
	static String describe(PadAction a, boolean onFoot)
	{
		switch (a)
		{
			case PUSH:
				return "Push (tap or hold)";
			case MONGO_PUSH:
				return "Mongo push (tap or hold)";
			case BRAKE:
				return "Brake";
			case OLLIE:
				return "Hold to crouch, let go to ollie";
			case GRAB_LEFT:
				return "Grab (left hand) in the air, or rolling (style only)";
			case GRAB_RIGHT:
				return "Grab (right hand) in the air, or rolling (style only)";
			case HARD_MODIFIER:
				return "Shift: hard flips with a flick, tight turn with {LS}, brake held alone. In the air, hold with "
					+ "{LS} up / down for a front / back flip";
			case LEAN_FORWARD:
				return "Lean forward: with a grab in the air, a front flip";
			case LEAN_BACK:
				return "Lean back: with a grab in the air, a back flip";
			case BOARD_TOGGLE:
				return onFoot ? "get on, or call a far board back" : "Step off and carry the board, or get on. Far "
					+ "away: calls it back";
			case RESET:
				return onFoot ? "get up" : "Get up after a bail, or stop";
			case STOP:
				return onFoot ? "stop skating" : "Stop skating";
			case CONTROLS_CARD:
				return onFoot ? "controls card" : "Controls card";
			case SPRINT:
				return "sprint (hold)";
			case JUMP:
				return "jump";
			case DROP_PICKUP:
				return "drop or pick up the board";
			case CAMERA_ORBIT:
				return "hold and move {RS} to turn the camera";
			case FLIP_BUTTON:
				return "Flip trick: hold a direction ({LS}, or the d-pad for diagonals) and press. Again mid-flip: "
					+ "a double";
			case GRAB_BUTTON:
				return "Grab: hold a direction and the button in the air (rolling: style only)";
			case GRIND_BUTTON:
				return "Near a rail, hold to catch it from further away. Rolling with no rail near: step off";
			case SPIN_ASSIST:
				return "Hard tricks (as Shift; alone, a brake). In the air, hold to spin faster";
			default:
				return a.label;
		}
	}

	/**
	 * The preset's buttons, each with what it does ("Push (tap or hold). On foot: sprint (hold)"), buttons that do
	 * exactly the same grouped ("{LB} {RB}"), in button order; unbound buttons are left out.
	 */
	public static Map<String, String> layout(PadPreset p)
	{
		Map<PadPreset.Binding, List<PadButton>> groups = new LinkedHashMap<>();
		for (PadButton b : PadButton.values())
		{
			PadPreset.Binding binding = p.binding(b);
			if (binding.board == PadAction.NONE && binding.foot == PadAction.NONE && binding.air == null)
			{
				continue;
			}
			groups.computeIfAbsent(binding, k -> new ArrayList<>()).add(b);
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<PadPreset.Binding, List<PadButton>> g : groups.entrySet())
		{
			List<String> tokens = new ArrayList<>();
			for (PadButton b : g.getValue())
			{
				tokens.add(token(b));
			}
			out.put(String.join(" ", tokens), describe(g.getKey()));
		}
		return out;
	}

	/** One binding in words. */
	static String describe(PadPreset.Binding b)
	{
		StringBuilder s = new StringBuilder();
		if (b.board != PadAction.NONE)
		{
			s.append(describe(b.board, false));
		}
		if (b.air != null)
		{
			s.append(s.length() > 0 ? ". " : "").append("In the air: ").append(lower(describe(b.air, false)));
		}
		if (b.foot != PadAction.NONE && b.foot != b.board)
		{
			s.append(s.length() > 0 ? ". " : "").append("On foot: ").append(describe(b.foot, true));
		}
		return s.toString();
	}

	private static String lower(String s)
	{
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}
}
