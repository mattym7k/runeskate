package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class GhostClockSyncTest
{
	/** Our clock minus the sender's. */
	private static final double OFFSET = 1234.5;

	/**
	 * Updates every {@code every} seconds for {@code seconds}, delivered after {@code latency} +- {@code jitter}
	 * (uniform), {@code loss} of them lost, in arrival order (reordered by the jitter).
	 */
	private static GhostClockSync run(float every, float seconds, float latency, float jitter, float loss, long seed)
	{
		Random rnd = new Random(seed);
		List<double[]> arrivals = new ArrayList<>();
		for (double s = 0; s < seconds; s += every)
		{
			if (rnd.nextFloat() < loss)
			{
				continue;
			}
			double at = s + OFFSET + latency + (rnd.nextFloat() * 2 - 1) * jitter;
			arrivals.add(new double[]{at, s});
		}
		arrivals.sort((a, b) -> Double.compare(a[0], b[0]));
		GhostClockSync sync = new GhostClockSync();
		for (double[] a : arrivals)
		{
			double sent = sync.unwrap(GhostTrajectory.timeMs((float) a[1]));
			assertTrue(sync.observe((float) a[0], sent));
		}
		return sync;
	}

	@Test
	public void floorJitterAndDelayUnderLatencyJitterReorderingAndLoss()
	{
		GhostClockSync sync = run(0.6f, 60f, 0.15f, 0.08f, 0.05f, 1);
		// the quickest delivery is 70 ms: the floor is the clocks' difference plus about that
		assertEquals(OFFSET + 0.07, sync.floor(), 0.015);
		// 90 % of updates arrive within about 0.9 * 160 ms of the floor
		assertEquals(0.144, sync.jitter(), 0.025);
		assertEquals(0.6, sync.gap(), 0.01);
		float expected = 0.6f + sync.jitter() + GhostClockSync.MARGIN;
		assertEquals(expected, sync.delay(), 1e-6);
		assertTrue(sync.delay() > 0.7f && sync.delay() < 0.85f);
	}

	@Test
	public void aQuietNetworkGetsAShortDelayAndAWildOneIsCapped()
	{
		GhostClockSync lan = run(0.6f, 30f, 0.02f, 0.005f, 0f, 2);
		assertEquals(0.6 + 0.01 + GhostClockSync.MARGIN, lan.delay(), 0.01);
		GhostClockSync wild = run(0.6f, 30f, 1f, 0.9f, 0f, 3);
		assertEquals(GhostClockSync.MAX_DELAY, wild.delay(), 1e-6);
		// event updates in between make the gaps shorter, and the delay with them (never under the minimum)
		GhostClockSync busy = run(0.1f, 30f, 0.02f, 0.005f, 0f, 4);
		assertEquals(GhostClockSync.MIN_DELAY, busy.delay(), 1e-6);
	}

	@Test
	public void aStandingSkatersKeepAlivesAreNotMotionGaps()
	{
		GhostClockSync sync = new GhostClockSync();
		float now = 100f;
		for (int i = 0; i < 10; i++)
		{
			// one every 10 s
			sync.observe(now + i * 10f, sync.unwrap(GhostTrajectory.timeMs(i * 10f)));
		}
		assertEquals(GhostClockSync.DEFAULT_GAP, sync.gap(), 1e-6);
	}

	@Test
	public void sendTimesUnwrapAcrossTheModulus()
	{
		GhostClockSync sync = new GhostClockSync();
		double wrap = GhostTrajectory.TIME_MOD / 1000.0;
		double first = sync.unwrap(GhostTrajectory.TIME_MOD - 500);
		assertEquals(wrap - 0.5, first, 1e-9);
		// 0.6 s later, past the wrap
		assertEquals(wrap + 0.1, sync.unwrap(100), 1e-9);
		// a late one from before it
		assertEquals(wrap - 0.2, sync.unwrap(GhostTrajectory.TIME_MOD - 200), 1e-9);
		assertEquals(wrap + 0.7, sync.unwrap(700), 1e-9);
	}

	@Test
	public void aRestartedSendersClockIsNoticed()
	{
		GhostClockSync sync = run(0.6f, 20f, 0.1f, 0.02f, 0f, 5);
		double floor = sync.floor();
		// the sender's plugin restarted: its clock is back near 0
		double sent = sync.unwrap(GhostTrajectory.timeMs(0.3f));
		assertFalse(sync.observe((float) (OFFSET + 20.4), sent));
		assertEquals(floor, sync.floor(), 1e-9);
		sync.clear();
		assertFalse(sync.has());
		assertTrue(sync.observe((float) (OFFSET + 20.4), sync.unwrap(GhostTrajectory.timeMs(0.3f))));
	}
}
