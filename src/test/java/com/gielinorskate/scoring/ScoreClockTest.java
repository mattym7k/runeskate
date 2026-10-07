package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScoreClockTest
{
	@Test
	public void countsSecondsFromItsOwnStartSoFloatsStayPrecise()
	{
		long[] nanos = {123_456_789_000_000_000L}; // ~3.9 years of uptime: far beyond float precision
		ScoreClock clock = new ScoreClock(() -> nanos[0]);
		assertEquals(0f, clock.now(), 0f);
		nanos[0] += 20_000_000L; // 20 ms later
		assertEquals(0.02f, clock.now(), 1e-6f);
		assertTrue(clock.now() > 0f);
	}
}
