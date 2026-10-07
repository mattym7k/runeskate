package com.gielinorskate.overlay;

import com.gielinorskate.party.GhostLabel;
import com.gielinorskate.party.PartyGhostService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Above each party ghost: the member's name, and their latest trick fading out over 1.5 s. */
@Singleton
public class GhostLabelOverlay extends Overlay
{
	private static final Color NAME = new Color(255, 255, 255);
	private static final Color TRICK = new Color(255, 215, 64);
	private static final Color SHADOW = Color.BLACK;

	private final Client client;
	private final PartyGhostService ghosts;

	@Inject
	GhostLabelOverlay(Client client, PartyGhostService ghosts)
	{
		this.client = client;
		this.ghosts = ghosts;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<GhostLabel> labels = ghosts.getLabels();
		if (labels.isEmpty())
		{
			return null;
		}
		FontMetrics fm = graphics.getFontMetrics();
		for (GhostLabel l : labels)
		{
			Point p = Perspective.localToCanvas(client, l.x, l.y, l.z);
			if (p == null)
			{
				continue;
			}
			int y = p.getY();
			if (l.trick != null)
			{
				text(graphics, fm, l.trick, p.getX(), y, withAlpha(TRICK, l.trickAlpha));
				y -= fm.getHeight();
			}
			if (l.name != null)
			{
				text(graphics, fm, l.name, p.getX(), y, NAME);
			}
		}
		return null;
	}

	private static void text(Graphics2D g, FontMetrics fm, String s, int centreX, int y, Color c)
	{
		int x = centreX - fm.stringWidth(s) / 2;
		g.setColor(withAlpha(SHADOW, c.getAlpha() / 255f));
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private static Color withAlpha(Color c, float alpha)
	{
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}
}
