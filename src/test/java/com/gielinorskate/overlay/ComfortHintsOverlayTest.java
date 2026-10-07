package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

public class ComfortHintsOverlayTest
{
	@Test
	public void longHintsWrapToTheViewportWidthWithoutLosingWords()
	{
		Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
			FontMetrics m = g.getFontMetrics();
			String text = "The game hasn't seen input for 4 minutes and will log you out soon. "
				+ "Skate keys don't count: move the mouse to stay logged in.";
			List<String> lines = ComfortHintsOverlay.wrap(text, m, 300);
			assertTrue(lines.size() > 1);
			for (String line : lines)
			{
				assertTrue(line, m.stringWidth(line) <= 300);
			}
			assertEquals(text, String.join(" ", lines));
		}
		finally
		{
			g.dispose();
		}
	}
}
