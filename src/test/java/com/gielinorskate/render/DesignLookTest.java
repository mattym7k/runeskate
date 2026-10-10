package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import java.util.Arrays;
import org.junit.Test;

/** Designs as colour files over the shared baked geometry, and recolouring a board without touching its geometry. */
public class DesignLookTest
{
	private static final BoardDesigns DESIGNS = BoardDesigns.bundled();

	private static BoardLook look(String grip, String deck, String wheels)
	{
		return new BoardLook(DESIGNS.byId(grip), DESIGNS.byId(deck), DESIGNS.byId(wheels));
	}

	@Test
	public void everyDesignHasColoursForEveryCornerOfItsPartAtBothDetails()
	{
		for (boolean high : new boolean[]{true, false})
		{
			BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(high);
			for (BoardDesign d : DESIGNS.all())
			{
				BakedBoardGeometry.Mesh mesh = null;
				for (BakedBoardGeometry.Mesh m : parts)
				{
					if (m.part == d.part.index)
					{
						mesh = m;
					}
				}
				assertNotNull(d.id, mesh);
				int[] c = DesignColours.colours(d.id, high, mesh);
				assertNotNull(d.id + (high ? " high" : " low"), c);
				assertEquals(d.id, mesh.faceCount() * 3, c.length);
			}
		}
	}

	@Test
	public void designPartsCarryTheirDesignUvs()
	{
		for (boolean high : new boolean[]{true, false})
		{
			for (BakedBoardGeometry.Mesh m : BakedBoardGeometry.sharedBoard(high))
			{
				if (m.part == BakedBoardGeometry.HARDWARE)
				{
					assertNull(m.cornerUv);
					continue;
				}
				assertNotNull(m.cornerUv);
				assertEquals(m.faceCount() * 6, m.cornerUv.length);
				int withUv = 0;
				for (int f = 0; f < m.faceCount(); f++)
				{
					float u = m.cornerUv[f * 6];
					if (!Float.isNaN(u))
					{
						withUv++;
						for (int k = 0; k < 6; k++)
						{
							float c = m.cornerUv[f * 6 + k];
							assertTrue(c >= -0.01f && c <= 1.01f);
						}
					}
				}
				// the grip and deck sheets are all design; the wheels' hubs are not
				if (m.part != BakedBoardGeometry.WHEELS)
				{
					assertEquals(m.faceCount(), withUv);
				}
				else
				{
					assertTrue(withUv > 0 && withUv < m.faceCount());
				}
			}
		}
	}

	@Test
	public void theDefaultDesignsAreTheGeometrysOwnColours()
	{
		BoardLook defaults = BoardLook.defaults(DESIGNS);
		for (BakedBoardGeometry.Mesh m : BakedBoardGeometry.sharedBoard(false))
		{
			BoardDesign d = BakedBoardModel.designFor(m, defaults);
			if (d != null)
			{
				assertArrayEquals(d.id, m.cornerRgb, DesignColours.colours(d.id, false, m));
			}
		}
	}

	@Test
	public void aNewLookRecoloursTheDesignPartsAndLeavesTheGeometryAlone()
	{
		BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(false);
		float[][] verts = new float[parts.length][];
		int[][] faces = new int[parts.length][];
		for (int i = 0; i < parts.length; i++)
		{
			verts[i] = parts[i].vertices.clone();
			faces[i] = parts[i].faces.clone();
		}
		BoardLook a = BoardLook.defaults(DESIGNS);
		BoardLook b = look("GRIP_RUNE", "RUNE", "WHEELS_RUNE");
		for (BakedBoardGeometry.Mesh m : parts)
		{
			short[] before = BakedBoardModel.partHsl(m, a, false, 0.8);
			short[] after = BakedBoardModel.partHsl(m, b, false, 0.8);
			assertEquals(m.cornerRgb.length, after.length);
			if (m.part == BakedBoardGeometry.HARDWARE)
			{
				// no designs on the hardware: the very same colours
				assertSame(before, after);
			}
			else
			{
				assertFalse("part " + m.part + " kept its colours", Arrays.equals(before, after));
			}
			// switching back is the cached conversion: no work, same array
			assertSame(before, BakedBoardModel.partHsl(m, a, false, 0.8));
		}
		for (int i = 0; i < parts.length; i++)
		{
			assertArrayEquals(verts[i], parts[i].vertices, 0f);
			assertArrayEquals(faces[i], parts[i].faces);
		}
	}

	@Test
	public void theLitRewriteDrawsTheNewColours()
	{
		BakedBoardGeometry.Mesh deck = BakedBoardGeometry.sharedBoard(false)[1];
		assertEquals(BakedBoardGeometry.DECK, deck.part);
		short[] base = BakedBoardModel.partHsl(deck, BoardLook.defaults(DESIGNS), false, 0.8);
		short[] rune = BakedBoardModel.partHsl(deck, look("GRIP_BLACK", "RUNE", "WHEELS_NATURAL"), false, 0.8);
		int faces = deck.faceCount();
		int[][] lit = new int[2][];
		short[][] looks = {base, rune};
		for (int k = 0; k < 2; k++)
		{
			// a lit probe at full intensity on every corner
			int[] c1 = new int[faces];
			int[] c2 = new int[faces];
			int[] c3 = new int[faces];
			Arrays.fill(c1, 64);
			Arrays.fill(c2, 64);
			Arrays.fill(c3, 64);
			BakedBoardModel.shade(c1, c2, c3, looks[k], null);
			lit[k] = c1;
		}
		assertFalse(Arrays.equals(lit[0], lit[1]));
		assertEquals(OsrsColor.light(rune[0] & 0xffff, BakedBoardModel.intensity(64)), lit[1][0]);
	}

	@Test
	public void brokenColourFilesAreRefused()
	{
		byte[] ok = {'R', 'S', 'K', 'C', 1, 2, 0, 0, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		assertArrayEquals(new int[]{0x010203, 0x040506, 0x070809}, DesignColours.parse(ok, 2));
		for (byte[] bad : new byte[][]{
			new byte[0],
			Arrays.copyOf(ok, ok.length - 1),
			Arrays.copyOf(ok, ok.length + 1)})
		{
			try
			{
				DesignColours.parse(bad, 2);
				fail("accepted " + bad.length + " bytes");
			}
			catch (IllegalArgumentException expected)
			{
				// refused
			}
		}
		try
		{
			// another part's file
			DesignColours.parse(ok, 1);
			fail();
		}
		catch (IllegalArgumentException expected)
		{
			// refused
		}
		// a design without a file: the part's own colours
		assertNull(DesignColours.colours("NO_SUCH_DESIGN", false, BakedBoardGeometry.sharedBoard(false)[0]));
	}

	@Test
	public void packedColoursReadBack()
	{
		BakedBoardGeometry.Mesh grip = null;
		for (BakedBoardGeometry.Mesh m : BakedBoardGeometry.sharedBoard(true))
		{
			if (m.part == BakedBoardGeometry.GRIP)
			{
				grip = m;
			}
		}
		assertNotNull(grip);
		int[] colours = new int[grip.cornerRgb.length];
		Arrays.fill(colours, 0x123456);
		assertArrayEquals(colours, DesignColours.parse(DesignColours.pack(0, colours), 0));
	}
}
