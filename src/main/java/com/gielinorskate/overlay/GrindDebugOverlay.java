package com.gielinorskate.overlay;

import com.gielinorskate.session.SkateSession;
import com.gielinorskate.world.GrindSegment;
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
 * Dev aid toggled by {@code ::skategrinds}: draws every grind segment of the current skate session as a
 * yellow line at its grindable top. Drawn only while skating with the toggle on.
 */
@Singleton
public class GrindDebugOverlay extends Overlay
{
	private static final Color COLOR = Color.YELLOW;
	private static final BasicStroke STROKE = new BasicStroke(2f);
	/** The rail a lock would catch right now (while airborne) is drawn brighter and thicker. */
	private static final Color PREDICTED_COLOR = new Color(80, 255, 120);
	private static final BasicStroke PREDICTED_STROKE = new BasicStroke(4f);

	/** Local (x, y) plus up-positive absolute height to a canvas point, or null when off screen. */
	interface Projector
	{
		Point project(float x, float y, float h);
	}

	private final BooleanSupplier visible;
	private final Supplier<List<GrindSegment>> segments;
	private final Projector projector;
	private final Supplier<GrindSegment> predicted;

	@Inject
	GrindDebugOverlay(Client client, SkateSession session)
	{
		this(session::isShowingGrinds, session::getGrindSegments, (x, y, h) -> project(client, x, y, h),
			session::getPredictedGrind);
	}

	GrindDebugOverlay(BooleanSupplier visible, Supplier<List<GrindSegment>> segments, Projector projector)
	{
		this(visible, segments, projector, () -> null);
	}

	GrindDebugOverlay(BooleanSupplier visible, Supplier<List<GrindSegment>> segments, Projector projector,
		Supplier<GrindSegment> predicted)
	{
		this.predicted = predicted;
		this.visible = visible;
		this.segments = segments;
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

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!visible.getAsBoolean())
		{
			return null;
		}
		graphics.setColor(COLOR);
		graphics.setStroke(STROKE);
		for (GrindSegment s : segments.get())
		{
			Point a = projector.project(s.x0, s.y0, s.top0);
			if (a == null)
			{
				continue;
			}
			Point b = projector.project(s.x1, s.y1, s.top1);
			if (b == null)
			{
				continue;
			}
			graphics.drawLine(a.x, a.y, b.x, b.y);
		}
		GrindSegment target = predicted.get();
		if (target != null)
		{
			Point a = projector.project(target.x0, target.y0, target.top0);
			Point b = projector.project(target.x1, target.y1, target.top1);
			if (a != null && b != null)
			{
				graphics.setColor(PREDICTED_COLOR);
				graphics.setStroke(PREDICTED_STROKE);
				graphics.drawLine(a.x, a.y, b.x, b.y);
			}
		}
		return null;
	}
}
