package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

/** A design held up part-way goes out again so receivers can still complete it. */
public class DesignOutboxTest
{
	private final DesignOutbox out = new DesignOutbox();
	private final DesignShare.Outgoing deck = DesignBudgetTest.deck(1);

	private static int index(PartyMessage m)
	{
		return m instanceof SkateDesignOffer ? -1 : ((SkateDesignChunk) m).index;
	}

	@Test
	public void sentPromptlyNothingIsRepeated()
	{
		out.set(DesignPart.DECK, deck, true);
		List<PartyMessage> all = deck.messages();
		for (int i = 0; i < all.size(); i++)
		{
			assertEquals(index(all.get(i)), index(out.poll(i * 1f)));
		}
		assertNull(out.poll(100f));
	}

	@Test
	public void anOfferWhoseChunksStallGoesAgainBeforeTheRest()
	{
		out.set(DesignPart.DECK, deck, true);
		int n = deck.chunks.size();
		assertTrue(n >= 4);
		assertTrue(out.poll(0f) instanceof SkateDesignOffer);
		assertEquals(0, index(out.poll(1f)));
		assertEquals(1, index(out.poll(2f)));
		// next chunk 25 s after the offer (the last chunk went 23 s ago, inside the receiver's 30 s): the offer
		// goes again first, then the chunks still to go
		assertTrue(out.poll(25f) instanceof SkateDesignOffer);
		for (int i = 2; i < n; i++)
		{
			assertEquals(i, index(out.poll(26f + i)));
		}
		assertNull(out.poll(60f));
	}

	@Test
	public void afterALongStallTheChunksAlreadySentGoAgainToo()
	{
		out.set(DesignPart.DECK, deck, true);
		int n = deck.chunks.size();
		out.poll(0f);
		out.poll(1f);
		out.poll(2f);
		// 40 s since anything of it went: the receiver has dropped what it had
		assertTrue(out.poll(42f) instanceof SkateDesignOffer);
		for (int i = 0; i < n; i++)
		{
			assertEquals(i, index(out.poll(43f + i)));
		}
		assertNull(out.poll(100f));
	}

	@Test
	public void queueingAgainResendsAPartAlreadyPartlySent()
	{
		out.set(DesignPart.DECK, deck, true);
		int all = deck.messages().size();
		// nothing of it sent yet: queueing again changes nothing
		out.queueAll();
		assertEquals(all, out.queue.size());
		out.poll(0f);
		out.poll(1f);
		// a new member: it never saw the offer or the first chunk
		out.queueAll();
		assertEquals(all, out.queue.size());
		assertTrue(out.poll(2f) instanceof SkateDesignOffer);
		assertEquals(0, index(out.poll(3f)));
	}
}
