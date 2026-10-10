package com.gielinorskate.ui;

import com.gielinorskate.tricks.Gesture;
import java.awt.*;
import java.awt.geom.*;

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
			// the glyph's unit box onto the square
			AffineTransform box = new AffineTransform(size, 0, 0, size, x, y);
			float thin = Math.max(1f, size / 14f);

			graphics.setColor(windUp);
			graphics.setStroke(new BasicStroke(thin, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.draw(box.createTransformedShape(new Line2D.Float(glyph.windUpStart[0], glyph.windUpStart[1],
				glyph.hook[0], glyph.hook[1])));

			graphics.setColor(flick);
			graphics.setStroke(new BasicStroke(Math.max(1.5f, size / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D path = new Path2D.Float();
			path.moveTo(glyph.hook[0], glyph.hook[1]);
			path.quadTo(glyph.control[0], glyph.control[1], glyph.flickEnd[0], glyph.flickEnd[1]);
			// the arrowhead
			path.moveTo(glyph.arrowLeft[0], glyph.arrowLeft[1]);
			path.lineTo(glyph.flickEnd[0], glyph.flickEnd[1]);
			path.lineTo(glyph.arrowRight[0], glyph.arrowRight[1]);
			graphics.draw(box.createTransformedShape(path));

			if (glyph.shift)
			{
				// a small Shift-key arrow in the top-left corner, 0.28 of the square
				float[] xs = {0.5f, 1f, 0.72f, 0.72f, 0.28f, 0.28f, 0f};
				float[] ys = {0f, 0.55f, 0.55f, 1f, 1f, 0.55f, 0.55f};
				Path2D mark = new Path2D.Float();
				mark.moveTo(xs[0] * 0.28f, 0);
				for (int i = 1; i < xs.length; i++)
					mark.lineTo(xs[i] * 0.28f, ys[i] * 0.28f);
				mark.closePath();
				graphics.setStroke(new BasicStroke(thin));
				graphics.draw(box.createTransformedShape(mark));
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
}
