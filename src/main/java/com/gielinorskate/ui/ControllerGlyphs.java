package com.gielinorskate.ui;

import com.gielinorskate.Text;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
* Xbox-style controller button prompts, drawn in code (no image assets, no logos): coloured circles with A / B / X /
* Y, pills for the bumpers, triggers and Back, a stick icon (with L3 / R3 for a click), and a d-pad with one arm lit.
* Text can carry them as tokens ("{A}: push"), measured and drawn inline by {@link #width} and {@link #drawText}.
* Pure apart from drawing.
*/
public final class ControllerGlyphs
{
public enum Glyph
{
A, B, X, Y, LB, RB, LT, RT, BACK,
/** Start / Menu. */
START, LS, RS,
/** Clicking the left / right stick in. */
L3, R3,
/** D-pad up, down, left and right: the d-pad with that arm lit (the only glyphs with no letters). */
DUP, DDOWN, DLEFT, DRIGHT;

/** The letters drawn on it. */
public final String label;
/** Its plain name ("A", "LT", "right stick"). */
final String spoken;
/** Face buttons only: fill and letter colours. */
final Color fill;
final Color letter;

Glyph()
{
// glyph.NAME: "letters|plain name", and for a face button "|fill|letter" colours as hex RGB
String[] f = Text.get("glyph." + name()).split("\\|");
label = f[0];
spoken = f[1];
fill = f.length > 2 ? new Color(Integer.parseInt(f[2], 16)) : null;
letter = f.length > 2 ? new Color(Integer.parseInt(f[3], 16)) : null;
}

boolean isPill()
{
return this == LB || this == RB || this == LT || this == RT || this == BACK || this == START;
}
}

private static final Pattern TOKEN = Pattern.compile("\\{("
+ Arrays.stream(Glyph.values()).map(Enum::name).collect(Collectors.joining("|")) + ")\\}");
/** A grey: the d-pad's arms and a stick's cap. */
private static final Color ARM = new Color(96, 102, 116);
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
out.add(text.substring(at, m.start()));
out.add(Glyph.valueOf(m.group(1)));
at = m.end();
}
if (at < text.length())
out.add(text.substring(at));
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
return split(text).stream().map(p -> p instanceof Glyph ? ((Glyph) p).spoken : (String) p)
.collect(Collectors.joining());
}

/** True when the text has any glyph token. */
public static boolean hasGlyphs(String text)
{
return TOKEN.matcher(text).find();
}

/** Width of a glyph drawn {@code h} px tall, without the gaps around it. */
public static int glyphWidth(Glyph g, int h)
{
return g.isPill() ? Math.round(h * (g == Glyph.START ? 2.15f : g == Glyph.BACK ? 2.0f : 1.45f)) : h;
}

/** Width of the text as {@link #drawText} draws it: plain pieces in the font, glyphs one line tall. */
public static int width(String text, FontMetrics metrics)
{
int h = metrics.getHeight();
int gap = Math.max(1, Math.round(h * GAP));
int w = 0;
for (Object piece : split(text))
w += piece instanceof Glyph ? glyphWidth((Glyph) piece, h) + 2 * gap : metrics.stringWidth((String) piece);
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
float left = x + pad;
graphics.setStroke(new BasicStroke(Math.max(1f, h / 14f)));
if (g.fill != null)
{
// a face button
Ellipse2D circle = new Ellipse2D.Float(left, y, size, size);
graphics.setColor(g.fill);
graphics.fill(circle);
graphics.setColor(OUTLINE);
graphics.draw(circle);
label(graphics, g.label, g.letter, left, y, size, size, 0.62f);
}
else if (g.isPill())
{
float w = glyphWidth(g, h) - 2 * pad;
float ph = size * 0.8f;
RoundRectangle2D pill = new RoundRectangle2D.Float(left, y + (size - ph) / 2f, w, ph, ph, ph);
graphics.setColor(new Color(58, 62, 72));
graphics.fill(pill);
graphics.setColor(new Color(150, 156, 170));
graphics.draw(pill);
label(graphics, g.label, Color.WHITE, left, y + (size - ph) / 2f, w, ph,
g == Glyph.BACK || g == Glyph.START ? 0.5f : 0.62f);
}
else if (g.label.isEmpty())
{
// the d-pad: a cross of grey arms, the one pressed lit white
float arm = size / 3f;
graphics.setColor(ARM);
graphics.fill(new RoundRectangle2D.Float(left, y + arm, size, arm, arm / 3f, arm / 3f));
graphics.fill(new RoundRectangle2D.Float(left + arm, y, arm, size, arm / 3f, arm / 3f));
graphics.setColor(Color.WHITE);
float lit = arm * 1.1f;
boolean upDown = g == Glyph.DUP || g == Glyph.DDOWN;
float lx = upDown ? left + arm : g == Glyph.DLEFT ? left : left + size - lit;
float ly = !upDown ? y + arm : g == Glyph.DUP ? y : y + size - lit;
graphics.fill(new RoundRectangle2D.Float(lx, ly, upDown ? arm : lit, upDown ? lit : arm, arm / 3f,
arm / 3f));
}
else
{
// a stick: a dark well, a lighter cap and its letter
Ellipse2D well = new Ellipse2D.Float(left, y, size, size);
graphics.setColor(new Color(48, 52, 60));
graphics.fill(well);
float cap = size * 0.72f;
float off = (size - cap) / 2f;
graphics.setColor(ARM);
graphics.fill(new Ellipse2D.Float(left + off, y + off, cap, cap));
graphics.setColor(OUTLINE);
graphics.draw(well);
label(graphics, g.label, Color.WHITE, left, y, size, size, 0.55f);
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
graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, Math.max(6f, h * scale)));
FontMetrics m = graphics.getFontMetrics();
graphics.setColor(color);
graphics.drawString(text, Math.round(x + (w - m.stringWidth(text)) / 2f),
Math.round(y + (h - m.getHeight()) / 2f + m.getAscent()));
}
}
