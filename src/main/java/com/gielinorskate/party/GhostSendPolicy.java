package com.gielinorskate.party;

/**
 * When the local skater's state goes to the party. While another party member is skating (an audience),
 * events go out as they happen, a state snapshot at most once per game tick (0.6 s) and only when something
 * changed, and a keep-alive after 10 s without any message. With no audience only an "I'm skating" announce
 * goes out, at most once every {@link #ANNOUNCE_INTERVAL} s, so other skaters can discover this one. Never more
 * than {@link #MAX_PER_SECOND} messages in any one-second window: a token bucket of 2 tokens in which each spent
 * token comes back exactly 1 s after it was spent, so even a burst of events cannot put more than 2 messages
 * into any second (events held back coalesce into the next message). Times are seconds on one monotonic clock.
 * Pure.
 */
public final class GhostSendPolicy
{
	public static final int MAX_PER_SECOND = 2;
	/** One game tick. */
	public static final float SNAPSHOT_INTERVAL = 0.6f;
	/** Receivers expire a ghost after 15 s of silence, so an idle skater re-sends well before. */
	public static final float KEEP_ALIVE = 10f;
	/** Without an audience: one announce per 5 s, well inside the receivers' 15 s ghost expiry. */
	public static final float ANNOUNCE_INTERVAL = 5f;
	/** The limit's window, seconds. */
	public static final float WINDOW = 1f;

	/** Times of the latest sends, oldest first once full (a ring of MAX_PER_SECOND). */
	private final float[] recent = new float[MAX_PER_SECOND];
	private int recentCount;
	private int recentNext;
	private float lastSend = Float.NEGATIVE_INFINITY;
	private int seq;

	public GhostSendPolicy()
	{
		this(0);
	}

	/** Sequence numbers start just above {@code seqStart}. */
	public GhostSendPolicy(int seqStart)
	{
		this.seq = seqStart;
	}

	/** Sharing is on, this client is skating and in a party, and not in a PvP area. */
	public static boolean allowed(boolean inPvpArea, boolean sharing, boolean skating, boolean inParty)
	{
		return !inPvpArea && sharing && skating && inParty;
	}

	/** A message may go out at {@code now} without breaking the per-second limit. */
	public boolean hasToken(float now)
	{
		if (recentCount < MAX_PER_SECOND)
		{
			return true;
		}
		// the ring is full: recentNext is the oldest of the last MAX_PER_SECOND sends
		return now - recent[recentNext] >= WINDOW;
	}

	/**
	 * Whether to send the current state now.
	 *
	 * @param changed the state differs from the last one sent
	 * @param event an event (pop, land, trick...) is waiting to go out
	 */
	public boolean shouldSend(float now, boolean changed, boolean event)
	{
		if (!hasToken(now))
		{
			return false;
		}
		if (event)
		{
			return true;
		}
		float since = now - lastSend;
		return since >= KEEP_ALIVE || (changed && since >= SNAPSHOT_INTERVAL);
	}

	/**
	 * With nobody else skating: whether the cheap "I'm skating" announce is due. Only with every token free, so a
	 * stop right after it (skating toggled off) still fits in the limit.
	 */
	public boolean shouldAnnounce(float now)
	{
		return freeTokens(now) == MAX_PER_SECOND && now - lastSend >= ANNOUNCE_INTERVAL;
	}

	/** How many messages may go out at {@code now} without breaking the per-second limit. */
	public int freeTokens(float now)
	{
		int used = 0;
		for (int i = 0; i < recentCount; i++)
		{
			if (now - recent[i] < WINDOW)
			{
				used++;
			}
		}
		return MAX_PER_SECOND - used;
	}

	/** Records a message (update or stop) sent at {@code now}. */
	public void recordSend(float now)
	{
		recent[recentNext] = now;
		recentNext = (recentNext + 1) % MAX_PER_SECOND;
		recentCount = Math.min(MAX_PER_SECOND, recentCount + 1);
		lastSend = now;
	}

	/**
	 * Records a message that is not part of the skater's own stream (a custom design's offer or chunk) sent at
	 * {@code now}: it counts in the per-second limit but does not move the snapshot, keep-alive or announce timing.
	 */
	public void recordSideSend(float now)
	{
		recent[recentNext] = now;
		recentNext = (recentNext + 1) % MAX_PER_SECOND;
		recentCount = Math.min(MAX_PER_SECOND, recentCount + 1);
	}

	/** When the latest update, stop or duel message went out (negative infinity when none since a reset). */
	public float lastSend()
	{
		return lastSend;
	}

	/** Sharing (re)starts: the first snapshot may go out at once. The rate limit is kept. */
	public void reset()
	{
		lastSend = Float.NEGATIVE_INFINITY;
	}

	/** The next sequence number; never repeats while the plugin runs. */
	public int nextSeq()
	{
		return ++seq;
	}
}
