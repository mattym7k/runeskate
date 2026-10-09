package com.gielinorskate.overlay;

import com.gielinorskate.duel.*;
import com.gielinorskate.party.GhostLabel;
import com.gielinorskate.party.PartyGhostService;
import com.gielinorskate.render.OffBoardPose;
import com.gielinorskate.session.SkateSession;
import java.awt.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/**
* The Skate Duel on screen, all drawn by this overlay (no real actor's health bar or hitsplats is used): both
* skaters' names and OSRS-style green / red HP bars at the top centre, the 3-2-1-FIGHT countdown, the KO
* banner, a challenge prompt, and over the opponent's ghost a small HP bar; OSRS-style hitsplats over the ghost
* and over the local skater.
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DuelOverlay extends Overlay
{
private static final Color PROMPT = new Color(255, 255, 0);
private static final Color MAX_EDGE = new Color(255, 230, 90);
/** Hitsplats sit about mid-body, this far (RuneLite z, down-negative) below the head, where the ghost label sits. */
private static final int BODY_HEIGHT = 110;
private static final int BAR_W = 120;
private static final int BAR_H = 8;
/** Where the 4 stacked hitsplats sit around the skater (as the game stacks them). */
private static final int[][] SLOT_OFFSETS = {{0, 0}, {0, -20}, {-16, -10}, {16, -10}};

private final Client client;
private final DuelService duel;
private final PartyGhostService ghosts;
private final SkateSession session;
private Font bigFont;
private Font bannerFont;
private Font splatFont;

{
setPosition(OverlayPosition.DYNAMIC);
setLayer(OverlayLayer.ABOVE_SCENE);
}

@Override
public Dimension render(Graphics2D g)
{
DuelService.Hud hud = duel.hud();
if (hud == null)
return null;
Font bold = FontManager.getRunescapeBoldFont();
if (bigFont == null)
{
bigFont = bold.deriveFont(48f);
bannerFont = bold.deriveFont(28f);
splatFont = bold.deriveFont(14f);
}
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
int cx = client.getViewportXOffset() + client.getViewportWidth() / 2;
int top = client.getViewportYOffset() + 8;
int midY = client.getViewportYOffset() + client.getViewportHeight() / 3;

drawOverGhost(g, hud);
if (!hud.mySplats.isEmpty())
{
OffBoardPose pose = session.getOffBoardPose();
if (pose != null)
splats(g, hud.mySplats, Perspective.localToCanvas(client, Math.round(pose.walkerX),
Math.round(pose.walkerY), Math.round(-pose.walkerH) - BODY_HEIGHT), hud.now);
}

int y = top;
g.setFont(bold);
if (hud.bars)
{
// both names, bars and HP at the top centre
FontMetrics fm = g.getFontMetrics();
int half = BAR_W + 24;
int h = fm.getHeight() + BAR_H + 12;
g.setColor(new Color(0, 0, 0, 150));
g.fillRoundRect(cx - half - 8, top, half * 2 + 16, h, 8, 8);
int nameY = top + 4 + fm.getAscent();
side(g, hud.myName, hud.myHp, cx - half + 4, nameY);
side(g, hud.oppName, hud.oppHp, cx + 20, nameY);
centred(g, "vs", cx, nameY + 4 + BAR_H, PROMPT);
y = top + h;
}
if (hud.prompt != null)
centred(g, hud.prompt, cx, y + g.getFontMetrics().getAscent() + 4, PROMPT);
if (hud.countdown != null)
{
g.setFont(bigFont);
// "FIGHT!" in red, the numbers in gold
centred(g, hud.countdown, cx, midY, hud.countdown.length() > 1 ? new Color(255, 80, 40)
: new Color(255, 215, 0));
}
if (hud.bannerOutcome != null && hud.bannerAlpha > 0f)
{
Color c = hud.bannerOutcome == DuelStateMachine.Outcome.WIN ? new Color(255, 152, 31)
: hud.bannerOutcome == DuelStateMachine.Outcome.LOSS ? new Color(230, 30, 30) : Color.WHITE;
g.setFont(bannerFont);
centred(g, hud.bannerTitle, cx, midY, HudLayout.fade(c, hud.bannerAlpha));
if (hud.bannerReason != null)
{
g.setFont(bold);
centred(g, hud.bannerReason, cx, midY + 24, HudLayout.fade(Color.WHITE, hud.bannerAlpha));
}
}
return null;
}

/** One skater's name, HP bar and HP number. */
private static void side(Graphics2D g, String name, int hp, int x, int nameY)
{
int barY = nameY + 4;
HudLayout.text(g, name, x, nameY, Color.WHITE, Color.BLACK, 1);
bar(g, x, barY, BAR_W - 28, BAR_H, hp);
HudLayout.text(g, Integer.toString(hp), x + BAR_W - 24, barY + BAR_H, Color.WHITE, Color.BLACK, 1);
}

/** An OSRS health bar: green for what is left, red for what is lost. */
private static void bar(Graphics2D g, int x, int y, int w, int h, int hp)
{
int fill = Math.round(w * Math.max(0, Math.min(DuelStateMachine.START_HP, hp))
/ (float) DuelStateMachine.START_HP);
g.setColor(Color.RED);
g.fillRect(x, y, w, h);
g.setColor(Color.GREEN);
g.fillRect(x, y, fill, h);
g.setColor(Color.BLACK);
g.drawRect(x - 1, y - 1, w + 1, h + 1);
}

private void drawOverGhost(Graphics2D g, DuelService.Hud hud)
{
boolean showBar = hud.phase == DuelStateMachine.Phase.COUNTDOWN || hud.phase == DuelStateMachine.Phase.FIGHT;
if (!showBar && hud.oppSplats.isEmpty())
return;
for (GhostLabel l : ghosts.getLabels())
{
if (l.memberId != hud.opponentId)
continue;
Point head = showBar ? Perspective.localToCanvas(client, l.x, l.y, l.z) : null;
if (head != null)
{
// a small bar above the ghost's name and trick lines
int lines = (l.name != null ? 1 : 0) + (l.trick != null ? 1 : 0);
int y = head.getY() - lines * g.getFontMetrics(FontManager.getRunescapeFont()).getHeight() - 4;
bar(g, head.getX() - 15, y, 30, 5, hud.oppHp);
}
// the label sits at the head, 240 above the feet
splats(g, hud.oppSplats, Perspective.localToCanvas(client, l.x, l.y, l.z + 240 - BODY_HEIGHT), hud.now);
return;
}
}

private void splats(Graphics2D g, List<DuelSplats.Splat> list, Point at, float now)
{
if (at == null)
return;
g.setFont(splatFont);
FontMetrics fm = g.getFontMetrics();
g.setStroke(new BasicStroke(1.5f));
for (DuelSplats.Splat s : list)
{
float a = s.alpha(now);
int x = at.getX() + SLOT_OFFSETS[s.slot][0];
int y = at.getY() + SLOT_OFFSETS[s.slot][1];
if (s.max)
{
// the max-hit splat: an orange burst behind the red splat, gold-edged
Polygon burst = burst(x, y);
g.setColor(HudLayout.fade(new Color(255, 140, 0), a));
g.fillPolygon(burst);
g.setColor(HudLayout.fade(MAX_EDGE, a));
g.drawPolygon(burst);
}
g.setColor(HudLayout.fade(new Color(170, 10, 10), a));
g.fillOval(x - 12, y - 11, 24, 22);
g.setColor(HudLayout.fade(s.max ? MAX_EDGE : new Color(70, 0, 0), a));
g.drawOval(x - 12, y - 11, 24, 22);
String n = Integer.toString(s.damage);
HudLayout.text(g, n, x - fm.stringWidth(n) / 2, y + fm.getAscent() / 2 - 1, HudLayout.fade(Color.WHITE, a),
HudLayout.fade(Color.BLACK, a), 1);
}
}

/** An 8-pointed star between radius 17 and 11 around (x, y). */
private static Polygon burst(int x, int y)
{
Polygon p = new Polygon();
for (int i = 0; i < 16; i++)
{
double ang = Math.PI * i / 8;
int r = i % 2 == 0 ? 17 : 11;
p.addPoint(x + (int) Math.round(Math.sin(ang) * r), y - (int) Math.round(Math.cos(ang) * r));
}
return p;
}

/** Centred over a black shadow 2 px down and right, faded as the text. */
private static void centred(Graphics2D g, String s, int cx, int y, Color c)
{
HudLayout.centred(g, s, cx, y, c, HudLayout.fade(Color.BLACK, c.getAlpha() / 255f), 2);
}
}
