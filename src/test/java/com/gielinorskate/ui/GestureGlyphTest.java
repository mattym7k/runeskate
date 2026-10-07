package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class GestureGlyphTest
{
	private static GestureGlyph glyph(Direction d, boolean nollie, float turn)
	{
		return GestureGlyph.of(new Gesture(d, nollie, turn));
	}

	@Test
	public void aRegularWindUpPullsDownIntoTheHookAndANolliePushesUp()
	{
		GestureGlyph regular = glyph(Direction.UP, false, 0f);
		assertTrue("starts above the hook", regular.windUpStart[1] < regular.hook[1]);
		GestureGlyph nollie = glyph(Direction.DOWN, true, 0f);
		assertTrue("starts below the hook", nollie.windUpStart[1] > nollie.hook[1]);
	}

	@Test
	public void theFlickPointsTheGesturesWay()
	{
		for (Direction d : Direction.values())
		{
			GestureGlyph g = glyph(d, false, 0f);
			float[] v = GestureGlyph.vector(d);
			double dx = g.flickEnd[0] - g.hook[0];
			double dy = g.flickEnd[1] - g.hook[1];
			double len = Math.hypot(dx, dy);
			double cos = (dx * v[0] + dy * v[1]) / len;
			assertTrue(d + " cos " + cos, cos > 0.99);
			assertFalse(g.curved);
		}
	}

	@Test
	public void everyPointStaysInTheBox()
	{
		for (Direction d : Direction.values())
		{
			for (boolean nollie : new boolean[]{false, true})
			{
				for (float turn : new float[]{0f, 90f, 180f})
				{
					GestureGlyph g = glyph(d, nollie, turn);
					for (float[] p : new float[][]{g.windUpStart, g.hook, g.control, g.flickEnd})
					{
						assertTrue(d + " " + p[0] + "," + p[1], p[0] >= 0f && p[0] <= 1f && p[1] >= 0f && p[1] <= 1f);
					}
				}
			}
		}
	}

	@Test
	public void curvedFlicksBendMoreForA360()
	{
		GestureGlyph straight = glyph(Direction.UP_LEFT, false, 0f);
		GestureGlyph varial = glyph(Direction.UP_LEFT, false, 90f);
		GestureGlyph full = glyph(Direction.UP_LEFT, false, 180f);
		assertTrue(varial.curved);
		assertTrue(full.curved);
		double mid = 0.5 * (straight.hook[0] + straight.flickEnd[0]);
		assertEquals(mid, straight.control[0], 1e-5);
		double bendVarial = offLine(varial);
		double bendFull = offLine(full);
		assertTrue(bendVarial > 0.05);
		assertTrue(bendFull > bendVarial);
	}

	/** Distance of the control point from the straight hook-to-end line. */
	private static double offLine(GestureGlyph g)
	{
		double dx = g.flickEnd[0] - g.hook[0];
		double dy = g.flickEnd[1] - g.hook[1];
		double cx = g.control[0] - g.hook[0];
		double cy = g.control[1] - g.hook[1];
		return Math.abs(dx * cy - dy * cx) / Math.hypot(dx, dy);
	}

	@Test
	public void theArrowheadSitsAtTheFlicksEnd()
	{
		GestureGlyph g = glyph(Direction.RIGHT, false, 0f);
		// pointing right: both sides of the head lie behind (left of) the tip, one above, one below
		assertTrue(g.arrowLeft[0] < g.flickEnd[0]);
		assertTrue(g.arrowRight[0] < g.flickEnd[0]);
		assertTrue((g.arrowLeft[1] - g.flickEnd[1]) * (g.arrowRight[1] - g.flickEnd[1]) < 0);
	}

	@Test
	public void painterDrawsSomethingAndRestoresState()
	{
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		Color before = g.getColor();
		GestureGlyphPainter.paint(g, new Gesture(Direction.UP_LEFT, false, 0f, true), 0, 0, 32, Color.GRAY,
			Color.WHITE);
		assertEquals(before, g.getColor());
		g.dispose();
		int drawn = 0;
		for (int y = 0; y < 32; y++)
		{
			for (int x = 0; x < 32; x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					drawn++;
				}
			}
		}
		assertTrue("pixels drawn " + drawn, drawn > 30);
	}
}
