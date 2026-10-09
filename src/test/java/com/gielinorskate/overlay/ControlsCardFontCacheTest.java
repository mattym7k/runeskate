package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class ControlsCardFontCacheTest
{
	@Test
	public void eachSizeIsTheSameFontEveryTime()
	{
		ControlsCardOverlay.warmFonts();
		for (int size = ControlsCardOverlay.MIN_FONT_SIZE; size <= ControlsCardOverlay.MAX_FONT_SIZE; size++)
		{
			Font f = ControlsCardOverlay.FONTS[size];
			assertSame(f, ControlsCardOverlay.FONTS[size]);
			assertEquals(new Font(Font.SANS_SERIF, Font.BOLD, size), f);
		}
	}

	@Test
	public void layoutWithCachedFontsMatchesFreshFonts()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			ControlsCardOverlay.Spec spec = new ControlsCardOverlay.Spec("Space", true, true, "right", false, false,
				false, true, "F", com.gielinorskate.controller.PadPreset.skate3());
			ControlsCardOverlay.Card a = ControlsCardOverlay.fit(g, ControlsCardOverlay.rows(spec), 500, 300);
			ControlsCardOverlay.Card b = ControlsCardOverlay.fit(g, ControlsCardOverlay.rows(spec), 500, 300);
			assertEquals(a.font, b.font);
			assertEquals(a.width, b.width);
			assertEquals(a.height, b.height);
			assertEquals(a.lines.size(), b.lines.size());
		}
		finally
		{
			g.dispose();
		}
	}
}
