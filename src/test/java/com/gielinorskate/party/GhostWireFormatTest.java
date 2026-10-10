package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Test;

/** The JSON RuneLite's party client sends, built the way its WebsocketGsonFactory builds it. */
public class GhostWireFormatTest
{
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class))
		.create();

	@Test
	public void aBusyUpdateIsSmallAndRoundTrips()
	{
		GhostState f = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.AIRBORNE, Trick.BOARDSLIDE, Trick.NOLLIE_INWARD_HEELFLIP, 0.5f, -TurnRateMeter.MAX_RATE,
			-4 * (float) Math.PI, -11.424f);
		SkateGhostUpdate m = GhostCodec.encode(f, 255, null);
		m.seq = 1_234_567_890;
		m.setMemberId(42L);
		String json = GSON.toJson(m, WebsocketMessage.class);
		// Names instead of ordinals: the longest state (8 chars), hold (10) and trick (22) names, quoted, add
		// 8 + 10 + 22 + 3 * 2 - 3 * 2 (the ordinals' digits) = 40 to the 180-byte ordinal message: 220. The turn
		// rate ("tw":-10000,), body flip ("bf":-12566,) and its rate ("bw":-11424,) add 12 + 12 + 12 = 36, and
		// "ev" one digit: 256 measured. 280 leaves a little slack: at the 4 msg/s cap that is 1.1 KB/s.
		assertTrue(json, json.length() <= 280);
		// the member is identified by the party itself, never by the message
		assertFalse(json, json.contains("memberId"));
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class);
		assertTrue(back.sameState(m));
		assertEquals(m.seq, back.seq);
		assertEquals(m.ev, back.ev);
	}

	@Test
	public void namesNotOrdinalsGoOnTheWire()
	{
		GhostState f = GhostFeed.frame(330, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.MANUAL, Trick.NOSE_MANUAL,
			null, 0f);
		String json = GSON.toJson(GhostCodec.encode(f, 0, null), WebsocketMessage.class);
		assertTrue(json, json.contains("\"st\":\"MANUAL\""));
		assertTrue(json, json.contains("\"hold\":\"NOSE_MANUAL\""));
		// nothing flipping: no tr at all (Gson leaves nulls out)
		assertFalse(json, json.contains("\"tr\""));
	}

	@Test
	public void anOldOrdinalMessageAndUnknownFieldsAreIgnoredSafely()
	{
		// an older plugin sent ordinals; a newer one may add fields: neither may throw or pick a wrong trick
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"x\":5,\"st\":1,\"hold\":19,\"tr\":12,"
			+ "\"ft\":250,\"seq\":3,\"zz\":[1,2]}";
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class);
		GhostState s = GhostCodec.decode(back);
		assertEquals(330, s.world);
		assertEquals(5f, s.x, 0f);
		assertEquals(SkaterState.ROLLING, s.state);
		assertEquals(null, s.hold);
		assertEquals(null, s.trick);
	}

	@Test
	public void theDeckNameIsShortAndRoundTrips()
	{
		BoardDesigns designs = BoardDesigns.bundled();
		GhostState f = GhostFeed.frame(330, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		BoardLook sara = new BoardLook(designs.byId("GRIP_SARADOMIN"), designs.byId("SARADOMIN"),
			designs.byId("WHEELS_SARADOMIN"));
		m.dk = GhostCodec.deckWire(sara);
		m.gw = GhostCodec.lookWire(sara);
		String json = GSON.toJson(m, WebsocketMessage.class);
		// an old ladder deck goes under its old name, so older versions still draw it
		assertTrue(json, json.contains("\"dk\":\"SARADOMIN\""));
		assertTrue(json, json.contains("\"gw\":\"SARADOMIN.SARADOMIN\""));
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class);
		assertEquals("SARADOMIN", back.dk);
		assertTrue(back.sameState(m));
		assertEquals(sara, GhostCodec.decodeLook(back.dk, back.gw, null, designs));
		// the default deck is not sent at all
		assertEquals(null, GhostCodec.deckWire(BoardLook.defaults(designs)));
	}

	@Test
	public void unknownDesignNamesDrawTheDefaults()
	{
		BoardDesigns designs = BoardDesigns.bundled();
		assertEquals(BoardLook.defaults(designs),
			GhostCodec.decodeLook("C:0a1b2c3e", "C:0a1b2c3d.C:0a1b2c3f", null, designs));
		assertEquals(BoardLook.defaults(designs), GhostCodec.decodeLook("NO_SUCH", "NOPE.NADA", null, designs));
	}

	/** The longest name a part can have in an update: the longest wire name of the bundled designs. */
	private static String longest(DesignPart part)
	{
		String w = "";
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
	public void theBiggestUpdatesWithDesignsStayWithinTheBound()
	{
		// a flip on the wire never carries the grip and wheels (the hub holds them back): the busiest update of
		// aBusyUpdateIsSmallAndRoundTrips with the longest deck name and the duel version: the bound here is 284
		// (still about 1.1 KB/s at the 4 msg/s cap)
		GhostState busy = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.AIRBORNE, Trick.BOARDSLIDE, Trick.NOLLIE_INWARD_HEELFLIP, 0.5f, -TurnRateMeter.MAX_RATE,
			-4 * (float) Math.PI, -11.424f);
		SkateGhostUpdate m = GhostCodec.encode(busy, 255, null);
		m.seq = 1_234_567_890;
		m.dk = longest(DesignPart.DECK);
		m.dv = GhostHub.DUEL_VERSION;
		assertFalse(GhostCodec.mayCarryLook(m));
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(json.length() + " " + json, json.length() <= 284);

		// the busiest update that may carry them: no flip, a hold, every event bit, the longest names
		GhostState grind = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.GRINDING, Trick.BOARDSLIDE, null, 0f, -TurnRateMeter.MAX_RATE, -4 * (float) Math.PI,
			-11.424f);
		SkateGhostUpdate g = GhostCodec.encode(grind, 0x3ff, null);
		g.seq = 1_234_567_890;
		g.dk = longest(DesignPart.DECK);
		g.dv = GhostHub.DUEL_VERSION;
		assertTrue(GhostCodec.mayCarryLook(g));
		g.gw = longest(DesignPart.GRIP) + "." + longest(DesignPart.WHEELS);
		String gj = GSON.toJson(g, WebsocketMessage.class);
		assertTrue(gj.length() + " " + gj, gj.length() <= 284);

		// on foot carrying the board; a dropped board's place is big enough that the designs wait
		GhostState foot = GhostFeed.onFoot(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -384f, -384f, -1820f,
			true, -TurnRateMeter.MAX_RATE, BoardState.CARRIED, 0f, 0f, 0f, 0f);
		SkateGhostUpdate o = GhostCodec.encode(foot, 0x3ff, null);
		o.seq = 1_234_567_890;
		o.dk = longest(DesignPart.DECK);
		o.dv = GhostHub.DUEL_VERSION;
		assertTrue(GhostCodec.mayCarryLook(o));
		o.gw = longest(DesignPart.GRIP) + "." + longest(DesignPart.WHEELS);
		String oj = GSON.toJson(o, WebsocketMessage.class);
		assertTrue(oj.length() + " " + oj, oj.length() <= 284);
		GhostState dropped = GhostFeed.onFoot(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -384f, -384f,
			-1820f, true, -TurnRateMeter.MAX_RATE, BoardState.DROPPED, 1_409_000f, 1_411_999f, -1237f, -3.14159f);
		assertFalse(GhostCodec.mayCarryLook(GhostCodec.encode(dropped, 0, null)));
	}

	@Test
	public void olderVersionsWithoutTheDesignsSeeDefaults()
	{
		BoardDesigns designs = BoardDesigns.bundled();
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"x\":5,\"st\":\"ROLLING\",\"seq\":3}";
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class);
		assertNull(back.gw);
		assertEquals(BoardLook.defaults(designs), GhostCodec.decodeLook(back.dk, back.gw, null, designs));
		// an old version's ladder deck still shows
		assertEquals("BANDOS", GhostCodec.decodeLook("BANDOS", null, null, designs).deck.id);
	}

	@Test
	public void stopIsTiny()
	{
		String json = GSON.toJson(new SkateGhostStop(), WebsocketMessage.class);
		assertTrue(json, json.length() <= 30);
	}

	@Test
	public void anOlderMessageWithoutATurnRateCarvesNothing()
	{
		// versions before the turn rate never sent "tw": the ghost moves straight, as it did for them
		String json = "{\"type\":\"SkateGhostUpdate\",\"w\":330,\"x\":5,\"hd\":1000,\"st\":\"ROLLING\",\"seq\":3}";
		GhostState s = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		assertEquals(0f, s.turnRate, 0f);
		assertEquals(1f, s.heading, 1e-6f);
	}

	@Test
	public void theDuelCapabilityIsOptionalOnTheWire()
	{
		GhostState f = GhostFeed.frame(330, 2, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
		SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
		String without = GSON.toJson(m, WebsocketMessage.class);
		assertFalse(without, without.contains("\"dv\""));
		m.dv = GhostHub.DUEL_VERSION;
		String with = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(with, with.contains("\"dv\":1"));
		SkateGhostUpdate back = (SkateGhostUpdate) GSON.fromJson(with, WebsocketMessage.class);
		assertEquals(Integer.valueOf(1), back.dv);
		// an older version's update has none: no duel support
		SkateGhostUpdate old = (SkateGhostUpdate) GSON.fromJson(without, WebsocketMessage.class);
		assertEquals(null, old.dv);
	}

	/** A trail of a skater at full speed (2600 u/s), turning fast and falling, sampled for {@code seconds}. */
	private static GhostTrail fastTrail(float seconds)
	{
		GhostTrail trail = new GhostTrail();
		for (float t = 0f; t <= seconds; t += 0.02f)
		{
			trail.record(GhostFeed.frame(330, 2, 1_409_664f - 2600f * (seconds - t), 1_411_200f - 2600f * (seconds - t),
				-1237f + 900f * (seconds - t), -3.14159f + 10f * (seconds - t), 0f, 0f, 0f, SkaterState.AIRBORNE,
				null, null, 0f), t);
		}
		trail.noteEvents(0xfff, seconds - 0.9f);
		return trail;
	}

	@Test
	public void theTimelineOnlyGoesInTheRoomLeftUnderTheBound()
	{
		// the busiest updates of theBiggestUpdatesWithDesignsStayWithinTheBound, now with every event bit (pushes
		// included) set long before, and the fastest skater's positions to send
		GhostState busy = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.AIRBORNE, Trick.BOARDSLIDE, Trick.NOLLIE_INWARD_HEELFLIP, 0.5f, -TurnRateMeter.MAX_RATE,
			-4 * (float) Math.PI, -11.424f);
		SkateGhostUpdate m = GhostCodec.encode(busy, 0xfff, null);
		m.seq = 1_234_567_890;
		m.dk = longest(DesignPart.DECK);
		m.dv = GhostHub.DUEL_VERSION;
		fastTrail(1f).fit(m, 1f);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertEquals(json.length(), GhostWire.jsonLength(m));
		System.out.println("busiest flip: " + json.length() + " chars, timeline " + m.tj);
		assertTrue(json.length() + " " + json, json.length() <= GhostWire.MAX_UPDATE_CHARS);

		GhostState grind = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -1237f, -3.14159f, -2600f, -2600f, -1820f,
			SkaterState.GRINDING, Trick.BOARDSLIDE, null, 0f, -TurnRateMeter.MAX_RATE, -4 * (float) Math.PI,
			-11.424f);
		SkateGhostUpdate g = GhostCodec.encode(grind, 0xfff, null);
		g.seq = 1_234_567_890;
		g.dk = longest(DesignPart.DECK);
		g.dv = GhostHub.DUEL_VERSION;
		g.gw = longest(DesignPart.GRIP) + "." + longest(DesignPart.WHEELS);
		fastTrail(1f).fit(g, 1f);
		String gj = GSON.toJson(g, WebsocketMessage.class);
		assertTrue(gj.length() + " " + gj, gj.length() <= GhostWire.MAX_UPDATE_CHARS);
		System.out.println("busiest grind with designs: " + gj.length() + " chars, timeline " + g.tj);
	}

	@Test
	public void aTypicalUpdateCarriesItsFourPositionsAndAPopItsTime()
	{
		// rolling at speed, a custom deck, seq of today's size, the duel version
		GhostState rolling = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -237f, 1.234f, 1200f, -900f, 0f,
			SkaterState.ROLLING, null, null, 0f, 1.2f);
		SkateGhostUpdate m = GhostCodec.encode(rolling, GhostCodec.EV_PUSH, null);
		m.seq = 412_345_678;
		m.dk = "C:0a1b2c3d";
		m.dv = GhostHub.DUEL_VERSION;
		GhostTrail trail = fastTrail(1f);
		trail.sent(0.2f);
		trail.fit(m, 1f);
		GhostTrajectory back = GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd);
		assertEquals(GhostTrail.MAX_SAMPLES, back.count);
		assertEquals(0.9f, back.eventAgo[11], 0.006f);
		int withTimeline = GSON.toJson(m, WebsocketMessage.class).length();
		m.tj = null;
		int without = GSON.toJson(m, WebsocketMessage.class).length();
		System.out.println("typical update: " + without + " chars, " + withTimeline + " with its timeline");
		assertTrue(withTimeline <= GhostWire.MAX_UPDATE_CHARS);

		// a kickflip's pop, sent at once
		GhostState pop = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, -237f, 1.234f, 1200f, -900f, 820f,
			SkaterState.AIRBORNE, null, Trick.KICKFLIP, 0.04f, 1.2f);
		SkateGhostUpdate p = GhostCodec.encode(pop, GhostCodec.EV_POP | GhostCodec.EV_TRICK, Trick.KICKFLIP);
		p.seq = 412_345_679;
		p.dk = "C:0a1b2c3d";
		p.dv = GhostHub.DUEL_VERSION;
		fastTrail(1f).fit(p, 1f);
		GhostTrajectory pb = GhostTrajectory.decode(p.tj, p.ev, p.x, p.y, p.h, p.hd);
		assertTrue(pb.count >= 2);
		assertEquals(0.9f, pb.eventAgo[0], 0.006f);
		System.out.println("kickflip pop: " + GSON.toJson(p, WebsocketMessage.class).length() + " chars with "
			+ pb.count + " positions");
	}

	@Test
	public void olderVersionsReadAnUpdateWithATimelineAsBefore()
	{
		GhostState f = GhostFeed.frame(330, 2, 1_409_664f, 1_411_200f, 0f, 1f, 600f, 0f, 0f, SkaterState.ROLLING, null,
			null, 0f);
		SkateGhostUpdate m = GhostCodec.encode(f, GhostCodec.EV_PUSH, null);
		m.seq = 5;
		fastTrail(1f).fit(m, 1f);
		String json = GSON.toJson(m, WebsocketMessage.class);
		assertTrue(json, json.contains("\"tj\":\""));
		// an older version has no tj field: Gson skips it, and the push bit means nothing to it
		String older = json.replaceAll(",?\"tj\":\"[^\"]*\"", "");
		GhostState a = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(json, WebsocketMessage.class));
		GhostState b = GhostCodec.decode((SkateGhostUpdate) GSON.fromJson(older, WebsocketMessage.class));
		assertEquals(a.x, b.x, 0f);
		assertEquals(a.heading, b.heading, 0f);
		assertEquals(a.events, b.events);
	}
}
