package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.image.BufferedImage;

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
	static final int SS = 4;

	private ThumbRender()
	{
	}

	/** The panel thumbnail of a design of {@code part} with these corner colours over {@code mesh}. */
	public static BufferedImage thumbnail(DesignPart part, BakedBoardGeometry.Mesh mesh, int[] cornerRgb)
	{
		return part == DesignPart.WHEELS ? render(part, mesh, cornerRgb, WHEEL_SIZE, WHEEL_SIZE)
			: render(part, mesh, cornerRgb, BOARD_W, BOARD_H);
	}

	/** The part drawn into a w x h picture (fitted, centred). */
	public static BufferedImage render(DesignPart part, BakedBoardGeometry.Mesh mesh, int[] cornerRgb, int w, int h)
	{
		int nv = mesh.vertexCount();
		double[] sx = new double[nv];
		double[] sy = new double[nv];
		double[] sd = new double[nv];
		for (int i = 0; i < nv; i++)
		{
			double x = mesh.vertices[i * 3];
			double y = mesh.vertices[i * 3 + 1];
			double z = mesh.vertices[i * 3 + 2];
			switch (part)
			{
				case GRIP:
					sx[i] = z;
					sy[i] = x;
					sd[i] = y;
					break;
				case DECK:
					sx[i] = z;
					sy[i] = -x;
					sd[i] = -y;
					break;
				default:
					sx[i] = z;
					sy[i] = y;
					sd[i] = x;
					break;
			}
		}
		int nf = mesh.faceCount();
		boolean[] keep = new boolean[nf];
		if (part == DesignPart.WHEELS)
		{
			// one wheel: the one at the far end (largest z) on the near side
			double zMin = Double.MAX_VALUE;
			double zMax = -Double.MAX_VALUE;
			double dMin = Double.MAX_VALUE;
			double dMax = -Double.MAX_VALUE;
			for (int i = 0; i < nv; i++)
			{
				zMin = Math.min(zMin, sx[i]);
				zMax = Math.max(zMax, sx[i]);
				dMin = Math.min(dMin, sd[i]);
				dMax = Math.max(dMax, sd[i]);
			}
			double midZ = (zMin + zMax) / 2;
			double midD = (dMin + dMax) / 2;
			for (int t = 0; t < nf; t++)
			{
				double lo = Double.MAX_VALUE;
				double hi = -Double.MAX_VALUE;
				for (int k = 0; k < 3; k++)
				{
					int vi = mesh.faces[t * 3 + k];
					lo = Math.min(lo, sx[vi]);
					hi = Math.max(hi, sd[vi]);
				}
				keep[t] = lo > midZ && hi < midD;
			}
		}
		else
		{
			java.util.Arrays.fill(keep, true);
		}
		double x0 = Double.MAX_VALUE;
		double x1 = -Double.MAX_VALUE;
		double y0 = Double.MAX_VALUE;
		double y1 = -Double.MAX_VALUE;
		for (int t = 0; t < nf; t++)
		{
			if (!keep[t])
			{
				continue;
			}
			for (int k = 0; k < 3; k++)
			{
				int vi = mesh.faces[t * 3 + k];
				x0 = Math.min(x0, sx[vi]);
				x1 = Math.max(x1, sx[vi]);
				y0 = Math.min(y0, sy[vi]);
				y1 = Math.max(y1, sy[vi]);
			}
		}
		int bw = w * SS;
		int bh = h * SS;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		if (x0 > x1)
		{
			return out;
		}
		double scale = Math.min((bw - 2) / Math.max(x1 - x0, 1e-9), (bh - 2) / Math.max(y1 - y0, 1e-9));
		double ox = (bw - (x1 - x0) * scale) / 2 - x0 * scale;
		double oy = (bh - (y1 - y0) * scale) / 2 - y0 * scale;
		double[] depth = new double[bw * bh];
		java.util.Arrays.fill(depth, Double.POSITIVE_INFINITY);
		float[] rgb = new float[bw * bh * 3];
		for (int t = 0; t < nf; t++)
		{
			if (keep[t])
			{
				drawTriangle(mesh, cornerRgb, t, sx, sy, sd, scale, ox, oy, bw, bh, depth, rgb);
			}
		}
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int cov = 0;
				double r = 0;
				double g = 0;
				double b = 0;
				for (int j = 0; j < SS; j++)
				{
					for (int i = 0; i < SS; i++)
					{
						int p = (y * SS + j) * bw + x * SS + i;
						if (depth[p] != Double.POSITIVE_INFINITY)
						{
							cov++;
							r += rgb[p * 3];
							g += rgb[p * 3 + 1];
							b += rgb[p * 3 + 2];
						}
					}
				}
				if (cov > 0)
				{
					int a = (int) Math.round(cov * 255.0 / (SS * SS));
					out.setRGB(x, y, a << 24 | channel(r / cov) << 16 | channel(g / cov) << 8 | channel(b / cov));
				}
			}
		}
		return out;
	}

	private static int channel(double v)
	{
		return (int) Math.max(0, Math.min(255, Math.round(v)));
	}

	private static void drawTriangle(BakedBoardGeometry.Mesh mesh, int[] cornerRgb, int t, double[] sx, double[] sy,
		double[] sd, double scale, double ox, double oy, int bw, int bh, double[] depth, float[] rgb)
	{
		int a = mesh.faces[t * 3];
		int b = mesh.faces[t * 3 + 1];
		int c = mesh.faces[t * 3 + 2];
		double ax = sx[a] * scale + ox;
		double ay = sy[a] * scale + oy;
		double bx = sx[b] * scale + ox;
		double by = sy[b] * scale + oy;
		double cx = sx[c] * scale + ox;
		double cy = sy[c] * scale + oy;
		double den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
		if (Math.abs(den) < 1e-12)
		{
			return;
		}
		int xa = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
		int xb = Math.min(bw - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
		int ya = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
		int yb = Math.min(bh - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
		int ca = cornerRgb[t * 3];
		int cb = cornerRgb[t * 3 + 1];
		int cc = cornerRgb[t * 3 + 2];
		for (int y = ya; y <= yb; y++)
		{
			double gy = y + 0.5;
			for (int x = xa; x <= xb; x++)
			{
				double gx = x + 0.5;
				double l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den;
				double l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den;
				double l3 = 1 - l1 - l2;
				if (l1 < 0 || l2 < 0 || l3 < 0)
				{
					continue;
				}
				double d = l1 * sd[a] + l2 * sd[b] + l3 * sd[c];
				int p = y * bw + x;
				if (d >= depth[p])
				{
					continue;
				}
				depth[p] = d;
				for (int k = 0; k < 3; k++)
				{
					int s = 16 - 8 * k;
					rgb[p * 3 + k] = (float) (l1 * (ca >> s & 255) + l2 * (cb >> s & 255) + l3 * (cc >> s & 255));
				}
			}
		}
	}
}
