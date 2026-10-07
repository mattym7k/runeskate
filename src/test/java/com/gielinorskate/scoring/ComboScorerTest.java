package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ComboScorerTest
{
	private ComboScorer scorer;

	@Before
	public void setUp()
	{
		scorer = new ComboScorer();
	}

	@Test
	public void singleTrickLandsForItsOwnPoints()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		assertEquals(100, scorer.comboPoints());
		assertEquals(1, scorer.multiplier());

		scorer.accept(TrickEvent.landed(), 1f);

		assertEquals(100, scorer.sessionScore());
		assertEquals(ComboScorer.Result.landed(100), scorer.lastResult());
	}

	@Test
	public void comboValueIsPointsTimesDistinctTrickCount()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f); // 100
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f); // 300
		// comboPoints = 400, distinct tricks = 2 -> value = 800
		assertEquals(400, scorer.comboPoints());
		assertEquals(2, scorer.multiplier());

		scorer.accept(TrickEvent.landed(), 0.2f);

		assertEquals(800, scorer.sessionScore());
	}

	@Test
	public void repeatingATrickWithinComboDoesNotIncreaseDistinctCount()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0.1f);

		assertEquals(1, scorer.multiplier());
	}

	@Test
	public void aTweakedGrabScoresHalfAsMuchAgainAsItsGrab()
	{
		// the physics renames the hold on the tweak (a second HOLD_START); its HOLD_END is the whole hold
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.holdStart(Trick.MELON), 0.1f);
		scorer.accept(TrickEvent.holdStart(Trick.METHOD), 0.62f);
		scorer.accept(TrickEvent.holdEnd(Trick.METHOD, 1f), 1.1f);
		assertEquals(100 + 450, scorer.comboPoints());
		assertEquals(Math.round(Trick.MELON.points * 1.5f), Trick.METHOD.points);
		scorer.accept(TrickEvent.landed(), 1.2f);
		assertTrue(scorer.lastSummary().tricks.contains(Trick.METHOD));
	}

	@Test
	public void holdScoresRatePerSecondTimesSeconds()
	{
		// MANUAL is 150 pts/s, held for 2s -> 300 points for this entry.
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 2f), 2f);

		assertEquals(300, scorer.comboPoints());
		assertEquals(1, scorer.multiplier());
	}

	@Test
	public void repetitionDecayAppliesPerPriorOccurrenceInSession()
	{
		// First OLLIE: full 100. Second OLLIE (n=1): 100 * 0.75 = 75. Third (n=2): 100*0.5625=56(rounded).
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.landed(), 0.1f);
		assertEquals(100, scorer.sessionScore());

		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1f);
		scorer.accept(TrickEvent.landed(), 1.1f);
		assertEquals(100 + 75, scorer.sessionScore());

		scorer.accept(TrickEvent.trick(Trick.OLLIE), 2f);
		scorer.accept(TrickEvent.landed(), 2.1f);
		assertEquals(100 + 75 + 56, scorer.sessionScore());
	}

	@Test
	public void repetitionDecayFloorsAtQuarterPoints()
	{
		// After many repeats, decay should floor at 0.25x rather than keep shrinking.
		for (int i = 0; i < 20; i++)
		{
			scorer.accept(TrickEvent.trick(Trick.OLLIE), i);
			scorer.accept(TrickEvent.landed(), i + 0.1f);
		}
		int scoreBefore = scorer.sessionScore();

		scorer.accept(TrickEvent.trick(Trick.OLLIE), 21f);
		scorer.accept(TrickEvent.landed(), 21.1f);

		assertEquals(25, scorer.sessionScore() - scoreBefore);
	}

	@Test
	public void bailedDiscardsComboAndDoesNotScore()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);

		scorer.accept(TrickEvent.bailed(), 0.2f);

		assertEquals(0, scorer.sessionScore());
		assertEquals(ComboScorer.Result.bailed((100 + 300) * 2), scorer.lastResult());
		assertEquals(0, scorer.comboPoints());
		assertEquals(0, scorer.multiplier());
		assertTrue(scorer.comboNames().isEmpty());
	}

	@Test
	public void rollingWithoutHoldForHalfASecondResolvesTheComboAsLanded()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);

		scorer.update(0.3f, true);
		assertEquals(ComboScorer.Result.NONE, scorer.lastResult());
		assertEquals(0, scorer.sessionScore());

		scorer.update(0.5f, true);

		assertEquals(100, scorer.sessionScore());
		assertEquals(ComboScorer.Result.landed(100), scorer.lastResult());
	}

	@Test
	public void rollingWithoutHoldDoesNotResolveWhileStillHolding()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);

		scorer.update(10f, false);

		assertEquals(0, scorer.sessionScore());
		assertEquals(100, scorer.comboPoints());
	}

	@Test
	public void comboNamesShowsOnlyTheLastSix()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 1f);
		scorer.accept(TrickEvent.trick(Trick.HEELFLIP), 2f);
		scorer.accept(TrickEvent.trick(Trick.POP_SHOVE_IT), 3f);
		scorer.accept(TrickEvent.trick(Trick.FS_POP_SHOVE_IT), 4f);
		scorer.accept(TrickEvent.trick(Trick.INDY), 5f);
		scorer.accept(TrickEvent.trick(Trick.MELON), 6f);

		List<String> names = scorer.comboNames();

		assertEquals(6, names.size());
		assertEquals("Kickflip", names.get(0));
		assertEquals("Melon", names.get(5));
	}

	@Test
	public void lastResultAgeTracksTimeSinceResolution()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.landed(), 5f);

		assertEquals(0f, scorer.lastResultAge(5f), 0.0001f);
		assertEquals(1.5f, scorer.lastResultAge(6.5f), 0.0001f);
	}

	@Test
	public void upgradeReplacesTheTrickItUpgradedInsteadOfAddingOne()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), 0.2f);

		// 100 + 800, two distinct tricks: the kickflip became the double kickflip
		assertEquals(900, scorer.comboPoints());
		assertEquals(2, scorer.multiplier());
		assertEquals(java.util.Arrays.asList("Ollie", "Double Kickflip"), scorer.comboNames());

		// the replaced kickflip does not count as a repeat for decay
		scorer.accept(TrickEvent.landed(), 1f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 2f);
		assertEquals(300, scorer.comboPoints());
	}

	@Test
	public void aTripleReplacesTheDoubleItCameFrom()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.upgrade(Trick.TRIPLE_KICKFLIP, Trick.DOUBLE_KICKFLIP), 0.2f);
		assertEquals(java.util.Arrays.asList("Triple Kickflip"), scorer.comboNames());
		assertEquals(1300, scorer.comboPoints());
		assertEquals(1, scorer.multiplier());
	}

	@Test
	public void upgradeWithNothingToReplaceIsAdded()
	{
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), 0f);
		assertEquals(800, scorer.comboPoints());
	}

	@Test
	public void holdShorterThanPointOneFiveSecondsIsDropped()
	{
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 0.1f), 0.1f);
		assertEquals(0, scorer.comboPoints());
		assertEquals(0, scorer.multiplier());
		assertTrue(scorer.comboNames().isEmpty());

		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 1f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 0.15f), 1.15f);
		assertEquals(Math.round(150 * 0.15f), scorer.comboPoints());
	}

	@Test
	public void landingWithNoComboShowsNothing()
	{
		scorer.accept(TrickEvent.landed(), 1f);
		assertEquals(ComboScorer.Result.NONE, scorer.lastResult());
		assertEquals(0, scorer.sessionScore());
	}

	@Test
	public void abandonedComboIsClearedWithoutAResult()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.abandonCombo();
		assertEquals(0, scorer.comboPoints());
		assertEquals(0, scorer.multiplier());

		scorer.update(10f, true);
		scorer.accept(TrickEvent.landed(), 10f);
		assertEquals(0, scorer.sessionScore());
		assertEquals(ComboScorer.Result.NONE, scorer.lastResult());
	}

	@Test
	public void landedComboNamesStayAvailableForTheFlash()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.landed(), 0.2f);
		assertTrue(scorer.comboNames().isEmpty());
		assertEquals(List.of("Ollie", "Kickflip"), scorer.lastComboNames());
	}

	@Test
	public void bailedResultCarriesTheLostComboValue()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f); // 100
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f); // 300
		scorer.accept(TrickEvent.bailed(), 0.2f);
		assertTrue(scorer.lastResult().isBailed());
		assertEquals((100 + 300) * 2, scorer.lastResult().value);
		assertEquals(List.of("Ollie", "Kickflip"), scorer.lastComboNames());
		assertEquals(0, scorer.sessionScore());
	}

	@Test
	public void fakieTricksAreNamedFakie()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE, true), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP, true), 0.1f);
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP, true), 0.2f);
		assertEquals(java.util.Arrays.asList("Fakie Ollie", "Fakie Double Kickflip"), scorer.comboNames());
		assertEquals(100 + 800, scorer.comboPoints());
	}

	@Test
	public void aCleanLandingAddsTenPercent()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f); // 100
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f); // 300, x2 = 800
		scorer.accept(TrickEvent.landed(true), 1f);
		assertEquals(880, scorer.sessionScore());
		assertEquals(ComboScorer.Result.landedClean(880), scorer.lastResult());
		assertTrue(scorer.lastResult().clean);
	}

	@Test
	public void aSloppyLandingScoresTheComboAsIs()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.landed(false), 1f);
		assertEquals(100, scorer.sessionScore());
		assertEquals(ComboScorer.Result.landed(100), scorer.lastResult());
	}

	@Test
	public void theCleanFlagRidesOnTheLandedEvent()
	{
		assertTrue(TrickEvent.landed(true).clean);
		assertFalse(TrickEvent.landed(false).clean);
		assertFalse(TrickEvent.landed().clean);
		assertFalse(TrickEvent.landed(true).equals(TrickEvent.landed(false)));
	}

	@Test
	public void aSpinRenamesTheAirsTrickAndAdds150PerHalfTurn()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.spin(-1), 0.5f);
		assertEquals(List.of("FS 180 Kickflip"), scorer.comboNames());
		assertEquals(300 + 150, scorer.comboPoints());
		assertEquals(1, scorer.multiplier());
		scorer.accept(TrickEvent.landed(), 0.5f);
		assertEquals(450, scorer.sessionScore());
		assertEquals(List.of("FS 180 Kickflip"), scorer.lastComboNames());
	}

	@Test
	public void aBackside360OllieIsNamedAndScored()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.spin(2), 0.5f);
		assertEquals(List.of("BS 360 Ollie"), scorer.comboNames());
		assertEquals(100 + 300, scorer.comboPoints());
	}

	@Test
	public void aSpinWithNoTrickInTheAirIsItsOwnEntry()
	{
		scorer.accept(TrickEvent.spin(-3), 0f);
		assertEquals(List.of("FS 540"), scorer.comboNames());
		assertEquals(450, scorer.comboPoints());
		assertEquals(1, scorer.multiplier());
	}

	@Test
	public void theSpinGoesOnTheLastFlipOfTheAir()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		assertEquals(List.of("Ollie", "BS 180 Kickflip"), scorer.comboNames());
		assertEquals(100 + 450, scorer.comboPoints());
	}

	@Test
	public void anUpgradedFlipKeepsItsSpin()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), 0.1f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		assertEquals(List.of("BS 180 Double Kickflip"), scorer.comboNames());
		assertEquals(800 + 150, scorer.comboPoints());
	}

	@Test
	public void fakieSpinsKeepTheFakiePrefixAndTheirDirection()
	{
		// FS/BS follows the rotation direction relative to the (regular) stance, riding fakie or not
		scorer.accept(TrickEvent.trick(Trick.OLLIE, true), 0f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		assertEquals(List.of("Fakie BS 180 Ollie"), scorer.comboNames());
	}

	@Test
	public void aGroundHoldEndsTheAirSoALaterSpinStandsAlone()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 1f), 1.5f);
		scorer.accept(TrickEvent.spin(1), 2f); // a 180 off the end of the manual (rolled off a ledge)
		assertEquals(List.of("Kickflip", "Manual", "BS 180"), scorer.comboNames());
	}

	@Test
	public void spinDecayIsKeyedByTheTrickAndTheSpinAmount()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		scorer.accept(TrickEvent.landed(), 0.5f);
		assertEquals(250, scorer.sessionScore());

		// the same spun trick again decays: 250 * 0.75 = 187.5 -> 188
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1f);
		scorer.accept(TrickEvent.spin(1), 1.5f);
		assertEquals(188, scorer.comboPoints());
		scorer.accept(TrickEvent.landed(), 1.5f);

		// a plain ollie and a 360 ollie are different tricks: neither has decayed
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 2f);
		assertEquals(100, scorer.comboPoints());
		scorer.accept(TrickEvent.landed(), 2.5f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 3f);
		scorer.accept(TrickEvent.spin(-2), 3.5f);
		// the plain ollie is now at n=1, but that is undone when it becomes a 360: full 100 + 300
		assertEquals(400, scorer.comboPoints());
	}

	@Test
	public void aBodyFlipIsItsOwnEntryAndNeverTakesTheSpin()
	{
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0f);
		scorer.accept(TrickEvent.trick(Trick.FRONTFLIP), 0.5f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		assertEquals(List.of("BS 180 Kickflip", "Frontflip"), scorer.comboNames());
		assertEquals(450 + 1000, scorer.comboPoints());
		assertEquals(2, scorer.multiplier());
	}

	@Test
	public void spunTricksCountAsDistinctForTheMultiplier()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.spin(1), 0.5f);
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 0.5f);
		scorer.accept(TrickEvent.holdEnd(Trick.MANUAL, 1f), 1.5f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1.5f);
		assertEquals(List.of("BS 180 Ollie", "Manual", "Ollie"), scorer.comboNames());
		assertEquals(3, scorer.multiplier());
	}
}
