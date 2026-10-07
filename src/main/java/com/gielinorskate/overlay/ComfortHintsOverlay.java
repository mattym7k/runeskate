package com.gielinorskate.overlay;

import com.gielinorskate.session.SkateSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Comfort hints while skating, at the top centre of the 3D viewport: the idle-logout warning, "Edge of the
 * loaded area" near the invisible wall at the scene's edge, and a short "Stumble!" flash after a hard hit.
 */
@Singleton
public class ComfortHintsOverlay extends Overlay
{
	private static final Color WARNING_COLOR = new Color(255, 190, 60);
	private static final Color EDGE_COLOR = new Color(230, 230, 230);
	private static final Color STUMBLE_COLOR = new Color(255, 140, 60);
	private static final Color SHADOW_COLOR = Color.BLACK;
	private static final int TOP_MARGIN = 28;
	private static final int LINE_GAP = 4;
	/** "Stumble!" sits this fraction of the viewport height above its centre. */
	private static final float STUMBLE_Y_FRACTION = 0.3f;

	private final Client client;
	private final SkateSession session;

	@Inject
	ComfortHintsOverlay(Client client, SkateSession session)
	{
		this.client = client;
		this.session = session;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!session.isActive())
		{
			return null;
		}
		int vx = client.getViewportXOffset();
		int vy = client.getViewportYOffset();
		int vw = client.getViewportWidth();
		int vh = client.getViewportHeight();
		int centerX = vx + vw / 2;
		int y = vy + TOP_MARGIN;

		graphics.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = graphics.getFontMetrics();
		String idle = session.getIdleWarningText();
		if (idle != null)
		{
			for (String line : wrap(idle, metrics, vw - 2 * TOP_MARGIN))
			{
				drawCentered(graphics, line, centerX, y, WARNING_COLOR, 1f);
				y += metrics.getHeight() + LINE_GAP;
			}
		}
		float edge = session.getEdgeHintAlpha();
		if (edge > 0f)
		{
			drawCentered(graphics, "Edge of the loaded area", centerX, y, EDGE_COLOR, edge);
		}

		float stumble = session.getStumbleFlashAlpha();
		if (stumble > 0f)
		{
			Font old = graphics.getFont();
			graphics.setFont(old.deriveFont(old.getSize2D() * 1.5f));
			drawCentered(graphics, "Stumble!", centerX, vy + Math.round(vh * (0.5f - STUMBLE_Y_FRACTION / 2)),
				STUMBLE_COLOR, stumble);
			graphics.setFont(old);
		}
		return null;
	}

	private static void drawCentered(Graphics2D graphics, String text, int centerX, int baselineY, Color color,
		float alpha)
	{
		int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
		int x = centerX - graphics.getFontMetrics().stringWidth(text) / 2;
		graphics.setColor(new Color(0, 0, 0, a * SHADOW_COLOR.getAlpha() / 255));
		graphics.drawString(text, x + 1, baselineY + 1);
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
		graphics.drawString(text, x, baselineY);
	}

	/** Word-wraps {@code text} to lines at most {@code maxW} wide (a single long word stays whole). */
	static List<String> wrap(String text, FontMetrics metrics, int maxW)
	{
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String next = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && metrics.stringWidth(next) > maxW)
			{
				out.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0)
		{
			out.add(line.toString());
		}
		return out;
	}
}
