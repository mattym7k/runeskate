package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GhostSendPolicyTest
{
	private static final float FRAME = 1f / 50f;

	@Test
	public void neverMoreThanTwoPerSecondEvenUnderEventSpam()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		List<Float> sent = new ArrayList<>();
		for (int i = 0; i < 500; i++)
		{
			float t = i * FRAME;
			// an event on every frame and the state always changing
			if (p.shouldSend(t, true, true))
			{
				p.recordSend(t);
				sent.add(t);
			}
		}
		// 10 s at 2 per second
		assertTrue(sent.size() >= 18);
		assertTrue(sent.size() <= 20);
		for (int i = 2; i < sent.size(); i++)
		{
			// any 3 consecutive sends span at least a second, so no 1 s window holds more than 2
			assertTrue("window at " + sent.get(i), sent.get(i) - sent.get(i - 2) >= 1f - 1e-4f);
		}
	}

	@Test
	public void eventsGoOutImmediately()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		p.recordSend(0f);
		assertTrue(p.shouldSend(0.05f, false, true));
	}

	@Test
	public void snapshotOnlyOnChangeAndAtMostOncePerTick()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		assertTrue(p.shouldSend(0f, true, false));
		p.recordSend(0f);
		// changed, but within the same 0.6 s tick
		assertFalse(p.shouldSend(0.3f, true, false));
		assertTrue(p.shouldSend(0.6f, true, false));
		p.recordSend(0.6f);
		// unchanged: nothing, tick after tick, until the keep-alive is due
		for (int i = 2; i < 17; i++)
		{
			float t = i * 0.6f;
			assertFalse("at " + t, p.shouldSend(t, false, false));
		}
	}

	@Test
	public void keepAliveAfterTenSecondsIdle()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		p.recordSend(0f);
		assertFalse(p.shouldSend(9.9f, false, false));
		assertTrue(p.shouldSend(10f, false, false));
	}

	@Test
	public void resetAllowsAnImmediateFirstSnapshot()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		p.recordSend(5f);
		p.reset();
		assertTrue(p.shouldSend(5.1f, true, false));
	}

	@Test
	public void resetDoesNotRefillTheRateLimit()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		for (int i = 0; i < GhostSendPolicy.MAX_PER_SECOND; i++)
		{
			p.recordSend(0.01f * i);
		}
		p.reset();
		assertFalse(p.hasToken(0.5f));
		assertTrue(p.hasToken(1.0f));
	}

	@Test
	public void limitIsTwoPerSecond()
	{
		assertEquals(2, GhostSendPolicy.MAX_PER_SECOND);
	}

	@Test
	public void announceAtMostEveryFiveSeconds()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		assertTrue("first announce at once", p.shouldAnnounce(0f));
		p.recordSend(0f);
		assertFalse(p.shouldAnnounce(4.9f));
		assertTrue(p.shouldAnnounce(5f));
		p.recordSend(5f);
		p.reset();
		// a new session announces as soon as a stop could follow its announce within the limit
		assertFalse(p.shouldAnnounce(5.5f));
		assertTrue("a new session announces at once", p.shouldAnnounce(6f));
	}

	@Test
	public void announceRespectsTheRateLimit()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		p.recordSend(0f);
		p.recordSend(0.1f);
		p.reset();
		assertFalse(p.shouldAnnounce(0.5f));
		// one token back, but none would be left for the stop that may follow the announce
		assertFalse(p.shouldAnnounce(1.0f));
		assertTrue(p.shouldAnnounce(1.1f));
	}

	@Test
	public void sequenceNumbersIncrease()
	{
		GhostSendPolicy p = new GhostSendPolicy();
		int a = p.nextSeq();
		p.reset();
		int b = p.nextSeq();
		int c = p.nextSeq();
		assertTrue(b > a);
		assertTrue(c > b);
	}

	@Test
	public void gating()
	{
		assertTrue(GhostSendPolicy.allowed(false, true, true, true));
		assertFalse("PvP area", GhostSendPolicy.allowed(true, true, true, true));
		assertFalse("sharing off", GhostSendPolicy.allowed(false, false, true, true));
		assertFalse("not skating", GhostSendPolicy.allowed(false, true, false, true));
		assertFalse("no party", GhostSendPolicy.allowed(false, true, true, false));
	}
}
