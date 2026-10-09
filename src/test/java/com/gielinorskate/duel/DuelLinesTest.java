package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import java.util.Collections;
import org.junit.Test;

public class DuelLinesTest
{
	@Test
	public void theEndLinesFollowTheGame()
	{
		assertEquals("You have defeated Zezima!", DuelLines.banner(Outcome.WIN, "Zezima"));
		assertTrue(DuelLines.banner(Outcome.LOSS, "Zezima").startsWith("Oh dear, you"));
		assertEquals("You have defeated your opponent!", DuelLines.banner(Outcome.WIN, null));
		assertEquals("You have defeated Zezima! Zezima forfeits.",
			DuelLines.over(Outcome.WIN, Cause.OPPONENT_FORFEIT, "Zezima"));
		assertEquals(DuelLines.LOSS + " You forfeit: you stopped skating for too long.",
			DuelLines.over(Outcome.LOSS, Cause.STOPPED_SKATING, "Zezima"));
		assertEquals(DuelLines.DRAW, DuelLines.over(Outcome.DRAW, Cause.KO, "Zezima"));
		assertEquals(DuelLines.LOSS + " You forfeit: you turned duels off.",
			DuelLines.over(Outcome.LOSS, Cause.DUELS_OFF, "Zezima"));
		assertEquals(DuelLines.LOSS + " You forfeit: duels are on one world, and you hopped.",
			DuelLines.over(Outcome.LOSS, Cause.HOPPED, "Zezima"));
	}

	@Test
	public void thePanelStatusFollowsThePhase()
	{
		DuelView off = view(false, true, true, Phase.IDLE);
		assertTrue(off.status().contains("Allow duel challenges"));
		assertFalse(off.canChallenge());
		assertTrue(view(true, true, false, Phase.IDLE).status().contains("party"));
		DuelView ready = new DuelView(true, true, true, Collections.singletonList(new DuelView.Member(5L, "Bob")),
			Phase.IDLE, null, 99, 99, 0, 0, null, false, true);
		assertTrue(ready.canChallenge());
		DuelView walking = new DuelView(true, false, true, Collections.singletonList(new DuelView.Member(5L, "Bob")),
			Phase.IDLE, null, 99, 99, 0, 0, null, false, true);
		assertFalse(walking.canChallenge());
		assertTrue(walking.status().contains("Start skating"));
		DuelView fight = new DuelView(true, true, true, Collections.emptyList(), Phase.FIGHT, "Bob", 80, 41, 0, 0,
			null, false, true);
		assertEquals("Duelling Bob: you 80 HP, them 41 HP.", fight.status());
		assertFalse(fight.canChallenge());
		DuelView blocked = new DuelView(true, true, true, Collections.singletonList(new DuelView.Member(5L, "Bob")),
			Phase.IDLE, null, 99, 99, 0, 0, null, true, true);
		assertFalse(blocked.canChallenge());
		assertTrue(blocked.status().contains("PvP"));
		DuelView notSharing = new DuelView(true, true, true,
			Collections.singletonList(new DuelView.Member(5L, "Bob")), Phase.IDLE, null, 99, 99, 0, 0, null, false,
			false);
		assertFalse(notSharing.canChallenge());
		assertTrue(notSharing.status().contains("Share my skater"));
		assertEquals(fight, new DuelView(true, true, true, Collections.emptyList(), Phase.FIGHT, "Bob", 80, 41, 0, 0,
			null, false, true));
	}

	private static DuelView view(boolean allowed, boolean skating, boolean inParty, Phase phase)
	{
		return new DuelView(allowed, skating, inParty, Collections.emptyList(), phase, null, 99, 99, 0, 0, null, false,
			true);
	}

	@Test
	public void namesInChatCannotCarryTags()
	{
		String c = DuelLines.chatName("<col=ff0000>Bob<img=1>");
		assertFalse(c.contains("<col"));
		assertFalse(c.contains("<img"));
		assertTrue(c.contains("Bob"));
		assertEquals("Zezima", DuelLines.chatName("Zezima"));
		assertEquals("your opponent", DuelLines.chatName(null));
	}
}
