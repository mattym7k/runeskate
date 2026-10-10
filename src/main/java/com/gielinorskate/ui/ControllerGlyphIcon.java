package com.gielinorskate.ui;

import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.*;
import javax.swing.Icon;

/** A row of controller glyphs as a Swing icon, for the side panel. */
public final class ControllerGlyphIcon implements Icon
{
	private final Glyph[] glyphs;
	private final int height;
	private final int gap;

	public ControllerGlyphIcon(int height, Glyph... glyphs)
	{
		this.glyphs = glyphs;
		this.height = height;
		gap = Math.max(1, height / 6);
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		for (Glyph glyph : glyphs)
		{
			ControllerGlyphs.paint((Graphics2D) g, glyph, x, y, height);
			x += ControllerGlyphs.glyphWidth(glyph, height) + gap;
		}
	}

	@Override
	public int getIconWidth()
	{
		int w = -gap;
		for (Glyph glyph : glyphs)
			w += ControllerGlyphs.glyphWidth(glyph, height) + gap;
		return Math.max(0, w);
	}

	@Override
	public int getIconHeight()
	{
		return height;
	}
}
