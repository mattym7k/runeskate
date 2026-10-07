package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import com.gielinorskate.world.BlockerSet;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BlockerDebugOverlayTest
{
	/** Top-down test projection: canvas = (x / 10, y / 10); records the height it was given. */
	private float lastH;

	private Point project(float x, float y, float h)
	{
		lastH = h;
		return new Point(Math.round(x / 10f), Math.round(y / 10f));
	}

	private static BufferedImage render(BlockerDebugOverlay o)
	{
		BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		o.render(g);
		g.dispose();
		return img;
	}

	private static boolean is(BufferedImage img, int x, int y, Color c)
	{
		return img.getRGB(x, y) == c.getRGB();
	}

	/** Three 200 x 200 boxes (canvas 20 x 20) side by side: SOLID, LOW, PASS. */
	private static List<BlockerSet> boxes()
	{
		return Collections.singletonList(new BlockerSet.Builder()
			.add(200, 500, 100, 100, 1, 0, 300, BlockerSet.SOLID, 0)
			.add(500, 500, 100, 100, 1, 0, 80, BlockerSet.LOW, BlockerSet.LANDABLE)
			.add(800, 500, 100, 100, 1, 0, 60, BlockerSet.PASS, 0)
			.build(10));
	}

	@Test
	public void outlinesEachBoxInItsKindsColourAtItsTop()
	{
		BlockerDebugOverlay o = new BlockerDebugOverlay(() -> true, BlockerDebugOverlayTest::boxes,
			() -> new float[]{500, 500}, this::project);
		BufferedImage img = render(o);
		// the west edges of the boxes are at canvas x = 10, 40, 70
		assertEquals(true, is(img, 10, 50, BlockerDebugOverlay.SOLID));
		assertEquals(true, is(img, 40, 50, BlockerDebugOverlay.LOW));
		assertEquals(true, is(img, 70, 50, BlockerDebugOverlay.PASS));
		assertFalse("outline only", is(img, 20, 50, BlockerDebugOverlay.SOLID));
		assertEquals(60f, lastH, 0f);
	}

	@Test
	public void drawsNothingAndReadsNothingWhenHidden()
	{
		BlockerDebugOverlay o = new BlockerDebugOverlay(() -> false, () ->
		{
			throw new AssertionError("boxes read while hidden");
		}, () -> new float[]{500, 500}, this::project);
		assertFalse(is(render(o), 10, 50, BlockerDebugOverlay.SOLID));
	}

	@Test
	public void skipsBoxesFarFromTheSkater()
	{
		// the skater is more than the draw radius east of the SOLID box only
		float x = 200 + BlockerDebugOverlay.DRAW_RADIUS + 10;
		BlockerDebugOverlay o = new BlockerDebugOverlay(() -> true, BlockerDebugOverlayTest::boxes,
			() -> new float[]{x, 500}, this::project);
		BufferedImage img = render(o);
		assertFalse(is(img, 10, 50, BlockerDebugOverlay.SOLID));
		assertEquals(true, is(img, 70, 50, BlockerDebugOverlay.PASS));
	}

	@Test
	public void skipsBoxesWithACornerOffScreen()
	{
		BlockerDebugOverlay o = new BlockerDebugOverlay(() -> true, BlockerDebugOverlayTest::boxes,
			() -> new float[]{500, 500}, (x, y, h) -> x < 150 ? null : project(x, y, h));
		BufferedImage img = render(o);
		assertFalse(is(img, 10, 50, BlockerDebugOverlay.SOLID));
		assertEquals(true, is(img, 40, 50, BlockerDebugOverlay.LOW));
	}
}
