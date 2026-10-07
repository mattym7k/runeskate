package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class SessionGoalsTest
{
	private static ComboScorer.Summary combo(int value, Trick... tricks)
	{
		return new ComboScorer.Summary(Arrays.asList(tricks), 0f, 0f, 0, value, tricks.length, false, 0, false);
	}

	private static SessionGoals with(SessionGoals.Type... types)
	{
		SessionGoals goals = new SessionGoals();
		goals.start(Arrays.asList(types));
		return goals;
	}

	@Test
	public void threeDifferentGoalsEachSession()
	{
		Random random = new Random(7);
		for (int i = 0; i < 50; i++)
		{
			SessionGoals goals = new SessionGoals();
			goals.start(random);
			Set<SessionGoals.Type> types = EnumSet.noneOf(SessionGoals.Type.class);
			for (SessionGoals.Goal g : goals.goals())
			{
				types.add(g.type);
				assertFalse(g.isDone());
			}
			assertEquals(3, types.size());
		}
	}

	@Test
	public void differentFlipsCountsEachFlipOnceAndNotBodyFlips()
	{
		SessionGoals goals = with(SessionGoals.Type.DIFFERENT_FLIPS);
		goals.onLanded(combo(300, Trick.KICKFLIP, Trick.KICKFLIP, Trick.OLLIE, Trick.BACKFLIP));
		assertEquals("1/5", goals.goals().get(0).progressText());
		goals.onLanded(combo(300, Trick.HEELFLIP, Trick.TRE_FLIP, Trick.HARDFLIP));
		List<SessionGoals.Goal> done = goals.onLanded(combo(300, Trick.KICKFLIP, Trick.POP_SHOVE_IT));
		assertEquals(1, done.size());
		assertTrue(goals.goals().get(0).isDone());
		assertEquals("Done", goals.goals().get(0).progressText());
		// a completed goal is reported once
		assertTrue(goals.onLanded(combo(300, Trick.LASER_FLIP)).isEmpty());
	}

	@Test
	public void grindSecondsAddUp()
	{
		SessionGoals goals = with(SessionGoals.Type.GRIND_SECONDS);
		ComboScorer.Summary grind = new ComboScorer.Summary(Collections.singletonList(Trick.FIFTY_FIFTY), 4.25f, 0f,
			0, 900, 1, false, 0, false);
		goals.onLanded(grind);
		assertEquals("4.2/10 s", goals.goals().get(0).progressText());
		goals.onLanded(grind);
		assertTrue(goals.onLanded(grind).size() == 1);
	}

	@Test
	public void bigComboNeedsOneComboTotalAddsThemUp()
	{
		SessionGoals goals = with(SessionGoals.Type.BIG_COMBO, SessionGoals.Type.TOTAL_POINTS);
		goals.onLanded(combo(15_000, Trick.OLLIE));
		goals.onLanded(combo(15_000, Trick.OLLIE));
		assertFalse(goals.goals().get(0).isDone());
		assertEquals("15,000/20,000", goals.goals().get(0).progressText());
		assertEquals("30,000/50,000", goals.goals().get(1).progressText());
		List<SessionGoals.Goal> done = goals.onLanded(combo(20_000, Trick.OLLIE));
		assertEquals(2, done.size());
	}

	@Test
	public void bodyFlipShiftTricksGrabsSpinsCleanAndMultiplier()
	{
		SessionGoals goals = with(SessionGoals.Type.BODY_FLIP, SessionGoals.Type.SHIFT_TRICKS,
			SessionGoals.Type.GRABS);
		goals.onLanded(combo(100, Trick.HARDFLIP, Trick.IMPOSSIBLE, Trick.INDY, Trick.MELON));
		assertFalse(goals.goals().get(0).isDone());
		assertEquals("2/3", goals.goals().get(1).progressText());
		assertEquals("2/3", goals.goals().get(2).progressText());
		assertEquals(3, goals.onLanded(combo(100, Trick.FRONTFLIP, Trick.BIGSPIN, Trick.INDY)).size());

		SessionGoals more = with(SessionGoals.Type.SPIN_360, SessionGoals.Type.CLEAN_LANDINGS,
			SessionGoals.Type.MULTIPLIER);
		more.onLanded(new ComboScorer.Summary(Collections.singletonList(Trick.OLLIE), 0f, 0f, 1, 100, 4, true, 0,
			false));
		assertFalse(more.goals().get(0).isDone());
		assertEquals("1/5", more.goals().get(1).progressText());
		assertEquals("4/5", more.goals().get(2).progressText());
		more.onLanded(new ComboScorer.Summary(Collections.singletonList(Trick.OLLIE), 0f, 0f, 2, 100, 5, false, 0,
			false));
		assertTrue(more.goals().get(0).isDone());
		assertTrue(more.goals().get(2).isDone());
	}

	@Test
	public void shiftTricksAreTheGuidesShiftGroup()
	{
		assertTrue(SessionGoals.isShiftTrick(Trick.HARDFLIP));
		assertTrue(SessionGoals.isShiftTrick(Trick.IMPOSSIBLE));
		assertFalse(SessionGoals.isShiftTrick(Trick.KICKFLIP));
		assertFalse(SessionGoals.isShiftTrick(Trick.BACKFLIP));
	}

	@Test
	public void currentIsTheFirstNotDone()
	{
		SessionGoals goals = with(SessionGoals.Type.BODY_FLIP, SessionGoals.Type.GRABS);
		assertEquals(SessionGoals.Type.BODY_FLIP, goals.current().type);
		goals.onLanded(combo(100, Trick.BACKFLIP));
		assertEquals(SessionGoals.Type.GRABS, goals.current().type);
		goals.onLanded(combo(100, Trick.INDY, Trick.INDY, Trick.MELON));
		assertNull(goals.current());
		assertNull(new SessionGoals().current());
	}
}
