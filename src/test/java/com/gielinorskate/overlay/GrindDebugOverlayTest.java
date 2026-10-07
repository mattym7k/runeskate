package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.gielinorskate.world.GrindSegment;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class GrindDebugOverlayTest
{
	/** Top-down test projection: canvas = (x / 10, y / 10); the height must be passed through as given. */
	private float lastH;

	private Point project(float x, float y, float h)
	{
		lastH = h;
		return new Point(Math.round(x / 10f), Math.round(y / 10f));
	}

	private static BufferedImage render(GrindDebugOverlay o)
	{
		BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		o.render(g);
		g.dispose();
		return img;
	}

	private static boolean yellow(BufferedImage img, int x, int y)
	{
		return img.getRGB(x, y) == Color.YELLOW.getRGB();
	}

	@Test
	public void drawsEverySegmentAsAYellowLineAtItsTopWhenShown()
	{
		List<GrindSegment> segs = Arrays.asList(
			new GrindSegment(100, 200, 800, 200, 160),
			new GrindSegment(500, 300, 500, 900, 40));
		GrindDebugOverlay o = new GrindDebugOverlay(() -> true, () -> segs, this::project);
		BufferedImage img = render(o);
		assertEquals("yellow at the first segment's middle", true, yellow(img, 45, 20));
		assertEquals("yellow at the second segment's middle", true, yellow(img, 50, 60));
		assertFalse(yellow(img, 5, 5));
		assertEquals(40f, lastH, 0f);
	}

	@Test
	public void drawsNothingAndReadsNothingWhenHidden()
	{
		GrindDebugOverlay o = new GrindDebugOverlay(() -> false, () ->
		{
			throw new AssertionError("segments read while hidden");
		}, this::project);
		BufferedImage img = render(o);
		assertFalse(yellow(img, 45, 20));
	}

	@Test
	public void skipsSegmentsWithAnOffScreenEnd()
	{
		List<GrindSegment> segs = Collections.singletonList(new GrindSegment(100, 200, 800, 200, 60));
		GrindDebugOverlay o = new GrindDebugOverlay(() -> true, () -> segs, (x, y, h) -> x > 500 ? null : project(x, y, h));
		BufferedImage img = render(o);
		assertFalse(yellow(img, 20, 20));
	}
}
