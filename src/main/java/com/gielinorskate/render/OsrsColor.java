package com.gielinorskate.render;

/**
 * Pure conversions between sRGB and the client's packed 16-bit HSL (6 bits hue, 3 saturation, 7 lightness).
 * {@link #hslToRgb} is the client's colour palette (the same table the GPU shader reproduces); {@link #rgbToHsl}
 * finds the packed colour whose palette entry is nearest the given RGB.
 */
public final class OsrsColor
{
	/** The client's default brightness setting, used when the real one isn't known. */
	public static final double DEFAULT_BRIGHTNESS = 0.8;

	private OsrsColor()
	{
	}

	public static int hue(int hsl)
	{
		return (hsl >> 10) & 63;
	}

	public static int saturation(int hsl)
	{
		return (hsl >> 7) & 7;
	}

	public static int lightness(int hsl)
	{
		return hsl & 127;
	}

	public static int pack(int hue, int saturation, int lightness)
	{
		return (hue & 63) << 10 | (saturation & 7) << 7 | (lightness & 127);
	}

	/** The palette's 0xRRGGBB for a packed HSL colour at a brightness (the client's 0.6 to 0.9; lower is brighter). */
	public static int hslToRgb(int hsl, double brightness)
	{
		double h = hue(hsl) / 64.0 + 0.0078125;
		double s = saturation(hsl) / 8.0 + 0.0625;
		double l = lightness(hsl) / 128.0;
		double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
		double p = 2.0 * l - q;
		double tr = h + 1.0 / 3.0;
		if (tr > 1.0)
		{
			tr -= 1.0;
		}
		double tb = h - 1.0 / 3.0;
		if (tb < 0.0)
		{
			tb += 1.0;
		}
		int r = channel(hueToChannel(p, q, tr), brightness);
		int g = channel(hueToChannel(p, q, h), brightness);
		int b = channel(hueToChannel(p, q, tb), brightness);
		int rgb = r << 16 | g << 8 | b;
		return rgb == 0 ? 1 : rgb;
	}

	private static double hueToChannel(double p, double q, double t)
	{
		if (6.0 * t < 1.0)
		{
			return p + (q - p) * 6.0 * t;
		}
		if (2.0 * t < 1.0)
		{
			return q;
		}
		if (3.0 * t < 2.0)
		{
			return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
		}
		return p;
	}

	/** As the client: the channel quantised to 8 bits, then brightness-adjusted (c^brightness). */
	private static int channel(double c, double brightness)
	{
		int c8 = (int) (c * 256.0);
		double adjusted = Math.pow(c8 / 256.0, brightness);
		return Math.min(255, (int) (adjusted * 256.0));
	}

	/**
	 * The packed HSL colour whose palette entry is nearest {@code rgb} (squared RGB distance): an analytic first
	 * guess, then a hill-climb over neighbours (hue +-1, saturation +-1, lightness +-2) to a local best.
	 */
	public static int rgbToHsl(int rgb, double brightness)
	{
		double inv = 1.0 / brightness;
		double r = Math.pow(((rgb >> 16) & 255) / 256.0, inv);
		double g = Math.pow(((rgb >> 8) & 255) / 256.0, inv);
		double b = Math.pow((rgb & 255) / 256.0, inv);
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double l = (max + min) / 2.0;
		double h = 0.0;
		double s = 0.0;
		if (max > min)
		{
			double d = max - min;
			s = l < 0.5 ? d / (max + min) : d / (2.0 - max - min);
			if (max == r)
			{
				h = (g - b) / d / 6.0;
			}
			else if (max == g)
			{
				h = ((b - r) / d + 2.0) / 6.0;
			}
			else
			{
				h = ((r - g) / d + 4.0) / 6.0;
			}
			if (h < 0.0)
			{
				h += 1.0;
			}
		}
		int h0 = ((int) Math.floor(h * 64.0)) & 63;
		int s0 = Math.max(0, Math.min(7, (int) Math.floor(s * 8.0)));
		int l0 = Math.max(0, Math.min(127, (int) Math.round(l * 128.0)));

		// hill-climb from the guess: the best of its neighbours, until none is better
		int best = pack(h0, s0, l0);
		long bestError = distance(rgb, hslToRgb(best, brightness));
		for (int step = 0; step < 64; step++)
		{
			int centre = best;
			for (int dh = -1; dh <= 1; dh++)
			{
				for (int ds = -1; ds <= 1; ds++)
				{
					int sat = saturation(centre) + ds;
					if (sat < 0 || sat > 7)
					{
						continue;
					}
					for (int dl = -2; dl <= 2; dl++)
					{
						int lum = lightness(centre) + dl;
						if (lum < 0 || lum > 127)
						{
							continue;
						}
						int candidate = pack(hue(centre) + dh, sat, lum);
						long e = distance(rgb, hslToRgb(candidate, brightness));
						if (e < bestError)
						{
							bestError = e;
							best = candidate;
						}
					}
				}
			}
			if (best == centre)
			{
				break;
			}
		}
		return best;
	}

	/** Squared distance between two 0xRRGGBB colours. */
	static long distance(int a, int b)
	{
		long dr = ((a >> 16) & 255) - ((b >> 16) & 255);
		long dg = ((a >> 8) & 255) - ((b >> 8) & 255);
		long db = (a & 255) - (b & 255);
		return dr * dr + dg * dg + db * db;
	}

	/**
	 * A packed colour's lightness scaled by a light intensity, as the client lights a model: lightness * intensity
	 * / 128, kept within 2..126. {@code intensity} 128 leaves the colour as it is.
	 */
	public static int light(int hsl, int intensity)
	{
		int l = (hsl & 127) * intensity >> 7;
		if (l < 2)
		{
			l = 2;
		}
		else if (l > 126)
		{
			l = 126;
		}
		return (hsl & 0xff80) | l;
	}
}
