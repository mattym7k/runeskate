package com.gielinorskate.duel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;

/**
 * A fake party for two duel clients: each client's messages wait in its own outbound queue (in send order, as
 * the party websocket keeps them) until the test delivers them, so tests can hold one side's messages back and
 * make them cross.
 */
final class DuelBus
{
	static final long A_ID = 101L;
	static final long B_ID = 202L;

	/** One client: its machine, what it sent and what it was told. */
	static final class Client implements DuelStateMachine.Outbox, DuelStateMachine.Listener
	{
		final long id;
		final DuelStateMachine machine;
		final Deque<PartyMessage> outbound = new ArrayDeque<>();
		final List<PartyMessage> sentLog = new ArrayList<>();
		/** The messages sent as a duel's last word (the one allowed from a PvP area or instance). */
		final List<PartyMessage> lastWords = new ArrayList<>();
		final List<String> events = new ArrayList<>();
		DuelStateMachine.Outcome outcome;
		DuelStateMachine.Cause cause;
		int damageTaken;
		int damageDealt;

		Client(long id, long duelIdSeed)
		{
			this.id = id;
			long[] next = {duelIdSeed};
			this.machine = new DuelStateMachine(this, this, () -> next[0]++);
			machine.setLocalId(id);
		}

		@Override
		public void send(PartyMessage message)
		{
			outbound.add(message);
			sentLog.add(message);
		}

		@Override
		public void sendLastWord(PartyMessage message)
		{
			send(message);
			lastWords.add(message);
		}

		@Override
		public void challenged(long fromId)
		{
			events.add("challenged by " + fromId);
		}

		@Override
		public void declined(long byId)
		{
			events.add("declined by " + byId);
		}

		@Override
		public void expired(long otherId, boolean mine)
		{
			events.add(mine ? "my challenge expired" : "their challenge expired");
		}

		@Override
		public void countdown(long opponentId)
		{
			events.add("countdown vs " + opponentId);
		}

		@Override
		public void fight()
		{
			events.add("fight");
		}

		@Override
		public void hit(boolean onMe, int damage, boolean selfInflicted)
		{
			events.add((onMe ? "took " : "dealt ") + damage + (selfInflicted ? " (bail)" : ""));
			if (onMe)
			{
				damageTaken += damage;
			}
			else if (!selfInflicted)
			{
				damageDealt += damage;
			}
		}

		@Override
		public void over(DuelStateMachine.Outcome outcome, DuelStateMachine.Cause cause, long opponentId)
		{
			this.outcome = outcome;
			this.cause = cause;
			events.add("over " + outcome + " " + cause);
		}

		@Override
		public void voided(DuelStateMachine.Outcome was, long opponentId)
		{
			this.outcome = DuelStateMachine.Outcome.CANCELLED;
			this.cause = DuelStateMachine.Cause.BOTH_QUIT;
			events.add("voided " + was);
		}

		<T extends PartyMessage> int count(Class<T> type)
		{
			return (int) sentLog.stream().filter(type::isInstance).count();
		}
	}

	final Client a = new Client(A_ID, 1_000L);
	final Client b = new Client(B_ID, 9_000L);

	Client other(Client c)
	{
		return c == a ? b : a;
	}

	/** Delivers everything {@code from} has sent so far to the other client. */
	void deliver(Client from, float now)
	{
		Client to = other(from);
		while (!from.outbound.isEmpty())
		{
			dispatch(from.id, to, from.outbound.poll(), now);
		}
	}

	/** Delivers both ways until nothing is left in flight. */
	void settle(float now)
	{
		while (!a.outbound.isEmpty() || !b.outbound.isEmpty())
		{
			deliver(a, now);
			deliver(b, now);
		}
	}

	/** Ticks both (both skating, allowed, in the party) and settles. */
	void tick(float now)
	{
		a.machine.tick(now, true, false, true);
		b.machine.tick(now, true, false, true);
		settle(now);
	}

	static void dispatch(long fromId, Client to, PartyMessage m, float now)
	{
		if (m instanceof SkateDuelChallenge)
		{
			to.machine.onChallenge(fromId, (SkateDuelChallenge) m, now, true);
		}
		else if (m instanceof SkateDuelReply)
		{
			to.machine.onReply(fromId, (SkateDuelReply) m, now);
		}
		else if (m instanceof SkateDuelHit)
		{
			to.machine.onHit(fromId, (SkateDuelHit) m, now);
		}
		else if (m instanceof SkateDuelEnd)
		{
			to.machine.onEnd(fromId, (SkateDuelEnd) m, now);
		}
	}

	/** A challenges B, B accepts, the countdown runs out: both fighting at {@code start + COUNTDOWN}. */
	float startFight(float start)
	{
		a.machine.challenge(B_ID, start);
		settle(start);
		b.machine.accept(start + 1f);
		settle(start + 1f);
		float fight = start + 1f + DuelStateMachine.COUNTDOWN;
		tick(fight);
		return fight;
	}
}
