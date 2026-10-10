package com.gielinorskate.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/**
 * The board designs' thumbnails (designs/thumbs/ID.png, drawn by the bake at about in-game size), and their greyed "locked" look. Loaded once each. EDT only.
 */
@Slf4j
final class DesignThumbs
{
	private static final String DIR = "/com/gielinorskate/designs/thumbs/";
	/** A locked design's picture: grey, at this opacity. */
	private static final float LOCKED_ALPHA = 0.45f;

	private final Map<String, BufferedImage> plain = new HashMap<>();
	private final Map<String, BufferedImage> locked = new HashMap<>();

	/** The design's thumbnail, or a 1x1 transparent image if it is missing. */
	BufferedImage get(String id)
	{
		return plain.computeIfAbsent(id, DesignThumbs::load);
	}

	/** The design's thumbnail greyed out, for a locked design. */
	BufferedImage locked(String id)
	{
		return locked.computeIfAbsent(id, k -> grey(get(k)));
	}

	/** How many thumbnails are kept, plain and locked (tests). */
	int size()
	{
		return plain.size() + locked.size();
	}

	private static BufferedImage load(String id)
	{
		try (InputStream in = DesignThumbs.class.getResourceAsStream(DIR + id + ".png"))
		{
			BufferedImage img = in == null ? null : ImageIO.read(in);
			if (img != null)
				return img;
		}
		catch (IOException e)
		{
			log.debug("RuneSkate: no thumbnail for design {}", id, e);
		}
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	}

	/** {@code src} in grey (its luminance) at {@link #LOCKED_ALPHA} of its opacity. */
	static BufferedImage grey(BufferedImage src)
	{
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				int argb = src.getRGB(x, y);
				// luminance from red, green and blue
				int l = ((argb >> 16 & 255) * 299 + (argb >> 8 & 255) * 587 + (argb & 255) * 114) / 1000;
				out.setRGB(x, y, Math.round((argb >>> 24) * LOCKED_ALPHA) << 24 | l << 16 | l << 8 | l);
			}
		}
		return out;
	}
}
