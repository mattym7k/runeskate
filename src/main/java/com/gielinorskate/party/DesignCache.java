package com.gielinorskate.party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
* Party members' complete designs, in memory only: at most {@code capacity}, the least recently used dropped
* first. Whatever leaves (dropped, a member forgotten, cleared) goes to the listener, so its colours can be let go.
* Pure; one thread.
*/
@RequiredArgsConstructor
final class DesignCache<V>
{
private final int capacity;
private final Consumer<V> onRemoved;
/** By "member:hash8", least recently used first. */
private final Map<String, Kept<V>> map = new LinkedHashMap<String, Kept<V>>(16, 0.75f, true)
{
@Override
protected boolean removeEldestEntry(Map.Entry<String, Kept<V>> eldest)
{
boolean full = size() > capacity;
if (full)
onRemoved.accept(eldest.getValue().value);
return full;
}
};

@AllArgsConstructor
private static final class Kept<V>
{
final long member;
final V value;
}

/** The member's design with this hash (8 hex), or null; counts as a use. */
V get(long member, String hash)
{
Kept<V> e = map.get(member + ":" + hash);
return e == null ? null : e.value;
}

/** True if it is here, without counting as a use. */
boolean contains(long member, String hash)
{
return map.containsKey(member + ":" + hash);
}

/** Keeps a design, dropping the least recently used beyond the capacity. */
void put(long member, String hash, V value)
{
Kept<V> old = map.put(member + ":" + hash, new Kept<>(member, value));
if (old != null && old.value != value)
onRemoved.accept(old.value);
}

/** Drops one member's designs; true if there were any. */
boolean forget(long member)
{
return remove(e -> e.member == member);
}

/** Drops every design; true if there were any. */
boolean clear()
{
return remove(e -> true);
}

private boolean remove(Predicate<Kept<V>> which)
{
return map.values().removeIf(e ->
{
boolean gone = which.test(e);
if (gone)
onRemoved.accept(e.value);
return gone;
});
}

int size()
{
return map.size();
}
}
