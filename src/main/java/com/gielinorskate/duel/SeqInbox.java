package com.gielinorskate.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Puts one sender's messages back into its seq order: a message at or below the last one applied is stale and
 * dropped, one ahead of a gap is held until the gap fills. A gap still open after {@link #GAP_TIMEOUT} s (a
 * message lost on the way) is skipped so the duel goes on. Pure; times are seconds on one monotonic clock.
 */
final class SeqInbox<T>
{
	static final float GAP_TIMEOUT = 3f;
	/** Early messages held at most; more are dropped (a sender never has that many in flight). */
	static final int MAX_HELD = 64;

	private final TreeMap<Integer, T> held = new TreeMap<>();
	private int last;
	private float gapSince = Float.NaN;

	/** Starts over: {@code lastApplied} is the seq already seen (the next expected is one more). */
	void reset(int lastApplied)
	{
		held.clear();
		last = lastApplied;
		gapSince = Float.NaN;
	}

	/** Takes message {@code seq}; returns the messages now ready, in seq order (often just this one). */
	List<T> offer(int seq, T item, float now)
	{
		if (seq <= last || held.containsKey(seq) || held.size() >= MAX_HELD)
		{
			return Collections.emptyList();
		}
		held.put(seq, item);
		return drain(now);
	}

	/** The held messages ready at {@code now}: the next in order, or past a gap that timed out. */
	List<T> drain(float now)
	{
		if (held.isEmpty())
		{
			return Collections.emptyList();
		}
		List<T> out = new ArrayList<>(1);
		while (!held.isEmpty())
		{
			Map.Entry<Integer, T> first = held.firstEntry();
			if (first.getKey() != last + 1)
			{
				if (Float.isNaN(gapSince))
				{
					gapSince = now;
				}
				if (now - gapSince < GAP_TIMEOUT)
				{
					break;
				}
				// the missing one is not coming: go on from the next held
			}
			held.pollFirstEntry();
			last = first.getKey();
			gapSince = Float.NaN;
			out.add(first.getValue());
		}
		return out;
	}

	int last()
	{
		return last;
	}

	int held()
	{
		return held.size();
	}
}
