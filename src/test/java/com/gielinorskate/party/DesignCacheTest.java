package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DesignCacheTest
{
	private final List<String> removed = new ArrayList<>();
	private final DesignCache<String> cache = new DesignCache<>(DesignShare.CACHE_SIZE, removed::add);

	private static String hash(int i)
	{
		return String.format("%08x", i);
	}

	@Test
	public void keepsTwentyAndDropsTheLeastRecentlyUsed()
	{
		for (int i = 0; i < 20; i++)
		{
			cache.put(1L, hash(i), "d" + i);
		}
		assertEquals(20, cache.size());
		assertTrue(removed.isEmpty());
		// using d0 makes d1 the least recently used
		assertEquals("d0", cache.get(1L, hash(0)));
		cache.put(2L, hash(99), "x");
		assertEquals(20, cache.size());
		assertEquals(Arrays.asList("d1"), removed);
		assertNull(cache.get(1L, hash(1)));
		assertEquals("d0", cache.get(1L, hash(0)));
	}

	@Test
	public void designsAreKeptPerMember()
	{
		cache.put(1L, hash(5), "a");
		cache.put(2L, hash(5), "b");
		assertEquals("a", cache.get(1L, hash(5)));
		assertEquals("b", cache.get(2L, hash(5)));
		cache.forget(1L);
		assertNull(cache.get(1L, hash(5)));
		assertEquals("b", cache.get(2L, hash(5)));
		assertEquals(Arrays.asList("a"), removed);
	}

	@Test
	public void clearingLetsEveryDesignGo()
	{
		cache.put(1L, hash(1), "a");
		cache.put(1L, hash(2), "b");
		cache.clear();
		assertEquals(0, cache.size());
		assertEquals(Arrays.asList("a", "b"), removed);
	}

	@Test
	public void replacingOneLetsTheOldGo()
	{
		cache.put(1L, hash(1), "a");
		cache.put(1L, hash(1), "a2");
		assertEquals(Arrays.asList("a"), removed);
		assertEquals("a2", cache.get(1L, hash(1)));
	}
}
