package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
* The template a player paints a design on: a picture at the part's layout size with the part white on grey, its
* outline, the truck bolts as dots, a centre line and (grip and deck) an arrow to the nose. A design painted to fit
* it lands on the board exactly as drawn when placed with "Reset" or "Fill" (they cover the part). Pure.
*/
final class DesignTemplate
{
static final Color OUTSIDE = new Color(200, 200, 200);
static final Color INSIDE = Color.WHITE;
static final Color LINES = new Color(30, 30, 30);
private static final Color GUIDE = new Color(230, 80, 40);

private DesignTemplate()
{
}

static BufferedImage render(DesignPart part, PartOutline outline)
{
int w = outline.width;
boolean[] mask = outline.mask();
int[] px = new int[mask.length];
for (int i = 0; i < px.length; i++)
px[i] = (mask[i] ? INSIDE : OUTSIDE).getRGB();
BufferedImage img = new BufferedImage(w, outline.height, BufferedImage.TYPE_INT_RGB);
img.setRGB(0, 0, w, outline.height, px, 0, w);
Graphics2D g = img.createGraphics();
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
double[] b = outline.bounds();
double midX = (b[0] + b[2]) / 2;
g.setColor(GUIDE);
g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
g.draw(new Line2D.Double(midX, b[1], midX, b[3]));
if (part == DesignPart.WHEELS)
g.draw(new Line2D.Double(b[0], (b[1] + b[3]) / 2, b[2], (b[1] + b[3]) / 2));
g.setColor(LINES);
g.setStroke(new BasicStroke(1.5f));
float[] e = outline.edges;
for (int i = 0; i < e.length; i += 4)
g.draw(new Line2D.Float(e[i], e[i + 1], e[i + 2], e[i + 3]));
double dot = Math.max(3, w / 90.0);
for (double[] p : outline.bolts)
g.fill(new Ellipse2D.Double(p[0] - dot, p[1] - dot, dot * 2, dot * 2));
if (part != DesignPart.WHEELS)
noseArrow(g, midX, outline.noseUp ? b[1] : b[3], outline.noseUp, w);
g.dispose();
return img;
}

/** An arrow by the nose end pointing at it, labelled. */
private static void noseArrow(Graphics2D g, double x, double endY, boolean up, int w)
{
double size = Math.max(10, w / 18.0);
double dir = up ? -1 : 1;
double tipY = endY - dir * size * 0.6;
double baseY = tipY - dir * size * 2.2;
g.setColor(GUIDE);
g.setStroke(new BasicStroke((float) Math.max(2, size / 5)));
double headX = x + size * 1.6;
g.draw(new Line2D.Double(headX, baseY, headX, tipY));
Path2D head = new Path2D.Double();
head.moveTo(headX, tipY + dir * size * 0.1);
head.lineTo(headX - size * 0.5, tipY - dir * size * 0.7);
head.lineTo(headX + size * 0.5, tipY - dir * size * 0.7);
head.closePath();
g.fill(head);
g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(size)));
g.drawString("NOSE", (float) (headX - g.getFontMetrics().stringWidth("NOSE") / 2.0),
(float) (up ? baseY + size : baseY - size * 0.3));
}
}
