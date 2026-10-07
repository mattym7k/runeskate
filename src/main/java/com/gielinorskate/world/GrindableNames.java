package com.gielinorskate.world;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides from an object's name whether it looks like something a skater would grind (fences, rails,
 * benches, tables, crates, low walls...). Pure; no client dependency.
 * <p>
 * Matching is per whole word (the name is split on anything that is not a letter), case-insensitive in
 * {@link Locale#ROOT}, and accepts a simple plural ("fences", "benches"), so "Trellis" does not hit
 * "rail" and "Tablet" does not hit "table". Any excluded word (stairs, steps, ladders, doors, trees,
 * bushes, rocks) vetoes the name: "If it looks like it should be grindable you should be able to grind
 * on it. Not stairs." Height is not checked here; the caller limits model height separately
 * ({@link GridCollisionWorld#GRIND_WALL_MIN}..{@link GridCollisionWorld#GRIND_WALL_MAX}), which is what
 * keeps tall walls and city gates out.
 */
public final class GrindableNames
{
	private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
		"fence", "railing", "rail", "banister", "balustrade", "bench", "table", "barrier", "ledge", "crate",
		"planter", "log", "trough", "counter", "stall", "wall", "barricade", "bollard", "pipe", "beam", "plank",
		"gate", "hurdle", "picket"));

	private static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList(
		"stair", "stairs", "staircase", "step", "ladder", "door", "tree", "bush", "rock", "null"));

	private GrindableNames()
	{
	}

	public static boolean looksGrindable(String name)
	{
		if (name == null)
		{
			return false;
		}
		boolean hit = false;
		for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z]+"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			if (matches(EXCLUDED, word))
			{
				return false;
			}
			hit |= matches(KEYWORDS, word);
		}
		return hit;
	}

	/** The word itself or a simple plural of it ("-s", "-es") is in the set. */
	static boolean matches(Set<String> set, String word)
	{
		if (set.contains(word))
		{
			return true;
		}
		if (word.endsWith("es") && set.contains(word.substring(0, word.length() - 2)))
		{
			return true;
		}
		return word.endsWith("s") && set.contains(word.substring(0, word.length() - 1));
	}
}
