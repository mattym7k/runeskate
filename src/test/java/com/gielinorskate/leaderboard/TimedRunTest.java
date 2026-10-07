package com.gielinorskate.leaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.scoring.ComboScorer;
import java.util.Collections;
import org.junit.Test;

/** The timed run's countdown, clock and banking rules. */
public class TimedRunTest
{
	private static ComboScorer.Landed combo(int value, float at)
	{
		return new ComboScorer.Landed(value, at - 1f, at,
			Collections.singletonList(new ComboScorer.TrickRecord("OLLIE", 100, 0, at - 1f)));
	}

	@Test
	public void countsDownThenRunsTwoMinutes()
	{
		TimedRun run = new TimedRun();
		assertTrue(run.start(10f));
		assertFalse(run.start(11f));
		assertEquals(TimedRun.Phase.COUNTDOWN, run.phase());
		assertEquals(2f, run.countdownLeft(11f), 1e-4);
		assertNull(run.update(12.9f, false));
		assertNull(run.update(13f, false));
		assertEquals(TimedRun.Phase.RUNNING, run.phase());
		assertEquals(120f, run.timeLeft(13f), 1e-4);
		assertEquals(60f, run.timeLeft(73f), 1e-4);
		assertNull(run.update(132.9f, false));
		TimedRun.Finished f = run.update(133f, false);
		assertNotNull(f);
		assertEquals(120_000, f.durationMs);
		assertEquals(13f, f.start, 1e-4);
		assertFalse(run.isActive());
	}

	@Test
	public void sumsCombosBankedDuringTheRunOnly()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		// landed during the countdown: not counted
		assertNull(run.onComboLanded(combo(999, 2f), 2f));
		run.update(3f, false);
		run.onComboLanded(combo(100, 10f), 10f);
		// a bail does not end the run
		assertNull(run.onComboBailed(20f));
		run.onComboLanded(combo(250, 50f), 50f);
		TimedRun.Finished f = run.update(123f, false);
		assertEquals(350, f.score);
		assertEquals(2, f.combos);
		assertEquals(2, f.tricks.size());
	}

	@Test
	public void comboRunningAtZeroIsBankedIfItLandsWithinThreeSeconds()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		run.onComboLanded(combo(100, 60f), 60f);
		assertNull(run.update(123f, true));
		assertEquals(TimedRun.Phase.GRACE, run.phase());
		assertNull(run.update(125f, true));
		TimedRun.Finished f = run.onComboLanded(combo(400, 125.5f), 125.5f);
		assertNotNull(f);
		assertEquals(500, f.score);
		assertEquals(122_500, f.durationMs);
	}

	@Test
	public void comboStillGoingAfterTheGraceIsNotBanked()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		run.onComboLanded(combo(100, 60f), 60f);
		run.update(123f, true);
		TimedRun.Finished f = run.update(126f, true);
		assertNotNull(f);
		assertEquals(100, f.score);
		assertEquals(120_000, f.durationMs);
		// landing later changes nothing
		assertNull(run.onComboLanded(combo(400, 127f), 127f));
	}

	@Test
	public void bailDuringTheGraceEndsTheRun()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		run.update(123.1f, true);
		TimedRun.Finished f = run.onComboBailed(124f);
		assertNotNull(f);
		assertEquals(0, f.score);
		assertEquals(120_000, f.durationMs);
	}

	@Test
	public void landingRightAtZeroBeforeTheClockTicksEndsTheRun()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		TimedRun.Finished f = run.onComboLanded(combo(300, 123.2f), 123.2f);
		assertNotNull(f);
		assertEquals(300, f.score);
		assertEquals(120_200, f.durationMs);
	}

	@Test
	public void cancelDropsTheRun()
	{
		TimedRun run = new TimedRun();
		assertFalse(run.cancel());
		run.start(0f);
		run.update(5f, false);
		run.onComboLanded(combo(100, 10f), 10f);
		assertTrue(run.cancel());
		assertFalse(run.isActive());
		assertNull(run.update(200f, false));
		assertTrue(run.start(300f));
		assertEquals(0, run.score());
	}

	@Test
	public void finishedRunMakesAPlausibleSessionSubmission()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		run.onComboLanded(combo(150, 10f), 10f);
		TimedRun.Finished f = run.update(123f, false);
		RunSubmission s = RunSubmission.session(f.score, f.durationMs, f.start, f.tricks);
		assertEquals(Long.valueOf(150), s.score);
		assertEquals(6000, s.tricks.get(0).t);
		assertNull(RunBounds.check(s));
	}

	@Test
	public void comboStartedBeforeGoIsNotCounted()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		// a manual held through the 3-2-1, landed just after GO: its points were made before the run
		ComboScorer.Landed early = new ComboScorer.Landed(5000, 0.5f, 3.4f,
			Collections.singletonList(new ComboScorer.TrickRecord("MANUAL", 5000, 0, 0.5f)));
		assertNull(run.onComboLanded(early, 3.4f));
		run.onComboLanded(combo(100, 10f), 10f);
		TimedRun.Finished f = run.update(123f, false);
		assertEquals(100, f.score);
		assertEquals(1, f.combos);
		assertEquals(1, f.tricks.size());
	}

	@Test
	public void comboStartedBeforeGoLandingInTheGraceStillEndsTheRun()
	{
		TimedRun run = new TimedRun();
		run.start(0f);
		run.update(3f, false);
		assertNull(run.update(123f, true));
		ComboScorer.Landed early = new ComboScorer.Landed(9000, 2f, 124f,
			Collections.singletonList(new ComboScorer.TrickRecord("MANUAL", 9000, 0, 2f)));
		TimedRun.Finished f = run.onComboLanded(early, 124f);
		assertNotNull(f);
		assertEquals(0, f.score);
	}
}
