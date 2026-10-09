package com.gielinorskate.party;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.util.Base64;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

public class DesignInboxTest
{
	private final DesignInbox inbox = new DesignInbox();

	private static DesignShare.Outgoing design(DesignPart part, int bytes, long seed)
	{
		int side = SharedDesignImage.maxSide(part);
		return DesignShare.outgoing("CUSTOM_0A1B2C3D", "Mine",
			DesignShareTest.picture(part, side / 3, side, bytes, seed));
	}

	/** Feeds every message of {@code d} from {@code member}; the picture if it completed. */
	private DesignInbox.Assembled feed(long member, DesignShare.Outgoing d, float now)
	{
		DesignInbox.Assembled done = null;
		for (PartyMessage m : d.messages())
		{
			if (m instanceof SkateDesignOffer)
			{
				inbox.offer(member, (SkateDesignOffer) m, now);
			}
			else
			{
				DesignInbox.Assembled a = inbox.chunk(member, (SkateDesignChunk) m, now);
				if (a != null)
				{
					done = a;
				}
			}
		}
		return done;
	}

	@Test
	public void aWholeDesignAssemblesToTheSentBytes()
	{
		DesignShare.Outgoing d = design(DesignPart.DECK, 5000, 1);
		DesignInbox.Assembled a = feed(7L, d, 0f);
		assertNotNull(a);
		assertEquals(7L, a.member);
		assertEquals(DesignPart.DECK, a.part);
		assertEquals(d.hash, a.hash);
		assertEquals(d.width, a.width);
		assertEquals(d.height, a.height);
		assertEquals("Mine", a.name);
		assertEquals(d.hash, DesignShare.hash(a.png));
		assertEquals(0, inbox.pending.size());
	}

	@Test
	public void chunksInAnyOrderAndRepeatsStillAssemble()
	{
		DesignShare.Outgoing d = design(DesignPart.GRIP, 4500, 2);
		List<PartyMessage> msgs = d.messages();
		assertTrue(inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 0f));
		DesignInbox.Assembled a = null;
		for (int i = msgs.size() - 1; i >= 1; i--)
		{
			if (i == msgs.size() - 2)
			{
				// a repeat of a chunk already here changes nothing
				assertNull(inbox.chunk(1L, (SkateDesignChunk) msgs.get(i + 1), 0f));
			}
			DesignInbox.Assembled got = inbox.chunk(1L, (SkateDesignChunk) msgs.get(i), 0f);
			if (got != null)
			{
				a = got;
			}
		}
		assertNotNull(a);
	}

	@Test
	public void chunksWithoutAnOfferOrFromAnotherMemberAreDropped()
	{
		DesignShare.Outgoing d = design(DesignPart.WHEELS, 1500, 3);
		List<PartyMessage> msgs = d.messages();
		for (int i = 1; i < msgs.size(); i++)
		{
			assertNull(inbox.chunk(1L, (SkateDesignChunk) msgs.get(i), 0f));
		}
		inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 0f);
		// another member cannot complete member 1's picture
		for (int i = 1; i < msgs.size(); i++)
		{
			assertNull(inbox.chunk(2L, (SkateDesignChunk) msgs.get(i), 0f));
		}
		assertEquals(1, inbox.pending.size());
	}

	private static SkateDesignOffer offer(String part, String hash, int chunks, int w, int h)
	{
		SkateDesignOffer o = new SkateDesignOffer();
		o.part = part;
		o.hash = hash;
		o.name = "x";
		o.totalChunks = chunks;
		o.width = w;
		o.height = h;
		return o;
	}

	@Test
	public void offersOutsideTheLimitsAreDropped()
	{
		assertTrue(inbox.offer(1L, offer("deck", "0123abcd", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("hull", "0123abcd", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer(null, "0123abcd", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123abc", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123456789abcdef0", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123ABCD", 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", null, 3, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123abcd", 0, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123abcd", 13, 35, 96), 0f));
		// 10 chunks of 1000 cannot fit 8 KB
		assertFalse(inbox.offer(1L, offer("deck", "0123abcd", 10, 35, 96), 0f));
		assertFalse(inbox.offer(1L, offer("deck", "0123abcd", 3, 35, 97), 0f));
		assertFalse(inbox.offer(1L, offer("wheels", "0123abcd", 3, 41, 40), 0f));
		assertFalse(inbox.offer(1L, offer("wheels", "0123abcd", 3, 0, 40), 0f));
		assertFalse(inbox.offer(1L, null, 0f));
		assertTrue(inbox.offer(1L, offer("deck", "0123456789abcdef", 9, 96, 96), 0f));
	}

	private static SkateDesignChunk chunk(String hash, int index, String data)
	{
		SkateDesignChunk c = new SkateDesignChunk();
		c.hash = hash;
		c.index = index;
		c.data = data;
		return c;
	}

	@Test
	public void badChunksAreDropped()
	{
		inbox.offer(1L, offer("deck", "0123abcd", 2, 35, 96), 0f);
		assertNull(inbox.chunk(1L, chunk("0123abcd", 2, "AAAA"), 0f));
		assertNull(inbox.chunk(1L, chunk("0123abcd", -1, "AAAA"), 0f));
		assertNull(inbox.chunk(1L, chunk("0123abcd", 0, "not base64!"), 0f));
		assertNull(inbox.chunk(1L, chunk("0123abcd", 0, ""), 0f));
		assertNull(inbox.chunk(1L, chunk("0123abcd", 0, null), 0f));
		assertNull(inbox.chunk(1L, chunk("0123abcd", 0, repeat('A', 1001)), 0f));
		assertNull(inbox.chunk(1L, chunk("bad", 0, "AAAA"), 0f));
		assertNull(inbox.chunk(1L, null, 0f));
		assertEquals(1, inbox.pending.size());
	}

	@Test
	public void aPictureNotMatchingItsHashIsDropped()
	{
		inbox.offer(1L, offer("deck", "0123abcd", 1, 35, 96), 0f);
		assertNull(inbox.chunk(1L, chunk("0123abcd", 0, "AAAA"), 0f));
		assertEquals(0, inbox.pending.size());
	}

	@Test
	public void aPictureOverEightKilobytesIsDroppedBeforeDecoding()
	{
		// 9 chunks of 1000 announce up to 9000 characters; the cap is 8192
		inbox.offer(1L, offer("deck", "0123abcd", 9, 35, 96), 0f);
		for (int i = 0; i < 8; i++)
		{
			assertNull(inbox.chunk(1L, chunk("0123abcd", i, repeat('A', 1000)), 0f));
		}
		assertEquals(1, inbox.pending.size());
		assertNull(inbox.chunk(1L, chunk("0123abcd", 8, repeat('A', 1000)), 0f));
		assertEquals(0, inbox.pending.size());
	}

	@Test
	public void incompletePicturesExpireThirtySecondsAfterTheirLastProgress()
	{
		DesignShare.Outgoing d = design(DesignPart.DECK, 3000, 4);
		List<PartyMessage> msgs = d.messages();
		inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 0f);
		inbox.expire(29.9f);
		assertEquals(1, inbox.pending.size());
		inbox.expire(30.1f);
		assertEquals(0, inbox.pending.size());
		// the rest arriving late completes nothing
		for (int i = 1; i < msgs.size(); i++)
		{
			assertNull(inbox.chunk(1L, (SkateDesignChunk) msgs.get(i), 31f));
		}
	}

	@Test
	public void eachAcceptedChunkAndARepeatedOfferKeepAPictureOnItsWay()
	{
		DesignShare.Outgoing d = design(DesignPart.DECK, 5000, 4);
		List<PartyMessage> msgs = d.messages();
		assertTrue(msgs.size() >= 4);
		inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 0f);
		// a chunk at 25 s keeps it past 30 s
		inbox.chunk(1L, (SkateDesignChunk) msgs.get(1), 25f);
		inbox.expire(50f);
		assertEquals(1, inbox.pending.size());
		// the same offer again at 54 s: the chunk already here still counts, and the clock starts again
		assertTrue(inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 54f));
		inbox.expire(80f);
		assertEquals(1, inbox.pending.size());
		// a repeat of a chunk already here is not progress
		assertNull(inbox.chunk(1L, (SkateDesignChunk) msgs.get(1), 83f));
		inbox.expire(84.1f);
		assertEquals(0, inbox.pending.size());
		// offered again: every chunk but the first arriving a minute apart (each within 30 s) completes it
		inbox.offer(1L, (SkateDesignOffer) msgs.get(0), 100f);
		DesignInbox.Assembled a = null;
		float t = 100f;
		for (int i = 1; i < msgs.size(); i++)
		{
			t += 29f;
			DesignInbox.Assembled got = inbox.chunk(1L, (SkateDesignChunk) msgs.get(i), t);
			if (got != null)
			{
				a = got;
			}
		}
		assertNotNull(a);
	}

	@Test
	public void aNewerDesignForAPartReplacesTheOneOnItsWay()
	{
		DesignShare.Outgoing older = design(DesignPart.DECK, 3000, 5);
		DesignShare.Outgoing newer = design(DesignPart.DECK, 3000, 6);
		inbox.offer(1L, (SkateDesignOffer) older.messages().get(0), 0f);
		inbox.offer(1L, (SkateDesignOffer) newer.messages().get(0), 0f);
		assertEquals(1, inbox.pending.size());
		assertNotNull(feed(1L, newer, 1f));
	}

	@Test
	public void pendingPicturesAreBoundedPerMemberAndInAll()
	{
		for (int i = 0; i < 10; i++)
		{
			inbox.offer(1L, offer(i % 2 == 0 ? "deck" : "grip", String.format("%08x", i), 2, 30, 90), 0f);
		}
		// one per part: deck and grip
		assertEquals(2, inbox.pending.size());
		for (long m = 0; m < 40; m++)
		{
			inbox.offer(m + 100, offer("deck", "0123abcd", 2, 30, 90), 0f);
		}
		assertEquals(DesignInbox.MAX_PENDING, inbox.pending.size());
		inbox.forget(139L);
		assertEquals(DesignInbox.MAX_PENDING - 1, inbox.pending.size());
		inbox.clear();
		assertEquals(0, inbox.pending.size());
	}

	@Test
	public void aLongerOfferedHashMustMatchToo()
	{
		DesignShare.Outgoing d = design(DesignPart.WHEELS, 1200, 8);
		List<PartyMessage> msgs = d.messages();
		SkateDesignOffer o = (SkateDesignOffer) msgs.get(0);
		byte[] png = Base64.getDecoder().decode(String.join("", d.chunks));
		o.hash = DesignShare.fullHash(png);
		inbox.offer(1L, o, 0f);
		DesignInbox.Assembled a = null;
		for (int i = 1; i < msgs.size(); i++)
		{
			SkateDesignChunk c = (SkateDesignChunk) msgs.get(i);
			c.hash = o.hash;
			DesignInbox.Assembled got = inbox.chunk(1L, c, 0f);
			a = got != null ? got : a;
		}
		assertNotNull(a);
		// ghost updates name it by its first 8 digits
		assertEquals(d.hash, a.hash);
		assertArrayEquals(png, a.png);
	}

	private static String repeat(char c, int n)
	{
		StringBuilder sb = new StringBuilder(n);
		for (int i = 0; i < n; i++)
		{
			sb.append(c);
		}
		return sb.toString();
	}
}
