package com.gielinorskate.overlay;

import com.gielinorskate.leaderboard.RunService;
import java.awt.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/**
* The timed run's HUD, at the top centre of the 3D viewport: the 3-2-1 countdown (large, mid-screen), the run's
* 2:00 clock with the points banked so far, and the result banner with its PB marker.
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunOverlay extends Overlay
{
private static final Color SCORE_COLOR = new Color(255, 225, 120);

private final Client client;
private final RunService runs;
private int cachedHeight = -1;
private Font timerFont;
private Font smallFont;
private Font bigFont;

{
setPosition(OverlayPosition.DYNAMIC);
setLayer(OverlayLayer.ABOVE_WIDGETS);
}

@Override
public Dimension render(Graphics2D g)
{
String timer = runs.timerText();
String banner = runs.bannerScore();
if (timer == null && banner == null)
return null;
int vy = client.getViewportYOffset();
int vh = client.getViewportHeight();
if (vh != cachedHeight)
{
cachedHeight = vh;
Font bold = FontManager.getRunescapeBoldFont();
timerFont = bold.deriveFont(Math.max(16f, vh * 0.045f));
smallFont = bold.deriveFont(Math.max(12f, vh * 0.026f));
bigFont = bold.deriveFont(Math.max(32f, vh * 0.14f));
}
int cx = client.getViewportXOffset() + client.getViewportWidth() / 2;
int small = smallFont.getSize() + 4;
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
if (timer != null)
{
// the clock 7% down the viewport, red under 10 seconds left
int timerY = vy + Math.round(vh * 0.07f) + timerFont.getSize() / 2;
boolean low = timer.startsWith("0:") && Integer.parseInt(timer.substring(2)) <= 10;
centred(g, timerFont, timer, cx, timerY, low ? new Color(255, 120, 90) : Color.WHITE, 1f);
centred(g, smallFont, String.format("%,d", runs.runScore()), cx, timerY + small, SCORE_COLOR, 1f);
String countdown = runs.countdownText();
if (countdown != null)
centred(g, bigFont, countdown, cx, vy + Math.round(vh * 0.42f), new Color(255, 200, 40), 1f);
}
if (banner != null)
{
// in over 0.2 s, out over the last 0.6 s, 24% down
float age = runs.bannerAge();
float alpha = Math.min(1f, Math.min(age / 0.2f, (RunService.BANNER_SECONDS - age) / 0.6f));
int y = vy + Math.round(vh * 0.24f);
centred(g, timerFont, "Run over: " + banner, cx, y, SCORE_COLOR, alpha);
if (runs.bannerIsPb())
centred(g, smallFont, "New personal best!", cx, y + small, new Color(90, 220, 110), alpha);
}
return null;
}

private static void centred(Graphics2D g, Font font, String text, int cx, int baseline, Color color, float alpha)
{
g.setFont(font);
HudLayout.centred(g, text, cx, baseline, HudLayout.fade(color, alpha), HudLayout.fade(new Color(0, 0, 0, 160),
alpha), 2);
}
}
