package com.gielinorskate.progression;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Every board design, from the bundled designs.json (written by tools/blend_to_board_baked.py from
 * tools/designs/designs.json): {"designs": [{"id", "name", "part", "unlock"[, "credit"]}, ...]}. The first design
 * of each part is its default, used for anything unknown or still locked. The old progression ladder's deck names
 * (BRONZE ... TORVA) are deck design ids, so saved and sent deck names keep working; the old starter CLASSIC is
 * not a design, so it is the default deck.
 *
 * <p>A player's own designs ({@link BoardDesign#custom}, set with {@link #setCustom}) come after the shipped ones
 * in {@link #of}, are found by {@link #byId} / {@link #find} / {@link #usable}, but never by {@link #fromWire} (a
 * custom design goes to the party as "C:" and its picture's hash, which party.GhostCodec resolves; older versions
 * find no such name and draw the default) and are not in {@link #all} (the shipped catalogue). The shipped designs are
 * immutable once parsed; the custom list is swapped whole, so any thread may read it.
 */
@Slf4j
public final class BoardDesigns
{
	/** Longest wire name (an id without its part's prefix), so party updates stay small. */
	public static final int WIRE_MAX = 9;
	private static final Pattern ID = Pattern.compile("[A-Z][A-Z0-9_]{0,23}");
	/** Ids of players' own designs: never in the manifest. */
	public static final String CUSTOM_PREFIX = "CUSTOM_";
	private static final String RESOURCE = "/com/gielinorskate/designs/designs.json";

	private final List<BoardDesign> all;
	private final Map<String, BoardDesign> byId = new HashMap<>();
	private final Map<DesignPart, List<BoardDesign>> byPart = new EnumMap<>(DesignPart.class);
	/** The player's own designs, swapped whole by {@link #setCustom}. */
	private volatile Custom custom = new Custom(Collections.emptyList(), null);

	/** A snapshot of the custom designs: by id, and each part's shipped designs followed by its custom ones. */
	private static final class Custom
	{
		final List<BoardDesign> list;
		final Map<String, BoardDesign> byId = new HashMap<>();
		final Map<DesignPart, List<BoardDesign>> withShipped = new EnumMap<>(DesignPart.class);

		Custom(List<BoardDesign> list, Map<DesignPart, List<BoardDesign>> shipped)
		{
			this.list = list;
			for (BoardDesign d : list)
			{
				byId.put(d.id, d);
			}
			if (shipped != null)
			{
				for (DesignPart p : DesignPart.values())
				{
					List<BoardDesign> all = new ArrayList<>(shipped.get(p));
					for (BoardDesign d : list)
					{
						if (d.part == p)
						{
							all.add(d);
						}
					}
					withShipped.put(p, Collections.unmodifiableList(all));
				}
			}
		}
	}

	private BoardDesigns(List<BoardDesign> designs)
	{
		all = Collections.unmodifiableList(new ArrayList<>(designs));
		for (DesignPart p : DesignPart.values())
		{
			byPart.put(p, new ArrayList<>());
		}
		for (BoardDesign d : designs)
		{
			byId.put(d.id, d);
			byPart.get(d.part).add(d);
		}
		for (DesignPart p : DesignPart.values())
		{
			byPart.put(p, Collections.unmodifiableList(byPart.get(p)));
		}
	}

	private static volatile BoardDesigns bundled;

	/** The plugin's designs, loaded once; just the three defaults if the manifest is missing or broken. */
	public static BoardDesigns bundled()
	{
		BoardDesigns b = bundled;
		if (b == null)
		{
			synchronized (BoardDesigns.class)
			{
				b = bundled;
				if (b == null)
				{
					b = load();
					bundled = b;
				}
			}
		}
		return b;
	}

	private static BoardDesigns load()
	{
		InputStream in = BoardDesigns.class.getResourceAsStream(RESOURCE);
		if (in != null)
		{
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return parse(r);
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("RuneSkate: board designs could not be read", e);
			}
		}
		List<BoardDesign> fallback = new ArrayList<>();
		fallback.add(new BoardDesign("GRIP_RUNESKATE", "RuneSkate", DesignPart.GRIP, 1, null));
		fallback.add(new BoardDesign("DECK_RED_CAMO", "Red Camo", DesignPart.DECK, 1, null));
		fallback.add(new BoardDesign("WHEELS_DEATH", "Death Grips", DesignPart.WHEELS, 1, null));
		return new BoardDesigns(fallback);
	}

	/** A manifest, checked; IllegalArgumentException when it breaks a rule above. */
	public static BoardDesigns parse(Reader reader) throws IOException
	{
		List<BoardDesign> out = new ArrayList<>();
		JsonReader r = new JsonReader(reader);
		try
		{
			r.beginObject();
			boolean found = false;
			while (r.hasNext())
			{
				String key = r.nextName();
				if (!"designs".equals(key))
				{
					r.skipValue();
					continue;
				}
				found = true;
				r.beginArray();
				while (r.hasNext())
				{
					out.add(entry(r, out.size()));
				}
				r.endArray();
			}
			r.endObject();
			if (!found)
			{
				throw new IllegalArgumentException("designs.json: no designs list");
			}
		}
		catch (IllegalStateException | NumberFormatException e)
		{
			throw new IllegalArgumentException("designs.json: " + e.getMessage(), e);
		}
		check(out);
		return new BoardDesigns(out);
	}

	private static BoardDesign entry(JsonReader r, int index) throws IOException
	{
		String id = null;
		String name = null;
		String part = null;
		String credit = null;
		int unlock = -1;
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
				case "id":
					id = r.nextString();
					break;
				case "name":
					name = r.nextString();
					break;
				case "part":
					part = r.nextString();
					break;
				case "credit":
					credit = r.nextString();
					break;
				case "unlock":
					if (r.peek() != JsonToken.NUMBER)
					{
						throw new IllegalArgumentException("design " + index + ": unlock is not a number");
					}
					double u = r.nextDouble();
					if (u != Math.rint(u))
					{
						throw new IllegalArgumentException("design " + index + ": unlock is not a whole level");
					}
					unlock = (int) u;
					break;
				default:
					r.skipValue();
					break;
			}
		}
		r.endObject();
		String where = "design " + (id != null ? id : Integer.toString(index));
		if (id == null || !ID.matcher(id).matches())
		{
			throw new IllegalArgumentException(where + ": bad id (uppercase A-Z, 0-9, _)");
		}
		DesignPart p = DesignPart.fromKey(part);
		if (p == null)
		{
			throw new IllegalArgumentException(where + ": bad part " + part);
		}
		if (name == null || name.trim().isEmpty())
		{
			throw new IllegalArgumentException(where + ": no name");
		}
		if (unlock < 1 || unlock > SkateLevels.MAX_LEVEL)
		{
			throw new IllegalArgumentException(where + ": unlock must be a level 1.." + SkateLevels.MAX_LEVEL);
		}
		return new BoardDesign(id, name.trim(), p, unlock, credit == null || credit.trim().isEmpty() ? null
			: credit.trim());
	}

	private static void check(List<BoardDesign> designs)
	{
		Set<String> ids = new HashSet<>();
		Set<String> wires = new HashSet<>();
		Map<DesignPart, BoardDesign> first = new EnumMap<>(DesignPart.class);
		for (BoardDesign d : designs)
		{
			if (d.id.startsWith(CUSTOM_PREFIX))
			{
				throw new IllegalArgumentException("designs.json: " + d.id + " uses the custom designs' prefix");
			}
			if (!ids.add(d.id))
			{
				throw new IllegalArgumentException("designs.json: id " + d.id + " twice");
			}
			String wire = d.wireName();
			if (wire.length() > WIRE_MAX || !wires.add(d.part + ":" + wire))
			{
				throw new IllegalArgumentException("designs.json: " + d.id + "'s wire name " + wire
					+ " is too long or used twice in its part");
			}
			if (!first.containsKey(d.part))
			{
				first.put(d.part, d);
				if (d.unlock != 1)
				{
					throw new IllegalArgumentException("designs.json: the default " + d.part.key + ", " + d.id
						+ ", must unlock at level 1");
				}
			}
		}
		for (DesignPart p : DesignPart.values())
		{
			if (!first.containsKey(p))
			{
				throw new IllegalArgumentException("designs.json: no " + p.key + " designs");
			}
		}
	}

	/** Every shipped design in manifest order (no custom ones). */
	public List<BoardDesign> all()
	{
		return all;
	}

	/** The part's designs: the shipped ones in manifest order (the default first), then the player's own. */
	public List<BoardDesign> of(DesignPart part)
	{
		Custom c = custom;
		List<BoardDesign> l = c.withShipped.get(part);
		return l != null ? l : byPart.get(part);
	}

	/** The design with this id (shipped or custom), or null. */
	public BoardDesign byId(String id)
	{
		if (id == null)
		{
			return null;
		}
		BoardDesign d = byId.get(id);
		return d != null ? d : custom.byId.get(id);
	}

	/** The player's own designs, in the order given to {@link #setCustom}. */
	public List<BoardDesign> custom()
	{
		return custom.list;
	}

	/**
	 * Replaces the player's own designs. Each must be {@link BoardDesign#custom} with an id starting
	 * {@link #CUSTOM_PREFIX}; others are left out.
	 */
	public void setCustom(List<BoardDesign> designs)
	{
		List<BoardDesign> ok = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (BoardDesign d : designs)
		{
			if (d != null && d.custom && d.id.startsWith(CUSTOM_PREFIX) && !byId.containsKey(d.id) && seen.add(d.id))
			{
				ok.add(d);
			}
		}
		custom = new Custom(Collections.unmodifiableList(ok), byPart);
	}

	public BoardDesign defaultFor(DesignPart part)
	{
		return byPart.get(part).get(0);
	}

	/** The part's design saved (or chosen) as {@code id}; the part's default when null, unknown or another part's. */
	public BoardDesign find(DesignPart part, String id)
	{
		BoardDesign d = id == null ? null : byId(id.trim());
		return d != null && d.part == part ? d : defaultFor(part);
	}

	/** {@link #find}, and the default while it is still locked at {@code skatingLevel}. */
	public BoardDesign usable(DesignPart part, String id, int skatingLevel)
	{
		BoardDesign d = find(part, id);
		return d.isUnlocked(skatingLevel) ? d : defaultFor(part);
	}

	/**
	 * The design a party update names as {@code wire} (a wire name or a full id); the default when unknown. Only
	 * shipped designs: a custom id never resolves.
	 */
	public BoardDesign fromWire(DesignPart part, String wire)
	{
		if (wire == null)
		{
			return defaultFor(part);
		}
		String w = wire.trim();
		BoardDesign d = byId.get(part.prefix + w);
		if (d == null || d.part != part)
		{
			d = byId.get(w);
		}
		return d != null && d.part == part ? d : defaultFor(part);
	}

	/** Designs first unlocked by going from level {@code from} to {@code to}, in manifest order. */
	public List<BoardDesign> unlockedBetween(int from, int to)
	{
		List<BoardDesign> out = new ArrayList<>();
		for (BoardDesign d : all)
		{
			if (d.unlock > from && d.unlock <= to)
			{
				out.add(d);
			}
		}
		return out.isEmpty() ? Collections.emptyList() : out;
	}
}
