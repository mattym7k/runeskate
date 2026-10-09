package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.duel.SkateDuelHit;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.runelite.client.party.messages.PartyMessage;
import org.junit.Test;

/** Design messages are the lowest priority in the shared budget: they never hold back a duel message or update. */
public class DesignBudgetTest
{
	private static final float FRAME = 1f / 50f;

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

		/** "time:x:ev" of every update, to compare two runs. */
		List<String> updates()
		{
			List<String> out = new ArrayList<>();
			for (int i = 0; i < sent.size(); i++)
			{
				if (sent.get(i) instanceof SkateGhostUpdate)
				{
					SkateGhostUpdate u = (SkateGhostUpdate) sent.get(i);
					out.add(times.get(i) + ":" + u.x + ":" + u.ev);
				}
			}
			return out;
		}

		int designs()
		{
			return (int) sent.stream().filter(m -> m instanceof SkateDesignOffer || m instanceof SkateDesignChunk)
				.count();
		}
	}

	static final BoardDesign MY_DECK = BoardDesign.custom("CUSTOM_0A1B2C3E", "Mine", DesignPart.DECK, 1);

	static DesignShare.Outgoing deck(long seed)
	{
		byte[] png = new byte[5000];
		new Random(seed).nextBytes(png);
		return DesignShare.outgoing(MY_DECK.id, "Mine", new SharedDesignImage.Encoded(DesignPart.DECK, 35, 96, png));
	}

	private static GhostState frame(float x)
	{
		return GhostFeed.frame(420, 0, x, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
	}

	private static SkateGhostUpdate remote(int seq)
	{
		SkateGhostUpdate m = GhostCodec.encode(frame(0f), 0, null);
		m.seq = seq;
		return m;
	}

	private static GhostHub hub(FakeLink link, boolean withDesigns)
	{
		GhostHub hub = new GhostHub(link, 1000);
		if (withDesigns)
		{
			hub.setDesignSharing(true);
			hub.setLocalLook(BoardLook.defaults(BoardDesigns.bundled()).with(MY_DECK));
			hub.setLocalDesign(DesignPart.DECK, deck(1));
		}
		return hub;
	}

	/** One frame the way the plugin runs it: the skate frame (when skating), then the duel tick's flush. */
	private static void step(GhostHub hub, FakeLink link, float now, GhostState f, int events, boolean audience)
	{
		link.now = now;
		if (audience)
		{
			// another member keeps skating
			hub.onRemoteUpdate(99L, remote(1 + (int) (now * 10)), now, null);
		}
		if (f != null)
		{
			hub.onLocalFrame(f, events, null, false, true, now);
		}
		hub.flushDuel(now, false);
	}

	@Test
	public void steadyRollingUpdatesAreExactlyTheSameWithDesignsWaiting()
	{
		FakeLink plain = new FakeLink();
		FakeLink with = new FakeLink();
		GhostHub a = hub(plain, false);
		GhostHub b = hub(with, true);
		for (int i = 0; i < 1000; i++)
		{
			float t = i * FRAME;
			step(a, plain, t, frame(i * 10f), i % 37 == 0 ? GhostCodec.EV_POP : 0, true);
			step(b, with, t, frame(i * 10f), i % 37 == 0 ? GhostCodec.EV_POP : 0, true);
		}
		assertEquals(plain.updates(), with.updates());
	}

	/**
	 * Runs {@code hub} for {@code seconds} with an audience, rolling for {@code roll} s then still for {@code pause}
	 * s over and over (starting with {@code firstPause} s still), feeding its design messages to a receiver. The
	 * time the deck completed there, or NaN.
	 */
	private static float mixed(GhostHub hub, FakeLink link, float firstPause, float roll, float pause, float seconds)
	{
		DesignInbox inbox = new DesignInbox();
		float done = Float.NaN;
		int seen = 0;
		float x = 0f;
		for (int i = 0; i * FRAME < seconds; i++)
		{
			float t = i * FRAME;
			boolean rolling = t >= firstPause && (t - firstPause) % (roll + pause) < roll;
			if (rolling)
			{
				x += 10f;
			}
			step(hub, link, t, frame(x), 0, true);
			for (; seen < link.sent.size(); seen++)
			{
				PartyMessage m = link.sent.get(seen);
				if (m instanceof SkateDesignOffer)
				{
					inbox.offer(1L, (SkateDesignOffer) m, t);
				}
				else if (m instanceof SkateDesignChunk && inbox.chunk(1L, (SkateDesignChunk) m, t) != null
					&& Float.isNaN(done))
				{
					done = t;
				}
			}
		}
		return done;
	}

	@Test
	public void rollingAndPausingByTurnsTheDesignStillArrivesWithinThirtySeconds()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		// a couple of seconds rolling, a short stop, again and again
		float done = mixed(hub, link, 0f, 2f, 1.2f, 30f);
		assertFalse(Float.isNaN(done));
		assertTrue("completed at " + done, done <= 30f);
	}

	@Test
	public void aDesignHalfSentBeforeALongRollStillCompletes()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		// still for 3 s (part of it goes), rolling for 35 s (the receiver drops it), then still for 10 s
		float done = mixed(hub, link, 3f, 35f, 10f, 60f);
		assertFalse(Float.isNaN(done));
		assertTrue("completed at " + done, done <= 48f);
	}

	@Test
	public void whileRollingUpdatesKeepTheirTickWithDesignsWaiting()
	{
		FakeLink plain = new FakeLink();
		FakeLink with = new FakeLink();
		GhostHub a = hub(plain, false);
		GhostHub b = hub(with, true);
		mixed(a, plain, 0f, 2f, 1.2f, 30f);
		mixed(b, with, 0f, 2f, 1.2f, 30f);
		assertTrue(with.designs() > 0);
		// every rolling stretch, once a second in: one update a tick, as without designs (the first of a stretch
		// may wait for the token a design took while the skater stood still)
		for (List<Float> gaps : Arrays.asList(rollingGaps(plain, 2f, 1.2f), rollingGaps(with, 2f, 1.2f)))
		{
			assertTrue(gaps.size() >= 4);
			for (float g : gaps)
			{
				assertTrue("gap " + g, g <= GhostSendPolicy.SNAPSHOT_INTERVAL + FRAME + 1e-3f);
			}
		}
	}

	private static List<Float> updateTimes(FakeLink link)
	{
		List<Float> out = new ArrayList<>();
		for (int i = 0; i < link.sent.size(); i++)
		{
			if (link.sent.get(i) instanceof SkateGhostUpdate)
			{
				out.add(link.times.get(i));
			}
		}
		return out;
	}

	/**
	 * The gaps between updates sent within one rolling stretch of {@link #mixed} (no first pause) from a second
	 * after the stretch began.
	 */
	private static List<Float> rollingGaps(FakeLink link, float roll, float pause)
	{
		List<Float> gaps = new ArrayList<>();
		float last = Float.NaN;
		for (int i = 0; i < link.sent.size(); i++)
		{
			if (!(link.sent.get(i) instanceof SkateGhostUpdate))
			{
				continue;
			}
			float t = link.times.get(i);
			float cycle = roll + pause;
			float start = (float) Math.floor(t / cycle) * cycle;
			if (!Float.isNaN(last) && t - start < roll && last - start >= GhostSendPolicy.WINDOW)
			{
				gaps.add(Math.round((t - last) * 1000f) / 1000f);
			}
			last = t;
		}
		return gaps;
	}

	@Test
	public void standingStillDesignsFlowAndAnEventWaitsAtMostASecond()
	{
		FakeLink plain = new FakeLink();
		FakeLink with = new FakeLink();
		GhostHub a = hub(plain, false);
		GhostHub b = hub(with, true);
		for (int i = 0; i < 1500; i++)
		{
			float t = i * FRAME;
			// a pop now and then (more than a second apart), otherwise still
			int ev = i % 65 == 3 ? GhostCodec.EV_POP : 0;
			step(a, plain, t, frame(0f), ev, true);
			step(b, with, t, frame(0f), ev, true);
		}
		// a design may hold a token an event then needs (events can't be foreseen): it waits for it, no longer
		List<Float> was = updateTimes(plain);
		List<Float> now = updateTimes(with);
		assertEquals(was.size(), now.size());
		for (int i = 0; i < was.size(); i++)
		{
			assertTrue(now.get(i) >= was.get(i));
			assertTrue(now.get(i) - was.get(i) <= GhostSendPolicy.WINDOW + 1e-4f);
		}
		// the whole deck went: its offer and every chunk
		assertEquals(deck(1).messages().size(), with.designs());
		assertEquals(0, b.designOut.queue.size());
	}

	@Test
	public void announcesKeepTheirTimeWhileDesignsFlow()
	{
		FakeLink plain = new FakeLink();
		FakeLink with = new FakeLink();
		GhostHub a = hub(plain, false);
		GhostHub b = hub(with, true);
		for (int i = 0; i < 1500; i++)
		{
			float t = i * FRAME;
			// nobody else skating: announces only
			step(a, plain, t, frame(i), 0, false);
			step(b, with, t, frame(i), 0, false);
		}
		assertEquals(plain.updates(), with.updates());
		assertTrue(with.designs() > 0);
	}

	@Test
	public void notSkatingDesignsGoAtMostOneASecondLeavingATokenFree()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		for (int i = 0; i < 600; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
		}
		assertEquals(deck(1).messages().size(), link.designs());
		for (int i = 1; i < link.times.size(); i++)
		{
			assertTrue(link.times.get(i) - link.times.get(i - 1) >= GhostSendPolicy.WINDOW - 1e-4f);
		}
	}

	@Test
	public void duelMessagesAlwaysGoFirst()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		for (int k = 0; k < 4; k++)
		{
			hub.sendDuel(new SkateDuelHit(), false);
		}
		for (int i = 0; i < 300; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
			if (i == 150)
			{
				hub.sendDuel(new SkateDuelHit(), false);
			}
		}
		// every duel hit went out before the first design message, or as soon as it was queued
		int firstDesign = -1;
		for (int i = 0; i < link.sent.size(); i++)
		{
			if (!(link.sent.get(i) instanceof SkateDuelHit))
			{
				firstDesign = i;
				break;
			}
		}
		assertEquals(4, firstDesign);
		// the late hit (queued at frame 150, t = 3.0 s) went as soon as the per-second limit let a token back
		int late = -1;
		for (int i = 4; i < link.sent.size(); i++)
		{
			if (link.sent.get(i) instanceof SkateDuelHit)
			{
				late = i;
			}
		}
		assertTrue(late > 0);
		assertTrue(link.times.get(late) <= 3.0f + FRAME + 1e-4f);
	}

	@Test
	public void nothingGoesOutsideAPartyOrInAPvpAreaOrWithSharingOff()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		link.inParty = false;
		for (int i = 0; i < 200; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
		}
		assertEquals(0, link.designs());
		assertEquals(0, hub.designOut.queue.size());
		link.inParty = true;
		for (int i = 200; i < 400; i++)
		{
			link.now = i * FRAME;
			hub.flushDuel(i * FRAME, true);
		}
		assertEquals(0, link.designs());
		// joined: queued again
		assertEquals(deck(1).messages().size(), hub.designOut.queue.size());
		hub.setDesignSharing(false);
		for (int i = 400; i < 600; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
		}
		assertEquals(0, link.designs());
		assertEquals(0, hub.designOut.queue.size());
	}

	@Test
	public void updatesNameTheSharedDeckOnlyWhileSharing()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		step(hub, link, 0f, frame(0f), 0, true);
		SkateGhostUpdate u = (SkateGhostUpdate) link.sent.get(0);
		assertEquals(GhostCodec.CUSTOM_REF + deck(1).hash, u.dk);
		hub.setDesignSharing(false);
		step(hub, link, 1f, frame(50f), 0, true);
		SkateGhostUpdate off = null;
		for (PartyMessage m : link.sent)
		{
			if (m instanceof SkateGhostUpdate)
			{
				off = (SkateGhostUpdate) m;
			}
		}
		assertNull(off.dk);
	}

	@Test
	public void aNewGhostSendsOurDesignsAgainAtMostOnceAMinute()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		for (int i = 0; i < 1000; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
		}
		int all = deck(1).messages().size();
		assertEquals(all, link.designs());
		hub.onRemoteUpdate(5L, remote(1), 20f, null);
		assertEquals(all, hub.designOut.queue.size());
		hub.onMemberLeft(5L);
		hub.onRemoteUpdate(5L, remote(2), 30f, null);
		// already queued, and not again within the minute
		assertEquals(all, hub.designOut.queue.size());
		for (int i = 1500; i < 3000; i++)
		{
			step(hub, link, i * FRAME, null, 0, false);
		}
		assertEquals(2 * all, link.designs());
		hub.onMemberLeft(5L);
		hub.onRemoteUpdate(5L, remote(3), 70f, null);
		assertEquals(0, hub.designOut.queue.size());
		hub.onMemberLeft(5L);
		hub.onRemoteUpdate(5L, remote(4), 81f, null);
		assertEquals(all, hub.designOut.queue.size());
	}

	@Test
	public void aChangedDesignReplacesTheOneStillQueued()
	{
		FakeLink link = new FakeLink();
		GhostHub hub = hub(link, true);
		int all = deck(1).messages().size();
		assertEquals(all, hub.designOut.queue.size());
		hub.setLocalDesign(DesignPart.DECK, deck(2));
		assertEquals(deck(2).messages().size(), hub.designOut.queue.size());
		// the same again changes nothing
		hub.setLocalDesign(DesignPart.DECK, deck(2));
		assertEquals(deck(2).messages().size(), hub.designOut.queue.size());
		hub.setLocalDesign(DesignPart.DECK, null);
		assertEquals(0, hub.designOut.queue.size());
		assertFalse(link.designs() > 0);
	}
}
