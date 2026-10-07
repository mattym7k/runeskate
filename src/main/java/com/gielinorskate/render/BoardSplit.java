package com.gielinorskate.render;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A board snapped in two across the deck at its middle: the plane z = 0 of board model space (the long axis is z,
 * + toward the nose). Every triangle of every part goes to exactly one half by its centroid (z >= 0: the nose half),
 * so nothing is cut and each half keeps its triangles' corner colours (designs, custom designs) as they are: a half
 * is the whole part's mesh with the other half's faces hidden. Each half has one centre shared by all its parts (so
 * the grip, deck, wheels and hardware of a half stay together as it spins), its parts' vertices are given relative to
 * that centre, and a vertex no face of the half uses is parked at the centre. Split once per mesh and kept
 * ({@link #of}). Pure.
 */
public final class BoardSplit
{
	/** The nose half (+z) and the tail half. */
	public final Half nose;
	public final Half tail;

	private BoardSplit(Half nose, Half tail)
	{
		this.nose = nose;
		this.tail = tail;
	}

	public Half half(boolean nose)
	{
		return nose ? this.nose : tail;
	}

	/** One half of the board, all its parts. */
	public static final class Half
	{
		public final boolean nose;
		/** Per part, per face: the face belongs to this half. */
		public final boolean[][] faceIn;
		/** Per part, the mesh's vertices (x, y, z) relative to {@link #cx} ...; unused ones are 0. */
		public final float[][] vertices;
		/** The half's centre in board model space (the middle of its bounds; y down). */
		public final float cx;
		public final float cy;
		public final float cz;
		/** The half's bounds in board model space, over the vertices of its faces. */
		public final float minX;
		public final float minY;
		public final float minZ;
		public final float maxX;
		public final float maxY;
		public final float maxZ;
		/** Faces in this half, all parts. */
		public final int faceCount;

		Half(boolean nose, boolean[][] faceIn, float[][] vertices, float[] bounds, int faceCount)
		{
			this.nose = nose;
			this.faceIn = faceIn;
			this.vertices = vertices;
			minX = bounds[0];
			minY = bounds[1];
			minZ = bounds[2];
			maxX = bounds[3];
			maxY = bounds[4];
			maxZ = bounds[5];
			cx = (minX + maxX) / 2f;
			cy = (minY + maxY) / 2f;
			cz = (minZ + maxZ) / 2f;
			this.faceCount = faceCount;
		}

		/** Units from the centre down to the half's lowest point (the wheels' bottoms): it rests on that. */
		public float bottom()
		{
			return maxY - cy;
		}
	}

	/**
	 * Splits a board's parts. {@code vertices[p]}: part p's (x, y, z) per vertex; {@code faces[p]}: three vertex
	 * indices per face, or null for unwelded triangles (face i uses vertices 3i, 3i + 1, 3i + 2, the classic board).
	 */
	public static BoardSplit split(float[][] vertices, int[][] faces)
	{
		int parts = vertices.length;
		boolean[][] noseIn = new boolean[parts][];
		boolean[][] tailIn = new boolean[parts][];
		float[] noseBounds = emptyBounds();
		float[] tailBounds = emptyBounds();
		int noseFaces = 0;
		int tailFaces = 0;
		for (int p = 0; p < parts; p++)
		{
			float[] v = vertices[p];
			int[] f = faces == null ? null : faces[p];
			int n = faceCount(v, f);
			noseIn[p] = new boolean[n];
			tailIn[p] = new boolean[n];
			for (int i = 0; i < n; i++)
			{
				int a = corner(f, i, 0);
				int b = corner(f, i, 1);
				int c = corner(f, i, 2);
				// the centroid's side of the cut: z = 0 itself goes to the nose
				boolean nose = v[a * 3 + 2] + v[b * 3 + 2] + v[c * 3 + 2] >= 0f;
				(nose ? noseIn[p] : tailIn[p])[i] = true;
				float[] bounds = nose ? noseBounds : tailBounds;
				grow(bounds, v, a);
				grow(bounds, v, b);
				grow(bounds, v, c);
				if (nose)
				{
					noseFaces++;
				}
				else
				{
					tailFaces++;
				}
			}
		}
		Half nose = half(true, vertices, faces, noseIn, settle(noseBounds), noseFaces);
		Half tail = half(false, vertices, faces, tailIn, settle(tailBounds), tailFaces);
		return new BoardSplit(nose, tail);
	}

	private static Half half(boolean nose, float[][] vertices, int[][] faces, boolean[][] in, float[] bounds,
		int faceCount)
	{
		float cx = (bounds[0] + bounds[3]) / 2f;
		float cy = (bounds[1] + bounds[4]) / 2f;
		float cz = (bounds[2] + bounds[5]) / 2f;
		float[][] out = new float[vertices.length][];
		for (int p = 0; p < vertices.length; p++)
		{
			float[] v = vertices[p];
			int[] f = faces == null ? null : faces[p];
			boolean[] used = new boolean[v.length / 3];
			for (int i = 0; i < in[p].length; i++)
			{
				if (in[p][i])
				{
					used[corner(f, i, 0)] = true;
					used[corner(f, i, 1)] = true;
					used[corner(f, i, 2)] = true;
				}
			}
			float[] o = new float[v.length];
			for (int k = 0; k < used.length; k++)
			{
				if (used[k])
				{
					o[k * 3] = v[k * 3] - cx;
					o[k * 3 + 1] = v[k * 3 + 1] - cy;
					o[k * 3 + 2] = v[k * 3 + 2] - cz;
				}
			}
			out[p] = o;
		}
		return new Half(nose, in, out, bounds, faceCount);
	}

	private static int faceCount(float[] v, int[] f)
	{
		return f == null ? v.length / 9 : f.length / 3;
	}

	private static int corner(int[] f, int face, int k)
	{
		return f == null ? face * 3 + k : f[face * 3 + k];
	}

	private static float[] emptyBounds()
	{
		return new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
			Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
	}

	private static void grow(float[] b, float[] v, int i)
	{
		for (int k = 0; k < 3; k++)
		{
			float c = v[i * 3 + k];
			b[k] = Math.min(b[k], c);
			b[k + 3] = Math.max(b[k + 3], c);
		}
	}

	/** An empty half (no faces) is a point at the origin. */
	private static float[] settle(float[] b)
	{
		if (b[0] > b[3])
		{
			return new float[6];
		}
		return b;
	}

	private static final Map<Object, BoardSplit> CACHE = new IdentityHashMap<>();

	/** The baked board's parts split, once per (shared) mesh array. */
	public static BoardSplit of(BakedBoardGeometry.Mesh[] meshes)
	{
		synchronized (CACHE)
		{
			BoardSplit s = CACHE.get(meshes);
			if (s == null)
			{
				float[][] v = new float[meshes.length][];
				int[][] f = new int[meshes.length][];
				for (int i = 0; i < meshes.length; i++)
				{
					v[i] = meshes[i].vertices;
					f[i] = meshes[i].faces;
				}
				s = split(v, f);
				CACHE.put(meshes, s);
			}
			return s;
		}
	}

	/** The classic board split, once per (shared) mesh. */
	public static BoardSplit of(BoardGeometry.Mesh mesh)
	{
		synchronized (CACHE)
		{
			BoardSplit s = CACHE.get(mesh);
			if (s == null)
			{
				s = split(new float[][]{mesh.tris}, null);
				CACHE.put(mesh, s);
			}
			return s;
		}
	}

	/**
	 * A half's lit colours: the faces of the half keep the colours their corners were lit to (the board's own, or
	 * for the baked board its designs' corner colours, {@link BakedBoardModel#shade}); every other face is hidden
	 * (colour 3 = -2, which the client's renderers skip).
	 *
	 * @param cornerHsl the baked board's corner colours, or null (the classic board: the client lit them already)
	 */
	static void shadeHalf(int[] c1, int[] c2, int[] c3, short[] cornerHsl, boolean[] in)
	{
		if (cornerHsl != null)
		{
			BakedBoardModel.shade(c1, c2, c3, cornerHsl);
		}
		int n = Math.min(in.length, c3.length);
		for (int i = 0; i < n; i++)
		{
			if (!in[i])
			{
				c3[i] = -2;
			}
		}
		// faces past the mesh's own (the classic template's leftovers) are never drawn
		for (int i = n; i < c3.length; i++)
		{
			c3[i] = -2;
		}
	}
}
