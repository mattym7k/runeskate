package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** The optional body fields: the pop charge's crouch and the knockdown (tumble, lie, get-up). */
public class GhostBodyWireTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class))
		.create();

	private static GhostFrame rolling(float charge)
	{
		return new GhostFrame(330, 0, 1000f, 2000f, 0f, 0f, 0f, 800f, 0f, SkaterState.ROLLING, null, null, 0f)
			.withCharge(charge);
	}

	private static SkateGhostUpdate roundTrip(SkateGhostUpdate m)
	{
		return (SkateGhostUpdate) GSON.fromJson(GSON.toJson(m, WebsocketMessage.class), WebsocketMessage.class);
	}

	@Test
	public void theChargeGoesInNinthsAndOnlyWhileCrouching()
	{
		assertNull(GhostCodec.encode(rolling(0f), 0, null).cr);
		assertNull(GhostCodec.encode(rolling(0.03f), 0, null).cr);
		assertEquals(Integer.valueOf(9), GhostCodec.encode(rolling(1f), 0, null).cr);
		assertEquals(Integer.valueOf(5), GhostCodec.encode(rolling(0.5f), 0, null).cr);
		GhostState s = GhostCodec.decode(roundTrip(GhostCodec.encode(rolling(0.5f), 0, null)));
		assertEquals(5f / 9f, s.charge, 1e-6f);
		assertNull(s.knockStage);
		// no charge in the air: the frame says so whatever the physics held
		GhostFrame air = new GhostFrame(330, 0, 0f, 0f, 100f, 0f, 0f, 0f, 300f, SkaterState.AIRBORNE, null, null, 0f)
			.withCharge(1f);
		assertNull(GhostCodec.encode(air, 0, null).cr);
	}

	@Test
	public void aChangedChargeIsAStateChange()
	{
		SkateGhostUpdate a = GhostCodec.encode(rolling(0f), 0, null);
		SkateGhostUpdate b = GhostCodec.encode(rolling(1f), 0, null);
		assertFalse(a.sameState(b));
		assertTrue(b.sameState(GhostCodec.encode(rolling(1f), 0, null)));
	}

	@Test
	public void olderVersionsMessagesHaveNoChargeAndNoKnockdown()
	{
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"x\":5,\"st\":\"BAILED\",\"seq\":3}";
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertEquals(0f, s.charge, 0f);
		assertNull(s.knockStage);
		assertSame(SkaterState.BAILED, s.state);
	}

	private static final float HALF_PI = (float) (Math.PI / 2);

	private static GhostFrame knocked(KnockdownPose.Stage stage, float lie, boolean airborne)
	{
		return GhostFrame.knockdown(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -1400f, 1400f, airborne,
			stage, lie, 1_409_000f, 1_411_999f, -1237f, -3.14159f);
	}

	@Test
	public void aKnockdownRoundTrips()
	{
		SkateGhostUpdate m = GhostCodec.encode(knocked(KnockdownPose.Stage.GET_UP, 3 * HALF_PI, false), 0, null);
		assertEquals(Integer.valueOf(3 + 10 * 5), m.kd);
		GhostState s = GhostCodec.decode(roundTrip(m));
		assertSame(KnockdownPose.Stage.GET_UP, s.knockStage);
		assertEquals(3 * HALF_PI, s.knockLie, 1e-5f);
		assertSame(SkaterState.BAILED, s.state);
		assertSame(BoardState.DROPPED, s.offBoard);
		assertEquals(1_409_000f, s.boardX, 0f);
		assertEquals(-1400f, s.vx, 0f);
		// thrown off: in the air the predictor flies the body
		GhostState air = GhostCodec.decode(roundTrip(GhostCodec.encode(knocked(KnockdownPose.Stage.AIR, HALF_PI,
			true), 0, null)));
		assertSame(KnockdownPose.Stage.AIR, air.knockStage);
		assertSame(SkaterState.AIRBORNE, air.state);
		assertEquals(HALF_PI, air.knockLie, 1e-5f);
		// every lying angle the tumble plans or settles at: odd quarter turns, onto the back off a wall too
		for (int k = -4; k <= 5; k++)
		{
			float lie = (2 * k + 1) * HALF_PI;
			GhostState back = GhostCodec.decode(GhostCodec.encode(knocked(KnockdownPose.Stage.LIE, lie, false), 0,
				null));
			assertEquals(lie, back.knockLie, 1e-4f);
		}
	}

	@Test
	public void junkKnockdownCodesAreIgnored()
	{
		SkateGhostUpdate m = GhostCodec.encode(knocked(KnockdownPose.Stage.LIE, HALF_PI, false), 0, null);
		m.kd = 7;
		assertNull(GhostCodec.decode(m).knockStage);
		m.kd = 40;
		assertNull(GhostCodec.decode(m).knockStage);
		m.kd = 100;
		assertNull(GhostCodec.decode(m).knockStage);
		m.kd = -5;
		assertNull(GhostCodec.decode(m).knockStage);
		// a knockdown needs its board's place: without it the code means nothing
		SkateGhostUpdate onBoard = GhostCodec.encode(rolling(0f), 0, null);
		onBoard.kd = 42;
		assertNull(GhostCodec.decode(onBoard).knockStage);
	}

	@Test
	public void toOlderVersionsAKnockdownIsAWalkerWithItsBoardDropped()
	{
		// they decode ob / bx... as on foot and ignore kd: the body stands where it lies, the board where it rests
		SkateGhostUpdate m = GhostCodec.encode(knocked(KnockdownPose.Stage.LIE, HALF_PI, false), 0, null);
		assertEquals("DROPPED", m.ob);
		assertTrue(m.bx != null && m.by != null && m.bh != null && m.bd != null);
		assertFalse(GhostCodec.mayCarryLook(m));
	}

	private static String longest(DesignPart part)
	{
		String w = GhostCodec.CUSTOM_REF + "ffffffff";
		for (BoardDesign d : BoardDesigns.bundled().of(part))
		{
			if (d.wireName().length() > w.length())
			{
				w = d.wireName();
			}
		}
		return w;
	}

	@Test
	public void theBiggestUpdatesWithTheBodyFieldsStayWithinTheBound()
	{
		// a grind with a full charge and every event bit: the designs wait while crouching (with them it was 286)
		GhostFrame grind = new GhostFrame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f,
			-1820f, SkaterState.GRINDING, Trick.BOARDSLIDE, null, 0f, -TurnRateMeter.MAX_RATE, -4 * (float) Math.PI,
			-11.424f).withCharge(1f);
		SkateGhostUpdate g = GhostCodec.encode(grind, 0x7ff, null);
		g.seq = 1_234_567_890;
		g.dk = longest(DesignPart.DECK);
		g.dv = GhostHub.DUEL_VERSION;
		assertFalse(GhostCodec.mayCarryLook(g));
		String gj = GSON.toJson(g, WebsocketMessage.class);
		assertTrue(gj.length() + " " + gj, gj.length() <= 284);

		// the busiest knockdown: every number at its longest, the longest deck, the duel version (with the vertical
		// speed, tumble angle and rate and a progress it measured 298)
		SkateGhostUpdate k = GhostCodec.encode(GhostFrame.knockdown(330, 2, 1_409_664f, 1_411_200f, -1237f,
			-3.14159f, -2600f, -2600f, true, KnockdownPose.Stage.GET_UP, -7 * HALF_PI, 1_409_000f, 1_411_999f,
			-1237f, -3.14159f), 0x7ff, null);
		k.seq = 1_234_567_890;
		k.dk = longest(DesignPart.DECK);
		k.dv = GhostHub.DUEL_VERSION;
		String kj = GSON.toJson(k, WebsocketMessage.class);
		assertTrue(kj.length() + " " + kj, kj.length() <= 284);
	}
}
