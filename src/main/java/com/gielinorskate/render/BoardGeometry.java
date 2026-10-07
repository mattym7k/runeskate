package com.gielinorskate.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Pure skateboard mesh. Model space: long axis = z, y negative = up, wheels rest on y = 0. */
public final class BoardGeometry
{
	public static final float BOARD_TOP = 16f;
	public static final int GRIP = 0;
	public static final int DECK = 1;
	public static final int WHEELS = 2;
	public static final int GRAPHIC = 3;
	public static final int METAL = 4;

	private static final String BOARD_RESOURCE = "/com/gielinorskate/board.txt";

	// Deck outline shape constants (see task-F2 brief).
	private static final float DECK_HALF_WIDTH = 13f;
	private static final float DECK_STRAIGHT_HALF_LEN = 37f;
	private static final float DECK_KICK_START = 30f;
	private static final float DECK_HALF_LEN = 50f;
	private static final float DECK_TOP_FLAT = -16f;
	private static final float DECK_TOP_TIP = -23f;
	private static final float DECK_THICKNESS = 3f;
	private static final int DECK_ARC_SEGMENTS = 8;

	private static final float TRUCK_Z = 30f;
	private static final int WHEEL_SEGMENTS = 8;
	private static final float WHEEL_RADIUS = 4.5f;
	private static final float WHEEL_CENTER_Y = -4.5f;

	private BoardGeometry()
	{
	}

	public static final class Mesh
	{
		/** 9 floats per face: three (x, y, z) vertices. */
		public final float[] tris;
		/** Palette index per face. */
		public final int[] colorIndex;

		Mesh(float[] tris, int[] colorIndex)
		{
			this.tris = tris;
			this.colorIndex = colorIndex;
		}

		public int faceCount()
		{
			return colorIndex.length;
		}
	}

	/** The parsed default board, loaded once; never handed out directly (see {@link #sharedDefaultBoard()}). */
	private static volatile Mesh cachedDefault;

	/**
	 * The bundled imported board (see tools/obj_to_board.py), or the built-in board if it's missing. The
	 * resource is parsed once; each call returns a fresh copy the caller may modify.
	 */
	public static Mesh defaultBoard()
	{
		Mesh m = sharedDefaultBoard();
		return new Mesh(m.tris.clone(), m.colorIndex.clone());
	}

	/** The cached default board. Shared by every caller: read only, never modify its arrays. */
	static Mesh sharedDefaultBoard()
	{
		Mesh m = cachedDefault;
		if (m == null)
		{
			synchronized (BoardGeometry.class)
			{
				m = cachedDefault;
				if (m == null)
				{
					m = loadDefaultBoard();
					cachedDefault = m;
				}
			}
		}
		return m;
	}

	/** Reads and parses the bundled board resource (uncached). */
	static Mesh loadDefaultBoard()
	{
		InputStream in = BoardGeometry.class.getResourceAsStream(BOARD_RESOURCE);
		if (in == null)
		{
			return standardBoard();
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

	/** Parses one triangle per line: ax ay az bx by bz cx cy cz colorIndex. Blank lines are skipped. */
	public static Mesh parse(Reader reader) throws IOException
	{
		List<float[]> tris = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		BufferedReader br = new BufferedReader(reader);
		String line;
		int lineNo = 0;
		while ((line = br.readLine()) != null)
		{
			lineNo++;
			line = line.trim();
			if (line.isEmpty())
			{
				continue;
			}
			String[] p = line.split("\\s+");
			if (p.length != 10)
			{
				throw new IllegalArgumentException("board line " + lineNo + ": expected 10 values, got " + p.length);
			}
			float[] t = new float[9];
			for (int i = 0; i < 9; i++)
			{
				t[i] = Float.parseFloat(p[i]);
			}
			int color = Integer.parseInt(p[9]);
			if (color < GRIP || color > METAL)
			{
				throw new IllegalArgumentException("board line " + lineNo + ": unknown colour " + color);
			}
			tris.add(t);
			colors.add(color);
		}
		return toMesh(tris, colors);
	}

	/** Detailed skateboard: rounded deck with kicktails, metal trucks and 4 cylindrical wheels. */
	public static Mesh standardBoard()
	{
		List<float[]> tris = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		addDeck(tris, colors);
		addTruck(tris, colors, TRUCK_Z);
		addTruck(tris, colors, -TRUCK_Z);
		addWheel(tris, colors, 14.5f, 19.5f, TRUCK_Z);
		addWheel(tris, colors, -19.5f, -14.5f, TRUCK_Z);
		addWheel(tris, colors, 14.5f, 19.5f, -TRUCK_Z);
		addWheel(tris, colors, -19.5f, -14.5f, -TRUCK_Z);
		return toMesh(tris, colors);
	}

	private static Mesh toMesh(List<float[]> tris, List<Integer> colors)
	{
		int faces = tris.size();
		float[] t = new float[faces * 9];
		int[] c = new int[faces];
		for (int i = 0; i < faces; i++)
		{
			System.arraycopy(tris.get(i), 0, t, i * 9, 9);
			c[i] = colors.get(i);
		}
		return new Mesh(t, c);
	}

	/** Rotates by roll (around z) then pitch (around x). Returns a new array. */
	public static float[] rotate(float[] tris, float roll, float pitch)
	{
		return rotateInto(tris, roll, pitch, new float[tris.length]);
	}

	/** As {@link #rotate}, writing into {@code out} (at least {@code tris.length} long; may not be {@code tris}). Returns {@code out}. */
	static float[] rotateInto(float[] tris, float roll, float pitch, float[] out)
	{
		float cr = (float) Math.cos(roll);
		float sr = (float) Math.sin(roll);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);
		for (int i = 0; i < tris.length; i += 3)
		{
			float x = tris[i];
			float y = tris[i + 1];
			float z = tris[i + 2];
			float x1 = x * cr - y * sr;
			float y1 = x * sr + y * cr;
			float y2 = y1 * cp - z * sp;
			float z2 = y1 * sp + z * cp;
			out[i] = x1;
			out[i + 1] = y2;
			out[i + 2] = z2;
		}
		return out;
	}

	// ---- deck ----

	private static void addDeck(List<float[]> tris, List<Integer> colors)
	{
		List<float[]> outline = buildDeckOutline();
		int n = outline.size();
		float[][] top = new float[n][];
		float[][] bottom = new float[n][];
		for (int i = 0; i < n; i++)
		{
			float x = outline.get(i)[0];
			float z = outline.get(i)[1];
			float topY = deckTopY(z);
			top[i] = new float[]{x, topY, z};
			bottom[i] = new float[]{x, topY + DECK_THICKNESS, z};
		}
		float[] centerTop = {0f, DECK_TOP_FLAT, 0f};
		float[] centerBottom = {0f, DECK_TOP_FLAT + DECK_THICKNESS, 0f};

		for (int i = 0; i < n; i++)
		{
			int j = (i + 1) % n;
			addTri(tris, colors, centerTop, top[i], top[j], GRIP);
			addTri(tris, colors, centerBottom, bottom[j], bottom[i], GRAPHIC);
			addQuad(tris, colors, top[i], bottom[i], bottom[j], top[j], DECK);
		}
	}

	private static float deckTopY(float z)
	{
		float absZ = Math.abs(z);
		if (absZ <= DECK_KICK_START)
		{
			return DECK_TOP_FLAT;
		}
		float t = (absZ - DECK_KICK_START) / (DECK_HALF_LEN - DECK_KICK_START);
		return DECK_TOP_FLAT + t * (DECK_TOP_TIP - DECK_TOP_FLAT);
	}

	/** Outline of the deck in the x-z plane, ordered counter-clockwise (viewed from above). */
	private static List<float[]> buildDeckOutline()
	{
		List<float[]> pts = new ArrayList<>();
		float hw = DECK_HALF_WIDTH;
		float sl = DECK_STRAIGHT_HALF_LEN;

		// right straight side, increasing z
		pts.add(new float[]{hw, -sl});
		pts.add(new float[]{hw, -DECK_KICK_START});
		pts.add(new float[]{hw, DECK_KICK_START});
		pts.add(new float[]{hw, sl});

		// nose-end semicircle, centre (0, sl), radius hw, angle 0 -> PI
		for (int k = 1; k < DECK_ARC_SEGMENTS; k++)
		{
			double angle = Math.PI * k / DECK_ARC_SEGMENTS;
			pts.add(new float[]{(float) (hw * Math.cos(angle)), (float) (sl + hw * Math.sin(angle))});
		}

		// left straight side, decreasing z
		pts.add(new float[]{-hw, sl});
		pts.add(new float[]{-hw, DECK_KICK_START});
		pts.add(new float[]{-hw, -DECK_KICK_START});
		pts.add(new float[]{-hw, -sl});

		// tail-end semicircle, centre (0, -sl), radius hw, angle PI -> 2*PI
		for (int k = 1; k < DECK_ARC_SEGMENTS; k++)
		{
			double angle = Math.PI + Math.PI * k / DECK_ARC_SEGMENTS;
			pts.add(new float[]{(float) (hw * Math.cos(angle)), (float) (-sl + hw * Math.sin(angle))});
		}

		return pts;
	}

	// ---- trucks ----

	private static void addTruck(List<float[]> tris, List<Integer> colors, float z)
	{
		addBox(tris, colors, -6f, 6f, -13f, -11f, z - 4f, z + 4f, METAL); // baseplate
		addBox(tris, colors, -2f, 2f, -11f, -9f, z - 2f, z + 2f, METAL);  // kingpin block
		addBox(tris, colors, -15f, 15f, -9f, -6f, z - 2f, z + 2f, METAL); // hanger/axle
	}

	// ---- wheels ----

	private static void addWheel(List<float[]> tris, List<Integer> colors, float x0, float x1, float z)
	{
		addCylinderX(tris, colors, x0, x1, WHEEL_CENTER_Y, z, WHEEL_RADIUS, WHEEL_SEGMENTS, WHEELS);
	}

	// ---- generic helpers ----

	/** Writes a 12-triangle axis-aligned box with outward winding. */
	private static void addBox(List<float[]> tris, List<Integer> colors,
		float x0, float x1, float yTop, float yBottom, float z0, float z1, int color)
	{
		float[][] v = {
			{x0, yTop, z0}, {x1, yTop, z0}, {x1, yTop, z1}, {x0, yTop, z1},
			{x0, yBottom, z0}, {x1, yBottom, z0}, {x1, yBottom, z1}, {x0, yBottom, z1},
		};
		int[][] quads = {
			{0, 1, 2, 3}, // top
			{7, 6, 5, 4}, // bottom
			{0, 4, 5, 1}, // back (z0)
			{2, 6, 7, 3}, // front (z1)
			{1, 5, 6, 2}, // right (x1)
			{3, 7, 4, 0}, // left (x0)
		};
		for (int[] k : quads)
		{
			addQuad(tris, colors, v[k[0]], v[k[1]], v[k[2]], v[k[3]], color);
		}
	}

	/** Writes a cylinder with axis along x, from x0 to x1, outward winding, 4*segments triangles. */
	private static void addCylinderX(List<float[]> tris, List<Integer> colors,
		float x0, float x1, float centerY, float centerZ, float radius, int segments, int color)
	{
		float[] y = new float[segments];
		float[] z = new float[segments];
		for (int k = 0; k < segments; k++)
		{
			double angle = 2 * Math.PI * k / segments;
			y[k] = centerY + radius * (float) Math.cos(angle);
			z[k] = centerZ + radius * (float) Math.sin(angle);
		}

		float[] apex0 = {x0, centerY, centerZ};
		float[] apex1 = {x1, centerY, centerZ};

		for (int k = 0; k < segments; k++)
		{
			int kn = (k + 1) % segments;
			float[] a = {x0, y[k], z[k]};
			float[] b = {x0, y[kn], z[kn]};
			float[] c = {x1, y[kn], z[kn]};
			float[] d = {x1, y[k], z[k]};
			addQuad(tris, colors, a, b, c, d, color);
			addTri(tris, colors, apex0, b, a, color);
			addTri(tris, colors, apex1, d, c, color);
		}
	}

	private static void addQuad(List<float[]> tris, List<Integer> colors,
		float[] a, float[] b, float[] c, float[] d, int color)
	{
		addTri(tris, colors, a, b, c, color);
		addTri(tris, colors, a, c, d, color);
	}

	private static void addTri(List<float[]> tris, List<Integer> colors, float[] a, float[] b, float[] c, int color)
	{
		float[] f = new float[9];
		System.arraycopy(a, 0, f, 0, 3);
		System.arraycopy(b, 0, f, 3, 3);
		System.arraycopy(c, 0, f, 6, 3);
		tris.add(f);
		colors.add(color);
	}
}
