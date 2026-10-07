package com.gielinorskate.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/** A row of controller glyphs as a Swing icon, for the side panel. */
public final class ControllerGlyphIcon implements Icon
{
	private final ControllerGlyphs.Glyph[] glyphs;
	private final int height;
	private final int gap;

	public ControllerGlyphIcon(int height, ControllerGlyphs.Glyph... glyphs)
	{
		this.glyphs = glyphs.clone();
		this.height = height;
		this.gap = Math.max(1, height / 6);
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		int cx = x;
		for (ControllerGlyphs.Glyph glyph : glyphs)
		{
			ControllerGlyphs.paint((Graphics2D) g, glyph, cx, y, height);
			cx += ControllerGlyphs.glyphWidth(glyph, height) + gap;
		}
	}

	@Override
	public int getIconWidth()
	{
		int w = 0;
		for (ControllerGlyphs.Glyph glyph : glyphs)
		{
			w += ControllerGlyphs.glyphWidth(glyph, height);
		}
		return w + gap * Math.max(0, glyphs.length - 1);
	}

	@Override
	public int getIconHeight()
	{
		return height;
	}
}
