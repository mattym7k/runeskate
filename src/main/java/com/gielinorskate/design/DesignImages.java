package com.gielinorskate.design;

import com.gielinorskate.Text;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.*;
import lombok.RequiredArgsConstructor;
import net.runelite.client.util.Filepath;

/**
* Reading the image a player picked (that file only), within {@link CustomDesignRules}' limits, and the downscaled
* copy kept for editing. Images are always buffered in memory, never in an ImageIO cache file (whatever ImageIO's
* global setting). Does file IO: never on the client thread or the EDT.
*/
final class DesignImages
{
private DesignImages()
{
}

/** A picked image refused, with a message for the player. */
static final class Refused extends Exception
{
Refused(String message)
{
super(message);
}
}

/** The picked image: its full size, and the copy kept (at most {@link CustomDesignRules#STORED_MAX_SIDE}). */
@RequiredArgsConstructor
static final class Picked
{
final int sourceWidth;
final int sourceHeight;
final BufferedImage image;
}

/**
* Reads {@code file}: refused when too big (file or pixels) or not an image. An animated GIF gives its first
* frame. The size is checked before the pixels are decoded.
*/
static Picked read(Filepath file) throws Refused
{
String problem;
try
{
problem = CustomDesignRules.fileProblem(file.size());
}
catch (IOException e)
{
throw new Refused("That file could not be opened.");
}
if (problem != null)
throw new Refused(problem);
try (InputStream raw = file.openInputStream(); ImageInputStream in = new MemoryCacheImageInputStream(raw))
{
Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
if (!readers.hasNext())
throw new Refused(Text.get("di.format"));
ImageReader reader = readers.next();
try
{
reader.setInput(in, false, true);
int w = reader.getWidth(0);
int h = reader.getHeight(0);
problem = CustomDesignRules.imageProblem(w, h);
if (problem != null)
throw new Refused(problem);
// a big picture is decoded at most about twice the kept size (every n-th pixel), not whole
ImageReadParam param = reader.getDefaultReadParam();
int s = subsampling(w, h);
param.setSourceSubsampling(s, s, 0, 0);
return new Picked(w, h, keptCopy(reader.read(0, param), CustomDesignRules.storedSize(w, h)));
}
finally
{
reader.dispose();
}
}
catch (IOException | RuntimeException e)
{
throw new Refused(Text.get("di.read"));
}
}

/** Writes {@code img} as a PNG to {@code out}. */
static void writePng(BufferedImage img, OutputStream out) throws IOException
{
try (ImageOutputStream ios = new MemoryCacheImageOutputStream(out))
{
if (!ImageIO.write(img, "png", ios))
throw new IOException("no PNG writer");
}
}

/** Reads an image from {@code in}; null if it isn't one. */
static BufferedImage readImage(InputStream in) throws IOException
{
return ImageIO.read(new MemoryCacheImageInputStream(in));
}

/**
* The subsampling a picture of {@code width} x {@code height} is decoded with: its long side then at most twice
* {@link CustomDesignRules#STORED_MAX_SIDE}.
*/
static int subsampling(int width, int height)
{
int twice = 2 * CustomDesignRules.STORED_MAX_SIDE;
return Math.max(1, (Math.max(width, height) + twice - 1) / twice);
}

/** {@code img} as ARGB, downscaled (averaging) to {@code size} (never larger than it). */
private static BufferedImage keptCopy(BufferedImage img, int[] size)
{
int w = img.getWidth();
int h = img.getHeight();
int nw = Math.min(size[0], w);
int nh = Math.min(size[1], h);
int[] src = img.getRGB(0, 0, w, h, null, 0, w);
BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
out.setRGB(0, 0, nw, nh, nw == w && nh == h ? src : downscale(src, w, h, nw, nh), 0, nw);
return out;
}

/** Area-average downscale of ARGB pixels (colours weighted by alpha, so clear pixels don't darken edges). */
static int[] downscale(int[] src, int w, int h, int nw, int nh)
{
int[] out = new int[nw * nh];
double fx = w / (double) nw;
double fy = h / (double) nh;
for (int y = 0; y < nh; y++)
{
double y0 = y * fy;
double y1 = y0 + fy;
for (int x = 0; x < nw; x++)
{
double x0 = x * fx;
double x1 = x0 + fx;
double[] sum = new double[4];
double area = 0;
for (int sy = (int) y0; sy < Math.min(h, Math.ceil(y1)); sy++)
{
double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
for (int sx = (int) x0; sx < Math.min(w, Math.ceil(x1)); sx++)
{
double wt = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
int c = src[sy * w + sx];
double ca = (c >>> 24) * wt;
sum[0] += ca;
for (int k = 1; k < 4; k++)
sum[k] += (c >> 24 - 8 * k & 255) * ca;
area += wt;
}
}
int argb = (int) Math.round(sum[0] / area);
for (int k = 1; k < 4; k++)
argb = argb << 8 | (int) Math.round(sum[k] / sum[0]);
out[y * nw + x] = sum[0] <= 0 ? 0 : argb;
}
}
return out;
}
}
