package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Each part's design image layout (design_layout.json, written by tools/blend_to_board_baked.py or
 * tools/custom_designs.py): the size of the image a design is painted in (the part's UVs span it, v up), and the
 * box-blur radius the offline bake gives an image of exactly that size at High and Normal detail. A player's image
 * is placed in this layout ({@link ImagePlacement}). Pure; immutable once parsed.
 */
@Slf4j
public final class DesignLayout
{
	private static final String RESOURCE = "/com/gielinorskate/design_layout.json";

	/** One part's layout. */
	public static final class Part
	{
		public final DesignPart part;
		/** The design image's size in pixels (UV 0..1 spans it). */
		public final int width;
		public final int height;
		/** Blur radius (pixels of a layout-sized image) at High and Normal detail. */
		public final int blurHigh;
		public final int blurLow;

		public Part(DesignPart part, int width, int height, int blurHigh, int blurLow)
		{
			this.part = part;
			this.width = width;
			this.height = height;
			this.blurHigh = blurHigh;
			this.blurLow = blurLow;
		}

		public int blur(boolean high)
		{
			return high ? blurHigh : blurLow;
		}
	}

	private final Map<DesignPart, Part> parts;

	private DesignLayout(Map<DesignPart, Part> parts)
	{
		this.parts = parts;
	}

	private static volatile DesignLayout bundled;

	/** The plugin's layout, read once; built-in values if the resource is missing or broken. */
	public static DesignLayout bundled()
	{
		DesignLayout l = bundled;
		if (l == null)
		{
			synchronized (DesignLayout.class)
			{
				l = bundled;
				if (l == null)
				{
					l = load();
					bundled = l;
				}
			}
		}
		return l;
	}

	private static DesignLayout load()
	{
		try (InputStream in = DesignLayout.class.getResourceAsStream(RESOURCE))
		{
			if (in != null)
			{
				return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("RuneSkate: the design layout could not be read", e);
		}
		Map<DesignPart, Part> m = new EnumMap<>(DesignPart.class);
		m.put(DesignPart.GRIP, new Part(DesignPart.GRIP, 361, 1059, 2, 5));
		m.put(DesignPart.DECK, new Part(DesignPart.DECK, 300, 820, 2, 3));
		m.put(DesignPart.WHEELS, new Part(DesignPart.WHEELS, 394, 389, 12, 12));
		return new DesignLayout(m);
	}

	/** A layout file; IllegalArgumentException when a part is missing or malformed. */
	public static DesignLayout parse(Reader reader) throws IOException
	{
		Map<DesignPart, Part> m = new EnumMap<>(DesignPart.class);
		JsonReader r = new JsonReader(reader);
		try
		{
			r.beginObject();
			while (r.hasNext())
			{
				if (!"parts".equals(r.nextName()))
				{
					r.skipValue();
					continue;
				}
				r.beginObject();
				while (r.hasNext())
				{
					DesignPart p = DesignPart.fromKey(r.nextName());
					if (p == null)
					{
						r.skipValue();
						continue;
					}
					m.put(p, part(r, p));
				}
				r.endObject();
			}
			r.endObject();
		}
		catch (IllegalStateException | NumberFormatException e)
		{
			throw new IllegalArgumentException("design layout: " + e.getMessage(), e);
		}
		for (DesignPart p : DesignPart.values())
		{
			if (!m.containsKey(p))
			{
				throw new IllegalArgumentException("design layout: no " + p.key);
			}
		}
		return new DesignLayout(m);
	}

	private static Part part(JsonReader r, DesignPart p) throws IOException
	{
		int w = -1;
		int h = -1;
		int high = -1;
		int low = -1;
		r.beginObject();
		while (r.hasNext())
		{
			String key = r.nextName();
			if ("size".equals(key))
			{
				List<Integer> s = ints(r);
				if (s.size() != 2)
				{
					throw new IllegalArgumentException("design layout: " + p.key + " size needs 2 numbers");
				}
				w = s.get(0);
				h = s.get(1);
			}
			else if ("blur".equals(key) && r.peek() == JsonToken.BEGIN_OBJECT)
			{
				r.beginObject();
				while (r.hasNext())
				{
					String d = r.nextName();
					if ("high".equals(d))
					{
						high = r.nextInt();
					}
					else if ("low".equals(d))
					{
						low = r.nextInt();
					}
					else
					{
						r.skipValue();
					}
				}
				r.endObject();
			}
			else
			{
				r.skipValue();
			}
		}
		r.endObject();
		if (w < 1 || h < 1 || w > 8192 || h > 8192 || high < 0 || low < 0 || high > 256 || low > 256)
		{
			throw new IllegalArgumentException("design layout: bad " + p.key);
		}
		return new Part(p, w, h, high, low);
	}

	private static List<Integer> ints(JsonReader r) throws IOException
	{
		List<Integer> out = new ArrayList<>();
		r.beginArray();
		while (r.hasNext())
		{
			out.add(r.nextInt());
		}
		r.endArray();
		return out;
	}

	public Part of(DesignPart part)
	{
		return parts.get(part);
	}
}
