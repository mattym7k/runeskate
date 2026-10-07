package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** The duel endings on the ghost timeline: their bits and times on the wire, the size bound, playback. */
public class GhostDuelEndingTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class))
		.create();
	private static final GhostPredictor.Ground FLAT = (x, y) -> 0f;

	@Test
	public void theEndingBitsAreNewAndCarryATime()
	{
		int ending = GhostCodec.EV_TANTRUM | GhostCodec.EV_CELEBRATE;
		int older = (1 << 12) - 1;
		assertEquals(0, ending & older);
		assertTrue(Integer.numberOfTrailingZeros(GhostCodec.EV_CELEBRATE) < GhostTrajectory.EVENT_BITS);
		// they go out at once, not with the next snapshot
		assertEquals(0, ending & GhostCodec.PASSIVE_EVENTS);
	}

	/** A walker standing with the board in hand at sender time {@code t} (as the tantrum shares it). */
	private static GhostFrame standing()
	{
		return GhostFrame.onFoot(330, 0, 1_409_664f, 1_411_200f, 0f, 0.7f, 0f, 0f, 0f, false, 0f,
			BoardState.CARRIED, 0f, 0f, 0f, 0f);
	}

	@Test
	public void aTantrumUpdateRoundTripsWithItsTimeAndStaysInTheBound()
	{
		GhostTrail trail = new GhostTrail();
		for (float t = 0f; t <= 1f; t += 0.02f)
		{
			trail.record(standing(), t);
		}
		trail.noteEvents(GhostCodec.EV_TANTRUM | GhostCodec.EV_BOARD_SWAP, 0.96f);
		SkateGhostUpdate m = GhostCodec.encode(standing(), GhostCodec.EV_TANTRUM | GhostCodec.EV_BOARD_SWAP, null);
		m.seq = 1_234_567_890;
		m.dk = "C:0a1b2c3d";
		m.dv = GhostHub.DUEL_VERSION;
		trail.fit(m, 1f);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertEquals(json.length(), GhostWire.jsonLength(m));
		assertTrue(json.length() + " " + json, json.length() <= GhostWire.MAX_UPDATE_CHARS);
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class);
		assertEquals(m.ev, back.ev);
		GhostTrajectory tj = GhostTrajectory.decode(back.tj, back.ev, back.x, back.y, back.h, back.hd);
		assertNotNull(tj);
		assertEquals(0.04f, tj.eventAgo[12], 0.006f);
		assertTrue(Float.isNaN(tj.eventAgo[13]));
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
	public void theBusiestUpdatesWithTheEndingBitsStayWithinTheBound()
	{
		// a celebration's bit still waiting for a token when the winner pops a flip: every event bit, the longest
		// trick names and deck, the duel version, a fast skater's positions to fit
		int every = (1 << 14) - 1;
		GhostFrame busy = new GhostFrame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.AIRBORNE, Trick.BOARDSLIDE, Trick.NOLLIE_INWARD_HEELFLIP, 0.5f, -TurnRateMeter.MAX_RATE,
			-4 * (float) Math.PI, -11.424f);
		SkateGhostUpdate m = GhostCodec.encode(busy, every, null);
		m.seq = 1_234_567_890;
		m.dk = longest(DesignPart.DECK);
		m.dv = GhostHub.DUEL_VERSION;
		String bare = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(bare.length() + " " + bare, bare.length() <= GhostWire.MAX_UPDATE_CHARS);
		GhostTrail trail = new GhostTrail();
		for (float t = 0f; t <= 1f; t += 0.02f)
		{
			trail.record(new GhostFrame(330, 2, 1_409_664f - 2600f * (1f - t), 1_411_200f - 2600f * (1f - t),
				-1237f, -3.14159f + 10f * (1f - t), 0f, 0f, 0f, SkaterState.AIRBORNE, null, null, 0f), t);
		}
		trail.noteEvents(every, 0.1f);
		trail.fit(m, 1f);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertEquals(json.length(), GhostWire.jsonLength(m));
		assertTrue(json.length() + " " + json, json.length() <= GhostWire.MAX_UPDATE_CHARS);
	}

	@Test
	public void aPlayedBackGhostThrowsTheTantrumOnceWhenItsTimeComes()
	{
		GhostPredictor g = new GhostPredictor();
		GhostTrail trail = new GhostTrail();
		float latency = 0.1f;
		int seq = 1;
		int seenAt = -1;
		float ageWhenSeen = Float.NaN;
		float frame = 0.02f;
		float nextSend = 0f;
		SkateGhostUpdate inFlight = null;
		float arrives = Float.NaN;
		for (int i = 0; i < 200; i++)
		{
			float t = i * frame;
			trail.record(standing(), t);
			int ev = 0;
			if (i == 60)
			{
				ev = GhostCodec.EV_TANTRUM;
				trail.noteEvents(ev, t);
				nextSend = t;
			}
			if (t >= nextSend && inFlight == null)
			{
				SkateGhostUpdate m = GhostCodec.encode(standing(), ev, null);
				m.seq = seq++;
				trail.fit(m, t);
				trail.sent(t);
				inFlight = m;
				arrives = t + latency;
				nextSend = t + 0.6f;
			}
			if (inFlight != null && t >= arrives)
			{
				SkateGhostUpdate m = inFlight;
				inFlight = null;
				g.accept(GhostCodec.decode(m), GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd), t, FLAT);
			}
			if (g.latest() != null)
			{
				g.pose(t, FLAT);
			}
			if (g.tantrumCount() == 1 && seenAt < 0)
			{
				seenAt = i;
				ageWhenSeen = g.tantrumAge(t);
			}
		}
		assertEquals(1, g.tantrumCount());
		assertEquals(0, g.celebrateCount());
		// played a little behind the member, when the playback reached it, from its start
		assertTrue(seenAt > 60);
		assertTrue(ageWhenSeen >= 0f && ageWhenSeen < 0.1f);
		assertTrue(Float.isNaN(g.celebrateAge(1f)));
	}

	@Test
	public void aGhostStampsInTheWindUpAndCheersOrJumpsForJoy()
	{
		float nan = Float.NaN;
		assertEquals(null, GhostAnim.ending(nan, nan, true));
		assertEquals(GhostAnim.NONE, GhostAnim.ending(0.1f, nan, true));
		assertEquals(GhostAnim.STOMP, GhostAnim.ending(0.5f, nan, true));
		assertEquals(GhostAnim.NONE, GhostAnim.ending(com.gielinorskate.render.TantrumSequence.SNAP_AT, nan, true));
		assertEquals(null, GhostAnim.ending(com.gielinorskate.render.TantrumSequence.DURATION, nan, true));
		assertEquals(GhostAnim.CHEER, GhostAnim.ending(nan, 0.2f, false));
		assertEquals(GhostAnim.JOY, GhostAnim.ending(nan, 0.2f, true));
		assertEquals(null, GhostAnim.ending(nan, com.gielinorskate.render.CelebrationSequence.DURATION, false));
		// the tantrum wins over a cheer
		assertEquals(GhostAnim.STOMP, GhostAnim.ending(0.5f, 0.2f, true));
		assertEquals(net.runelite.api.gameval.AnimationID.EMOTE_STAMPFEET, GhostAnim.STOMP.id(-1, -1));
		assertEquals(net.runelite.api.gameval.AnimationID.EMOTE_CHEER, GhostAnim.CHEER.id(-1, -1));
		assertEquals(net.runelite.api.gameval.AnimationID.EMOTE_JUMP_WITH_JOY, GhostAnim.JOY.id(-1, -1));
		assertTrue(!GhostAnim.STOMP.loops() && !GhostAnim.CHEER.loops() && !GhostAnim.JOY.loops());
		assertEquals(0.5f, GhostAnim.endingProgress(GhostAnim.CHEER, nan,
			com.gielinorskate.render.CelebrationSequence.DURATION / 2), 1e-6f);
	}

	@Test
	public void anOlderSendersUpdateWithoutATimelineStillCelebrates()
	{
		GhostPredictor g = new GhostPredictor();
		SkateGhostUpdate m = GhostCodec.encode(new GhostFrame(330, 0, 0f, 0f, 0f, 0f, 300f, 0f, 0f,
			SkaterState.ROLLING, null, null, 0f), GhostCodec.EV_CELEBRATE, null);
		m.seq = 5;
		g.accept(GhostCodec.decode(m), 2f, FLAT);
		assertEquals(1, g.celebrateCount());
		assertEquals(0.5f, g.celebrateAge(2.5f), 1e-6f);
		assertEquals(0, g.tantrumCount());
	}
}
