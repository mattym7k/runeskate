package com.gielinorskate.party;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.design.CustomBake;
import com.gielinorskate.design.DesignLayout;
import com.gielinorskate.design.ImagePlacement;
import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Two clients in one party through a fake party bus that serialises every message the way RuneLite's party client
 * does: A shares a custom deck, B sees it on A's ghost once the picture is complete.
 */
public class PartyShareSimulationTest
{
	private static final float FRAME = 1f / 50f;
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class)
			.registerSubtype(SkateDesignOffer.class)
			.registerSubtype(SkateDesignChunk.class))
		.create();
	private static final BoardDesigns DESIGNS = BoardDesigns.bundled();
	private static final BoardDesign MY_DECK = BoardDesign.custom("CUSTOM_0A1B2C3E", "Checkers", DesignPart.DECK, 1);

	/** One client: its hub, its party designs, and its client thread and executor as queues the test runs. */
	private final class Client
	{
		final long id;
		final GhostHub hub;
		final PartyDesigns designs;
		final ArrayDeque<Runnable> clientThread = new ArrayDeque<>();
		final ArrayDeque<Runnable> executor = new ArrayDeque<>();
		final Map<String, int[]> colours = new HashMap<>();
		boolean inParty = true;
		/** The largest message this client sent, characters of JSON. */
		int largest;
		Client peer;

		Client(long id)
		{
			this.id = id;
			this.hub = new GhostHub(new GhostHub.Link()
			{
				@Override
				public boolean inParty()
				{
					return inParty;
				}

				@Override
				public void send(PartyMessage message)
				{
					deliver(Client.this, message);
				}
			}, 1000);
			this.designs = new PartyDesigns(executor::add, clientThread::add, members::contains,
				PartyShareSimulationTest::bake, colours::put, colours::remove, () -> this.hub.relook());
			hub.setMemberDesigns(designs);
			hub.setDesignSharing(true);
			// the receiver has opted in to seeing party members' designs (off by default)
			designs.setShowOthers(true);
		}

		/** Runs what is waiting on the executor, then on the client thread. */
		void pump()
		{
			while (!executor.isEmpty())
			{
				executor.poll().run();
			}
			while (!clientThread.isEmpty())
			{
				clientThread.poll().run();
			}
		}

		BoardLook ghostOf(Client other)
		{
			GhostPredictor g = hub.ghosts().get(other.id);
			return g == null ? null : g.look();
		}
	}

	private final Set<Long> members = new HashSet<>();
	private float now;
	private Client a;
	private Client b;

	private static int[] bake(DesignPart part, BufferedImage picture)
	{
		return SharedDesignImage.bake(picture, mesh(part), DesignLayout.bundled().of(part));
	}

	private static BakedBoardGeometry.Mesh mesh(DesignPart part)
	{
		for (BakedBoardGeometry.Mesh m : BakedBoardGeometry.sharedBoard(false))
		{
			if (m.part == part.index)
			{
				return m;
			}
		}
		throw new AssertionError("no " + part);
	}

	/** The party bus: JSON out, JSON in, the sender's member id set by the party, to the other client. */
	private void deliver(Client from, PartyMessage message)
	{
		if (!from.inParty)
		{
			return;
		}
		String json = GSON.toJson(message, WebsocketMessage.class);
		from.largest = Math.max(from.largest, json.length());
		Client to = from.peer;
		if (!to.inParty)
		{
			return;
		}
		WebsocketMessage back = GSON.fromJson(json, WebsocketMessage.class);
		((PartyMemberMessage) back).setMemberId(from.id);
		// party callbacks are handed to the client thread
		to.clientThread.add(() -> receive(to, back));
	}

	private void receive(Client to, WebsocketMessage m)
	{
		long sender = ((PartyMemberMessage) m).getMemberId();
		if (m instanceof SkateGhostUpdate)
		{
			to.hub.onRemoteUpdate(sender, (SkateGhostUpdate) m, now, null);
		}
		else if (m instanceof SkateGhostStop)
		{
			to.hub.onMemberLeft(sender);
		}
		else if (m instanceof SkateDesignOffer)
		{
			to.designs.onOffer(sender, (SkateDesignOffer) m, now);
		}
		else if (m instanceof SkateDesignChunk)
		{
			to.designs.onChunk(sender, (SkateDesignChunk) m, now);
		}
	}

	/** A checkerboard: compresses fine but is not flat. */
	private static BufferedImage checkers()
	{
		BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 120; y++)
		{
			for (int x = 0; x < 120; x++)
			{
				img.setRGB(x, y, (x / 15 + y / 15) % 2 == 0 ? 0xd02020 : 0x202080);
			}
		}
		return img;
	}

	private static DesignShare.Outgoing checkersDeck()
	{
		BufferedImage img = checkers();
		DesignLayout.Part layout = DesignLayout.bundled().of(DesignPart.DECK);
		CustomBake.Source src = new CustomBake.Source(img.getRGB(0, 0, 120, 120, null, 0, 120), 120, 120, 0);
		ImagePlacement p = ImagePlacement.initial(120, 120, new double[]{0, 0, layout.width, layout.height});
		return DesignShare.outgoing(MY_DECK.id, MY_DECK.name, SharedDesignImage.encode(src, DesignPart.DECK, layout,
			p));
	}

	private static GhostState still()
	{
		return GhostFeed.frame(420, 0, 6400f, 6400f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, null, 0f);
	}

	/** One frame on both clients: skate frames (both skating, standing still), the duel tick's flush, callbacks. */
	private void frame(boolean aSkating, boolean bSkating)
	{
		for (Client c : new Client[]{a, b})
		{
			boolean skating = c == a ? aSkating : bSkating;
			if (skating)
			{
				c.hub.prune(now);
				c.hub.onLocalFrame(still(), 0, null, false, true, now);
			}
			c.hub.flushDuel(now, false);
		}
		a.pump();
		b.pump();
		now += FRAME;
	}

	@Before
	public void party()
	{
		a = new Client(1L);
		b = new Client(2L);
		a.peer = b;
		b.peer = a;
		members.add(1L);
		members.add(2L);
		a.hub.setLocalLook(BoardLook.defaults(DESIGNS).with(MY_DECK));
	}

	@Test
	public void bSeesAsCustomDeckOnceItIsCompleteAndTheDefaultUntilThen()
	{
		DesignShare.Outgoing deck = checkersDeck();
		assertNotNull(deck);
		a.hub.setLocalDesign(DesignPart.DECK, deck);
		boolean sawDefaultWhileOnItsWay = false;
		BoardLook seen = null;
		for (int i = 0; i < 50 * 30 && b.designs.size() == 0; i++)
		{
			frame(true, true);
			seen = b.ghostOf(a);
			if (seen != null && b.designs.size() == 0)
			{
				// the ghost names the custom deck, but it is not here yet: the default deck
				assertEquals(DESIGNS.defaultFor(DesignPart.DECK), seen.deck);
				sawDefaultWhileOnItsWay = true;
			}
		}
		assertTrue(sawDefaultWhileOnItsWay);
		assertEquals(1, b.designs.size());
		seen = b.ghostOf(a);
		assertEquals(PartyDesigns.designId(1L, deck.hash), seen.deck.id);
		assertEquals("Checkers", seen.deck.name);
		// B's colours are the Normal-detail bake of the very picture A sent
		byte[] png = java.util.Base64.getDecoder().decode(String.join("", deck.chunks));
		int[] expected = bake(DesignPart.DECK, SharedDesignImage.decode(png, deck.width, deck.height));
		assertArrayEquals(expected, b.colours.get(seen.deck.id));
		// the grip and wheels stay the defaults; A sees no custom design of B
		assertEquals(DESIGNS.defaultFor(DesignPart.GRIP), seen.grip);
		assertEquals(BoardLook.defaults(DESIGNS), a.ghostOf(b));
		// every message stayed small: chunks are the biggest
		assertTrue(String.valueOf(a.largest), a.largest <= DesignShare.CHUNK_CHARS + 100);
	}

	@Test
	public void showingOthersDesignsOffDrawsTheDefault()
	{
		a.hub.setLocalDesign(DesignPart.DECK, checkersDeck());
		for (int i = 0; i < 50 * 30 && b.designs.size() == 0; i++)
		{
			frame(true, true);
		}
		assertFalse(b.ghostOf(a).deck.id.equals(DESIGNS.defaultFor(DesignPart.DECK).id));
		b.designs.setShowOthers(false);
		assertEquals(DESIGNS.defaultFor(DesignPart.DECK), b.ghostOf(a).deck);
		b.designs.setShowOthers(true);
		assertEquals(PartyDesigns.designId(1L, checkersDeck().hash), b.ghostOf(a).deck.id);
	}

	@Test
	public void leavingThePartyLetsEveryDesignGo()
	{
		a.hub.setLocalDesign(DesignPart.DECK, checkersDeck());
		for (int i = 0; i < 50 * 30 && b.designs.size() == 0; i++)
		{
			frame(true, true);
		}
		assertEquals(1, b.colours.size());
		b.designs.clear();
		b.hub.onPartyChanged();
		assertEquals(0, b.designs.size());
		assertTrue(b.colours.isEmpty());
	}

	@Test
	public void sharingOffSendsNoDesignAndNamesTheDefault()
	{
		a.hub.setDesignSharing(false);
		a.hub.setLocalDesign(DesignPart.DECK, checkersDeck());
		for (int i = 0; i < 50 * 20; i++)
		{
			frame(true, true);
		}
		assertEquals(0, b.designs.size());
		assertEquals(BoardLook.defaults(DESIGNS), b.ghostOf(a));
	}

	@Test
	public void aNonMemberIsIgnored()
	{
		members.remove(1L);
		a.hub.setLocalDesign(DesignPart.DECK, checkersDeck());
		for (int i = 0; i < 50 * 20; i++)
		{
			frame(true, true);
		}
		assertEquals(0, b.designs.size());
	}

	@Test
	public void anEditedDesignReplacesTheOldOnTheGhost()
	{
		a.hub.setLocalDesign(DesignPart.DECK, checkersDeck());
		for (int i = 0; i < 50 * 30 && b.designs.size() == 0; i++)
		{
			frame(true, true);
		}
		BoardDesign edited = BoardDesign.custom(MY_DECK.id, MY_DECK.name, DesignPart.DECK, 2);
		a.hub.setLocalLook(BoardLook.defaults(DESIGNS).with(edited));
		// a valid picture of another design (solid blue)
		BufferedImage blue = new BufferedImage(35, 96, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 96; y++)
		{
			for (int x = 0; x < 35; x++)
			{
				blue.setRGB(x, y, 0x0000ff);
			}
		}
		DesignLayout.Part layout = DesignLayout.bundled().of(DesignPart.DECK);
		CustomBake.Source src = new CustomBake.Source(blue.getRGB(0, 0, 35, 96, null, 0, 35), 35, 96, 0);
		DesignShare.Outgoing second = DesignShare.outgoing(MY_DECK.id, MY_DECK.name, SharedDesignImage.encode(src,
			DesignPart.DECK, layout, ImagePlacement.initial(35, 96, new double[]{0, 0, layout.width, layout.height})));
		a.hub.setLocalDesign(DesignPart.DECK, second);
		for (int i = 0; i < 50 * 30 && b.designs.size() < 2; i++)
		{
			frame(true, true);
		}
		// the ghost picks up the new hash with the next update
		for (int i = 0; i < 50 * 11; i++)
		{
			frame(true, true);
		}
		assertEquals(PartyDesigns.designId(1L, second.hash), b.ghostOf(a).deck.id);
	}
}
