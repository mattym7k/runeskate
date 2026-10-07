package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.scoring.ComboScorer;
import org.junit.Test;

public class SessionStatsTest
{
	@Test
	public void eachLandedResultCountsOnce()
	{
		SessionStats s = new SessionStats();
		assertFalse("nothing yet", s.update(0, ComboScorer.Result.NONE, 0));
		assertTrue(s.update(1, ComboScorer.Result.landed(1200), 3));
		assertFalse("same result again", s.update(1, ComboScorer.Result.landed(1200), 3));
		assertTrue(s.update(2, ComboScorer.Result.landedClean(800), 2));
		assertEquals(1200, s.bestCombo());
		assertEquals(5, s.tricksLanded());
	}

	@Test
	public void bailsDoNotCount()
	{
		SessionStats s = new SessionStats();
		assertFalse(s.update(1, ComboScorer.Result.bailed(5000), 4));
		assertEquals(0, s.bestCombo());
		assertEquals(0, s.tricksLanded());
	}
}
