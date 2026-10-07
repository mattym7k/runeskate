package com.gielinorskate.leaderboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Runs waiting to be sent, in memory only: at most {@link #CAPACITY} (the oldest is dropped for a new one), each
 * retried with backoff from {@link #FIRST_BACKOFF_MS} doubling up to {@link #MAX_BACKOFF_MS}, or after the
 * server's Retry-After. Cleared on logout. Pure (times are passed in); one thread (the client thread).
 */
public final class SubmitQueue
{
	public static final int CAPACITY = 50;
	public static final long FIRST_BACKOFF_MS = 30_000L;
	public static final long MAX_BACKOFF_MS = 600_000L;

	/** A queued run, for one account. */
	public static final class Item
	{
		public final RunSubmission run;
		/** The account it was made for; never sent for another. */
		public final long accountHash;
		/** The week ("YYYY-MM-DD") it was queued in. */
		public final String week;
		int failures;
		long dueAt;
		/** A claim was already made for it (a second "not claimed" is retried later, not claimed again). */
		boolean claimed;

		Item(RunSubmission run, long accountHash, long dueAt)
		{
			this.run = run;
			this.accountHash = accountHash;
			this.dueAt = dueAt;
			this.week = WeekStart.key(dueAt);
		}

		public int failures()
		{
			return failures;
		}

		public long dueAt()
		{
			return dueAt;
		}
	}

	private final List<Item> items = new ArrayList<>();

	/** Queues a run, due now. An XP run replaces any XP run still waiting (only the newest XP matters). */
	public Item add(RunSubmission run, long accountHash, long now)
	{
		if (RunSubmission.XP.equals(run.kind))
		{
			items.removeIf(i -> RunSubmission.XP.equals(i.run.kind) && i.accountHash == accountHash);
		}
		while (items.size() >= CAPACITY)
		{
			items.remove(0);
		}
		Item item = new Item(run, accountHash, now);
		items.add(item);
		return item;
	}

	/** The oldest item due at {@code now}, or null. It stays queued until {@link #done} or {@link #retryLater}. */
	public Item due(long now)
	{
		for (Item i : items)
		{
			if (i.dueAt <= now)
			{
				return i;
			}
		}
		return null;
	}

	/** Sent (or never to be retried): removed. */
	public void done(Item item)
	{
		items.remove(item);
	}

	/** Failed: due again after the backoff, or after {@code retryAfterMs} when the server gave one. */
	public void retryLater(Item item, long now, Long retryAfterMs)
	{
		if (!items.contains(item))
		{
			return;
		}
		item.failures++;
		item.dueAt = now + (retryAfterMs != null ? retryAfterMs : backoffMs(item.failures));
	}

	/** Due again now (after a claim). */
	public void retryNow(Item item, long now)
	{
		item.dueAt = now;
	}

	/** Backoff after {@code failures} failures: 30 s, 60 s, 120 s ... at most 10 min. */
	public static long backoffMs(int failures)
	{
		long ms = FIRST_BACKOFF_MS;
		for (int i = 1; i < failures && ms < MAX_BACKOFF_MS; i++)
		{
			ms *= 2;
		}
		return Math.min(ms, MAX_BACKOFF_MS);
	}

	/** The highest combo still queued (or in flight) for {@code accountHash} from week {@code week}; 0 when none. */
	public long highestCombo(long accountHash, String week)
	{
		long best = 0;
		for (Item i : items)
		{
			if (RunSubmission.COMBO.equals(i.run.kind) && i.accountHash == accountHash && i.week.equals(week)
				&& i.run.score != null)
			{
				best = Math.max(best, i.run.score);
			}
		}
		return best;
	}

	public boolean contains(Item item)
	{
		return items.contains(item);
	}

	/** Drops every item of other accounts than {@code accountHash}. */
	public void keepOnly(long accountHash)
	{
		for (Iterator<Item> it = items.iterator(); it.hasNext(); )
		{
			if (it.next().accountHash != accountHash)
			{
				it.remove();
			}
		}
	}

	public void clear()
	{
		items.clear();
	}

	public int size()
	{
		return items.size();
	}

	public List<Item> items()
	{
		return Collections.unmodifiableList(items);
	}
}
