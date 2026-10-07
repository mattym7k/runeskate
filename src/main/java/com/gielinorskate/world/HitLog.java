package com.gielinorskate.world;

import com.gielinorskate.physics.Contact;
import java.util.HashSet;
import java.util.Set;

/**
 * The ::skateboxes chat line naming what the skater ran into: "Hit: Rocks (game object)", posted once per
 * blocker until {@link #reset}. Pure; use from the client thread only.
 */
public final class HitLog
{
	private final Set<Integer> boxes = new HashSet<>();
	private final Set<String> unnumbered = new HashSet<>();

	/** "Name (kind)" for a blocker's debug name; unnamed objects ("null") show their id instead. */
	public static String label(String name, int id, String kind)
	{
		String n = name == null || name.isEmpty() || "null".equalsIgnoreCase(name) ? "Object " + id : name;
		return n + " (" + kind + ")";
	}

	/**
	 * The chat line for a contact with blocker {@code box} named {@code label}, or null when that blocker
	 * was already reported (blockers the world could not number are told apart by name).
	 */
	public String report(int box, String label)
	{
		String name = label == null ? "unnamed blocker" : label;
		boolean fresh = box == Contact.UNKNOWN_BOX ? unnumbered.add(name) : boxes.add(box);
		return fresh ? "Hit: " + name : null;
	}

	/** Forgets what was reported (a new world, or the debug view toggled). */
	public void reset()
	{
		boxes.clear();
		unnumbered.clear();
	}
}
