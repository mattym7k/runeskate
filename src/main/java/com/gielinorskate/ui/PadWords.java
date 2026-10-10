package com.gielinorskate.ui;

import com.gielinorskate.Text;
import com.gielinorskate.controller.*;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Words for the pad's buttons under a preset, as glyph tokens ("{A} or {X}"), so the controls card, the Trick Book
 * and the side panel name the buttons the player's layout actually uses. Pure.
 */
public final class PadWords
{
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{@(\\w+)\\}");
	/** What each action does, by name; "NAME.foot" for its on-foot wording where that differs. */
	private static final Map<String, String> DOES = TrickGuide.table(Text.lines("pad.does"));
	/** The action placeholders but the modifier's, by name (see {@link #placeholder}). */
	private static final Map<String, String> PLACES = TrickGuide.table(Text.lines("pad.places"));

	private PadWords()
	{
	}

	/** The drawn glyph for a pad button: the same name, the d-pad's shortened ("DPAD_UP" is "DUP"). */
	public static Glyph glyph(PadButton b)
	{
		return Glyph.valueOf(b.name().replace("DPAD_", "D"));
	}

	/** A button's token: "{A}". */
	public static String token(PadButton b)
	{
		return ControllerGlyphs.token(glyph(b));
	}

	private static String tokens(List<PadButton> bs, String joiner)
	{
		return bs.stream().map(PadWords::token).collect(Collectors.joining(joiner));
	}

	/** The buttons doing {@code action} in {@code context}, joined by {@code joiner}; null when none does. */
	public static String buttons(PadPreset p, PadAction action, PadContext context, String joiner)
	{
		return or(p, action, context, joiner, null);
	}

	/** As {@link #buttons}, or {@code fallback} (the keyboard key, which still works) when no button does it. */
	public static String or(PadPreset p, PadAction action, PadContext context, String joiner, String fallback)
	{
		List<PadButton> bs = p.buttonsFor(action, context);
		return bs.isEmpty() ? fallback : tokens(bs, joiner);
	}

	/** The first button doing {@code action} in {@code context}, or {@code fallback}. */
	public static String first(PadPreset p, PadAction action, PadContext context, String fallback)
	{
		List<PadButton> bs = p.buttonsFor(action, context);
		return bs.isEmpty() ? fallback : token(bs.get(0));
	}

	/**
	 * Fills in the action placeholders of a text with the preset's buttons: {@code {@push}} (push, "or"),
	 * {@code {@pushFirst}}, {@code {@brake}}, {@code {@mod}} (the hard tricks modifier, "or"), {@code {@modSlash}},
	 * {@code {@grabL}}, {@code {@grabR}}, {@code {@grabs}} ("{LT} / {RT}"), {@code {@reset}}, {@code {@card}},
	 * {@code {@stop}}, {@code {@board}}, and on foot {@code {@sprint}}, {@code {@jump}}, {@code {@drop}}. An action
	 * no button does is named by its keyboard key ({@code boardKey} for the board key).
	 */
	public static String resolve(String text, PadPreset p, String boardKey)
	{
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuffer out = new StringBuffer();
		while (m.find())
			m.appendReplacement(out, Matcher.quoteReplacement(placeholder(m.group(1), p, boardKey)));
		m.appendTail(out);
		return out.toString();
	}

	private static String placeholder(String name, PadPreset p, String boardKey)
	{
		if (name.startsWith("mod"))
			return modifier(p, name.equals("mod") ? " or " : "/");
		if (name.equals("grabs"))
			return placeholder("grabL", p, boardKey) + " / " + placeholder("grabR", p, boardKey);
		String row = PLACES.get(name);
		if (row == null)
			throw new IllegalArgumentException("no pad placeholder " + name);
		// action|context|joiner (none: the first button only)|the key, when no button does it (none: the board key)
		String[] f = row.split("\\|");
		PadAction a = PadAction.valueOf(f[0]);
		PadContext c = PadContext.valueOf(f[1]);
		String key = f.length > 3 ? f[3] : boardKey;
		return f[2].isEmpty() ? first(p, a, c, key) : or(p, a, c, f[2], key);
	}

	/**
	 * The hard tricks buttons: the modifier, and the spin assist, which is the modifier too ("{LB} or {RB}"), or
	 * Shift when the pad has neither.
	 */
	private static String modifier(PadPreset p, String joiner)
	{
		List<PadButton> bs = Arrays.stream(PadButton.values()).filter(b -> p.action(b, PadContext.BOARD)
			== PadAction.HARD_MODIFIER || p.action(b, PadContext.BOARD) == PadAction.SPIN_ASSIST)
			.collect(Collectors.toList());
		return bs.isEmpty() ? "Shift" : tokens(bs, joiner);
	}

	/** What an action does, for the pad layout lists (board wording; on-foot wording with {@code onFoot}). */
	static String describe(PadAction a, boolean onFoot)
	{
		String foot = onFoot ? DOES.get(a.name() + ".foot") : null;
		return foot != null ? foot : DOES.getOrDefault(a.name(), a.label);
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
			if (binding.board != PadAction.NONE || binding.foot != PadAction.NONE || binding.air != null)
				groups.computeIfAbsent(binding, k -> new ArrayList<>()).add(b);
		}
		Map<String, String> out = new LinkedHashMap<>();
		groups.forEach((binding, bs) -> out.put(tokens(bs, " "), describe(binding)));
		return out;
	}

	/** One binding in words. */
	static String describe(PadPreset.Binding b)
	{
		List<String> parts = new ArrayList<>();
		if (b.board != PadAction.NONE)
			parts.add(describe(b.board, false));
		if (b.air != null)
		{
			String air = describe(b.air, false);
			parts.add("In the air: " + (air.isEmpty() ? air : Character.toLowerCase(air.charAt(0)) + air.substring(1)));
		}
		if (b.foot != PadAction.NONE && b.foot != b.board)
			parts.add("On foot: " + describe(b.foot, true));
		return String.join(". ", parts);
	}
}
