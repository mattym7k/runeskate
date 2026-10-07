package com.gielinorskate.design;

import com.gielinorskate.render.BakedBoardGeometry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a part's design faces lie in its design layout (layout pixels, top-left origin, y down), generated from the
 * geometry's corner UVs: the faces themselves (each distinct UV triangle once: the four wheels share one), their
 * outline (edges no other face shares), the bounding box, a coverage mask, the truck bolts (grip and deck) and which
 * end is the nose. Pure, immutable.
 */
public final class PartOutline
{
	/**
	 * The truck bolts' centres in board model space (x across, z along), measured from the baked board's hardware
	 * (the nuts under the deck): four per truck.
	 */
	static final float[][] BOLTS_XZ = {
		{-3.16f, -33.24f}, {3.16f, -33.24f}, {-3.16f, -22.14f}, {3.16f, -22.14f},
		{-3.16f, 22.14f}, {3.16f, 22.14f}, {-3.16f, 33.24f}, {3.16f, 33.24f}};

	public final int width;
	public final int height;
	/** Each distinct design face: 6 floats (x, y of A, B, C) in layout pixels. */
	private final float[] faces;
	/** Each outline edge: 4 floats (x1, y1, x2, y2) in layout pixels. */
	private final float[] edges;
	/** {minX, minY, maxX, maxY} of the design faces, layout pixels. */
	private final double[] bounds;
	/** Bolt centres {x, y} in layout pixels (none for wheels). */
	private final List<double[]> bolts;
	/** True if the nose (model +z) is towards the top of the layout. */
	public final boolean noseUp;
	private boolean[] mask;

	private PartOutline(int width, int height, float[] faces, float[] edges, double[] bounds, List<double[]> bolts,
		boolean noseUp)
	{
		this.width = width;
		this.height = height;
		this.faces = faces;
		this.edges = edges;
		this.bounds = bounds;
		this.bolts = bolts;
		this.noseUp = noseUp;
	}

	/** The outline of {@code mesh}'s design faces (the part at High detail) in {@code layout}. */
	public static PartOutline of(BakedBoardGeometry.Mesh mesh, DesignLayout.Part layout, boolean withBolts)
	{
		int w = layout.width;
		int h = layout.height;
		float[] uv = mesh.cornerUv;
		List<Float> faceList = new ArrayList<>();
		Set<String> seenFaces = new HashSet<>();
		Map<String, float[]> edgeOf = new HashMap<>();
		Map<String, Integer> edgeCount = new HashMap<>();
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		float nearZ = Float.MAX_VALUE;
		float farZ = -Float.MAX_VALUE;
		double nearY = 0;
		double farY = 0;
		int nFaces = mesh.faceCount();
		for (int t = 0; uv != null && t < nFaces; t++)
		{
			long[] key = new long[3];
			float[] xy = new float[6];
			boolean design = true;
			for (int k = 0; k < 3 && design; k++)
			{
				float u = uv[t * 6 + k * 2];
				float v = uv[t * 6 + k * 2 + 1];
				if (Float.isNaN(u) || Float.isNaN(v))
				{
					design = false;
					break;
				}
				key[k] = Math.round(u * 10000.0) * 1_000_000L + Math.round(v * 10000.0);
				xy[k * 2] = u * w;
				xy[k * 2 + 1] = (1 - v) * h;
			}
			if (!design)
			{
				continue;
			}
			for (int k = 0; k < 3; k++)
			{
				double x = xy[k * 2];
				double y = xy[k * 2 + 1];
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				float z = mesh.vertices[mesh.faces[t * 3 + k] * 3 + 2];
				if (z < nearZ)
				{
					nearZ = z;
					nearY = y;
				}
				if (z > farZ)
				{
					farZ = z;
					farY = y;
				}
			}
			long[] sorted = key.clone();
			java.util.Arrays.sort(sorted);
			if (sorted[0] == sorted[1] || sorted[1] == sorted[2]
				|| !seenFaces.add(sorted[0] + ":" + sorted[1] + ":" + sorted[2]))
			{
				// degenerate in UV, or a face another part of the mesh already painted the same way
				continue;
			}
			for (float f : xy)
			{
				faceList.add(f);
			}
			for (int k = 0; k < 3; k++)
			{
				int k2 = (k + 1) % 3;
				long a = key[k];
				long b = key[k2];
				String ek = Math.min(a, b) + ":" + Math.max(a, b);
				edgeCount.merge(ek, 1, Integer::sum);
				edgeOf.put(ek, new float[]{xy[k * 2], xy[k * 2 + 1], xy[k2 * 2], xy[k2 * 2 + 1]});
			}
		}
		if (faceList.isEmpty())
		{
			throw new IllegalArgumentException("the part has no design faces");
		}
		float[] faces = new float[faceList.size()];
		for (int i = 0; i < faces.length; i++)
		{
			faces[i] = faceList.get(i);
		}
		List<float[]> outline = new ArrayList<>();
		for (Map.Entry<String, Integer> e : edgeCount.entrySet())
		{
			if (e.getValue() == 1)
			{
				outline.add(edgeOf.get(e.getKey()));
			}
		}
		float[] edges = new float[outline.size() * 4];
		for (int i = 0; i < outline.size(); i++)
		{
			System.arraycopy(outline.get(i), 0, edges, i * 4, 4);
		}
		List<double[]> bolts = withBolts ? bolts(mesh, w, h) : Collections.emptyList();
		return new PartOutline(w, h, faces, edges, new double[]{minX, minY, maxX, maxY}, bolts, farY < nearY);
	}

	/** Each bolt's layout position: the UV of the design face above or below it (x, z), interpolated. */
	private static List<double[]> bolts(BakedBoardGeometry.Mesh mesh, int w, int h)
	{
		List<double[]> out = new ArrayList<>();
		float[] uv = mesh.cornerUv;
		for (float[] b : BOLTS_XZ)
		{
			for (int t = 0; uv != null && t < mesh.faceCount(); t++)
			{
				if (Float.isNaN(uv[t * 6]) || Float.isNaN(uv[t * 6 + 2]) || Float.isNaN(uv[t * 6 + 4]))
				{
					continue;
				}
				double[] bary = new double[3];
				float[] v = mesh.vertices;
				int ia = mesh.faces[t * 3] * 3;
				int ib = mesh.faces[t * 3 + 1] * 3;
				int ic = mesh.faces[t * 3 + 2] * 3;
				if (!barycentric(b[0], b[1], v[ia], v[ia + 2], v[ib], v[ib + 2], v[ic], v[ic + 2], bary))
				{
					continue;
				}
				double u = bary[0] * uv[t * 6] + bary[1] * uv[t * 6 + 2] + bary[2] * uv[t * 6 + 4];
				double vv = bary[0] * uv[t * 6 + 1] + bary[1] * uv[t * 6 + 3] + bary[2] * uv[t * 6 + 5];
				out.add(new double[]{u * w, (1 - vv) * h});
				break;
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** True (and the weights in {@code out}) if (px, py) is inside triangle a, b, c. */
	static boolean barycentric(double px, double py, double ax, double ay, double bx, double by, double cx,
		double cy, double[] out)
	{
		double den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
		if (Math.abs(den) < 1e-12)
		{
			return false;
		}
		double l1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den;
		double l2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den;
		double l3 = 1 - l1 - l2;
		double eps = -1e-9;
		if (l1 < eps || l2 < eps || l3 < eps)
		{
			return false;
		}
		out[0] = l1;
		out[1] = l2;
		out[2] = l3;
		return true;
	}

	/** {minX, minY, maxX, maxY} of the design faces in layout pixels (a copy). */
	public double[] bounds()
	{
		return bounds.clone();
	}

	/** The outline's edges, 4 floats each (x1, y1, x2, y2); shared, do not modify. */
	public float[] edges()
	{
		return edges;
	}

	/** The distinct design faces, 6 floats each; shared, do not modify. */
	public float[] faces()
	{
		return faces;
	}

	/** The bolt centres {x, y} in layout pixels (empty for wheels). */
	public List<double[]> bolts()
	{
		return bolts;
	}

	/** One flag per layout pixel (row by row): true where a design face covers the pixel's centre. */
	public synchronized boolean[] mask()
	{
		if (mask == null)
		{
			boolean[] m = new boolean[width * height];
			double[] bary = new double[3];
			for (int f = 0; f < faces.length; f += 6)
			{
				double ax = faces[f];
				double ay = faces[f + 1];
				double bx = faces[f + 2];
				double by = faces[f + 3];
				double cx = faces[f + 4];
				double cy = faces[f + 5];
				int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
				int x1 = Math.min(width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
				int y0 = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
				int y1 = Math.min(height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
				for (int y = y0; y <= y1; y++)
				{
					for (int x = x0; x <= x1; x++)
					{
						if (!m[y * width + x] && barycentric(x + 0.5, y + 0.5, ax, ay, bx, by, cx, cy, bary))
						{
							m[y * width + x] = true;
						}
					}
				}
			}
			mask = m;
		}
		return mask;
	}
}
