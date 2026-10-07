package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.Test;

public class CustomBakeTest
{
	/** Largest channel difference allowed against the offline bake (float rounding, half-way roundings). */
	private static final int TOLERANCE = 2;

	static int[] argb(BufferedImage img)
	{
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}

	private static int channelDiff(int a, int b)
	{
		int d = 0;
		for (int s = 0; s <= 16; s += 8)
		{
			d = Math.max(d, Math.abs((a >> s & 255) - (b >> s & 255)));
		}
		return d;
	}

	/**
	 * tools/custom_designs.py sampled custom_fixture.png with the offline bake's own blur and bilinear sample
	 * (tools/design_sampling.py) at deck corner UVs, the image placed over the whole deck layout: the runtime
	 * bake gives the same colours.
	 */
	@Test
	public void matchesTheOfflineBakeOfTheFixture() throws Exception
	{
		BufferedImage img;
		try (InputStream in = getClass().getResourceAsStream("/com/gielinorskate/design/custom_fixture.png"))
		{
			img = ImageIO.read(in);
		}
		CustomBake.Source src = new CustomBake.Source(argb(img), img.getWidth(), img.getHeight(), 0);
		DesignLayout.Part deck = DesignLayout.bundled().of(DesignPart.DECK);
		double scale = deck.width / (double) img.getWidth();
		assertEquals(deck.height, img.getHeight() * scale, 1e-9);
		ImagePlacement p = new ImagePlacement(img.getWidth(), img.getHeight(), deck.width / 2.0, deck.height / 2.0,
			scale, 0, false);
		int checked = 0;
		int worst = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			getClass().getResourceAsStream("/com/gielinorskate/design/custom_fixture_expected.txt"),
			StandardCharsets.UTF_8)))
		{
			float[][] blurred = null;
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] f = line.split(" ");
				if (f[0].equals("detail"))
				{
					boolean high = f[1].equals("high");
					int radius = Integer.parseInt(f[3]);
					assertEquals(radius, CustomBake.radius(deck.blur(high), p.meanScale()));
					blurred = src.blurred(radius);
					continue;
				}
				float u = Float.parseFloat(f[0]);
				float v = Float.parseFloat(f[1]);
				int want = Integer.parseInt(f[2], 16);
				double[] s = p.toImage(u * (double) deck.width, (1 - v) * (double) deck.height);
				int got = CustomBake.sample(blurred, src.width, src.height, s[0], s[1]);
				worst = Math.max(worst, channelDiff(want, got));
				checked++;
			}
		}
		assertTrue("fixture has samples", checked > 1000);
		assertTrue("largest difference " + worst, worst <= TOLERANCE);
	}

	@Test
	public void bakeSamplesEveryDesignCornerAndKeepsTheRest() throws Exception
	{
		// two triangles: the first a design face, the second without UVs (a hub)
		float[] verts = {0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 1};
		int[] faces = {0, 1, 2, 1, 3, 2};
		int[] rgb = {1, 2, 3, 0x111111, 0x222222, 0x333333};
		float nan = Float.NaN;
		float[] uv = {0.25f, 0.75f, 0.75f, 0.75f, 0.25f, 0.25f, nan, nan, nan, nan, nan, nan};
		BakedBoardGeometry.Mesh mesh = BakedBoardGeometry.Mesh.of(1, verts, faces, rgb, uv);
		// a 2x2 image: red, green / blue, white, spread over a 100x100 layout
		int[] px = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
		CustomBake.Source src = new CustomBake.Source(px, 2, 2, 0);
		DesignLayout.Part layout = new DesignLayout.Part(DesignPart.DECK, 100, 100, 0, 0);
		ImagePlacement p = new ImagePlacement(2, 2, 50, 50, 50, 0, false);
		int[] out = CustomBake.bake(mesh, layout, p, src, true);
		// v up: v 0.75 is the top row
		assertArrayEquals(new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x111111, 0x222222, 0x333333}, out);
		// turned a quarter clockwise, the bottom-left (blue) is now at the top-left
		int[] turned = CustomBake.bake(mesh, layout, p.rotated(), src, true);
		assertEquals(0x0000FF, turned[0]);
		assertEquals(0xFF0000, turned[1]);
		// stretched: squeezed to 20 wide at x 70..90 (still 100 tall), both top corners fall left of its middle
		int[] squeezed = CustomBake.bake(mesh, layout, new ImagePlacement(2, 2, 80, 50, 10, 50, 0, false), src, true);
		assertEquals(0xFF0000, squeezed[0]);
		assertEquals(0xFF0000, squeezed[1]);
		assertEquals(0x0000FF, squeezed[2]);
	}

	@Test
	public void outsideTheImageIsItsEdgeColour()
	{
		int[] px = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
		CustomBake.Source src = new CustomBake.Source(px, 2, 2, 0);
		float[][] img = src.blurred(0);
		assertEquals(0xFF0000, CustomBake.sample(img, 2, 2, -50, -50));
		assertEquals(0xFFFFFF, CustomBake.sample(img, 2, 2, 90, 90));
		// halfway between red and green along the top edge
		assertEquals(0x808000, CustomBake.sample(img, 2, 2, 1, -3));
	}

	@Test
	public void transparentPixelsShowTheBackdrop()
	{
		CustomBake.Source src = new CustomBake.Source(new int[]{0x00FFFFFF, 0x80FF0000}, 2, 1, 0x0000FF);
		float[][] img = src.blurred(0);
		assertEquals(0x0000FF, CustomBake.sample(img, 2, 1, 0.5, 0.5));
		assertEquals(0x80007F, CustomBake.sample(img, 2, 1, 1.5, 0.5));
	}

	@Test
	public void boxBlurAveragesAndKeepsFlatAreasFlat()
	{
		float[] flat = new float[30];
		java.util.Arrays.fill(flat, 0.4f);
		for (float f : CustomBake.boxBlur(flat, 6, 5, 2))
		{
			assertEquals(0.4f, f, 1e-6f);
		}
		// one bright pixel in the middle of a 5x5 spreads over its 3x3 neighbourhood
		float[] dot = new float[25];
		dot[12] = 9;
		float[] b = CustomBake.boxBlur(dot, 5, 5, 1);
		assertEquals(1f, b[12], 1e-6f);
		assertEquals(1f, b[6], 1e-6f);
		assertEquals(0f, b[0], 1e-6f);
	}

	@Test
	public void radiusScalesWithTheImagePixelsSize()
	{
		assertEquals(2, CustomBake.radius(2, 1));
		assertEquals(1, CustomBake.radius(2, 2));
		// 1.5 rounds up, as the fixture tool rounds
		assertEquals(2, CustomBake.radius(3, 2));
		assertEquals(10, CustomBake.radius(5, 0.5));
		assertEquals(0, CustomBake.radius(2, 10));
		assertEquals(CustomBake.MAX_RADIUS, CustomBake.radius(12, 0.01));
	}
}
