package com.gielinorskate.overlay;

import com.gielinorskate.session.SkateSession;
import com.gielinorskate.world.BlockerSet;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Dev aid toggled by {@code ::skateboxes}: outlines every collision box near the skater at its top height,
 * SOLID red, LOW yellow and PASS (ridden through) green. Drawn only while skating with the toggle on.
 */
@Singleton
public class BlockerDebugOverlay extends Overlay
{
	static final Color SOLID = Color.RED;
	static final Color LOW = Color.YELLOW;
	static final Color PASS = Color.GREEN;
	/** Only boxes whose centre is this close to the skater are drawn (12 tiles), to keep each frame cheap. */
	static final float DRAW_RADIUS = 12 * 128f;
	private static final BasicStroke STROKE = new BasicStroke(1.5f);

	private final BooleanSupplier visible;
	private final Supplier<List<BlockerSet>> sets;
	private final Supplier<float[]> centre;
	private final GrindDebugOverlay.Projector projector;
	private final float[] corners = new float[8];
	private final int[] xs = new int[4];
	private final int[] ys = new int[4];

	@Inject
	BlockerDebugOverlay(Client client, SkateSession session)
	{
		this(session::isShowingBoxes, session::getBlockerSets, session::getSkaterPosition,
			(x, y, h) -> project(client, x, y, h));
	}

	BlockerDebugOverlay(BooleanSupplier visible, Supplier<List<BlockerSet>> sets, Supplier<float[]> centre,
		GrindDebugOverlay.Projector projector)
	{
		this.visible = visible;
		this.sets = sets;
		this.centre = centre;
		this.projector = projector;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** RuneLite heights are negative-up, so the absolute z of a top at height h is -h. */
	private static Point project(Client client, float x, float y, float h)
	{
		net.runelite.api.Point p = Perspective.localToCanvas(client, Math.round(x), Math.round(y), Math.round(-h));
		return p == null ? null : new Point(p.getX(), p.getY());
	}

	static Color color(byte kind)
	{
		return kind == BlockerSet.SOLID ? SOLID : kind == BlockerSet.LOW ? LOW : PASS;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!visible.getAsBoolean())
		{
			return null;
		}
		float[] c = centre.get();
		if (c == null)
		{
			return null;
		}
		graphics.setStroke(STROKE);
		float r2 = DRAW_RADIUS * DRAW_RADIUS;
		for (BlockerSet set : sets.get())
		{
			for (int i = 0; i < set.count(); i++)
			{
				float dx = set.centreX(i) - c[0];
				float dy = set.centreY(i) - c[1];
				if (dx * dx + dy * dy > r2)
				{
					continue;
				}
				set.corners(i, corners);
				boolean onScreen = true;
				for (int k = 0; k < 4 && onScreen; k++)
				{
					Point p = projector.project(corners[2 * k], corners[2 * k + 1], set.top(i));
					if (p == null)
					{
						onScreen = false;
					}
					else
					{
						xs[k] = p.x;
						ys[k] = p.y;
					}
				}
				if (onScreen)
				{
					graphics.setColor(color(set.kind(i)));
					graphics.drawPolygon(xs, ys, 4);
				}
			}
		}
		return null;
	}
}
