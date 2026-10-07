package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** The JSON RuneLite's party client sends for the duel messages, built as its WebsocketGsonFactory builds it. */
public class DuelWireFormatTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateDuelChallenge.class)
			.registerSubtype(SkateDuelReply.class)
			.registerSubtype(SkateDuelHit.class)
			.registerSubtype(SkateDuelEnd.class))
		.create();

	@Test
	public void aHitRoundTripsSmallWithoutTheMemberId()
	{
		SkateDuelHit m = new SkateDuelHit();
		m.duelId = Long.MIN_VALUE + 1;
		m.seq = 12;
		m.targetMemberId = 9_876_543_210L;
		m.damage = 25;
		m.comboValue = 1_234_567;
		m.trickCount = 14;
		m.selfInflicted = false;
		m.setMemberId(42L);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(json, json.length() <= 200);
		assertFalse(json, json.contains("memberId\""));
		SkateDuelHit back = (SkateDuelHit) GSON.fromJson(json, WebsocketMessage.class);
		assertEquals(m.duelId, back.duelId);
		assertEquals(12, back.seq);
		assertEquals(m.targetMemberId, back.targetMemberId);
		assertEquals(25, back.damage);
		assertEquals(1_234_567, back.comboValue);
		assertEquals(14, back.trickCount);
		assertFalse(back.selfInflicted);
	}

	@Test
	public void theOtherMessagesRoundTrip()
	{
		SkateDuelChallenge c = new SkateDuelChallenge();
		c.duelId = 5L;
		c.seq = 1;
		c.targetMemberId = 7L;
		SkateDuelChallenge c2 = (SkateDuelChallenge) GSON.fromJson(GSON.toJson(c, WebsocketMessage.class),
			WebsocketMessage.class);
		assertEquals(5L, c2.duelId);
		assertEquals(7L, c2.targetMemberId);

		SkateDuelReply r = new SkateDuelReply();
		r.duelId = 5L;
		r.seq = 1;
		r.accepted = true;
		SkateDuelReply r2 = (SkateDuelReply) GSON.fromJson(GSON.toJson(r, WebsocketMessage.class),
			WebsocketMessage.class);
		assertTrue(r2.accepted);

		SkateDuelEnd e = new SkateDuelEnd();
		e.duelId = 5L;
		e.seq = 4;
		e.reason = "KO";
		SkateDuelEnd e2 = (SkateDuelEnd) GSON.fromJson(GSON.toJson(e, WebsocketMessage.class), WebsocketMessage.class);
		assertEquals("KO", e2.reason);
		assertEquals(4, e2.seq);
	}

	@Test
	public void unknownFieldsFromANewerVersionAreIgnored()
	{
		String json = "{\"type\":\"SkateDuelHit\",\"duelId\":3,\"seq\":2,\"targetMemberId\":8,\"damage\":4,"
			+ "\"selfInflicted\":true,\"zz\":[1]}";
		SkateDuelHit m = (SkateDuelHit) GSON.fromJson(json, WebsocketMessage.class);
		assertEquals(4, m.damage);
		assertTrue(m.selfInflicted);
	}
}
