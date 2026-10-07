package com.gielinorskate.party;

import java.util.Arrays;

/**
 * One party member's clock as seen from here, from the send times their updates carry
 * ({@link GhostTrajectory#timeMs}): the offset floor (the smallest arrival minus send time over a recent window:
 * the two clocks' difference plus the quickest delivery), the jitter (how much later than that floor updates
 * usually arrive), and how far apart their updates go. From these, the playback delay: how far behind the floor a
 * ghost is drawn so the next update has nearly always arrived before it is needed. Pure; nothing allocated per
 * update.
 */
final class GhostClockSync
{
	/** Updates remembered for the floor, jitter and gaps. */
	static final int WINDOW = 48;
	/** The floor and jitter come from updates that arrived within this many seconds. */
	static final float WINDOW_SECONDS = 20f;
	static final float MIN_DELAY = 0.25f;
	static final float MAX_DELAY = 1.2f;
	/** Gaps between updates longer than this are a standing skater's keep-alives, not motion: not counted. */
	static final float MAX_GAP = 1.25f;
	/** Before enough updates: the snapshot interval and a typical jitter. */
	static final float DEFAULT_GAP = GhostSendPolicy.SNAPSHOT_INTERVAL;
	static final float DEFAULT_JITTER = 0.1f;
	/** Added on top of the gap and jitter: a frame or two of slack. */
	static final float MARGIN = 0.04f;
	/** The share of gaps and late arrivals the delay covers. */
	static final float GAP_PERCENTILE = 0.9f;
	static final float JITTER_PERCENTILE = 0.9f;
	/** An arrival this far (seconds) off the floor is the sender's clock starting again (a restart), not delay. */
	static final float RESTART = 5f;

	private final double[] offset = new double[WINDOW];
	private final float[] arrived = new float[WINDOW];
	private int offsetCount;
	private int offsetNext;
	private final float[] gaps = new float[WINDOW];
	private int gapCount;
	private int gapNext;
	private final float[] scratch = new float[WINDOW];

	private boolean unwrapped;
	private int lastRaw;
	private double lastUnwrapped;
	private double newest = Double.NEGATIVE_INFINITY;

	private double floor;
	private float jitter = DEFAULT_JITTER;
	private float gap = DEFAULT_GAP;

	/** Some update's send time has been seen. */
	boolean has()
	{
		return offsetCount > 0;
	}

	/**
	 * The sender's time in seconds of a send time on the wire ({@code raw}, ms modulo 2^18), unwrapped against the
	 * one before (they are never more than half the modulus apart: a member silent that long has gone).
	 */
	double unwrap(int raw)
	{
		if (!unwrapped)
		{
			unwrapped = true;
			lastRaw = raw;
			lastUnwrapped = raw / 1000.0;
			return lastUnwrapped;
		}
		int mod = GhostTrajectory.TIME_MOD;
		int diff = Math.floorMod(raw - lastRaw + mod / 2, mod) - mod / 2;
		lastRaw = raw;
		lastUnwrapped += diff / 1000.0;
		return lastUnwrapped;
	}

	/**
	 * An update sent at {@code sent} (sender seconds) arrived at {@code now} (ours). Returns false, taking nothing
	 * in, when it is so far off the floor that the sender's clock must have started again: then {@link #clear} and
	 * start over.
	 */
	boolean observe(float now, double sent)
	{
		double o = now - sent;
		if (offsetCount > 0 && Math.abs(o - floor) > RESTART)
		{
			return false;
		}
		offset[offsetNext] = o;
		arrived[offsetNext] = now;
		offsetNext = (offsetNext + 1) % WINDOW;
		offsetCount = Math.min(WINDOW, offsetCount + 1);

		double min = o;
		for (int i = 0; i < offsetCount; i++)
		{
			if (now - arrived[i] <= WINDOW_SECONDS)
			{
				min = Math.min(min, offset[i]);
			}
		}
		floor = min;
		int n = 0;
		for (int i = 0; i < offsetCount; i++)
		{
			if (now - arrived[i] <= WINDOW_SECONDS)
			{
				scratch[n++] = (float) (offset[i] - floor);
			}
		}
		jitter = n < 4 ? Math.max(DEFAULT_JITTER, percentile(scratch, n, 1f)) : percentile(scratch, n, JITTER_PERCENTILE);

		if (sent > newest)
		{
			if (newest > Double.NEGATIVE_INFINITY && sent - newest <= MAX_GAP)
			{
				gaps[gapNext] = (float) (sent - newest);
				gapNext = (gapNext + 1) % WINDOW;
				gapCount = Math.min(WINDOW, gapCount + 1);
			}
			newest = sent;
		}
		if (gapCount >= 3)
		{
			System.arraycopy(gaps, 0, scratch, 0, gapCount);
			gap = percentile(scratch, gapCount, GAP_PERCENTILE);
		}
		return true;
	}

	/** The {@code p} quantile of the first {@code n} values of {@code v} (sorted in place); 0 for none. */
	private static float percentile(float[] v, int n, float p)
	{
		if (n == 0)
		{
			return 0f;
		}
		Arrays.sort(v, 0, n);
		return v[Math.min(n - 1, Math.max(0, (int) Math.ceil(p * n) - 1))];
	}

	/** Our clock minus theirs, plus the quickest recent delivery: an update sent at s arrives at s + floor or later. */
	double floor()
	{
		return floor;
	}

	/** How much later than the floor updates usually arrive, seconds. */
	float jitter()
	{
		return jitter;
	}

	/** How far apart (sender seconds) updates usually go while the skater moves. */
	float gap()
	{
		return gap;
	}

	/**
	 * Seconds behind the floor to draw at: the next update is due within a gap and arrives within the jitter,
	 * plus a little; within {@link #MIN_DELAY} and {@link #MAX_DELAY}.
	 */
	float delay()
	{
		return Math.max(MIN_DELAY, Math.min(MAX_DELAY, gap + jitter + MARGIN));
	}

	/** Forget everything (the sender restarted). */
	void clear()
	{
		offsetCount = 0;
		offsetNext = 0;
		gapCount = 0;
		gapNext = 0;
		unwrapped = false;
		newest = Double.NEGATIVE_INFINITY;
		floor = 0;
		jitter = DEFAULT_JITTER;
		gap = DEFAULT_GAP;
	}
}
