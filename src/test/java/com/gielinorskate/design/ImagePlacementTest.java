package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImagePlacementTest
{
	private static final double EPS = 1e-9;

	private static void assertPoint(double x, double y, double[] p)
	{
		assertEquals(x, p[0], 1e-7);
		assertEquals(y, p[1], 1e-7);
	}

	@Test
	public void identityMapsPixelsOneToOne()
	{
		ImagePlacement p = new ImagePlacement(100, 50, 50, 25, 1, 0, false);
		assertPoint(0, 0, p.toLayout(0, 0));
		assertPoint(100, 50, p.toLayout(100, 50));
		assertPoint(30, 20, p.toImage(30, 20));
	}

	@Test
	public void scaleAndMoveAboutTheCentre()
	{
		ImagePlacement p = new ImagePlacement(100, 50, 200, 300, 2, 0, false);
		assertPoint(100, 250, p.toLayout(0, 0));
		assertPoint(300, 350, p.toLayout(100, 50));
		assertEquals(200, p.placedWidth(), EPS);
		assertEquals(100, p.placedHeight(), EPS);
	}

	@Test
	public void quarterTurnIsClockwise()
	{
		// the image's top-left corner goes to the top-right once turned clockwise
		ImagePlacement p = new ImagePlacement(100, 50, 0, 0, 1, 1, false);
		assertPoint(25, -50, p.toLayout(0, 0));
		assertPoint(-25, 50, p.toLayout(100, 50));
		assertEquals(50, p.placedWidth(), EPS);
		assertEquals(100, p.placedHeight(), EPS);
	}

	@Test
	public void flipMirrorsLeftRight()
	{
		ImagePlacement p = new ImagePlacement(100, 50, 50, 25, 1, 0, true);
		assertPoint(100, 0, p.toLayout(0, 0));
		assertPoint(0, 50, p.toLayout(100, 50));
	}

	@Test
	public void inverseUndoesEveryCombination()
	{
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(64, 37, 120.5, -33.25, 1.7, turns, flip);
				for (double[] s : new double[][]{{0, 0}, {64, 37}, {10.5, 30.25}, {-5, 80}})
				{
					double[] l = p.toLayout(s[0], s[1]);
					assertArrayEquals("turns " + turns + " flip " + flip, s, p.toImage(l[0], l[1]), 1e-9);
				}
			}
		}
	}

	@Test
	public void flipHorizontallyMirrorsInTheLayoutWhateverTheTurn()
	{
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1.5, turns, flip);
				ImagePlacement m = p.flippedHorizontally();
				for (double[] s : new double[][]{{0, 0}, {64, 0}, {13, 29}})
				{
					double[] a = p.toLayout(s[0], s[1]);
					double[] b = m.toLayout(s[0], s[1]);
					// mirrored about the vertical line through the centre, same height
					assertEquals(200 - a[0], b[0], 1e-9);
					assertEquals(a[1], b[1], 1e-9);
				}
			}
		}
	}

	@Test
	public void rotateTurnsAboutTheCentreFourTimesToTheStart()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1.5, 0, true);
		ImagePlacement r = p.rotated();
		assertEquals(1, r.turns);
		assertEquals(100, r.cx, EPS);
		assertEquals(p, r.rotated().rotated().rotated());
	}

	@Test
	public void fitPutsTheWholeImageInsideTheBox()
	{
		double[] box = {10, 20, 110, 420};
		ImagePlacement p = ImagePlacement.initial(200, 100, box).fit(box);
		assertEquals(60, p.cx, EPS);
		assertEquals(220, p.cy, EPS);
		assertEquals(100, p.placedWidth(), 1e-9);
		assertTrue(p.placedHeight() <= 400);
		// turned, the long side goes along the box's long side
		ImagePlacement t = p.rotated().fit(box);
		assertEquals(100, t.placedWidth(), 1e-9);
		assertEquals(200, t.placedHeight(), 1e-9);
	}

	@Test
	public void fillCoversTheBoxCompletely()
	{
		double[] box = {10, 20, 110, 420};
		ImagePlacement p = ImagePlacement.initial(200, 100, box);
		assertEquals(400, p.placedHeight(), 1e-9);
		assertEquals(800, p.placedWidth(), 1e-9);
		double[] tl = p.toImage(10, 20);
		double[] br = p.toImage(110, 420);
		assertTrue(tl[0] >= 0 && tl[1] >= -1e-9 && br[0] <= 200 && br[1] <= 100 + 1e-9);
	}

	@Test
	public void scaleAboutKeepsThePointUnderTheCursor()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1.5, 3, false);
		double[] under = p.toImage(130, 180);
		ImagePlacement z = p.scaledAbout(130, 180, 1.25);
		assertEquals(1.875, z.scaleX, EPS);
		assertEquals(1.875, z.scaleY, EPS);
		assertArrayEquals(under, z.toImage(130, 180), 1e-9);
	}

	@Test
	public void scaleStaysWithinLimits()
	{
		ImagePlacement p = new ImagePlacement(100, 100, 0, 0, 1, 0, false);
		assertEquals(ImagePlacement.MIN_SIDE / 100, p.scaledAbout(0, 0, 1e-9).scaleX, EPS);
		assertEquals(ImagePlacement.MAX_SIDE / 100, p.scaledAbout(0, 0, 1e9).scaleX, EPS);
	}

	@Test
	public void movedAndResizedImageCoversTheSameArea()
	{
		ImagePlacement p = new ImagePlacement(2000, 1000, 50, 60, 0.25, 1, true).moved(5, -5);
		assertEquals(55, p.cx, EPS);
		ImagePlacement small = p.forImageSize(1000, 500);
		assertEquals(p.placedWidth(), small.placedWidth(), 1e-9);
		assertEquals(p.placedHeight(), small.placedHeight(), 1e-9);
		assertPoint(p.toLayout(2000, 0)[0], p.toLayout(2000, 0)[1], small.toLayout(1000, 0));
	}

	private static final double[][] SAMPLES = {{0, 0}, {64, 37}, {10.5, 30.25}, {-5, 80}};

	@Test
	public void aUniformPlacementIsTheSameAsEqualScales()
	{
		ImagePlacement u = new ImagePlacement(64, 37, 10, 20, 1.5, 3, true);
		assertEquals(new ImagePlacement(64, 37, 10, 20, 1.5, 1.5, 3, true), u);
		assertEquals(1.5, u.scaleX, EPS);
		assertEquals(1.5, u.scaleY, EPS);
		assertTrue(u.uniform());
		assertEquals(1.5, u.meanScale(), EPS);
	}

	@Test
	public void nonUniformScaleStretchesAlongTheImagesOwnAxes()
	{
		ImagePlacement p = new ImagePlacement(100, 50, 0, 0, 2, 3, 0, false);
		assertPoint(-100, -75, p.toLayout(0, 0));
		assertPoint(100, 75, p.toLayout(100, 50));
		assertEquals(200, p.placedWidth(), EPS);
		assertEquals(150, p.placedHeight(), EPS);
		// turned a quarter, the image's width (stretched 2x) now runs down the layout
		ImagePlacement t = p.rotated();
		assertEquals(150, t.placedWidth(), EPS);
		assertEquals(200, t.placedHeight(), EPS);
		assertPoint(75, -100, t.toLayout(0, 0));
		assertEquals(Math.sqrt(6), p.meanScale(), EPS);
	}

	@Test
	public void inverseUndoesEveryCombinationWhenStretched()
	{
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(64, 37, 120.5, -33.25, 1.7, 0.6, turns, flip);
				for (double[] s : SAMPLES)
				{
					double[] l = p.toLayout(s[0], s[1]);
					assertArrayEquals("turns " + turns + " flip " + flip, s, p.toImage(l[0], l[1]), 1e-9);
				}
				double[] b = p.bounds();
				assertEquals(p.placedWidth(), b[2] - b[0], 1e-9);
				assertEquals(p.placedHeight(), b[3] - b[1], 1e-9);
				assertEquals(p.cx, (b[0] + b[2]) / 2, 1e-9);
				assertEquals(p.cy, (b[1] + b[3]) / 2, 1e-9);
			}
		}
	}

	@Test
	public void rotateAndFlipKeepTheStretchWithThePicture()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 2, 0.5, 0, false);
		assertEquals(p, p.rotated().rotated().rotated().rotated());
		ImagePlacement m = p.rotated().flippedHorizontally();
		assertEquals(2, m.scaleX, EPS);
		assertEquals(0.5, m.scaleY, EPS);
		for (double[] s : SAMPLES)
		{
			double[] a = p.rotated().toLayout(s[0], s[1]);
			double[] b = m.toLayout(s[0], s[1]);
			assertEquals(200 - a[0], b[0], 1e-9);
			assertEquals(a[1], b[1], 1e-9);
		}
	}

	@Test
	public void fitAndFillGoBackToTheImagesOwnProportions()
	{
		double[] box = {10, 20, 110, 420};
		ImagePlacement stretched = new ImagePlacement(200, 100, 0, 0, 3, 0.2, 1, true);
		ImagePlacement fit = stretched.fit(box);
		assertTrue(fit.uniform());
		assertEquals(1, fit.turns);
		assertTrue(fit.flipped);
		assertEquals(100, fit.placedWidth(), 1e-9);
		assertEquals(200, fit.placedHeight(), 1e-9);
		ImagePlacement fill = stretched.fill(box);
		assertTrue(fill.uniform());
		assertEquals(400, fill.placedHeight(), 1e-9);
	}

	@Test
	public void stretchedCoversExactlyTheBoxWhateverTheTurn()
	{
		double[] box = {10, 20, 110, 420};
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(200, 100, 0, 0, 1, turns, flip).stretched(box);
				assertArrayEquals("turns " + turns, box, p.bounds(), 1e-9);
				assertEquals(turns, p.turns);
				assertEquals(flip, p.flipped);
			}
		}
	}

	@Test
	public void draggingAnEdgeMovesOnlyThatEdge()
	{
		int[] edges = {ImagePlacement.LEFT, ImagePlacement.TOP, ImagePlacement.RIGHT, ImagePlacement.BOTTOM};
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1.5, 2, turns, flip);
				double[] b = p.bounds();
				for (int edge : edges)
				{
					// the drag's other axis is ignored on an edge
					ImagePlacement r = p.resized(edge, 13, -21, false);
					double[] want = b.clone();
					if (edge == ImagePlacement.LEFT)
					{
						want[0] += 13;
					}
					else if (edge == ImagePlacement.RIGHT)
					{
						want[2] += 13;
					}
					else if (edge == ImagePlacement.TOP)
					{
						want[1] -= 21;
					}
					else
					{
						want[3] -= 21;
					}
					String at = "edge " + edge + " turns " + turns + " flip " + flip;
					assertArrayEquals(at, want, r.bounds(), 1e-9);
					assertEquals(at, turns, r.turns);
					assertEquals(at, flip, r.flipped);
				}
			}
		}
	}

	@Test
	public void draggingACornerMovesBothOfItsEdges()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1.5, 1.5, 1, false);
		double[] b = p.bounds();
		ImagePlacement r = p.resized(ImagePlacement.BOTTOM | ImagePlacement.RIGHT, 10, 40, false);
		assertArrayEquals(new double[]{b[0], b[1], b[2] + 10, b[3] + 40}, r.bounds(), 1e-9);
		ImagePlacement l = p.resized(ImagePlacement.TOP | ImagePlacement.LEFT, -10, 5, false);
		assertArrayEquals(new double[]{b[0] - 10, b[1] + 5, b[2], b[3]}, l.bounds(), 1e-9);
	}

	@Test
	public void shiftOnACornerKeepsTheProportionsAndTheOppositeCorner()
	{
		for (int turns = 0; turns < 4; turns++)
		{
			ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 2, 1, turns, false);
			double[] b = p.bounds();
			double aspect = (b[2] - b[0]) / (b[3] - b[1]);
			ImagePlacement r = p.resized(ImagePlacement.TOP | ImagePlacement.RIGHT, 30, 2, true);
			double[] nb = r.bounds();
			assertEquals((nb[2] - nb[0]) / (nb[3] - nb[1]), aspect, 1e-9);
			// the bottom-left corner stays put, and the image grew (the larger pull wins)
			assertEquals(b[0], nb[0], 1e-9);
			assertEquals(b[3], nb[3], 1e-9);
			assertEquals(b[2] + 30, nb[2], 1e-9);
			assertEquals(p.scaleX / p.scaleY, r.scaleX / r.scaleY, 1e-9);
		}
	}

	@Test
	public void anEdgeDraggedPastTheOtherStopsAtTheSmallestSize()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 1, 0, false);
		double[] b = p.bounds();
		ImagePlacement r = p.resized(ImagePlacement.LEFT, 500, 0, false);
		double[] nb = r.bounds();
		assertEquals(b[2], nb[2], 1e-9);
		assertEquals(ImagePlacement.MIN_SIDE, nb[2] - nb[0], 1e-9);
		ImagePlacement u = p.resized(ImagePlacement.BOTTOM, 0, -500, false);
		assertEquals(b[1], u.bounds()[1], 1e-9);
		assertEquals(ImagePlacement.MIN_SIDE, u.placedHeight(), 1e-9);
	}

	@Test
	public void wheelZoomKeepsTheStretch()
	{
		ImagePlacement p = new ImagePlacement(64, 37, 100, 200, 2, 0.5, 1, true);
		double[] under = p.toImage(130, 180);
		ImagePlacement z = p.scaledAbout(130, 180, 1.25);
		assertEquals(2.5, z.scaleX, EPS);
		assertEquals(0.625, z.scaleY, EPS);
		assertArrayEquals(under, z.toImage(130, 180), 1e-9);
	}

	@Test
	public void aStretchedImageResizedCoversTheSameArea()
	{
		ImagePlacement p = new ImagePlacement(2000, 1000, 50, 60, 0.25, 0.1, 1, true);
		ImagePlacement small = p.forImageSize(1000, 500);
		assertEquals(p.placedWidth(), small.placedWidth(), 1e-9);
		assertEquals(p.placedHeight(), small.placedHeight(), 1e-9);
		assertPoint(p.toLayout(2000, 0)[0], p.toLayout(2000, 0)[1], small.toLayout(1000, 0));
	}
}
