package com.gielinorskate.leaderboard;

import java.util.ArrayList;
import java.util.List;

/**
* Runs waiting to be sent, in memory only: at most {@link #CAPACITY} (the oldest is dropped for a new one), each
* retried with backoff from 30 s doubling up to 10 min, or after the server's Retry-After. Cleared on logout. Pure
* (times are passed in); one thread (the client thread).
*/
public final class SubmitQueue
{
public static final int CAPACITY = 50;

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
week = WeekStart.key(dueAt);
}
}

private final List<Item> items = new ArrayList<>();

/** Queues a run, due now. An XP run replaces any XP run still waiting (only the newest XP matters). */
public Item add(RunSubmission run, long accountHash, long now)
{
if (RunSubmission.XP.equals(run.kind))
items.removeIf(i -> RunSubmission.XP.equals(i.run.kind) && i.accountHash == accountHash);
while (items.size() >= CAPACITY)
items.remove(0);
Item item = new Item(run, accountHash, now);
items.add(item);
return item;
}

/** The oldest item due at {@code now}, or null. It stays queued until {@link #done} or {@link #retryLater}. */
public Item due(long now)
{
return items.stream().filter(i -> i.dueAt <= now).findFirst().orElse(null);
}

/** Sent (or never to be retried): removed. */
public void done(Item item)
{
items.remove(item);
}

/** Failed: due again after the backoff, or after {@code retryAfterMs} when the server gave one. */
public void retryLater(Item item, long now, Long retryAfterMs)
{
if (items.contains(item))
{
item.failures++;
item.dueAt = now + (retryAfterMs != null ? retryAfterMs : backoffMs(item.failures));
}
}

/** Due again now (after a claim). */
public void retryNow(Item item, long now)
{
item.dueAt = now;
}

/** Backoff after {@code failures} failures: 30 s, 60 s, 120 s ... at most 10 min. */
public static long backoffMs(int failures)
{
return Math.min(600_000L, 30_000L << Math.max(0, Math.min(failures - 1, 5)));
}

/** The highest combo still queued (or in flight) for {@code accountHash} from week {@code week}; 0 when none. */
public long highestCombo(long accountHash, String week)
{
return items.stream().filter(i -> RunSubmission.COMBO.equals(i.run.kind) && i.accountHash == accountHash
&& i.week.equals(week)).mapToLong(i -> i.run.score).max().orElse(0);
}

public boolean contains(Item item)
{
return items.contains(item);
}

/** Drops every item of other accounts than {@code accountHash}. */
public void keepOnly(long accountHash)
{
items.removeIf(i -> i.accountHash != accountHash);
}

public void clear()
{
items.clear();
}

public int size()
{
return items.size();
}

List<Item> items()
{
return items;
}
}
