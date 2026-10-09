package com.gielinorskate.progression;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
* Every board design, from the bundled designs.json (written by tools/blend_to_board_baked.py from
* tools/designs/designs.json): {"designs": [{"id", "name", "part", "unlock"[, "credit"]}, ...]}. The first design
* of each part is its default, used for anything unknown or still locked. The old progression ladder's deck names
* (BRONZE ... TORVA) are deck design ids, so saved and sent deck names keep working; the old starter CLASSIC is
* not a design, so it is the default deck. The manifest is trusted: tools/designs.py checks its rules (ids, wire
* names, defaults unlocked at level 1) when writing it, and the tests check the bundled copy.
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
/** Ids of players' own designs: never in the manifest. */
public static final String CUSTOM_PREFIX = "CUSTOM_";

private final List<BoardDesign> all;
private final Map<String, BoardDesign> byId = new HashMap<>();
private final Map<DesignPart, List<BoardDesign>> byPart = new EnumMap<>(DesignPart.class);
/** The player's own designs, swapped whole by {@link #setCustom}. */
private volatile List<BoardDesign> custom = Collections.emptyList();

private BoardDesigns(List<BoardDesign> designs)
{
all = Collections.unmodifiableList(designs);
for (BoardDesign d : designs)
{
byId.put(d.id, d);
byPart.computeIfAbsent(d.part, p -> new ArrayList<>()).add(d);
}
}

/** Loaded on first use. */
private static final class Bundled
{
static final BoardDesigns DESIGNS = load();
}

/** The plugin's designs, loaded once; just the three defaults if the manifest is missing or broken. */
public static BoardDesigns bundled()
{
return Bundled.DESIGNS;
}

private static BoardDesigns load()
{
try (Reader r = new InputStreamReader(BoardDesigns.class.getResourceAsStream(
"/com/gielinorskate/designs/designs.json"), StandardCharsets.UTF_8))
{
return parse(r);
}
catch (Exception e)
{
log.warn("RuneSkate: board designs could not be read", e);
return new BoardDesigns(Arrays.asList(new BoardDesign("GRIP_BLACK", "Classic black", DesignPart.GRIP, 1, null),
new BoardDesign("DECK_TROPICAL", "Tropical", DesignPart.DECK, 1, null),
new BoardDesign("WHEELS_NATURAL", "Natural", DesignPart.WHEELS, 1, null)));
}
}

/** A manifest; a RuntimeException when it is not one. */
public static BoardDesigns parse(Reader reader)
{
List<BoardDesign> out = new ArrayList<>();
for (JsonElement e : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("designs"))
{
JsonObject o = e.getAsJsonObject();
JsonElement credit = o.get("credit");
out.add(new BoardDesign(o.get("id").getAsString(), o.get("name").getAsString(),
DesignPart.fromKey(o.get("part").getAsString()), o.get("unlock").getAsInt(),
credit == null ? null : credit.getAsString()));
}
return new BoardDesigns(out);
}

/** Every shipped design in manifest order (no custom ones). */
public List<BoardDesign> all()
{
return all;
}

/** The part's designs: the shipped ones in manifest order (the default first), then the player's own. */
public List<BoardDesign> of(DesignPart part)
{
List<BoardDesign> l = new ArrayList<>(byPart.get(part));
custom.stream().filter(d -> d.part == part).forEach(l::add);
return l;
}

/** The design with this id (shipped or custom), or null. */
public BoardDesign byId(String id)
{
BoardDesign d = byId.get(id);
return d != null ? d : custom.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
}

/**
* Replaces the player's own designs. Each must be {@link BoardDesign#custom} with an id starting
* {@link #CUSTOM_PREFIX}; others (and repeats) are left out.
*/
public void setCustom(List<BoardDesign> designs)
{
Set<String> seen = new HashSet<>();
custom = designs.stream().filter(d -> d != null && d.custom && d.id.startsWith(CUSTOM_PREFIX)
&& !byId.containsKey(d.id) && seen.add(d.id)).collect(Collectors.toList());
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
String w = wire == null ? "" : wire.trim();
BoardDesign d = byId.get(part.prefix + w);
if (d == null || d.part != part)
d = byId.get(w);
return d != null && d.part == part ? d : defaultFor(part);
}

/** Designs first unlocked by going from level {@code from} to {@code to}, in manifest order. */
public List<BoardDesign> unlockedBetween(int from, int to)
{
return all.stream().filter(d -> d.unlock > from && d.unlock <= to).collect(Collectors.toList());
}
}
