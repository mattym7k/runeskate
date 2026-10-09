package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import javax.imageio.ImageIO;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Ids, names, size limits, reading the picked image and the template. */
public class CustomDesignRulesTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void idsAreCustomAndEightHexDigits()
	{
		Random r = new Random(1);
		for (int i = 0; i < 100; i++)
		{
			String id = CustomDesignRules.newId(r);
			assertTrue(id, CustomDesignRules.isId(id));
			assertEquals(15, id.length());
		}
		assertFalse(CustomDesignRules.isId("CUSTOM_0a1b2c3d"));
		assertFalse(CustomDesignRules.isId("CUSTOM_0A1B2C3"));
		assertFalse(CustomDesignRules.isId("GRIP_RUNE"));
		assertFalse(CustomDesignRules.isId(null));
	}

	@Test
	public void namesAreShortAndPlain()
	{
		assertNull(CustomDesignRules.nameProblem("My grip"));
		assertNull(CustomDesignRules.nameProblem("  Zezima's-board_2  "));
		assertNull(CustomDesignRules.nameProblem("abcdefghijklmnopqrstuvwx"));
		assertNotNull(CustomDesignRules.nameProblem("abcdefghijklmnopqrstuvwxy"));
		assertNotNull(CustomDesignRules.nameProblem("   "));
		assertNotNull(CustomDesignRules.nameProblem(null));
		assertNotNull(CustomDesignRules.nameProblem("<b>bold</b>"));
		assertNotNull(CustomDesignRules.nameProblem("dot."));
	}

	@Test
	public void bigFilesAndImagesAreRefused()
	{
		assertNull(CustomDesignRules.fileProblem(20L * 1024 * 1024));
		assertNotNull(CustomDesignRules.fileProblem(20L * 1024 * 1024 + 1));
		assertNotNull(CustomDesignRules.fileProblem(0));
		assertNull(CustomDesignRules.imageProblem(4096, 4096));
		assertNotNull(CustomDesignRules.imageProblem(4097, 10));
		assertNotNull(CustomDesignRules.imageProblem(10, 4097));
	}

	@Test
	public void theLayoutMatchesTheToolsFile() throws Exception
	{
		com.google.gson.JsonObject parts;
		try (java.io.Reader r = new java.io.InputStreamReader(
			getClass().getResourceAsStream("/com/gielinorskate/design_layout.json"), "UTF-8"))
		{
			parts = new com.google.gson.JsonParser().parse(r).getAsJsonObject().getAsJsonObject("parts");
		}
		for (DesignPart part : DesignPart.values())
		{
			com.google.gson.JsonObject o = parts.getAsJsonObject(part.key);
			DesignLayout.Part l = DesignLayout.bundled().of(part);
			assertEquals(o.getAsJsonArray("size").get(0).getAsInt(), l.width);
			assertEquals(o.getAsJsonArray("size").get(1).getAsInt(), l.height);
			assertEquals(o.getAsJsonObject("blur").get("high").getAsInt(), l.blur(true));
			assertEquals(o.getAsJsonObject("blur").get("low").getAsInt(), l.blur(false));
		}
	}

	@Test
	public void keptCopiesFitTheStoredSize()
	{
		assertArrayEquals(new int[]{1024, 512}, CustomDesignRules.storedSize(4000, 2000));
		assertArrayEquals(new int[]{300, 820}, CustomDesignRules.storedSize(300, 820));
		assertArrayEquals(new int[]{1, 1024}, CustomDesignRules.storedSize(2, 4096));
	}

	private Filepath file(String name) throws Exception
	{
		return Filepath.Unchecked.getRooted(tmp.getRoot().toPath()).joinSegment(name);
	}

	@Test
	public void readsThePickedImageAndDownscalesIt() throws Exception
	{
		BufferedImage big = new BufferedImage(2048, 100, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 2048; x++)
		{
			for (int y = 0; y < 100; y++)
			{
				big.setRGB(x, y, x % 2 == 0 ? 0xFF0000 : 0x0000FF);
			}
		}
		ImageIO.write(big, "png", new File(tmp.getRoot(), "big.png"));
		DesignImages.Picked p = DesignImages.read(file("big.png"));
		assertEquals(2048, p.sourceWidth);
		assertEquals(1024, p.image.getWidth());
		assertEquals(50, p.image.getHeight());
		// red and blue columns averaged
		assertEquals(0xFF80_0080, p.image.getRGB(10, 10));
	}

	@Test
	public void tooLargeOrNotAnImageIsRefused() throws Exception
	{
		ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", new File(tmp.getRoot(), "w.png"));
		Files.write(new File(tmp.getRoot(), "x.png").toPath(), "not an image".getBytes());
		for (String name : new String[]{"w.png", "x.png"})
		{
			try
			{
				DesignImages.read(file(name));
				fail(name);
			}
			catch (DesignImages.Refused expected)
			{
				assertFalse(expected.getMessage().isEmpty());
			}
		}
	}

	@Test
	public void aGifGivesItsFirstFrame() throws Exception
	{
		java.awt.image.IndexColorModel cm = new java.awt.image.IndexColorModel(8, 2, new byte[]{0, -1},
			new byte[]{0, -1}, new byte[]{0, -1});
		BufferedImage img = new BufferedImage(16, 12, BufferedImage.TYPE_BYTE_INDEXED, cm);
		img.getRaster().setSample(1, 1, 0, 1);
		ImageIO.write(img, "gif", new File(tmp.getRoot(), "a.gif"));
		DesignImages.Picked p = DesignImages.read(file("a.gif"));
		assertEquals(16, p.image.getWidth());
		assertEquals(0xFFFFFFFF, p.image.getRGB(1, 1));
		assertEquals(0xFF000000, p.image.getRGB(0, 0));
	}

	@Test
	public void templateShowsThePartOutlineAndBolts()
	{
		com.gielinorskate.render.BakedBoardGeometry.Mesh grip =
			com.gielinorskate.render.BakedBoardGeometry.sharedBoard(true)[DesignPart.GRIP.index];
		DesignLayout.Part layout = DesignLayout.bundled().of(DesignPart.GRIP);
		PartOutline o = PartOutline.of(grip, layout, true);
		BufferedImage t = DesignTemplate.render(DesignPart.GRIP, o);
		assertEquals(layout.width, t.getWidth());
		assertEquals(layout.height, t.getHeight());
		assertEquals(DesignTemplate.OUTSIDE.getRGB(), t.getRGB(2, 2));
		double[] bolt = o.bolts.get(0);
		assertEquals(DesignTemplate.LINES.getRGB(), t.getRGB((int) bolt[0], (int) bolt[1]));
		// somewhere off the centre line, between the trucks, is plain part
		double[] b = o.bounds();
		assertEquals(DesignTemplate.INSIDE.getRGB(), t.getRGB((int) (b[0] + (b[2] - b[0]) * 0.3),
			(int) ((b[1] + b[3]) / 2)));
	}
}
