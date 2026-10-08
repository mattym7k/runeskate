package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.gielinorskate.duel.SkateDuelHit;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

public class GhostHubTest
{
	private static final float FRAME = 1f / 50f;

	/** Records what would go to the party. */
	private static final class FakeLink implements GhostHub.Link
	{
		boolean inParty = true;
		final List<PartyMessage> sent = new ArrayList<>();
		final List<Float> times = new ArrayList<>();
		float now;

		@Override
		public boolean inParty()
		{
			return inParty;
		}

		@Override
		public void send(PartyMessage m)
		{
			sent.add(m);
			times.add(now);
		}

		int updates()
		{
			return (int) sent.stream().filter(m -> m instanceof SkateGhostUpdate).count();
		}

		int stops()
		{
			return (int) sent.stream().filter(m -> m instanceof SkateGhostStop).count();
		}
	}

	private final FakeLink link = new FakeLink();
	private final GhostHub hub = new GhostHub(link, 1000);

	private static GhostFrame frame(float x)
	{
		return new GhostFrame(420, 0, x, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
	}

	/** Another party member is skating (member 99 sent us an update), so full updates go out. */
	private void audience()
	{
		hub.onRemoteUpdate(99L, remote(0f, 1), 0f, null);
	}

	@Test
	public void withNobodyElseSkatingOnlyAnAnnounceEveryFiveSeconds()
	{
		for (int i = 0; i < 1050; i++)
		{
			// moving and popping every frame
			frame(i * FRAME, frame(i), GhostCodec.EV_POP, false, true);
		}
		// t = 0, 5, 10, 15 and 20 s
		assertEquals(5, link.updates());
		for (PartyMessage m : link.sent)
		{
			// nobody draws them, so events are not sent (nor saved up)
			assertEquals(0, ((SkateGhostUpdate) m).ev);
		}
	}

	@Test
	public void anotherSkaterAppearingStartsFullUpdatesAtOnce()
	{
		frame(0f, frame(0f), 0, false, true);
		frame(1f, frame(1f), GhostCodec.EV_POP, false, true);
		assertEquals(1, link.updates());
		audience();
		frame(1.1f, frame(2f), GhostCodec.EV_POP, false, true);
		assertEquals(2, link.updates());
		assertEquals(GhostCodec.EV_POP, ((SkateGhostUpdate) link.sent.get(1)).ev);
	}

	@Test
	public void theLastOtherSkaterStoppingFallsBackToAnnounces()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		hub.onRemoteStop(99L);
		for (int i = 1; i < 200; i++)
		{
			frame(i * FRAME, frame(i), GhostCodec.EV_POP, false, true);
		}
		// t = 0 only: the next announce is due at 5 s
		assertEquals(1, link.updates());
		hub.onLocalSkateEnd(link.now);
		assertEquals("the announce still made us live, so a stop goes out", 1, link.stops());
	}

	private void frame(float now, GhostFrame f, int events, boolean pvp, boolean sharing)
	{
		link.now = now;
		hub.onLocalFrame(f, events, null, pvp, sharing, now);
	}

	@Test
	public void firstSnapshotGoesOutAtOnceWithIncreasingSeq()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		frame(0.6f, frame(10f), 0, false, true);
		assertEquals(2, link.updates());
		int a = ((SkateGhostUpdate) link.sent.get(0)).seq;
		int b = ((SkateGhostUpdate) link.sent.get(1)).seq;
		assertTrue(a > 1000);
		assertTrue(b > a);
	}

	@Test
	public void standingStillOnlySendsKeepAlives()
	{
		audience();
		for (int i = 0; i < 1050; i++)
		{
			frame(i * FRAME, frame(0f), 0, false, true);
		}
		// t = 0, 10 and 20 s
		assertEquals(3, link.updates());
	}

	@Test
	public void nothingIsSentWhenNotAllowed()
	{
		audience();
		frame(0f, frame(0f), GhostCodec.EV_POP, true, true);
		frame(1f, frame(5f), GhostCodec.EV_POP, false, false);
		link.inParty = false;
		frame(2f, frame(9f), GhostCodec.EV_POP, false, true);
		assertTrue(link.sent.isEmpty());
	}

	@Test
	public void enteringAPvpAreaSendsOneStop()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		for (int i = 1; i < 100; i++)
		{
			frame(i * FRAME, frame(i), GhostCodec.EV_POP, true, true);
		}
		assertEquals(1, link.updates());
		assertEquals(1, link.stops());
		// and sharing again afterwards starts with an immediate snapshot
		frame(3f, frame(0f), 0, false, true);
		assertEquals(2, link.updates());
	}

	@Test
	public void sharingOffSendsOneStop()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		frame(0.1f, frame(0f), 0, false, false);
		frame(0.2f, frame(0f), 0, false, false);
		assertEquals(1, link.stops());
	}

	@Test
	public void skateEndSendsOneStopOnlyIfSomethingWasShared()
	{
		audience();
		hub.onLocalSkateEnd(link.now);
		assertEquals(0, link.stops());
		frame(0f, frame(0f), 0, false, true);
		hub.onLocalSkateEnd(link.now);
		hub.onLocalSkateEnd(link.now);
		assertEquals(1, link.stops());
	}

	@Test
	public void noStopWhenTheLocalPlayerAlreadyLeftTheParty()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		link.inParty = false;
		hub.onLocalSkateEnd(link.now);
		assertEquals(0, link.stops());
	}

	@Test
	public void closedHubSendsNothing()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		hub.close();
		assertEquals(1, link.stops());
		frame(1f, frame(50f), GhostCodec.EV_POP, false, true);
		hub.onLocalSkateEnd(link.now);
		assertEquals(1, link.updates());
		assertEquals(1, link.stops());
		hub.open();
		frame(2f, frame(50f), 0, false, true);
		assertEquals(2, link.updates());
	}

	@Test
	public void eventSpamStaysUnderTwoPerSecondAndEventsAreNotLost()
	{
		audience();
		for (int i = 0; i < 500; i++)
		{
			float t = i * FRAME;
			// pvp flapping on and off adds stops on top of the event spam
			frame(t, frame(i), GhostCodec.EV_TRICK, i % 37 == 0, true);
		}
		List<Float> times = link.times;
		for (int i = 2; i < times.size(); i++)
		{
			assertTrue("window at " + times.get(i), times.get(i) - times.get(i - 2) >= 1f - 1e-4f);
		}
		for (PartyMessage m : link.sent)
		{
			if (m instanceof SkateGhostUpdate)
			{
				assertEquals(GhostCodec.EV_TRICK, ((SkateGhostUpdate) m).ev);
			}
		}
	}

	@Test
	public void rateLimitedEventsAreCoalescedIntoTheNextUpdate()
	{
		audience();
		// two quick events use up the second's budget
		for (int i = 0; i < 2; i++)
		{
			frame(i * FRAME, frame(i), GhostCodec.EV_TRICK, false, true);
		}
		frame(0.1f, frame(5f), GhostCodec.EV_POP, false, true);
		frame(0.2f, frame(6f), GhostCodec.EV_LAND, false, true);
		assertEquals(2, link.updates());
		frame(1.0f, frame(7f), 0, false, true);
		SkateGhostUpdate last = (SkateGhostUpdate) link.sent.get(link.sent.size() - 1);
		assertEquals(GhostCodec.EV_POP | GhostCodec.EV_LAND, last.ev);
	}

	@Test
	public void eventTrickIsKeptUntilSent()
	{
		audience();
		for (int i = 0; i < 2; i++)
		{
			frame(i * FRAME, frame(i), GhostCodec.EV_POP, false, true);
		}
		link.now = 0.5f;
		hub.onLocalFrame(frame(9f), GhostCodec.EV_TRICK, Trick.INDY, false, true, 0.5f);
		frame(1.0f, frame(10f), 0, false, true);
		SkateGhostUpdate last = (SkateGhostUpdate) link.sent.get(link.sent.size() - 1);
		assertEquals("INDY", last.tr);
	}

	private static SkateGhostUpdate remote(float x, int seq)
	{
		SkateGhostUpdate m = GhostCodec.encode(frame(x), 0, null);
		m.seq = seq;
		return m;
	}

	@Test
	public void remoteUpdatesMakeGhostsAndStopsRemoveThem()
	{
		hub.onRemoteUpdate(7L, remote(100f, 1), 0f, null);
		hub.onRemoteUpdate(8L, remote(200f, 1), 0f, null);
		assertEquals(2, hub.ghosts().size());
		assertNotNull(hub.ghosts().get(7L));
		hub.onRemoteStop(7L);
		assertFalse(hub.ghosts().containsKey(7L));
		hub.onMemberLeft(8L);
		assertTrue(hub.ghosts().isEmpty());
	}

	@Test
	public void silentGhostsArePruned()
	{
		hub.onRemoteUpdate(7L, remote(100f, 1), 0f, null);
		hub.onRemoteUpdate(8L, remote(200f, 1), 10f, null);
		hub.prune(16f);
		assertFalse(hub.ghosts().containsKey(7L));
		assertTrue(hub.ghosts().containsKey(8L));
		hub.clearGhosts();
		assertTrue(hub.ghosts().isEmpty());
	}

	@Test
	public void seqSeedGrowsWithWallClockSoARestartNeverGoesBackwards()
	{
		long t = 1_800_000_000_000L;
		assertTrue(GhostHub.seqSeed(t + 1000) > GhostHub.seqSeed(t));
		// four per second of wall time: a run sending at the 2/s limit never overtakes the next run's seed
		assertEquals(4, GhostHub.seqSeed(t + 1000) - GhostHub.seqSeed(t));
		assertTrue(GhostHub.seqSeed(t) > 0);
	}

	private static final BoardDesigns DESIGNS = BoardDesigns.bundled();

	private static BoardLook look(String grip, String deck, String wheels)
	{
		return new BoardLook(DESIGNS.byId(grip), DESIGNS.byId(deck), DESIGNS.byId(wheels));
	}

	private SkateGhostUpdate lastUpdate()
	{
		return (SkateGhostUpdate) link.sent.get(link.sent.size() - 1);
	}

	@Test
	public void theLocalLookGoesOutAndTheDefaultDeckIsLeftOut()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		assertEquals(null, lastUpdate().dk);
		// the first update after starting names the grip and wheels
		assertEquals("BLACK.NATURAL", lastUpdate().gw);
		hub.setLocalLook(look("GRIP_RUNE", "RUNE", "WHEELS_NATURAL"));
		// a design change alone is a change worth sending
		frame(5f, frame(0f), 0, false, true);
		assertEquals("RUNE", lastUpdate().dk);
		assertEquals("RUNE.NATURAL", lastUpdate().gw);
		// back on the default deck (DECK_TROPICAL): left out again
		hub.setLocalLook(look("GRIP_RUNE", "DECK_TROPICAL", "WHEELS_NATURAL"));
		frame(10f, frame(0f), 0, false, true);
		assertNull(lastUpdate().dk);
		assertEquals("RUNE.NATURAL", lastUpdate().gw);
	}

	@Test
	public void theGripAndWheelsRideAlongNowAndThenNotInEveryUpdate()
	{
		audience();
		int with = 0;
		int without = 0;
		for (int i = 0; i < 600; i++)
		{
			// moving: snapshots every game tick for 12 s
			frame(i * FRAME, frame(i * 3f), 0, false, true);
		}
		for (PartyMessage m : link.sent)
		{
			if (((SkateGhostUpdate) m).gw != null)
			{
				with++;
			}
			else
			{
				without++;
			}
		}
		// at the start, then every LOOK_REFRESH_SECONDS
		assertEquals(1 + (int) (12f / GhostHub.LOOK_REFRESH_SECONDS), with);
		assertTrue(without > with * 4);
	}

	@Test
	public void theGripAndWheelsWaitForAnUpdateWithoutATrick()
	{
		audience();
		hub.setLocalLook(look("GRIP_SARADOMIN", "SARADOMIN", "WHEELS_SARADOMIN"));
		GhostFrame flipping = new GhostFrame(420, 0, 0f, 0f, 50f, 0f, 0f, 0f, 0f, SkaterState.AIRBORNE, null,
			Trick.KICKFLIP, 0.5f);
		frame(0f, flipping, 0, false, true);
		// a flip's name already makes it the biggest kind of update: the designs wait
		assertNotNull(lastUpdate().tr);
		assertNull(lastUpdate().gw);
		assertEquals("SARADOMIN", lastUpdate().dk);
		frame(0.6f, frame(5f), 0, false, true);
		assertEquals("SARADOMIN.SARADOMIN", lastUpdate().gw);
	}

	@Test
	public void restartingSendsTheDesignsAgainAtOnce()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		frame(0.6f, frame(10f), 0, false, true);
		assertNull(lastUpdate().gw);
		hub.onLocalSkateEnd(1f);
		audience();
		frame(2f, frame(0f), 0, false, true);
		assertEquals("BLACK.NATURAL", lastUpdate().gw);
	}

	@Test
	public void remoteDesignsAreCheckedAgainstTheManifest()
	{
		SkateGhostUpdate m = remote(0f, 1);
		m.dk = "TORVA";
		m.gw = "TORVA.TORVA";
		hub.onRemoteUpdate(5L, m, 0f, null);
		assertEquals("GRIP_TORVA/TORVA/WHEELS_TORVA", hub.ghosts().get(5L).look().toString());
		// later updates without the grip and wheels keep them; the deck goes in every update
		SkateGhostUpdate next = remote(0f, 2);
		next.dk = "RUNE";
		hub.onRemoteUpdate(5L, next, 0.1f, null);
		assertEquals("GRIP_TORVA/RUNE/WHEELS_TORVA", hub.ghosts().get(5L).look().toString());
		// the old starter deck and decks removed from the ladder: the default deck
		SkateGhostUpdate classic = remote(0f, 1);
		classic.dk = "CLASSIC";
		hub.onRemoteUpdate(7L, classic, 0f, null);
		assertEquals(BoardLook.defaults(DESIGNS), hub.ghosts().get(7L).look());
		SkateGhostUpdate removed = remote(0f, 2);
		removed.dk = "MAX_CAPE";
		hub.onRemoteUpdate(7L, removed, 0.1f, null);
		assertEquals(BoardLook.defaults(DESIGNS), hub.ghosts().get(7L).look());
		// junk: defaults, never an exception
		SkateGhostUpdate odd = remote(0f, 3);
		odd.dk = "<b>GOLD</b>";
		odd.gw = "...TORVA.";
		hub.onRemoteUpdate(5L, odd, 0.2f, null);
		assertEquals(BoardLook.defaults(DESIGNS), hub.ghosts().get(5L).look());
		SkateGhostUpdate half = remote(0f, 4);
		half.gw = "BRONZE";
		hub.onRemoteUpdate(5L, half, 0.3f, null);
		assertEquals("GRIP_BRONZE/DECK_TROPICAL/WHEELS_NATURAL", hub.ghosts().get(5L).look().toString());
		// an older version naming the removed test designs: the defaults
		SkateGhostUpdate removedTest = remote(0f, 5);
		removedTest.dk = "RED_CAMO";
		removedTest.gw = "RUNESKATE.DEATH";
		hub.onRemoteUpdate(5L, removedTest, 0.4f, null);
		assertEquals(BoardLook.defaults(DESIGNS), hub.ghosts().get(5L).look());
		// an older version sends neither: the defaults
		hub.onRemoteUpdate(6L, remote(0f, 1), 0f, null);
		assertEquals(BoardLook.defaults(DESIGNS), hub.ghosts().get(6L).look());
	}

	@Test
	public void aStaleUpdateDoesNotChangeTheDesigns()
	{
		SkateGhostUpdate current = remote(0f, 2);
		current.dk = "DRAGON";
		current.gw = "DRAGON.DRAGON";
		hub.onRemoteUpdate(5L, current, 0f, null);
		assertEquals("GRIP_DRAGON/DRAGON/WHEELS_DRAGON", hub.ghosts().get(5L).look().toString());
		// an earlier, out-of-order packet (lower seq, within the resync window): rejected as stale
		SkateGhostUpdate stale = remote(0f, 1);
		stale.dk = "RUNE";
		stale.gw = "RUNE.RUNE";
		hub.onRemoteUpdate(5L, stale, 0.1f, null);
		assertEquals("GRIP_DRAGON/DRAGON/WHEELS_DRAGON", hub.ghosts().get(5L).look().toString());
	}

	@Test
	public void togglingSkatingFastStaysUnderTwoMessagesPerSecond()
	{
		// nobody else skating: each session announces once a token is free, and its end sends a stop
		float[] sessions = {0.5f, 0.6f, 0.3f, 0.9f, 0.2f, 0.45f};
		float t = 0f;
		for (int i = 0; i < 60; i++)
		{
			float end = t + sessions[i % sessions.length];
			for (; t < end; t += FRAME)
			{
				frame(t, frame(t), 0, false, true);
			}
			link.now = t;
			hub.onLocalSkateEnd(t);
			t += 0.1f;
		}
		List<Float> times = link.times;
		assertTrue(link.updates() > 1);
		for (int i = 2; i < times.size(); i++)
		{
			assertTrue("window at " + times.get(i), times.get(i) - times.get(i - 2) >= 1f - 1e-4f);
		}
	}

	// ---- Skate Duel messages share the budget

	private int duels()
	{
		return (int) link.sent.stream().filter(m -> m instanceof SkateDuelHit).count();
	}

	@Test
	public void duelMessagesGoBeforeGhostUpdatesAndCountInTheBudget()
	{
		audience();
		hub.sendDuel(new SkateDuelHit());
		frame(0f, frame(0f), GhostCodec.EV_POP, false, true);
		assertEquals(1, duels());
		// the duel message took a token; the event still fits (2 per second)
		assertEquals(1, link.updates());
		assertTrue(link.sent.get(0) instanceof SkateDuelHit);
		hub.sendDuel(new SkateDuelHit());
		frame(0.1f, frame(1f), GhostCodec.EV_POP, false, true);
		// no token left in this second: neither goes, and the duel message is kept, not dropped
		assertEquals(1, duels());
		assertEquals(1, link.updates());
		frame(1.0f, frame(2f), GhostCodec.EV_POP, false, true);
		assertEquals("the waiting duel message goes first", 2, duels());
		assertTrue(link.sent.get(2) instanceof SkateDuelHit);
	}

	@Test
	public void aWaitingDuelMessageHoldsBackGhostUpdatesUntilItIsOut()
	{
		audience();
		for (int i = 0; i < 5; i++)
		{
			hub.sendDuel(new SkateDuelHit());
		}
		for (int i = 0; i < 200; i++)
		{
			frame(i * FRAME, frame(i), GhostCodec.EV_POP, false, true);
		}
		// 4 s: 8 tokens, the 5 duel messages first, then updates
		assertEquals(5, duels());
		int firstUpdate = -1;
		for (int i = 0; i < link.sent.size(); i++)
		{
			if (link.sent.get(i) instanceof SkateGhostUpdate)
			{
				firstUpdate = i;
				break;
			}
		}
		assertEquals(5, firstUpdate);
		assertLimit();
	}

	@Test
	public void duelMessagesFlushWithoutSkatingAndStayInTheLimit()
	{
		for (int i = 0; i < 4; i++)
		{
			hub.sendDuel(new SkateDuelHit());
		}
		link.now = 0f;
		hub.flushDuel(0f);
		link.now = 0.5f;
		hub.flushDuel(0.5f);
		assertEquals(2, duels());
		link.now = 1f;
		hub.flushDuel(1f);
		assertEquals(4, duels());
		assertLimit();
	}

	@Test
	public void duelMessagesAreDroppedOutsideAParty()
	{
		hub.sendDuel(new SkateDuelHit());
		link.inParty = false;
		hub.flushDuel(0f);
		link.inParty = true;
		hub.flushDuel(1f);
		assertEquals(0, duels());
	}

	@Test
	public void closingSendsOnlyTheLatestLastWord()
	{
		SkateDuelHit forfeit = new SkateDuelHit();
		hub.sendDuel(new SkateDuelHit());
		hub.sendDuel(new SkateDuelHit());
		hub.sendDuel(forfeit, true);
		hub.sendDuel(new SkateDuelHit());
		hub.close();
		// queued hits would go over the budget in a burst: only the duel's last word goes
		assertEquals(1, duels());
		assertTrue(link.sent.get(0) == forfeit);
		hub.sendDuel(new SkateDuelHit(), true);
		hub.flushDuel(5f);
		assertEquals("closed: nothing more", 1, duels());
	}

	@Test
	public void closingWithNoLastWordSendsNoDuelMessage()
	{
		hub.sendDuel(new SkateDuelHit());
		hub.close();
		assertEquals(0, duels());
	}

	@Test
	public void closingInAPvpAreaAfterTheLastWordSendsNoOther()
	{
		hub.sendDuel(new SkateDuelHit(), true);
		hub.flushDuel(0f, true);
		hub.sendDuel(new SkateDuelHit(), true);
		hub.close();
		assertEquals(1, duels());
	}

	@Test
	public void whileBlockedOnlyOneLastWordGoesOut()
	{
		SkateDuelHit last = new SkateDuelHit();
		hub.sendDuel(new SkateDuelHit());
		hub.sendDuel(new SkateDuelHit());
		hub.sendDuel(last, true);
		hub.sendDuel(new SkateDuelHit());
		link.now = 0f;
		hub.flushDuel(0f, true);
		assertEquals(1, duels());
		assertTrue(link.sent.get(0) == last);
		// a second last word in the same stretch is dropped too
		hub.sendDuel(new SkateDuelHit(), true);
		hub.flushDuel(5f, true);
		assertEquals(1, duels());
		// out again: sending resumes
		hub.flushDuel(6f, false);
		hub.sendDuel(new SkateDuelHit());
		hub.flushDuel(7f, false);
		assertEquals(2, duels());
	}

	@Test
	public void aSkateFrameInAPvpAreaDropsWaitingDuelMessages()
	{
		audience();
		hub.sendDuel(new SkateDuelHit());
		frame(0f, frame(0f), 0, true, true);
		hub.flushDuel(1f, false);
		assertEquals(0, duels());
	}

	@Test
	public void theDuelCapabilityGoesOutOnlyWhenOn()
	{
		frame(0f, frame(0f), 0, false, true);
		assertNull(((SkateGhostUpdate) link.sent.get(0)).dv);
		hub.setDuelCapable(true);
		frame(6f, frame(0f), 0, false, true);
		assertEquals(Integer.valueOf(GhostHub.DUEL_VERSION), ((SkateGhostUpdate) link.sent.get(1)).dv);
	}

	@Test
	public void aRemoteDuelCapabilityIsKeptAndMissingMeansNone()
	{
		hub.onRemoteUpdate(7L, remote(0f, 1), 0f, null);
		assertEquals(0, hub.ghosts().get(7L).duelVersion());
		SkateGhostUpdate m = remote(0f, 2);
		m.dv = 1;
		hub.onRemoteUpdate(7L, m, 0.1f, null);
		assertEquals(1, hub.ghosts().get(7L).duelVersion());
	}

	/** Never more than 2 messages of any kind in any 1-second window. */
	private void assertLimit()
	{
		for (int i = 0; i + 2 < link.times.size(); i++)
		{
			assertTrue("3 messages within 1 s at " + link.times.get(i),
				link.times.get(i + 2) - link.times.get(i) >= 1f - 1e-4f);
		}
	}

	@Test
	public void aPushRidesWithTheNextSnapshotAndCarriesItsTime()
	{
		audience();
		frame(0f, frame(0f), 0, false, true);
		assertEquals(1, link.updates());
		// a push: a passive event, nothing goes out at once
		frame(0.1f, frame(5f), GhostCodec.EV_PUSH, false, true);
		frame(0.2f, frame(10f), 0, false, true);
		assertEquals(1, link.updates());
		// the next snapshot carries it, and when it happened
		frame(0.6f, frame(30f), 0, false, true);
		assertEquals(2, link.updates());
		SkateGhostUpdate m = (SkateGhostUpdate) link.sent.get(link.sent.size() - 1);
		assertEquals(GhostCodec.EV_PUSH, m.ev);
		GhostTrajectory tj = GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd);
		assertEquals(0.5f, tj.eventAgo[11], 0.006f);
		assertEquals(600, tj.timeMs);
		// a pop still goes at once
		frame(0.7f, frame(35f), GhostCodec.EV_POP, false, true);
		assertEquals(2, link.updates());
		frame(1.21f, frame(40f), GhostCodec.EV_POP, false, true);
		assertEquals(3, link.updates());
	}

	@Test
	public void everyUpdateCarriesItsPositionsSinceTheLastAndNothingCarriesOverAStop()
	{
		audience();
		for (int i = 0; i <= 90; i++)
		{
			frame(i * FRAME, frame(i * 10f), 0, false, true);
		}
		SkateGhostUpdate last = (SkateGhostUpdate) link.sent.get(link.sent.size() - 1);
		GhostTrajectory tj = GhostTrajectory.decode(last.tj, last.ev, last.x, last.y, last.h, last.hd);
		assertTrue(tj.count >= 3);
		for (int i = 0; i < tj.count; i++)
		{
			// 10 units a frame
			assertEquals(last.x - tj.ago[i] / FRAME * 10f, tj.x[i], 10.5f);
		}
		hub.onLocalSkateEnd(2f);
		int before = link.sent.size();
		frame(10f, frame(5000f), 0, false, true);
		SkateGhostUpdate fresh = (SkateGhostUpdate) link.sent.get(before);
		assertEquals(0, GhostTrajectory.decode(fresh.tj, fresh.ev, fresh.x, fresh.y, fresh.h, fresh.hd).count);
	}
}
