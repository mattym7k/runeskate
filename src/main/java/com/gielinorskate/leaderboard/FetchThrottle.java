package com.gielinorskate.leaderboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side limit on leaderboard requests: a board fetched less than {@link #FRESH_MS} ago is shown from cache
 * (even when Refresh is pressed), and no two requests go out within {@link #MIN_GAP_MS}. Pure (times passed in).
 */
public final class FetchThrottle
{
	public static final long FRESH_MS = 30_000L;
	public static final long MIN_GAP_MS = 2_000L;

	private final Map<String, Long> fetchedAt = new HashMap<>();
	private long lastRequest = Long.MIN_VALUE / 2;

	/** True when board {@code key} may be fetched now (and records the request). */
	public boolean tryFetch(String key, long now)
	{
		Long at = fetchedAt.get(key);
		if (at != null && now - at < FRESH_MS)
		{
			return false;
		}
		if (now - lastRequest < MIN_GAP_MS)
		{
			return false;
		}
		fetchedAt.put(key, now);
		lastRequest = now;
		return true;
	}

	/** A failed fetch may be tried again sooner (still no faster than {@link #MIN_GAP_MS}). */
	public void failed(String key)
	{
		fetchedAt.remove(key);
	}

	/** Milliseconds until board {@code key} may be fetched again (0 when it may now). */
	public long waitMs(String key, long now)
	{
		Long at = fetchedAt.get(key);
		long fresh = at == null ? 0 : Math.max(0, FRESH_MS - (now - at));
		return Math.max(fresh, Math.max(0, MIN_GAP_MS - (now - lastRequest)));
	}

	public void clear()
	{
		fetchedAt.clear();
	}
}
