package com.gielinorskate.party;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A synthetic skate path through a real sender, Gson and a simulated network (150 ms +- 80 ms, 5 % loss, a 0.5 s
 * stall every 9 s) into a receiver: the timeline's playback against the true path at the playback time, and the
 * old dead reckoning (the same updates without their timeline) at its best constant lag.
 */
public class GhostPlaybackSimulationTest
{
	private static final float SECONDS = 90f;
	private static final float SKIP = 3f;

	@Test
	public void playbackFollowsTheTruePathCloselyAndSmoothlyAndBeatsDeadReckoning()
	{
		GhostSim.Net net = new GhostSim.Net();
		GhostSim timed = new GhostSim();
		timed.run(SECONDS, net);
		GhostSim.Stats t = timed.stats(SKIP, true, 0);
		GhostSim.Stats tBest = timed.bestLag(SKIP);

		GhostSim.Net oldNet = new GhostSim.Net();
		oldNet.old = true;
		GhostSim old = new GhostSim();
		old.run(SECONDS, oldNet);
		GhostSim.Stats o = old.bestLag(SKIP);

		double minDelay = Double.MAX_VALUE;
		double maxDelay = 0;
		double minRate = Double.MAX_VALUE;
		double maxRate = 0;
		double maxRateChange = 0;
		int underrunFrames = 0;
		int frames = 0;
		double[] prev = null;
		for (double[] d : timed.drawn)
		{
			if (d[0] < SKIP)
			{
				continue;
			}
			frames++;
			minDelay = Math.min(minDelay, d[7]);
			maxDelay = Math.max(maxDelay, d[7]);
			minRate = Math.min(minRate, d[6]);
			maxRate = Math.max(maxRate, d[6]);
			if (d[8] != 0)
			{
				underrunFrames++;
			}
			else if (prev != null && prev[8] == 0)
			{
				maxRateChange = Math.max(maxRateChange, Math.abs(d[6] - prev[6]) / GhostSim.DRAW_DT);
			}
			prev = d;
		}
		System.out.println("timeline vs truth at playback time: " + t);
		System.out.println("timeline vs truth at best constant lag: " + tBest);
		System.out.println("dead reckoning (old) vs truth at best constant lag: " + o);
		System.out.println(String.format("playback delay %.2f..%.2f s, rate %.3f..%.3f changing at most %.2f/s with "
			+ "samples to play, "
			+ "buffer dry %.1f %% of frames; updates %d sent, %d delivered,"
			+ " biggest %d chars", minDelay, maxDelay, minRate, maxRate, maxRateChange, 100.0 * underrunFrames / frames,
			timed.sentUpdates, timed.delivered,
			timed.maxJson));

		assertTrue("biggest update " + timed.maxJson, timed.maxJson <= GhostWire.MAX_UPDATE_CHARS);
		// close to the true path, and much closer than dead reckoning was
		assertTrue(t.toString(), t.p95 < 12);
		assertTrue(t.toString(), t.max < 128);
		assertTrue(t + " vs " + o, t.mean * 4 < o.mean);
		assertTrue(t + " vs " + o, t.p95 * 4 < o.p95);
		// also against the true path at one constant delay (the playback time eases, never jumps)
		assertTrue(tBest + " vs " + o, tBest.mean * 3 < o.mean);
		assertTrue(tBest + " vs " + o, tBest.max < o.max);
		// no jumps: the velocity drawn never changes by more than a hard landing does in a frame
		assertTrue(t + " vs " + o, t.maxDv < o.maxDv);
		assertTrue(t.toString(), t.p99Dv < 100);
		assertTrue(t.toString(), t.maxDv < 200);
		// never a visible speed change of playback while there are samples to play: it eases (a slight fast-forward
		// only to catch up after the buffer ran dry)
		assertTrue("rate change " + maxRateChange, maxRateChange <= GhostTimeline.RATE_SLEW * 1.02);
		assertTrue(minRate + ".." + maxRate, minRate >= 0
			&& maxRate <= 1 + GhostTimeline.MAX_CATCH_UP + 1e-6);
		assertTrue("dry " + underrunFrames + " of " + frames, underrunFrames < frames * 0.05);
		assertTrue(minDelay + ".." + maxDelay, minDelay >= GhostClockSync.MIN_DELAY
			&& maxDelay <= GhostClockSync.MAX_DELAY);
	}

	/** Runs the timeline and the old dead reckoning under {@code net}; returns {timeline, old}. */
	private static GhostSim.Stats[] compare(String name, GhostSim.Net net)
	{
		GhostSim timed = new GhostSim();
		timed.run(SECONDS, net);
		GhostSim.Net oldNet = new GhostSim.Net();
		oldNet.latency = net.latency;
		oldNet.jitter = net.jitter;
		oldNet.loss = net.loss;
		oldNet.stall = net.stall;
		oldNet.stallEvery = net.stallEvery;
		oldNet.old = true;
		GhostSim old = new GhostSim();
		old.run(SECONDS, oldNet);
		GhostSim.Stats t = timed.stats(SKIP, true, 0);
		GhostSim.Stats tBest = timed.bestLag(SKIP);
		GhostSim.Stats o = old.bestLag(SKIP);
		double delay = timed.drawn.get(timed.drawn.size() - 1)[7];
		System.out.println(name + ": timeline " + t + " | at best constant lag " + tBest + " | old " + o
			+ String.format(" | delay %.2f s", delay));
		return new GhostSim.Stats[]{tBest, o};
	}

	@Test
	public void aGoodConnectionGetsAShorterDelayAndAHarshOneStaysSmooth()
	{
		GhostSim.Net lan = new GhostSim.Net();
		lan.latency = 0.03f;
		lan.jitter = 0.01f;
		lan.loss = 0f;
		lan.stall = 0f;
		GhostSim.Stats[] good = compare("good connection (30 +- 10 ms, no loss)", lan);
		assertTrue(good[0].lag < 0.75);
		assertTrue(good[0].mean * 3 < good[1].mean);

		GhostSim.Net harsh = new GhostSim.Net();
		harsh.latency = 0.25f;
		harsh.jitter = 0.15f;
		harsh.loss = 0.1f;
		harsh.stall = 0.8f;
		harsh.stallEvery = 7f;
		GhostSim.Stats[] bad = compare("harsh connection (250 +- 150 ms, 10 % loss, 0.8 s stall every 7 s)", harsh);
		assertTrue(bad[0].mean * 2 < bad[1].mean);
		assertTrue(bad[0].maxDv < bad[1].maxDv / 10);
	}
}
