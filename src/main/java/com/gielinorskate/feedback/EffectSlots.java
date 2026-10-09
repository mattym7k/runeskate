package com.gielinorskate.feedback;

import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
* Live effects with an expiry time, capped at {@link #capacity}: adding to a full set evicts the oldest. Pure;
* the caller despawns whatever comes back.
*/
@RequiredArgsConstructor
final class EffectSlots<T>
{
@AllArgsConstructor
private static final class Slot<T>
{
final T item;
final float expiresAt;
}

private final int capacity;
private final Deque<Slot<T>> slots = new ArrayDeque<>();

/** Adds {@code item} living {@code lifetime} seconds; returns the evicted oldest item when full, else null. */
T add(T item, float now, float lifetime)
{
T evicted = slots.size() >= capacity ? slots.pollFirst().item : null;
slots.addLast(new Slot<>(item, now + lifetime));
return evicted;
}

/** Removes and returns every item whose time is up. */
List<T> expire(float now)
{
List<T> done = new ArrayList<>(2);
slots.removeIf(s -> now >= s.expiresAt && done.add(s.item));
return done;
}

/** Removes and returns everything. */
List<T> clear()
{
List<T> all = slots.stream().map(s -> s.item).collect(Collectors.toList());
slots.clear();
return all;
}

int size()
{
return slots.size();
}
}
