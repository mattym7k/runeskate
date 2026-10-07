package com.gielinorskate.progression;

/**
 * One board design from designs.json: the colours of one part (grip, deck or wheels), unlocked at a Skating level.
 * Saved per account and sent to party members by {@link #id}, which never changes. A player's own design
 * ({@link #custom}, id CUSTOM_ and 8 hex digits) is always unlocked, never goes to the party by its id (only as a
 * small picture when shared, see party.DesignShare) and gets a new {@link #revision} each time it is edited. A party
 * member's design received that way is custom too (id PARTY_...). Pure, immutable.
 */
public final class BoardDesign
{
	public final String id;
	public final String name;
	public final DesignPart part;
	/** The Skating level that unlocks it (1 = from the start). */
	public final int unlock;
	/** Who made it, or null. */
	public final String credit;
	/** True for a player's own design (saved on this computer only). */
	public final boolean custom;
	/** Changes whenever a custom design is edited, so a look holding the old colours differs (0 when shipped). */
	public final long revision;

	public BoardDesign(String id, String name, DesignPart part, int unlock, String credit)
	{
		this(id, name, part, unlock, credit, false, 0);
	}

	private BoardDesign(String id, String name, DesignPart part, int unlock, String credit, boolean custom,
		long revision)
	{
		this.id = id;
		this.name = name;
		this.part = part;
		this.unlock = unlock;
		this.credit = credit;
		this.custom = custom;
		this.revision = revision;
	}

	/** A player's own design: unlocked from level 1. */
	public static BoardDesign custom(String id, String name, DesignPart part, long revision)
	{
		return new BoardDesign(id, name, part, 1, null, true, revision);
	}

	public boolean isUnlocked(int skatingLevel)
	{
		return skatingLevel >= unlock;
	}

	/** The id without its part's prefix ("GRIP_RUNE" goes as "RUNE"): what party updates carry. */
	public String wireName()
	{
		return id.startsWith(part.prefix) ? id.substring(part.prefix.length()) : id;
	}

	@Override
	public String toString()
	{
		return id;
	}
}
