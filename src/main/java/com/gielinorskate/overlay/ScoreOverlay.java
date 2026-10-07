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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Score HUD, placed inside the 3D viewport ({@link HudLayout}) so the minimap and chatbox never cover
 * it. Drawn as a single cluster in the lower-left of the viewport: a vertical stack of trick names
 * (oldest on top, newest on the bottom in a larger bold font), a charging ring meter below it, the
 * current combo score beside the ring, and the session total underneath. A brief LANDED/BAILED flash
 * replaces the newest line and the score when a combo resolves.
 *
 * <p>Animated ({@link HudAnim}): new trick lines pop in, the ring pulses when the multiplier rises, a landed
 * score counts up and a bail shakes the cluster. A landed combo also gets an OSRS-style XP drop ("+1,234") and,
 * when big enough, a callout word ({@link Callout}). Text uses the RuneScape fonts.
 */
@Singleton
public class ScoreOverlay extends Overlay
{
	private static final float FLASH_SECONDS = 1.5f;
	private static final float FLASH_FADE_SECONDS = 0.4f;
	/** How many older trick names (above the newest) are shown in the stack. */
	private static final int OLDER_TRICKS_SHOWN = 4;

	private static final Color NEWEST_COLOR = Color.WHITE;
	private static final Color OLDER_COLOR = new Color(220, 220, 220);
	private static final int OLDER_ALPHA = 170;
	private static final Color LANDED_COLOR = new Color(90, 220, 110);
	private static final Color BAILED_COLOR = new Color(230, 70, 70);
	private static final Color BAIL_REASON_COLOR = new Color(255, 170, 160);
	private static final Color HINT_COLOR = new Color(255, 225, 120);
	/** Flick visualizer: the trail and wedge of a regular flick, and of a nollie (push up first). */
	private static final Color FLICK_TINT = new Color(120, 200, 255);
	private static final Color NOLLIE_TINT = new Color(255, 170, 80);
	private static final Color SHADOW_COLOR = new Color(0, 0, 0, 140);
	private static final Color TOTAL_COLOR = new Color(190, 190, 190, 200);
	private static final Color MANUAL_COLOR = new Color(255, 205, 90, 230);
	private static final Color XP_DROP_COLOR = Color.WHITE;
	private static final Color CALLOUT_EXTRA_COLOR = new Color(255, 230, 150);
	private static final Color GOAL_DONE_COLOR = new Color(255, 200, 40, 230);
	/** The level banner: the game's level-up gold. */
	private static final Color BANNER_COLOR = new Color(255, 200, 40);

	private static final Color RING_TRACK_COLOR = new Color(150, 170, 200, 170);
	private static final Color RING_FILL_COLOR = new Color(20, 25, 35, 140);
	private static final Color RING_CHARGE_COLOR = new Color(120, 170, 255, 200);
	/** Ring stroke thickness, as a fraction of the ring radius. */
	private static final float RING_STROKE_FRACTION = 0.28f;
	/** Multiplier at which the charge arc is full. */
	private static final float CHARGE_MULTIPLIER_CAP = 10f;

	/** Font sizes, as a fraction of the ring radius. */
	private static final float NEWEST_FONT_FRACTION = 0.75f;
	private static final float OLDER_FONT_FRACTION = 0.5f;
	private static final float SCORE_FONT_FRACTION = 1.25f;
	private static final float TOTAL_FONT_FRACTION = HudLayout.TOTAL_FONT_FRACTION;
	private static final float TOTAL_FONT_MIN = HudLayout.TOTAL_FONT_MIN;
	private static final float CALLOUT_FONT_FRACTION = 1.6f;
	private static final float CALLOUT_EXTRA_FONT_FRACTION = 0.6f;
	private static final float XP_DROP_FONT_FRACTION = 0.6f;
	/** Smallest size for the secondary lines: the RuneScape fonts get hard to read below it. */
	private static final float SMALL_FONT_MIN = 12f;

	private static final int LINE_GAP = HudLayout.LINE_GAP;
	/** The chatbox's message area and tab row: the HUD cluster stays above whichever is shown. */
	private static final int[] CHATBOX_WIDGETS = {InterfaceID.Chatbox.CHATAREA, InterfaceID.Chatbox.CONTROLS};
	private static final int SHADOW_OFFSET = 2;
	/**
	 * Pop-in scales are snapped to multiples of 1/this. Java2D rasterises and caches glyphs per transform, so a
	 * new scale every frame re-rasterises the word every frame; snapped, the ~20 scales a pop passes through are
	 * cached after their first use. 1/40 is at most a 1.25% size error: under half a pixel on the biggest word.
	 */
	static final float SCALE_STEPS = 40f;

	private final Client client;
	private final ComboScorer scorer;
	private final BooleanSupplier isSkating;
	private final Supplier<Float> clock;
	private final Supplier<String> manualMeterText;

	private int cachedRadius = -1;
	private Font newestFont;
	private Font olderFont;
	private Font scoreFont;
	private Font totalFont;
	private Font calloutFont;
	private Font calloutExtraFont;
	private Font xpDropFont;

	/** Why the latest bail happened, drawn under "Bailed"; null when unknown. */
	private final Supplier<String> bailReason;
	/** The near-miss flick hint and its opacity. */
	private final Supplier<String> trickHint;
	private final Supplier<Float> trickHintAlpha;
	/** The flick stroke for the live visualizer; null when it is off. */
	private final Supplier<LiveStroke> liveStroke;

	/** The level banner showing (null when none) and its age. */
	private final Supplier<String> bannerText;
	private final Supplier<Float> bannerAge;

	/** The session-goal line (null when hidden), and whether it names a goal just completed. */
	private Supplier<String> goalLine = () -> null;
	private BooleanSupplier goalFlashing = () -> false;

	@Inject
	ScoreOverlay(Client client, ComboScorer scorer, SkateSession session, ScoreClock scoreClock,
		ProgressionService progression)
	{
		this(client, scorer, session::isActive, scoreClock::now, session::getManualMeterText,
			session::getBailReasonText, session::getTrickHintText, session::getTrickHintAlpha,
			session::getLiveStroke, progression::getBannerText, progression::getBannerAge);
		this.goalLine = progression::getGoalLine;
		this.goalFlashing = progression::isGoalFlashing;
	}

	ScoreOverlay(Client client, ComboScorer scorer, BooleanSupplier isSkating, Supplier<Float> clock,
		Supplier<String> manualMeterText, Supplier<String> bailReason, Supplier<String> trickHint,
		Supplier<Float> trickHintAlpha, Supplier<LiveStroke> liveStroke, Supplier<String> bannerText,
		Supplier<Float> bannerAge)
	{
		this.bannerText = bannerText;
		this.bannerAge = bannerAge;
		this.client = client;
		this.scorer = scorer;
		this.isSkating = isSkating;
		this.clock = clock;
		this.manualMeterText = manualMeterText;
		this.bailReason = bailReason;
		this.trickHint = trickHint;
		this.trickHintAlpha = trickHintAlpha;
		this.liveStroke = liveStroke;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!isSkating.getAsBoolean())
		{
			return null;
		}

		HudLayout layout = HudLayout.of(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight(), client.getCanvasWidth(), client.getCanvasHeight(),
			chatboxTop());
		ensureFonts(layout.ringRadius);

		Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		Object oldAntialiasingShapes = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try
		{
			float now = clock.get();
			LiveStroke live = liveStroke.get();
			boolean visualizer = FlickVisualizer.visible(live);
			drawCluster(graphics, layout, now, visualizer);
			if (visualizer)
			{
				drawFlickVisualizer(graphics, layout, live);
			}
			drawLandedExtras(graphics, layout, now);
			drawBanner(graphics, layout);
			drawSessionTotal(graphics, layout);
			drawGoalLine(graphics, layout);
			drawManualMeter(graphics, layout, manualMeterText.get());
			drawTrickHint(graphics, layout, now);
		}
		finally
		{
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				oldAntialiasing == null ? RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT : oldAntialiasing);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				oldAntialiasingShapes == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldAntialiasingShapes);
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
		for (int id : CHATBOX_WIDGETS)
		{
			Widget w = client.getWidget(id);
			if (w == null || w.isHidden())
			{
				continue;
			}
			Rectangle r = w.getBounds();
			if (r != null && r.width > 0 && r.height > 0)
			{
				top = Math.min(top, r.y);
			}
		}
		return top;
	}

	/** (Re)derives the cluster's fonts only when the ring radius (and so the viewport height) changes. */
	private void ensureFonts(int radius)
	{
		if (radius == cachedRadius)
		{
			return;
		}
		cachedRadius = radius;
		Font bold = FontManager.getRunescapeBoldFont();
		Font plain = FontManager.getRunescapeFont();
		newestFont = bold.deriveFont((float) Math.round(radius * NEWEST_FONT_FRACTION));
		olderFont = bold.deriveFont(Math.max(SMALL_FONT_MIN, Math.round(radius * OLDER_FONT_FRACTION)));
		scoreFont = bold.deriveFont((float) Math.round(radius * SCORE_FONT_FRACTION));
		totalFont = plain.deriveFont(Math.max(TOTAL_FONT_MIN, Math.round(radius * TOTAL_FONT_FRACTION)));
		calloutFont = bold.deriveFont((float) Math.round(radius * CALLOUT_FONT_FRACTION));
		calloutExtraFont = bold.deriveFont(Math.max(SMALL_FONT_MIN, Math.round(radius * CALLOUT_EXTRA_FONT_FRACTION)));
		xpDropFont = bold.deriveFont(Math.max(SMALL_FONT_MIN, Math.round(radius * XP_DROP_FONT_FRACTION)));
	}

	private void drawCluster(Graphics2D graphics, HudLayout layout, float now, boolean visualizer)
	{
		ComboScorer.Result result = scorer.lastResult();
		float age = scorer.lastResultAge(now);
		boolean landedFlash = result.isLanded() && age < FLASH_SECONDS;
		boolean bailedFlash = result.isBailed() && age < FLASH_SECONDS;

		List<String> names = scorer.comboNames();
		if (names.isEmpty() && !landedFlash && !bailedFlash)
		{
			// idle: no combo in progress and no flash to show, so the cluster stays hidden; the ring alone shows
			// while the flick visualizer draws in it
			if (visualizer)
			{
				drawRing(graphics, layout, 0, 1f, Float.MAX_VALUE);
			}
			return;
		}

		float fade = fadeAlpha(age);

		// during a flash the scorer has already reset; show the finished combo with the result as its last line
		List<String> flashNames = new ArrayList<>(scorer.lastComboNames());
		flashNames.add("");

		if (landedFlash)
		{
			drawStack(graphics, layout, flashNames, result.clean ? "Clean landing" : "Landed", LANDED_COLOR, fade,
				HudAnim.popScale(age));
			drawRing(graphics, layout, 0, fade, Float.MAX_VALUE);
			drawScore(graphics, layout, String.valueOf(HudAnim.countUp(result.value, age)), LANDED_COLOR, false, fade);
			return;
		}
		if (bailedFlash)
		{
			// the whole cluster shakes for a moment
			int dx = HudAnim.shakeX(age);
			int dy = HudAnim.shakeY(age);
			graphics.translate(dx, dy);
			try
			{
				String reason = bailReason.get();
				if (reason == null)
				{
					drawStack(graphics, layout, flashNames, "Bailed", BAILED_COLOR, fade, HudAnim.popScale(age));
				}
				else
				{
					// "Bailed" moves up a line for the reason under it; one older name fewer keeps the stack's height
					int lineStep = graphics.getFontMetrics(olderFont).getHeight() + LINE_GAP;
					List<String> shown = flashNames.subList(Math.max(0, flashNames.size() - OLDER_TRICKS_SHOWN),
						flashNames.size());
					graphics.translate(0, -lineStep);
					try
					{
						drawStack(graphics, layout, shown, "Bailed", BAILED_COLOR, fade, HudAnim.popScale(age));
					}
					finally
					{
						graphics.translate(0, lineStep);
					}
					drawShadowedString(graphics, olderFont, reason, layout.stackLeftX, layout.stackBaselineY,
						withAlpha(BAIL_REASON_COLOR, Math.round(255 * fade)), fade);
				}
				drawRing(graphics, layout, 0, fade, Float.MAX_VALUE);
				drawScore(graphics, layout, String.valueOf(result.value), BAILED_COLOR, true, fade);
			}
			finally
			{
				graphics.translate(-dx, -dy);
			}
			return;
		}

		drawStack(graphics, layout, names, names.get(names.size() - 1), NEWEST_COLOR, 1f,
			HudAnim.popScale(now - scorer.newestNameTime()));
		drawRing(graphics, layout, scorer.multiplier(), 1f, now - scorer.multiplierRiseTime());
		int comboValue = scorer.comboPoints() * scorer.multiplier();
		drawScore(graphics, layout, String.valueOf(comboValue), NEWEST_COLOR, false, 1f);
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
	private void drawLandedExtras(Graphics2D graphics, HudLayout layout, float now)
	{
		ComboScorer.Result result = scorer.lastResult();
		if (!result.isLanded() || result.value <= 0)
		{
			return;
		}
		float age = scorer.lastResultAge(now);

		float xpAlpha = HudAnim.xpDropAlpha(age);
		if (xpAlpha > 0f)
		{
			String text = xpDropText(result.value);
			FontMetrics metrics = graphics.getFontMetrics(xpDropFont);
			int x = layout.xpDropRightX - metrics.stringWidth(text);
			int y = layout.xpDropStartY - Math.round(layout.xpDropRise * HudAnim.xpDropRise(age));
			drawShadowedString(graphics, xpDropFont, text, x, y, withAlpha(XP_DROP_COLOR, Math.round(255 * xpAlpha)),
				xpAlpha);
		}

		float alpha = HudAnim.calloutAlpha(age);
		if (alpha <= 0f)
		{
			return;
		}
		Callout word = Callout.forValue(result.value);
		List<String> extras = new ArrayList<>(Callout.extras(word, result.clean, scorer.lastResultMultiplier(),
			scorer.lastResultTrickCount()));
		ComboScorer.Summary summary = scorer.lastSummary();
		extras.addAll(Callout.varietyLines(summary.newTricks, summary.longCombo));
		int y = layout.calloutBaselineY;
		FontMetrics extraMetrics = graphics.getFontMetrics(calloutExtraFont);
		if (word != null)
		{
			drawCentredScaled(graphics, calloutFont, word.word, layout.calloutCenterX, y,
				withAlpha(word.color, Math.round(255 * alpha)), alpha, HudAnim.calloutScale(age));
			y += extraMetrics.getHeight() + LINE_GAP;
		}
		for (String line : extras)
		{
			drawCentredScaled(graphics, calloutExtraFont, line, layout.calloutCenterX, y,
				withAlpha(CALLOUT_EXTRA_COLOR, Math.round(255 * alpha)), alpha, 1f);
			y += extraMetrics.getHeight() + LINE_GAP;
		}
	}

	/** The level banner every ten levels, above the callout words. */
	private void drawBanner(Graphics2D graphics, HudLayout layout)
	{
		String text = bannerText.get();
		if (text == null)
		{
			return;
		}
		float age = bannerAge.get();
		float alpha = HudAnim.bannerAlpha(age);
		if (alpha > 0f)
		{
			drawCentredScaled(graphics, calloutFont, text, layout.calloutCenterX, layout.bannerBaselineY,
				withAlpha(BANNER_COLOR, Math.round(255 * alpha)), alpha, HudAnim.calloutScale(age));
		}
	}

	/** Draws {@code text} centred on {@code cx}, scaled about its centre on the baseline by {@code scale}. */
	private void drawCentredScaled(Graphics2D graphics, Font font, String text, int cx, int baseline, Color color,
		float fade, float scale)
	{
		int width = graphics.getFontMetrics(font).stringWidth(text);
		AffineTransform saved = graphics.getTransform();
		try
		{
			graphics.translate(cx, baseline);
			float snapped = quantiseScale(scale);
			graphics.scale(snapped, snapped);
			drawShadowedString(graphics, font, text, -width / 2, 0, color, fade);
		}
		finally
		{
			graphics.setTransform(saved);
		}
	}

	/**
	 * Draws the vertical trick stack: up to {@link #OLDER_TRICKS_SHOWN} older names above the newest
	 * line, oldest most transparent, with {@code newestLabel} (and {@code newestColor}) always last.
	 */
	private void drawStack(Graphics2D graphics, HudLayout layout, List<String> names, String newestLabel,
		Color newestColor, float fade, float newestScale)
	{
		int size = names.size();
		// the newest trick itself is replaced by newestLabel during a flash, so only the names before
		// it count as "older" lines
		int olderCount = Math.min(OLDER_TRICKS_SHOWN, Math.max(0, size - 1));
		int olderFrom = size - 1 - olderCount;

		FontMetrics olderMetrics = graphics.getFontMetrics(olderFont);
		FontMetrics newestMetrics = graphics.getFontMetrics(newestFont);
		int lineStep = olderMetrics.getHeight() + LINE_GAP;

		int y = layout.stackBaselineY - lineStep * olderCount;
		for (int i = olderFrom; i < size - 1; i++)
		{
			// oldest (smallest index within this window) is the most transparent
			int rank = i - olderFrom;
			float ageFade = (rank + 1f) / (olderCount + 1f);
			int alpha = Math.round(OLDER_ALPHA * ageFade * fade);
			drawShadowedString(graphics, olderFont, names.get(i), layout.stackLeftX, y,
				withAlpha(OLDER_COLOR, alpha), fade);
			y += lineStep;
		}

		// the newest line pops in, scaled about its left end on the baseline
		AffineTransform saved = graphics.getTransform();
		try
		{
			graphics.translate(layout.stackLeftX, layout.stackBaselineY);
			float snapped = quantiseScale(newestScale);
			graphics.scale(snapped, snapped);
			drawShadowedString(graphics, newestFont, newestLabel, 0, 0, withAlpha(newestColor, Math.round(255 * fade)),
				fade);
		}
		finally
		{
			graphics.setTransform(saved);
		}
	}

	/** @param riseAge seconds since the multiplier last rose: the charge arc pulses thicker and flashes white */
	private void drawRing(Graphics2D graphics, HudLayout layout, int multiplier, float fade, float riseAge)
	{
		int cx = layout.ringCenterX;
		int cy = layout.ringCenterY;
		int radius = layout.ringRadius;
		float stroke = radius * RING_STROKE_FRACTION;
		float flash = HudAnim.ringFlash(riseAge);

		Ellipse2D disc = new Ellipse2D.Double(cx - radius + stroke / 2.0, cy - radius + stroke / 2.0,
			(radius - stroke / 2.0) * 2, (radius - stroke / 2.0) * 2);
		graphics.setColor(withAlpha(RING_FILL_COLOR, Math.round(RING_FILL_COLOR.getAlpha() * fade)));
		graphics.fill(disc);

		Ellipse2D track = new Ellipse2D.Double(cx - radius + stroke / 2.0, cy - radius + stroke / 2.0,
			(radius - stroke / 2.0) * 2, (radius - stroke / 2.0) * 2);
		graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(withAlpha(RING_TRACK_COLOR, Math.round(RING_TRACK_COLOR.getAlpha() * fade)));
		graphics.draw(track);

		float charge = Math.min(multiplier, CHARGE_MULTIPLIER_CAP) / CHARGE_MULTIPLIER_CAP;
		if (charge > 0f)
		{
			Arc2D arc = new Arc2D.Double(cx - radius + stroke / 2.0, cy - radius + stroke / 2.0,
				(radius - stroke / 2.0) * 2, (radius - stroke / 2.0) * 2, 90, -360 * charge, Arc2D.OPEN);
			graphics.setStroke(new BasicStroke(stroke * HudAnim.ringPulse(riseAge), BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND));
			int baseAlpha = RING_CHARGE_COLOR.getAlpha();
			graphics.setColor(withAlpha(blend(RING_CHARGE_COLOR, Color.WHITE, flash),
				Math.round((baseAlpha + (255 - baseAlpha) * flash) * fade)));
			graphics.draw(arc);
		}
	}

	private void drawScore(Graphics2D graphics, HudLayout layout, String text, Color color, boolean strikethrough,
		float fade)
	{
		FontMetrics metrics = graphics.getFontMetrics(scoreFont);
		int y = layout.scoreCenterY + (metrics.getAscent() - metrics.getDescent()) / 2;
		Color faded = withAlpha(color, Math.round(255 * fade));
		drawShadowedString(graphics, scoreFont, text, layout.scoreLeftX, y, faded, fade);
		if (strikethrough)
		{
			graphics.setStroke(new BasicStroke(Math.max(2f, scoreFont.getSize() * 0.08f)));
			graphics.setColor(faded);
			int strikeY = y - metrics.getAscent() / 3;
			graphics.drawLine(layout.scoreLeftX, strikeY, layout.scoreLeftX + metrics.stringWidth(text), strikeY);
		}
	}

	/** "Manual 1.4s" while the skater is in a manual, just above the trick stack; nothing otherwise. */
	private void drawManualMeter(Graphics2D graphics, HudLayout layout, String text)
	{
		if (text == null)
		{
			return;
		}
		FontMetrics olderMetrics = graphics.getFontMetrics(olderFont);
		int lineStep = olderMetrics.getHeight() + LINE_GAP;
		// a fixed gap above the stack's worst-case height, so the meter never overlaps the trick names
		int y = layout.stackBaselineY - lineStep * (OLDER_TRICKS_SHOWN + 1);
		drawShadowedString(graphics, olderFont, text, layout.stackLeftX, y, MANUAL_COLOR, 1f);
	}

	/**
	 * The near-miss flick hint ("Flick faster"): where the newest trick name goes when no combo shows, else above
	 * the tallest stack (and the manual meter).
	 */
	private void drawTrickHint(Graphics2D graphics, HudLayout layout, float now)
	{
		String text = trickHint.get();
		float alpha = trickHintAlpha.get();
		if (text == null || alpha <= 0f)
		{
			return;
		}
		boolean clusterIdle = scorer.comboNames().isEmpty() && scorer.lastResultAge(now) >= FLASH_SECONDS;
		int lineStep = graphics.getFontMetrics(olderFont).getHeight() + LINE_GAP;
		int y = clusterIdle ? layout.stackBaselineY : layout.stackBaselineY - lineStep * (OLDER_TRICKS_SHOWN + 2);
		if (ControllerGlyphs.hasGlyphs(text))
		{
			// controller mode: the hint names the pad's buttons with drawn glyphs
			graphics.setFont(olderFont);
			Composite oldComposite = graphics.getComposite();
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
			try
			{
				ControllerGlyphs.drawText(graphics, text, layout.stackLeftX, y, HINT_COLOR, SHADOW_COLOR, SHADOW_OFFSET);
			}
			finally
			{
				graphics.setComposite(oldComposite);
			}
			return;
		}
		drawShadowedString(graphics, olderFont, text, layout.stackLeftX, y,
			withAlpha(HINT_COLOR, Math.round(255 * alpha)), alpha);
	}

	/**
	 * The live flick visualizer inside the ring: the recognised direction's wedge flashing, the last 300 ms of the
	 * mouse path fading, the wind-up's turnaround point, and a dot where the mouse is (tinted for a nollie).
	 */
	private void drawFlickVisualizer(Graphics2D graphics, HudLayout layout, LiveStroke live)
	{
		int cx = layout.ringCenterX;
		int cy = layout.ringCenterY;
		float inner = layout.ringRadius * (1f - RING_STROKE_FRACTION / 2f);
		Stroke oldStroke = graphics.getStroke();
		try
		{
			float flash = live.firedDirection == null ? 0f : FlickVisualizer.flashAlpha(live.nowMs - live.firedMs);
			if (flash > 0f)
			{
				double[] arc = FlickVisualizer.sector(live.firedDirection);
				Color wedge = live.firedNollie ? NOLLIE_TINT : FLICK_TINT;
				graphics.setColor(withAlpha(wedge, Math.round(150 * flash)));
				graphics.fill(new Arc2D.Double(cx - inner, cy - inner, 2 * inner, 2 * inner, arc[0], arc[1], Arc2D.PIE));
			}
			if (!live.held)
			{
				return;
			}
			Color tint = live.nollie ? NOLLIE_TINT : FLICK_TINT;
			float width = Math.max(1.5f, layout.ringRadius * 0.08f);
			graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (int i = 1; i < live.trailMs.length; i++)
			{
				float a = FlickVisualizer.trailAlpha(live.nowMs - live.trailMs[i]);
				if (a <= 0f)
				{
					continue;
				}
				float[] p = FlickVisualizer.toRing(live.trailX[i - 1] - live.originX, live.trailY[i - 1] - live.originY,
					live.reachPx, inner);
				float[] q = FlickVisualizer.toRing(live.trailX[i] - live.originX, live.trailY[i] - live.originY,
					live.reachPx, inner);
				graphics.setColor(withAlpha(tint, Math.round(200 * a)));
				graphics.draw(new Line2D.Float(cx + p[0], cy + p[1], cx + q[0], cy + q[1]));
			}
			if (live.wound)
			{
				float[] e = FlickVisualizer.toRing(live.extremeX - live.originX, live.extremeY - live.originY,
					live.reachPx, inner);
				float er = Math.max(2f, layout.ringRadius * 0.1f);
				graphics.setStroke(new BasicStroke(1f));
				graphics.setColor(withAlpha(Color.WHITE, 110));
				graphics.draw(new Ellipse2D.Float(cx + e[0] - er, cy + e[1] - er, 2 * er, 2 * er));
			}
			float[] d = FlickVisualizer.toRing(live.x - live.originX, live.y - live.originY, live.reachPx, inner);
			float dr = Math.max(2.5f, layout.ringRadius * 0.13f);
			graphics.setColor(SHADOW_COLOR);
			graphics.fill(new Ellipse2D.Float(cx + d[0] - dr + 1, cy + d[1] - dr + 1, 2 * dr, 2 * dr));
			graphics.setColor(live.nollie ? NOLLIE_TINT : Color.WHITE);
			graphics.fill(new Ellipse2D.Float(cx + d[0] - dr, cy + d[1] - dr, 2 * dr, 2 * dr));
		}
		finally
		{
			graphics.setStroke(oldStroke);
		}
	}

	/** Always drawn while skating; never placed in the top/minimap area. */
	private void drawSessionTotal(Graphics2D graphics, HudLayout layout)
	{
		String text = "Total " + String.format("%,d", scorer.sessionScore());
		drawShadowedString(graphics, totalFont, text, layout.stackLeftX, layout.totalY, TOTAL_COLOR, 1f);
	}

	/** The session goal in progress (or the one just completed, in gold), one line under the session total. */
	private void drawGoalLine(Graphics2D graphics, HudLayout layout)
	{
		String text = goalLine.get();
		if (text == null)
		{
			return;
		}
		int y = layout.totalY + graphics.getFontMetrics(totalFont).getHeight() + LINE_GAP;
		drawShadowedString(graphics, totalFont, text, layout.stackLeftX, y,
			goalFlashing.getAsBoolean() ? GOAL_DONE_COLOR : TOTAL_COLOR, 1f);
	}

	/** @param fade 0..1, applied to the shadow too so fading text does not leave its shadow behind */
	private void drawShadowedString(Graphics2D graphics, Font font, String text, int x, int y, Color color, float fade)
	{
		graphics.setFont(font);
		graphics.setColor(withAlpha(SHADOW_COLOR, Math.round(SHADOW_COLOR.getAlpha() * fade)));
		graphics.drawString(text, x + SHADOW_OFFSET, y + SHADOW_OFFSET);
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	/** {@code scale} snapped to the nearest multiple of 1/{@link #SCALE_STEPS} (1 stays exactly 1). */
	static float quantiseScale(float scale)
	{
		return Math.round(scale * SCALE_STEPS) / SCALE_STEPS;
	}

	/** Linear fade over the last {@link #FLASH_FADE_SECONDS} of the {@link #FLASH_SECONDS} flash window. */
	private static float fadeAlpha(float age)
	{
		float fadeStart = FLASH_SECONDS - FLASH_FADE_SECONDS;
		if (age <= fadeStart)
		{
			return 1f;
		}
		float remaining = (FLASH_SECONDS - age) / FLASH_FADE_SECONDS;
		return Math.max(0f, Math.min(1f, remaining));
	}

	private static Color blend(Color from, Color to, float t)
	{
		float u = Math.max(0f, Math.min(1f, t));
		return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * u),
			Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * u),
			Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * u));
	}

	private static Color withAlpha(Color color, int alpha)
	{
		int clamped = Math.max(0, Math.min(255, alpha));
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
	}
}
