package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoardSplitTest
{
	@Test
	public void everyTriangleOfTheClassicBoardIsInExactlyOneHalf()
	{
		BoardGeometry.Mesh mesh = BoardGeometry.standardBoard();
		BoardSplit s = BoardSplit.split(new float[][]{mesh.tris}, null);
		int faces = mesh.faceCount();
		assertEquals(faces, s.nose.faceIn[0].length);
		for (int i = 0; i < faces; i++)
		{
			assertTrue("face " + i, s.nose.faceIn[0][i] ^ s.tail.faceIn[0][i]);
			float cz = mesh.tris[i * 9 + 2] + mesh.tris[i * 9 + 5] + mesh.tris[i * 9 + 8];
			assertEquals("face " + i + " by its centroid", cz >= 0f, s.nose.faceIn[0][i]);
		}
		assertEquals(faces, s.nose.faceCount + s.tail.faceCount);
		// a board is about symmetric: neither half is a sliver
		assertTrue(s.nose.faceCount > faces / 4);
		assertTrue(s.tail.faceCount > faces / 4);
	}

	@Test
	public void everyTriangleOfEveryBakedPartIsInExactlyOneHalf()
	{
		for (boolean high : new boolean[]{false, true})
		{
			BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(high);
			if (parts == null)
			{
				continue;
			}
			BoardSplit s = BoardSplit.of(parts);
			int total = 0;
			for (int p = 0; p < parts.length; p++)
			{
				BakedBoardGeometry.Mesh m = parts[p];
				assertEquals(m.faceCount(), s.nose.faceIn[p].length);
				for (int i = 0; i < m.faceCount(); i++)
				{
					assertTrue(s.nose.faceIn[p][i] ^ s.tail.faceIn[p][i]);
				}
				total += m.faceCount();
			}
			assertEquals(total, s.nose.faceCount + s.tail.faceCount);
		}
	}

	@Test
	public void theHalvesMeetAtTheMiddleAndTogetherSpanTheBoard()
	{
		BoardGeometry.Mesh mesh = BoardGeometry.standardBoard();
		BoardSplit s = BoardSplit.split(new float[][]{mesh.tris}, null);
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int i = 2; i < mesh.tris.length; i += 3)
		{
			minZ = Math.min(minZ, mesh.tris[i]);
			maxZ = Math.max(maxZ, mesh.tris[i]);
			maxY = Math.max(maxY, mesh.tris[i - 1]);
		}
		assertEquals(maxZ, s.nose.maxZ, 1e-4f);
		assertEquals(minZ, s.tail.minZ, 1e-4f);
		// the nose half is in front of the cut, the tail behind it (a long deck triangle across the cut goes with its
		// centroid, so the break is a sawtooth and each half reaches a little past the middle)
		assertTrue(s.nose.cz > 0f);
		assertTrue(s.tail.cz < 0f);
		assertTrue(s.nose.minZ < s.nose.maxZ && s.nose.maxZ > 0f);
		assertTrue(s.tail.maxZ > s.tail.minZ && s.tail.minZ < 0f);
		assertEquals(-s.nose.cz, s.tail.cz, 1e-3f);
		// each half keeps a truck and its wheels: it rests on them, as low as the whole board
		assertEquals(maxY, s.nose.maxY, 1e-4f);
		assertEquals(maxY, s.tail.maxY, 1e-4f);
		assertEquals(s.nose.maxY - s.nose.cy, s.nose.bottom(), 1e-6f);
		assertTrue(s.nose.bottom() > 0f);
	}

	@Test
	public void aHalfsVerticesAreAboutItsOwnCentreAndUnusedOnesAreParked()
	{
		BoardGeometry.Mesh mesh = BoardGeometry.standardBoard();
		BoardSplit s = BoardSplit.split(new float[][]{mesh.tris}, null);
		BoardSplit.Half h = s.nose;
		for (int i = 0; i < mesh.faceCount(); i++)
		{
			for (int k = 0; k < 9; k++)
			{
				float centre = k % 3 == 0 ? h.cx : k % 3 == 1 ? h.cy : h.cz;
				float want = h.faceIn[0][i] ? mesh.tris[i * 9 + k] - centre : 0f;
				assertEquals(want, h.vertices[0][i * 9 + k], 1e-4f);
			}
		}
	}

	@Test
	public void bakedPartsOfAHalfShareOneCentre()
	{
		BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(false);
		if (parts == null)
		{
			return;
		}
		BoardSplit s = BoardSplit.of(parts);
		for (int p = 0; p < parts.length; p++)
		{
			BakedBoardGeometry.Mesh m = parts[p];
			for (int i = 0; i < m.faceCount(); i++)
			{
				if (!s.nose.faceIn[p][i])
				{
					continue;
				}
				int v = m.faces[i * 3];
				assertEquals(m.vertices[v * 3] - s.nose.cx, s.nose.vertices[p][v * 3], 1e-3f);
				assertEquals(m.vertices[v * 3 + 2] - s.nose.cz, s.nose.vertices[p][v * 3 + 2], 1e-3f);
			}
		}
	}

	@Test
	public void theSplitIsMadeOnceAndKept()
	{
		BoardGeometry.Mesh mesh = BoardGeometry.sharedDefaultBoard();
		assertSame(BoardSplit.of(mesh), BoardSplit.of(mesh));
		BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(false);
		if (parts != null)
		{
			assertSame(BoardSplit.of(parts), BoardSplit.of(parts));
			assertSame(BoardSplit.of(parts).nose, BoardSplit.of(parts).half(true));
		}
	}

	@Test
	public void aHalfKeepsItsFacesColoursAndHidesTheRest()
	{
		short a = (short) OsrsColor.pack(10, 5, 60);
		short b = (short) OsrsColor.pack(40, 7, 30);
		short[] corners = {a, a, a, b, b, b, a, b, a};
		int probe = PROBE_LIT;
		int[] c1 = {probe, probe, probe};
		int[] c2 = {probe, probe, probe};
		int[] c3 = {probe, -1, probe};
		// the whole board as the baked board lights it
		int[] w1 = c1.clone();
		int[] w2 = c2.clone();
		int[] w3 = c3.clone();
		BakedBoardModel.shade(w1, w2, w3, corners);
		boolean[] in = {true, false, true};
		BoardSplit.shadeHalf(c1, c2, c3, corners, in);
		for (int i = 0; i < 3; i++)
		{
			if (in[i])
			{
				assertEquals(w1[i], c1[i]);
				assertEquals(w2[i], c2[i]);
				assertEquals(w3[i], c3[i]);
			}
			else
			{
				assertEquals(-2, c3[i]);
			}
		}
		// the classic board: the client's own lit colours stay, the other half and leftovers are hidden
		int[] k1 = {5, 6, 7, 8};
		int[] k3 = {5, 6, 7, 8};
		BoardSplit.shadeHalf(k1, k1.clone(), k3, null, new boolean[]{false, true, true});
		assertEquals(-2, k3[0]);
		assertEquals(6, k3[1]);
		assertEquals(7, k3[2]);
		assertEquals(-2, k3[3]);
		assertFalse(k1[0] == -2);
	}

	/** A probe corner lit to intensity 128 (unchanged). */
	private static final int PROBE_LIT = OsrsColor.pack(0, 0, BakedBoardModel.PROBE_LIGHTNESS);

	@Test
	public void anEmptyHalfIsAPoint()
	{
		// a single triangle wholly at the nose: the tail is empty
		BoardSplit s = BoardSplit.split(new float[][]{{0, 0, 5, 1, 0, 6, 0, 1, 7}}, null);
		assertEquals(1, s.nose.faceCount);
		assertEquals(0, s.tail.faceCount);
		assertEquals(0f, s.tail.cx, 0f);
		assertNotNull(s.tail.vertices[0]);
	}
}
