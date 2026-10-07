package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

public class DesignShareTest
{
	/** A fake picture: random bytes (only the inbox's checks read them, never ImageIO). */
	static SharedDesignImage.Encoded picture(DesignPart part, int w, int h, int bytes, long seed)
	{
		byte[] png = new byte[bytes];
		new Random(seed).nextBytes(png);
		return new SharedDesignImage.Encoded(part, w, h, png);
	}

	@Test
	public void chunksAreAtMostAThousandCharactersAndJoinBack()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8192; i++)
		{
			sb.append((char) ('A' + i % 26));
		}
		String all = sb.toString();
		List<String> chunks = DesignShare.chunks(all);
		assertEquals(9, chunks.size());
		for (String c : chunks)
		{
			assertTrue(c.length() <= DesignShare.CHUNK_CHARS);
		}
		assertEquals(all, String.join("", chunks));
		assertEquals(1, DesignShare.chunks("abc").size());
		assertEquals(0, DesignShare.chunks("").size());
	}

	@Test
	public void theHashIsEightLowercaseHexOfTheBytesSha256()
	{
		byte[] data = "RuneSkate".getBytes();
		String h = DesignShare.hash(data);
		assertEquals(8, h.length());
		assertTrue(DesignShare.validHash(h));
		assertEquals(h, DesignShare.hash(data.clone()));
		assertTrue(DesignShare.fullHash(data).startsWith(h));
		assertEquals(16, DesignShare.fullHash(data).length());
		assertFalse(h.equals(DesignShare.hash("RuneSkatf".getBytes())));
		// it fits a ghost update's reference
		assertEquals(h, GhostCodec.customHash(GhostCodec.CUSTOM_REF + h));
	}

	@Test
	public void validHashesAreEightToSixteenLowercaseHex()
	{
		assertTrue(DesignShare.validHash("0123abcd"));
		assertTrue(DesignShare.validHash("0123456789abcdef"));
		assertFalse(DesignShare.validHash("0123abc"));
		assertFalse(DesignShare.validHash("0123456789abcdef0"));
		assertFalse(DesignShare.validHash("0123ABCD"));
		assertFalse(DesignShare.validHash("0123abcz"));
		assertFalse(DesignShare.validHash(null));
	}

	@Test
	public void anOutgoingDesignIsAnOfferThenItsChunks()
	{
		SharedDesignImage.Encoded pic = picture(DesignPart.DECK, 35, 96, 5000, 1);
		DesignShare.Outgoing out = DesignShare.outgoing("CUSTOM_0A1B2C3D", "My\u0007 deck", pic);
		assertNotNull(out);
		assertEquals(DesignShare.hash(pic.png), out.hash);
		assertEquals("My deck", out.name);
		List<PartyMessage> msgs = out.messages();
		SkateDesignOffer offer = (SkateDesignOffer) msgs.get(0);
		assertEquals("deck", offer.part);
		assertEquals(out.hash, offer.hash);
		assertEquals(msgs.size() - 1, offer.totalChunks);
		assertEquals(35, offer.width);
		assertEquals(96, offer.height);
		StringBuilder joined = new StringBuilder();
		for (int i = 1; i < msgs.size(); i++)
		{
			SkateDesignChunk c = (SkateDesignChunk) msgs.get(i);
			assertEquals(i - 1, c.index);
			assertEquals(out.hash, c.hash);
			joined.append(c.data);
		}
		assertEquals(pic.base64, joined.toString());
		// hash round trip: the bytes back from the chunks hash to the offer's hash
		assertEquals(offer.hash, DesignShare.hash(Base64.getDecoder().decode(joined.toString())));
	}

	@Test
	public void pictureOverTheLimitsIsNotShared()
	{
		assertNull(DesignShare.outgoing("CUSTOM_0A1B2C3D", "x", picture(DesignPart.DECK, 35, 96, 6200, 1)));
		assertNull(DesignShare.outgoing("CUSTOM_0A1B2C3D", "x", picture(DesignPart.WHEELS, 41, 40, 100, 1)));
		assertNull(DesignShare.outgoing("CUSTOM_0A1B2C3D", "x", null));
	}

	@Test
	public void namesAreCleanedAndCut()
	{
		assertEquals("", DesignShare.cleanName(null));
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 100; i++)
		{
			longName.append('n');
		}
		assertEquals(24, DesignShare.cleanName(longName.toString()).length());
		assertEquals("ab", DesignShare.cleanName("a\nb"));
		assertEquals("colredHi", DesignShare.cleanName("<col=red>Hi"));
		assertEquals("ltgtimg1 Kev's deck", DesignShare.cleanName("<lt><gt><img=1> Kev's deck"));
		assertEquals("Rune-deck_2", DesignShare.cleanName("Rune-deck_2é"));
	}
}
