package com.gielinorskate.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Pictures are encoded and decoded in memory only, whatever ImageIO's global disk cache setting: with the cache on
 * and its folder gone (so any cache file would fail), everything still works.
 */
public class ImageStreamCacheTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private boolean useCache;
	private File cacheDir;

	@Before
	public void cacheToAFolderThatIsGone() throws Exception
	{
		useCache = ImageIO.getUseCache();
		cacheDir = ImageIO.getCacheDirectory();
		File gone = tmp.newFolder("cache");
		ImageIO.setUseCache(true);
		ImageIO.setCacheDirectory(gone);
		Files.delete(gone.toPath());
	}

	@After
	public void restore()
	{
		ImageIO.setCacheDirectory(cacheDir);
		ImageIO.setUseCache(useCache);
	}

	private static BufferedImage picture(int w, int h)
	{
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				img.setRGB(x, y, x * 7 << 16 | y * 5 << 8 | 0x33);
			}
		}
		return img;
	}

	@Test
	public void sharedPicturesEncodeAndDecodeInMemory()
	{
		byte[] png = SharedDesignImage.png(picture(30, 20));
		assertNotNull(png);
		BufferedImage back = SharedDesignImage.decode(png, 30, 20);
		assertNotNull(back);
		assertEquals(picture(30, 20).getRGB(4, 3) & 0xffffff, back.getRGB(4, 3) & 0xffffff);
	}

	@Test
	public void aPickedImageIsReadInMemory() throws Exception
	{
		byte[] png = SharedDesignImage.png(picture(40, 30));
		Path file = tmp.getRoot().toPath().resolve("picked.png");
		Files.write(file, png);
		DesignImages.Picked p = DesignImages.read(Filepath.Unchecked.getRooted(tmp.getRoot().toPath())
			.joinSegment("picked.png"));
		assertEquals(40, p.sourceWidth);
		assertEquals(30, p.sourceHeight);
		assertNotNull(p.image);
	}

	@Test
	public void keptImagesAreSavedAndReadInMemory() throws Exception
	{
		CustomDesignStore store = new CustomDesignStore(Filepath.Unchecked.getRooted(tmp.newFolder("designs").toPath()));
		CustomDesign d = new CustomDesign("CUSTOM_0000000A", "Mine", DesignPart.DECK, 1, 12, 8,
			new ImagePlacement(12, 8, 10, 10, 1, 0, false));
		store.save(d, picture(12, 8), null, null);
		BufferedImage back = store.image(d);
		assertNotNull(back);
		assertEquals(picture(12, 8).getRGB(5, 6) & 0xffffff, back.getRGB(5, 6) & 0xffffff);
	}
}
