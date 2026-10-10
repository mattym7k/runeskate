package com.gielinorskate.progression;

import lombok.AllArgsConstructor;

/**
* One board design from designs.json: the colours of one part (grip, deck or wheels), unlocked at a Skating level.
* Saved per account and sent to party members by {@link #id}, which never changes. Pure, immutable.
*/
@AllArgsConstructor
public final class BoardDesign
{
public final String id;
public final String name;
public final DesignPart part;
/** The Skating level that unlocks it (1 = from the start). */
public final int unlock;
/** Who made it, or null. */
public final String credit;

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
