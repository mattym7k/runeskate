package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class BakedBoardTest
{
	private static final String TWO_PARTS = "# a quad and a triangle\n"
		+ "part grip\n"
		+ "v 0 0 0\nv 1 0 0\nv 1 0 1\n\nv 0 0 1\n"
		+ "t 0 1 2 FF0000 00FF00 0000ff\n"
		+ "t 0 2 3 FF0000 0000FF 808080\n"
		+ "part hardware\n"
		+ "v 5 5 5\nv 6 5 5\nv 5 6 5\n"
		+ "t 0 1 2 112233 445566 778899\n";

	private static BakedBoardGeometry.Mesh[] parse(String s) throws IOException
	{
		return BakedBoardGeometry.parse(new StringReader(s));
	}

	private static void assertRejected(String s, String why)
	{
		try
		{
			parse(s);
			fail("accepted: " + why);
		}
		catch (IllegalArgumentException | IOException expected)
		{
			// rejected
		}
	}

	// ---- parser ----

	@Test
	public void parsesPartsWithTheirOwnVerticesAndCornerColours() throws IOException
	{
		BakedBoardGeometry.Mesh[] parts = parse(TWO_PARTS);
		assertEquals(2, parts.length);
		BakedBoardGeometry.Mesh grip = parts[0];
		assertEquals(BakedBoardGeometry.GRIP, grip.part);
		assertEquals(4, grip.vertexCount());
		assertEquals(2, grip.faceCount());
		assertArrayEquals(new float[]{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, grip.vertices, 0f);
		assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3}, grip.faces);
		assertArrayEquals(new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0xFF0000, 0x0000FF, 0x808080}, grip.cornerRgb);
		BakedBoardGeometry.Mesh hw = parts[1];
		assertEquals(BakedBoardGeometry.HARDWARE, hw.part);
		assertEquals(3, hw.vertexCount());
		// indices are the part's own
		assertArrayEquals(new int[]{0, 1, 2}, hw.faces);
		assertArrayEquals(new float[]{5, 5, 5, 6, 5, 5, 5, 6, 5}, hw.vertices, 0f);
	}

	@Test
	public void rejectsMalformedBoards()
	{
		String tri = "v 0 0 0\nv 1 0 0\nv 0 1 0\n";
		String t = "t 0 1 2 000000 000000 000000\n";
		assertRejected(tri + t, "no part");
		assertRejected("part grip\n" + tri + "t 0 1 3 000000 000000 000000\n", "undefined vertex");
		assertRejected("part grip\n" + tri + t + "part deck\n" + t, "vertex of another part");
		assertRejected("part grip\n" + tri + "t 0 2 1 000000 000000 000000\n", "not first-use order");
		assertRejected("part grip\n" + tri + "v 1 1 0\n" + t, "unused vertex");
		assertRejected("part grip\n" + tri + "t 0 1 2 00000 000000 000000\n", "short colour");
		assertRejected("part grip\n" + tri + "t 0 1 2 GG0000 000000 000000\n", "bad hex");
		assertRejected("part tail\n" + tri + t, "unknown part");
		assertRejected("part grip\n" + tri + t + "part grip\n" + tri + t, "part twice");
		assertRejected("part grip\npart deck\n" + tri + t, "empty part");
		assertRejected("part grip\nv 0 0 0\nv 1 0 0\nt 0 1 1 000000 000000 000000\n", "repeated vertex");
		assertRejected("part grip\n" + tri + "t 0 1 2 000000 000000\n", "missing colour");
		assertRejected("part grip\n" + tri + "t 0 1 2 000000 000000 000000 grip\n", "v2 group tag");
		assertRejected("part grip\nv 0 0\n", "short vertex");
		assertRejected("part grip\nv 0 0 NaN\nv 1 0 0\nv 0 1 0\n" + t, "NaN");
		assertRejected("part grip\nf 0 1 2\n", "unknown record");
		assertRejected("# nothing\n", "no parts");
	}

	// ---- the bundled board ----

	@Test
	public void everyBundledPartFitsItsBudget()
	{
		for (boolean high : new boolean[]{true, false})
		{
			BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(high);
			assertNotNull("board_v3 " + (high ? "high" : "low") + " is bundled", parts);
			assertTrue(BakedBoardModel.fitsRenderers(parts));
			for (BakedBoardGeometry.Mesh m : parts)
			{
				String what = (high ? "high " : "low ") + BakedBoardGeometry.PART_NAMES[m.part];
				assertTrue(what + " vertices " + m.vertexCount(), m.vertexCount() <= 6000);
				assertTrue(what + " faces " + m.faceCount(), m.faceCount() <= 6000);
				if (!high && (m.part == BakedBoardGeometry.GRIP || m.part == BakedBoardGeometry.DECK))
				{
					assertTrue(what + " faces " + m.faceCount(), m.faceCount() <= 1600);
				}
			}
		}
	}

	@Test
	public void bothBundledVariantsHaveEveryPart()
	{
		for (boolean high : new boolean[]{true, false})
		{
			boolean[] seen = new boolean[4];
			for (BakedBoardGeometry.Mesh m : BakedBoardGeometry.sharedBoard(high))
			{
				seen[m.part] = true;
			}
			for (int g = 0; g < 4; g++)
			{
				assertTrue((high ? "high " : "low ") + BakedBoardGeometry.PART_NAMES[g], seen[g]);
			}
		}
	}

	@Test
	public void theBundledBoardMatchesTheClassicBoardsSizeAndPlacement()
	{
		for (boolean high : new boolean[]{true, false})
		{
			BakedBoardGeometry.Mesh[] v3 = BakedBoardGeometry.sharedBoard(high);
			BoardGeometry.Mesh classic = BoardGeometry.sharedDefaultBoard();

			// length (z): the whole board, every part together
			float[] cz = extent(classic.tris, 2, null, -1);
			float[] vz = extentOfParts(v3, 2, -1);
			assertEquals(cz[1] - cz[0], vz[1] - vz[0], 0.01f * (cz[1] - cz[0]));
			assertEquals(0f, (vz[0] + vz[1]) / 2, 0.1f);

			// width (x) of the grip
			float[] cx = extent(classic.tris, 0, classic.colorIndex, BoardGeometry.GRIP);
			float[] vx = extentOfParts(v3, 0, BakedBoardGeometry.GRIP);
			assertEquals(cx[1] - cx[0], vx[1] - vx[0], 0.02f * (cx[1] - cx[0]));
			assertEquals(0f, (vx[0] + vx[1]) / 2, 0.2f);

			// the wheels rest on y = 0
			float[] vy = extentOfParts(v3, 1, -1);
			assertEquals(0f, vy[1], 0.5f);
			assertTrue(vy[1] <= 0.01f);
			float[] wy = extentOfParts(v3, 1, BakedBoardGeometry.WHEELS);
			assertEquals(0f, wy[1], 0.5f);

			// the middle of the deck top is where the feet stand: y = -BOARD_TOP
			float top = Float.MAX_VALUE;
			float halfLength = (vz[1] - vz[0]) / 2;
			BakedBoardGeometry.Mesh grip = part(v3, BakedBoardGeometry.GRIP);
			for (int v = 0; v < grip.vertexCount(); v++)
			{
				if (Math.abs(grip.vertices[v * 3 + 2]) < 0.25f * halfLength)
				{
					top = Math.min(top, grip.vertices[v * 3 + 1]);
				}
			}
			assertEquals(-BoardGeometry.BOARD_TOP, top, 0.1f);
		}
	}

	@Test
	public void highAndLowPartsShareOnePlacement()
	{
		BakedBoardGeometry.Mesh[] high = BakedBoardGeometry.sharedBoard(true);
		BakedBoardGeometry.Mesh[] low = BakedBoardGeometry.sharedBoard(false);
		for (int g = 0; g < 4; g++)
		{
			for (int axis = 0; axis < 3; axis++)
			{
				float[] h = extentOfParts(high, axis, g);
				float[] l = extentOfParts(low, axis, g);
				String what = BakedBoardGeometry.PART_NAMES[g] + " axis " + axis;
				assertEquals(what + " min", h[0], l[0], 0.6f);
				assertEquals(what + " max", h[1], l[1], 0.6f);
			}
		}
	}

	@Test
	public void posingThePartsApartIsPosingTheBoardWhole()
	{
		// every part goes through the same per-vertex transform, so the parts line up in any pose
		BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(false);
		int total = 0;
		for (BakedBoardGeometry.Mesh m : parts)
		{
			total += m.vertices.length;
		}
		float[] whole = new float[total];
		int at = 0;
		for (BakedBoardGeometry.Mesh m : parts)
		{
			System.arraycopy(m.vertices, 0, whole, at, m.vertices.length);
			at += m.vertices.length;
		}
		float roll = 0.7f;
		float pitch = -0.3f;
		float flip = 1.9f;
		float pivotY = -60f;
		float[] posedWhole = BoardPlacement.poseInto(whole, roll, pitch, flip, pivotY, new float[total]);
		at = 0;
		for (BakedBoardGeometry.Mesh m : parts)
		{
			float[] posed = BoardPlacement.poseInto(m.vertices, roll, pitch, flip, pivotY,
				new float[m.vertices.length]);
			for (int i = 0; i < posed.length; i++)
			{
				assertEquals(posedWhole[at + i], posed[i], 1e-4f);
			}
			at += m.vertices.length;
		}
	}

	private static BakedBoardGeometry.Mesh part(BakedBoardGeometry.Mesh[] parts, int part)
	{
		for (BakedBoardGeometry.Mesh m : parts)
		{
			if (m.part == part)
			{
				return m;
			}
		}
		throw new AssertionError("no part " + part);
	}

	/** min and max of one axis over a flat xyz array (optionally only faces of one colour, 9 floats per face). */
	private static float[] extent(float[] xyz, int axis, int[] faceColor, int color)
	{
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (int i = axis; i < xyz.length; i += 3)
		{
			if (faceColor != null && faceColor[i / 9] != color)
			{
				continue;
			}
			min = Math.min(min, xyz[i]);
			max = Math.max(max, xyz[i]);
		}
		return new float[]{min, max};
	}

	/** min and max of one axis over one part's vertices, or every part's for part -1. */
	private static float[] extentOfParts(BakedBoardGeometry.Mesh[] parts, int axis, int part)
	{
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		for (BakedBoardGeometry.Mesh m : parts)
		{
			if (part >= 0 && m.part != part)
			{
				continue;
			}
			for (int i = axis; i < m.vertices.length; i += 3)
			{
				min = Math.min(min, m.vertices[i]);
				max = Math.max(max, m.vertices[i]);
			}
		}
		return new float[]{min, max};
	}

	// ---- relighting and limits ----

	@Test
	public void aTinyPoseChangeReusesTheLitModel()
	{
		assertFalse(BakedBoardModel.needsRelight(0.5f, 0.1f, 1f, -60f, 0.502f, 0.099f, 1.001f, -60.1f));
		assertTrue(BakedBoardModel.needsRelight(0.5f, 0.1f, 1f, -60f, 0.51f, 0.1f, 1f, -60f));
		assertTrue(BakedBoardModel.needsRelight(0.5f, 0.1f, 1f, -60f, 0.5f, 0.09f, 1f, -60f));
		assertTrue(BakedBoardModel.needsRelight(0.5f, 0.1f, 1f, -60f, 0.5f, 0.1f, 1.01f, -60f));
		assertTrue(BakedBoardModel.needsRelight(0.5f, 0.1f, 1f, -60f, 0.5f, 0.1f, 1f, -61f));
	}

	@Test
	public void aPartOverTheRenderersLimitIsRefused() throws IOException
	{
		BakedBoardGeometry.Mesh[] ok = parse(TWO_PARTS);
		assertTrue(BakedBoardModel.fitsRenderers(ok));
		BakedBoardGeometry.Mesh big = new BakedBoardGeometry.Mesh(BakedBoardGeometry.DECK,
			new float[3 * (BakedBoardModel.MAX_VERTICES + 1)], new int[3], new int[3]);
		assertFalse(BakedBoardModel.fitsRenderers(new BakedBoardGeometry.Mesh[]{ok[0], big}));
		assertFalse(BakedBoardModel.fitsRenderers(new BakedBoardGeometry.Mesh[0]));
	}

	// ---- colour ----

	private static int channelError(int a, int b)
	{
		return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)),
			Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)), Math.abs((a & 255) - (b & 255))));
	}

	@Test
	public void rgbToHslRoundTripsWithinTheFormatsPrecision()
	{
		Random rnd = new Random(7);
		for (double brightness : new double[]{0.6, 0.7, 0.8, 0.9})
		{
			long sum = 0;
			int worst = 0;
			int n = 20000;
			for (int i = 0; i < n; i++)
			{
				int rgb = rnd.nextInt(0x1000000);
				int back = OsrsColor.hslToRgb(OsrsColor.rgbToHsl(rgb, brightness), brightness);
				int e = channelError(rgb, back);
				sum += e;
				worst = Math.max(worst, e);
			}
			double mean = (double) sum / n;
			// 64 hues and 8 saturations: a few steps per channel on average, at worst about a sixth of the range
			assertTrue("brightness " + brightness + " mean " + mean, mean < 10);
			assertTrue("brightness " + brightness + " worst " + worst, worst <= 48);
		}
	}

	@Test
	public void greysRoundTripClosely()
	{
		for (int c = 0; c < 256; c += 5)
		{
			int rgb = c << 16 | c << 8 | c;
			int back = OsrsColor.hslToRgb(OsrsColor.rgbToHsl(rgb, 0.8), 0.8);
			assertTrue("grey " + c + " -> " + Integer.toHexString(back), channelError(rgb, back) <= 12);
		}
	}

	@Test
	public void rgbToHslPicksTheBestOfItsNeighbours()
	{
		Random rnd = new Random(11);
		for (int i = 0; i < 300; i++)
		{
			int rgb = rnd.nextInt(0x1000000);
			int hsl = OsrsColor.rgbToHsl(rgb, 0.8);
			long e = OsrsColor.distance(rgb, OsrsColor.hslToRgb(hsl, 0.8));
			for (int dh = -1; dh <= 1; dh++)
			{
				for (int ds = -1; ds <= 1; ds++)
				{
					for (int dl = -1; dl <= 1; dl++)
					{
						int s = OsrsColor.saturation(hsl) + ds;
						int l = OsrsColor.lightness(hsl) + dl;
						if (s < 0 || s > 7 || l < 0 || l > 127)
						{
							continue;
						}
						int n = OsrsColor.pack(OsrsColor.hue(hsl) + dh, s, l);
						assertTrue(OsrsColor.distance(rgb, OsrsColor.hslToRgb(n, 0.8)) >= e);
					}
				}
			}
		}
	}

	@Test
	public void packAndUnpackAgree()
	{
		int hsl = OsrsColor.pack(45, 5, 99);
		assertEquals(45, OsrsColor.hue(hsl));
		assertEquals(5, OsrsColor.saturation(hsl));
		assertEquals(99, OsrsColor.lightness(hsl));
		assertEquals(0, OsrsColor.pack(64, 8, 128));
	}

	@Test
	public void lightScalesLightnessOnlyWithinTheClientsRange()
	{
		int hsl = OsrsColor.pack(20, 3, 80);
		assertEquals(hsl, OsrsColor.light(hsl, 128));
		assertEquals(OsrsColor.pack(20, 3, 40), OsrsColor.light(hsl, 64));
		assertEquals(OsrsColor.pack(20, 3, 126), OsrsColor.light(hsl, 255));
		assertEquals(OsrsColor.pack(20, 3, 2), OsrsColor.light(hsl, 0));
	}

	// ---- lighting the baked colours ----

	@Test
	public void theIntensityReadBackFromTheLitProbeIsWithinOneStep()
	{
		for (int intensity = 6; intensity < 250; intensity++)
		{
			int lit = OsrsColor.light(BakedBoardModel.PROBE, intensity);
			int read = BakedBoardModel.intensity(lit);
			assertTrue(intensity + " read as " + read, Math.abs(read - intensity) <= 1);
		}
	}

	@Test
	public void shadeLightsEachCornersBakedColourByItsOwnIntensity()
	{
		short a = (short) OsrsColor.pack(10, 4, 100);
		short b = (short) OsrsColor.pack(30, 2, 60);
		short c = (short) OsrsColor.pack(50, 7, 20);
		short[] corners = {a, b, c, a, b, c, a, b, c};
		int[] c1 = {OsrsColor.light(BakedBoardModel.PROBE, 128), OsrsColor.light(BakedBoardModel.PROBE, 90), 5};
		int[] c2 = {OsrsColor.light(BakedBoardModel.PROBE, 64), 0, 6};
		int[] c3 = {OsrsColor.light(BakedBoardModel.PROBE, 160), -1, -2};
		BakedBoardModel.shade(c1, c2, c3, corners);

		// smooth face: each corner its own intensity (within the probe's one-step precision)
		assertLit(a, 128, c1[0]);
		assertLit(b, 64, c2[0]);
		assertLit(c, 160, c3[0]);
		// flat face: the one intensity at every corner, now smooth so the corners keep their colours
		assertLit(a, 90, c1[1]);
		assertLit(b, 90, c2[1]);
		assertLit(c, 90, c3[1]);
		// hidden face: untouched
		assertEquals(5, c1[2]);
		assertEquals(6, c2[2]);
		assertEquals(-2, c3[2]);
	}

	private static void assertLit(short baked, int intensity, int actual)
	{
		assertEquals("hue and saturation kept", baked & 0xff80, actual & 0xff80);
		int expected = OsrsColor.lightness(OsrsColor.light(baked & 0xffff, intensity));
		assertTrue("lightness " + (actual & 127) + " vs " + expected, Math.abs((actual & 127) - expected) <= 1);
	}

	@Test
	public void weldKeysAreDistinctIntegerPositionsForEveryVertex()
	{
		Set<Long> seen = new HashSet<>();
		for (int v = 0; v < BakedBoardModel.MAX_VERTICES; v++)
		{
			int x = BakedBoardModel.weldKeyX(v);
			int y = BakedBoardModel.weldKeyY(v);
			assertTrue(Math.abs(x) <= 128 && Math.abs(y) <= 128);
			assertTrue("vertex " + v, seen.add((long) x << 32 | (y & 0xffffffffL)));
		}
	}

	@Test
	public void cornerColoursConvertOncePerDistinctColour() throws IOException
	{
		BakedBoardGeometry.Mesh m = parse(TWO_PARTS)[0];
		short[] hsl = BakedBoardGeometry.cornerHsl(m, 0.8);
		assertEquals(6, hsl.length);
		assertEquals(hsl[0], hsl[3]);
		assertEquals(OsrsColor.rgbToHsl(0x808080, 0.8), hsl[5] & 0xffff);
	}
}
