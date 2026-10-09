package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Random;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

public class GhostWireTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class))
		.create();

	@Test
	public void varintsRoundTripAndSmallOnesTakeOneCharacter()
	{
		StringBuilder sb = new StringBuilder();
		int[] values = {0, 1, -1, 15, -16, 16, 511, -512, 512, 100_000, -100_000, Integer.MAX_VALUE / 4};
		for (int v : values)
		{
			GhostWire.putSigned(sb, v);
		}
		GhostWire.putUnsigned(sb, 31);
		GhostWire.putUnsigned(sb, 32);
		GhostWire.putFixed(sb, 262_143, 3);
		GhostWire.Reader r = new GhostWire.Reader(sb.toString());
		for (int v : values)
		{
			assertEquals(v, r.signed());
		}
		assertEquals(31, r.unsigned());
		assertEquals(32, r.unsigned());
		assertEquals(262_143, r.fixed(3));
		assertTrue(r.ok());
		// nothing is left: reading on fails
		r.fixed(1);
		assertFalse(r.ok());

		StringBuilder one = new StringBuilder();
		GhostWire.putSigned(one, -16);
		GhostWire.putSigned(one, 15);
		GhostWire.putUnsigned(one, 31);
		assertEquals(3, one.length());
	}

	@Test
	public void junkNeverThrows()
	{
		for (String junk : new String[]{"", "!", "\"", "______________", "ZZZZ", "é"})
		{
			GhostWire.Reader r = new GhostWire.Reader(junk);
			r.signed();
			r.unsigned();
			r.fixed(3);
			assertFalse(junk, r.ok());
		}
	}

	@Test
	public void theAlphabetIsNeverEscapedByGson()
	{
		String json = GSON.toJson(GhostWire.ALPHABET);
		assertEquals(GhostWire.ALPHABET.length() + 2, json.length());
	}

	@Test
	public void jsonLengthMatchesGsonExactly()
	{
		Random rnd = new Random(7);
		Trick[] tricks = Trick.values();
		SkaterState[] states = SkaterState.values();
		for (int i = 0; i < 2000; i++)
		{
			SkateGhostUpdate m = new SkateGhostUpdate();
			m.w = rnd.nextInt(600);
			m.p = rnd.nextInt(4);
			m.x = rnd.nextInt() / (1 + rnd.nextInt(1000));
			m.y = rnd.nextInt() / (1 + rnd.nextInt(1000));
			m.h = rnd.nextInt(4000) - 2000;
			m.hd = rnd.nextInt(6284) - 3142;
			m.tw = rnd.nextInt(20001) - 10000;
			m.vx = rnd.nextInt(5201) - 2600;
			m.vy = rnd.nextInt(5201) - 2600;
			m.vh = rnd.nextInt(5201) - 2600;
			m.st = rnd.nextBoolean() ? states[rnd.nextInt(states.length)].name() : null;
			m.hold = rnd.nextBoolean() ? tricks[rnd.nextInt(tricks.length)].name() : null;
			m.bf = rnd.nextInt(30000) - 15000;
			m.bw = rnd.nextInt(30000) - 15000;
			m.ev = rnd.nextInt(4096);
			m.tr = rnd.nextBoolean() ? tricks[rnd.nextInt(tricks.length)].name() : null;
			m.ft = rnd.nextInt(2000);
			m.seq = rnd.nextInt(Integer.MAX_VALUE);
			m.dk = rnd.nextBoolean() ? "C:0a1b2c3d" : null;
			m.gw = rnd.nextBoolean() ? "GRIP_X.WHEELS_Y" : null;
			m.ob = rnd.nextBoolean() ? BoardState.DROPPED.name() : null;
			m.bx = rnd.nextBoolean() ? rnd.nextInt() : null;
			m.by = rnd.nextBoolean() ? -rnd.nextInt(1000) : null;
			m.bh = rnd.nextBoolean() ? 0 : null;
			m.bd = rnd.nextBoolean() ? rnd.nextInt(6000) : null;
			m.cr = rnd.nextBoolean() ? rnd.nextInt(10) : null;
			m.kd = rnd.nextBoolean() ? rnd.nextInt(99) : null;
			m.dv = rnd.nextBoolean() ? 1 : null;
			m.tj = rnd.nextBoolean() ? GhostWire.ALPHABET.substring(rnd.nextInt(64)) : null;
			String json = GSON.toJson(m, WebsocketMessage.class);
			assertEquals(json, json.length(), GhostWire.jsonLength(m));
		}
	}

	@Test
	public void htmlCharactersCountAsGsonEscapesThem()
	{
		SkateGhostUpdate m = new SkateGhostUpdate();
		m.dk = "<a&b='c'>\"\\";
		assertEquals(GSON.toJson(m, WebsocketMessage.class).length(), GhostWire.jsonLength(m));
	}
}
