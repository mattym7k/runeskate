package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.image.BufferedImage;

/**
* Bakes a player's image into a part's corner colours at runtime, the way tools/blend_to_board_baked.py bakes the
* shipped designs (tools/design_sampling.py): the image is box-blurred so each corner averages about the image
* under its cell, then sampled bilinearly at the corner. A corner's design UV (v up) gives its layout position
* (u * width, (1 - v) * height); the inverse of the player's {@link ImagePlacement} takes that into the image. The
* blur radius is the layout's ({@link DesignLayout.Part#blur}) in image pixels. Unlike the offline bake (which
* wraps), the image is clamped at its edges: outside it, the colour is its edge colour. Corners without a design
* UV (wheel hubs) keep the geometry's colour. Pure; a {@link Source} is not thread-safe.
*/
public final class CustomBake
{
/** Largest blur radius, image pixels: a tiny image scaled up a lot would otherwise blur away entirely. */
static final int MAX_RADIUS = 64;

private CustomBake()
{
}

/**
* A player's image ready to bake: its colours as 0..1 sRGB values, transparent pixels laid over a backdrop
* colour, with the last blurred copy kept (dragging the image keeps the radius).
*/
public static final class Source
{
final int width;
final int height;
/** {r, g, b}, row by row from the top. */
private final float[][] rgb = new float[3][];
private int cachedRadius = -1;
private float[][] cached;

/**
* @param argb the image's pixels, row by row from the top (0xAARRGGBB)
* @param backdrop 0xRRGGBB shown through transparent pixels
*/
public Source(int[] argb, int width, int height, int backdrop)
{
if (width < 1 || height < 1 || argb.length < width * height)
throw new IllegalArgumentException("bad image");
this.width = width;
this.height = height;
for (int k = 0; k < 3; k++)
{
int shift = 16 - 8 * k;
float back = (backdrop >> shift & 255) / 255f;
float[] c = rgb[k] = new float[width * height];
for (int i = 0; i < c.length; i++)
{
float t = (argb[i] >>> 24) / 255f;
c[i] = (argb[i] >> shift & 255) / 255f * t + back * (1 - t);
}
}
}

/** The image blurred at {@code radius} as {r, g, b}: the last radius asked is kept. */
float[][] blurred(int radius)
{
if (radius != cachedRadius)
{
cached = radius < 1 ? rgb : new float[][]{boxBlur(rgb[0], width, height, radius),
boxBlur(rgb[1], width, height, radius), boxBlur(rgb[2], width, height, radius)};
cachedRadius = radius;
}
return cached;
}
}

/** A player's image ready to bake for {@code part}: transparent pixels show the part's plain colour. */
static Source source(BufferedImage image, DesignPart part)
{
int w = image.getWidth();
int h = image.getHeight();
return new Source(image.getRGB(0, 0, w, h, null, 0, w), w, h,
part == DesignPart.GRIP ? 0x202020 : part == DesignPart.DECK ? 0xD9C49F : 0xEDEBE3);
}

/** The blur radius in image pixels for a layout radius, when one image pixel covers {@code scale} layout ones. */
static int radius(int layoutRadius, double scale)
{
return (int) Math.max(0, Math.min(MAX_RADIUS, Math.floor(layoutRadius / scale + 0.5)));
}

/**
* The part's corner colours (3 per triangle, 0xRRGGBB) in the player's design: {@code mesh} is the part at
* High or Normal detail ({@code high}).
*/
public static int[] bake(BakedBoardGeometry.Mesh mesh, DesignLayout.Part layout, ImagePlacement placement,
Source src, boolean high)
{
int[] out = mesh.cornerRgb.clone();
float[] uv = mesh.cornerUv;
if (uv == null)
return out;
float[][] img = src.blurred(radius(layout.blur(high), placement.meanScale()));
for (int i = 0; i < out.length; i++)
{
float u = uv[i * 2];
float v = uv[i * 2 + 1];
if (!Float.isNaN(u) && !Float.isNaN(v))
{
double[] s = placement.toImage(u * (double) layout.width, (1 - v) * (double) layout.height);
out[i] = sample(img, src.width, src.height, s[0], s[1]);
}
}
return out;
}

/** The colour at image position (x, y) (pixel centres at +0.5), bilinear, clamped at the edges. */
static int sample(float[][] img, int w, int h, double x, double y)
{
double x0f = Math.floor(x - 0.5);
double y0f = Math.floor(y - 0.5);
double fx = x - 0.5 - x0f;
double fy = y - 0.5 - y0f;
int x0 = clamp(x0f, w);
int x1 = clamp(x0f + 1, w);
int y0 = clamp(y0f, h);
int y1 = clamp(y0f + 1, h);
int rgb = 0;
for (float[] a : img)
{
double top = a[y0 * w + x0] * (1 - fx) + a[y0 * w + x1] * fx;
double bot = a[y1 * w + x0] * (1 - fx) + a[y1 * w + x1] * fx;
rgb = rgb << 8 | (int) Math.round(Math.max(0, Math.min(1, top * (1 - fy) + bot * fy)) * 255);
}
return rgb;
}

private static int clamp(double i, int n)
{
return i < 0 ? 0 : i >= n ? n - 1 : (int) i;
}

/** Separable box blur of radius r (a (2r + 1)^2 average), the edge pixels repeated outside. */
static float[] boxBlur(float[] a, int w, int h, int r)
{
// along rows, then along columns
return blurLines(blurLines(a, w, h, 1, w, r), h, w, w, 1, r);
}

/**
* A box average of radius r along {@code lines} lines of {@code n} values each, {@code step} apart in the array,
* the lines starting {@code stride} apart; the end values repeated outside.
*/
private static float[] blurLines(float[] a, int n, int lines, int step, int stride, int r)
{
float[] out = new float[a.length];
// running sums: sum[k + 1] is the total of the first k values, from k = -r
double[] sum = new double[n + 2 * r + 1];
for (int line = 0; line < lines; line++)
{
int start = line * stride;
for (int k = -r; k < n + r; k++)
sum[k + r + 1] = sum[k + r] + a[start + Math.max(0, Math.min(n - 1, k)) * step];
for (int i = 0; i < n; i++)
out[start + i * step] = (float) ((sum[i + 2 * r + 1] - sum[i]) / (2 * r + 1.0));
}
return out;
}
}
