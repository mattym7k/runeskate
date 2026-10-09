package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BoardGeometryTest
{
	@Test
	public void faceCountWithinBudget()
	{
		ClassicBoard.Mesh m = ClassicBoard.standardBoard();
		assertTrue(m.faceCount() > 0);
		assertTrue(m.faceCount() <= 400);
		assertEquals(m.faceCount() * 9, m.tris.length);
	}

	@Test
	public void boundsMatchSpec()
	{
		ClassicBoard.Mesh mesh = ClassicBoard.standardBoard();
		float[] t = mesh.tris;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		float maxAbsX = 0f;
		for (int i = 0; i < t.length; i += 3)
		{
			minY = Math.min(minY, t[i + 1]);
			maxY = Math.max(maxY, t[i + 1]);
			minZ = Math.min(minZ, t[i + 2]);
			maxZ = Math.max(maxZ, t[i + 2]);
			maxAbsX = Math.max(maxAbsX, Math.abs(t[i]));
		}
		assertEquals(0f, maxY, 1e-3f);
		assertEquals(-23f, minY, 1e-3f);
		assertEquals(50f, maxZ, 1e-3f);
		assertEquals(-50f, minZ, 1e-3f);
		assertEquals(19.5f, maxAbsX, 1e-3f);
	}

	@Test
	public void deckMiddleTopIsBoardTop()
	{
		ClassicBoard.Mesh mesh = ClassicBoard.standardBoard();
		float[] t = mesh.tris;
		int[] c = mesh.colorIndex;
		for (int f = 0; f < c.length; f++)
		{
			if (c[f] != ClassicBoard.GRIP)
			{
				continue;
			}
			int o = f * 9;
			boolean allWithinMiddle = true;
			for (int v = 0; v < 3; v++)
			{
				float z = t[o + v * 3 + 2];
				if (Math.abs(z) > 30f)
				{
					allWithinMiddle = false;
					break;
				}
			}
			if (!allWithinMiddle)
			{
				continue;
			}
			for (int v = 0; v < 3; v++)
			{
				float y = t[o + v * 3 + 1];
				assertEquals(-BoardGeometry.BOARD_TOP, y, 1e-3f);
			}
		}
	}

	@Test
	public void kicktailsRise()
	{
		ClassicBoard.Mesh mesh = ClassicBoard.standardBoard();
		float[] t = mesh.tris;
		int[] c = mesh.colorIndex;
		boolean foundPositive = false;
		boolean foundNegative = false;
		for (int f = 0; f < c.length; f++)
		{
			if (c[f] != ClassicBoard.GRIP)
			{
				continue;
			}
			int o = f * 9;
			for (int v = 0; v < 3; v++)
			{
				float y = t[o + v * 3 + 1];
				float z = t[o + v * 3 + 2];
				if (z > 45f && y < -20f)
				{
					foundPositive = true;
				}
				if (z < -45f && y < -20f)
				{
					foundNegative = true;
				}
			}
		}
		assertTrue("expected a GRIP vertex with y < -20 at z > 45", foundPositive);
		assertTrue("expected a GRIP vertex with y < -20 at z < -45", foundNegative);
	}

	@Test
	public void gripFacesPointUp_graphicFacesPointDown()
	{
		ClassicBoard.Mesh mesh = ClassicBoard.standardBoard();
		float[] t = mesh.tris;
		int[] c = mesh.colorIndex;
		for (int f = 0; f < c.length; f++)
		{
			int o = f * 9;
			float ax = t[o], ay = t[o + 1], az = t[o + 2];
			float bx = t[o + 3], by = t[o + 4], bz = t[o + 5];
			float cx = t[o + 6], cy = t[o + 7], cz = t[o + 8];
			float ux = bx - ax, uy = by - ay, uz = bz - az;
			float vx = cx - ax, vy = cy - ay, vz = cz - az;
			float normalY = uz * vx - ux * vz;
			if (c[f] == ClassicBoard.GRIP)
			{
				assertTrue("GRIP face normal.y should be < 0", normalY < 0f);
			}
			else if (c[f] == ClassicBoard.GRAPHIC)
			{
				assertTrue("GRAPHIC face normal.y should be > 0", normalY > 0f);
			}
		}
	}

	@Test
	public void wheelsAreSymmetric()
	{
		ClassicBoard.Mesh mesh = ClassicBoard.standardBoard();
		float[] t = mesh.tris;
		int[] c = mesh.colorIndex;
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int f = 0; f < c.length; f++)
		{
			if (c[f] != ClassicBoard.WHEELS)
			{
				continue;
			}
			int o = f * 9;
			for (int v = 0; v < 3; v++)
			{
				float x = t[o + v * 3];
				float z = t[o + v * 3 + 2];
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minZ = Math.min(minZ, z);
				maxZ = Math.max(maxZ, z);
			}
		}
		assertEquals(-maxX, minX, 1e-3f);
		assertEquals(-maxZ, minZ, 1e-3f);
	}

	@Test
	public void rollByPiFlipsBoardOverItsLongAxis()
	{
		float[] t = ClassicBoard.standardBoard().tris;
		float[] r = BoardGeometry.rotate(t, (float) Math.PI, 0f);
		// x and y are negated, z unchanged
		assertEquals(-t[0], r[0], 1e-3f);
		assertEquals(-t[1], r[1], 1e-3f);
		assertEquals(t[2], r[2], 1e-3f);
	}

	@Test
	public void rotateDoesNotModifyInput()
	{
		float[] t = ClassicBoard.standardBoard().tris;
		float before = t[0];
		BoardGeometry.rotate(t, 1f, 1f);
		assertEquals(before, t[0], 0f);
	}
}
