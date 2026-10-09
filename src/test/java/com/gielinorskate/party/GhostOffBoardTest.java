package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.FootEvent;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** On-foot ghosts: the new fields, their validation and their backward compatibility. */
public class GhostOffBoardTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class))
		.create();

	private static GhostState walker(BoardState board, float bx, float by, float bh, float bd)
	{
		return GhostFeed.onFoot(330, 2, 409_600f, 411_200f, 40f, 1.5f, 120f, -150f, 0f, false, 0.4f, board, bx, by,
			bh, bd);
	}

	@Test
	public void onTheBoardNothingNewGoesOnTheWire()
	{
		GhostState f = GhostFeed.frame(330, 2, 1f, 2f, 3f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertFalse(json, json.contains("\"ob\""));
		assertFalse(json, json.contains("\"bx\""));
		assertNull(GhostCodec.decode(m).offBoard);
	}

	@Test
	public void aCarryingWalkerRoundTrips()
	{
		SkateGhostUpdate m = GhostCodec.encode(walker(BoardState.CARRIED, 0f, 0f, 0f, 0f), 0, null);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(json, json.contains("\"ob\":\"CARRIED\""));
		// a carried board has no position of its own
		assertFalse(json, json.contains("\"bx\""));
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertEquals(BoardState.CARRIED, s.offBoard);
		assertEquals(SkaterState.ROLLING, s.state);
		assertEquals(409_600f, s.x, 0.5f);
		assertEquals(120f, s.vx, 0.5f);
		assertEquals(1.5f, s.heading, 0.001f);
		assertEquals(0.4f, s.turnRate, 0.001f);
	}

	@Test
	public void aDroppedBoardRoundTripsWithinRounding()
	{
		SkateGhostUpdate m = GhostCodec.encode(walker(BoardState.DROPPED, 409_700.4f, 411_000.6f, 32.2f, -2.5f), 0,
			null);
		String json = GSON.toJson(m, WebsocketMessage.class);
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertEquals(BoardState.DROPPED, s.offBoard);
		assertEquals(409_700.4f, s.boardX, 0.5f);
		assertEquals(411_000.6f, s.boardY, 0.5f);
		assertEquals(32.2f, s.boardH, 0.5f);
		assertEquals(-2.5f, s.boardHeading, 0.001f);
	}

	@Test
	public void anOnFootUpdateStaysSmall()
	{
		GhostState f = GhostFeed.onFoot(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -384f, -384f, -1820f,
			true, -TurnRateMeter.MAX_RATE, BoardState.DROPPED, 1_409_000f, 1_411_999f, -1237f, -3.14159f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0x3ff, null);
		m.seq = 1_234_567_890;
		m.dk = "PARTYHAT_PURPLE";
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(json, json.length() <= 280);
	}

	@Test
	public void anOldMessageIsOnTheBoard()
	{
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"x\":5,\"st\":\"MANUAL\",\"hold\":\"MANUAL\",\"seq\":3}";
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertNull(s.offBoard);
		assertEquals(SkaterState.MANUAL, s.state);
		assertEquals(Trick.MANUAL, s.hold);
	}

	@Test
	public void unknownOrBrokenBoardFieldsAreSafe()
	{
		// an unknown board state (a later version): on the board, as an old message
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"ob\":\"FLYING\",\"seq\":3}";
		assertNull(GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class)).offBoard);
		// dropped with no position: drawn carried
		json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"ob\":\"DROPPED\",\"bx\":5,\"seq\":3}";
		assertEquals(BoardState.CARRIED,
			GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class)).offBoard);
		// a dropped board sent without its heading lies with its nose north
		json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"ob\":\"DROPPED\",\"bx\":5,\"by\":6,\"bh\":7,\"seq\":3}";
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertEquals(BoardState.DROPPED, s.offBoard);
		assertEquals(0f, s.boardHeading, 0f);
	}

	@Test
	public void aDroppedBoardImplausiblyFarAwayIsDrawnCarried()
	{
		float far = GhostCodec.MAX_BOARD_DISTANCE + 10f;
		GhostState s = GhostCodec.decode(GhostCodec.encode(walker(BoardState.DROPPED, 409_600f + far, 411_200f, 40f,
			0f), 0, null));
		assertEquals(BoardState.CARRIED, s.offBoard);
		s = GhostCodec.decode(GhostCodec.encode(walker(BoardState.DROPPED, 409_600f, 411_200f,
			40f + GhostCodec.MAX_BOARD_DISTANCE + 10f, 0f), 0, null));
		assertEquals(BoardState.CARRIED, s.offBoard);
		s = GhostCodec.decode(GhostCodec.encode(walker(BoardState.DROPPED, 409_600f + 1000f, 411_200f, 40f, 0f), 0,
			null));
		assertEquals(BoardState.DROPPED, s.offBoard);
	}

	@Test
	public void aWalkerNeverGrindsFlipsOrBails()
	{
		SkateGhostUpdate m = GhostCodec.encode(walker(BoardState.CARRIED, 0f, 0f, 0f, 0f), 0, null);
		m.st = "GRINDING";
		m.hold = "BOARDSLIDE";
		m.tr = "KICKFLIP";
		m.ft = 100;
		m.bf = 3000;
		m.bw = 6000;
		GhostState s = GhostCodec.decode(m);
		assertEquals(SkaterState.ROLLING, s.state);
		assertNull(s.hold);
		assertNull(s.trick);
		assertEquals(0f, s.bodyFlip, 0f);
		assertEquals(0f, s.bodyFlipRate, 0f);
		m.st = "AIRBORNE";
		assertEquals(SkaterState.AIRBORNE, GhostCodec.decode(m).state);
	}

	@Test
	public void boardFieldsCountAsAStateChange()
	{
		SkateGhostUpdate a = GhostCodec.encode(walker(BoardState.DROPPED, 409_700f, 411_000f, 32f, 0f), 0, null);
		SkateGhostUpdate b = GhostCodec.encode(walker(BoardState.DROPPED, 409_700f, 411_000f, 32f, 0f), 0, null);
		assertTrue(a.sameState(b));
		b = GhostCodec.encode(walker(BoardState.DROPPED, 409_800f, 411_000f, 32f, 0f), 0, null);
		assertFalse(a.sameState(b));
		b = GhostCodec.encode(walker(BoardState.CARRIED, 0f, 0f, 0f, 0f), 0, null);
		assertFalse(a.sameState(b));
	}

	@Test
	public void footEventsAndSwapsGoOutAsEvents()
	{
		assertEquals(GhostCodec.EV_POP, GhostCodec.footEventBits(Collections.singletonList(FootEvent.JUMP)));
		assertEquals(GhostCodec.EV_LAND, GhostCodec.footEventBits(Collections.singletonList(FootEvent.LAND)));
		assertEquals(0, GhostCodec.footEventBits(Collections.singletonList(FootEvent.FALL)));
		assertEquals(GhostCodec.EV_POP | GhostCodec.EV_LAND,
			GhostCodec.footEventBits(Arrays.asList(FootEvent.JUMP, FootEvent.LAND)));

		assertEquals(0, GhostCodec.swapBits(null, null));
		assertEquals(GhostCodec.EV_BOARD_SWAP, GhostCodec.swapBits(null, BoardState.CARRIED));
		assertEquals(GhostCodec.EV_BOARD_SWAP, GhostCodec.swapBits(BoardState.CARRIED, BoardState.DROPPED));
		assertEquals(GhostCodec.EV_BOARD_SWAP, GhostCodec.swapBits(BoardState.DROPPED, null));
		assertEquals(0, GhostCodec.swapBits(BoardState.DROPPED, BoardState.DROPPED));
	}

	@Test
	public void newEventBitsDoNotCollide()
	{
		int old = GhostCodec.EV_POP | GhostCodec.EV_LAND | GhostCodec.EV_BAIL | GhostCodec.EV_RESET
			| GhostCodec.EV_GRIND_LOCK | GhostCodec.EV_GRIND_EXIT | GhostCodec.EV_TRICK | GhostCodec.EV_BODY_FLIP;
		assertEquals(0, old & GhostCodec.EV_BOARD_SWAP);
		assertEquals(0, old & GhostCodec.EV_GAIT);
		assertEquals(0, GhostCodec.EV_BOARD_SWAP & GhostCodec.EV_GAIT);
	}
}
