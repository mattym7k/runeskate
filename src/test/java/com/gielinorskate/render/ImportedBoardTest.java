package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.StringReader;
import org.junit.Test;

public class ImportedBoardTest
{
	@Test
	public void parsesTrianglesAndColours() throws Exception
	{
		BoardGeometry.Mesh m = BoardGeometry.parse(new StringReader(
			"0 0 0 1 0 0 0 0 1 2\n"
				+ "\n"
				+ "1 -2 3 4 -5 6 7 -8 9 4\n"));
		assertEquals(2, m.faceCount());
		assertEquals(2, m.colorIndex[0]);
		assertEquals(4, m.colorIndex[1]);
		assertEquals(-8f, m.tris[16], 0f);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMalformedLines() throws Exception
	{
		BoardGeometry.parse(new StringReader("0 0 0 1 0 0 0 0 1\n"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnknownColour() throws Exception
	{
		BoardGeometry.parse(new StringReader("0 0 0 1 0 0 0 0 1 9\n"));
	}

	@Test
	public void bundledBoardLoadsAndSitsOnTheGround()
	{
		BoardGeometry.Mesh m = BoardGeometry.defaultBoard();
		assertNotNull(m);
		assertTrue(m.faceCount() > 100);
		float maxY = -Float.MAX_VALUE;
		float minTopNearMiddle = Float.MAX_VALUE;
		for (int i = 0; i < m.tris.length; i += 3)
		{
			maxY = Math.max(maxY, m.tris[i + 1]);
			if (Math.abs(m.tris[i + 2]) < 40)
			{
				minTopNearMiddle = Math.min(minTopNearMiddle, m.tris[i + 1]);
			}
		}
		// wheels rest on y = 0 and the flat middle of the deck is at board-top height
		assertEquals(0f, maxY, 0.01f);
		assertEquals(-BoardGeometry.BOARD_TOP, minTopNearMiddle, 0.01f);
	}
}
