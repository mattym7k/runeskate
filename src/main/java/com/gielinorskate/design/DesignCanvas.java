package com.gielinorskate.design;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.*;

/**
* The design editor's canvas: the player's image under the part's outline, everything outside the outline dimmed,
* the truck bolts as dots, and the image's box with eight handles.
* <ul>
* <li>Dragging anywhere but a handle moves the image with the cursor.</li>
* <li>Dragging an edge (anywhere along it) stretches the image that way with the opposite edge fixed; a corner moves
* both its edges (Shift: keeping the proportions). The handles work on the box as seen, so whatever the turn or
* mirror the top edge goes up and down ({@link ImagePlacement#resized}).</li>
* <li>The mouse wheel scales the image about the cursor; the arrow keys nudge it (Shift: further).</li>
* </ul>
* Every drag is worked out from where it started (the placement and cursor at the press), so coalesced or dropped
* mouse events can't add up wrong. Shows one {@link ImagePlacement} and reports each change to its listener. EDT
* only.
*/
final class DesignCanvas extends JComponent
{
/** Outside the outline: black at this opacity (about 60% darker). */
private static final int DIM_ALPHA = 153;
static final double WHEEL_STEP = 1.1;
static final double NUDGE = 1;
static final double NUDGE_FAR = 10;
/** How near (view pixels) the cursor must be to an edge or corner of the image's box to grab it. */
private static final int HANDLE_HIT = 6;
private static final Color OUTLINE = new Color(255, 152, 31);

private final BufferedImage image;
private final PartOutline outline;
private final boolean showNose;
private final Consumer<ImagePlacement> listener;
/** Black at {@link #DIM_ALPHA} outside the outline, clear inside, at layout size. */
private final BufferedImage dimOverlay;
/** The outline's edges as one path in layout pixels, drawn in one go. */
private final Path2D outlinePath = new Path2D.Double();
private ImagePlacement placement;
/** The placement when the drag started; null when not dragging. */
private ImagePlacement dragFrom;
/** The handle dragged ({@link ImagePlacement#resized} edges), 0 for a move. */
private int dragEdges;
/** Where the drag started, view pixels. */
private int dragX;
private int dragY;

DesignCanvas(BufferedImage image, PartOutline outline, ImagePlacement start, boolean showNose,
Consumer<ImagePlacement> listener)
{
this.image = image;
this.outline = outline;
placement = start;
this.showNose = showNose;
this.listener = listener;
boolean[] mask = outline.mask();
int[] px = new int[mask.length];
for (int i = 0; i < px.length; i++)
px[i] = mask[i] ? 0 : DIM_ALPHA << 24;
dimOverlay = new BufferedImage(outline.width, outline.height, BufferedImage.TYPE_INT_ARGB);
dimOverlay.setRGB(0, 0, outline.width, outline.height, px, 0, outline.width);
float[] e = outline.edges;
for (int i = 0; i < e.length; i += 4)
{
outlinePath.moveTo(e[i], e[i + 1]);
outlinePath.lineTo(e[i + 2], e[i + 3]);
}
setName("editor:canvas");
setFocusable(true);
setOpaque(true);
setPreferredSize(new Dimension(360, 640));
setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
MouseAdapter mouse = new MouseAdapter()
{
@Override
public void mousePressed(MouseEvent e)
{
requestFocusInWindow();
if (SwingUtilities.isLeftMouseButton(e))
{
dragX = e.getX();
dragY = e.getY();
dragEdges = handleAt(dragX, dragY);
dragFrom = placement;
}
}

@Override
public void mouseReleased(MouseEvent e)
{
if (SwingUtilities.isLeftMouseButton(e))
{
dragFrom = null;
mouseMoved(e);
}
}

@Override
public void mouseMoved(MouseEvent e)
{
setCursor(Cursor.getPredefinedCursor(cursorFor(handleAt(e.getX(), e.getY()))));
}

@Override
public void mouseDragged(MouseEvent e)
{
if (dragFrom != null)
{
double s = viewScale();
double dx = (e.getX() - dragX) / s;
double dy = (e.getY() - dragY) / s;
set(dragEdges == 0 ? dragFrom.moved(dx, dy) : dragFrom.resized(dragEdges, dx, dy, e.isShiftDown()));
}
}

@Override
public void mouseWheelMoved(MouseWheelEvent e)
{
zoomAt(e.getX(), e.getY(), e.getPreciseWheelRotation());
}
};
addMouseListener(mouse);
addMouseMotionListener(mouse);
addMouseWheelListener(mouse);
key(KeyEvent.VK_LEFT, -1, 0);
key(KeyEvent.VK_RIGHT, 1, 0);
key(KeyEvent.VK_UP, 0, -1);
key(KeyEvent.VK_DOWN, 0, 1);
}

/** The arrow key {@code code} nudges the image in the direction (dx, dy) of -1, 0 or 1 (Shift: further). */
private void key(int code, int dx, int dy)
{
for (boolean far : new boolean[]{false, true})
{
String name = "nudge" + code + far;
getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, far ? InputEvent.SHIFT_DOWN_MASK : 0), name);
double step = far ? NUDGE_FAR : NUDGE;
getActionMap().put(name, new AbstractAction()
{
@Override
public void actionPerformed(ActionEvent e)
{
set(placement.moved(dx * step, dy * step));
}
});
}
}

/** Scales the image about view position (x, y): wheel rotation -1 (up, away) zooms in one step. */
void zoomAt(int x, int y, double rotation)
{
double[] at = toLayout(x, y);
set(placement.scaledAbout(at[0], at[1], Math.pow(WHEEL_STEP, -rotation)));
}

ImagePlacement placement()
{
return placement;
}

/** Shows {@code p} and tells the listener. */
void set(ImagePlacement p)
{
if (!p.equals(placement))
{
placement = p;
repaint();
listener.accept(p);
}
}

/** Layout pixels to view pixels: the layout fitted and centred, with 24 view pixels of room around it. */
private AffineTransform view()
{
Dimension d = getWidth() > 0 ? getSize() : getPreferredSize();
double s = Math.max(1e-3, Math.min((d.width - 48.0) / outline.width, (d.height - 48.0) / outline.height));
return new AffineTransform(s, 0, 0, s, (d.width - outline.width * s) / 2, (d.height - outline.height * s) / 2);
}

/** View pixels per layout pixel. */
double viewScale()
{
return view().getScaleX();
}

/** The layout position under view position (x, y). */
double[] toLayout(double x, double y)
{
AffineTransform v = view();
return new double[]{(x - v.getTranslateX()) / v.getScaleX(), (y - v.getTranslateY()) / v.getScaleY()};
}

/** The view position of layout position (lx, ly). */
double[] toView(double lx, double ly)
{
double[] p = {lx, ly};
view().transform(p, 0, p, 0, 1);
return p;
}

/**
* The handle of the image's box at view position (x, y): its {@link ImagePlacement#resized} edges (two at a
* corner, which wins over an edge), or 0 where a drag moves the image. A box narrower than two grabs: the nearer
* edge.
*/
int handleAt(int x, int y)
{
double[] b = placement.bounds();
double[] tl = toView(b[0], b[1]);
double[] br = toView(b[2], b[3]);
double dl = Math.abs(x - tl[0]);
double dr = Math.abs(x - br[0]);
double dt = Math.abs(y - tl[1]);
double db = Math.abs(y - br[1]);
int edges = 0;
if (y >= tl[1] - HANDLE_HIT && y <= br[1] + HANDLE_HIT && Math.min(dl, dr) <= HANDLE_HIT)
edges = dl < dr ? ImagePlacement.LEFT : ImagePlacement.RIGHT;
if (x >= tl[0] - HANDLE_HIT && x <= br[0] + HANDLE_HIT && Math.min(dt, db) <= HANDLE_HIT)
edges |= dt < db ? ImagePlacement.TOP : ImagePlacement.BOTTOM;
return edges;
}

/** The cursor over a handle ({@link #handleAt}): a resize arrow its way, else the move cursor. */
private static int cursorFor(int edges)
{
boolean l = (edges & ImagePlacement.LEFT) != 0;
boolean r = (edges & ImagePlacement.RIGHT) != 0;
if ((edges & ImagePlacement.TOP) != 0)
return l ? Cursor.NW_RESIZE_CURSOR : r ? Cursor.NE_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR;
if ((edges & ImagePlacement.BOTTOM) != 0)
return l ? Cursor.SW_RESIZE_CURSOR : r ? Cursor.SE_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR;
return l ? Cursor.W_RESIZE_CURSOR : r ? Cursor.E_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
}

@Override
protected void paintComponent(Graphics graphics)
{
Graphics2D g = (Graphics2D) graphics.create();
try
{
g.setColor(new Color(40, 40, 40));
g.fillRect(0, 0, getWidth(), getHeight());
AffineTransform view = view();
g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
AffineTransform imageToView = new AffineTransform(view);
imageToView.concatenate(placement.toLayout());
g.drawImage(image, imageToView, null);
// dim everything outside the outline: the margin, then the layout outside the part
Area margin = new Area(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
margin.subtract(new Area(view.createTransformedShape(
new Rectangle2D.Double(0, 0, outline.width, outline.height))));
g.setColor(new Color(0, 0, 0, DIM_ALPHA));
g.fill(margin);
g.drawImage(dimOverlay, view, null);
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
g.setColor(OUTLINE);
g.setStroke(new BasicStroke(1.5f));
g.draw(view.createTransformedShape(outlinePath));
for (double[] b : outline.bolts)
{
double[] v = toView(b[0], b[1]);
g.setColor(Color.BLACK);
g.fill(new Ellipse2D.Double(v[0] - 3.5, v[1] - 3.5, 7, 7));
g.setColor(Color.WHITE);
g.fill(new Ellipse2D.Double(v[0] - 2, v[1] - 2, 4, 4));
}
if (showNose)
{
// centred over (or under) the nose end
double[] b = outline.bounds();
double[] end = toView((b[0] + b[2]) / 2, outline.noseUp ? b[1] : b[3]);
g.setColor(OUTLINE);
g.setFont(getFont() != null ? getFont().deriveFont(Font.BOLD) : new Font(Font.SANS_SERIF, Font.BOLD, 12));
String label = outline.noseUp ? "▲ nose" : "▼ nose";
int ascent = g.getFontMetrics().getAscent();
g.drawString(label, (float) (end[0] - g.getFontMetrics().stringWidth(label) / 2.0),
(float) Math.max(ascent, outline.noseUp ? end[1] - 6 : end[1] + ascent + 4));
}
paintHandles(g);
}
finally
{
g.dispose();
}
}

/** The image's box, dashed, with a square handle (7 view pixels) at each corner and edge middle. */
private void paintHandles(Graphics2D g)
{
double[] b = placement.bounds();
double[] tl = toView(b[0], b[1]);
double[] br = toView(b[2], b[3]);
g.setColor(new Color(255, 255, 255, 170));
g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
g.draw(new Rectangle2D.Double(tl[0], tl[1], br[0] - tl[0], br[1] - tl[1]));
g.setStroke(new BasicStroke(1f));
double mx = (tl[0] + br[0]) / 2;
double my = (tl[1] + br[1]) / 2;
for (double[] p : new double[][]{{tl[0], tl[1]}, {mx, tl[1]}, {br[0], tl[1]}, {br[0], my}, {br[0], br[1]},
{mx, br[1]}, {tl[0], br[1]}, {tl[0], my}})
{
Rectangle2D r = new Rectangle2D.Double(p[0] - 3.5, p[1] - 3.5, 7, 7);
g.setColor(Color.WHITE);
g.fill(r);
g.setColor(Color.BLACK);
g.draw(r);
}
}
}
