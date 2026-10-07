package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class BoardGeometryCacheTest
{
	private static void assertSameBits(float[] expected, float[] actual)
	{
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals("float " + i, Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]));
		}
	}

	@Test
	public void theCachedBoardIsTheParsedResourceBitForBit()
	{
		BoardGeometry.Mesh parsed = BoardGeometry.loadDefaultBoard();
		BoardGeometry.Mesh cached = BoardGeometry.defaultBoard();
		assertSameBits(parsed.tris, cached.tris);
		assertArrayEquals(parsed.colorIndex, cached.colorIndex);
		assertSameBits(parsed.tris, BoardGeometry.sharedDefaultBoard().tris);
		assertArrayEquals(parsed.colorIndex, BoardGeometry.sharedDefaultBoard().colorIndex);
	}

	@Test
	public void theResourceIsParsedOnce()
	{
		assertSame(BoardGeometry.sharedDefaultBoard(), BoardGeometry.sharedDefaultBoard());
	}

	@Test
	public void eachDefaultBoardIsACopyTheCallerMayChange()
	{
		BoardGeometry.Mesh a = BoardGeometry.defaultBoard();
		BoardGeometry.Mesh b = BoardGeometry.defaultBoard();
		assertNotSame(a.tris, b.tris);
		assertNotSame(a.colorIndex, b.colorIndex);
		float first = BoardGeometry.loadDefaultBoard().tris[0];
		a.tris[0] = first + 100f;
		a.colorIndex[0] = 99;
		assertEquals(first, BoardGeometry.defaultBoard().tris[0], 0f);
		assertEquals(first, BoardGeometry.sharedDefaultBoard().tris[0], 0f);
	}
}
