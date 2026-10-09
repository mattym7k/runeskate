package com.gielinorskate.overlay;

import com.gielinorskate.Text;
import com.gielinorskate.controller.*;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.session.SkateSession;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.ui.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.*;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;

/**
* The controls card, in the same visual style as {@link ScoreOverlay}: text over a translucent dark backing. One
* card with the essential controls for the current mode (on the board or on foot; keys, flicks or pad buttons),
* the core flicks drawn as arrows, and a pointer to the side panel's Trick Book for everything else. Shown by
* itself until the player has pushed and jumped once; H shows and hides it ({@link ControlsCardTimer}, owned and
* driven by {@link SkateSession}).
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ControlsCardOverlay extends Overlay
{
private static final Color TITLE_COLOR = new Color(255, 215, 110);
private static final Color SHADOW_COLOR = new Color(0, 0, 0, 140);
/** Font sizes tried, largest first, until the card fits its region. */
static final int MAX_FONT_SIZE = 16;
static final int MIN_FONT_SIZE = 9;

/**
* What the card shows: the settings its wording depends on. A value: cached by equality.
* {@code flickButton} is "right", "left" or "middle"; {@code controller}: the controls are named with drawn
* buttons of {@code preset}; {@code onFoot}: off the board, the walking controls; {@code boardKey}: the board
* on/off key (F by default).
*/
@EqualsAndHashCode
@AllArgsConstructor
public static final class Spec
{
final String manualKey;
final boolean keyboardTricks;
final boolean mouseTricks;
final String flickButton;
final boolean mirror;
final boolean learned;
final boolean controller;
final boolean onFoot;
final String boardKey;
final PadPreset preset;
}

/**
* One line of content: text, or a row of {@link #cells} (several flick pictures with a word each on one line,
* never wrapped; its text is their words, for searching). A cell is a row with its picture as {@link #glyph}.
* A laid-out card's lines are rows too, the text ones wrapped.
*/
@AllArgsConstructor
static final class Row
{
final String text;
final Gesture glyph;
final boolean title;
final List<Row> cells;
}

/** A laid-out card: font, wrapped lines and the box size (padding included). */
@AllArgsConstructor
static final class Card
{
final Font font;
final List<Row> lines;
final int padding;
final int lineGap;
/** Width of a cell's picture column (a line height plus a gap). */
final int glyphColumn;
/** Space between the cells of a row of cells. */
final int cellGap;
final int width;
final int height;
}

/** The card's bold sans-serif fonts, by size (MIN_FONT_SIZE..MAX_FONT_SIZE). */
static final Font[] FONTS = new Font[MAX_FONT_SIZE + 1];

static
{
for (int i = MIN_FONT_SIZE; i <= MAX_FONT_SIZE; i++)
FONTS[i] = new Font(Font.SANS_SERIF, Font.BOLD, i);
}

private final Client client;
private final SkateSession session;

/** Fitted cards by spec and region size, so stepping on and off the board never lays out again (32 kept). */
private final Map<List<Object>, Card> layouts = new LinkedHashMap<List<Object>, Card>(16, 0.75f, true)
{
@Override
protected boolean removeEldestEntry(Map.Entry<List<Object>, Card> eldest)
{
return size() > 32;
}
};

/**
* Loads the card's fonts' glyph data now. The first sans-serif font of a JVM is slow (font configuration,
* hundreds of ms), so the plugin calls this off the client thread at start-up rather than letting the first
* card shown stall a frame. Safe on any thread.
*/
public static void warmFonts()
{
FontRenderContext frc = new FontRenderContext(null, true, false);
for (int i = MIN_FONT_SIZE; i <= MAX_FONT_SIZE; i++)
FONTS[i].getStringBounds("Skating basics: push, jump 0123456789", frc);
}

{
setPosition(OverlayPosition.DYNAMIC);
setLayer(OverlayLayer.ABOVE_WIDGETS);
}

@Override
public Dimension render(Graphics2D g)
{
float alpha = session.isActive() ? session.getControlsCardAlpha() : 0f;
if (alpha <= 0f)
return null;
int[] region = region(HudLayout.of(client, HudLayout.NO_OBSTACLE));
Spec spec = session.getControlsCardSpec();
Card card = layouts.computeIfAbsent(Arrays.asList(spec, region[2], region[3]),
k -> fit(g, rows(spec), region[2], region[3]));
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
g.setFont(card.font);
FontMetrics m = g.getFontMetrics();
g.setColor(new Color(10, 12, 18, 180));
g.fill(new RoundRectangle2D.Float(region[0], region[1], card.width, card.height, 10, 10));
int top = region[1] + card.padding;
for (Row line : card.lines)
{
int x = region[0] + card.padding;
for (Row c : line.cells != null ? line.cells : Collections.singletonList(line))
{
if (c.glyph != null)
{
GestureGlyphPainter.paint(g, c.glyph, x, top, m.getHeight(), new Color(170, 180, 200),
new Color(120, 200, 255));
x += card.glyphColumn;
}
ControllerGlyphs.drawText(g, c.text, x, top + m.getAscent(), c.title ? TITLE_COLOR : Color.WHITE,
SHADOW_COLOR, 1);
x += ControllerGlyphs.width(c.text, m) + card.cellGap;
}
top += m.getHeight() + card.lineGap;
}
return null;
}

/**
* The card's region {x, y, maxWidth, maxHeight}: the top-left of the game viewport (so fixed mode never
* spills into the side panel), inset by a margin, down to 6 px above the tallest trick stack of
* {@link HudLayout}.
*/
static int[] region(HudLayout hud)
{
// 3% of the viewport height, 6..24 px
int margin = Math.max(6, Math.min(24, Math.round(hud.vh * 0.03f)));
int y = hud.vy + margin;
return new int[]{hud.vx + margin, y, Math.max(0, hud.vw - 2 * margin),
Math.max(0, Math.min(hud.vy + hud.vh - margin, hud.stackTopY - 6) - y)};
}

/**
* Lays the rows out at the largest font size from {@link #MAX_FONT_SIZE} down to {@link #MIN_FONT_SIZE} whose
* box, with lines word-wrapped to the width, fits maxW x maxH; the smallest size when none does.
*/
static Card fit(Graphics2D g, List<Row> content, int maxW, int maxH)
{
Card card = null;
for (int size = MAX_FONT_SIZE; size >= MIN_FONT_SIZE && (card == null || card.width > maxW
|| card.height > maxH); size--)
{
FontMetrics m = g.getFontMetrics(FONTS[size]);
int padding = Math.max(4, Math.round(size * 0.55f));
int lineGap = Math.max(1, size / 5);
int glyphColumn = m.getHeight() + Math.max(3, size / 3);
int cellGap = size * 2;
List<Row> lines = new ArrayList<>();
int textW = 0;
for (Row row : content)
{
if (row.cells != null)
{
int w = -cellGap;
for (Row c : row.cells)
w += cellGap + glyphColumn + ControllerGlyphs.width(c.text, m);
lines.add(row);
textW = Math.max(textW, w);
continue;
}
for (String piece : wrap(row.text, m, maxW - 2 * padding))
{
lines.add(new Row(piece, null, row.title, null));
textW = Math.max(textW, ControllerGlyphs.width(piece, m));
}
}
card = new Card(FONTS[size], lines, padding, lineGap, glyphColumn, cellGap, textW + padding * 2,
(m.getHeight() + lineGap) * lines.size() - lineGap + padding * 2);
}
return card;
}

/**
* {@code line} broken at spaces into pieces no wider than maxW where possible (a single too-wide word stays
* whole). Glyph tokens ("{A}") hold no spaces, so they are never split.
*/
static List<String> wrap(String line, FontMetrics metrics, int maxW)
{
List<String> out = new ArrayList<>();
String rest = line;
while (ControllerGlyphs.width(rest, metrics) > maxW)
{
int cut = -1;
for (int i = rest.indexOf(' '); i > 0; i = rest.indexOf(' ', i + 1))
{
if (ControllerGlyphs.width(rest.substring(0, i).trim(), metrics) > maxW)
break;
cut = i;
}
if (cut <= 0)
break;
out.add(rest.substring(0, cut).trim());
rest = rest.substring(cut).trim();
}
out.add(rest);
return out;
}

private static Row title(String text)
{
return new Row(text, null, true, null);
}

private static Row text(String text)
{
return new Row(text, null, false, null);
}

/** Each line of {@code text} as a text row. */
private static void texts(List<Row> rows, String text)
{
for (String line : text.split("\n"))
rows.add(text(line));
}

/** Every card's last lines: the Trick Book, and until the basics are learned how to put the card away. */
private static List<Row> end(List<Row> rows, boolean learned)
{
rows.add(text(Text.get("card.book")));
if (!learned)
rows.add(title(Text.get("card.learn")));
return rows;
}

/** The card's content for the spec: on foot, controller mode, or keys and mouse. Nine lines at most. */
static List<Row> rows(Spec spec)
{
if (spec.onFoot)
return onFootRows(spec);
if (spec.controller)
return spec.preset.buttonTricks() ? buttonTrickRows(spec) : controllerRows(spec);
List<Row> rows = new ArrayList<>();
rows.add(title(Text.get("card.hide", "H")));
rows.add(text(Text.get("card.keys.basics")));
if (spec.mouseTricks)
{
rows.add(title(Text.get("card.keys.mouse", spec.flickButton)));
rows.add(coreFlicks(spec));
}
if (spec.keyboardTricks && spec.mouseTricks)
// one line for both, so the card still fits a fixed-mode viewport
rows.add(text(Text.get("card.keys.both")));
else if (spec.keyboardTricks)
{
rows.add(title(Text.get("card.keys.trickTitle")));
rows.add(text(Text.get("card.keys.tricks")));
}
rows.add(text(Text.get("card.keys.air", spec.manualKey)));
rows.add(text(Text.get("card.keys.reset", spec.boardKey)));
return end(rows, spec.learned);
}

/**
* The four flicks every skater needs first, side by side: ollie, kickflip, heelflip, shove-it. With mirrored
* flicks the pictures are mirrored and the tricks stay the same.
*/
private static Row coreFlicks(Spec spec)
{
List<Row> cells = new ArrayList<>();
Direction[] dirs = {Direction.UP, Direction.UP_LEFT, Direction.UP_RIGHT, Direction.LEFT};
String[] tricks = {"ollie", "kickflip", "heelflip", "shove-it"};
for (int i = 0; i < dirs.length; i++)
{
Gesture g = new Gesture(dirs[i], false, 0f);
cells.add(new Row(tricks[i], spec.mirror ? KeyboardTricks.mirror(g) : g, false, null));
}
return new Row(String.join("   ", tricks), null, false, cells);
}

/**
* Controller mode's card, with the preset's buttons drawn in (the universal profile: the left stick the arrows,
* the right stick the mouse, each button its own pad key). An action no button does is named by its keyboard
* key, which still works, or left out.
*/
static List<Row> controllerRows(Spec spec)
{
PadPreset p = spec.preset;
PadContext b = PadContext.BOARD;
String mod = PadWords.buttons(p, PadAction.HARD_MODIFIER, b, "/");
String grabs = grabs(p, " / ", "Q / E");
List<Row> rows = new ArrayList<>();
rows.add(title(Text.get("card.hide", PadWords.or(p, PadAction.CONTROLS_CARD, b, "/", "H"))));
List<String> basics = new ArrayList<>();
basics.add(PadWords.or(p, PadAction.PUSH, b, " or ", "W") + ": push");
basics.add("{LS} left/right: steer");
addIf(basics, PadWords.buttons(p, PadAction.BRAKE, b, "/"), ": brake");
addIf(basics, mod, ": Shift (hard tricks)");
addIf(basics, PadWords.buttons(p, PadAction.OLLIE, b, "/"), " held, let go: ollie");
rows.add(text(String.join("    ", basics)));
if (spec.mouseTricks)
{
// with trick keys as well, they share the title so the card still fits a fixed-mode viewport
rows.add(title(Text.get("card.pad.mouse") + (mod != null ? "  " + mod
+ " held: harder" : "") + (spec.keyboardTricks ? ".  Keys too: Space, 1-0" : "")));
rows.add(coreFlicks(spec));
}
else if (spec.keyboardTricks)
rows.add(text(Text.get("card.pad.keys")));
rows.add(text(Text.get("card.pad.air", grabs, mod != null ? " or " + mod : "")));
rows.add(text(Text.get("card.pad.rail", grabs(p, "/", "Q/E"))));
rows.add(text(Text.get("card.pad.reset", PadWords.or(p, PadAction.RESET, b, "/", "R"),
PadWords.or(p, PadAction.BOARD_TOGGLE, b, "/", spec.boardKey), PadWords.or(p, PadAction.STOP, b, "/", "Esc"))));
return end(rows, spec.learned);
}

/**
* Controller mode with a button-trick layout (Tony Hawk's American Wasteland): a direction on the left stick or
* the d-pad with the flip or grab button, the left stick's manual gesture, and the right stick on the camera.
*/
static List<Row> buttonTrickRows(Spec spec)
{
List<Row> rows = new ArrayList<>();
List<String> lines = Text.lines("card.bt");
for (int i = 0; i < lines.size(); i++)
rows.add(new Row(PadWords.resolve(lines.get(i), spec.preset, spec.boardKey), null, i == 0 || i == 2, null));
return end(rows, spec.learned);
}

/** The grab buttons, left hand then right ("{LT} / {RT}"), or the keys when the pad has neither. */
private static String grabs(PadPreset p, String joiner, String fallback)
{
List<String> both = new ArrayList<>();
addIf(both, PadWords.buttons(p, PadAction.GRAB_LEFT, PadContext.BOARD, joiner), "");
addIf(both, PadWords.buttons(p, PadAction.GRAB_RIGHT, PadContext.BOARD, joiner), "");
return both.isEmpty() ? fallback : String.join(joiner, both);
}

private static void addIf(List<String> out, String buttons, String what)
{
if (buttons != null)
out.add(buttons + what);
}

/** Off the board: the walking controls (keys, or the pad's buttons drawn in). */
static List<Row> onFootRows(Spec spec)
{
List<Row> rows = new ArrayList<>();
PadPreset p = spec.controller ? spec.preset : null;
PadContext f = PadContext.FOOT;
String board = p != null ? PadWords.or(p, PadAction.BOARD_TOGGLE, f, "/", spec.boardKey) : spec.boardKey;
rows.add(title(Text.get("card.foot.title", p != null ? PadWords.or(p, PadAction.CONTROLS_CARD, f, "/", "H")
: "H")));
if (p != null)
{
texts(rows, Text.get("card.foot.pad", PadWords.first(p, PadAction.SPRINT, f, "Shift"),
PadWords.or(p, PadAction.JUMP, f, "/", "Space"), PadWords.or(p, PadAction.DROP_PICKUP, f, " / ", "Q / E")));
String camera = PadWords.buttons(p, PadAction.CAMERA_ORBIT, f, "/");
if (p.buttonTricks())
rows.add(text("{RS}: turn the camera"));
else if (camera != null)
rows.add(text(Text.get("card.foot.orbit", camera)));
}
else
texts(rows, Text.get("card.foot.keys"));
texts(rows, Text.get("card.foot.end", board, p != null ? PadWords.or(p, PadAction.STOP, f, "/", "Esc") : "Esc"));
return end(rows, true);
}
}
