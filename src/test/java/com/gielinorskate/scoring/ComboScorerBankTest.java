package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import org.junit.Test;

/** Stepping off the board banks the combo in progress. */
public class ComboScorerBankTest
{
	private final ComboScorer scorer = new ComboScorer();

	@Test
	public void bankingLandsTheComboInProgress()
	{
		scorer.accept(TrickEvent.trick(Trick.OLLIE), 0f);
		scorer.accept(TrickEvent.trick(Trick.KICKFLIP), 0.1f);
		int before = scorer.resultSequence();
		scorer.bankCombo(0.5f);
		assertEquals(800, scorer.sessionScore());
		assertTrue(scorer.lastResult().isLanded());
		assertEquals(before + 1, scorer.resultSequence());
		assertEquals(0, scorer.comboPoints());
	}

	@Test
	public void bankingWithNoComboDoesNothing()
	{
		int before = scorer.resultSequence();
		scorer.bankCombo(1f);
		assertEquals(before, scorer.resultSequence());
		assertEquals(0, scorer.sessionScore());
	}
}
