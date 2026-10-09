package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.duel.DuelStateMachine.*;
import java.util.*;
import org.junit.Test;

/** Every duel line looked up by cause or phase reads exactly as written. */
public class DuelTextTablesTest
{
	@Test
	public void everyCauseHasItsReason()
	{
		Map<Cause, String> loss = new EnumMap<>(Cause.class);
		loss.put(Cause.KO, null);
		loss.put(Cause.STOPPED_SKATING, "You forfeit: you stopped skating for too long.");
		loss.put(Cause.LEFT, "You forfeit: you left the party.");
		loss.put(Cause.BLOCKED, "You forfeit: no duels in PvP areas or instances.");
		loss.put(Cause.HOPPED, "You forfeit: duels are on one world, and you hopped.");
		loss.put(Cause.DUELS_OFF, "You forfeit: you turned duels off.");
		loss.put(Cause.SHARING_OFF, "You forfeit: duels need \"Share my skater with my party\" on.");
		loss.put(Cause.SHUTDOWN, "You forfeit.");
		loss.put(Cause.OPPONENT_FORFEIT, "Zezima forfeits.");
		loss.put(Cause.OPPONENT_SILENT, "Zezima stopped responding.");
		loss.put(Cause.OPPONENT_LEFT, "Zezima left the party.");
		loss.put(Cause.TIMED_OUT, "You forfeit: Zezima stopped hearing from you.");
		loss.put(Cause.CANCELLED, "The challenge was taken back or had expired.");
		loss.put(Cause.BOTH_QUIT, "You both quit at once: it counts for nobody.");
		assertEquals(EnumSet.allOf(Cause.class), loss.keySet());
		loss.forEach((cause, text) -> assertEquals(cause.name(), text, DuelLines.reason(Outcome.LOSS, cause, "Zezima")));

		assertEquals("Duels need \"Share my skater with my party\" on.",
			DuelLines.reason(Outcome.CANCELLED, Cause.SHARING_OFF, "Zezima"));
		assertEquals(null, DuelLines.reason(Outcome.CANCELLED, Cause.SHUTDOWN, "Zezima"));
		assertEquals("The Skate Duel with your opponent was called off. You left the party.",
			DuelLines.over(Outcome.CANCELLED, Cause.LEFT, null));
	}

	@Test
	public void theOtherLinesKeepTheirWording()
	{
		assertEquals("Bob challenges you to a Skate Duel! Accept or decline in the RuneSkate side panel within "
			+ Math.round(DuelStateMachine.CHALLENGE_EXPIRY) + " seconds.", DuelLines.challenged("Bob"));
		assertEquals("You challenge Bob to a Skate Duel.", DuelLines.challengeSent("Bob"));
		assertEquals("Bob declined your Skate Duel.", DuelLines.declined("Bob"));
		assertEquals("Your Skate Duel challenge to Bob expired.", DuelLines.expired("Bob", true));
		assertEquals("The Skate Duel challenge from Bob expired.", DuelLines.expired("Bob", false));
		assertEquals("Skate Duel against Bob: land combos to hit, bails cost you " + DuelStateMachine.BAIL_DAMAGE
			+ " HP. Get ready!", DuelLines.countdown("Bob"));
		assertEquals("Oh dear, you've been out-skated!", DuelLines.banner(Outcome.LOSS, "Bob"));
		assertEquals("Double KO! It's a draw.", DuelLines.banner(Outcome.DRAW, "Bob"));
		assertEquals("Skate Duel called off.", DuelLines.banner(Outcome.CANCELLED, "Bob"));
		assertEquals("Record: 3 W / 1 L", DuelLines.record(3, 1));
	}

	private static DuelView view(boolean skating, List<DuelView.Member> members, Phase phase, boolean blocked)
	{
		return new DuelView(true, skating, true, members, phase, "Bob", 80, 41, 0, 0, null, blocked, true);
	}

	@Test
	public void everyPhaseHasItsStatus()
	{
		List<DuelView.Member> one = Collections.singletonList(new DuelView.Member(5L, "Al"));
		assertEquals("Waiting for Bob to answer...", view(true, one, Phase.CHALLENGING, false).status());
		assertEquals("Bob challenges you to a Skate Duel!", view(true, one, Phase.CHALLENGED, false).status());
		assertEquals("Bob challenges you to a Skate Duel! Start skating to accept.",
			view(false, one, Phase.CHALLENGED, false).status());
		assertEquals("Get ready: Skate Duel against Bob!", view(true, one, Phase.COUNTDOWN, false).status());
		assertEquals("Duelling Bob: you 80 HP, them 41 HP.", view(true, one, Phase.FIGHT, false).status());
		assertEquals("No Skate Duels in PvP areas or instances.", view(true, one, Phase.IDLE, true).status());
		assertEquals("No party members on this world are skating with duels on.",
			view(true, Collections.emptyList(), Phase.OVER, false).status());
		assertEquals("Challenge a skater (bragging rights only: no stakes).", view(true, one, Phase.IDLE, false).status());
		assertEquals("Start skating to challenge someone.", view(false, one, Phase.IDLE, false).status());
		assertEquals("Duels are off. Turn on \"Allow duel challenges\" in the plugin's settings (Play together section).",
			new DuelView(false, true, true, one, Phase.IDLE, null, 0, 0, 0, 0, null, false, true).status());
		assertEquals("Duels need \"Share my skater with my party\" on (Play together section of the plugin's settings).",
			new DuelView(true, true, true, one, Phase.IDLE, null, 0, 0, 0, 0, null, false, false).status());
		assertEquals("Join a RuneLite party to duel party members who are skating.",
			new DuelView(true, true, false, one, Phase.IDLE, null, 0, 0, 0, 0, null, false, true).status());
	}
}
