package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;

public class ControllerGlyphsTest
{
	private static Graphics2D graphics(BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		return g;
	}

	@Test
	public void textSplitsIntoPlainPiecesAndGlyphs()
	{
		assertEquals(Arrays.asList(Glyph.A, "/", Glyph.X, ": push"), ControllerGlyphs.split("{A}/{X}: push"));
		assertEquals(Arrays.asList("Hold ", Glyph.LT, " in the air"), ControllerGlyphs.split("Hold {LT} in the air"));
		assertEquals(Arrays.asList("no {tokens} here"), ControllerGlyphs.split("no {tokens} here"));
		assertTrue(ControllerGlyphs.split("").isEmpty());
	}

	@Test
	public void plainSpellsGlyphsOut()
	{
		assertEquals("A/X: push, right stick: flick, d-pad up: card",
			ControllerGlyphs.plain("{A}/{X}: push, {RS}: flick, {DUP}: card"));
		assertEquals("{A}", ControllerGlyphs.token(Glyph.A));
		assertTrue(ControllerGlyphs.hasGlyphs("press {B}"));
		assertFalse(ControllerGlyphs.hasGlyphs("press B"));
	}

	@Test
	public void plainTextIsMeasuredExactlyAsTheFont()
	{
		Graphics2D g = graphics(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
		FontMetrics m = g.getFontMetrics();
		String s = "W: push (tap or hold)    A/D: steer";
		assertEquals(m.stringWidth(s), ControllerGlyphs.width(s, m));
	}

	@Test
	public void aGlyphIsAboutALineWideAndPillsWider()
	{
		Graphics2D g = graphics(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
		FontMetrics m = g.getFontMetrics();
		int h = m.getHeight();
		int a = ControllerGlyphs.width("{A}", m);
		assertTrue(a >= h && a <= 2 * h);
		assertTrue(ControllerGlyphs.width("{LT}", m) > a);
		assertEquals(ControllerGlyphs.width("x", m) + a, ControllerGlyphs.width("x{A}", m));
	}

	@Test
	public void faceButtonsAreFilledInTheirColour()
	{
		BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = graphics(img);
		ControllerGlyphs.paint(g, Glyph.B, 0, 0, 40);
		// a point inside the circle, off the letter
		Color c = new Color(img.getRGB(20, 5), true);
		assertEquals(255, c.getAlpha());
		assertTrue("red: " + c, c.getRed() > 180 && c.getGreen() < 100 && c.getBlue() < 100);
	}

	@Test
	public void everyGlyphPaintsSomethingAndRestoresTheGraphics()
	{
		for (Glyph glyph : Glyph.values())
		{
			int h = 24;
			BufferedImage img = new BufferedImage(ControllerGlyphs.glyphWidth(glyph, h), h,
				BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = graphics(img);
			g.setColor(Color.MAGENTA);
			Font font = g.getFont();
			ControllerGlyphs.paint(g, glyph, 0, 0, h);
			assertEquals(Color.MAGENTA, g.getColor());
			assertEquals(font, g.getFont());
			boolean any = false;
			for (int y = 0; y < h && !any; y++)
			{
				for (int x = 0; x < img.getWidth() && !any; x++)
				{
					any = (img.getRGB(x, y) >>> 24) != 0;
				}
			}
			assertTrue(glyph.name(), any);
		}
	}

	@Test
	public void theIconIsAsWideAsItsGlyphs()
	{
		ControllerGlyphIcon icon = new ControllerGlyphIcon(18, Glyph.LT, Glyph.RT);
		assertEquals(18, icon.getIconHeight());
		assertEquals(2 * ControllerGlyphs.glyphWidth(Glyph.LT, 18) + 3, icon.getIconWidth());
	}
}
