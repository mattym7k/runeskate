package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;

public class StartGlyphTest
{
	@Test
	public void startIsAWidePillToken()
	{
		assertEquals(Arrays.asList(Glyph.START, ": stop"), ControllerGlyphs.split("{START}: stop"));
		assertEquals("START: stop", ControllerGlyphs.plain("{START}: stop"));
		Graphics2D g = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		FontMetrics m = g.getFontMetrics();
		assertTrue(ControllerGlyphs.width("{START}", m) > ControllerGlyphs.width("{BACK}", m));
		BufferedImage img = new BufferedImage(80, 20, BufferedImage.TYPE_INT_ARGB);
		ControllerGlyphs.paint(img.createGraphics(), Glyph.START, 0, 0, 20);
		// something was drawn in the middle of the pill
		assertTrue((img.getRGB(20, 10) >>> 24) > 0);
	}

	@Test
	public void thePanelListsTheOnFootPadControls()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, true, "F");
		java.util.List<javax.swing.JLabel> all = new java.util.ArrayList<>();
		collect(panel, all);
		assertTrue(all.stream().anyMatch(l -> l.getText() != null && l.getText().contains("On foot: walk")));
		assertTrue(all.stream().anyMatch(l -> l.getText() != null && l.getText().contains("Step off and carry")));

		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, false, "G");
		all.clear();
		collect(panel, all);
		assertTrue(all.stream().anyMatch(l -> l.getText() != null && l.getText().contains("G: step off and carry")
			&& l.getText().contains("WASD walk")));
	}

	private static void collect(java.awt.Container c, java.util.List<javax.swing.JLabel> out)
	{
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel)
			{
				out.add((javax.swing.JLabel) child);
			}
			if (child instanceof java.awt.Container)
			{
				collect((java.awt.Container) child, out);
			}
		}
	}
}
