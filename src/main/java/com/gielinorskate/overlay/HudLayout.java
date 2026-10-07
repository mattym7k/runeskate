package com.gielinorskate.overlay;

/**
 * Score HUD layout anchors, relative to the 3D viewport rather than the canvas: the canvas corners hold
 * the minimap (top right, resizable mode) and the chatbox (bottom left/centre). Pure.
 *
 * <p>The HUD is a single cluster in the lower-left of the viewport: a vertical stack of trick names
 * (newest at the bottom), a ring meter below it, the combo score to the right of the ring, and the
 * session total below the ring. Callout words ("Nice!") show centred in the upper viewport and XP drops float
 * up at its right, clear of the minimap.
 */
final class HudLayout
{
	/** Fraction of viewport width from the left edge to the ring's horizontal anchor. */
	static final float RING_X_FRACTION = 0.09f;
	/** Fraction of viewport height down to the ring's vertical centre. */
	static final float RING_Y_FRACTION = 0.78f;
	/** Ring radius as a fraction of viewport height, before clamping. */
	static final float RING_RADIUS_FRACTION = 0.045f;
	static final int RING_RADIUS_MIN = 22;
	static final int RING_RADIUS_MAX = 48;
	/** Gap between the top of the ring and the trick stack's bottom baseline, as a fraction of radius. */
	static final float STACK_GAP_FRACTION = 0.35f;
	/** Extra gap beyond the ring's right edge before the score number, as a fraction of radius. */
	static final float SCORE_GAP_FRACTION = 0.6f;
	/** Gap below the ring's bottom edge to the session total line, as a fraction of radius. */
	static final float TOTAL_GAP_FRACTION = 0.55f;
	/**
	 * Height of the full trick stack above its baseline, as a fraction of the radius: ScoreOverlay draws the
	 * newest name at 0.75 r (ascent at most ~0.75 r) and up to 4 older names at 0.5 r, each a font height
	 * (~1.3 * size = 0.65 r) plus a 3 px gap: 0.75 + 4 * 0.65 = 3.35 r, plus {@link #STACK_LINE_GAPS} px.
	 */
	static final float STACK_HEIGHT_FRACTION = 3.35f;
	static final int STACK_LINE_GAPS = 4 * 3;
	/** Callout words: centred, this fraction of the viewport height down. */
	static final float CALLOUT_Y_FRACTION = 0.3f;
	/** The level banner ("Skating level 50!"): centred, this fraction of the viewport height down. */
	static final float BANNER_Y_FRACTION = 0.17f;
	/** XP drops start this fraction of the viewport height down and rise {@link #XP_DROP_RISE_FRACTION}. */
	static final float XP_DROP_Y_FRACTION = 0.32f;
	static final float XP_DROP_RISE_FRACTION = 0.18f;
	/** Gap between the XP drop's right edge and the viewport's when the minimap is outside it (fixed mode). */
	static final int XP_DROP_EDGE_MARGIN = 8;
	/**
	 * In resizable mode the viewport is the whole canvas and the minimap with its orbs (about 210 px wide)
	 * covers the top right, so XP drops end left of it, where the game's own drops are.
	 */
	static final int XP_DROP_MINIMAP_MARGIN = 240;
	/** The session total and goal line font: this fraction of the ring radius, at least {@link #TOTAL_FONT_MIN}. */
	static final float TOTAL_FONT_FRACTION = 0.42f;
	static final float TOTAL_FONT_MIN = 11f;
	/** A text line's height and descent as fractions of its font size (generous for the RuneScape fonts). */
	static final float LINE_HEIGHT_FRACTION = 1.3f;
	static final float DESCENT_FRACTION = 0.3f;
	/** Gap between the session total and the goal line under it. */
	static final int LINE_GAP = 3;
	/** No chatbox (or other widget) under the cluster. */
	static final int NO_OBSTACLE = Integer.MAX_VALUE;
	/** Gap kept between the cluster's bottom and the top of the chatbox. */
	static final int OBSTACLE_MARGIN = 4;

	/** Ring centre. */
	final int ringCenterX;
	final int ringCenterY;
	/** Ring radius, scaled with viewport height and clamped to {@link #RING_RADIUS_MIN}/{@link #RING_RADIUS_MAX}. */
	final int ringRadius;
	/** Left edge of the ring; the trick stack is left-aligned with it. */
	final int stackLeftX;
	/** Baseline of the newest (bottom) trick name line; older lines stack upward from here. */
	final int stackBaselineY;
	/** Left edge of the combo score number, vertically centred on the ring. */
	final int scoreLeftX;
	final int scoreCenterY;
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

	private HudLayout(int ringCenterX, int ringCenterY, int ringRadius, int stackLeftX, int stackBaselineY,
		int scoreLeftX, int scoreCenterY, int totalY, int clusterBottomY, int calloutCenterX, int calloutBaselineY, int bannerBaselineY,
		int xpDropRightX, int xpDropStartY, int xpDropRise)
	{
		this.calloutCenterX = calloutCenterX;
		this.calloutBaselineY = calloutBaselineY;
		this.bannerBaselineY = bannerBaselineY;
		this.xpDropRightX = xpDropRightX;
		this.xpDropStartY = xpDropStartY;
		this.xpDropRise = xpDropRise;
		this.stackTopY = stackBaselineY - Math.round(ringRadius * STACK_HEIGHT_FRACTION) - STACK_LINE_GAPS;
		this.ringCenterX = ringCenterX;
		this.ringCenterY = ringCenterY;
		this.ringRadius = ringRadius;
		this.stackLeftX = stackLeftX;
		this.stackBaselineY = stackBaselineY;
		this.scoreLeftX = scoreLeftX;
		this.scoreCenterY = scoreCenterY;
		this.totalY = totalY;
		this.clusterBottomY = clusterBottomY;
	}

	/** From the client's viewport rectangle; falls back to the whole canvas when the viewport is empty. */
	static HudLayout of(int vx, int vy, int vw, int vh, int canvasW, int canvasH)
	{
		return of(vx, vy, vw, vh, canvasW, canvasH, NO_OBSTACLE);
	}

	/**
	 * As {@link #of(int, int, int, int, int, int)}, with the ring cluster (trick stack, ring, score, session total
	 * and goal line) lifted as one so its bottom stays {@link #OBSTACLE_MARGIN} above {@code obstacleTopY}, the
	 * top edge of the chatbox ({@link #NO_OBSTACLE} when there is none). It never rises past the viewport top.
	 * Nothing else moves.
	 */
	static HudLayout of(int vx, int vy, int vw, int vh, int canvasW, int canvasH, int obstacleTopY)
	{
		if (vw <= 0 || vh <= 0)
		{
			vx = 0;
			vy = 0;
			vw = canvasW;
			vh = canvasH;
		}

		int radius = Math.round(vh * RING_RADIUS_FRACTION);
		radius = Math.max(RING_RADIUS_MIN, Math.min(RING_RADIUS_MAX, radius));

		int centerX = vx + Math.round(vw * RING_X_FRACTION) + radius;
		int centerY = vy + Math.round(vh * RING_Y_FRACTION);
		int left = centerX - radius;
		int baseline = centerY - radius - Math.round(radius * STACK_GAP_FRACTION);
		int scoreLeft = centerX + radius + Math.round(radius * SCORE_GAP_FRACTION);
		int total = centerY + radius + Math.round(radius * TOTAL_GAP_FRACTION);

		float totalFont = Math.max(TOTAL_FONT_MIN, Math.round(radius * TOTAL_FONT_FRACTION));
		int bottom = total + Math.round(totalFont * LINE_HEIGHT_FRACTION) + LINE_GAP
			+ Math.round(totalFont * DESCENT_FRACTION);
		int stackTop = baseline - Math.round(radius * STACK_HEIGHT_FRACTION) - STACK_LINE_GAPS;
		if (obstacleTopY != NO_OBSTACLE)
		{
			int lift = bottom + OBSTACLE_MARGIN - obstacleTopY;
			lift = Math.min(lift, stackTop - vy);
			if (lift > 0)
			{
				centerY -= lift;
				baseline -= lift;
				total -= lift;
				bottom -= lift;
			}
		}

		boolean minimapInside = vx <= 0 && vw >= canvasW;
		int xpRight = vx + vw - (minimapInside ? XP_DROP_MINIMAP_MARGIN : XP_DROP_EDGE_MARGIN);

		return new HudLayout(centerX, centerY, radius, left, baseline, scoreLeft, centerY, total, bottom,
			vx + vw / 2, vy + Math.round(vh * CALLOUT_Y_FRACTION), vy + Math.round(vh * BANNER_Y_FRACTION),
			xpRight, vy + Math.round(vh * XP_DROP_Y_FRACTION), Math.round(vh * XP_DROP_RISE_FRACTION));
	}
}
