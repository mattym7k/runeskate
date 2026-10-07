package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;
import org.junit.Test;

public class DuelEndingTest
{
	@Test
	public void aKnockoutLossThrowsATantrumAndAKnockoutWinCelebrates()
	{
		assertEquals(DuelEnding.TANTRUM, DuelEnding.pick(Outcome.LOSS, Cause.KO, true, false));
		assertEquals(DuelEnding.CELEBRATE, DuelEnding.pick(Outcome.WIN, Cause.KO, true, false));
		// the other side gave up mid-fight: still a win worth a cheer
		assertEquals(DuelEnding.CELEBRATE, DuelEnding.pick(Outcome.WIN, Cause.OPPONENT_FORFEIT, true, false));
	}

	@Test
	public void forfeitsTimeoutsDrawsAndCancelsPlayNothing()
	{
		for (Cause c : Cause.values())
		{
			if (c != Cause.KO)
			{
				assertEquals(c.name(), DuelEnding.NONE, DuelEnding.pick(Outcome.LOSS, c, true, false));
			}
			assertEquals(c.name(), DuelEnding.NONE, DuelEnding.pick(Outcome.DRAW, c, true, false));
			assertEquals(c.name(), DuelEnding.NONE, DuelEnding.pick(Outcome.CANCELLED, c, true, false));
		}
		// a win because the opponent went silent or left: nobody to celebrate at
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.WIN, Cause.OPPONENT_SILENT, true, false));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.WIN, Cause.OPPONENT_LEFT, true, false));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(null, Cause.KO, true, false));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.LOSS, null, true, false));
	}

	@Test
	public void theSettingAndPvpAreasTurnItOff()
	{
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.LOSS, Cause.KO, false, false));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.WIN, Cause.KO, false, false));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.LOSS, Cause.KO, true, true));
		assertEquals(DuelEnding.NONE, DuelEnding.pick(Outcome.WIN, Cause.KO, true, true));
	}
}
