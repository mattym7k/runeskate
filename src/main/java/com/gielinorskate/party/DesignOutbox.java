package com.gielinorskate.party;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.DesignPart;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.client.party.messages.PartyMessage;

/**
 * Our custom designs on their way to the party: each part's current shared design and the queue of its messages
 * (an offer, then its chunks). A design goes out again when it changes and when a party member's ghost first
 * appears (at most once per member per {@link DesignShare#REOFFER_SECONDS}). A design whose chunks are held up
 * (the budget had no room for them) is offered again {@link DesignShare#RESEND_OFFER_SECONDS} after its offer, before
 * its next chunk, so receivers keep what they have; held up so long that receivers have dropped it
 * ({@link DesignShare#ASSEMBLY_SECONDS} since anything of it went), its chunks already sent go again too. What goes
 * out when is the {@link GhostHub}'s call: these are the lowest priority messages. Pure; guarded by the hub.
 */
final class DesignOutbox
{
	private static final class Queued
	{
		final DesignPart part;
		final PartyMessage message;

		Queued(DesignPart part, PartyMessage message)
		{
			this.part = part;
			this.message = message;
		}
	}

	/** A part whose offer has gone out and whose chunks are going. */
	private static final class Sending
	{
		final Queued offer;
		/** When its offer last went out. */
		float offeredAt;
		/** When anything of it last went out. */
		float lastAt;
		/** Its chunks already sent since its offer, in order. */
		final ArrayDeque<Queued> sent = new ArrayDeque<>();

		Sending(Queued offer, float now)
		{
			this.offer = offer;
			this.offeredAt = now;
			this.lastAt = now;
		}
	}

	private final Map<DesignPart, DesignShare.Outgoing> current = new EnumMap<>(DesignPart.class);
	private final ArrayDeque<Queued> queue = new ArrayDeque<>();
	/** Parts part-way out: their offer went and chunks are still queued. */
	private final Map<DesignPart, Sending> sending = new EnumMap<>(DesignPart.class);
	/** When each member's ghost last sent our designs out again. */
	private final Map<Long, Float> offeredFor = new HashMap<>();

	/**
	 * Our shared design for {@code part} (null: none, or not custom). A different one replaces what of the old one
	 * is still queued and, when {@code queue}, goes out. True if it changed.
	 */
	boolean set(DesignPart part, DesignShare.Outgoing design, boolean queue)
	{
		DesignShare.Outgoing old = current.get(part);
		if (old == design || old != null && design != null && old.hash.equals(design.hash)
			&& old.designId.equals(design.designId))
		{
			return false;
		}
		if (design == null)
		{
			current.remove(part);
		}
		else
		{
			current.put(part, design);
		}
		drop(part);
		if (queue && design != null)
		{
			add(design);
		}
		return true;
	}

	/**
	 * Queues every current design not already waiting whole: one part-way out goes again from its offer (someone
	 * new may have missed what went).
	 */
	void queueAll()
	{
		for (DesignShare.Outgoing d : current.values())
		{
			if (sending.containsKey(d.part) || !queued(d.part))
			{
				drop(d.part);
				add(d);
			}
		}
	}

	/**
	 * A party member's ghost appeared at {@code now}: our designs go out again unless they did for this member in
	 * the last {@link DesignShare#REOFFER_SECONDS}. True if they were queued.
	 */
	boolean memberAppeared(long member, float now)
	{
		Float last = offeredFor.get(member);
		if (last != null && now - last < DesignShare.REOFFER_SECONDS)
		{
			return false;
		}
		offeredFor.put(member, now);
		queueAll();
		return !current.isEmpty();
	}

	/** The hash of {@code d}'s shared picture while it is the shared design of its part, else null. */
	String hashOf(BoardDesign d)
	{
		DesignShare.Outgoing o = d == null ? null : current.get(d.part);
		return o != null && o.designId.equals(d.id) ? o.hash : null;
	}

	boolean isEmpty()
	{
		return queue.isEmpty();
	}

	int queued()
	{
		return queue.size();
	}

	/** The next message, sent at {@code now}, removed; null when none. */
	PartyMessage poll(float now)
	{
		Queued q = queue.peek();
		if (q == null)
		{
			return null;
		}
		Sending s = sending.get(q.part);
		if (s != null && !(q.message instanceof SkateDesignOffer)
			&& now - s.offeredAt >= DesignShare.RESEND_OFFER_SECONDS)
		{
			// held up: offer it again first; if the receivers have dropped it, what went already goes again too
			if (now - s.lastAt > DesignShare.ASSEMBLY_SECONDS)
			{
				for (Iterator<Queued> it = s.sent.descendingIterator(); it.hasNext(); )
				{
					queue.addFirst(it.next());
				}
			}
			queue.addFirst(s.offer);
		}
		q = queue.poll();
		if (q.message instanceof SkateDesignOffer)
		{
			if (s == null)
			{
				sending.put(q.part, new Sending(q, now));
			}
			else
			{
				s.offeredAt = now;
				s.lastAt = now;
				s.sent.clear();
			}
		}
		else if (s != null)
		{
			s.lastAt = now;
			s.sent.add(q);
		}
		if (!queued(q.part))
		{
			sending.remove(q.part);
		}
		return q.message;
	}

	/** Nothing waits any more (sharing stopped, the party left). */
	void clearQueue()
	{
		queue.clear();
		sending.clear();
	}

	/** The party changed: who was sent what no longer matters. */
	void forgetMembers()
	{
		offeredFor.clear();
	}

	private boolean queued(DesignPart part)
	{
		for (Queued q : queue)
		{
			if (q.part == part)
			{
				return true;
			}
		}
		return false;
	}

	private void add(DesignShare.Outgoing d)
	{
		for (PartyMessage m : d.messages())
		{
			queue.add(new Queued(d.part, m));
		}
	}

	private void drop(DesignPart part)
	{
		sending.remove(part);
		for (Iterator<Queued> it = queue.iterator(); it.hasNext(); )
		{
			if (it.next().part == part)
			{
				it.remove();
			}
		}
	}
}
