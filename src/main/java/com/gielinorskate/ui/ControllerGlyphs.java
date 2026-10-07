package com.gielinorskate.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xbox-style controller button prompts, drawn in code (no image assets, no logos): coloured circles with A / B / X /
 * Y, pills for the bumpers, triggers and Back, a stick icon (with L3 / R3 for a click), and a d-pad with one arm lit. Text can carry them as
 * tokens ("{A}: push"), measured and drawn inline by {@link #width} and {@link #drawText}. Pure apart from drawing.
 */
public final class ControllerGlyphs
{
	public enum Glyph
	{
		A("A", new Color(84, 170, 60), Color.WHITE),
		B("B", new Color(212, 58, 48), Color.WHITE),
		X("X", new Color(40, 112, 214), Color.WHITE),
		Y("Y", new Color(236, 190, 30), new Color(30, 30, 30)),
		LB("LB", null, null),
		RB("RB", null, null),
		LT("LT", null, null),
		RT("RT", null, null),
		BACK("BACK", null, null),
		/** Start / Menu. */
		START("START", null, null),
		/** The left stick. */
		LS("L", null, null),
		/** The right stick. */
		RS("R", null, null),
		/** Clicking the left / right stick in. */
		L3("L3", null, null),
		R3("R3", null, null),
		/** D-pad up, down, left and right: the d-pad with that arm lit. */
		DUP("", null, null),
		DDOWN("", null, null),
		DLEFT("", null, null),
		DRIGHT("", null, null);

		/** The letters drawn on it. */
		public final String label;
		/** Face buttons only: fill and letter colours. */
		final Color fill;
		final Color letter;

		Glyph(String label, Color fill, Color letter)
		{
			this.label = label;
			this.fill = fill;
			this.letter = letter;
		}

		boolean isFace()
		{
			return fill != null;
		}

		boolean isPill()
		{
			return this == LB || this == RB || this == LT || this == RT || this == BACK || this == START;
		}

		boolean isDpad()
		{
			return this == DUP || this == DDOWN || this == DLEFT || this == DRIGHT;
		}
	}

	private static final Pattern TOKEN = Pattern.compile(
		"\\{(A|B|X|Y|LB|RB|LT|RT|BACK|START|LS|RS|L3|R3|DUP|DDOWN|DLEFT|DRIGHT)\\}");
	private static final Color PILL_FILL = new Color(58, 62, 72);
	private static final Color PILL_EDGE = new Color(150, 156, 170);
	private static final Color PILL_TEXT = Color.WHITE;
	private static final Color STICK_OUTER = new Color(48, 52, 60);
	private static final Color STICK_INNER = new Color(96, 102, 116);
	private static final Color DPAD_ARM = new Color(96, 102, 116);
	private static final Color DPAD_LIT = Color.WHITE;
	private static final Color OUTLINE = new Color(0, 0, 0, 150);
	/** Space either side of an inline glyph, as a fraction of the line height. */
	private static final float GAP = 0.12f;

	private ControllerGlyphs()
	{
	}

	/** The text split into its plain pieces (String) and glyphs (Glyph), in order; no empty strings. */
	public static List<Object> split(String text)
	{
		List<Object> out = new ArrayList<>();
		Matcher m = TOKEN.matcher(text);
		int at = 0;
		while (m.find())
		{
			if (m.start() > at)
			{
				out.add(text.substring(at, m.start()));
			}
			out.add(Glyph.valueOf(m.group(1)));
			at = m.end();
		}
		if (at < text.length())
		{
			out.add(text.substring(at));
		}
		return out;
	}

	/** The token for a glyph in text: "{A}". */
	public static String token(Glyph g)
	{
		return "{" + g.name() + "}";
	}

	/** The text with each glyph spelt out ("A", "LT", "right stick"), for places that can only show plain text. */
	public static String plain(String text)
	{
		StringBuilder sb = new StringBuilder();
		for (Object piece : split(text))
		{
			sb.append(piece instanceof Glyph ? name((Glyph) piece) : (String) piece);
		}
		return sb.toString();
	}

	/** A glyph's plain name. */
	public static String name(Glyph g)
	{
		switch (g)
		{
			case LS:
				return "left stick";
			case RS:
				return "right stick";
			case L3:
				return "L3 (left stick click)";
			case R3:
				return "R3 (right stick click)";
			case DUP:
				return "d-pad up";
			case DDOWN:
				return "d-pad down";
			case DLEFT:
				return "d-pad left";
			case DRIGHT:
				return "d-pad right";
			default:
				return g.label;
		}
	}

	/** True when the text has any glyph token. */
	public static boolean hasGlyphs(String text)
	{
		return TOKEN.matcher(text).find();
	}

	/** Width of a glyph drawn {@code h} px tall, without the gaps around it. */
	public static int glyphWidth(Glyph g, int h)
	{
		if (g.isPill())
		{
			return Math.round(h * (g == Glyph.START ? 2.15f : g == Glyph.BACK ? 2.0f : 1.45f));
		}
		return h;
	}

	/** Width of the text as {@link #drawText} draws it: plain pieces in the font, glyphs one line tall. */
	public static int width(String text, FontMetrics metrics)
	{
		int h = metrics.getHeight();
		int gap = Math.max(1, Math.round(h * GAP));
		int w = 0;
		for (Object piece : split(text))
		{
			w += piece instanceof Glyph ? glyphWidth((Glyph) piece, h) + 2 * gap : metrics.stringWidth((String) piece);
		}
		return w;
	}

	/**
	 * Draws the text at (x, baseline) in the graphics' font: plain pieces with a drop shadow (when {@code shadow}
	 * is not null) then {@code color}, glyphs one line tall in their own colours. Plain text is drawn exactly as
	 * two drawString calls would.
	 */
	public static void drawText(Graphics2D graphics, String text, int x, int baseline, Color color, Color shadow,
		int shadowOffset)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		int h = metrics.getHeight();
		int gap = Math.max(1, Math.round(h * GAP));
		int top = baseline - metrics.getAscent();
		int cx = x;
		for (Object piece : split(text))
		{
			if (piece instanceof Glyph)
			{
				Glyph g = (Glyph) piece;
				paint(graphics, g, cx + gap, top, h);
				cx += glyphWidth(g, h) + 2 * gap;
				continue;
			}
			String s = (String) piece;
			if (shadow != null)
			{
				graphics.setColor(shadow);
				graphics.drawString(s, cx + shadowOffset, baseline + shadowOffset);
			}
			graphics.setColor(color);
			graphics.drawString(s, cx, baseline);
			cx += metrics.stringWidth(s);
		}
	}

	/** Paints one glyph in the box (x, top, its width, h). Leaves the graphics' colour, font and hints as found. */
	public static void paint(Graphics2D graphics, Glyph g, int x, int top, int h)
	{
		Color oldColor = graphics.getColor();
		Font oldFont = graphics.getFont();
		Stroke oldStroke = graphics.getStroke();
		Object oldAa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try
		{
			float pad = Math.max(1f, h * 0.06f);
			float size = h - 2 * pad;
			float y = top + pad;
			graphics.setStroke(new BasicStroke(Math.max(1f, h / 14f)));
			if (g.isFace())
			{
				Ellipse2D circle = new Ellipse2D.Float(x + pad, y, size, size);
				graphics.setColor(g.fill);
				graphics.fill(circle);
				graphics.setColor(OUTLINE);
				graphics.draw(circle);
				label(graphics, g.label, g.letter, x + pad, y, size, size, 0.62f);
			}
			else if (g.isPill())
			{
				float w = glyphWidth(g, h) - 2 * pad;
				float ph = size * 0.8f;
				RoundRectangle2D pill = new RoundRectangle2D.Float(x + pad, y + (size - ph) / 2f, w, ph, ph, ph);
				graphics.setColor(PILL_FILL);
				graphics.fill(pill);
				graphics.setColor(PILL_EDGE);
				graphics.draw(pill);
				label(graphics, g.label, PILL_TEXT, x + pad, y + (size - ph) / 2f, w, ph,
					g == Glyph.BACK || g == Glyph.START ? 0.5f : 0.62f);
			}
			else if (g.isDpad())
			{
				float arm = size / 3f;
				float cx = x + pad;
				graphics.setColor(DPAD_ARM);
				graphics.fill(new RoundRectangle2D.Float(cx, y + arm, size, arm, arm / 3f, arm / 3f));
				graphics.fill(new RoundRectangle2D.Float(cx + arm, y, arm, size, arm / 3f, arm / 3f));
				graphics.setColor(DPAD_LIT);
				float lit = arm * 1.1f;
				switch (g)
				{
					case DDOWN:
						graphics.fill(new RoundRectangle2D.Float(cx + arm, y + size - lit, arm, lit, arm / 3f, arm / 3f));
						break;
					case DLEFT:
						graphics.fill(new RoundRectangle2D.Float(cx, y + arm, lit, arm, arm / 3f, arm / 3f));
						break;
					case DRIGHT:
						graphics.fill(new RoundRectangle2D.Float(cx + size - lit, y + arm, lit, arm, arm / 3f, arm / 3f));
						break;
					default:
						graphics.fill(new RoundRectangle2D.Float(cx + arm, y, arm, lit, arm / 3f, arm / 3f));
						break;
				}
			}
			else
			{
				// a stick: a dark well, a lighter cap and its letter
				Ellipse2D well = new Ellipse2D.Float(x + pad, y, size, size);
				graphics.setColor(STICK_OUTER);
				graphics.fill(well);
				float cap = size * 0.72f;
				float off = (size - cap) / 2f;
				graphics.setColor(STICK_INNER);
				graphics.fill(new Ellipse2D.Float(x + pad + off, y + off, cap, cap));
				graphics.setColor(OUTLINE);
				graphics.draw(well);
				label(graphics, g.label, Color.WHITE, x + pad, y, size, size, 0.55f);
			}
		}
		finally
		{
			graphics.setColor(oldColor);
			graphics.setFont(oldFont);
			graphics.setStroke(oldStroke);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				oldAa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldAa);
		}
	}

	/** Centres bold text of {@code scale} x the box height in the box. */
	private static void label(Graphics2D graphics, String text, Color color, float x, float y, float w, float h,
		float scale)
	{
		if (text.isEmpty())
		{
			return;
		}
		Font font = graphics.getFont().deriveFont(Font.BOLD, Math.max(6f, h * scale));
		graphics.setFont(font);
		FontMetrics m = graphics.getFontMetrics();
		float tx = x + (w - m.stringWidth(text)) / 2f;
		float ty = y + (h - m.getHeight()) / 2f + m.getAscent();
		graphics.setColor(color);
		graphics.drawString(text, Math.round(tx), Math.round(ty));
	}
}
