package com.gielinorskate.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DesignImagesTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void bigPicturesDecodeAtMostAboutTwiceTheKeptSize()
	{
		int twice = 2 * CustomDesignRules.STORED_MAX_SIDE;
		assertEquals(1, DesignImages.subsampling(100, 80));
		assertEquals(1, DesignImages.subsampling(twice, twice));
		assertEquals(2, DesignImages.subsampling(twice + 1, 10));
		assertEquals(2, DesignImages.subsampling(10, CustomDesignRules.MAX_SIDE));
		// whatever the picture, the decoded long side is at most twice the kept one
		for (int side = 1; side <= CustomDesignRules.MAX_SIDE; side += 97)
		{
			int s = DesignImages.subsampling(side, 1);
			assertTrue((side + s - 1) / s <= twice);
		}
	}

	@Test
	public void aBigPictureKeepsItsSizeAndIsKeptAtTheKeptSize() throws Exception
	{
		BufferedImage img = new BufferedImage(3001, 20, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 3001; x++)
		{
			for (int y = 0; y < 20; y++)
			{
				img.setRGB(x, y, x < 1500 ? 0xff0000 : 0x0000ff);
			}
		}
		ImageIO.write(img, "png", tmp.getRoot().toPath().resolve("big.png").toFile());
		DesignImages.Picked p = DesignImages.read(Filepath.Unchecked.getRooted(tmp.getRoot().toPath())
			.joinSegment("big.png"));
		assertEquals(3001, p.sourceWidth);
		assertEquals(20, p.sourceHeight);
		int[] kept = CustomDesignRules.storedSize(3001, 20);
		assertEquals(kept[0], p.image.getWidth());
		assertEquals(kept[1], p.image.getHeight());
		assertEquals(0xff0000, p.image.getRGB(10, 3) & 0xffffff);
		assertEquals(0x0000ff, p.image.getRGB(kept[0] - 10, 3) & 0xffffff);
		Files.delete(tmp.getRoot().toPath().resolve("big.png"));
	}
}
