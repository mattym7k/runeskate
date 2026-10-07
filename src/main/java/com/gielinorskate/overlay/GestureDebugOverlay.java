package com.gielinorskate.overlay;

import com.gielinorskate.input.StrokeSnapshot;
import com.gielinorskate.session.SkateSession;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Dev aid toggled by {@code ::skategesture}: draws the last right-mouse stroke's path, its wind-up
 * extreme and the recognized sector/trick name, for {@link #VISIBLE_MS} after each flick. Screen-space
 * (the stroke's own mouse coordinates), so no world projection is needed.
 */
@Singleton
public class GestureDebugOverlay extends Overlay
{
	static final long VISIBLE_MS = 2000;
	private static final Color PATH_COLOR = new Color(80, 220, 255, 220);
	private static final Color EXTREME_COLOR = Color.RED;
	private static final Color LABEL_COLOR = Color.WHITE;
	private static final Color SHADOW_COLOR = new Color(0, 0, 0, 160);
	private static final BasicStroke PATH_STROKE = new BasicStroke(2f);
	private static final int EXTREME_RADIUS = 5;

	private final BooleanSupplier enabled;
	private final Supplier<StrokeSnapshot> snapshot;
	private final LongSupplier clock;
	private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 13);

	@Inject
	GestureDebugOverlay(SkateSession session)
	{
		this(session::isShowingGesture, session::getLastStrokeSnapshot, System::currentTimeMillis);
	}

	GestureDebugOverlay(BooleanSupplier enabled, Supplier<StrokeSnapshot> snapshot, LongSupplier clock)
	{
		this.enabled = enabled;
		this.snapshot = snapshot;
		this.clock = clock;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** Visible while the ::skategesture toggle is on and the snapshot's flick fired within {@link #VISIBLE_MS}. */
	static boolean visible(StrokeSnapshot s, long nowMs)
	{
		return s != null && nowMs - s.firedAtMs <= VISIBLE_MS;
	}

	/** The sector/trick label text for a snapshot's gesture. */
	static String label(StrokeSnapshot s)
	{
		Trick t = TrickCatalog.forGesture(s.gesture);
		String name = t == null ? "no trick" : t.displayName;
		return (s.gesture.nollie ? "Nollie " : "") + s.gesture.direction + " -> " + name;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!enabled.getAsBoolean())
		{
			return null;
		}
		StrokeSnapshot s = snapshot.get();
		if (!visible(s, clock.getAsLong()))
		{
			return null;
		}

		graphics.setStroke(PATH_STROKE);
		graphics.setColor(PATH_COLOR);
		List<float[]> path = s.path;
		for (int i = 1; i < path.size(); i++)
		{
			float[] a = path.get(i - 1);
			float[] b = path.get(i);
			graphics.drawLine(Math.round(a[0]), Math.round(a[1]), Math.round(b[0]), Math.round(b[1]));
		}

		graphics.setColor(EXTREME_COLOR);
		graphics.fill(new Ellipse2D.Float(s.extremeX - EXTREME_RADIUS, s.extremeY - EXTREME_RADIUS,
			EXTREME_RADIUS * 2f, EXTREME_RADIUS * 2f));

		String text = label(s);
		graphics.setFont(font);
		int lx = Math.round(s.extremeX) + EXTREME_RADIUS + 6;
		int ly = Math.round(s.extremeY);
		graphics.setColor(SHADOW_COLOR);
		graphics.drawString(text, lx + 1, ly + 1);
		graphics.setColor(LABEL_COLOR);
		graphics.drawString(text, lx, ly);

		return null;
	}
}
