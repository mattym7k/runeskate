package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gielinorskate.progression.DesignPart;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CustomDesignStoreTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private Path dir;
	private CustomDesignStore store;

	@Before
	public void setUp() throws Exception
	{
		dir = tmp.newFolder("designs").toPath();
		store = new CustomDesignStore(Filepath.Unchecked.getRooted(dir));
	}

	private static BufferedImage image(int w, int h)
	{
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				img.setRGB(x, y, 0xFF000000 | x * 20 << 16 | y * 20 << 8 | 0x40);
			}
		}
		return img;
	}

	private static List<CustomDesignStore.Stored> loadAll(CustomDesignStore s)
	{
		List<CustomDesignStore.Stored> out = new ArrayList<>();
		s.loadEach(out::add);
		return out;
	}

	private static CustomDesign design(String id, long created)
	{
		return new CustomDesign(id, "My grip", DesignPart.GRIP, created, 2000, 1000,
			new ImagePlacement(8, 4, 180.5, 529.25, 12.5, 3, true));
	}

	private void save(String id, long created) throws Exception
	{
		store.save(design(id, created), image(8, 4), new int[]{1, 2, 3, 4, 5, 6}, new int[]{7, 8, 9});
	}

	@Test
	public void savedDesignsReadBackTheSame() throws Exception
	{
		save("CUSTOM_0000000A", 5);
		List<CustomDesignStore.Stored> all = loadAll(store);
		assertEquals(1, all.size());
		CustomDesignStore.Stored s = all.get(0);
		CustomDesign d = s.design;
		assertEquals("CUSTOM_0000000A", d.id);
		assertEquals("My grip", d.name);
		assertEquals(DesignPart.GRIP, d.part);
		assertEquals(5, d.created);
		assertEquals(2000, d.sourceWidth);
		assertEquals(1000, d.sourceHeight);
		assertEquals(design("CUSTOM_0000000A", 5).placement, d.placement);
		assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, s.high);
		assertArrayEquals(new int[]{7, 8, 9}, s.low);
		assertEquals(8, s.image.getWidth());
		assertEquals(image(8, 4).getRGB(3, 2), s.image.getRGB(3, 2));
		// nothing temporary is left behind
		try (java.util.stream.Stream<Path> files = Files.list(dir))
		{
			assertEquals(4, files.count());
		}
	}

	@Test
	public void renameKeepsTheImageAndColours() throws Exception
	{
		save("CUSTOM_0000000A", 5);
		store.save(design("CUSTOM_0000000A", 5).withName("Flames"), null, null, null);
		CustomDesignStore.Stored s = loadAll(store).get(0);
		assertEquals("Flames", s.design.name);
		assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, s.high);
	}

	@Test
	public void oldestFirstAndDeletedOnesGone() throws Exception
	{
		save("CUSTOM_0000000B", 9);
		save("CUSTOM_0000000A", 5);
		save("CUSTOM_0000000C", 7);
		List<CustomDesignStore.Stored> all = loadAll(store);
		assertEquals("CUSTOM_0000000A", all.get(0).design.id);
		assertEquals("CUSTOM_0000000C", all.get(1).design.id);
		store.delete("CUSTOM_0000000C");
		assertEquals(2, loadAll(store).size());
		assertFalse(Files.exists(dir.resolve("CUSTOM_0000000C.png")));
	}

	@Test
	public void corruptOrIncompleteDesignsAreSkipped() throws Exception
	{
		save("CUSTOM_00000001", 1);
		save("CUSTOM_00000002", 2);
		save("CUSTOM_00000003", 3);
		save("CUSTOM_00000004", 4);
		// broken JSON, a missing image, a file naming another id, and stray files
		Files.write(dir.resolve("CUSTOM_00000001.json"), "{\"id\": ".getBytes(StandardCharsets.UTF_8));
		Files.delete(dir.resolve("CUSTOM_00000002.png"));
		Files.write(dir.resolve("CUSTOM_00000003.json"), CustomDesignStore.toJson(design("CUSTOM_00000009", 3))
			.getBytes(StandardCharsets.UTF_8));
		Files.write(dir.resolve("notes.json"), "{}".getBytes(StandardCharsets.UTF_8));
		Files.write(dir.resolve("CUSTOM_00000005.png"), new byte[]{1, 2, 3});
		List<CustomDesignStore.Stored> all = loadAll(store);
		assertEquals(1, all.size());
		assertEquals("CUSTOM_00000004", all.get(0).design.id);
	}

	@Test
	public void brokenColoursComeBackAsNullToBakeAgain() throws Exception
	{
		save("CUSTOM_00000001", 1);
		Files.write(dir.resolve("CUSTOM_00000001.high.rgb"), new byte[]{'R', 'S', 'K', 'C', 1});
		Files.delete(dir.resolve("CUSTOM_00000001.low.rgb"));
		CustomDesignStore.Stored s = loadAll(store).get(0);
		assertNull(s.high);
		assertNull(s.low);
	}

	@Test
	public void badFieldsAreRefused() throws Exception
	{
		String good = CustomDesignStore.toJson(design("CUSTOM_00000001", 1));
		assertTrue(good.contains("\"scale\": 12.5"));
		for (String bad : new String[]{
			good.replace("\"grip\"", "\"tail\""),
			good.replace("My grip", "<script>"),
			good.replace("CUSTOM_00000001", "GRIP_RUNE"),
			good.replace("\"scale\": 12.5", "\"scale\": -1"),
			good.replace("\"version\": 1", "\"version\": 99")})
		{
			save("CUSTOM_00000001", 1);
			assertEquals(1, loadAll(store).size());
			Files.write(dir.resolve("CUSTOM_00000001.json"), bad.getBytes(StandardCharsets.UTF_8));
			assertTrue(bad, loadAll(store).isEmpty());
		}
	}

	@Test
	public void aStretchedPlacementReadsBackTheSame() throws Exception
	{
		CustomDesign stretched = new CustomDesign("CUSTOM_0000000B", "My grip", DesignPart.GRIP, 5, 2000, 1000,
			new ImagePlacement(8, 4, 180.5, 529.25, 12.5, 40.25, 1, true));
		String json = CustomDesignStore.toJson(stretched);
		assertTrue(json.contains("\"scaleX\": 12.5"));
		assertTrue(json.contains("\"scaleY\": 40.25"));
		CustomDesign back = CustomDesignStore.parse(new java.io.StringReader(json));
		assertEquals(stretched.placement, back.placement);
		// a uniform placement is written as before the stretch, so older versions read it unchanged
		String uniform = CustomDesignStore.toJson(design("CUSTOM_00000001", 1));
		assertFalse(uniform.contains("scaleX"));
		for (String bad : new String[]{json.replace("\"scaleX\": 12.5", "\"scaleX\": -1"),
			json.replace("\"scaleY\": 40.25", "\"scaleY\": 0")})
		{
			try
			{
				CustomDesignStore.parse(new java.io.StringReader(bad));
				fail(bad);
			}
			catch (IllegalArgumentException expected)
			{
				// refused
			}
		}
	}

	@Test
	public void aDesignSavedWithOneScaleLoadsUniform() throws Exception
	{
		String old = "{\"version\": 1, \"id\": \"CUSTOM_972A6D43\", \"name\": \"Test Grip\", \"part\": \"grip\","
			+ " \"created\": 1791328912886, \"source\": {\"width\": 350, \"height\": 938}, \"placement\":"
			+ " {\"imageWidth\": 350, \"imageHeight\": 938, \"cx\": 180.57756872683626, \"cy\": 545.7597344722661,"
			+ " \"scale\": 1.185888159249637, \"turns\": 0, \"flipped\": false}}";
		ImagePlacement p = CustomDesignStore.parse(new java.io.StringReader(old)).placement;
		assertEquals(new ImagePlacement(350, 938, 180.57756872683626, 545.7597344722661, 1.185888159249637, 0, false),
			p);
		assertTrue(p.uniform());
	}

	@Test
	public void atMostTheLimitAreLoaded() throws Exception
	{
		for (int i = 0; i < CustomDesignRules.MAX_DESIGNS + 2; i++)
		{
			save(String.format("CUSTOM_%08X", i), i);
		}
		List<CustomDesignStore.Stored> all = loadAll(store);
		assertEquals(CustomDesignRules.MAX_DESIGNS, all.size());
		assertEquals("CUSTOM_00000000", all.get(0).design.id);
	}

	@Test
	public void theKeptImageIsReadAgainWhenAsked() throws Exception
	{
		save("CUSTOM_0000000A", 5);
		CustomDesign d = design("CUSTOM_0000000A", 5);
		BufferedImage img = store.image(d);
		assertEquals(8, img.getWidth());
		assertEquals(4, img.getHeight());
		assertEquals(image(8, 4).getRGB(5, 1), img.getRGB(5, 1));
		// not the size its placement was made for, or gone: none
		assertNull(store.image(new CustomDesign(d.id, d.name, d.part, 5, 2000, 1000,
			new ImagePlacement(9, 4, 180.5, 529.25, 12.5, 3, true))));
		Files.delete(dir.resolve("CUSTOM_0000000A.png"));
		assertNull(store.image(d));
	}

	@Test
	public void designsComeOneAtATimeOldestFirstUpToTheLimit() throws Exception
	{
		for (int i = 0; i < CustomDesignRules.MAX_DESIGNS + 2; i++)
		{
			save(String.format("CUSTOM_%08X", i), i);
		}
		// a broken one does not count towards the limit
		Files.delete(dir.resolve("CUSTOM_00000001.png"));
		List<String> ids = new ArrayList<>();
		store.loadEach(s -> ids.add(s.design.id));
		assertEquals(CustomDesignRules.MAX_DESIGNS, ids.size());
		assertEquals("CUSTOM_00000000", ids.get(0));
		assertEquals("CUSTOM_00000002", ids.get(1));
		assertEquals(String.format("CUSTOM_%08X", CustomDesignRules.MAX_DESIGNS), ids.get(ids.size() - 1));
	}

	@Test
	public void aFailedWriteLeavesNoTemporaryFile() throws Exception
	{
		// the JSON can't be moved into place: a folder (not empty) is in the way
		Path blocker = dir.resolve("CUSTOM_0000000A.json");
		Files.createDirectories(blocker);
		Files.write(blocker.resolve("x"), new byte[]{1});
		try
		{
			save("CUSTOM_0000000A", 5);
			fail("saved over a folder");
		}
		catch (IOException expected)
		{
			// refused
		}
		assertFalse(Files.exists(dir.resolve("CUSTOM_0000000A.json.tmp")));
	}

	@Test
	public void temporaryFilesLeftBehindAreSweptAtLoad() throws Exception
	{
		save("CUSTOM_0000000A", 5);
		Files.write(dir.resolve("CUSTOM_0000000B.png.tmp"), new byte[]{1, 2});
		Files.write(dir.resolve("CUSTOM_0000000A.json.tmp"), new byte[]{3});
		assertEquals(1, loadAll(store).size());
		assertFalse(Files.exists(dir.resolve("CUSTOM_0000000B.png.tmp")));
		assertFalse(Files.exists(dir.resolve("CUSTOM_0000000A.json.tmp")));
	}

	@Test
	public void aMissingFolderHasNoDesigns()
	{
		CustomDesignStore none = new CustomDesignStore(Filepath.Unchecked.getRooted(dir.resolve("nothing")));
		assertTrue(loadAll(none).isEmpty());
	}
}
