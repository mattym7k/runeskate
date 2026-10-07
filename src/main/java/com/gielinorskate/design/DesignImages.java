package com.gielinorskate.design;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.runelite.client.util.Filepath;

/**
 * Reading the image a player picked (that file only), within {@link CustomDesignRules}' limits, and the downscaled
 * copy kept for editing. Does file IO: never on the client thread or the EDT.
 */
public final class DesignImages
{
	private DesignImages()
	{
	}

	/** A picked image refused, with a message for the player. */
	public static final class Refused extends Exception
	{
		public Refused(String message)
		{
			super(message);
		}
	}

	/** The picked image: its full size, and the copy kept (at most {@link CustomDesignRules#STORED_MAX_SIDE}). */
	public static final class Picked
	{
		public final int sourceWidth;
		public final int sourceHeight;
		public final BufferedImage image;

		Picked(int sourceWidth, int sourceHeight, BufferedImage image)
		{
			this.sourceWidth = sourceWidth;
			this.sourceHeight = sourceHeight;
			this.image = image;
		}
	}

	/**
	 * Reads {@code file}: refused when too big (file or pixels) or not an image. An animated GIF gives its first
	 * frame. The size is checked before the pixels are decoded.
	 */
	public static Picked read(Filepath file) throws Refused
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
		{
			throw new Refused(problem);
		}
		// buffered in memory, never in a cache file, whatever ImageIO's global setting
		try (InputStream raw = file.openInputStream(); ImageInputStream in = new MemoryCacheImageInputStream(raw))
		{
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext())
			{
				throw new Refused("That file isn't an image RuneSkate can read (PNG, JPG, GIF or BMP).");
			}
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(in, false, true);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				problem = CustomDesignRules.imageProblem(w, h);
				if (problem != null)
				{
					throw new Refused(problem);
				}
				// a big picture is decoded at most about twice the kept size (every n-th pixel), not whole
				ImageReadParam param = reader.getDefaultReadParam();
				int s = subsampling(w, h);
				param.setSourceSubsampling(s, s, 0, 0);
				BufferedImage img = reader.read(0, param);
				return new Picked(w, h, keptCopy(img, CustomDesignRules.storedSize(w, h)));
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException | RuntimeException e)
		{
			throw new Refused("That image could not be read. Try saving it again as a PNG.");
		}
	}

	/** Writes {@code img} as a PNG to {@code out}, buffered in memory (never in an ImageIO cache file). */
	public static void writePng(BufferedImage img, OutputStream out) throws IOException
	{
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
		if (!writers.hasNext())
		{
			throw new IOException("no PNG writer");
		}
		ImageWriter writer = writers.next();
		try (ImageOutputStream ios = new MemoryCacheImageOutputStream(out))
		{
			writer.setOutput(ios);
			writer.write(img);
		}
		finally
		{
			writer.dispose();
		}
	}

	/** Reads an image from {@code in}, buffered in memory (never in an ImageIO cache file); null if none. */
	public static BufferedImage readImage(InputStream in) throws IOException
	{
		try (ImageInputStream iis = new MemoryCacheImageInputStream(in))
		{
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext())
			{
				return null;
			}
			ImageReader reader = readers.next();
			try
			{
				reader.setInput(iis, true, true);
				return reader.read(0);
			}
			finally
			{
				reader.dispose();
			}
		}
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

	/** {@code img} as ARGB, downscaled (averaging) to at most {@link CustomDesignRules#STORED_MAX_SIDE}. */
	public static BufferedImage keptCopy(BufferedImage img)
	{
		return keptCopy(img, CustomDesignRules.storedSize(img.getWidth(), img.getHeight()));
	}

	/** {@code img} as ARGB, downscaled (averaging) to {@code size} (never larger than it). */
	static BufferedImage keptCopy(BufferedImage img, int[] size)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		size = new int[]{Math.min(size[0], w), Math.min(size[1], h)};
		int[] src = img.getRGB(0, 0, w, h, null, 0, w);
		int[] px = size[0] == w && size[1] == h ? src : downscale(src, w, h, size[0], size[1]);
		BufferedImage out = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, size[0], size[1], px, 0, size[0]);
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
				double a = 0;
				double r = 0;
				double g = 0;
				double b = 0;
				double area = 0;
				for (int sy = (int) y0; sy < Math.min(h, Math.ceil(y1)); sy++)
				{
					double wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
					for (int sx = (int) x0; sx < Math.min(w, Math.ceil(x1)); sx++)
					{
						double wt = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
						int c = src[sy * w + sx];
						double ca = (c >>> 24) * wt;
						a += ca;
						r += (c >> 16 & 255) * ca;
						g += (c >> 8 & 255) * ca;
						b += (c & 255) * ca;
						area += wt;
					}
				}
				int oa = (int) Math.round(a / area);
				out[y * nw + x] = a <= 0 ? 0 : oa << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8
					| (int) Math.round(b / a);
			}
		}
		return out;
	}
}
