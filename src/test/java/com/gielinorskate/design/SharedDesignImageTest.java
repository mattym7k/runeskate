package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.Test;

public class SharedDesignImageTest
{
	private static final DesignLayout LAYOUTS = DesignLayout.bundled();

	private static CustomBake.Source source(BufferedImage img)
	{
		return new CustomBake.Source(CustomBakeTest.argb(img), img.getWidth(), img.getHeight(), 0);
	}

	private static BufferedImage solid(int w, int h, int rgb)
	{
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				img.setRGB(x, y, rgb);
			}
		}
		return img;
	}

	private static BufferedImage noise(int w, int h, long seed)
	{
		Random r = new Random(seed);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				img.setRGB(x, y, r.nextInt(0x1000000));
			}
		}
		return img;
	}

	private static ImagePlacement covering(BufferedImage img, DesignLayout.Part layout)
	{
		return ImagePlacement.initial(img.getWidth(), img.getHeight(),
			new double[]{0, 0, layout.width, layout.height});
	}

	private static SharedDesignImage.Encoded encode(BufferedImage img, DesignPart part)
	{
		DesignLayout.Part layout = LAYOUTS.of(part);
		return SharedDesignImage.encode(source(img), part, layout, covering(img, layout));
	}

	@Test
	public void picturesAreAboutInGameSizeInTheLayoutsShape()
	{
		for (DesignPart p : DesignPart.values())
		{
			SharedDesignImage.Encoded e = encode(solid(64, 64, 0x336699), p);
			assertNotNull(p.key, e);
			DesignLayout.Part layout = LAYOUTS.of(p);
			// a flat picture compresses well: full size, the long side at the part's most
			assertEquals(p.key, SharedDesignImage.maxSide(p), Math.max(e.width, e.height));
			assertEquals(p.key, layout.width / (double) layout.height, e.width / (double) e.height, 0.05);
			assertTrue(SharedDesignImage.sizeAllowed(p, e.width, e.height));
		}
		assertEquals(96, SharedDesignImage.maxSide(DesignPart.GRIP));
		assertEquals(96, SharedDesignImage.maxSide(DesignPart.DECK));
		assertEquals(40, SharedDesignImage.maxSide(DesignPart.WHEELS));
	}

	@Test
	public void noisyPicturesAreMadeSmallerUntilTheyFit()
	{
		for (DesignPart p : DesignPart.values())
		{
			SharedDesignImage.Encoded e = encode(noise(400, 400, 7), p);
			assertNotNull(p.key, e);
			assertTrue(p.key + " " + e.base64.length(), e.base64.length() <= SharedDesignImage.MAX_ENCODED);
			assertTrue(e.png.length <= SharedDesignImage.MAX_BYTES);
			assertTrue(SharedDesignImage.sizeAllowed(p, e.width, e.height));
		}
		// noise at 96 px does not fit 8 KB: the grip went smaller
		SharedDesignImage.Encoded grip = encode(noise(400, 400, 7), DesignPart.GRIP);
		assertTrue(grip.height < 96);
	}

	@Test
	public void theRenderFollowsThePlacement()
	{
		// left half red, right half blue, placed over the whole deck layout
		BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 100; y++)
		{
			for (int x = 0; x < 100; x++)
			{
				img.setRGB(x, y, x < 50 ? 0xff0000 : 0x0000ff);
			}
		}
		DesignLayout.Part deck = LAYOUTS.of(DesignPart.DECK);
		ImagePlacement p = new ImagePlacement(100, 100, deck.width / 2.0, deck.height / 2.0,
			deck.height / 100.0, 0, false);
		BufferedImage out = SharedDesignImage.render(source(img), deck, p, 35, 96);
		assertEquals(0xff0000, out.getRGB(2, 48) & 0xffffff);
		assertEquals(0x0000ff, out.getRGB(32, 48) & 0xffffff);
		// mirrored, the sides swap
		BufferedImage flipped = SharedDesignImage.render(source(img), deck, p.flippedHorizontally(), 35, 96);
		assertEquals(0x0000ff, flipped.getRGB(2, 48) & 0xffffff);
		// stretched to twice the deck's width with its left edge at the deck's: the red half covers it all
		ImagePlacement wide = new ImagePlacement(100, 100, deck.width, deck.height / 2.0, 2.0 * deck.width / 100,
			deck.height / 100.0, 0, false);
		BufferedImage stretched = SharedDesignImage.render(source(img), deck, wide, 35, 96);
		assertEquals(0xff0000, stretched.getRGB(2, 48) & 0xffffff);
		assertEquals(0xff0000, stretched.getRGB(30, 48) & 0xffffff);
	}

	@Test
	public void decodeRoundTripsTheEncodedPicture()
	{
		BufferedImage img = noise(60, 60, 3);
		SharedDesignImage.Encoded e = encode(img, DesignPart.WHEELS);
		BufferedImage back = SharedDesignImage.decode(e.png, e.width, e.height);
		assertNotNull(back);
		BufferedImage again = SharedDesignImage.decode(SharedDesignImage.png(back), e.width, e.height);
		assertArrayEquals(back.getRGB(0, 0, e.width, e.height, null, 0, e.width),
			again.getRGB(0, 0, e.width, e.height, null, 0, e.width));
	}

	@Test
	public void decodeRefusesAnythingButAPngOfTheOfferedSize() throws Exception
	{
		SharedDesignImage.Encoded e = encode(solid(10, 10, 0x123456), DesignPart.WHEELS);
		assertNull(SharedDesignImage.decode(e.png, e.width + 1, e.height));
		assertNull(SharedDesignImage.decode(e.png, e.width, e.height - 1));
		assertNull(SharedDesignImage.decode(new byte[]{1, 2, 3, 4}, 4, 4));
		assertNull(SharedDesignImage.decode(null, 4, 4));
		assertNull(SharedDesignImage.decode(new byte[SharedDesignImage.MAX_BYTES + 1], 4, 4));
		// a GIF (or any other format) is not read, even of the right size
		ByteArrayOutputStream gif = new ByteArrayOutputStream();
		ImageIO.write(solid(e.width, e.height, 0x123456), "gif", gif);
		assertNull(SharedDesignImage.decode(gif.toByteArray(), e.width, e.height));
	}

	@Test
	public void aTinyFileClaimingAHugePictureIsRefusedBeforeDecoding()
	{
		// a valid 1x1 PNG whose header is rewritten to claim 30000 x 30000 (the CRC is then wrong too)
		byte[] png = SharedDesignImage.png(solid(1, 1, 0));
		assertTrue(png.length < 200);
		// IHDR's width and height follow the 8-byte signature, the chunk's length and its type
		for (int i = 0; i < 2; i++)
		{
			int at = 16 + i * 4;
			png[at] = 0;
			png[at + 1] = 0;
			png[at + 2] = (byte) (30000 >> 8);
			png[at + 3] = (byte) 30000;
		}
		assertNull(SharedDesignImage.decode(png, 40, 40));
	}

	@Test
	public void sizesOutsideThePartAreNotAllowed()
	{
		assertFalse(SharedDesignImage.sizeAllowed(DesignPart.WHEELS, 41, 40));
		assertFalse(SharedDesignImage.sizeAllowed(DesignPart.GRIP, 33, 97));
		assertFalse(SharedDesignImage.sizeAllowed(DesignPart.DECK, 0, 96));
		assertTrue(SharedDesignImage.sizeAllowed(DesignPart.DECK, 35, 96));
	}

	@Test
	public void aReceivedPictureBakesLikeTheOwnersDesign()
	{
		BakedBoardGeometry.Mesh[] low = BakedBoardGeometry.sharedBoard(false);
		assertNotNull(low);
		for (BakedBoardGeometry.Mesh m : low)
		{
			DesignPart part = null;
			for (DesignPart p : DesignPart.values())
			{
				if (p.index == m.part)
				{
					part = p;
				}
			}
			if (part == null || m.cornerUv == null)
			{
				continue;
			}
			DesignLayout.Part layout = LAYOUTS.of(part);
			BufferedImage red = solid(200, 200, 0xc01010);
			SharedDesignImage.Encoded e = encode(red, part);
			BufferedImage got = SharedDesignImage.decode(e.png, e.width, e.height);
			int[] theirs = SharedDesignImage.bake(got, m, layout);
			int[] own = CustomBake.bake(m, layout, covering(red, layout), source(red), false);
			assertEquals(own.length, theirs.length);
			for (int i = 0; i < own.length; i++)
			{
				assertEquals(part.key + " corner " + i, own[i], theirs[i]);
			}
		}
	}
}
