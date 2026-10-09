package com.gielinorskate.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* The OSRS-style hitsplats showing over the two duellists: each lives {@link #LIFE} s in one of
* {@link #SLOTS} places around the skater (as the game stacks hits that land together), a fifth at once
* replacing the oldest. Drawn by our own overlay only; no real actor's hitsplats are touched. Client thread.
*/
public final class DuelSplats
{
public static final float LIFE = 1.2f;
public static final int SLOTS = 4;

/** One hitsplat. */
public static final class Splat
{
/** Over the local skater (else over the opponent's ghost). */
public final boolean onMe;
public final int damage;
/** A max hit: drawn in the max-hit style. */
public final boolean max;
public final float born;
/** 0..SLOTS-1: where it sits around the skater. */
public final int slot;

Splat(boolean onMe, int damage, float born, int slot)
{
this.onMe = onMe;
this.damage = damage;
max = damage >= DuelDamage.MAX;
this.born = born;
this.slot = slot;
}

/** Opacity at {@code now}: full, then fading over the last 0.3 s. */
public float alpha(float now)
{
return Math.max(0f, Math.min(1f, (LIFE - (now - born)) / 0.3f));
}
}

private final List<Splat> splats = new ArrayList<>();

public void add(boolean onMe, int damage, float now)
{
List<Splat> mine = live(onMe, now);
int slot = 0;
while (slot < SLOTS && hasSlot(mine, slot))
slot++;
if (slot == SLOTS)
{
// all taken: the oldest (first added) makes way
Splat oldest = mine.get(0);
splats.remove(oldest);
slot = oldest.slot;
}
splats.add(new Splat(onMe, damage, now, slot));
}

private static boolean hasSlot(List<Splat> list, int slot)
{
return list.stream().anyMatch(s -> s.slot == slot);
}

/** The splats showing over one skater at {@code now}, oldest first. */
public List<Splat> live(boolean onMe, float now)
{
splats.removeIf(s -> now - s.born >= LIFE);
return splats.stream().filter(s -> s.onMe == onMe).collect(Collectors.toList());
}

public void clear()
{
splats.clear();
}
}
