package com.gielinorskate.overlay;

import com.gielinorskate.feedback.Callout;
import com.gielinorskate.feedback.HudAnim;
import com.gielinorskate.input.LiveStroke;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.progression.SkateLevels;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.session.SkateSession;
import com.gielinorskate.ui.ControllerGlyphs;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/**
* Score HUD, placed inside the 3D viewport ({@link HudLayout}) so the minimap and chatbox never cover
* it. Drawn as a single cluster in the lower-left of the viewport: a vertical stack of trick names
* (oldest on top, newest on the bottom in a larger bold font), a charging ring meter below it, the
* current combo score beside the ring, and the session total underneath. A brief LANDED/BAILED flash
* replaces the newest line and the score when a combo resolves.
*
* <p>Animated ({@link HudAnim}): new trick lines pop in, the ring pulses when the multiplier rises, a landed
* score counts up and a bail shakes the cluster. A landed combo also gets an OSRS-style XP drop ("+1,234") and,
* when big enough, a callout word ({@link Callout}). Text uses the RuneScape fonts. RuneLite restores the
* graphics' hints, stroke, composite and transform after each overlay, so they are set freely here.
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScoreOverlay extends Overlay
{
/** The landed/bailed flash; it fades over its last 0.4 s. */
private static final float FLASH_SECONDS = 1.5f;
/** How many older trick names (above the newest) are shown in the stack. */
private static final int OLDER_TRICKS_SHOWN = 4;

/** Flick visualizer: the trail and wedge of a regular flick, and of a nollie (push up first). */
private static final Color FLICK_TINT = new Color(120, 200, 255);
private static final Color NOLLIE_TINT = new Color(255, 170, 80);
private static final Color SHADOW_COLOR = new Color(0, 0, 0, 140);
private static final Color TOTAL_COLOR = new Color(190, 190, 190, 200);
/** Ring stroke thickness, as a fraction of the ring radius. */
private static final float RING_STROKE_FRACTION = 0.28f;
/**
* Pop-in scales are snapped to multiples of 1/this. Java2D rasterises and caches glyphs per transform, so a
* new scale every frame re-rasterises the word every frame; snapped, the ~20 scales a pop passes through are
* cached after their first use. 1/40 is at most a 1.25% size error: under half a pixel on the biggest word.
*/
static final float SCALE_STEPS = 40f;

private final Client client;
private final ComboScorer scorer;
private final SkateSession session;
private final ScoreClock clock;
private final ProgressionService progression;

/** The fonts, derived again only when the ring radius (and so the viewport height) changes. */
private int cachedRadius = -1;
private Font newestFont;
private Font olderFont;
private Font scoreFont;
private Font totalFont;
private Font calloutFont;
/** The callout's extra lines and the XP drop. */
private Font smallFont;
/** An older trick line's height plus the gap under it. */
private int olderStep;

{
setPosition(OverlayPosition.DYNAMIC);
setLayer(OverlayLayer.ABOVE_WIDGETS);
}

@Override
public Dimension render(Graphics2D g)
{
if (!session.isActive())
return null;
HudLayout layout = HudLayout.of(client, chatboxTop());
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
ensureFonts(g, layout.ringRadius);
int x = layout.stackLeftX;

float now = clock.now();
LiveStroke live = session.getLiveStroke();
boolean visualizer = FlickVisualizer.visible(live);
drawCluster(g, layout, now, visualizer);
if (visualizer)
drawFlickVisualizer(g, layout, live);
drawLandedExtras(g, layout, now);

// the level banner every ten levels, above the callout words
String banner = progression.getBannerText();
if (banner != null)
{
float age = progression.getBannerAge();
drawScaled(g, calloutFont, banner, layout.calloutCenterX, layout.bannerBaselineY, true,
new Color(255, 200, 40), HudAnim.bannerAlpha(age), HudAnim.calloutScale(age));
}

// the session total, always drawn while skating, and the daily goal under it (gold when just completed)
draw(g, totalFont, "Total " + String.format("%,d", scorer.sessionScore()), x, layout.totalY, TOTAL_COLOR, 1f);
String goal = progression.getGoalLine();
if (goal != null)
draw(g, totalFont, goal, x, layout.totalY + g.getFontMetrics(totalFont).getHeight() + HudLayout.LINE_GAP,
progression.isGoalFlashing() ? new Color(255, 200, 40, 230) : TOTAL_COLOR, 1f);

// "Manual 1.4s" in a manual: a fixed gap above the stack's worst-case height, so it never overlaps the names
String manual = session.getManualMeterText();
if (manual != null)
draw(g, olderFont, manual, x, layout.stackBaselineY - olderStep * (OLDER_TRICKS_SHOWN + 1),
new Color(255, 205, 90, 230), 1f);

// the near-miss flick hint ("Flick faster"): where the newest trick name goes when no combo shows, else
// above the tallest stack and the manual meter; drawn last, as it leaves its composite set
String hint = session.getTrickHintText();
float hintAlpha = session.getTrickHintAlpha();
if (hint != null && hintAlpha > 0f)
{
boolean idle = scorer.comboNames().isEmpty() && scorer.lastResultAge(now) >= FLASH_SECONDS;
g.setFont(olderFont);
g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, hintAlpha)));
// in controller mode the hint names the pad's buttons with drawn glyphs
ControllerGlyphs.drawText(g, hint, x, layout.stackBaselineY - (idle ? 0 : olderStep
* (OLDER_TRICKS_SHOWN + 2)), new Color(255, 225, 120), SHADOW_COLOR, 2);
}
return null;
}

/**
* Top edge of the chatbox on the canvas, from its widgets (overlays render on the client thread): the message
* area when shown, else the tab row (resizable with chat collapsed). In fixed mode both sit below the
* viewport, so the cluster stays put. {@link HudLayout#NO_OBSTACLE} when neither is shown.
*/
private int chatboxTop()
{
int top = HudLayout.NO_OBSTACLE;
for (int id : new int[]{InterfaceID.Chatbox.CHATAREA, InterfaceID.Chatbox.CONTROLS})
{
Widget w = client.getWidget(id);
Rectangle r = w == null || w.isHidden() ? null : w.getBounds();
if (r != null && r.width > 0 && r.height > 0)
top = Math.min(top, r.y);
}
return top;
}

private void ensureFonts(Graphics2D g, int r)
{
if (r == cachedRadius)
return;
cachedRadius = r;
Font bold = FontManager.getRunescapeBoldFont();
newestFont = bold.deriveFont((float) Math.round(r * 0.75f));
// secondary lines are at least 12: the RuneScape fonts get hard to read below it
olderFont = bold.deriveFont(Math.max(12f, Math.round(r * 0.5f)));
scoreFont = bold.deriveFont((float) Math.round(r * 1.25f));
totalFont = FontManager.getRunescapeFont().deriveFont(Math.max(11f, Math.round(r * HudLayout.TOTAL_FONT_FRACTION)));
calloutFont = bold.deriveFont((float) Math.round(r * 1.6f));
smallFont = bold.deriveFont(Math.max(12f, Math.round(r * 0.6f)));
olderStep = g.getFontMetrics(olderFont).getHeight() + HudLayout.LINE_GAP;
}

private void drawCluster(Graphics2D g, HudLayout layout, float now, boolean visualizer)
{
ComboScorer.Result result = scorer.lastResult();
float age = scorer.lastResultAge(now);
boolean landed = result.isLanded() && age < FLASH_SECONDS;
if (!landed && !(result.isBailed() && age < FLASH_SECONDS))
{
List<String> names = scorer.comboNames();
if (!names.isEmpty())
{
drawStack(g, layout, names, names.get(names.size() - 1), Color.WHITE, 1f,
HudAnim.popScale(now - scorer.newestNameTime()));
drawRing(g, layout, scorer.multiplier(), 1f, now - scorer.multiplierRiseTime());
drawScore(g, layout, String.valueOf(scorer.comboPoints() * scorer.multiplier()), Color.WHITE, false, 1f);
}
else if (visualizer)
// idle, the cluster hidden: the ring alone shows while the flick visualizer draws in it
drawRing(g, layout, 0, 1f, Float.MAX_VALUE);
return;
}

float fade = Math.max(0f, Math.min(1f, (FLASH_SECONDS - age) / 0.4f));
// the scorer has already reset: the finished combo shows with the result as its last line
List<String> names = new ArrayList<>(scorer.lastComboNames());
names.add("");
// green or red
Color color = landed ? new Color(90, 220, 110) : new Color(230, 70, 70);
// a bail shakes the whole cluster for a moment
int dx = landed ? 0 : HudAnim.shakeX(age);
int dy = landed ? 0 : HudAnim.shakeY(age);
g.translate(dx, dy);
String reason = landed ? null : session.getBailReasonText();
if (reason != null)
{
// "Bailed" moves up a line for the reason under it; one older name fewer keeps the stack's height
g.translate(0, -olderStep);
drawStack(g, layout, names.subList(Math.max(0, names.size() - OLDER_TRICKS_SHOWN), names.size()), "Bailed",
color, fade, HudAnim.popScale(age));
g.translate(0, olderStep);
draw(g, olderFont, reason, layout.stackLeftX, layout.stackBaselineY, new Color(255, 170, 160), fade);
}
else
drawStack(g, layout, names, landed ? result.clean ? "Clean landing" : "Landed" : "Bailed", color, fade,
HudAnim.popScale(age));
drawRing(g, layout, 0, fade, Float.MAX_VALUE);
drawScore(g, layout, String.valueOf(landed ? HudAnim.countUp(result.value, age) : result.value), color, !landed,
fade);
g.translate(-dx, -dy);
}

/**
* The OSRS-style XP-drop text for a landed combo worth {@code comboValue} points: the Skating XP it actually
* awards ({@link SkateLevels#xpForCombo}), not the combo score (which keeps its own, unchanged, display).
*/
static String xpDropText(int comboValue)
{
return "+" + String.format("%,d", SkateLevels.xpForCombo(comboValue));
}

/**
* After a landing: the XP drop floating up at the right and, for a big enough combo, the callout word with
* its extra lines. Both show even once a new combo has started.
*/
private void drawLandedExtras(Graphics2D g, HudLayout layout, float now)
{
ComboScorer.Result result = scorer.lastResult();
if (!result.isLanded() || result.value <= 0)
return;
float age = scorer.lastResultAge(now);
String xp = xpDropText(result.value);
draw(g, smallFont, xp, layout.xpDropRightX - g.getFontMetrics(smallFont).stringWidth(xp),
layout.xpDropStartY - Math.round(layout.xpDropRise * HudAnim.xpDropRise(age)), Color.WHITE,
HudAnim.xpDropAlpha(age));

float alpha = HudAnim.calloutAlpha(age);
if (alpha <= 0f)
return;
Callout word = Callout.forValue(result.value);
List<String> extras = new ArrayList<>(Callout.extras(word, result.clean, scorer.lastResultMultiplier(),
scorer.lastResultTrickCount()));
ComboScorer.Summary summary = scorer.lastSummary();
extras.addAll(Callout.varietyLines(summary.newTricks, summary.longCombo));
int y = layout.calloutBaselineY;
int step = g.getFontMetrics(smallFont).getHeight() + HudLayout.LINE_GAP;
if (word != null)
{
drawScaled(g, calloutFont, word.word, layout.calloutCenterX, y, true, word.color, alpha,
HudAnim.calloutScale(age));
y += step;
}
for (String line : extras)
{
drawScaled(g, smallFont, line, layout.calloutCenterX, y, true, new Color(255, 230, 150), alpha, 1f);
y += step;
}
}

/** Draws text scaled by {@code scale} about (x, baseline): its left end there, or its centre when centred. */
private static void drawScaled(Graphics2D g, Font font, String text, int x, int baseline, boolean centred,
Color color, float fade, float scale)
{
int left = centred ? -g.getFontMetrics(font).stringWidth(text) / 2 : 0;
AffineTransform saved = g.getTransform();
g.translate(x, baseline);
float snapped = quantiseScale(scale);
g.scale(snapped, snapped);
draw(g, font, text, left, 0, color, fade);
g.setTransform(saved);
}

/**
* Draws the vertical trick stack: up to {@link #OLDER_TRICKS_SHOWN} older names above the newest
* line, oldest most transparent, with {@code newestLabel} (and {@code newestColor}) always last, popping in.
*/
private void drawStack(Graphics2D g, HudLayout layout, List<String> names, String newestLabel,
Color newestColor, float fade, float newestScale)
{
// the newest trick itself is replaced by newestLabel during a flash, so only the names before it are older
int last = names.size() - 1;
int older = Math.min(OLDER_TRICKS_SHOWN, Math.max(0, last));
for (int rank = 0; rank < older; rank++)
// oldest the most transparent
draw(g, olderFont, names.get(last - older + rank), layout.stackLeftX,
layout.stackBaselineY - olderStep * (older - rank),
HudLayout.fade(new Color(220, 220, 220, 170), (rank + 1f) / (older + 1f)), fade);
drawScaled(g, newestFont, newestLabel, layout.stackLeftX, layout.stackBaselineY, false, newestColor, fade,
newestScale);
}

/** @param riseAge seconds since the multiplier last rose: the charge arc pulses thicker and flashes white */
private static void drawRing(Graphics2D g, HudLayout layout, int multiplier, float fade, float riseAge)
{
int r = layout.ringRadius;
float stroke = r * RING_STROKE_FRACTION;
double x = layout.ringCenterX - r + stroke / 2.0;
double y = layout.ringCenterY - r + stroke / 2.0;
double d = (r - stroke / 2.0) * 2;
Ellipse2D disc = new Ellipse2D.Double(x, y, d, d);
g.setColor(HudLayout.fade(new Color(20, 25, 35, 140), fade));
g.fill(disc);
g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
g.setColor(HudLayout.fade(new Color(150, 170, 200, 170), fade));
g.draw(disc);

// full at x10
float charge = Math.min(multiplier, 10f) / 10f;
if (charge > 0f)
{
float flash = HudAnim.ringFlash(riseAge);
g.setStroke(new BasicStroke(stroke * HudAnim.ringPulse(riseAge), BasicStroke.CAP_ROUND,
BasicStroke.JOIN_ROUND));
// the charge blue (120, 170, 255, 200) flashed that far towards opaque white
g.setColor(HudLayout.fade(new Color(Math.round(120 + 135 * flash), Math.round(170 + 85 * flash), 255,
Math.round(200 + 55 * flash)), fade));
g.draw(new Arc2D.Double(x, y, d, d, 90, -360 * charge, Arc2D.OPEN));
}
}

private void drawScore(Graphics2D g, HudLayout layout, String text, Color color, boolean strikethrough,
float fade)
{
FontMetrics metrics = g.getFontMetrics(scoreFont);
int y = layout.ringCenterY + (metrics.getAscent() - metrics.getDescent()) / 2;
draw(g, scoreFont, text, layout.scoreLeftX, y, color, fade);
if (strikethrough)
{
// in the faded colour draw left set
g.setStroke(new BasicStroke(Math.max(2f, scoreFont.getSize() * 0.08f)));
int strikeY = y - metrics.getAscent() / 3;
g.drawLine(layout.scoreLeftX, strikeY, layout.scoreLeftX + metrics.stringWidth(text), strikeY);
}
}

/**
* The live flick visualizer inside the ring: the recognised direction's wedge flashing, the last 300 ms of the
* mouse path fading, the wind-up's turnaround point, and a dot where the mouse is (tinted for a nollie).
*/
private static void drawFlickVisualizer(Graphics2D g, HudLayout layout, LiveStroke live)
{
int cx = layout.ringCenterX;
int cy = layout.ringCenterY;
int r = layout.ringRadius;
float inner = r * (1f - RING_STROKE_FRACTION / 2f);
float flash = live.firedDirection == null ? 0f : FlickVisualizer.flashAlpha(live.nowMs - live.firedMs);
if (flash > 0f)
{
double[] arc = FlickVisualizer.sector(live.firedDirection);
g.setColor(HudLayout.fade(live.firedNollie ? NOLLIE_TINT : FLICK_TINT, 150 / 255f * flash));
g.fill(new Arc2D.Double(cx - inner, cy - inner, 2 * inner, 2 * inner, arc[0], arc[1], Arc2D.PIE));
}
if (!live.held)
return;
g.setStroke(new BasicStroke(Math.max(1.5f, r * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
for (int i = 1; i < live.trailMs.length; i++)
{
float a = FlickVisualizer.trailAlpha(live.nowMs - live.trailMs[i]);
if (a > 0f)
{
float[] p = toRing(live, live.trailX[i - 1], live.trailY[i - 1], inner);
float[] q = toRing(live, live.trailX[i], live.trailY[i], inner);
g.setColor(HudLayout.fade(live.nollie ? NOLLIE_TINT : FLICK_TINT, 200 / 255f * a));
g.draw(new Line2D.Float(cx + p[0], cy + p[1], cx + q[0], cy + q[1]));
}
}
if (live.wound)
{
float[] e = toRing(live, live.extremeX, live.extremeY, inner);
float er = Math.max(2f, r * 0.1f);
g.setStroke(new BasicStroke(1f));
g.setColor(new Color(255, 255, 255, 110));
g.draw(new Ellipse2D.Float(cx + e[0] - er, cy + e[1] - er, 2 * er, 2 * er));
}
float[] d = toRing(live, live.x, live.y, inner);
float dr = Math.max(2.5f, r * 0.13f);
g.setColor(SHADOW_COLOR);
g.fill(new Ellipse2D.Float(cx + d[0] - dr + 1, cy + d[1] - dr + 1, 2 * dr, 2 * dr));
g.setColor(live.nollie ? NOLLIE_TINT : Color.WHITE);
g.fill(new Ellipse2D.Float(cx + d[0] - dr, cy + d[1] - dr, 2 * dr, 2 * dr));
}

/** A mouse position of the stroke in ring coordinates. */
private static float[] toRing(LiveStroke live, float x, float y, float inner)
{
return FlickVisualizer.toRing(x - live.originX, y - live.originY, live.reachPx, inner);
}

/** Text in {@code font}, faded by {@code fade} (0..1) with its shadow, so fading text leaves no shadow behind. */
private static void draw(Graphics2D g, Font font, String text, int x, int y, Color color, float fade)
{
g.setFont(font);
HudLayout.text(g, text, x, y, HudLayout.fade(color, fade), HudLayout.fade(SHADOW_COLOR, fade), 2);
}

/** {@code scale} snapped to the nearest multiple of 1/{@link #SCALE_STEPS} (1 stays exactly 1). */
static float quantiseScale(float scale)
{
return Math.round(scale * SCALE_STEPS) / SCALE_STEPS;
}
}
