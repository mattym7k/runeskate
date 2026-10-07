package com.gielinorskate.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DrawnModelReportTest
{
	@Test
	public void aReportIsUsedOnce()
	{
		DrawnModelReport r = new DrawnModelReport();
		assertFalse(r.isFresh());
		r.report(false);
		assertTrue(r.isFresh());
		assertFalse(r.take());
		assertFalse("consumed: the animator fetches for itself until the next draw", r.isFresh());
	}

	@Test
	public void theLatestDrawWins()
	{
		DrawnModelReport r = new DrawnModelReport();
		r.report(false);
		r.report(true);
		assertTrue(r.take());
	}

	@Test
	public void clearForgetsAnOldSessionsDraw()
	{
		DrawnModelReport r = new DrawnModelReport();
		r.report(true);
		r.clear();
		assertFalse(r.isFresh());
	}
}
