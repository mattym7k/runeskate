package com.gielinorskate.party;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Party members' complete designs, in memory only: at most {@code capacity}, the least recently used dropped
 * first. Whatever leaves (dropped, a member forgotten, cleared) goes to the listener, so its colours can be let go.
 * Pure; one thread.
 */
public final class DesignCache<V>
{
	private final int capacity;
	private final Consumer<V> onRemoved;
	/** By "member:hash8", least recently used first. */
	private final LinkedHashMap<String, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);

	private static final class Entry<V>
	{
		final long member;
		final V value;

		Entry(long member, V value)
		{
			this.member = member;
			this.value = value;
		}
	}

	public DesignCache(int capacity, Consumer<V> onRemoved)
	{
		this.capacity = capacity;
		this.onRemoved = onRemoved;
	}

	private static String key(long member, String hash)
	{
		return member + ":" + hash;
	}

	/** The member's design with this hash (8 hex), or null; counts as a use. */
	public V get(long member, String hash)
	{
		Entry<V> e = map.get(key(member, hash));
		return e == null ? null : e.value;
	}

	/** True if it is here, without counting as a use. */
	public boolean contains(long member, String hash)
	{
		return map.containsKey(key(member, hash));
	}

	/** Keeps a design, dropping the least recently used beyond the capacity. */
	public void put(long member, String hash, V value)
	{
		Entry<V> old = map.put(key(member, hash), new Entry<>(member, value));
		if (old != null && old.value != value)
		{
			onRemoved.accept(old.value);
		}
		List<V> dropped = new ArrayList<>();
		for (Iterator<Entry<V>> it = map.values().iterator(); map.size() > capacity && it.hasNext(); )
		{
			dropped.add(it.next().value);
			it.remove();
		}
		dropped.forEach(onRemoved);
	}

	/** Drops one member's designs. */
	public void forget(long member)
	{
		List<V> dropped = new ArrayList<>();
		for (Iterator<Entry<V>> it = map.values().iterator(); it.hasNext(); )
		{
			Entry<V> e = it.next();
			if (e.member == member)
			{
				dropped.add(e.value);
				it.remove();
			}
		}
		dropped.forEach(onRemoved);
	}

	public void clear()
	{
		List<V> dropped = new ArrayList<>();
		for (Map.Entry<String, Entry<V>> e : map.entrySet())
		{
			dropped.add(e.getValue().value);
		}
		map.clear();
		dropped.forEach(onRemoved);
	}

	public int size()
	{
		return map.size();
	}
}
