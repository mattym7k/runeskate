package com.gielinorskate.party;

import java.util.Arrays;

/**
 * Sender side of the timeline: the local skater's position every {@link #SPACING} seconds and when each event
 * last happened, so each update can carry the few positions since the update before it and its events' times
 * ({@link GhostTrajectory}). Updates go every 0.6 s while moving, so receivers get about 6-7 positions a second
 * without one message more. Pure; no allocation per frame.
 */
final class GhostTrail
{
	/** Positions per update at most (0.6 s of them). */
	static final int MAX_SAMPLES = 4;
	/** A position this close (seconds) to the update's own, or to the update before, adds nothing. */
	private static final float MIN_GAP = 0.05f;
	private static final int CAPACITY = 8;

	private final float[] t = new float[CAPACITY];
	private final GhostState[] frames = new GhostState[CAPACITY];
	/** Ring: the newest is at next - 1. */
	private int count;
	private int next;
	private float lastRecord = Float.NEGATIVE_INFINITY;
	private float lastSent = Float.NEGATIVE_INFINITY;
	private final float[] eventTime = new float[GhostTrajectory.EVENT_BITS];

	/** Scratch for one encode, newest first. */
	private final GhostTrajectory out = new GhostTrajectory(MAX_SAMPLES);
	private final float[] eventAgo = new float[GhostTrajectory.EVENT_BITS];

	GhostTrail()
	{
		Arrays.fill(eventTime, Float.NaN);
	}

	/** One local frame at {@code now}: kept when a spacing has passed since the last kept one. */
	void record(GhostState f, float now)
	{
		// Seconds between recorded positions.
		if (now - lastRecord < 0.15f && count > 0)
			return;
		lastRecord = now;
		t[next] = now;
		frames[next] = f;
		next = (next + 1) % CAPACITY;
		count = Math.min(CAPACITY, count + 1);
	}

	/** This frame's EV_* {@code bits} happened at {@code now}. */
	void noteEvents(int bits, float now)
	{
		for (int bit = 0; bit < GhostTrajectory.EVENT_BITS && bits != 0; bit++)
		{
			if ((bits & (1 << bit)) != 0)
				eventTime[bit] = now;
		}
	}

	/**
	 * The timeline for update {@code m} going out at {@code now}: its events' times when {@code withEvents}, and
	 * at most {@code maxSamples} positions since the update before (same world and plane, no teleport), newest
	 * first.
	 */
	String encode(SkateGhostUpdate m, float now, int maxSamples, boolean withEvents)
	{
		int n = 0;
		float refX = m.x;
		float refY = m.y;
		int limit = Math.min(maxSamples, MAX_SAMPLES);
		for (int k = 0; k < count && n < limit; k++)
		{
			int i = Math.floorMod(next - 1 - k, CAPACITY);
			GhostState f = frames[i];
			float age = now - t[i];
			if (age < MIN_GAP)
				continue;
			// Positions older than this never go.
			// One step of the chain longer than this (units) is a teleport: the chain stops before it.
			if (age > 1.0f || t[i] <= lastSent + MIN_GAP || f.world != m.w || f.plane != m.p
				|| Math.hypot(f.x - refX, f.y - refY) > 1024f)
				break;
			out.ago[n] = age;
			out.x[n] = f.x;
			out.y[n] = f.y;
			out.h[n] = f.h;
			out.heading[n] = f.heading;
			out.state[n] = f.state;
			refX = f.x;
			refY = f.y;
			n++;
		}
		for (int bit = 0; bit < GhostTrajectory.EVENT_BITS; bit++)
		{
			float e = eventTime[bit];
			eventAgo[bit] = Float.isNaN(e) ? 0f : Math.max(0f, now - e);
		}
		out.timeMs = GhostTrajectory.timeMs(now);
		out.eventAgo = withEvents ? eventAgo : null;
		out.count = n;
		return out.encode(m.ev, m.x, m.y, m.h, m.hd);
	}

	/**
	 * Fits the timeline into {@code m} going out at {@code now}: as many positions as fit under
	 * {@link GhostWire#MAX_UPDATE_CHARS} with everything else the update has, then without positions, then without
	 * the event times; none at all when not even the send time fits (receivers then place it by its arrival).
	 */
	void fit(SkateGhostUpdate m, float now)
	{
		for (int n = MAX_SAMPLES; n >= -1; n--)
		{
			m.tj = n >= 0 ? encode(m, now, n, true) : encode(m, now, 0, false);
			if (GhostWire.jsonLength(m) <= GhostWire.MAX_UPDATE_CHARS)
				return;
		}
		m.tj = null;
	}

	/** An update went out at {@code now}: the next one carries only positions after it. */
	void sent(float now)
	{
		lastSent = now;
	}

	/** Sharing (re)starts: nothing recorded before carries over. */
	void clear()
	{
		count = 0;
		next = 0;
		lastRecord = Float.NEGATIVE_INFINITY;
		lastSent = Float.NEGATIVE_INFINITY;
		Arrays.fill(eventTime, Float.NaN);
	}
}
