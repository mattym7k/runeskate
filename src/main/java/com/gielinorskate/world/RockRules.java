package com.gielinorskate.world;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rocks on the ground. Scattered decorative rocks, pebbles and stones flag their tile as blocked like any object,
 * so the builder used to cut them a box: 25..120 tall with a 40+ footprint made them {@link BlockerSet#LOW}, a step
 * too high to roll over whose four top edges were grind ledges. Now:
 * <ul>
 * <li>a <b>small decorative rock</b> is ridden straight through: a rock by name ({@link #isRockName}), nothing to do
 * on it (no menu action at all, so mining rocks with "Mine", agility rocks with "Climb" and anything searchable stay
 * solid), at most {@link #SMALL_ROCK_MAX_HEIGHT} tall and at most {@link #SMALL_ROCK_MAX_SIDE} across;</li>
 * <li>any other rock still collides as before (a big boulder blocks, a low wide one can be landed on), but no rock's
 * box edges are grind ledges: rocks are not rails (name-grinding already left them out, see
 * {@link GrindableNames}).</li>
 * </ul>
 * Pure; no client dependency.
 */
public final class RockRules
{
	/** Model height (local units) up to which a decorative rock is ridden through: about the skater's knee. */
	public static final float SMALL_ROCK_MAX_HEIGHT = 80f;
	/** Longest side of the model's footprint up to which it counts as small: one and a half tiles. */
	public static final float SMALL_ROCK_MAX_SIDE = 192f;

	/** Whole words naming a rock (simple plurals included). */
	private static final Set<String> ROCK_WORDS = new HashSet<>(Arrays.asList(
		"rock", "rocks", "rocky", "pebble", "pebbles", "stone", "stones", "boulder", "boulders", "rubble", "gravel",
		"scree"));

	/** Whole words that make a rock name something built (or a rail): never a decorative rock. */
	private static final Set<String> STRUCTURE_WORDS = new HashSet<>(Arrays.asList(
		"wall", "walls", "bench", "benches", "table", "tables", "pillar", "pillars", "column", "columns", "statue",
		"statues", "step", "steps", "stair", "stairs", "staircase", "arch", "archway", "altar", "fence", "fences",
		"bridge", "gate", "door", "doorway", "sign", "tomb", "grave", "well", "fountain", "obelisk", "monument",
		"throne", "chair", "ledge", "rail", "railing", "barrier", "barricade", "slab", "tablet", "block", "blocks",
		"crate", "planter", "trough", "counter", "stall", "plinth", "pedestal", "shrine", "circle", "henge", "cairn",
		"ruin", "ruins", "furnace", "oven", "anvil"));

	private RockRules()
	{
	}

	/** The name is a rock, pebble, stone or boulder (whole words), and not something built from stone. */
	public static boolean isRockName(String name)
	{
		if (name == null)
		{
			return false;
		}
		boolean rock = false;
		for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z]+"))
		{
			if (STRUCTURE_WORDS.contains(word))
			{
				return false;
			}
			rock |= ROCK_WORDS.contains(word);
		}
		return rock;
	}

	/** True when the object has any menu action (Mine, Prospect, Climb, Search...): something to do on it. */
	public static boolean hasActions(String[] actions)
	{
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a != null && !a.trim().isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A small decorative rock, ridden through (see the class doc): a rock by name with no actions, its model at most
	 * {@link #SMALL_ROCK_MAX_HEIGHT} tall and its footprint {@code ext} = {minX, maxX, minZ, maxZ} (model units; null
	 * when there is no model, which never counts) at most {@link #SMALL_ROCK_MAX_SIDE} on its longer side.
	 */
	public static boolean isSmallDecorativeRock(String name, String[] actions, float height, float[] ext)
	{
		if (ext == null || !isRockName(name) || hasActions(actions))
		{
			return false;
		}
		float longSide = Math.max(ext[1] - ext[0], ext[3] - ext[2]);
		return height <= SMALL_ROCK_MAX_HEIGHT && longSide <= SMALL_ROCK_MAX_SIDE;
	}
}
