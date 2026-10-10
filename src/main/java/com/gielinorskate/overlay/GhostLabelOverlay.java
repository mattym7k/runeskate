package com.gielinorskate.overlay;

import com.gielinorskate.party.GhostLabel;
import com.gielinorskate.party.PartyGhostService;
import java.awt.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.*;

/** Above each party ghost: the member's name, and their latest trick fading out over 1.5 s. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GhostLabelOverlay extends Overlay
{
	private final Client client;
	private final PartyGhostService ghosts;

	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		for (GhostLabel l : ghosts.getLabels())
		{
			Point p = Perspective.localToCanvas(client, l.x, l.y, l.z);
			if (p == null)
				continue;
			int y = p.getY();
			if (l.trick != null)
			{
				HudLayout.centred(g, l.trick, p.getX(), y, HudLayout.fade(new Color(255, 215, 64), l.trickAlpha),
					HudLayout.fade(Color.BLACK, l.trickAlpha), 1);
				y -= g.getFontMetrics().getHeight();
			}
			if (l.name != null)
				HudLayout.centred(g, l.name, p.getX(), y, Color.WHITE, Color.BLACK, 1);
		}
		return null;
	}
}
