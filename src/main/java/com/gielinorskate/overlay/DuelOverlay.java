package com.gielinorskate.overlay;

import com.gielinorskate.duel.DuelService;
import com.gielinorskate.duel.DuelSplats;
import com.gielinorskate.duel.DuelStateMachine;
import com.gielinorskate.party.GhostLabel;
import com.gielinorskate.party.PartyGhostService;
import com.gielinorskate.render.OffBoardPose;
import com.gielinorskate.session.SkateSession;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The Skate Duel on screen, all drawn by this overlay (no real actor's health bar or hitsplats is used): both
 * skaters' names and OSRS-style green / red HP bars at the top centre, the 3-2-1-FIGHT countdown, the KO
 * banner, a challenge prompt, and over the opponent's ghost a small HP bar; OSRS-style hitsplats over the ghost
 * and over the local skater.
 */
@Singleton
public class DuelOverlay extends Overlay
{
	private static final Color HP_GREEN = new Color(0, 255, 0);
	private static final Color HP_RED = new Color(255, 0, 0);
	private static final Color PANEL_BG = new Color(0, 0, 0, 150);
	private static final Color TEXT = Color.WHITE;
	private static final Color PROMPT = new Color(255, 255, 0);
	private static final Color COUNT = new Color(255, 215, 0);
	private static final Color FIGHT = new Color(255, 80, 40);
	private static final Color WIN = new Color(255, 152, 31);
	private static final Color LOSS = new Color(230, 30, 30);
	private static final Color SPLAT = new Color(170, 10, 10);
	private static final Color SPLAT_EDGE = new Color(70, 0, 0);
	private static final Color MAX_FLARE = new Color(255, 140, 0);
	private static final Color MAX_EDGE = new Color(255, 230, 90);
	/** RuneLite z (down-negative) above the feet: the head, where the ghost label sits. */
	private static final int HEAD_HEIGHT = 240;
	/** Hitsplats sit about mid-body. */
	private static final int BODY_HEIGHT = 110;
	private static final int BAR_W = 120;
	private static final int BAR_H = 8;
	private static final int GHOST_BAR_W = 30;
	private static final int GHOST_BAR_H = 5;
	/** Where the 4 stacked hitsplats sit around the skater (as the game stacks them). */
	private static final int[][] SLOT_OFFSETS = {{0, 0}, {0, -20}, {-16, -10}, {16, -10}};

	private final Client client;
	private final DuelService duel;
	private final PartyGhostService ghosts;
	private final SkateSession session;
	private Font bigFont;
	private Font bannerFont;
	private Font splatFont;

	@Inject
	DuelOverlay(Client client, DuelService duel, PartyGhostService ghosts, SkateSession session)
	{
		this.client = client;
		this.duel = duel;
		this.ghosts = ghosts;
		this.session = session;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		DuelService.Hud hud = duel.hud();
		if (hud == null)
		{
			return null;
		}
		fonts();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int cx = client.getViewportXOffset() + client.getViewportWidth() / 2;
		int top = client.getViewportYOffset() + 8;
		int midY = client.getViewportYOffset() + client.getViewportHeight() / 3;

		drawOverGhost(g, hud);
		drawOverSelf(g, hud);

		int y = top;
		if (hud.bars)
		{
			y = drawBars(g, hud, cx, top);
		}
		if (hud.prompt != null)
		{
			g.setFont(FontManager.getRunescapeBoldFont());
			shadowed(g, hud.prompt, cx, y + g.getFontMetrics().getAscent() + 4, PROMPT);
		}
		if (hud.countdown != null)
		{
			g.setFont(bigFont);
			shadowed(g, hud.countdown, cx, midY, hud.countdown.length() > 1 ? FIGHT : COUNT);
		}
		if (hud.bannerOutcome != null && hud.bannerAlpha > 0f)
		{
			Color c = hud.bannerOutcome == DuelStateMachine.Outcome.WIN ? WIN
				: hud.bannerOutcome == DuelStateMachine.Outcome.LOSS ? LOSS : TEXT;
			g.setFont(bannerFont);
			shadowed(g, hud.bannerTitle, cx, midY, alpha(c, hud.bannerAlpha));
			if (hud.bannerReason != null)
			{
				g.setFont(FontManager.getRunescapeBoldFont());
				shadowed(g, hud.bannerReason, cx, midY + 24, alpha(TEXT, hud.bannerAlpha));
			}
		}
		return null;
	}

	private void fonts()
	{
		if (bigFont == null)
		{
			Font bold = FontManager.getRunescapeBoldFont();
			bigFont = bold.deriveFont(48f);
			bannerFont = bold.deriveFont(28f);
			splatFont = bold.deriveFont(14f);
		}
	}

	/** Both names, bars and HP at the top centre; returns the y under them. */
	private int drawBars(Graphics2D g, DuelService.Hud hud, int cx, int top)
	{
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int half = BAR_W + 24;
		int h = fm.getHeight() + BAR_H + 12;
		g.setColor(PANEL_BG);
		g.fillRoundRect(cx - half - 8, top, half * 2 + 16, h, 8, 8);
		int nameY = top + 4 + fm.getAscent();
		int barY = nameY + 4;
		side(g, fm, hud.myName, hud.myHp, cx - half + 4, nameY, barY);
		side(g, fm, hud.oppName, hud.oppHp, cx + 20, nameY, barY);
		shadowed(g, "vs", cx, barY + BAR_H, PROMPT);
		return top + h;
	}

	private static void side(Graphics2D g, FontMetrics fm, String name, int hp, int x, int nameY, int barY)
	{
		g.setColor(Color.BLACK);
		g.drawString(name, x + 1, nameY + 1);
		g.setColor(TEXT);
		g.drawString(name, x, nameY);
		bar(g, x, barY, BAR_W - 28, BAR_H, hp);
		String n = Integer.toString(hp);
		int nx = x + BAR_W - 24;
		g.setColor(Color.BLACK);
		g.drawString(n, nx + 1, barY + BAR_H + 1);
		g.setColor(TEXT);
		g.drawString(n, nx, barY + BAR_H);
	}

	/** An OSRS health bar: green for what is left, red for what is lost. */
	private static void bar(Graphics2D g, int x, int y, int w, int h, int hp)
	{
		int fill = Math.round(w * Math.max(0, Math.min(DuelStateMachine.START_HP, hp))
			/ (float) DuelStateMachine.START_HP);
		g.setColor(HP_RED);
		g.fillRect(x, y, w, h);
		g.setColor(HP_GREEN);
		g.fillRect(x, y, fill, h);
		g.setColor(Color.BLACK);
		g.drawRect(x - 1, y - 1, w + 1, h + 1);
	}

	private void drawOverGhost(Graphics2D g, DuelService.Hud hud)
	{
		boolean showBar = hud.phase == DuelStateMachine.Phase.COUNTDOWN || hud.phase == DuelStateMachine.Phase.FIGHT;
		if (!showBar && hud.oppSplats.isEmpty())
		{
			return;
		}
		for (GhostLabel l : ghosts.getLabels())
		{
			if (l.memberId != hud.opponentId)
			{
				continue;
			}
			if (showBar)
			{
				Point head = Perspective.localToCanvas(client, l.x, l.y, l.z);
				if (head != null)
				{
					int lines = (l.name != null ? 1 : 0) + (l.trick != null ? 1 : 0);
					int y = head.getY() - lines * g.getFontMetrics(FontManager.getRunescapeFont()).getHeight() - 4;
					bar(g, head.getX() - GHOST_BAR_W / 2, y, GHOST_BAR_W, GHOST_BAR_H, hud.oppHp);
				}
			}
			Point body = Perspective.localToCanvas(client, l.x, l.y, l.z + HEAD_HEIGHT - BODY_HEIGHT);
			if (body != null)
			{
				splats(g, hud.oppSplats, body, hud.now);
			}
			return;
		}
	}

	private void drawOverSelf(Graphics2D g, DuelService.Hud hud)
	{
		if (hud.mySplats.isEmpty())
		{
			return;
		}
		OffBoardPose pose = session.getOffBoardPose();
		if (pose == null)
		{
			return;
		}
		Point body = Perspective.localToCanvas(client, Math.round(pose.walkerX), Math.round(pose.walkerY),
			Math.round(-pose.walkerH) - BODY_HEIGHT);
		if (body != null)
		{
			splats(g, hud.mySplats, body, hud.now);
		}
	}

	private void splats(Graphics2D g, List<DuelSplats.Splat> list, Point at, float now)
	{
		g.setFont(splatFont);
		FontMetrics fm = g.getFontMetrics();
		Stroke old = g.getStroke();
		g.setStroke(new BasicStroke(1.5f));
		for (DuelSplats.Splat s : list)
		{
			float a = s.alpha(now);
			int x = at.getX() + SLOT_OFFSETS[s.slot][0];
			int y = at.getY() + SLOT_OFFSETS[s.slot][1];
			if (s.max)
			{
				// the max-hit splat: an orange burst behind the red splat, gold-edged
				g.setColor(alpha(MAX_FLARE, a));
				g.fillPolygon(burst(x, y, 17, 11, 8));
				g.setColor(alpha(MAX_EDGE, a));
				g.drawPolygon(burst(x, y, 17, 11, 8));
			}
			g.setColor(alpha(SPLAT, a));
			g.fillOval(x - 12, y - 11, 24, 22);
			g.setColor(alpha(s.max ? MAX_EDGE : SPLAT_EDGE, a));
			g.drawOval(x - 12, y - 11, 24, 22);
			String n = Integer.toString(s.damage);
			int tx = x - fm.stringWidth(n) / 2;
			int ty = y + fm.getAscent() / 2 - 1;
			g.setColor(alpha(Color.BLACK, a));
			g.drawString(n, tx + 1, ty + 1);
			g.setColor(alpha(TEXT, a));
			g.drawString(n, tx, ty);
		}
		g.setStroke(old);
	}

	/** A star of {@code points} points between radius {@code outer} and {@code inner}, around (x, y). */
	private static Polygon burst(int x, int y, int outer, int inner, int points)
	{
		Polygon p = new Polygon();
		for (int i = 0; i < points * 2; i++)
		{
			double ang = Math.PI * i / points;
			int r = i % 2 == 0 ? outer : inner;
			p.addPoint(x + (int) Math.round(Math.sin(ang) * r), y - (int) Math.round(Math.cos(ang) * r));
		}
		return p;
	}

	private static void shadowed(Graphics2D g, String s, int centreX, int y, Color c)
	{
		FontMetrics fm = g.getFontMetrics();
		int x = centreX - fm.stringWidth(s) / 2;
		g.setColor(alpha(Color.BLACK, c.getAlpha() / 255f));
		g.drawString(s, x + 2, y + 2);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private static Color alpha(Color c, float alpha)
	{
		int a = Math.max(0, Math.min(255, Math.round(alpha * c.getAlpha())));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}
}
