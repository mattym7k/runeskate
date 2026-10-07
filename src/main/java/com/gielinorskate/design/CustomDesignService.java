package com.gielinorskate.design;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.render.BakedBoardGeometry;
import com.gielinorskate.render.BakedBoardModel;
import com.gielinorskate.render.DesignColours;
import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;

/**
 * Players' own designs in the running plugin: loads them from the plugin's folder at start, opens the editor for
 * "+ Custom" and Edit, bakes and saves on Save, renames and deletes. Threads: file IO and baking on RuneLite's
 * executor; Swing (choosers, the editor, prompts) on the EDT; the board's colours change on the client thread
 * (through {@link ProgressionService}). The designs map is swapped whole, so any thread may read it.
 */
@Slf4j
@Singleton
public class CustomDesignService
{
	/** A revision no two edits share, even across plugin restarts in one client session. */
	private static final AtomicLong REVISIONS = new AtomicLong(System.currentTimeMillis());

	private final ScheduledExecutorService executor;
	private final ClientThread clientThread;
	private final ProgressionService progression;
	private final BoardDesigns designs = BoardDesigns.bundled();
	private final DesignLayout layouts = DesignLayout.bundled();
	private final Random random = new SecureRandom();

	/**
	 * One loaded design. Its kept image is not held (up to 4 MB each): it is read again from the store for Edit.
	 */
	private static final class Entry
	{
		final CustomDesign design;
		final BoardDesign board;
		final BufferedImage thumb;
		/** Its small picture for party members, or null when it couldn't be made. */
		final SharedDesignImage.Encoded shared;

		Entry(CustomDesign design, BoardDesign board, BufferedImage thumb, SharedDesignImage.Encoded shared)
		{
			this.design = design;
			this.board = board;
			this.thumb = thumb;
			this.shared = shared;
		}
	}

	private volatile Map<String, Entry> entries = Collections.emptyMap();
	private volatile CustomDesignStore store;
	private volatile Consumer<Map<String, BufferedImage>> thumbsListener;
	/** Each part's outline, made once (executor). */
	private final Map<DesignPart, PartOutline> outlines = new EnumMap<>(DesignPart.class);
	/** The open editor (EDT). */
	private DesignEditorDialog editor;
	private volatile boolean running;

	@Inject
	CustomDesignService(ScheduledExecutorService executor, ClientThread clientThread, ProgressionService progression)
	{
		this.executor = executor;
		this.clientThread = clientThread;
		this.progression = progression;
	}

	/**
	 * Loads the saved designs (in the background) from {@code pluginDir}/designs, a supplier because finding the
	 * plugin's folder touches the disk.
	 */
	public void startUp(PluginDir pluginDir, Consumer<Map<String, BufferedImage>> thumbsListener)
	{
		running = true;
		this.thumbsListener = thumbsListener;
		executor.execute(() ->
		{
			try
			{
				store = new CustomDesignStore(pluginDir.get().joinSegment("designs"));
				loadAll();
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("RuneSkate: custom designs could not be loaded", e);
			}
		});
	}

	/** The plugin's data folder (RuneLite's Plugin#getPluginDirectory). */
	public interface PluginDir
	{
		Filepath get() throws IOException;
	}

	public void shutDown()
	{
		running = false;
		thumbsListener = null;
		SwingUtilities.invokeLater(this::closeEditor);
		Map<String, Entry> old = entries;
		entries = Collections.emptyMap();
		designs.setCustom(Collections.emptyList());
		for (String id : old.keySet())
		{
			DesignColours.unregister(id);
		}
	}

	private static BakedBoardGeometry.Mesh mesh(DesignPart part, boolean high)
	{
		BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(high);
		if (parts == null)
		{
			return null;
		}
		for (BakedBoardGeometry.Mesh m : parts)
		{
			if (m.part == part.index)
			{
				return m;
			}
		}
		return null;
	}

	/** Executor: the part's outline, made once. */
	private synchronized PartOutline outline(DesignPart part)
	{
		PartOutline o = outlines.get(part);
		if (o == null)
		{
			BakedBoardGeometry.Mesh m = mesh(part, true);
			if (m == null)
			{
				return null;
			}
			o = PartOutline.of(m, layouts.of(part), part != DesignPart.WHEELS);
			outlines.put(part, o);
		}
		return o;
	}

	/** Executor: reads every saved design, bakes any whose colours are missing or stale, and shows them. */
	private void loadAll()
	{
		Map<String, Entry> loaded = new LinkedHashMap<>();
		// one at a time: each kept image is let go once its design is prepared
		store.loadEach(s ->
		{
			Entry e = prepare(s.design, s.image, s.high, s.low);
			if (e != null)
			{
				loaded.put(e.design.id, e);
			}
		});
		publish(loaded);
		log.debug("RuneSkate: {} custom designs loaded", loaded.size());
	}

	/**
	 * Executor: a design ready to draw: its colours (baked again if missing or made for another geometry, and saved),
	 * registered, its thumbnail and its party picture. {@code image} is only used here, not kept. Null when the
	 * board's geometry isn't there.
	 */
	private Entry prepare(CustomDesign d, BufferedImage image, int[] high, int[] low)
	{
		BakedBoardGeometry.Mesh hi = mesh(d.part, true);
		BakedBoardGeometry.Mesh lo = mesh(d.part, false);
		if (hi == null || lo == null)
		{
			return null;
		}
		if (!running)
		{
			// stopped meanwhile: nothing more is registered
			return null;
		}
		boolean stale = high == null || high.length != hi.cornerRgb.length || low == null
			|| low.length != lo.cornerRgb.length;
		if (stale)
		{
			CustomBake.Source src = source(image, d.part);
			DesignLayout.Part layout = layouts.of(d.part);
			high = CustomBake.bake(hi, layout, d.placement, src, true);
			low = CustomBake.bake(lo, layout, d.placement, src, false);
			try
			{
				store.save(d, null, high, low);
			}
			catch (IOException e)
			{
				log.warn("RuneSkate: custom design {} colours could not be saved", d.id, e);
			}
		}
		DesignColours.register(d.id, high, low);
		return new Entry(d, d.asBoardDesign(REVISIONS.incrementAndGet()), ThumbRender.thumbnail(d.part, hi, high),
			sharedPicture(d, image));
	}

	/** Executor: the design's small picture for party members (made whether or not it is ever shared), or null. */
	private SharedDesignImage.Encoded sharedPicture(CustomDesign d, BufferedImage image)
	{
		try
		{
			SharedDesignImage.Encoded e = SharedDesignImage.encode(source(image, d.part), d.part, layouts.of(d.part),
				d.placement);
			if (e == null)
			{
				log.debug("RuneSkate: custom design {} has no party picture under the size cap", d.id);
			}
			return e;
		}
		catch (RuntimeException e)
		{
			log.warn("RuneSkate: custom design {} party picture failed", d.id, e);
			return null;
		}
	}

	/** Custom design {@code id}'s small picture for party members, or null (unknown, or none could be made). */
	public SharedDesignImage.Encoded sharedPicture(String id)
	{
		Entry e = id == null ? null : entries.get(id);
		return e == null ? null : e.shared;
	}

	private static CustomBake.Source source(BufferedImage image, DesignPart part)
	{
		int w = image.getWidth();
		int h = image.getHeight();
		return new CustomBake.Source(image.getRGB(0, 0, w, h, null, 0, w), w, h, DesignEditorPanel.backdrop(part));
	}

	/**
	 * Any thread: the catalogue, the panel and the board get these designs. Stopped meanwhile (work that was still
	 * running at shutdown), their colours are let go instead.
	 */
	private void publish(Map<String, Entry> now)
	{
		if (!running)
		{
			for (String id : now.keySet())
			{
				DesignColours.unregister(id);
				BakedBoardModel.forgetDesign(id);
			}
			return;
		}
		entries = Collections.unmodifiableMap(now);
		List<BoardDesign> list = new ArrayList<>();
		Map<String, BufferedImage> thumbs = new LinkedHashMap<>();
		for (Entry e : now.values())
		{
			list.add(e.board);
			thumbs.put(e.design.id, e.thumb);
		}
		designs.setCustom(list);
		Consumer<Map<String, BufferedImage>> l = thumbsListener;
		if (l != null)
		{
			SwingUtilities.invokeLater(() -> l.accept(thumbs));
		}
		clientThread.invoke(progression::designsChanged);
	}

	/** How many custom designs there are. */
	public int count()
	{
		return entries.size();
	}

	// ---- panel actions (EDT) ----

	/** "+ Custom": pick an image, then edit it. */
	public void add(DesignPart part, Component from)
	{
		if (store == null)
		{
			message(from, "Custom designs aren't ready yet. Try again in a moment.");
			return;
		}
		if (bringEditorToFront())
		{
			return;
		}
		if (count() >= CustomDesignRules.MAX_DESIGNS)
		{
			message(from, "You have " + CustomDesignRules.MAX_DESIGNS + " custom designs, the most RuneSkate keeps. "
				+ "Delete one (right-click it) to make room.");
			return;
		}
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Pick an image for your " + part.key)
			.addExtensionFilter("Images (PNG, JPG, GIF, BMP)", CustomDesignRules.EXTENSIONS);
		List<Filepath> picked = chooser.showDialog(window(from));
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath file = picked.get(0);
		executor.execute(() ->
		{
			DesignImages.Picked img;
			try
			{
				img = DesignImages.read(file);
			}
			catch (DesignImages.Refused r)
			{
				SwingUtilities.invokeLater(() -> message(from, r.getMessage()));
				return;
			}
			PartOutline outline = outline(part);
			if (outline == null)
			{
				SwingUtilities.invokeLater(() -> message(from, "The board couldn't be loaded."));
				return;
			}
			ImagePlacement start = ImagePlacement.initial(img.image.getWidth(), img.image.getHeight(),
				outline.bounds());
			SwingUtilities.invokeLater(() -> openEditor(from, part, outline, img.image, start,
				CustomDesignRules.defaultName(part), null, img.sourceWidth, img.sourceHeight));
		});
	}

	/** Edit: the kept image read again (executor), then the editor with it and its placement. */
	public void edit(BoardDesign design, Component from)
	{
		Entry e = entries.get(design.id);
		if (e == null || bringEditorToFront())
		{
			return;
		}
		executor.execute(() ->
		{
			BufferedImage image = store.image(e.design);
			if (image == null)
			{
				log.warn("RuneSkate: custom design {} image could not be read for editing", e.design.id);
				SwingUtilities.invokeLater(() -> message(from, "The design's image couldn't be read."));
				return;
			}
			PartOutline outline = outline(e.design.part);
			if (outline != null)
			{
				SwingUtilities.invokeLater(() -> openEditor(from, e.design.part, outline, image, e.design.placement,
					e.design.name, e.design, e.design.sourceWidth, e.design.sourceHeight));
			}
		});
	}

	/** Rename: asks for the new name (checked), then saves it. */
	public void rename(BoardDesign design, Component from)
	{
		Entry e = entries.get(design.id);
		if (e == null)
		{
			return;
		}
		String current = e.design.name;
		while (true)
		{
			Object answer = JOptionPane.showInputDialog(window(from), "New name for \"" + current + "\":",
				"Rename design", JOptionPane.PLAIN_MESSAGE, null, null, current);
			if (answer == null)
			{
				return;
			}
			String name = answer.toString().trim();
			String problem = CustomDesignRules.nameProblem(name);
			if (problem == null)
			{
				executor.execute(() -> renamed(design.id, name, from));
				return;
			}
			message(from, problem);
			current = name.length() > CustomDesignRules.MAX_NAME ? name.substring(0, CustomDesignRules.MAX_NAME)
				: name;
		}
	}

	private void renamed(String id, String name, Component from)
	{
		Entry e = entries.get(id);
		if (e == null)
		{
			return;
		}
		CustomDesign d = e.design.named(name);
		try
		{
			store.save(d, null, null, null);
		}
		catch (IOException ex)
		{
			log.warn("RuneSkate: custom design {} could not be renamed", id, ex);
			SwingUtilities.invokeLater(() -> message(from, "The design couldn't be renamed: " + ex.getMessage()));
			return;
		}
		Map<String, Entry> now = new LinkedHashMap<>(entries);
		now.put(id, new Entry(d, d.asBoardDesign(REVISIONS.incrementAndGet()), e.thumb, e.shared));
		publish(now);
		// the old revision's converted colours
		BakedBoardModel.forgetDesign(id);
	}

	/** Delete, after a yes. */
	public void delete(BoardDesign design, Component from)
	{
		Entry e = entries.get(design.id);
		if (e == null)
		{
			return;
		}
		int ok = JOptionPane.showConfirmDialog(window(from), "Delete your design \"" + e.design.name
			+ "\"? This can't be undone.", "Delete design", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.OK_OPTION)
		{
			return;
		}
		executor.execute(() ->
		{
			try
			{
				store.delete(design.id);
			}
			catch (IOException ex)
			{
				log.warn("RuneSkate: custom design {} could not be deleted", design.id, ex);
				SwingUtilities.invokeLater(() -> message(from, "The design couldn't be deleted: " + ex.getMessage()));
				return;
			}
			Map<String, Entry> now = new LinkedHashMap<>(entries);
			now.remove(design.id);
			publish(now);
			DesignColours.unregister(design.id);
			BakedBoardModel.forgetDesign(design.id);
		});
	}

	// ---- the editor (EDT) ----

	private boolean bringEditorToFront()
	{
		if (editor != null && editor.isDisplayable())
		{
			editor.toFront();
			return true;
		}
		return false;
	}

	private void openEditor(Component from, DesignPart part, PartOutline outline, BufferedImage image,
		ImagePlacement start, String name, CustomDesign editing, int sourceWidth, int sourceHeight)
	{
		if (!running || bringEditorToFront())
		{
			return;
		}
		BakedBoardGeometry.Mesh hi = mesh(part, true);
		DesignEditorDialog[] dialog = new DesignEditorDialog[1];
		DesignEditorPanel panel = new DesignEditorPanel(part, layouts.of(part), hi, outline, image, start, name,
			executor, new DesignEditorPanel.Listener()
			{
				@Override
				public void save(String newName, ImagePlacement placement)
				{
					closeEditor();
					executor.execute(() -> saveDesign(from, part, image, placement, newName, editing, sourceWidth,
						sourceHeight));
				}

				@Override
				public void cancel()
				{
					closeEditor();
				}

				@Override
				public void downloadTemplate()
				{
					saveTemplate(dialog[0], part, outline);
				}
			});
		String title = (editing == null ? "New " : "Edit ") + part.key + " design";
		dialog[0] = new DesignEditorDialog(window(from), title, panel, this::closeEditor);
		editor = dialog[0];
		editor.setVisible(true);
	}

	private void closeEditor()
	{
		if (editor != null)
		{
			editor.close();
			editor = null;
		}
	}

	/** Executor: bakes both details, saves, shows and selects the design. */
	private void saveDesign(Component from, DesignPart part, BufferedImage image, ImagePlacement placement,
		String name, CustomDesign editing, int sourceWidth, int sourceHeight)
	{
		Map<String, Entry> now = new LinkedHashMap<>(entries);
		CustomDesign d;
		if (editing != null)
		{
			d = editing.named(name).placed(placement);
		}
		else
		{
			if (now.size() >= CustomDesignRules.MAX_DESIGNS)
			{
				SwingUtilities.invokeLater(() -> message(from, "You already have " + CustomDesignRules.MAX_DESIGNS
					+ " custom designs."));
				return;
			}
			String id;
			do
			{
				id = CustomDesignRules.newId(random);
			}
			while (now.containsKey(id) || designs.byId(id) != null);
			d = new CustomDesign(id, name, part, System.currentTimeMillis(), sourceWidth, sourceHeight, placement);
		}
		BakedBoardGeometry.Mesh hi = mesh(part, true);
		BakedBoardGeometry.Mesh lo = mesh(part, false);
		if (hi == null || lo == null)
		{
			return;
		}
		CustomBake.Source src = source(image, part);
		DesignLayout.Part layout = layouts.of(part);
		int[] high = CustomBake.bake(hi, layout, placement, src, true);
		int[] low = CustomBake.bake(lo, layout, placement, src, false);
		try
		{
			store.save(d, editing == null ? image : null, high, low);
		}
		catch (IOException e)
		{
			log.warn("RuneSkate: custom design {} could not be saved", d.id, e);
			SwingUtilities.invokeLater(() -> message(from, "The design couldn't be saved: " + e.getMessage()));
			return;
		}
		Entry e = prepare(d, image, high, low);
		if (e == null)
		{
			return;
		}
		now.put(d.id, e);
		publish(now);
		if (editing != null)
		{
			// the old revision's converted colours
			BakedBoardModel.forgetDesign(d.id);
		}
		BoardDesign chosen = e.board;
		clientThread.invoke(() -> progression.selectDesign(chosen));
	}

	/** Download template: where to save it, then the picture written there (executor). */
	private void saveTemplate(Component from, DesignPart part, PartOutline outline)
	{
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save the " + part.key + " template")
			.addExtensionFilter("PNG image", "png")
			.setDefaultExtension("png")
			.setFileName("runeskate-" + part.key + "-template.png");
		List<Filepath> picked = chooser.showDialog(from);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath file = picked.get(0);
		executor.execute(() ->
		{
			try (OutputStream out = file.openOutputStream())
			{
				DesignImages.writePng(DesignTemplate.render(part, outline), out);
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("RuneSkate: template could not be saved", e);
				SwingUtilities.invokeLater(() -> message(from, "The template couldn't be saved: " + e.getMessage()));
			}
		});
	}

	private static Window window(Component c)
	{
		if (c == null)
		{
			return null;
		}
		return c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
	}

	private static void message(Component from, String text)
	{
		JOptionPane.showMessageDialog(window(from), text, "RuneSkate", JOptionPane.INFORMATION_MESSAGE);
	}
}
