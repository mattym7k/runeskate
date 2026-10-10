package com.gielinorskate.overlay;

import com.gielinorskate.session.SkateSession;
import com.gielinorskate.world.GrindSegment;
import java.awt.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.*;

/**
 * "Show grindable edges": draws every grind segment of the current skate session as a yellow line at its
 * grindable top, and the rail a lock would catch right now brighter. Drawn only while skating with the setting on.
 */
@Singleton
public class GrindEdgesOverlay extends Overlay
{
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
	GrindEdgesOverlay(Client client, SkateSession session)
	{
		// RuneLite heights are negative-up, so the absolute z of a top at height h is -h
		this(session::isShowingGrinds, session::getGrindSegments, (x, y, h) ->
		{
			net.runelite.api.Point p = Perspective.localToCanvas(client, Math.round(x), Math.round(y), Math.round(-h));
			return p == null ? null : new Point(p.getX(), p.getY());
		}, session::getPredictedGrind);
	}

	GrindEdgesOverlay(BooleanSupplier visible, Supplier<List<GrindSegment>> segments, Projector projector,
		Supplier<GrindSegment> predicted)
	{
		this.predicted = predicted;
		this.visible = visible;
		this.segments = segments;
		this.projector = projector;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!visible.getAsBoolean())
			return null;
		g.setColor(Color.YELLOW);
		g.setStroke(new BasicStroke(2f));
		for (GrindSegment s : segments.get())
			line(g, s);
		// the rail a lock would catch right now (while airborne): brighter and thicker
		GrindSegment target = predicted.get();
		if (target != null)
		{
			g.setColor(new Color(80, 255, 120));
			g.setStroke(new BasicStroke(4f));
			line(g, target);
		}
		return null;
	}

	/** The segment's top as a line, when both ends are on screen. */
	private void line(Graphics2D g, GrindSegment s)
	{
		Point a = projector.project(s.x0, s.y0, s.top0);
		Point b = a == null ? null : projector.project(s.x1, s.y1, s.top1);
		if (b != null)
			g.drawLine(a.x, a.y, b.x, b.y);
	}
}
