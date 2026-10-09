package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HudLayoutTest
{
	@Test
	public void fixedModeViewportClampsRadiusToMinimum()
	{
		// fixed-mode viewport: 512 x 334 at (4, 4) on a 765 x 503 canvas
		// radius = round(334 * 0.045) = 15, clamped up to the 22px minimum
		HudLayout l = new HudLayout(4, 4, 512, 334, 765, 503, HudLayout.NO_OBSTACLE);
		assertEquals(22, l.ringRadius);
		assertEquals(4 + Math.round(512 * 0.09f) + 22, l.ringCenterX);
		assertEquals(4 + Math.round(334 * 0.78f), l.ringCenterY);
		assertEquals(l.ringCenterX - 22, l.stackLeftX);
		assertEquals(l.ringCenterY - 22 - Math.round(22 * 0.35f), l.stackBaselineY);
		assertEquals(l.ringCenterX + 22 + Math.round(22 * 0.6f), l.scoreLeftX);
		assertEquals(l.ringCenterY, l.ringCenterY);
		assertEquals(l.ringCenterY + 22 + Math.round(22 * 0.55f), l.totalY);
	}

	@Test
	public void resizableViewportIsNotTheCanvasCorner()
	{
		// radius = round(900 * 0.045) = 41, within the 22..48 clamp range
		HudLayout l = new HudLayout(0, 0, 1600, 900, 1600, 900, HudLayout.NO_OBSTACLE);
		assertEquals(41, l.ringRadius);
		assertEquals(Math.round(1600 * 0.09f) + 41, l.ringCenterX);
		assertEquals(Math.round(900 * 0.78f), l.ringCenterY);
		assertEquals(l.ringCenterX - 41, l.stackLeftX);
		assertEquals(l.ringCenterY - 41 - Math.round(41 * 0.35f), l.stackBaselineY);
		assertEquals(l.ringCenterX + 41 + Math.round(41 * 0.6f), l.scoreLeftX);
		assertEquals(l.ringCenterY + 41 + Math.round(41 * 0.55f), l.totalY);
	}

	@Test
	public void fallsBackToTheCanvasWithoutAViewport()
	{
		HudLayout l = new HudLayout(0, 0, 0, 0, 800, 600, HudLayout.NO_OBSTACLE);
		// radius = round(600 * 0.045) = 27, within the clamp range
		assertEquals(27, l.ringRadius);
		assertEquals(Math.round(800 * 0.09f) + 27, l.ringCenterX);
		assertEquals(Math.round(600 * 0.78f), l.ringCenterY);
		assertEquals(l.ringCenterX - 27, l.stackLeftX);
		assertEquals(l.ringCenterX + 27 + Math.round(27 * 0.6f), l.scoreLeftX);
	}

	@Test
	public void ringRadiusClampsToTheMaximumOnATallViewport()
	{
		HudLayout l = new HudLayout(0, 0, 3000, 2000, 3000, 2000, HudLayout.NO_OBSTACLE);
		assertEquals(48, l.ringRadius);
	}

	@Test
	public void calloutSitsCentredInTheUpperViewport()
	{
		HudLayout l = new HudLayout(4, 4, 512, 334, 765, 503, HudLayout.NO_OBSTACLE);
		assertEquals(4 + 256, l.calloutCenterX);
		assertEquals(4 + Math.round(334 * 0.3f), l.calloutBaselineY);
	}

	@Test
	public void xpDropsStayClearOfTheResizableMinimap()
	{
		// resizable: the viewport is the whole canvas and the minimap covers its top-right corner
		HudLayout resizable = new HudLayout(0, 0, 1600, 900, 1600, 900, HudLayout.NO_OBSTACLE);
		assertEquals(1600 - 240, resizable.xpDropRightX);
		// fixed: the minimap is outside the viewport
		HudLayout fixed = new HudLayout(4, 4, 512, 334, 765, 503, HudLayout.NO_OBSTACLE);
		assertEquals(4 + 512 - 8, fixed.xpDropRightX);
		assertEquals(4 + Math.round(334 * 0.32f), fixed.xpDropStartY);
		assertEquals(Math.round(334 * 0.18f), fixed.xpDropRise);
	}

	@Test
	public void withoutAChatboxTheClusterIsWhereItAlwaysWas()
	{
		HudLayout plain = new HudLayout(0, 0, 1600, 800, 1600, 800, HudLayout.NO_OBSTACLE);
		HudLayout none = new HudLayout(0, 0, 1600, 800, 1600, 800, HudLayout.NO_OBSTACLE);
		assertEquals(plain.ringCenterY, none.ringCenterY);
		assertEquals(plain.totalY, none.totalY);
		assertEquals(plain.stackBaselineY, none.stackBaselineY);
	}

	@Test
	public void resizableClusterRisesAboveTheChatbox()
	{
		// 800 px tall resizable canvas, chatbox top at 800 - 165 = 635: the old cluster ran into it
		HudLayout plain = new HudLayout(0, 0, 1600, 800, 1600, 800, HudLayout.NO_OBSTACLE);
		assertTrue(plain.clusterBottomY > 635);
		HudLayout l = new HudLayout(0, 0, 1600, 800, 1600, 800, 635);
		assertTrue(l.clusterBottomY <= 635 - 4);
		int shift = plain.ringCenterY - l.ringCenterY;
		assertTrue(shift > 0);
		// the whole cluster moves together; nothing else moves
		assertEquals(plain.totalY - shift, l.totalY);
		assertEquals(plain.stackBaselineY - shift, l.stackBaselineY);
		assertEquals(plain.stackTopY - shift, l.stackTopY);
		assertEquals(plain.ringCenterY - shift, l.ringCenterY);
		assertEquals(plain.ringCenterX, l.ringCenterX);
		assertEquals(plain.calloutBaselineY, l.calloutBaselineY);
		assertEquals(plain.xpDropStartY, l.xpDropStartY);
	}

	@Test
	public void aChatboxBelowTheClusterMovesNothing()
	{
		// fixed mode: the chatbox sits below the viewport
		HudLayout plain = new HudLayout(4, 4, 512, 334, 765, 503, HudLayout.NO_OBSTACLE);
		HudLayout l = new HudLayout(4, 4, 512, 334, 765, 503, 338);
		assertEquals(plain.ringCenterY, l.ringCenterY);
		assertEquals(plain.totalY, l.totalY);
	}

	@Test
	public void collapsedChatOnlyLiftsAboveTheTabRow()
	{
		// resizable with chat hidden: only the tab row (about 23 px) is left at the bottom
		HudLayout l = new HudLayout(0, 0, 1600, 800, 1600, 800, 777);
		assertTrue(l.clusterBottomY <= 777 - 4);
		HudLayout chatOpen = new HudLayout(0, 0, 1600, 800, 1600, 800, 635);
		assertTrue(l.ringCenterY > chatOpen.ringCenterY);
	}

	@Test
	public void neverRisesPastTheViewportTop()
	{
		// a tiny viewport with the chatbox almost at its top: the stack top stops at the viewport edge
		HudLayout l = new HudLayout(0, 0, 800, 300, 800, 300, 20);
		assertEquals(0, l.stackTopY);
	}
}
