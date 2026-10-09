package com.gielinorskate.progression;

import java.util.Arrays;
import java.util.List;

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
static final String XP_KEY = "skateXp";
/**
* Saved keys of the chosen designs' ids (profile config) by part: grip, deck, wheels; never renamed. skateDeck
* held the old ladder deck's name, which is still a deck design id.
*/
static final List<String> DESIGN_KEYS = Arrays.asList("skateGrip", "skateDeck", "skateWheels");
/** Seconds after the latest gain before it is saved. */
public static final float SAVE_DELAY_SECONDS = 5f;
/** Seconds a gain may stay unsaved while new ones keep coming. */
public static final float MAX_UNSAVED_SECONDS = 30f;

private final ProfileStore store;
private final BoardDesigns designs = BoardDesigns.bundled();
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
this.store = store;
}

/** Saves what is pending for the previous account, then loads the current one's. */
public void load()
{
flush();
profile = store.profile();
xp = profile == null ? 0 : SkateLevels.parseXp(store.get(profile, XP_KEY));
for (DesignPart p : DesignPart.values())
designIds[p.ordinal()] = profile == null ? null : store.get(profile, DESIGN_KEYS.get(p.ordinal()));
dirty = false;
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
int before = level();
int after = SkateLevels.add(xp, amount);
if (after == xp)
return null;
xp = after;
if (!dirty)
{
dirty = true;
firstUnsaved = now;
}
lastGain = now;
return level() > before ? new LevelUp(before, level()) : null;
}

/**
* The board's designs in use: each part's chosen design, or its default when none was chosen, the saved id is
* unknown (an old deck name no longer on the ladder, CLASSIC included) or it is locked at the current level.
*/
public BoardLook look()
{
BoardDesign[] d = new BoardDesign[designIds.length];
for (DesignPart p : DesignPart.values())
d[p.ordinal()] = designs.usable(p, designIds[p.ordinal()], level());
return new BoardLook(d[0], d[1], d[2]);
}

/** Chooses a design for its part (saved at once); false, and nothing changes, when it is still locked. */
public boolean select(BoardDesign design)
{
if (design == null || designs.byId(design.id) == null || !design.isUnlocked(level()))
return false;
designIds[design.part.ordinal()] = design.id;
if (profile != null)
store.set(profile, DESIGN_KEYS.get(design.part.ordinal()), design.id);
return true;
}

/** Saves pending gains once they have been quiet long enough (or waited too long). Call now and then. */
public void tick(float now)
{
if (dirty && (now - lastGain >= SAVE_DELAY_SECONDS || now - firstUnsaved >= MAX_UNSAVED_SECONDS))
flush();
}

/** Saves pending gains now. */
public void flush()
{
if (dirty && profile != null)
store.set(profile, XP_KEY, Integer.toString(xp));
dirty = false;
}
}
