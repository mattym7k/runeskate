package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

public class GhostTrajectoryTest
{
	private static GhostState at(float x, float y, float h, float heading, SkaterState st)
	{
		return GhostFeed.frame(330, 1, x, y, h, heading, 0f, 0f, 0f, st, null, null, 0f);
	}

	@Test
	public void samplesAndEventTimesRoundTripWithoutDrift()
	{
		float[] ago = {0.15f, 0.31f, 0.45f, 0.6f};
		float[] xs = {1_409_600.4f, 1_409_380.6f, 1_409_100.2f, 1_408_830f};
		float[] ys = {1_411_190f, 1_411_150f, 1_411_080f, 1_410_990f};
		float[] hs = {-120f, -40f, 0f, 3f};
		// headings across the +-PI seam
		float[] hd = {3.10f, -3.12f, -3.0f, 2.9f};
		SkaterState[] st = {SkaterState.AIRBORNE, SkaterState.AIRBORNE, SkaterState.ROLLING, SkaterState.GRINDING};
		float[] eventAgo = new float[GhostTrajectory.EVENT_BITS];
		eventAgo[0] = 0.33f;
		eventAgo[6] = 0f;
		int ev = GhostCodec.EV_POP | GhostCodec.EV_TRICK;
		String tj = GhostFeed.wire(123_456, ev, eventAgo, 4, ago, xs, ys, hs, hd, st, 1_409_664, 1_411_200,
			-150, -3140);
		GhostTrajectory back = GhostTrajectory.decode(tj, ev, 1_409_664, 1_411_200, -150, -3140);
		assertNotNull(back);
		assertEquals(123_456, back.timeMs);
		assertEquals(0.33f, back.eventAgo[0], 1e-4f);
		assertEquals(0f, back.eventAgo[6], 1e-4f);
		assertTrue(Float.isNaN(back.eventAgo[1]));
		assertEquals(4, back.count);
		for (int i = 0; i < 4; i++)
		{
			assertEquals(ago[i], back.ago[i], 0.005f + 1e-4f);
			assertEquals(Math.round(xs[i]), back.x[i], 0f);
			assertEquals(Math.round(ys[i]), back.y[i], 0f);
			assertEquals(Math.round(hs[i]), back.h[i], 0f);
			// never more than half a unit off, however long the chain
			assertTrue(back.heading[i] + " vs " + hd[i], Angles.absDiff(back.heading[i], hd[i]) <= 0.005f + 1e-4f);
			assertEquals(st[i], back.state[i]);
		}
		// four samples of fast skating (about 1900 u/s, rising and falling) in about 10 characters each
		assertTrue(tj, tj.length() <= 44);
	}

	@Test
	public void junkAndOtherVersionsAreIgnored()
	{
		assertNull(GhostTrajectory.decode(null, 0, 0, 0, 0, 0));
		assertNull(GhostTrajectory.decode("", 0, 0, 0, 0, 0));
		assertNull(GhostTrajectory.decode("!!!", 0, 0, 0, 0, 0));
		// header says 2 samples, none there
		assertNull(GhostTrajectory.decode("4000", 0, 0, 0, 0, 0));
		// version 1
		assertNull(GhostTrajectory.decode(GhostWire.ALPHABET.charAt(16) + "000", 0, 0, 0, 0, 0));
		// just a time
		GhostTrajectory t = GhostTrajectory.decode("0001", 0, 0, 0, 0, 0);
		assertNotNull(t);
		assertEquals(1, t.timeMs);
		assertEquals(0, t.count);
	}

	@Test
	public void theSendTimeWrapsEvery262Seconds()
	{
		assertEquals(0, GhostTrajectory.timeMs(0f));
		assertEquals(1500, GhostTrajectory.timeMs(1.5f));
		assertEquals(1000, GhostTrajectory.timeMs(GhostTrajectory.TIME_MOD / 1000f + 1f), 1);
	}

	@Test
	public void theTrailSendsThePositionsSinceTheLastUpdateNewestFirst()
	{
		GhostTrail trail = new GhostTrail();
		float now = 10f;
		for (int i = 0; i <= 60; i++)
		{
			float tt = now + i * 0.02f;
			trail.record(at(1000f + i * 10f, 500f, 0f, 0.5f, SkaterState.ROLLING), tt);
		}
		float sendAt = now + 1.2f;
		SkateGhostUpdate m = GhostCodec.encode(at(1000f + 600f, 500f, 0f, 0.5f, SkaterState.ROLLING), 0, null);
		trail.sent(now + 0.6f);
		String tj = trail.encode(m, sendAt, GhostTrail.MAX_SAMPLES, true);
		GhostTrajectory back = GhostTrajectory.decode(tj, 0, m.x, m.y, m.h, m.hd);
		assertNotNull(back);
		// positions newer than the update before (0.6 s) and at least 0.05 s older than this one
		assertTrue(back.count >= 3 && back.count <= 4);
		for (int i = 0; i < back.count; i++)
		{
			float ago = back.ago[i];
			assertTrue(ago >= 0.05f && ago < 0.6f);
			if (i > 0)
			{
				assertTrue(ago > back.ago[i - 1]);
			}
			// 10 units per 0.02 s: x tracks its time
			assertEquals(1600f - ago * 500f, back.x[i], 11f);
		}
	}

	@Test
	public void anotherWorldOrPlaneOrATeleportEndsTheChain()
	{
		GhostTrail trail = new GhostTrail();
		trail.record(at(0f, 0f, 0f, 0f, SkaterState.ROLLING), 0f);
		trail.record(at(5000f, 0f, 0f, 0f, SkaterState.ROLLING), 0.2f);
		trail.record(at(5050f, 0f, 0f, 0f, SkaterState.ROLLING), 0.4f);
		SkateGhostUpdate m = GhostCodec.encode(at(5100f, 0f, 0f, 0f, SkaterState.ROLLING), 0, null);
		GhostTrajectory back = GhostTrajectory.decode(trail.encode(m, 0.6f, 4, false), 0, m.x, m.y, m.h, m.hd);
		assertEquals(2, back.count);
		m.p = 2;
		back = GhostTrajectory.decode(trail.encode(m, 0.6f, 4, false), 0, m.x, m.y, m.h, m.hd);
		assertEquals(0, back.count);
	}

	@Test
	public void eventTimesAreHowLongBeforeTheSend()
	{
		GhostTrail trail = new GhostTrail();
		trail.noteEvents(GhostCodec.EV_POP, 3.0f);
		trail.noteEvents(GhostCodec.EV_LAND | GhostCodec.EV_TRICK, 3.4f);
		SkateGhostUpdate m = GhostCodec.encode(at(0f, 0f, 0f, 0f, SkaterState.ROLLING),
			GhostCodec.EV_POP | GhostCodec.EV_LAND | GhostCodec.EV_TRICK, null);
		GhostTrajectory back = GhostTrajectory.decode(trail.encode(m, 3.5f, 4, true), m.ev, m.x, m.y, m.h, m.hd);
		assertEquals(0.5f, back.eventAgo[0], 0.006f);
		assertEquals(0.1f, back.eventAgo[1], 0.006f);
		assertEquals(0.1f, back.eventAgo[6], 0.006f);
	}
}
