package com.gielinorskate.overlay;

import com.gielinorskate.session.SkateSession;
import java.awt.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

/**
* Comfort hints while skating, at the top centre of the 3D viewport: the idle-logout warning, "Edge of the
* loaded area" near the invisible wall at the scene's edge, and a short "Stumble!" flash after a hard hit.
*/
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ComfortHintsOverlay extends Overlay
{
/** The hints start this far below the viewport top, and wrap this far in from its sides. */
private static final int TOP_MARGIN = 28;

private final Client client;
private final SkateSession session;

{
setPosition(OverlayPosition.DYNAMIC);
setLayer(OverlayLayer.ABOVE_WIDGETS);
}

@Override
public Dimension render(Graphics2D g)
{
if (!session.isActive())
return null;
int vy = client.getViewportYOffset();
int vw = client.getViewportWidth();
int cx = client.getViewportXOffset() + vw / 2;
int y = vy + TOP_MARGIN;

g.setFont(FontManager.getRunescapeBoldFont());
FontMetrics metrics = g.getFontMetrics();
String idle = session.getIdleWarningText();
if (idle != null)
{
for (String line : ControlsCardOverlay.wrap(idle, metrics, vw - 2 * TOP_MARGIN))
{
centred(g, line, cx, y, new Color(255, 190, 60), 1f);
y += metrics.getHeight() + 4;
}
}
centred(g, "Edge of the loaded area", cx, y, new Color(230, 230, 230), session.getEdgeHintAlpha());
float stumble = session.getStumbleFlashAlpha();
if (stumble > 0f)
{
// half again as big, 15% of the viewport height above its centre
g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() * 1.5f));
centred(g, "Stumble!", cx, vy + Math.round(client.getViewportHeight() * 0.35f), new Color(255, 140, 60),
stumble);
}
return null;
}

private static void centred(Graphics2D g, String text, int cx, int y, Color color, float alpha)
{
if (alpha > 0f)
HudLayout.centred(g, text, cx, y, HudLayout.fade(color, alpha), HudLayout.fade(Color.BLACK, alpha), 1);
}
}
