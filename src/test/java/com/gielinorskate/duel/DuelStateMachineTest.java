package com.gielinorskate.duel;

import static com.gielinorskate.duel.DuelBus.A_ID;
import static com.gielinorskate.duel.DuelBus.B_ID;
import static com.gielinorskate.duel.DuelStateMachine.Cause;
import static com.gielinorskate.duel.DuelStateMachine.Outcome;
import static com.gielinorskate.duel.DuelStateMachine.Phase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;

/** One duel client's rules; the other side is driven by hand-made messages or a second client on a fake bus. */
public class DuelStateMachineTest
{
	private final DuelBus bus = new DuelBus();
	private final DuelBus.Client a = bus.a;
	private final DuelBus.Client b = bus.b;

	// ---- Challenge, accept, decline, expiry

	@Test
	public void aChallengeReachesTheOtherAndAcceptingStartsTheCountdownOnBoth()
	{
		assertTrue(a.machine.challenge(B_ID, 0f));
		assertEquals(Phase.CHALLENGING, a.machine.phase());
		bus.settle(0f);
		assertEquals(Phase.CHALLENGED, b.machine.phase());
		assertEquals(A_ID, b.machine.opponentId());
		assertTrue(b.events.contains("challenged by " + A_ID));
		assertTrue(b.machine.accept(2f));
		bus.settle(2f);
		assertEquals(Phase.COUNTDOWN, a.machine.phase());
		assertEquals(Phase.COUNTDOWN, b.machine.phase());
		bus.tick(2f + DuelStateMachine.COUNTDOWN - 0.01f);
		assertEquals(Phase.COUNTDOWN, a.machine.phase());
		bus.tick(2f + DuelStateMachine.COUNTDOWN);
		assertEquals(Phase.FIGHT, a.machine.phase());
		assertEquals(Phase.FIGHT, b.machine.phase());
		assertTrue(a.events.contains("fight"));
		assertEquals(99, a.machine.myHp());
		assertEquals(99, a.machine.oppHp());
	}

	@Test
	public void declineEndsTheChallengeOnBoth()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		b.machine.decline(1f);
		bus.settle(1f);
		assertEquals(Phase.IDLE, a.machine.phase());
		assertEquals(Phase.IDLE, b.machine.phase());
		assertTrue(a.events.contains("declined by " + B_ID));
	}

	@Test
	public void anUnansweredChallengeExpiresAfter30Seconds()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		bus.tick(29.9f);
		assertEquals(Phase.CHALLENGED, b.machine.phase());
		bus.tick(30f);
		assertEquals(Phase.IDLE, b.machine.phase());
		assertTrue(b.events.contains("their challenge expired"));
		// the challenger waits a moment longer for a reply still on its way
		assertEquals(Phase.CHALLENGING, a.machine.phase());
		bus.tick(30f + DuelStateMachine.REPLY_GRACE);
		assertEquals(Phase.IDLE, a.machine.phase());
		assertTrue(a.events.contains("my challenge expired"));
		assertEquals(0, b.count(SkateDuelReply.class));
	}

	@Test
	public void anAcceptThatArrivesAfterTheChallengeExpiredIsCalledOff()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		b.machine.accept(29f);
		// held up: the challenger expires first
		a.machine.tick(32f, true, false, true);
		assertEquals(Phase.IDLE, a.machine.phase());
		bus.settle(32.5f);
		assertEquals(Phase.IDLE, b.machine.phase());
		assertEquals(Outcome.CANCELLED, b.outcome);
	}

	@Test
	public void aWithdrawnChallengeIsGoneOnBoth()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		a.machine.withdraw(3f);
		bus.settle(3f);
		assertEquals(Phase.IDLE, a.machine.phase());
		assertEquals(Phase.IDLE, b.machine.phase());
		assertFalse(b.machine.accept(4f));
	}

	@Test
	public void aBusyOrUnwillingMemberDeclinesAtOnce()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		// a third member challenges B while B is answering A
		DuelBus.Client c = new DuelBus.Client(303L, 50_000L);
		c.machine.challenge(B_ID, 1f);
		DuelBus.dispatch(c.id, b, c.outbound.poll(), 1f);
		assertEquals(A_ID, b.machine.opponentId());
		SkateDuelReply r = (SkateDuelReply) b.outbound.pollLast();
		assertFalse(r.accepted);
		assertEquals(c.id, r.targetMemberId);

		// "Allow duel challenges" off: declined without asking
		DuelBus.Client d = new DuelBus.Client(404L, 60_000L);
		DuelBus.Client e = new DuelBus.Client(505L, 70_000L);
		d.machine.challenge(e.id, 0f);
		e.machine.onChallenge(d.id, (SkateDuelChallenge) d.outbound.poll(), 0f, false);
		assertEquals(Phase.IDLE, e.machine.phase());
		assertFalse(((SkateDuelReply) e.outbound.poll()).accepted);
	}

	@Test
	public void challengesThatCrossBecomeOneDuel()
	{
		a.machine.challenge(B_ID, 0f);
		b.machine.challenge(A_ID, 0f);
		bus.settle(0.1f);
		// B's duel ID (from 9000) is higher than A's (from 1000): B's challenge stands
		assertEquals(Phase.CHALLENGED, a.machine.phase());
		assertEquals(Phase.CHALLENGING, b.machine.phase());
		a.machine.accept(1f);
		bus.settle(1f);
		assertEquals(Phase.COUNTDOWN, a.machine.phase());
		assertEquals(Phase.COUNTDOWN, b.machine.phase());
	}

	@Test
	public void youCannotChallengeYourselfOrTwiceAtOnce()
	{
		assertFalse(a.machine.challenge(A_ID, 0f));
		assertTrue(a.machine.challenge(B_ID, 0f));
		assertFalse(a.machine.challenge(303L, 0f));
	}

	@Test
	public void leavingThePartyOrPvpDropsAChallengeWithoutSending()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		int sent = b.sentLog.size();
		b.machine.tick(1f, true, true, true);
		assertEquals(Phase.IDLE, b.machine.phase());
		assertEquals(sent, b.sentLog.size());
	}

	// ---- Damage

	@Test
	public void aLandedComboHitsTheOpponentOnBothClients()
	{
		float t = bus.startFight(0f);
		a.machine.comboLanded(5_000, 4, t);
		bus.settle(t);
		assertEquals(92, a.machine.oppHp());
		assertEquals(92, b.machine.myHp());
		assertTrue(b.events.contains("took 7"));
		SkateDuelHit h = (SkateDuelHit) a.sentLog.get(a.sentLog.size() - 1);
		assertEquals(5_000, h.comboValue);
		assertEquals(4, h.trickCount);
		assertEquals(B_ID, h.targetMemberId);
		assertFalse(h.selfInflicted);
	}

	@Test
	public void combosBeforeTheFightDoNothing()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		b.machine.accept(1f);
		bus.settle(1f);
		a.machine.comboLanded(100_000, 9, 2f);
		bus.tick(2f);
		assertEquals(99, b.machine.myHp());
		assertEquals(0, a.count(SkateDuelHit.class));
	}

	@Test
	public void aBailCostsTheBailerFourOnBothClients()
	{
		float t = bus.startFight(0f);
		b.machine.bail(t);
		bus.settle(t);
		assertEquals(95, b.machine.myHp());
		assertEquals(95, a.machine.oppHp());
		assertTrue(b.events.contains("took 4 (bail)"));
		SkateDuelHit h = (SkateDuelHit) b.sentLog.get(b.sentLog.size() - 1);
		assertTrue(h.selfInflicted);
		assertEquals(B_ID, h.targetMemberId);
	}

	@Test
	public void combosLandingInsideTheCooldownAreQueuedThenApplied()
	{
		float t = bus.startFight(0f);
		a.machine.comboLanded(1_000, 1, t);
		a.machine.comboLanded(1_000, 1, t + 0.2f);
		a.machine.comboLanded(1_000, 1, t + 0.4f);
		bus.settle(t + 0.4f);
		assertEquals(96, b.machine.myHp());
		assertEquals(2, a.machine.queuedHits());
		bus.tick(t + 1.49f);
		assertEquals(96, b.machine.myHp());
		bus.tick(t + 1.5f);
		assertEquals(93, b.machine.myHp());
		bus.tick(t + 3.0f);
		assertEquals(90, b.machine.myHp());
		assertEquals(90, a.machine.oppHp());
	}

	// ---- Validation and ordering

	private SkateDuelHit forged(DuelBus.Client from, long target, int damage, int seq, long duelId)
	{
		SkateDuelHit h = new SkateDuelHit();
		h.duelId = duelId;
		h.seq = seq;
		h.targetMemberId = target;
		h.damage = damage;
		return h;
	}

	private long duelIdOf(DuelBus.Client c)
	{
		for (Object m : c.sentLog)
		{
			if (m instanceof SkateDuelChallenge)
			{
				return ((SkateDuelChallenge) m).duelId;
			}
		}
		throw new AssertionError("no challenge sent");
	}

	@Test
	public void invalidHitsAreIgnored()
	{
		float t = bus.startFight(0f);
		long duel = duelIdOf(a);
		// A has sent challenge (1); its next seq is 2
		b.machine.onHit(A_ID, forged(a, B_ID, 0, 2, duel), t);
		b.machine.onHit(A_ID, forged(a, B_ID, 26, 3, duel), t);
		b.machine.onHit(A_ID, forged(a, A_ID, 5, 4, duel), t);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 5, duel + 1), t);
		b.machine.onHit(303L, forged(a, B_ID, 5, 6, duel), t);
		assertEquals(99, b.machine.myHp());
		// a valid one still goes through, and a repeat of its seq does not
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 5, duel), t);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 5, duel), t);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 3, duel), t);
		assertEquals(94, b.machine.myHp());
	}

	@Test
	public void hitsArriveOutOfOrderAndAreAppliedInSeqOrder()
	{
		float t = bus.startFight(0f);
		long duel = duelIdOf(a);
		b.machine.onHit(A_ID, forged(a, B_ID, 10, 3, duel), t);
		assertEquals("waits for seq 2", 99, b.machine.myHp());
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 2, duel), t);
		assertEquals(84, b.machine.myHp());
		int took5 = b.events.indexOf("took 5");
		int took10 = b.events.indexOf("took 10");
		assertTrue(took5 >= 0 && took5 < took10);
	}

	@Test
	public void aLostMessageDoesNotStallTheDuel()
	{
		float t = bus.startFight(0f);
		long duel = duelIdOf(a);
		b.machine.onHit(A_ID, forged(a, B_ID, 10, 3, duel), t);
		b.machine.tick(t + SeqInbox.GAP_TIMEOUT, true, false, true);
		b.machine.tick(t + SeqInbox.GAP_TIMEOUT + 0.1f, true, false, true);
		assertEquals(89, b.machine.myHp());
	}

	// ---- End: KO, draw, forfeits

	@Test
	public void aKoEndsItWithAWinnerAndALoser()
	{
		float t = bus.startFight(0f);
		for (int i = 0; i < 4; i++)
		{
			a.machine.comboLanded(250_000, 10, t);
			bus.tick(t);
			t += HitCooldown.COOLDOWN;
		}
		// 4 x 25 = 100 >= 99
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.KO, a.cause);
		assertEquals(Phase.OVER, a.machine.phase());
		assertEquals(0, a.machine.oppHp());
		assertEquals(0, b.machine.myHp());
	}

	@Test
	public void bailingToZeroLosesTheDuel()
	{
		float t = bus.startFight(0f);
		for (int i = 0; i < 25; i++)
		{
			b.machine.bail(t);
		}
		bus.settle(t);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals("no bail after the KO", 25, b.count(SkateDuelHit.class));
	}

	@Test
	public void koMessagesThatCrossAreADrawOnBoth()
	{
		float t = bus.startFight(0f);
		// both at 4
		for (int i = 0; i < 3; i++)
		{
			a.machine.comboLanded(250_000, 10, t);
			b.machine.comboLanded(250_000, 10, t);
			bus.tick(t);
			t += HitCooldown.COOLDOWN;
		}
		a.machine.comboLanded(60_000, 10, t);
		b.machine.comboLanded(60_000, 10, t);
		bus.tick(t);
		assertEquals(4, a.machine.myHp());
		assertEquals(4, b.machine.myHp());
		t += HitCooldown.COOLDOWN;
		// each lands the finishing hit before hearing of the other's
		a.machine.comboLanded(5_000, 3, t);
		b.machine.comboLanded(5_000, 3, t);
		bus.settle(t);
		assertEquals(Outcome.DRAW, a.outcome);
		assertEquals(Outcome.DRAW, b.outcome);
	}

	@Test
	public void aBailToZeroCrossingTheOthersKoIsADraw()
	{
		float t = bus.startFight(0f);
		for (int i = 0; i < 24; i++)
		{
			b.machine.bail(t);
			a.machine.bail(t);
		}
		bus.settle(t);
		assertEquals(3, a.machine.myHp());
		// both bail out at once
		a.machine.bail(t);
		b.machine.bail(t);
		bus.settle(t);
		assertEquals(Outcome.DRAW, a.outcome);
		assertEquals(Outcome.DRAW, b.outcome);
	}

	@Test
	public void stoppingSkatingForTenSecondsForfeits()
	{
		float t = bus.startFight(0f);
		b.machine.tick(t + 1f, false, false, true);
		b.machine.tick(t + 10.9f, false, false, true);
		assertEquals(Phase.FIGHT, b.machine.phase());
		assertEquals(0.1f, b.machine.restartLeft(t + 10.9f), 1e-4f);
		b.machine.tick(t + 11f, false, false, true);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.STOPPED_SKATING, b.cause);
		bus.settle(t + 11f);
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals(Cause.OPPONENT_FORFEIT, a.cause);
	}

	@Test
	public void gettingBackOnWithinTenSecondsKeepsTheDuelGoing()
	{
		float t = bus.startFight(0f);
		b.machine.tick(t + 1f, false, false, true);
		b.machine.tick(t + 9f, true, false, true);
		assertTrue(Float.isNaN(b.machine.restartLeft(t + 9f)));
		b.machine.heard(A_ID, t + 12f);
		b.machine.tick(t + 12f, false, false, true);
		b.machine.tick(t + 20f, false, false, true);
		assertEquals(Phase.FIGHT, b.machine.phase());
	}

	@Test
	public void twentySecondsOfSilenceIsAForfeit()
	{
		float t = bus.startFight(0f);
		a.machine.heard(B_ID, t + 5f);
		a.machine.tick(t + 24.9f, true, false, true);
		assertEquals(Phase.FIGHT, a.machine.phase());
		a.machine.tick(t + 25f, true, false, true);
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals(Cause.OPPONENT_SILENT, a.cause);
	}

	@Test
	public void theOpponentLeavingThePartyIsAForfeit()
	{
		float t = bus.startFight(0f);
		a.machine.memberLeft(303L, t);
		assertEquals(Phase.FIGHT, a.machine.phase());
		a.machine.memberLeft(B_ID, t);
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals(Cause.OPPONENT_LEFT, a.cause);
	}

	@Test
	public void leavingThePartyLosesWithoutSending()
	{
		float t = bus.startFight(0f);
		int sent = b.sentLog.size();
		b.machine.tick(t, true, false, false);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.LEFT, b.cause);
		assertEquals(sent, b.sentLog.size());
	}

	@Test
	public void enteringPvpOrAnInstanceForfeitsWithOneLastMessage()
	{
		float t = bus.startFight(0f);
		b.machine.tick(t, true, true, true);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.BLOCKED, b.cause);
		SkateDuelEnd e = (SkateDuelEnd) b.sentLog.get(b.sentLog.size() - 1);
		assertEquals("FORFEIT", e.reason);
		assertEquals("sent as the one last word", Collections.singletonList(e), b.lastWords);
		b.machine.tick(t + 1f, true, true, true);
		b.machine.bail(t + 1f);
		assertEquals("nothing more", e, b.sentLog.get(b.sentLog.size() - 1));
		bus.settle(t);
		assertEquals(Outcome.WIN, a.outcome);
	}

	@Test
	public void quittingForfeitsAFightAndTakesBackAChallenge()
	{
		float t = bus.startFight(0f);
		a.machine.quit(Cause.SHUTDOWN, t);
		bus.settle(t);
		assertEquals(Outcome.LOSS, a.outcome);
		assertEquals(Outcome.WIN, b.outcome);

		DuelBus other = new DuelBus();
		other.a.machine.challenge(B_ID, 0f);
		other.settle(0f);
		other.a.machine.quit(Cause.SHUTDOWN, 1f);
		other.settle(1f);
		assertEquals(Phase.IDLE, other.b.machine.phase());
		assertEquals("the withdrawal is a last word (sent at shutdown)", 1, other.a.lastWords.size());

		DuelBus third = new DuelBus();
		third.a.machine.challenge(B_ID, 0f);
		third.settle(0f);
		third.b.machine.quit(Cause.SHUTDOWN, 1f);
		assertEquals("the decline is a last word", 1, third.b.lastWords.size());
	}

	@Test
	public void theResultStaysUpThenClears()
	{
		float t = bus.startFight(0f);
		a.machine.memberLeft(B_ID, t);
		a.machine.tick(t + DuelStateMachine.RESULT_SECONDS - 0.1f, true, false, true);
		assertEquals(Phase.OVER, a.machine.phase());
		a.machine.tick(t + DuelStateMachine.RESULT_SECONDS, true, false, true);
		assertEquals(Phase.IDLE, a.machine.phase());
		assertEquals(Outcome.WIN, a.machine.outcome());
		// and a new challenge can go out
		assertTrue(a.machine.challenge(B_ID, t + 9f));
	}

	@Test
	public void hitsForADuelWeAreNotInAreCalledOffOnce()
	{
		SkateDuelHit h = forged(a, B_ID, 5, 2, 777L);
		b.machine.onHit(A_ID, h, 0f);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 3, 777L), 0f);
		assertEquals(1, b.count(SkateDuelEnd.class));
		SkateDuelEnd e = (SkateDuelEnd) b.sentLog.get(0);
		assertEquals("CANCEL", e.reason);
		assertEquals(777L, e.duelId);
		assertNull(b.outcome);
	}

	// ---- Nothing from a PvP area or instance (but the one forfeit)

	@Test
	public void noChallengeOrAcceptGoesOutFromAPvpAreaOrInstance()
	{
		a.machine.tick(0f, true, true, true);
		assertFalse(a.machine.challenge(B_ID, 0f));
		assertEquals(Phase.IDLE, a.machine.phase());
		assertEquals(0, a.sentLog.size());

		DuelBus other = new DuelBus();
		other.a.machine.challenge(B_ID, 0f);
		other.settle(0f);
		assertEquals(Phase.CHALLENGED, other.b.machine.phase());
		other.b.machine.tick(0.5f, true, true, true);
		assertFalse(other.b.machine.accept(0.5f));
		assertEquals(0, other.b.sentLog.size());
	}

	@Test
	public void noAnswerGoesOutFromAPvpAreaOrInstance()
	{
		b.machine.tick(0f, true, true, true);
		// a challenge (allowed or not) is left to run out
		a.machine.challenge(B_ID, 0f);
		SkateDuelChallenge c = (SkateDuelChallenge) a.outbound.poll();
		b.machine.onChallenge(A_ID, c, 0f, false);
		b.machine.onChallenge(A_ID, c, 0f, true);
		assertEquals(Phase.IDLE, b.machine.phase());
		// a late accept and an orphan hit are not called off from here
		SkateDuelReply r = new SkateDuelReply();
		r.duelId = 4242L;
		r.seq = 1;
		r.targetMemberId = B_ID;
		r.accepted = true;
		b.machine.onReply(A_ID, r, 0f);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 2, 777L), 0f);
		assertEquals(0, b.sentLog.size());
		// out again: the orphan is answered
		b.machine.tick(1f, true, false, true);
		b.machine.onHit(A_ID, forged(a, B_ID, 5, 3, 778L), 1f);
		assertEquals(1, b.count(SkateDuelEnd.class));
	}

	// ---- One side ending alone never leaves the other stuck

	@Test
	public void endingOnSilenceTellsTheOtherItLost()
	{
		float t = bus.startFight(0f);
		a.machine.tick(t + DuelStateMachine.SILENCE, true, false, true);
		assertEquals(Outcome.WIN, a.outcome);
		assertEquals(Cause.OPPONENT_SILENT, a.cause);
		assertEquals("TIMEOUT", ((SkateDuelEnd) a.sentLog.get(a.sentLog.size() - 1)).reason);
		bus.settle(t + DuelStateMachine.SILENCE);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.TIMED_OUT, b.cause);
	}

	@Test
	public void theOpponentLeavingIsAnsweredWithAnEndToo()
	{
		float t = bus.startFight(0f);
		int ends = a.count(SkateDuelEnd.class);
		a.machine.memberLeft(B_ID, t);
		assertEquals(ends + 1, a.count(SkateDuelEnd.class));
	}

	@Test
	public void aMessageForADuelJustFinishedIsAnsweredOnce()
	{
		float t = bus.startFight(0f);
		a.machine.tick(t + DuelStateMachine.SILENCE, true, false, true);
		// A's end is lost on the way
		a.outbound.clear();
		int sent = a.sentLog.size();
		float later = t + DuelStateMachine.SILENCE + 1f;
		// B still hears A (its ghost updates still come)
		b.machine.heard(A_ID, later);
		b.machine.comboLanded(100_000, 5, later);
		b.machine.tick(later, true, false, true);
		b.machine.comboLanded(100_000, 5, later + HitCooldown.COOLDOWN);
		b.machine.tick(later + HitCooldown.COOLDOWN, true, false, true);
		bus.deliver(b, later + HitCooldown.COOLDOWN);
		assertEquals("answered once", sent + 1, a.sentLog.size());
		bus.settle(later + HitCooldown.COOLDOWN);
		// the lost end leaves a gap in A's seqs: B waits it out, then takes the answer
		b.machine.heard(A_ID, later + 2f);
		b.machine.tick(later + HitCooldown.COOLDOWN + SeqInbox.GAP_TIMEOUT, true, false, true);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Outcome.WIN, a.outcome);
	}

	@Test
	public void aKnockedOutSkaterWithNoAnswerLosesAfterTenSeconds()
	{
		float t = bus.startFight(0f);
		for (int i = 0; i < 25; i++)
		{
			b.machine.bail(t);
		}
		assertTrue(b.machine.isSelfKo());
		// nothing reaches A, and A's answer never comes
		b.outbound.clear();
		b.machine.heard(A_ID, t + 9f);
		b.machine.tick(t + DuelStateMachine.KO_WAIT - 0.1f, true, false, true);
		assertEquals(Phase.FIGHT, b.machine.phase());
		b.machine.tick(t + DuelStateMachine.KO_WAIT, true, false, true);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.KO, b.cause);
	}

	@Test
	public void duelsNeedPartySharing()
	{
		a.machine.sharing(false, 0f);
		assertFalse(a.machine.challenge(B_ID, 0f));
		a.machine.sharing(true, 0f);
		float t = bus.startFight(0f);
		a.machine.sharing(false, t);
		assertEquals(Outcome.LOSS, a.outcome);
		assertEquals(Cause.SHARING_OFF, a.cause);
		bus.settle(t);
		assertEquals(Outcome.WIN, b.outcome);

		DuelBus other = new DuelBus();
		other.a.machine.challenge(B_ID, 0f);
		other.settle(0f);
		other.b.machine.sharing(false, 0.5f);
		assertFalse(other.b.machine.accept(0.5f));
	}

	@Test
	public void aChallengeOrReplyMustStartAtSeqOne()
	{
		a.machine.challenge(B_ID, 0f);
		SkateDuelChallenge c = (SkateDuelChallenge) a.outbound.poll();
		c.seq = Integer.MAX_VALUE - 5;
		b.machine.onChallenge(A_ID, c, 0f, true);
		assertEquals(Phase.IDLE, b.machine.phase());
		assertEquals(0, b.sentLog.size());

		SkateDuelReply r = new SkateDuelReply();
		r.duelId = c.duelId;
		r.seq = Integer.MAX_VALUE - 5;
		r.targetMemberId = A_ID;
		r.accepted = true;
		a.machine.onReply(B_ID, r, 1f);
		assertEquals(Phase.CHALLENGING, a.machine.phase());
		r.seq = 1;
		a.machine.onReply(B_ID, r, 1f);
		assertEquals(Phase.COUNTDOWN, a.machine.phase());
	}

	@Test
	public void turningDuelsOffForfeitsADuelAndTakesBackAChallenge()
	{
		float t = bus.startFight(0f);
		a.machine.allowed(false, t);
		assertEquals(Outcome.LOSS, a.outcome);
		assertEquals(Cause.DUELS_OFF, a.cause);
		bus.settle(t);
		assertEquals(Outcome.WIN, b.outcome);

		DuelBus other = new DuelBus();
		other.a.machine.challenge(B_ID, 0f);
		other.settle(0f);
		other.a.machine.allowed(false, 1f);
		other.settle(1f);
		assertEquals(Phase.IDLE, other.a.machine.phase());
		assertEquals(Phase.IDLE, other.b.machine.phase());
		assertFalse(other.a.machine.challenge(B_ID, 2f));
		other.a.machine.allowed(true, 2f);
		assertTrue(other.a.machine.challenge(B_ID, 2f));
	}

	// ---- Quits in the countdown, and quits that cross, count for nobody

	/** A challenges B and B accepts: both in the countdown at 1 s. */
	private void startCountdown()
	{
		a.machine.challenge(B_ID, 0f);
		bus.settle(0f);
		b.machine.accept(1f);
		bus.settle(1f);
		assertEquals(Phase.COUNTDOWN, a.machine.phase());
		assertEquals(Phase.COUNTDOWN, b.machine.phase());
	}

	@Test
	public void quittingInTheCountdownCallsItOffOnBoth()
	{
		startCountdown();
		b.machine.quit(Cause.SHUTDOWN, 2f);
		assertEquals(Outcome.CANCELLED, b.outcome);
		assertEquals(Phase.IDLE, b.machine.phase());
		assertEquals("CANCEL", ((SkateDuelEnd) b.lastWords.get(b.lastWords.size() - 1)).reason);
		bus.settle(2f);
		assertEquals(Outcome.CANCELLED, a.outcome);
	}

	@Test
	public void goingIntoPvpInTheCountdownCallsItOffWithOneLastWord()
	{
		startCountdown();
		b.machine.tick(2f, true, true, true);
		assertEquals(Outcome.CANCELLED, b.outcome);
		assertEquals(Cause.BLOCKED, b.cause);
		assertEquals(1, b.lastWords.size());
		bus.settle(2f);
		assertEquals(Outcome.CANCELLED, a.outcome);
	}

	@Test
	public void leavingInTheCountdownCallsItOff()
	{
		startCountdown();
		b.machine.tick(2f, true, false, false);
		assertEquals(Outcome.CANCELLED, b.outcome);
		a.machine.memberLeft(B_ID, 2f);
		assertEquals(Outcome.CANCELLED, a.outcome);
	}

	@Test
	public void forfeitsThatCrossCountForNobody()
	{
		float t = bus.startFight(0f);
		a.machine.quit(Cause.SHUTDOWN, t);
		b.machine.tick(t, true, true, true);
		assertEquals(Outcome.LOSS, a.outcome);
		assertEquals(Outcome.LOSS, b.outcome);
		bus.settle(t);
		assertEquals(Outcome.CANCELLED, a.outcome);
		assertEquals(Outcome.CANCELLED, b.outcome);
		assertTrue(a.events.contains("voided LOSS"));
		assertTrue(b.events.contains("voided LOSS"));
		assertEquals(Outcome.CANCELLED, a.machine.outcome());
	}

	@Test
	public void aForfeitCrossingACountdownQuitCountsForNobody()
	{
		startCountdown();
		// A's countdown ran out first
		a.machine.tick(1f + DuelStateMachine.COUNTDOWN, true, false, true);
		assertEquals(Phase.FIGHT, a.machine.phase());
		a.machine.quit(Cause.SHUTDOWN, 4f);
		b.machine.quit(Cause.SHUTDOWN, 3.9f);
		bus.settle(4f);
		assertEquals(Outcome.CANCELLED, a.outcome);
		assertEquals(Outcome.CANCELLED, b.outcome);
	}

	@Test
	public void aWinIsNotVoidedByALateForfeit()
	{
		float t = bus.startFight(0f);
		a.machine.quit(Cause.SHUTDOWN, t);
		bus.settle(t);
		assertEquals(Outcome.WIN, b.outcome);
		SkateDuelEnd again = new SkateDuelEnd();
		again.duelId = duelIdOf(a);
		again.seq = 99;
		again.targetMemberId = B_ID;
		again.reason = "FORFEIT";
		b.machine.onEnd(A_ID, again, t + 1f);
		assertEquals(Outcome.WIN, b.outcome);
	}

	@Test
	public void hoppingWorldsForfeits()
	{
		float t = bus.startFight(0f);
		b.machine.quit(Cause.HOPPED, t);
		assertEquals(Outcome.LOSS, b.outcome);
		assertEquals(Cause.HOPPED, b.cause);
		bus.settle(t);
		assertEquals(Outcome.WIN, a.outcome);
	}
}
