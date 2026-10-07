package com.gielinorskate.world;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Decides from an object's (impostor-resolved) name and actions whether the skater rides straight through
 * it: vegetation (plants, flowers, bushes, crops, grass, vines...), and doors or gates standing open. Pure;
 * no client dependency.
 * <p>
 * Vegetation is a case-insensitive substring match on {@link #VEGETATION_STEMS}, so compounds such as
 * "Rosebush", "Shrubbery" or "Sunflowers" match as well as "Tea bush". A name containing any of
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
 */
public final class ClutterNames
{
	/** Lower-case plant stems, matched anywhere in the name. */
	static final String[] VEGETATION_STEMS = {
		"plant", "flower", "bush", "fern", "grass", "shrub", "weed", "mushroom", "fungus", "fungi", "crop", "hay",
		"reed", "daisy", "daisies", "cabbage", "flax", "potato", "onion", "sapling", "root", "thistle", "nettle",
		"leaf", "leaves", "ivy", "vine", "cactus", "cacti", "bramble", "lily", "rose", "tulip", "poppy", "poppies",
		"sunflower", "bamboo", "foliage", "herb", "seaweed", "kelp", "wheat", "corn", "barley", "marigold",
		"lavender", "bluebell", "undergrowth", "creeper", "bulrush", "bullrush", "clover", "heather",
		"tomato", "pumpkin", "orchid", "lilac"};

	/** Lower-case parts that keep a name solid even with a plant stem in it (see the class doc). */
	static final String[] SOLID_PARTS = {
		"tree", "hedge", "planter", "flowerpot", "vase", "stall", "table", "desk", "chair", "bench", "shelf",
		"shelves", "barrel", "crate", "statue", "sign", "trellis", "wall", "fence", "door", "gate", "haystack",
		"weedkiller", "rosewood", "infern", "ravine", "divine", "vinegar", "herblore", "herbiboar", "ambush",
		"corner", "shayzien"};

	/** Whole words that keep a name solid ("pot" alone, so "Potato" still matches). */
	private static final Set<String> SOLID_WORDS = new HashSet<>(Arrays.asList("pot", "pots", "potted"));

	private ClutterNames()
	{
	}

	/** The name is vegetation: it contains a plant stem and nothing that keeps it solid. */
	public static boolean passByName(String name)
	{
		if (name == null)
		{
			return false;
		}
		// "vegetable" holds "table": take it out before looking for furniture
		String n = name.toLowerCase(Locale.ROOT).replace("vegetable", "veg plant");
		if (!containsAny(n, VEGETATION_STEMS))
		{
			return false;
		}
		for (String part : SOLID_PARTS)
		{
			// tree roots are low tangles, not trunks: "Tree roots" and "Treeroot" stay ridden through
			if (n.contains(part) && !("tree".equals(part) && n.contains("root")))
			{
				return false;
			}
		}
		for (String word : n.split("[^a-z]+"))
		{
			if (SOLID_WORDS.contains(word))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean containsAny(String n, String[] parts)
	{
		for (String p : parts)
		{
			if (n.contains(p))
			{
				return true;
			}
		}
		return false;
	}

	/** A door or gate that currently offers "Close", i.e. stands open. */
	public static boolean isOpenDoor(String name, String[] actions)
	{
		if (name == null || actions == null)
		{
			return false;
		}
		// "vegetable" holds "table": take it out before looking for furniture
		String n = name.toLowerCase(Locale.ROOT).replace("vegetable", "veg plant");
		if (!n.contains("door") && !n.contains("gate"))
		{
			return false;
		}
		for (String a : actions)
		{
			if ("close".equalsIgnoreCase(a))
			{
				return true;
			}
		}
		return false;
	}

	/** Ridden through: a plant/crop by name, or an open door/gate. */
	public static boolean passThrough(String name, String[] actions)
	{
		return passThrough(name, actions, true);
	}

	/**
	 * Ridden through: an open door/gate always; vegetation only when {@code passVegetation} (the "Pass
	 * through vegetation" setting) is on, otherwise it collides like any other object.
	 */
	public static boolean passThrough(String name, String[] actions, boolean passVegetation)
	{
		return isOpenDoor(name, actions) || (passVegetation && passByName(name));
	}

	/**
	 * The definition to read the name/actions from: the current impostor when the object has impostors
	 * (varbit-driven objects; may be null when it currently shows nothing), else the definition itself.
	 */
	public static <T> T resolve(T def, Predicate<T> hasImpostors, UnaryOperator<T> impostor)
	{
		if (def == null)
		{
			return null;
		}
		return hasImpostors.test(def) ? impostor.apply(def) : def;
	}
}
