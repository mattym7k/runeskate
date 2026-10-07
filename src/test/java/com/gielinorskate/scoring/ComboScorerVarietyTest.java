package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/** Variety bonuses of a skate session: first landings of a trick, and long combos. */
public class ComboScorerVarietyTest
{
	private ComboScorer scorer;

	@Before
	public void setUp()
	{
		scorer = new ComboScorer();
		scorer.startSession();
	}

	@Test
	public void firstLandingOfATrickScoresAQuarterMore()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		// 300 * 1.25 = 375, x1
		assertEquals(375, scorer.lastResult().value);
		assertEquals(1, scorer.lastSummary().newTricks);
	}

	@Test
	public void secondLandingOfTheSameTrickHasNoBonus()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 2f);
		scorer.accept(TrickEvent.landed(), 2.5f);
		// the repeat decays to 0.75: 225, and is no longer new
		assertEquals(225, scorer.lastResult().value);
		assertEquals(0, scorer.lastSummary().newTricks);
	}

	@Test
	public void aBailedTrickIsStillNewNextTime()
	{
		scorer.accept(TrickEvent.trick(Trick.HEELFLIP), 0f);
		scorer.accept(TrickEvent.bailed(), 0.5f);
		scorer.accept(TrickEvent.trick(Trick.HEELFLIP), 2f);
		scorer.accept(TrickEvent.landed(), 2.5f);
		// decayed once (225), then the first-landing bonus: round(225 * 1.25) = 281
		assertEquals(281, scorer.lastResult().value);
	}

	@Test
	public void onlyTheFirstEntryOfANewKeyInACombo()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 1f), 1.5f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1.5f);
		scorer.accept(TrickEvent.landed(), 2f);
		// ollie 100 (+25) + manual 150 (+38, 37.5 rounded) + ollie 75 (no bonus) = 388, x2 = 776
		assertEquals(776, scorer.lastResult().value);
		assertEquals(2, scorer.lastSummary().newTricks);
	}

	@Test
	public void aComboLongerThanEightSecondsGetsOneMoreMultiplier()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		// the ollie is no longer new; a 9 s grind combo
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 10f);
		scorer.accept(TrickEvent.holdStart(Trick.FIFTY_FIFTY), 10.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.FIFTY_FIFTY, 8.6f), 19.1f);
		scorer.accept(TrickEvent.landed(), 19.6f);
		ComboScorer.Summary s = scorer.lastSummary();
		assertTrue(s.longCombo);
		// ollie 75 + 50-50 round(200 * 8.6) = 1720 * 1.25 = 2150 -> 2225, x(2 + 1) = 6675
		assertEquals(6675, scorer.lastResult().value);
		assertEquals(3, s.multiplier);
	}

	@Test
	public void aLongHoldStartsTheComboWhenItBegan()
	{
		// the combo is just one grind, but it lasted 9 s
		scorer.accept(TrickEvent.holdStart(Trick.FIFTY_FIFTY), 0f);
		scorer.accept(TrickEvent.holdEnd(Trick.FIFTY_FIFTY, 9f), 9f);
		scorer.accept(TrickEvent.landed(), 9.2f);
		assertTrue(scorer.lastSummary().longCombo);
		assertEquals(2, scorer.lastSummary().multiplier);
	}

	@Test
	public void aShortComboHasNoLongBonus()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 7.9f);
		scorer.accept(TrickEvent.landed(), 7.95f);
		assertFalse(scorer.lastSummary().longCombo);
		assertEquals(2, scorer.lastSummary().multiplier);
	}

	@Test
	public void rollOutTimesTheComboToItsLastTrickNotTheRollOut()
	{
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 7.8f), 7.8f);
		scorer.update(8.4f, true);
		assertTrue(scorer.lastResult().isLanded());
		assertFalse(scorer.lastSummary().longCombo);
	}

	@Test
	public void aNewSessionMakesEveryTrickNewAgain()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		scorer.startSession();
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 2f);
		scorer.accept(TrickEvent.landed(), 2.5f);
		assertEquals(1, scorer.lastSummary().newTricks);
	}

	@Test
	public void noBonusesBeforeASessionStarts()
	{
		ComboScorer plain = new ComboScorer();
		plain.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		plain.accept(TrickEvent.landed(), 0.5f);
		assertEquals(300, plain.lastResult().value);
	}

	@Test
	public void summaryListsTheLandedTricksAndHoldTimes()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.holdStart(Trick.FIVE_O), 0.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.FIVE_O, 2f), 2.5f);
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 2.6f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 1f), 3.6f);
		scorer.accept(TrickEvent.trick(Trick.BACKFLIP), 3.7f);
		scorer.accept(TrickEvent.spin(2), 4f);
		scorer.accept(TrickEvent.landed(true), 4.2f);
		ComboScorer.Summary s = scorer.lastSummary();
		assertEquals(Arrays.asList(Trick.KICKFLIP, Trick.FIVE_O, Trick.MANUAL, Trick.BACKFLIP), s.tricks);
		assertEquals(2f, s.grindSeconds, 1e-6);
		assertEquals(1f, s.manualSeconds, 1e-6);
		assertEquals(2, s.maxSpinHalfTurns);
		assertTrue(s.clean);
		assertEquals(scorer.lastResult().value, s.value);
	}

	@Test
	public void bailsHaveNoSummary()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		ComboScorer.Summary landed = scorer.lastSummary();
		scorer.accept(TrickEvent.trick(Trick.HEELFLIP), 1f);
		scorer.accept(TrickEvent.bailed(), 1.5f);
		assertEquals(landed, scorer.lastSummary());
	}
}
