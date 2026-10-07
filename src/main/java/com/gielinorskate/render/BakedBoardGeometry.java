package com.gielinorskate.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The baked board v3 (board_v3_high.txt and board_v3_low.txt, written by tools/blend_to_board_baked.py): up to four
 * parts (grip, deck, wheels, hardware), each an indexed mesh drawn as its own model, all in the one board model
 * space (as {@link BoardGeometry}: long axis z, y negative up, wheels on y = 0, mid-deck top at y = -16), so posing
 * every part with the same transform puts them together as one board. Each part's image texture was baked into a
 * colour at every triangle corner. Format, one record per line ('#' comments and blank lines skipped):
 * <pre>
 * part NAME                        starts a part: grip, deck, wheels or hardware, each at most once
 * v X Y Z                          a vertex of the current part
 * t A B C RRGGBB RRGGBB RRGGBB [UA VA UB VB UC VC]
 *                                  a triangle of the current part: 0-based indices into the part's own vertices,
 *                                  the sRGB colour at each corner and, on a design face, each corner's UV in the
 *                                  part's design image (v up)
 * </pre>
 * The colours are the default designs; every design of a part is a colour file over the same triangles
 * ({@link DesignColours}), sampled at those UVs.
 * Within a part, vertices must be numbered in first-use order over its triangles (the merged runtime model numbers
 * them so). High detail fills each part's budget (about 6000 triangles); low detail (about 1500 for grip and deck)
 * is for the "Normal" board detail setting and for party ghosts.
 */
public final class BakedBoardGeometry
{
	public static final int GRIP = 0;
	public static final int DECK = 1;
	public static final int WHEELS = 2;
	public static final int HARDWARE = 3;
	static final String[] PART_NAMES = {"grip", "deck", "wheels", "hardware"};

	private static final String HIGH_RESOURCE = "/com/gielinorskate/board_v3_high.txt";
	private static final String LOW_RESOURCE = "/com/gielinorskate/board_v3_low.txt";

	private BakedBoardGeometry()
	{
	}

	/** One part of the board. */
	public static final class Mesh
	{
		/** {@link #GRIP} ... {@link #HARDWARE}. */
		public final int part;
		/** 3 floats (x, y, z) per vertex. */
		public final float[] vertices;
		/** 3 vertex indices per face. */
		public final int[] faces;
		/** 0xRRGGBB per face corner, 3 per face in the order of {@link #faces}. */
		public final int[] cornerRgb;
		/**
		 * Each corner's design-image UV (u, v; v up), 6 per face; NaN on faces without a design (wheel hubs,
		 * hardware). Null when the part has none.
		 */
		public final float[] cornerUv;

		Mesh(int part, float[] vertices, int[] faces, int[] cornerRgb)
		{
			this(part, vertices, faces, cornerRgb, null);
		}

		Mesh(int part, float[] vertices, int[] faces, int[] cornerRgb, float[] cornerUv)
		{
			this.part = part;
			this.vertices = vertices;
			this.faces = faces;
			this.cornerRgb = cornerRgb;
			this.cornerUv = cornerUv;
		}

		/** A part made in code (tests, tools): the arrays are used as they are. */
		public static Mesh of(int part, float[] vertices, int[] faces, int[] cornerRgb, float[] cornerUv)
		{
			return new Mesh(part, vertices, faces, cornerRgb, cornerUv);
		}

		public int vertexCount()
		{
			return vertices.length / 3;
		}

		public int faceCount()
		{
			return faces.length / 3;
		}
	}

	private static final Object LOCK = new Object();
	private static Mesh[] cachedHigh;
	private static Mesh[] cachedLow;
	private static boolean missingHigh;
	private static boolean missingLow;

	/**
	 * The bundled board's parts in high or low detail, parsed once; null if that variant isn't bundled. Shared: never
	 * modify them.
	 */
	public static Mesh[] sharedBoard(boolean high)
	{
		synchronized (LOCK)
		{
			if (high)
			{
				if (cachedHigh == null && !missingHigh)
				{
					cachedHigh = load(HIGH_RESOURCE);
					missingHigh = cachedHigh == null;
				}
				return cachedHigh;
			}
			if (cachedLow == null && !missingLow)
			{
				cachedLow = load(LOW_RESOURCE);
				missingLow = cachedLow == null;
			}
			return cachedLow;
		}
	}

	static Mesh[] load(String resource)
	{
		InputStream in = BakedBoardGeometry.class.getResourceAsStream(resource);
		if (in == null)
		{
			return null;
		}
		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return parse(r);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	/** The parts, in file order. Throws IllegalArgumentException on a malformed board. */
	public static Mesh[] parse(Reader reader) throws IOException
	{
		List<Mesh> parts = new ArrayList<>();
		boolean[] seen = new boolean[PART_NAMES.length];
		PartBuilder cur = null;
		BufferedReader br = new BufferedReader(reader);
		String line;
		int lineNo = 0;
		while ((line = br.readLine()) != null)
		{
			lineNo++;
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#')
			{
				continue;
			}
			String[] p = line.split("\\s+");
			if (p[0].equals("part"))
			{
				if (p.length != 2)
				{
					throw error(lineNo, "a part needs one name");
				}
				int part = partOf(p[1], lineNo);
				if (seen[part])
				{
					throw error(lineNo, "part " + p[1] + " appears twice");
				}
				seen[part] = true;
				if (cur != null)
				{
					parts.add(cur.build());
				}
				cur = new PartBuilder(part);
			}
			else if (p[0].equals("v") || p[0].equals("t"))
			{
				if (cur == null)
				{
					throw error(lineNo, "a vertex or triangle before the first part");
				}
				if (p[0].equals("v"))
				{
					cur.vertex(p, lineNo);
				}
				else
				{
					cur.triangle(p, lineNo);
				}
			}
			else
			{
				throw error(lineNo, "unknown record " + p[0]);
			}
		}
		if (cur != null)
		{
			parts.add(cur.build());
		}
		if (parts.isEmpty())
		{
			throw new IllegalArgumentException("board v3: no parts");
		}
		return parts.toArray(new Mesh[0]);
	}

	private static final class PartBuilder
	{
		private final int part;
		private float[] verts = new float[3 * 1024];
		private int vCount;
		private int[] faces = new int[3 * 1024];
		private int[] rgb = new int[3 * 1024];
		private float[] uv = new float[6 * 1024];
		private boolean anyUv;
		private int fCount;
		private int nextUnused;

		PartBuilder(int part)
		{
			this.part = part;
		}

		void vertex(String[] p, int lineNo)
		{
			if (p.length != 4)
			{
				throw error(lineNo, "a vertex needs 3 coordinates");
			}
			if (vCount * 3 + 3 > verts.length)
			{
				verts = Arrays.copyOf(verts, verts.length * 2);
			}
			for (int i = 0; i < 3; i++)
			{
				float c;
				try
				{
					c = Float.parseFloat(p[1 + i]);
				}
				catch (NumberFormatException e)
				{
					throw error(lineNo, "bad coordinate " + p[1 + i]);
				}
				if (!Float.isFinite(c))
				{
					throw error(lineNo, "coordinate is not finite");
				}
				verts[vCount * 3 + i] = c;
			}
			vCount++;
		}

		void triangle(String[] p, int lineNo)
		{
			if (p.length != 7 && p.length != 13)
			{
				throw error(lineNo, "a triangle needs 3 indices and 3 colours (and maybe 3 UVs)");
			}
			if (fCount * 3 + 3 > faces.length)
			{
				faces = Arrays.copyOf(faces, faces.length * 2);
				rgb = Arrays.copyOf(rgb, rgb.length * 2);
				uv = Arrays.copyOf(uv, uv.length * 2);
			}
			for (int i = 0; i < 6; i++)
			{
				float c = Float.NaN;
				if (p.length == 13)
				{
					try
					{
						c = Float.parseFloat(p[7 + i]);
					}
					catch (NumberFormatException e)
					{
						throw error(lineNo, "bad UV " + p[7 + i]);
					}
					if (!Float.isFinite(c))
					{
						throw error(lineNo, "UV is not finite");
					}
					anyUv = true;
				}
				uv[fCount * 6 + i] = c;
			}
			for (int i = 0; i < 3; i++)
			{
				int v;
				try
				{
					v = Integer.parseInt(p[1 + i]);
				}
				catch (NumberFormatException e)
				{
					throw error(lineNo, "bad vertex index " + p[1 + i]);
				}
				if (v < 0 || v >= vCount)
				{
					throw error(lineNo, "vertex " + v + " is not defined above in this part");
				}
				if (v > nextUnused)
				{
					throw error(lineNo, "vertex " + v + " used before vertex " + nextUnused
						+ " (vertices must be numbered in first-use order)");
				}
				if (v == nextUnused)
				{
					nextUnused++;
				}
				faces[fCount * 3 + i] = v;
				rgb[fCount * 3 + i] = parseRgb(p[4 + i], lineNo);
			}
			int a = faces[fCount * 3];
			int b = faces[fCount * 3 + 1];
			int c = faces[fCount * 3 + 2];
			if (a == b || b == c || a == c)
			{
				throw error(lineNo, "a triangle uses a vertex twice");
			}
			fCount++;
		}

		Mesh build()
		{
			String name = PART_NAMES[part];
			if (fCount == 0)
			{
				throw new IllegalArgumentException("board v3: part " + name + " has no triangles");
			}
			if (nextUnused != vCount)
			{
				throw new IllegalArgumentException("board v3: part " + name + " never uses vertex " + nextUnused);
			}
			return new Mesh(part, Arrays.copyOf(verts, vCount * 3), Arrays.copyOf(faces, fCount * 3),
				Arrays.copyOf(rgb, fCount * 3), anyUv ? Arrays.copyOf(uv, fCount * 6) : null);
		}
	}

	private static int parseRgb(String s, int lineNo)
	{
		if (s.length() != 6)
		{
			throw error(lineNo, "colour " + s + " is not RRGGBB");
		}
		try
		{
			return Integer.parseInt(s, 16);
		}
		catch (NumberFormatException e)
		{
			throw error(lineNo, "colour " + s + " is not RRGGBB");
		}
	}

	private static int partOf(String s, int lineNo)
	{
		for (int i = 0; i < PART_NAMES.length; i++)
		{
			if (PART_NAMES[i].equals(s))
			{
				return i;
			}
		}
		throw error(lineNo, "unknown part " + s);
	}

	private static IllegalArgumentException error(int lineNo, String msg)
	{
		return new IllegalArgumentException("board v3 line " + lineNo + ": " + msg);
	}

	/** Every corner's colour as packed HSL at {@code brightness}, in corner order. */
	public static short[] cornerHsl(Mesh mesh, double brightness)
	{
		return cornerHsl(mesh.cornerRgb, brightness);
	}

	/** Every corner's colour (0xRRGGBB) as packed HSL at {@code brightness}, in corner order. */
	public static short[] cornerHsl(int[] cornerRgb, double brightness)
	{
		short[] out = new short[cornerRgb.length];
		// boards repeat colours a lot: convert each distinct colour once
		Map<Integer, Short> seen = new HashMap<>();
		for (int i = 0; i < out.length; i++)
		{
			int rgb = cornerRgb[i];
			Short hsl = seen.get(rgb);
			if (hsl == null)
			{
				hsl = (short) OsrsColor.rgbToHsl(rgb, brightness);
				seen.put(rgb, hsl);
			}
			out[i] = hsl;
		}
		return out;
	}
}
