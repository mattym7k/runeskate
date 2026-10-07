package com.gielinorskate.overlay;

import com.gielinorskate.leaderboard.RunService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The timed run's HUD, at the top centre of the 3D viewport: the 3-2-1 countdown (large, mid-screen), the run's
 * 2:00 clock with the points banked so far, and the result banner with its PB marker.
 */
@Singleton
public class RunOverlay extends Overlay
{
	private static final Color TIMER_COLOR = Color.WHITE;
	private static final Color TIMER_LOW_COLOR = new Color(255, 120, 90);
	private static final Color SCORE_COLOR = new Color(255, 225, 120);
	private static final Color COUNTDOWN_COLOR = new Color(255, 200, 40);
	private static final Color PB_COLOR = new Color(90, 220, 110);
	private static final Color SHADOW = new Color(0, 0, 0, 160);
	/** Under this many seconds left, the clock turns red. */
	private static final int LOW_SECONDS = 10;
	/** Fraction of the viewport height: the clock's baseline, the result banner's, the countdown's. */
	private static final float TIMER_Y = 0.07f;
	private static final float BANNER_Y = 0.24f;
	private static final float COUNTDOWN_Y = 0.42f;
	private static final float FADE_SECONDS = 0.6f;

	private final Client client;
	private final RunService runs;
	private int cachedHeight = -1;
	private Font timerFont;
	private Font smallFont;
	private Font bigFont;

	@Inject
	RunOverlay(Client client, RunService runs)
	{
		this.client = client;
		this.runs = runs;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String timer = runs.timerText();
		String banner = runs.bannerScore();
		if (timer == null && banner == null)
		{
			return null;
		}
		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		int vw = client.getViewportWidth();
		int vh = client.getViewportHeight();
		ensureFonts(vh);
		int cx = vx + vw / 2;
		Object oldAa = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		try
		{
			if (timer != null)
			{
				int timerY = vy + Math.round(vh * TIMER_Y) + timerFont.getSize() / 2;
				boolean low = timer.startsWith("0:") && Integer.parseInt(timer.substring(2)) <= LOW_SECONDS;
				centred(g, timerFont, timer, cx, timerY, low ? TIMER_LOW_COLOR : TIMER_COLOR, 1f);
				centred(g, smallFont, String.format("%,d", runs.runScore()), cx, timerY + smallFont.getSize() + 4,
					SCORE_COLOR, 1f);
				String countdown = runs.countdownText();
				if (countdown != null)
				{
					centred(g, bigFont, countdown, cx, vy + Math.round(vh * COUNTDOWN_Y), COUNTDOWN_COLOR, 1f);
				}
			}
			if (banner != null)
			{
				float age = runs.bannerAge();
				float alpha = Math.min(1f, Math.min(age / 0.2f, (RunService.BANNER_SECONDS - age) / FADE_SECONDS));
				int y = vy + Math.round(vh * BANNER_Y);
				centred(g, timerFont, "Run over: " + banner, cx, y, SCORE_COLOR, alpha);
				if (runs.bannerIsPb())
				{
					centred(g, smallFont, "New personal best!", cx, y + smallFont.getSize() + 4, PB_COLOR, alpha);
				}
			}
		}
		finally
		{
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				oldAa == null ? RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT : oldAa);
		}
		return null;
	}

	private void ensureFonts(int viewportHeight)
	{
		if (viewportHeight == cachedHeight)
		{
			return;
		}
		cachedHeight = viewportHeight;
		Font bold = FontManager.getRunescapeBoldFont();
		timerFont = bold.deriveFont(Math.max(16f, viewportHeight * 0.045f));
		smallFont = bold.deriveFont(Math.max(12f, viewportHeight * 0.026f));
		bigFont = bold.deriveFont(Math.max(32f, viewportHeight * 0.14f));
	}

	private static void centred(Graphics2D g, Font font, String text, int cx, int baseline, Color color, float alpha)
	{
		if (alpha <= 0f)
		{
			return;
		}
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int x = cx - fm.stringWidth(text) / 2;
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
		g.setColor(new Color(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(), SHADOW.getAlpha() * a / 255));
		g.drawString(text, x + 2, baseline + 2);
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() * a / 255));
		g.drawString(text, x, baseline);
	}
}
