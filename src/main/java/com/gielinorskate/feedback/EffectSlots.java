package com.gielinorskate.feedback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Live effects with an expiry time, capped at {@link #capacity}: adding to a full set evicts the oldest. Pure;
 * the caller despawns whatever comes back.
 */
final class EffectSlots<T>
{
	private static final class Slot<T>
	{
		final T item;
		final float expiresAt;

		Slot(T item, float expiresAt)
		{
			this.item = item;
			this.expiresAt = expiresAt;
		}
	}

	private final int capacity;
	private final Deque<Slot<T>> slots = new ArrayDeque<>();

	EffectSlots(int capacity)
	{
		this.capacity = capacity;
	}

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
		slots.removeIf(s ->
		{
			if (now >= s.expiresAt)
			{
				done.add(s.item);
				return true;
			}
			return false;
		});
		return done;
	}

	/** Removes and returns everything. */
	List<T> clear()
	{
		List<T> all = new ArrayList<>(slots.size());
		for (Slot<T> s : slots)
		{
			all.add(s.item);
		}
		slots.clear();
		return all;
	}

	int size()
	{
		return slots.size();
	}
}
