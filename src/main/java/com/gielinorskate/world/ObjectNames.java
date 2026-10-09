package com.gielinorskate.world;

import com.gielinorskate.Text;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
* Decides from a scene object's (impostor-resolved) name and actions what it is to the skater. Pure; no client
* dependency.
* <p>
* <b>Grindable</b> ({@link #looksGrindable}): something a skater would grind (fences, rails, benches, tables,
* crates, low walls...). Matching is per whole word (the name is split on anything that is not a letter),
* case-insensitive in {@link Locale#ROOT}, and accepts a simple plural ("fences", "benches"), so "Trellis" does
* not hit "rail" and "Tablet" does not hit "table". Any excluded word (stairs, steps, ladders, doors, trees,
* bushes, rocks) vetoes the name: "If it looks like it should be grindable you should be able to grind on it.
* Not stairs." Height is not checked here; the caller limits model height separately
* ({@link GridCollisionWorld#GRIND_WALL_MIN}..{@link GridCollisionWorld#GRIND_WALL_MAX}), which is what keeps
* tall walls and city gates out.
* <p>
* <b>Ridden through</b> ({@link #passThrough}): vegetation (plants, flowers, bushes, crops, grass, vines...), and
* doors or gates standing open. Vegetation is a case-insensitive substring match on {@link #VEGETATION_STEMS}, so
* compounds such as "Rosebush", "Shrubbery" or "Sunflowers" match as well as "Tea bush". A name containing any of
* {@link #SOLID_PARTS} never matches, whatever its plant word. The choices:
* <ul>
* <li>trees and hedges stay solid ("Bushy tree", "Rose hedge"), except tree roots, which are low and were
* already ridden through ("Tree roots", "Treeroot");</li>
* <li>furniture and structures made of or holding plants stay solid: planters, pots ("Plant pot", "Potted
* plant", "Flowerpot"), vases, stalls, tables, desks, chairs, shelves, barrels, crates, statues, signs,
* trellises and anything called a wall, fence, door or gate ("Ivy-covered wall");</li>
* <li>words that merely contain a stem stay solid: "Haystack", "Weedkiller", "Rosewood", "Infernal",
* "Ravine", "Divine", "Vinegar", "Herblore", "Herbiboar", "Ambush", "Corner", "Street", "Shayzien"
* (a vegetable never reads as a table).</li>
* </ul>
* <b>Rocks</b> on the ground. Scattered decorative rocks, pebbles and stones flag their tile as blocked like any
* object, so the builder used to cut them a box: 25..120 tall with a 40+ footprint made them
* {@link BlockerSet#LOW}, a step too high to roll over whose four top edges were grind ledges. Now:
* <ul>
* <li>a <b>small decorative rock</b> is ridden straight through: a rock by name ({@link #isRockName}), nothing
* to do on it (no menu action at all, so mining rocks with "Mine", agility rocks with "Climb" and anything
* searchable stay solid), at most 80 tall (about the skater's knee) and at most 192 (one and a half tiles)
* across;</li>
* <li>any other rock still collides as before (a big boulder blocks, a low wide one can be landed on), but no
* rock's box edges are grind ledges: rocks are not rails (name-grinding already leaves them out).</li>
* </ul>
*/
public final class ObjectNames
{
private static final Set<String> GRIND_WORDS = words("grind");

private static final Set<String> NOT_GRIND_WORDS = words("notgrind");

/** Lower-case plant stems, matched anywhere in the name. */
private static final String[] VEGETATION_STEMS = Text.get("on.veg").split(" ");

/** Lower-case parts that keep a name solid even with a plant stem in it (see the class doc). */
private static final String[] SOLID_PARTS = Text.get("on.solidparts").split(" ");

/** Whole words that keep a name solid ("pot" alone, so "Potato" still matches). */
private static final Set<String> SOLID_WORDS = words("solid");

/** Whole words naming a rock (simple plurals included). */
private static final Set<String> ROCK_WORDS = words("rock");

/** Whole words that make a rock name something built (or a rail): never a decorative rock. */
private static final Set<String> STRUCTURE_WORDS = words("structure");

private ObjectNames()
{
}

/** The space-separated words bundled under on.{@code key}. */
private static Set<String> words(String key)
{
return Set.of(Text.get("on." + key).split(" "));
}

/**
* True when a whole word of the name (lower case, split on anything that is not a letter) is a {@code hit}
* and none is a {@code veto}; false for a null name.
*/
private static boolean hasWord(String name, Predicate<String> hit, Predicate<String> veto)
{
if (name == null)
return false;
String[] words = name.toLowerCase(Locale.ROOT).split("[^a-z]+");
return Arrays.stream(words).noneMatch(veto) && Arrays.stream(words).anyMatch(hit);
}

/** The word itself or a simple plural of it ("-s", "-es") is in the set. */
private static boolean matches(Set<String> set, String word)
{
return set.contains(word) || word.endsWith("es") && set.contains(word.substring(0, word.length() - 2))
|| word.endsWith("s") && set.contains(word.substring(0, word.length() - 1));
}

public static boolean looksGrindable(String name)
{
return hasWord(name, w -> matches(GRIND_WORDS, w), w -> matches(NOT_GRIND_WORDS, w));
}

/** The name is vegetation: it contains a plant stem and nothing that keeps it solid. */
public static boolean passByName(String name)
{
if (name == null)
return false;
// "vegetable" holds "table": take it out before looking for furniture
String n = name.toLowerCase(Locale.ROOT).replace("vegetable", "veg plant");
// tree roots are low tangles, not trunks: "Tree roots" and "Treeroot" stay ridden through
return Arrays.stream(VEGETATION_STEMS).anyMatch(n::contains)
&& Arrays.stream(SOLID_PARTS).noneMatch(p -> n.contains(p) && !("tree".equals(p) && n.contains("root")))
&& Arrays.stream(n.split("[^a-z]+")).noneMatch(SOLID_WORDS::contains);
}

/** A door or gate that currently offers "Close", i.e. stands open. */
public static boolean isOpenDoor(String name, String[] actions)
{
if (name == null || actions == null)
return false;
String n = name.toLowerCase(Locale.ROOT);
return (n.contains("door") || n.contains("gate")) && Arrays.stream(actions).anyMatch("close"::equalsIgnoreCase);
}

/**
* Ridden through: an open door/gate always; vegetation only when {@code passVegetation} (the "Pass
* through vegetation" setting) is on, otherwise it collides like any other object.
*/
public static boolean passThrough(String name, String[] actions, boolean passVegetation)
{
return isOpenDoor(name, actions) || passVegetation && passByName(name);
}

/**
* The definition to read the name/actions from: the current impostor when the object has impostors
* (varbit-driven objects; may be null when it currently shows nothing), else the definition itself.
*/
public static <T> T resolve(T def, Predicate<T> hasImpostors, UnaryOperator<T> impostor)
{
return def == null || !hasImpostors.test(def) ? def : impostor.apply(def);
}

/** The name is a rock, pebble, stone or boulder (whole words), and not something built from stone. */
public static boolean isRockName(String name)
{
return hasWord(name, ROCK_WORDS::contains, STRUCTURE_WORDS::contains);
}

/**
* A small decorative rock, ridden through (see the class doc): a rock by name with no menu action (Mine,
* Prospect, Climb, Search...), its model at most 80 tall and its footprint {@code ext} = {minX, maxX, minZ,
* maxZ} (model units; null when there is no model, which never counts) at most 192 on its longer side.
*/
public static boolean isSmallDecorativeRock(String name, String[] actions, float height, float[] ext)
{
return ext != null && isRockName(name)
&& (actions == null || Arrays.stream(actions).allMatch(a -> a == null || a.trim().isEmpty()))
&& height <= 80f && Math.max(ext[1] - ext[0], ext[3] - ext[2]) <= 192f;
}
}
