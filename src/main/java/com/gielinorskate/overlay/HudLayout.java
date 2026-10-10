package com.gielinorskate.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import net.runelite.api.Client;

/**
 * Score HUD layout anchors, relative to the 3D viewport rather than the canvas: the canvas corners hold
 * the minimap (top right, resizable mode) and the chatbox (bottom left/centre). Pure, apart from the shared text
 * drawing helpers of the overlays at the bottom.
 *
 * <p>The HUD is a single cluster in the lower-left of the viewport: a vertical stack of trick names
 * (newest at the bottom), a ring meter below it, the combo score to the right of the ring, and the
 * session total below the ring. Callout words ("Nice!") show centred in the upper viewport and XP drops float
 * up at its right, clear of the minimap.
 */
final class HudLayout
{
	/** No chatbox (or other widget) under the cluster. */
	static final int NO_OBSTACLE = Integer.MAX_VALUE;
	/** The session total and goal line font: this fraction of the ring radius, at least 11. */
	static final float TOTAL_FONT_FRACTION = 0.42f;
	/** Gap between the session total and the goal line under it (and between other stacked lines). */
	static final int LINE_GAP = 3;

	/** The viewport (the whole canvas when the client's is empty). */
	final int vx;
	final int vy;
	final int vw;
	final int vh;
	/** Ring centre, and its radius (scaled with viewport height and clamped to 22..48 px). */
	final int ringCenterX;
	final int ringCenterY;
	final int ringRadius;
	/** Left edge of the ring; the trick stack is left-aligned with it. */
	final int stackLeftX;
	/** Baseline of the newest (bottom) trick name line; older lines stack upward from here. */
	final int stackBaselineY;
	/** Left edge of the combo score number, vertically centred on the ring. */
	final int scoreLeftX;
	/** Baseline of the session total line below the ring cluster, left-aligned with the ring's left edge. */
	final int totalY;
	/** Top of the tallest trick stack (newest plus four older names); other HUD stays above this. */
	final int stackTopY;
	/** Bottom of the cluster's lowest text (the goal line under the session total). */
	final int clusterBottomY;
	/** Centre and baseline of the callout word ("Nice!"). */
	final int calloutCenterX;
	final int calloutBaselineY;
	/** Baseline of the level banner, centred on {@link #calloutCenterX}. */
	final int bannerBaselineY;
	/** Right edge and starting baseline of an XP drop, and how far it rises. */
	final int xpDropRightX;
	final int xpDropStartY;
	final int xpDropRise;

	/**
	 * From the client's viewport rectangle (the whole canvas when the viewport is empty), with the ring cluster
	 * (trick stack, ring, score, session total and goal line) lifted as one so its bottom stays 4 px above
	 * {@code obstacleTopY}, the top edge of the chatbox ({@link #NO_OBSTACLE} when there is none). It never rises
	 * past the viewport top. Nothing else moves.
	 */
	HudLayout(int vx, int vy, int vw, int vh, int canvasW, int canvasH, int obstacleTopY)
	{
		if (vw <= 0 || vh <= 0)
		{
			vx = 0;
			vy = 0;
			vw = canvasW;
			vh = canvasH;
		}
		this.vx = vx;
		this.vy = vy;
		this.vw = vw;
		this.vh = vh;
		int r = Math.max(22, Math.min(48, Math.round(vh * 0.045f)));
		ringRadius = r;
		// the ring's left edge 9% of the viewport width in, its centre 78% down
		ringCenterX = vx + Math.round(vw * 0.09f) + r;
		stackLeftX = ringCenterX - r;
		scoreLeftX = ringCenterX + r + Math.round(r * 0.6f);
		int centerY = vy + Math.round(vh * 0.78f);
		int baseline = centerY - r - Math.round(r * 0.35f);
		int total = centerY + r + Math.round(r * 0.55f);
		float totalFont = Math.max(11f, Math.round(r * TOTAL_FONT_FRACTION));
		// a line is 1.3 and a descent 0.3 of the font size (generous for the RuneScape fonts)
		int bottom = total + Math.round(totalFont * 1.3f) + LINE_GAP + Math.round(totalFont * 0.3f);
		// the full trick stack above its baseline: ScoreOverlay draws the newest name at 0.75 r and up to 4 older
		// names at 0.5 r, each a font height (~0.65 r) plus a 3 px gap: 0.75 + 4 * 0.65 = 3.35 r, plus 4 * 3 px
		int stackHeight = Math.round(r * 3.35f) + 12;
		// no lift without a chatbox: the difference is then hugely negative
		int lift = Math.max(0, Math.min(bottom + 4 - obstacleTopY, baseline - stackHeight - vy));
		ringCenterY = centerY - lift;
		stackBaselineY = baseline - lift;
		totalY = total - lift;
		clusterBottomY = bottom - lift;
		stackTopY = stackBaselineY - stackHeight;
		calloutCenterX = vx + vw / 2;
		calloutBaselineY = vy + Math.round(vh * 0.3f);
		bannerBaselineY = vy + Math.round(vh * 0.17f);
		// in resizable mode the viewport is the whole canvas and the minimap with its orbs (about 210 px wide)
		// covers the top right, so XP drops end left of it, where the game's own drops are; else 8 px in
		xpDropRightX = vx + vw - (vx <= 0 && vw >= canvasW ? 240 : 8);
		xpDropStartY = vy + Math.round(vh * 0.32f);
		xpDropRise = Math.round(vh * 0.18f);
	}

	/** The layout of the client's viewport now. */
	static HudLayout of(Client c, int obstacleTopY)
	{
		return new HudLayout(c.getViewportXOffset(), c.getViewportYOffset(), c.getViewportWidth(), c.getViewportHeight(),
			c.getCanvasWidth(), c.getCanvasHeight(), obstacleTopY);
	}

	/** Draws {@code s} at (x, y) over its shadow, {@code off} px down and right. */
	static void text(Graphics2D g, String s, int x, int y, Color color, Color shadow, int off)
	{
		g.setColor(shadow);
		g.drawString(s, x + off, y + off);
		g.setColor(color);
		g.drawString(s, x, y);
	}

	/** {@link #text} centred on {@code cx}, in the graphics' font. */
	static void centred(Graphics2D g, String s, int cx, int y, Color color, Color shadow, int off)
	{
		text(g, s, cx - g.getFontMetrics().stringWidth(s) / 2, y, color, shadow, off);
	}

	/** {@code c} with its alpha times {@code f}, clamped to 0..255. */
	static Color fade(Color c, float f)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(c.getAlpha() * f))));
	}
}
