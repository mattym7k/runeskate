package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
* The small picture of a player's design that party members get: the design drawn into its part's whole layout
* at about the in-game (Normal detail) resolution, at most {@link #maxSide} pixels on its long side, PNG then
* base64 encoded, at most {@link #MAX_ENCODED} characters (made smaller until it fits). The receiver bakes its
* Normal-detail colours from it through {@link CustomBake}, the same path as the player's own bake. Pure.
*/
public final class SharedDesignImage
{
/** Hard cap on one part's picture once base64 encoded, characters. */
public static final int MAX_ENCODED = 8192;
/** The most PNG bytes {@link #MAX_ENCODED} base64 characters can hold. */
public static final int MAX_BYTES = MAX_ENCODED / 4 * 3;

private SharedDesignImage()
{
}

/** One part's picture, ready to send. */
public static final class Encoded
{
public final DesignPart part;
public final int width;
public final int height;
public final byte[] png;
public final String base64;

public Encoded(DesignPart part, int width, int height, byte[] png)
{
this.part = part;
this.width = width;
this.height = height;
this.png = png;
base64 = Base64.getEncoder().encodeToString(png);
}
}

/** The largest side a part's picture may have: 96 for the grip and deck, 40 for the wheels. */
public static int maxSide(DesignPart part)
{
return part == DesignPart.WHEELS ? 40 : 96;
}

/** True if a picture of this size may be one of {@code part}'s. */
public static boolean sizeAllowed(DesignPart part, int width, int height)
{
return width >= 1 && height >= 1 && Math.max(width, height) <= maxSide(part);
}

/**
* The design ({@code image} placed by {@code placement} in {@code layout}) drawn at {@code width} x
* {@code height}: each pixel the colour of the image under its centre, averaged over about the image area it
* covers, clamped at the image's edges as {@link CustomBake} does.
*/
static BufferedImage render(CustomBake.Source src, DesignLayout.Part layout, ImagePlacement placement, int width,
int height)
{
double cellX = layout.width / (double) width;
double cellY = layout.height / (double) height;
// image pixels one picture pixel spans (along the image's smaller scale): a box blur about that wide
// averages them
double cover = Math.max(cellX, cellY) / Math.min(placement.scaleX, placement.scaleY);
float[][] img = src.blurred((int) Math.max(0, Math.min(CustomBake.MAX_RADIUS,
Math.floor((cover - 1) / 2 + 0.5))));
BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
for (int j = 0; j < height; j++)
{
for (int i = 0; i < width; i++)
{
double[] s = placement.toImage((i + 0.5) * cellX, (j + 0.5) * cellY);
out.setRGB(i, j, CustomBake.sample(img, src.width, src.height, s[0], s[1]));
}
}
return out;
}

/**
* The part's picture of a design, made smaller (down to 8 pixels on its long side) until its encoding fits
* {@link #MAX_ENCODED}; null when even the smallest does not (or PNG writing fails).
*/
public static Encoded encode(CustomBake.Source src, DesignPart part, DesignLayout.Part layout,
ImagePlacement placement)
{
for (int side = maxSide(part); side >= 8; side = side * 3 / 4)
{
// the layout's long side gets side pixels
double f = side / (double) Math.max(layout.width, layout.height);
int w = Math.max(1, (int) Math.round(layout.width * f));
int h = Math.max(1, (int) Math.round(layout.height * f));
byte[] png = png(render(src, layout, placement, w, h));
if (png != null && (png.length + 2) / 3 * 4 <= MAX_ENCODED)
return new Encoded(part, w, h, png);
}
return null;
}

static byte[] png(BufferedImage img)
{
ByteArrayOutputStream out = new ByteArrayOutputStream();
try
{
DesignImages.writePng(img, out);
return out.toByteArray();
}
catch (IOException e)
{
return null;
}
}

/**
* A received picture's pixels (0xRRGGBB, row by row), or null unless {@code png} is at most {@link #MAX_BYTES}
* bytes and a PNG of exactly {@code width} x {@code height}. The size in the PNG's header is checked before any
* pixel is decoded, so a small file claiming a huge picture is refused without allocating it. Received bytes
* stay in memory, never in an ImageIO cache file.
*/
public static BufferedImage decode(byte[] png, int width, int height)
{
if (png == null || png.length > MAX_BYTES)
return null;
try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(png)))
{
ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
try
{
reader.setInput(in, true, true);
if (reader.getWidth(0) != width || reader.getHeight(0) != height)
return null;
BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
Graphics2D g = rgb.createGraphics();
g.drawImage(reader.read(0), 0, 0, null);
g.dispose();
return rgb;
}
finally
{
reader.dispose();
}
}
catch (IOException | RuntimeException e)
{
return null;
}
}

/**
* A received picture baked into the part's corner colours at Normal detail ({@code mesh}): the picture spans
* the part's whole layout, as it was drawn.
*/
public static int[] bake(BufferedImage picture, BakedBoardGeometry.Mesh mesh, DesignLayout.Part layout)
{
int w = picture.getWidth();
int h = picture.getHeight();
CustomBake.Source src = new CustomBake.Source(picture.getRGB(0, 0, w, h, null, 0, w), w, h, 0);
double scale = Math.max(layout.width / (double) w, layout.height / (double) h);
ImagePlacement covering = new ImagePlacement(w, h, layout.width / 2.0, layout.height / 2.0, scale, 0, false);
return CustomBake.bake(mesh, layout, covering, src, false);
}
}
