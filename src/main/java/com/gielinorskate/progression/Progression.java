package com.gielinorskate.progression;

/**
 * The logged-in account's Skate XP, kept in its RuneScape profile config. Gains are saved after a quiet spell
 * ({@link #SAVE_DELAY_SECONDS}), at least every {@link #MAX_UNSAVED_SECONDS} during a long streak, and at once
 * on {@link #flush()} (skating stops, logout, account change, plugin stop): never on every frame. Values go back
 * to the profile they were loaded from, so a gain is never written into the next account's profile. Pure apart
 * from its {@link ProfileStore}; client thread.
 */
public final class Progression
{
	/** Saved key of the XP (profile config); never renamed. */
	public static final String XP_KEY = "skateXp";
	/**
	 * Saved keys of the chosen designs' ids (profile config); never renamed. skateDeck held the old ladder deck's
	 * name, which is still a deck design id.
	 */
	public static final String DECK_KEY = "skateDeck";
	public static final String GRIP_KEY = "skateGrip";
	public static final String WHEELS_KEY = "skateWheels";
	/**
	 * Set to "true" (profile config) once the developer ::skatelevel command has set this account's level, so
	 * anything ranking accounts later can leave it out.
	 */
	public static final String DEV_LEVEL_KEY = "devLevelSet";
	/** Seconds after the latest gain before it is saved. */
	public static final float SAVE_DELAY_SECONDS = 5f;
	/** Seconds a gain may stay unsaved while new ones keep coming. */
	public static final float MAX_UNSAVED_SECONDS = 30f;

	private final ProfileStore store;
	private final BoardDesigns designs;
	/** The profile the values came from (and are saved to); null when none. */
	private String profile;
	private int xp;
	/** The chosen designs' saved ids by part (null before one was chosen). */
	private final String[] designIds = new String[DesignPart.values().length];
	private boolean dirty;
	private float firstUnsaved;
	private float lastGain;

	public Progression(ProfileStore store)
	{
		this(store, BoardDesigns.bundled());
	}

	public Progression(ProfileStore store, BoardDesigns designs)
	{
		this.store = store;
		this.designs = designs;
	}

	/** The saved key of a part's chosen design. */
	public static String keyOf(DesignPart part)
	{
		switch (part)
		{
			case GRIP:
				return GRIP_KEY;
			case DECK:
				return DECK_KEY;
			default:
				return WHEELS_KEY;
		}
	}

	/** Saves what is pending for the previous account, then loads the current one's. */
	public void load()
	{
		flush();
		profile = store.profile();
		xp = profile == null ? 0 : SkateLevels.parseXp(store.get(profile, XP_KEY));
		for (DesignPart p : DesignPart.values())
		{
			designIds[p.ordinal()] = profile == null ? null : store.get(profile, keyOf(p));
		}
		dirty = false;
	}

	/** True once ::skatelevel set this account's level by hand (the leaderboard hides it from the XP boards). */
	public boolean devLevelSet()
	{
		return profile != null && "true".equals(store.get(profile, DEV_LEVEL_KEY));
	}

	/** The account the values belong to, or null. */
	public String profile()
	{
		return profile;
	}

	public int xp()
	{
		return xp;
	}

	public int level()
	{
		return SkateLevels.level(xp);
	}

	/**
	 * Adds XP gained at score-clock time {@code now}.
	 *
	 * @return the level-up it caused (several levels at once give one, with the final level), or null
	 */
	public LevelUp addXp(int amount, float now)
	{
		if (amount <= 0)
		{
			return null;
		}
		int before = level();
		int after = SkateLevels.add(xp, amount);
		if (after == xp)
		{
			return null;
		}
		xp = after;
		if (!dirty)
		{
			dirty = true;
			firstUnsaved = now;
		}
		lastGain = now;
		int level = level();
		return level > before ? new LevelUp(before, level) : null;
	}

	/**
	 * The part's chosen design, or its default when none was chosen, the saved id is unknown (an old deck name
	 * no longer on the ladder, CLASSIC included) or it is locked at the current level.
	 */
	public BoardDesign design(DesignPart part)
	{
		return designs.usable(part, designIds[part.ordinal()], level());
	}

	/** The board's designs in use. */
	public BoardLook look()
	{
		return new BoardLook(design(DesignPart.GRIP), design(DesignPart.DECK), design(DesignPart.WHEELS));
	}

	/** Chooses a design for its part (saved at once); false, and nothing changes, when it is still locked. */
	public boolean select(BoardDesign design)
	{
		if (design == null || designs.byId(design.id) == null || !design.isUnlocked(level()))
		{
			return false;
		}
		designIds[design.part.ordinal()] = design.id;
		if (profile != null)
		{
			store.set(profile, keyOf(design.part), design.id);
		}
		return true;
	}

	/**
	 * Developer command: sets the XP to exactly the start of {@code level} (1..99), saved at once, and marks the
	 * account with {@link #DEV_LEVEL_KEY}. No level-up is reported.
	 */
	public void setLevelForDev(int level)
	{
		xp = SkateLevels.xpForLevel(Math.max(1, Math.min(SkateLevels.MAX_LEVEL, level)));
		dirty = false;
		if (profile != null)
		{
			store.set(profile, XP_KEY, Integer.toString(xp));
			store.set(profile, DEV_LEVEL_KEY, "true");
		}
	}

	/** Saves pending gains once they have been quiet long enough (or waited too long). Call now and then. */
	public void tick(float now)
	{
		if (dirty && (now - lastGain >= SAVE_DELAY_SECONDS || now - firstUnsaved >= MAX_UNSAVED_SECONDS))
		{
			flush();
		}
	}

	/** Saves pending gains now. */
	public void flush()
	{
		if (!dirty)
		{
			return;
		}
		dirty = false;
		if (profile != null)
		{
			store.set(profile, XP_KEY, Integer.toString(xp));
		}
	}
}
