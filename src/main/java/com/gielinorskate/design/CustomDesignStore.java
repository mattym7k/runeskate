package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.DesignColours;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Players' own designs on disk, one set of files per design in one folder (the plugin's data folder/designs, through
 * RuneLite's {@link Filepath}):
 * <pre>
 * CUSTOM_XXXXXXXX.json       id, name, part, created, the picked image's size, the placement (format below)
 * CUSTOM_XXXXXXXX.high.rgb   the baked colours at High and Normal detail (the shipped designs' colour file format,
 * CUSTOM_XXXXXXXX.low.rgb    {@link DesignColours})
 * CUSTOM_XXXXXXXX.png        the kept image (at most {@link CustomDesignRules#STORED_MAX_SIDE} on its long side)
 * </pre>
 * The JSON is written last (and deleted first), so a design without it is never seen. A design whose JSON or image is
 * missing or corrupt is skipped with a log line; missing or corrupt colours come back as null (they can be baked
 * again from the image). Every call does file IO: never on the client thread or the EDT.
 */
@Slf4j
public final class CustomDesignStore
{
	static final int VERSION = 1;
	private static final String JSON = ".json";
	private static final String TMP = ".tmp";

	private final Filepath dir;

	public CustomDesignStore(Filepath dir)
	{
		this.dir = dir;
	}

	public Filepath dir()
	{
		return dir;
	}

	/** A design read back: colours null when their file is missing or broken. */
	public static final class Stored
	{
		public final CustomDesign design;
		public final BufferedImage image;
		public final int[] high;
		public final int[] low;

		Stored(CustomDesign design, BufferedImage image, int[] high, int[] low)
		{
			this.design = design;
			this.image = image;
			this.high = high;
			this.low = low;
		}
	}

	/** Every readable design, oldest first, at most {@link CustomDesignRules#MAX_DESIGNS}. */
	public List<Stored> loadAll()
	{
		List<Stored> out = new ArrayList<>();
		loadEach(out::add);
		return out;
	}

	/**
	 * Every readable design, oldest first, at most {@link CustomDesignRules#MAX_DESIGNS}, handed over one at a time
	 * (its image read just before), so only one kept image need be in memory at once.
	 */
	public void loadEach(Consumer<Stored> each)
	{
		if (!dir.isDirectory())
		{
			return;
		}
		List<String> all;
		try (Stream<Filepath> files = dir.walk(1))
		{
			all = files.map(Filepath::getFileName).sorted().collect(Collectors.toList());
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("RuneSkate: custom designs folder {} could not be listed", dir, e);
			return;
		}
		sweepTemporaries(all);
		List<String> names = all.stream().filter(n -> n.endsWith(JSON)).collect(Collectors.toList());
		List<CustomDesign> found = new ArrayList<>();
		for (String name : names)
		{
			String id = name.substring(0, name.length() - JSON.length());
			if (!CustomDesignRules.isId(id))
			{
				continue;
			}
			CustomDesign d = design(id);
			if (d != null)
			{
				found.add(d);
			}
		}
		found.sort(Comparator.comparingLong((CustomDesign d) -> d.created).thenComparing(d -> d.id));
		int loaded = 0;
		for (CustomDesign d : found)
		{
			if (loaded == CustomDesignRules.MAX_DESIGNS)
			{
				log.warn("RuneSkate: more than {} custom designs found, only the first {} are used",
					CustomDesignRules.MAX_DESIGNS, CustomDesignRules.MAX_DESIGNS);
				return;
			}
			Stored s = withImage(d);
			if (s != null)
			{
				loaded++;
				each.accept(s);
			}
		}
	}

	/** A design's JSON, or null (logged) when it is missing or broken. */
	private CustomDesign design(String id)
	{
		CustomDesign d;
		try (Reader r = dir.joinSegment(id + JSON).openBufferedReader())
		{
			d = parse(r);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("RuneSkate: custom design {} skipped: its file could not be read ({})", id, e.getMessage());
			return null;
		}
		if (!d.id.equals(id))
		{
			log.warn("RuneSkate: custom design {} skipped: its file names {}", id, d.id);
			return null;
		}
		return d;
	}

	private Stored withImage(CustomDesign d)
	{
		BufferedImage image = image(d);
		if (image == null)
		{
			log.warn("RuneSkate: custom design {} skipped: its image is missing or broken", d.id);
			return null;
		}
		return new Stored(d, image, colours(d.id + ".high.rgb", d.part), colours(d.id + ".low.rgb", d.part));
	}

	/** Design {@code d}'s kept image, or null when it is missing, broken or not the size its placement is for. */
	public BufferedImage image(CustomDesign d)
	{
		BufferedImage image;
		Filepath png = dir.joinSegment(d.id + ".png");
		try (InputStream in = png.isFile() ? png.openInputStream() : null)
		{
			image = in == null ? null : DesignImages.readImage(in);
		}
		catch (IOException | RuntimeException e)
		{
			image = null;
		}
		if (image == null || image.getWidth() != d.placement.imageWidth
			|| image.getHeight() != d.placement.imageHeight)
		{
			return null;
		}
		return image;
	}

	private int[] colours(String file, DesignPart part)
	{
		Filepath p = dir.joinSegment(file);
		if (!p.isFile())
		{
			return null;
		}
		try (InputStream in = p.openInputStream())
		{
			return DesignColours.parse(in.readAllBytes(), part.index);
		}
		catch (IOException | IllegalArgumentException e)
		{
			log.warn("RuneSkate: custom design colours {} could not be read ({})", file, e.getMessage());
			return null;
		}
	}

	/**
	 * Saves a design: its image (unless null: kept as it is), its colours (unless null) and then its JSON.
	 *
	 * @throws IOException when it couldn't be written
	 */
	public void save(CustomDesign d, BufferedImage image, int[] high, int[] low) throws IOException
	{
		dir.createDirectories();
		if (image != null)
		{
			write(d.id + ".png", out -> DesignImages.writePng(image, out));
		}
		if (high != null)
		{
			byte[] b = DesignColours.pack(d.part.index, high);
			write(d.id + ".high.rgb", out -> out.write(b));
		}
		if (low != null)
		{
			byte[] b = DesignColours.pack(d.part.index, low);
			write(d.id + ".low.rgb", out -> out.write(b));
		}
		byte[] json = toJson(d).getBytes(StandardCharsets.UTF_8);
		write(d.id + JSON, out -> out.write(json));
	}

	/** Deletes a design's files (its JSON first); missing ones are fine. */
	public void delete(String id) throws IOException
	{
		if (!CustomDesignRules.isId(id))
		{
			throw new IllegalArgumentException("not a custom design id: " + id);
		}
		dir.joinSegment(id + JSON).deleteIfExists();
		dir.joinSegment(id + ".png").deleteIfExists();
		dir.joinSegment(id + ".high.rgb").deleteIfExists();
		dir.joinSegment(id + ".low.rgb").deleteIfExists();
	}

	/** Deletes our temporary files left behind by a write that was cut off (the client closed mid-save). */
	private void sweepTemporaries(List<String> names)
	{
		for (String name : names)
		{
			int dot = name.indexOf('.');
			if (name.endsWith(TMP) && dot > 0 && CustomDesignRules.isId(name.substring(0, dot)))
			{
				try
				{
					dir.joinSegment(name).deleteIfExists();
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("RuneSkate: temporary file {} could not be deleted", name, e);
				}
			}
		}
	}

	private interface Body
	{
		void write(OutputStream out) throws IOException;
	}

	/** Writes a file through a temporary one beside it, then moves it into place. */
	private void write(String name, Body body) throws IOException
	{
		Filepath target = dir.joinSegment(name);
		Filepath tmp = dir.joinSegment(name + TMP);
		try
		{
			try (OutputStream out = tmp.openOutputStream())
			{
				body.write(out);
			}
			try
			{
				tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException | RuntimeException e)
		{
			try
			{
				tmp.deleteIfExists();
			}
			catch (IOException | RuntimeException d)
			{
				e.addSuppressed(d);
			}
			throw e;
		}
	}

	static String toJson(CustomDesign d) throws IOException
	{
		StringWriter sw = new StringWriter();
		try (JsonWriter w = new JsonWriter(sw))
		{
			w.setIndent("  ");
			w.beginObject();
			w.name("version").value(VERSION);
			w.name("id").value(d.id);
			w.name("name").value(d.name);
			w.name("part").value(d.part.key);
			w.name("created").value(d.created);
			w.name("source").beginObject();
			w.name("width").value(d.sourceWidth);
			w.name("height").value(d.sourceHeight);
			w.endObject();
			ImagePlacement p = d.placement;
			w.name("placement").beginObject();
			w.name("imageWidth").value(p.imageWidth);
			w.name("imageHeight").value(p.imageHeight);
			w.name("cx").value(p.cx);
			w.name("cy").value(p.cy);
			// one scale as before the stretch handles (older versions read it); a stretch adds both
			w.name("scale").value(p.meanScale());
			if (!p.uniform())
			{
				w.name("scaleX").value(p.scaleX);
				w.name("scaleY").value(p.scaleY);
			}
			w.name("turns").value(p.turns);
			w.name("flipped").value(p.flipped);
			w.endObject();
			w.endObject();
		}
		return sw.toString();
	}

	/** A design's JSON; IllegalArgumentException (or IOException) when it is malformed. */
	static CustomDesign parse(Reader reader) throws IOException
	{
		String id = null;
		String name = null;
		DesignPart part = null;
		long created = 0;
		int sw = -1;
		int sh = -1;
		ImagePlacement placement = null;
		JsonReader r = new JsonReader(reader);
		try
		{
			r.beginObject();
			while (r.hasNext())
			{
				String key = r.nextName();
				if (r.peek() == JsonToken.NULL)
				{
					r.nextNull();
					continue;
				}
				switch (key)
				{
					case "version":
						if (r.nextInt() > VERSION)
						{
							throw new IllegalArgumentException("made by a newer RuneSkate");
						}
						break;
					case "id":
						id = r.nextString();
						break;
					case "name":
						name = r.nextString();
						break;
					case "part":
						part = DesignPart.fromKey(r.nextString());
						break;
					case "created":
						created = r.nextLong();
						break;
					case "source":
						r.beginObject();
						while (r.hasNext())
						{
							String k = r.nextName();
							if ("width".equals(k))
							{
								sw = r.nextInt();
							}
							else if ("height".equals(k))
							{
								sh = r.nextInt();
							}
							else
							{
								r.skipValue();
							}
						}
						r.endObject();
						break;
					case "placement":
						placement = placement(r);
						break;
					default:
						r.skipValue();
						break;
				}
			}
			r.endObject();
		}
		catch (IllegalStateException | NumberFormatException e)
		{
			throw new IllegalArgumentException(e.getMessage(), e);
		}
		if (!CustomDesignRules.isId(id) || part == null || placement == null
			|| CustomDesignRules.nameProblem(name) != null)
		{
			throw new IllegalArgumentException("a field is missing or bad");
		}
		return new CustomDesign(id, name.trim(), part, created, Math.max(sw, 0), Math.max(sh, 0), placement);
	}

	private static ImagePlacement placement(JsonReader r) throws IOException
	{
		int w = 0;
		int h = 0;
		double cx = Double.NaN;
		double cy = Double.NaN;
		double scale = Double.NaN;
		double scaleX = Double.NaN;
		double scaleY = Double.NaN;
		int turns = 0;
		boolean flipped = false;
		r.beginObject();
		while (r.hasNext())
		{
			switch (r.nextName())
			{
				case "imageWidth":
					w = r.nextInt();
					break;
				case "imageHeight":
					h = r.nextInt();
					break;
				case "cx":
					cx = r.nextDouble();
					break;
				case "cy":
					cy = r.nextDouble();
					break;
				case "scale":
					scale = r.nextDouble();
					break;
				case "scaleX":
					scaleX = r.nextDouble();
					break;
				case "scaleY":
					scaleY = r.nextDouble();
					break;
				case "turns":
					turns = r.nextInt();
					break;
				case "flipped":
					flipped = r.nextBoolean();
					break;
				default:
					r.skipValue();
					break;
			}
		}
		r.endObject();
		if (w > CustomDesignRules.STORED_MAX_SIDE || h > CustomDesignRules.STORED_MAX_SIDE)
		{
			throw new IllegalArgumentException("kept image too large");
		}
		if (Double.isNaN(scaleX) && Double.isNaN(scaleY))
		{
			// saved before the stretch handles: one scale both ways
			return new ImagePlacement(w, h, cx, cy, scale, turns, flipped);
		}
		return new ImagePlacement(w, h, cx, cy, scaleX, scaleY, turns, flipped);
	}
}
