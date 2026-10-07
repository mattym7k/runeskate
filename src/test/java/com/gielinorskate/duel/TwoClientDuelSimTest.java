package com.gielinorskate.duel;

import static com.gielinorskate.duel.DuelBus.B_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.Random;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

/**
 * Two duel clients on a fake party bus with random delivery delays: whatever order things happen in, both see the
 * same HP once their messages are through, and the results always agree (a win for one is a loss for the other,
 * or a draw for both).
 */
public class TwoClientDuelSimTest
{
	private static final float FRAME = 0.05f;

	@Test
	public void randomDuelsAlwaysAgree()
	{
		for (long seed = 1; seed <= 300; seed++)
		{
			runDuel(seed);
		}
	}

	/**
	 * One side's ghost updates never arrive (it does not share its skater, or its ghost failed) and both act
	 * rarely, so the 20 s silence rule ends duels on one side first: the other still finishes, and agrees.
	 */
	@Test
	public void duelsWithOneSideOftenSilentStillEndOnBothAndAgree()
	{
		for (long seed = 1; seed <= 150; seed++)
		{
			runDuel(seed, false, true, 2000);
		}
	}

	private void runDuel(long seed)
	{
		runDuel(seed, true, true, 100);
	}

	/**
	 * @param ghostsOfA B hears A's ghost updates
	 * @param ghostsOfB A hears B's ghost updates
	 * @param actOutOf each frame a combo lands with a chance of 4 in this many, a bail 2 in this many
	 */
	private void runDuel(long seed, boolean ghostsOfA, boolean ghostsOfB, int actOutOf)
	{
		Random rnd = new Random(seed);
		DuelBus bus = new DuelBus();
		float t = bus.startFight(0f);
		// per-direction delay: messages are held back for a random number of frames, in order
		int holdA = 0;
		int holdB = 0;
		for (int frame = 0; frame < 20_000 && (bus.a.outcome == null || bus.b.outcome == null); frame++)
		{
			t += FRAME;
			act(rnd, bus.a, t, actOutOf);
			act(rnd, bus.b, t, actOutOf);
			bus.a.machine.tick(t, true, false, true);
			bus.b.machine.tick(t, true, false, true);
			if (holdA-- <= 0)
			{
				bus.deliver(bus.a, t);
				holdA = rnd.nextInt(8);
			}
			if (holdB-- <= 0)
			{
				bus.deliver(bus.b, t);
				holdB = rnd.nextInt(8);
			}
			if (bus.a.outcome == null && bus.b.outcome == null && bus.a.outbound.isEmpty()
				&& bus.b.outbound.isEmpty())
			{
				// nothing in flight: both must show the same HP
				assertEquals("seed " + seed, bus.a.machine.myHp(), bus.b.machine.oppHp());
				assertEquals("seed " + seed, bus.b.machine.myHp(), bus.a.machine.oppHp());
			}
			// the opponent is heard from all the time through ghost updates
			if (ghostsOfB)
			{
				bus.a.machine.heard(B_ID, t);
			}
			if (ghostsOfA)
			{
				bus.b.machine.heard(DuelBus.A_ID, t);
			}
		}
		bus.settle(t);
		assertNotNull("seed " + seed + " A never finished", bus.a.outcome);
		assertNotNull("seed " + seed + " B never finished", bus.b.outcome);
		DuelStateMachine.Outcome a = bus.a.outcome;
		DuelStateMachine.Outcome b = bus.b.outcome;
		boolean agree = (a == DuelStateMachine.Outcome.WIN && b == DuelStateMachine.Outcome.LOSS)
			|| (a == DuelStateMachine.Outcome.LOSS && b == DuelStateMachine.Outcome.WIN)
			|| (a == DuelStateMachine.Outcome.DRAW && b == DuelStateMachine.Outcome.DRAW);
		assertTrue("seed " + seed + ": " + a + " vs " + b + "\nA " + bus.a.events + "\nB " + bus.b.events, agree);
		// the loser really is at 0 on the winner's screen
		if (a == DuelStateMachine.Outcome.WIN && bus.a.cause == DuelStateMachine.Cause.KO)
		{
			assertEquals("seed " + seed, 0, bus.a.machine.oppHp());
		}
		checkSeqs(bus.a);
		checkSeqs(bus.b);
	}

	private static void act(Random rnd, DuelBus.Client c, float t, int actOutOf)
	{
		int r = rnd.nextInt(actOutOf);
		if (r < 4)
		{
			c.machine.comboLanded(100 + rnd.nextInt(200_000), 1 + rnd.nextInt(10), t);
		}
		else if (r < 6)
		{
			c.machine.bail(t);
		}
	}

	/** Every message a client sent in one duel has a seq one higher than the one before. */
	private static void checkSeqs(DuelBus.Client c)
	{
		int last = 0;
		for (PartyMessage m : c.sentLog)
		{
			int seq;
			if (m instanceof SkateDuelChallenge)
			{
				seq = ((SkateDuelChallenge) m).seq;
			}
			else if (m instanceof SkateDuelReply)
			{
				seq = ((SkateDuelReply) m).seq;
			}
			else if (m instanceof SkateDuelHit)
			{
				seq = ((SkateDuelHit) m).seq;
			}
			else
			{
				seq = ((SkateDuelEnd) m).seq;
			}
			assertEquals(last + 1, seq);
			last = seq;
		}
	}
}
