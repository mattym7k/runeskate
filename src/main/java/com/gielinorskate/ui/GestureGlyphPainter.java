package com.gielinorskate.ui;

import com.gielinorskate.tricks.Gesture;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

/**
 * Draws a {@link GestureGlyph} into a square: the side panel's trick list and the controls card's flick page share
 * it. Restores the stroke, colour and antialiasing it changes.
 */
public final class GestureGlyphPainter
{
	private GestureGlyphPainter()
	{
	}

	/**
	 * Paints the flick for {@code g} into the {@code size} x {@code size} square at (x, y).
	 *
	 * @param windUp colour of the thin wind-up stroke
	 * @param flick colour of the flick and its arrowhead (and the Shift mark)
	 */
	public static void paint(Graphics2D graphics, Gesture g, int x, int y, int size, Color windUp, Color flick)
	{
		GestureGlyph glyph = GestureGlyph.of(g);
		Stroke oldStroke = graphics.getStroke();
		Color oldColor = graphics.getColor();
		Object oldAa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		try
		{
			float thin = Math.max(1f, size / 14f);
			float thick = Math.max(1.5f, size / 8f);

			graphics.setColor(windUp);
			graphics.setStroke(new BasicStroke(thin, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.draw(new Line2D.Float(px(glyph.windUpStart[0], x, size), px(glyph.windUpStart[1], y, size),
				px(glyph.hook[0], x, size), px(glyph.hook[1], y, size)));

			graphics.setColor(flick);
			graphics.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			GeneralPath path = new GeneralPath();
			path.moveTo(px(glyph.hook[0], x, size), px(glyph.hook[1], y, size));
			path.quadTo(px(glyph.control[0], x, size), px(glyph.control[1], y, size),
				px(glyph.flickEnd[0], x, size), px(glyph.flickEnd[1], y, size));
			graphics.draw(path);

			GeneralPath head = new GeneralPath();
			head.moveTo(px(glyph.arrowLeft[0], x, size), px(glyph.arrowLeft[1], y, size));
			head.lineTo(px(glyph.flickEnd[0], x, size), px(glyph.flickEnd[1], y, size));
			head.lineTo(px(glyph.arrowRight[0], x, size), px(glyph.arrowRight[1], y, size));
			graphics.draw(head);

			if (glyph.shift)
			{
				// a small Shift-key arrow in the top-left corner
				float s = size * 0.28f;
				GeneralPath mark = new GeneralPath();
				mark.moveTo(x + s / 2f, y);
				mark.lineTo(x + s, y + s * 0.55f);
				mark.lineTo(x + s * 0.72f, y + s * 0.55f);
				mark.lineTo(x + s * 0.72f, y + s);
				mark.lineTo(x + s * 0.28f, y + s);
				mark.lineTo(x + s * 0.28f, y + s * 0.55f);
				mark.lineTo(x, y + s * 0.55f);
				mark.closePath();
				graphics.setStroke(new BasicStroke(thin));
				graphics.draw(mark);
			}
		}
		finally
		{
			graphics.setStroke(oldStroke);
			graphics.setColor(oldColor);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				oldAa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldAa);
		}
	}

	private static float px(float unit, int origin, int size)
	{
		return origin + unit * size;
	}
}
