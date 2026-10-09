package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
* A small flat picture of one part in a design's corner colours, as tools/design_thumbs.py draws the shipped
* designs' thumbnails: unlit, the corner colours blended across each triangle (as the client does), the nearest
* surface in front, drawn at {@link #SS} times the size and box-filtered down. The grip is seen from above, nose to
* the right; the deck from below; the wheels as one wheel from the side. Uncovered pixels are transparent. Pure.
*/
public final class ThumbRender
{
/** The panel thumbnails' sizes (as tools/design_thumbs.py). */
public static final int BOARD_W = 92;
public static final int BOARD_H = 26;
public static final int WHEEL_SIZE = 40;
private static final int SS = 4;

private ThumbRender()
{
}

/** The panel thumbnail of a design of {@code part} with these corner colours over {@code mesh}. */
public static BufferedImage thumbnail(DesignPart part, BakedBoardGeometry.Mesh mesh, int[] cornerRgb)
{
boolean wheels = part == DesignPart.WHEELS;
return render(part, mesh, cornerRgb, wheels ? WHEEL_SIZE : BOARD_W, wheels ? WHEEL_SIZE : BOARD_H);
}

/** The part drawn into a w x h picture (fitted, centred). */
public static BufferedImage render(DesignPart part, BakedBoardGeometry.Mesh mesh, int[] cornerRgb, int w, int h)
{
// screen x, screen y and depth (nearer is smaller) of each vertex
int nv = mesh.vertexCount();
double[] sx = new double[nv];
double[] sy = new double[nv];
double[] sd = new double[nv];
for (int i = 0; i < nv; i++)
{
double x = mesh.vertices[i * 3];
double y = mesh.vertices[i * 3 + 1];
sx[i] = mesh.vertices[i * 3 + 2];
sy[i] = part == DesignPart.GRIP ? x : part == DesignPart.DECK ? -x : y;
sd[i] = part == DesignPart.GRIP ? y : part == DesignPart.DECK ? -y : x;
}
int[] f = mesh.faces;
int nf = mesh.faceCount();
boolean[] keep = new boolean[nf];
Arrays.fill(keep, true);
if (part == DesignPart.WHEELS)
{
// one wheel: the one at the far end (largest z) on the near side
double midZ = (Arrays.stream(sx).min().getAsDouble() + Arrays.stream(sx).max().getAsDouble()) / 2;
double midD = (Arrays.stream(sd).min().getAsDouble() + Arrays.stream(sd).max().getAsDouble()) / 2;
for (int t = 0; t < nf; t++)
{
int a = f[t * 3];
int b = f[t * 3 + 1];
int c = f[t * 3 + 2];
keep[t] = Math.min(sx[a], Math.min(sx[b], sx[c])) > midZ && Math.max(sd[a], Math.max(sd[b], sd[c])) < midD;
}
}
double x0 = Double.MAX_VALUE;
double x1 = -Double.MAX_VALUE;
double y0 = Double.MAX_VALUE;
double y1 = -Double.MAX_VALUE;
for (int t = 0; t < nf * 3; t++)
{
if (keep[t / 3])
{
x0 = Math.min(x0, sx[f[t]]);
x1 = Math.max(x1, sx[f[t]]);
y0 = Math.min(y0, sy[f[t]]);
y1 = Math.max(y1, sy[f[t]]);
}
}
BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
if (x0 > x1)
return out;
int bw = w * SS;
int bh = h * SS;
double scale = Math.min((bw - 2) / Math.max(x1 - x0, 1e-9), (bh - 2) / Math.max(y1 - y0, 1e-9));
double ox = (bw - (x1 - x0) * scale) / 2 - x0 * scale;
double oy = (bh - (y1 - y0) * scale) / 2 - y0 * scale;
double[] depth = new double[bw * bh];
Arrays.fill(depth, Double.POSITIVE_INFINITY);
float[] rgb = new float[bw * bh * 3];
for (int t = 0; t < nf; t++)
{
if (!keep[t])
continue;
int a = f[t * 3];
int b = f[t * 3 + 1];
int c = f[t * 3 + 2];
int corner = t * 3;
PartOutline.raster(sx[a] * scale + ox, sy[a] * scale + oy, sx[b] * scale + ox, sy[b] * scale + oy,
sx[c] * scale + ox, sy[c] * scale + oy, bw, bh, (x, y, l) ->
{
double d = l[0] * sd[a] + l[1] * sd[b] + l[2] * sd[c];
int p = y * bw + x;
if (d < depth[p])
{
depth[p] = d;
for (int k = 0; k < 3; k++)
{
int s = 16 - 8 * k;
rgb[p * 3 + k] = (float) (l[0] * (cornerRgb[corner] >> s & 255)
+ l[1] * (cornerRgb[corner + 1] >> s & 255) + l[2] * (cornerRgb[corner + 2] >> s & 255));
}
}
});
}
// box-filtered down: colours averaged over the covered samples, alpha the covered share
for (int y = 0; y < h; y++)
{
for (int x = 0; x < w; x++)
{
int cov = 0;
double[] sum = new double[3];
for (int j = 0; j < SS; j++)
{
for (int i = 0; i < SS; i++)
{
int p = (y * SS + j) * bw + x * SS + i;
if (depth[p] != Double.POSITIVE_INFINITY)
{
cov++;
for (int k = 0; k < 3; k++)
sum[k] += rgb[p * 3 + k];
}
}
}
if (cov > 0)
{
int argb = (int) Math.round(cov * 255.0 / (SS * SS));
for (double v : sum)
argb = argb << 8 | (int) Math.max(0, Math.min(255, Math.round(v / cov)));
out.setRGB(x, y, argb);
}
}
}
return out;
}
}
