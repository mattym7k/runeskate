package com.gielinorskate.design;

import com.gielinorskate.render.BakedBoardGeometry;

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
		final float[] r;
		final float[] g;
		final float[] b;
		private int cachedRadius = -1;
		private float[][] cached;

		/**
		 * @param argb the image's pixels, row by row from the top (0xAARRGGBB)
		 * @param backdrop 0xRRGGBB shown through transparent pixels
		 */
		public Source(int[] argb, int width, int height, int backdrop)
		{
			if (width < 1 || height < 1 || argb.length < width * height)
			{
				throw new IllegalArgumentException("bad image");
			}
			this.width = width;
			this.height = height;
			int n = width * height;
			r = new float[n];
			g = new float[n];
			b = new float[n];
			float br = (backdrop >> 16 & 255) / 255f;
			float bg = (backdrop >> 8 & 255) / 255f;
			float bb = (backdrop & 255) / 255f;
			for (int i = 0; i < n; i++)
			{
				int c = argb[i];
				int a = c >>> 24;
				float pr = (c >> 16 & 255) / 255f;
				float pg = (c >> 8 & 255) / 255f;
				float pb = (c & 255) / 255f;
				if (a == 255)
				{
					r[i] = pr;
					g[i] = pg;
					b[i] = pb;
				}
				else
				{
					float t = a / 255f;
					r[i] = pr * t + br * (1 - t);
					g[i] = pg * t + bg * (1 - t);
					b[i] = pb * t + bb * (1 - t);
				}
			}
		}

		public int width()
		{
			return width;
		}

		public int height()
		{
			return height;
		}

		/** The image blurred at {@code radius} as {r, g, b}: the last radius asked is kept. */
		float[][] blurred(int radius)
		{
			if (radius != cachedRadius)
			{
				cached = radius < 1 ? new float[][]{r, g, b}
					: new float[][]{boxBlur(r, width, height, radius), boxBlur(g, width, height, radius),
					boxBlur(b, width, height, radius)};
				cachedRadius = radius;
			}
			return cached;
		}
	}

	/** The blur radius in image pixels for a layout radius, when one image pixel covers {@code scale} layout ones. */
	public static int radius(int layoutRadius, double scale)
	{
		double r = Math.floor(layoutRadius / scale + 0.5);
		return (int) Math.max(0, Math.min(MAX_RADIUS, r));
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
		{
			return out;
		}
		float[][] img = src.blurred(radius(layout.blur(high), placement.meanScale()));
		for (int i = 0; i < out.length; i++)
		{
			float u = uv[i * 2];
			float v = uv[i * 2 + 1];
			if (Float.isNaN(u) || Float.isNaN(v))
			{
				continue;
			}
			double[] s = placement.toImage(u * (double) layout.width, (1 - v) * (double) layout.height);
			out[i] = sample(img, src.width, src.height, s[0], s[1]);
		}
		return out;
	}

	/** The colour at image position (x, y) (pixel centres at +0.5), bilinear, clamped at the edges. */
	static int sample(float[][] img, int w, int h, double x, double y)
	{
		double fx0 = x - 0.5;
		double fy0 = y - 0.5;
		double x0f = Math.floor(fx0);
		double y0f = Math.floor(fy0);
		double fx = fx0 - x0f;
		double fy = fy0 - y0f;
		int x0 = clamp(x0f, w);
		int x1 = clamp(x0f + 1, w);
		int y0 = clamp(y0f, h);
		int y1 = clamp(y0f + 1, h);
		int rgb = 0;
		for (int c = 0; c < 3; c++)
		{
			float[] a = img[c];
			double top = a[y0 * w + x0] * (1 - fx) + a[y0 * w + x1] * fx;
			double bot = a[y1 * w + x0] * (1 - fx) + a[y1 * w + x1] * fx;
			double val = top * (1 - fy) + bot * fy;
			int k = (int) Math.round(Math.max(0, Math.min(1, val)) * 255);
			rgb = rgb << 8 | k;
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
		float[] tmp = new float[a.length];
		float[] out = new float[a.length];
		double[] line = new double[Math.max(w, h) + 2 * r + 1];
		double n = 2 * r + 1;
		// along rows
		for (int y = 0; y < h; y++)
		{
			line[0] = 0;
			for (int k = -r; k < w + r; k++)
			{
				int x = k < 0 ? 0 : k >= w ? w - 1 : k;
				line[k + r + 1] = line[k + r] + a[y * w + x];
			}
			for (int x = 0; x < w; x++)
			{
				tmp[y * w + x] = (float) ((line[x + 2 * r + 1] - line[x]) / n);
			}
		}
		// along columns
		for (int x = 0; x < w; x++)
		{
			line[0] = 0;
			for (int k = -r; k < h + r; k++)
			{
				int y = k < 0 ? 0 : k >= h ? h - 1 : k;
				line[k + r + 1] = line[k + r] + tmp[y * w + x];
			}
			for (int y = 0; y < h; y++)
			{
				out[y * w + x] = (float) ((line[y + 2 * r + 1] - line[y]) / n);
			}
		}
		return out;
	}
}
