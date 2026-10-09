package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import org.junit.Test;

public class PartOutlineTest
{
	/** A square sheet (x -10..10, z -40..40) in two triangles, UV-mapped onto the middle of the image, twice. */
	private static BakedBoardGeometry.Mesh square()
	{
		float[] v = {-10, 0, -40, 10, 0, -40, 10, 0, 40, -10, 0, 40};
		// the second copy (another wheel, say) shares the UVs: its faces are the same in the image
		int[] f = {0, 1, 2, 0, 2, 3, 0, 1, 2, 0, 2, 3};
		int[] rgb = new int[12];
		// nose (z = 40) at v = 0.75 (towards the top of the image)
		float[] uv = {0.25f, 0.25f, 0.75f, 0.25f, 0.75f, 0.75f, 0.25f, 0.25f, 0.75f, 0.75f, 0.25f, 0.75f,
			0.25f, 0.25f, 0.75f, 0.25f, 0.75f, 0.75f, 0.25f, 0.25f, 0.75f, 0.75f, 0.25f, 0.75f};
		return BakedBoardGeometry.Mesh.of(1, v, f, rgb, uv);
	}

	private static final DesignLayout.Part LAYOUT = new DesignLayout.Part(100, 200, 0, 0);

	@Test
	public void outlineIsTheEdgesNoOtherFaceShares()
	{
		PartOutline o = PartOutline.of(square(), LAYOUT, false);
		// the shared copy is one set of faces; the diagonal is inside
		assertEquals(2 * 6, o.faces.length);
		assertEquals(4 * 4, o.edges.length);
		for (int i = 0; i < o.edges.length; i += 4)
		{
			float[] e = o.edges;
			boolean vertical = e[i] == e[i + 2];
			boolean horizontal = e[i + 1] == e[i + 3];
			assertTrue("an outline edge is a side of the square", vertical ^ horizontal);
		}
		assertArrayEquals(new double[]{25, 50, 75, 150}, o.bounds(), 1e-4);
	}

	@Test
	public void maskCoversTheFacesOnly()
	{
		PartOutline o = PartOutline.of(square(), LAYOUT, false);
		boolean[] m = o.mask();
		assertEquals(100 * 200, m.length);
		assertTrue(m[100 * 100 + 50]);
		assertTrue(m[50 * 100 + 25]);
		assertFalse(m[49 * 100 + 50]);
		assertFalse(m[100 * 100 + 80]);
		int covered = 0;
		for (boolean b : m)
		{
			covered += b ? 1 : 0;
		}
		assertEquals(50 * 100, covered);
	}

	@Test
	public void noseEndIsFoundFromTheGeometry()
	{
		assertTrue(PartOutline.of(square(), LAYOUT, false).noseUp);
	}

	@Test
	public void boltsTakeTheUvOfTheFaceOverThem()
	{
		PartOutline o = PartOutline.of(square(), LAYOUT, true);
		assertEquals(8, o.bolts.size());
		// x -3.16 of -10..10 maps to u 0.25 + 0.5 * 6.84 / 20; z 33.24 of -40..40 to v 0.25 + 0.5 * 73.24 / 80
		double u = 0.25 + 0.5 * (10 - 3.16) / 20;
		double v = 0.25 + 0.5 * (40 + 33.24) / 80;
		double[] b = o.bolts.get(6);
		assertEquals(u * 100, b[0], 1e-3);
		assertEquals((1 - v) * 200, b[1], 1e-3);
	}

	@Test
	public void theRealBoardsOutlinesSitInTheirLayouts()
	{
		BakedBoardGeometry.Mesh[] high = BakedBoardGeometry.sharedBoard(true);
		for (DesignPart part : DesignPart.values())
		{
			DesignLayout.Part layout = DesignLayout.bundled().of(part);
			PartOutline o = PartOutline.of(high[part.index], layout, part != DesignPart.WHEELS);
			double[] b = o.bounds();
			assertTrue(part + " bounds", b[0] >= 0 && b[1] >= 0 && b[2] <= layout.width && b[3] <= layout.height);
			assertTrue(part + " outline", o.edges.length > 40);
			if (part != DesignPart.WHEELS)
			{
				assertEquals(part + " bolts", 8, o.bolts.size());
				boolean[] m = o.mask();
				for (double[] bolt : o.bolts)
				{
					assertTrue(part + " bolt on the part", m[(int) bolt[1] * layout.width + (int) bolt[0]]);
				}
				// the board's long axis runs up the image
				assertTrue(b[3] - b[1] > 2 * (b[2] - b[0]));
			}
		}
	}
}
