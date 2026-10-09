package com.gielinorskate.design;

import java.awt.geom.AffineTransform;
import java.util.function.DoubleBinaryOperator;
import lombok.EqualsAndHashCode;

/**
* Where a player's image lies in a part's design layout ({@link DesignLayout.Part}): the image (its pixels, top-left
* origin, y down) is stretched by {@link #scaleX} along its own width and {@link #scaleY} along its own height
* (layout pixels per image pixel), mirrored left-right if {@link #flipped}, turned clockwise by {@link #turns}
* quarter turns about its centre and centred at ({@link #cx}, {@link #cy}) in layout pixels (top-left origin, y
* down). Positions are continuous: pixel (i, j) covers [i, i + 1) x [j, j + 1).
* <p>
* The stretch belongs to the picture: turning or mirroring it turns the stretched picture. The editor's handles work
* on the placed image's box in the layout ({@link #resized}); since only quarter turns exist that box is always
* upright, and dragging its top edge stretches whichever image axis runs up the layout. Saved designs from before
* the stretch had one scale: both scales equal ({@link #uniform}). Pure, immutable.
*/
@EqualsAndHashCode
public final class ImagePlacement
{
/** Each side of the placed image stays between these many layout pixels. */
static final double MIN_SIDE = 8;
static final double MAX_SIDE = 16384;

/** {@link #resized} edges: one for an edge handle, two for a corner. */
public static final int LEFT = 1;
public static final int TOP = 2;
public static final int RIGHT = 4;
public static final int BOTTOM = 8;

/** The image's size in its own pixels. */
public final int imageWidth;
public final int imageHeight;
/** The image centre's position in the layout. */
public final double cx;
public final double cy;
/** Layout pixels per image pixel along the image's own width (before mirroring and turning). */
public final double scaleX;
/** Layout pixels per image pixel along the image's own height. */
public final double scaleY;
/** Clockwise quarter turns, 0..3. */
public final int turns;
/** Mirrored left-right (in the image, before turning). */
public final boolean flipped;

/** Scaled the same both ways. */
public ImagePlacement(int imageWidth, int imageHeight, double cx, double cy, double scale, int turns,
boolean flipped)
{
this(imageWidth, imageHeight, cx, cy, scale, scale, turns, flipped);
}

/** Each scale is held so its side spans {@link #MIN_SIDE}..{@link #MAX_SIDE} layout pixels. */
public ImagePlacement(int imageWidth, int imageHeight, double cx, double cy, double scaleX, double scaleY,
int turns, boolean flipped)
{
// (a sum of finite values is finite unless one is infinite or NaN)
if (imageWidth < 1 || imageHeight < 1 || !Double.isFinite(cx + cy + scaleX + scaleY) || scaleX <= 0
|| scaleY <= 0)
throw new IllegalArgumentException("bad placement");
this.imageWidth = imageWidth;
this.imageHeight = imageHeight;
this.cx = cx;
this.cy = cy;
this.scaleX = Math.max(MIN_SIDE / imageWidth, Math.min(MAX_SIDE / imageWidth, scaleX));
this.scaleY = Math.max(MIN_SIDE / imageHeight, Math.min(MAX_SIDE / imageHeight, scaleY));
this.turns = Math.floorMod(turns, 4);
this.flipped = flipped;
}

/** Scaled the same both ways (as every placement was before the stretch handles). */
public boolean uniform()
{
return scaleX == scaleY;
}

/** The scale an image pixel averages to (the geometric mean of the two): blur radii are worked out with it. */
public double meanScale()
{
return uniform() ? scaleX : Math.sqrt(scaleX * scaleY);
}

private boolean sideways()
{
return turns % 2 == 1;
}

/** The placed image's width in layout pixels. */
public double placedWidth()
{
return sideways() ? imageHeight * scaleY : imageWidth * scaleX;
}

public double placedHeight()
{
return sideways() ? imageWidth * scaleX : imageHeight * scaleY;
}

/** The placed image's box in the layout: {minX, minY, maxX, maxY}. */
public double[] bounds()
{
double hw = placedWidth() / 2;
double hh = placedHeight() / 2;
return new double[]{cx - hw, cy - hh, cx + hw, cy + hh};
}

/** The placement as a transform from image positions to layout positions. */
AffineTransform toLayout()
{
AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
t.quadrantRotate(turns);
t.scale(flipped ? -scaleX : scaleX, scaleY);
t.translate(-imageWidth / 2.0, -imageHeight / 2.0);
return t;
}

/** Image position {x, y} of layout position (lx, ly): the inverse of {@link #toLayout}. */
public double[] toImage(double lx, double ly)
{
double x = lx - cx;
double y = ly - cy;
for (int i = 0; i < turns; i++)
{
double t = x;
x = y;
y = -t;
}
return new double[]{(flipped ? -x : x) / scaleX + imageWidth / 2.0, y / scaleY + imageHeight / 2.0};
}

/** Moved by (dx, dy) layout pixels. */
public ImagePlacement moved(double dx, double dy)
{
return new ImagePlacement(imageWidth, imageHeight, cx + dx, cy + dy, scaleX, scaleY, turns, flipped);
}

/**
* Scaled by {@code factor} both ways about layout position (px, py), which stays where it is: the stretch is
* kept, and the factor is held so neither side passes its limit.
*/
public ImagePlacement scaledAbout(double px, double py, double factor)
{
double lo = Math.max(MIN_SIDE / (imageWidth * scaleX), MIN_SIDE / (imageHeight * scaleY));
double hi = Math.min(MAX_SIDE / (imageWidth * scaleX), MAX_SIDE / (imageHeight * scaleY));
double f = Math.max(Math.min(lo, 1), Math.min(Math.max(hi, 1), factor));
return new ImagePlacement(imageWidth, imageHeight, px + (cx - px) * f, py + (cy - py) * f, scaleX * f,
scaleY * f, turns, flipped);
}

/** Turned a quarter turn clockwise about its centre (the stretch turns with the picture). */
public ImagePlacement rotated()
{
return new ImagePlacement(imageWidth, imageHeight, cx, cy, scaleX, scaleY, turns + 1, flipped);
}

/** Mirrored left-right in the layout, about its centre. */
public ImagePlacement flippedHorizontally()
{
// a mirror after turning t equals turning -t after a mirror
return new ImagePlacement(imageWidth, imageHeight, cx, cy, scaleX, scaleY, 4 - turns, !flipped);
}

/**
* Centred on {@code box} {minX, minY, maxX, maxY}, as large as fits inside it, in the image's own proportions
* (any stretch is undone; turn and mirror kept).
*/
public ImagePlacement fit(double[] box)
{
return sized(box, Math::min);
}

/** Centred on {@code box}, as small as covers it completely, in the image's own proportions. */
public ImagePlacement fill(double[] box)
{
return sized(box, Math::max);
}

/** Centred on {@code box} at the scale {@code pick} chooses of the two that fit its width and its height. */
private ImagePlacement sized(double[] box, DoubleBinaryOperator pick)
{
double s = pick.applyAsDouble((box[2] - box[0]) / (sideways() ? imageHeight : imageWidth),
(box[3] - box[1]) / (sideways() ? imageWidth : imageHeight));
return new ImagePlacement(imageWidth, imageHeight, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, s, turns,
flipped);
}

/** Stretched to cover exactly {@code box} {minX, minY, maxX, maxY} (turn and mirror kept). */
public ImagePlacement stretched(double[] box)
{
double w = box[2] - box[0];
double h = box[3] - box[1];
return new ImagePlacement(imageWidth, imageHeight, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2,
(sideways() ? h : w) / imageWidth, (sideways() ? w : h) / imageHeight, turns, flipped);
}

/**
* A handle on the placed image's box dragged by (dx, dy) layout pixels. {@code edges} are the edges that move
* ({@link #LEFT}, {@link #TOP}, {@link #RIGHT}, {@link #BOTTOM}): one for an edge handle, which ignores the drag
* along that edge, two for a corner. The opposite edges stay where they are. On a corner {@code keepAspect}
* keeps the box's proportions, the larger pull deciding the size. Each side stays within {@link #MIN_SIDE} and
* {@link #MAX_SIDE}, so an edge dragged past the opposite one stops at the smallest size.
*/
public ImagePlacement resized(int edges, double dx, double dy, boolean keepAspect)
{
double[] b = bounds();
double w = b[2] - b[0];
double h = b[3] - b[1];
double nw = w + ((edges & RIGHT) != 0 ? dx : (edges & LEFT) != 0 ? -dx : 0);
double nh = h + ((edges & BOTTOM) != 0 ? dy : (edges & TOP) != 0 ? -dy : 0);
if (keepAspect && (edges & (LEFT | RIGHT)) != 0 && (edges & (TOP | BOTTOM)) != 0)
{
double fx = nw / w;
double fy = nh / h;
double f = Math.abs(fx - 1) >= Math.abs(fy - 1) ? fx : fy;
f = Math.max(Math.max(MIN_SIDE / w, MIN_SIDE / h), Math.min(Math.min(MAX_SIDE / w, MAX_SIDE / h), f));
nw = w * f;
nh = h * f;
}
else
{
nw = Math.max(MIN_SIDE, Math.min(MAX_SIDE, nw));
nh = Math.max(MIN_SIDE, Math.min(MAX_SIDE, nh));
}
double x0 = (edges & LEFT) != 0 ? b[2] - nw : b[0];
double y0 = (edges & TOP) != 0 ? b[3] - nh : b[1];
return stretched(new double[]{x0, y0, x0 + nw, y0 + nh});
}

/** The starting placement of an image: unturned, unmirrored, covering {@code box}. */
public static ImagePlacement initial(int imageWidth, int imageHeight, double[] box)
{
return new ImagePlacement(imageWidth, imageHeight, 0, 0, 1, 0, false).fill(box);
}
}
