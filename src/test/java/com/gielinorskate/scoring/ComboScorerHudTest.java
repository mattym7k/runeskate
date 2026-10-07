package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import org.junit.Test;

/** The timing and summary data the animated HUD and the feedback layer read from the scorer. */
public class ComboScorerHudTest
{
	@Test
	public void newestNameTimeIsWhenTheBottomLineLastChanged()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1f);
		assertEquals(1f, scorer.newestNameTime(), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 1.4f);
		assertEquals(1.4f, scorer.newestNameTime(), 0f);
		// a spin renames the newest line ("BS 180 Kickflip"): it pops in again
		scorer.accept(TrickEvent.spin(1), 2f);
		assertEquals(2f, scorer.newestNameTime(), 0f);
		// a hold start adds no line
		scorer.accept(TrickEvent.holdStart(Trick.MANUAL), 2.5f);
		assertEquals(2f, scorer.newestNameTime(), 0f);
	}

	@Test
	public void multiplierRiseTimeOnlyMovesWhenTheMultiplierGoesUp()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 1f);
		assertEquals(1f, scorer.multiplierRiseTime(), 0f);
		// a repeat is a new line but not a new distinct trick
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 2f);
		assertEquals(1f, scorer.multiplierRiseTime(), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 3f);
		assertEquals(3f, scorer.multiplierRiseTime(), 0f);
	}

	@Test
	public void eachResolutionBumpsTheSequenceAndKeepsItsSummary()
	{
		ComboScorer scorer = new ComboScorer();
		assertEquals(0, scorer.resultSequence());
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0.1f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.2f);
		scorer.accept(TrickEvent.landed(), 1f);
		assertEquals(1, scorer.resultSequence());
		assertEquals(2, scorer.lastResultMultiplier());
		assertEquals(3, scorer.lastResultTrickCount());

		scorer.accept(TrickEvent.bailed(), 2f);
		assertEquals(2, scorer.resultSequence());
		assertEquals(0, scorer.lastResultTrickCount());
	}

	@Test
	public void aRollOffLandingBumpsTheSequence()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.update(1f, true);
		assertEquals(1, scorer.resultSequence());
	}

	@Test
	public void anEmptyLandingIsNoResolution()
	{
		ComboScorer scorer = new ComboScorer();
		scorer.accept(TrickEvent.landed(), 0f);
		assertEquals(0, scorer.resultSequence());
	}
}
