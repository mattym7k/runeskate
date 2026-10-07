package com.gielinorskate.party;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.design.CustomDesignService;
import com.gielinorskate.design.DesignLayout;
import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.FootEvent;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import com.gielinorskate.render.BakedBoardModel;
import com.gielinorskate.render.DesignColours;
import com.gielinorskate.render.FootBody;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.render.OffBoardPose;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMessage;

/**
 * Shares the local skater with the player's RuneLite party and receives the party's skaters, only through
 * RuneLite's own {@link PartyService}. Party messages arrive on the party websocket's thread (WSClient posts
 * them to the event bus from OkHttp's listener), so every handler here hops to the client thread before
 * touching ghosts or the client. Registered on the event bus by the plugin.
 */
@Slf4j
@Singleton
public class PartyGhostService
{
	private final Client client;
	private final ClientThread clientThread;
	private final PartyService party;
	private final GielinorSkateConfig config;
	private final ScoreClock clock;
	private final GhostHub hub;
	private final GhostRenderer renderer;
	/** Client thread. */
	private final TurnRateMeter turnMeter = new TurnRateMeter();
	private final BodyFlipMeter bodyFlipMeter = new BodyFlipMeter();
	/** The board's state last frame (null on the board) and the walker's gait, for the at-once events. Client thread. */
	private BoardState lastOffBoard;
	private FootBody.Gait lastGait = FootBody.Gait.IDLE;
	/** The knockdown stage last shared (null when not knocked down): a new one goes out at once. */
	private KnockdownPose.Stage lastKnockStage;
	/** The latest skate frame was somewhere nothing is shared from (PvP area, instance). */
	private volatile boolean sendBlocked;
	/** Our custom designs' party pictures. */
	private final CustomDesignService customDesigns;
	/** Party members' custom designs, in memory only. Client thread. */
	private final PartyDesigns partyDesigns;
	/** The local skater's collision world as a ghost ground (absolute coordinates), and the scene it covers. */
	private GhostPredictor.Ground collisionGround;
	private int collisionBaseX;
	private int collisionBaseY;
	private int collisionPlane;
	/** Events of our own (a duel ending) for the next shared frame, GhostCodec.EV_* bits. Client thread. */
	private int extraEvents;

	@Inject
	PartyGhostService(Client client, ClientThread clientThread, PartyService party, GielinorSkateConfig config,
		ScoreClock clock, ScheduledExecutorService executor, CustomDesignService customDesigns, SkateFeedback feedback)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.party = party;
		this.config = config;
		this.clock = clock;
		this.renderer = new GhostRenderer(client);
		// a ghost's duel ending: its crack or sparkle, by the sound and particle settings
		renderer.setEndingCues(feedback::playEndingCue);
		this.hub = new GhostHub(new GhostHub.Link()
		{
			@Override
			public boolean inParty()
			{
				return party.isInParty();
			}

			@Override
			public void send(PartyMessage message)
			{
				try
				{
					party.send(message);
				}
				catch (RuntimeException e)
				{
					// a party hiccup must never end the skate session
					log.debug("Failed to send party ghost message", e);
				}
			}
		}, GhostHub.seqSeed(System.currentTimeMillis()));
		this.customDesigns = customDesigns;
		this.partyDesigns = new PartyDesigns(executor, clientThread::invoke,
			id -> party.getMemberById(id) != null, PartyGhostService::bakeReceived, new PartyDesigns.Colours()
		{
			@Override
			public void register(String id, int[] low)
			{
				// ghosts are drawn at Normal detail only
				DesignColours.register(id, null, low);
			}

			@Override
			public void unregister(String id)
			{
				DesignColours.unregister(id);
				BakedBoardModel.forgetDesign(id);
			}
		}, hub::relook);
		hub.setMemberDesigns(partyDesigns);
	}

	/** Executor: a party member's picture baked at Normal detail, or null when the board isn't there. */
	private static int[] bakeReceived(DesignPart part, java.awt.image.BufferedImage picture)
	{
		BakedBoardGeometry.Mesh[] low = BakedBoardGeometry.sharedBoard(false);
		if (low == null)
		{
			return null;
		}
		for (BakedBoardGeometry.Mesh m : low)
		{
			if (m.part == part.index)
			{
				return SharedDesignImage.bake(picture, m, DesignLayout.bundled().of(part));
			}
		}
		return null;
	}

	/** Plugin start: sending may begin. */
	public void startUp()
	{
		hub.open();
	}

	/**
	 * Plugin shutdown, before the message types are unregistered: tells the party we stopped (from this thread,
	 * so it goes out while the types are still registered) and sends nothing afterwards.
	 */
	public void shutDown()
	{
		hub.close();
		clientThread.invoke(() ->
		{
			hub.clearGhosts();
			// party members' designs never outlive the plugin
			partyDesigns.clear();
			renderer.despawnAll();
		});
	}

	/**
	 * One local skate frame, client thread: shares the skater when allowed and draws the party's ghosts.
	 *
	 * @param stateBefore the skater's state before this frame's physics steps
	 * @param inPvpArea the real character or the skater is in a PvP area
	 * @param physicsDt seconds of physics steps this frame ran (0 when none)
	 */
	public void onSkateFrame(SkatePhysics physics, List<SkateEvent> events, List<TrickEvent> trickEvents,
		SkaterState stateBefore, boolean inPvpArea, float frameDt, float physicsDt)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		float now = clock.now();
		float turnRate = turnMeter.update(physics.getHeading(), physics.getFlipTrick(), physics.getFlipProgress(),
			physicsDt);
		bodyFlipMeter.update(physics.getBodyFlipAngle(), physics.getState() == SkaterState.AIRBORNE, physicsDt);
		float[] v = GhostCodec.velocity(physics.getSpeed(), physics.getTravelHeading());
		GhostFrame frame = new GhostFrame(client.getWorld(), wv.getPlane(),
			GhostCodec.toAbsolute(physics.getX(), wv.getBaseX()), GhostCodec.toAbsolute(physics.getY(), wv.getBaseY()),
			physics.getH(), physics.getHeading(), v[0], v[1], physics.getVerticalVelocity(), physics.getState(),
			sharedHold(physics), physics.getFlipTrick(), physics.getFlipProgress(), turnRate, physics.getBodyFlipAngle(),
			bodyFlipMeter.rate()).withCharge(physics.getPopCharge());
		int bits = GhostCodec.eventBits(events, trickEvents, stateBefore, physics.getState());
		if (bodyFlipMeter.directionChanged())
		{
			bits |= GhostCodec.EV_BODY_FLIP;
		}
		// just got back on: sent at once
		bits |= GhostCodec.swapBits(lastOffBoard, null);
		lastOffBoard = null;
		lastGait = FootBody.Gait.IDLE;
		lastKnockStage = null;
		share(wv, frame, bits, GhostCodec.eventTrick(trickEvents), inPvpArea, now, frameDt);
	}

	/**
	 * One frame knocked off the board, client thread: shares the body (its lowest point, facing and flight, the stage
	 * and the angle it comes to lie at; receivers time the tumble, lie-down and get-up themselves) and the board where
	 * it is, under the same rules and limits as skating, and draws the party's ghosts. A new stage goes out at once.
	 *
	 * @param k the knockdown as stepped (not blended between steps)
	 * @param vx the body's velocity, u/s
	 * @param events this frame's skate events (the bail on the frame it starts)
	 * @param stateBefore the skater's state before this frame's physics steps
	 * @param inPvpArea the real character or the skater is in a PvP area
	 * @param physicsDt seconds of steps this frame ran (0 when none)
	 */
	public void onKnockdownFrame(KnockdownPose k, float vx, float vy, boolean airborne, float lieAngle,
		List<SkateEvent> events, List<TrickEvent> trickEvents, SkaterState stateBefore, boolean inPvpArea,
		float frameDt, float physicsDt)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		float now = clock.now();
		turnMeter.update(k.facing, null, 0f, physicsDt);
		bodyFlipMeter.update(0f, false, physicsDt);
		int baseX = wv.getBaseX();
		int baseY = wv.getBaseY();
		GhostFrame frame = GhostFrame.knockdown(client.getWorld(), wv.getPlane(), GhostCodec.toAbsolute(k.bodyX, baseX),
			GhostCodec.toAbsolute(k.bodyY, baseY), k.bodyH, k.facing, vx, vy, airborne, k.stage, lieAngle,
			GhostCodec.toAbsolute(k.boardX, baseX), GhostCodec.toAbsolute(k.boardY, baseY), k.boardH, k.boardYaw);
		int bits = GhostCodec.eventBits(events, trickEvents, stateBefore, SkaterState.BAILED)
			| GhostCodec.swapBits(lastOffBoard, BoardState.DROPPED);
		lastOffBoard = BoardState.DROPPED;
		lastGait = FootBody.Gait.IDLE;
		if (k.stage != lastKnockStage)
		{
			bits |= GhostCodec.EV_KNOCK;
			lastKnockStage = k.stage;
		}
		share(wv, frame, bits, GhostCodec.eventTrick(trickEvents), inPvpArea, now, frameDt);
	}

	/**
	 * One local frame on foot, client thread: shares the walker (feet, heading, velocity, in the air or not) and its
	 * board, carried or where it lies, under the same rules and limits as skating, and draws the party's ghosts.
	 *
	 * @param pose the walker and board as stepped (not blended between steps)
	 * @param vx the walker's velocity, u/s
	 * @param events this frame's walker events
	 * @param inPvpArea the real character or the walker is in a PvP area
	 * @param physicsDt seconds of walker steps this frame ran (0 when none)
	 */
	public void onFootFrame(OffBoardPose pose, float vx, float vy, List<FootEvent> events, boolean inPvpArea,
		float frameDt, float physicsDt)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		float now = clock.now();
		float turnRate = turnMeter.update(pose.walkerHeading, null, 0f, physicsDt);
		bodyFlipMeter.update(0f, false, physicsDt);
		int baseX = wv.getBaseX();
		int baseY = wv.getBaseY();
		GhostFrame frame = GhostFrame.onFoot(client.getWorld(), wv.getPlane(),
			GhostCodec.toAbsolute(pose.walkerX, baseX), GhostCodec.toAbsolute(pose.walkerY, baseY), pose.walkerH,
			pose.walkerHeading, vx, vy, pose.airborne ? pose.verticalSpeed : 0f, pose.airborne, turnRate, pose.board,
			GhostCodec.toAbsolute(pose.boardX, baseX), GhostCodec.toAbsolute(pose.boardY, baseY), pose.boardH,
			pose.boardHeading);
		int bits = GhostCodec.footEventBits(events) | GhostCodec.swapBits(lastOffBoard, pose.board);
		lastOffBoard = pose.board;
		lastKnockStage = null;
		// starting or stopping goes out at once, so a ghost does not walk on past where the walker stopped
		FootBody.Gait gait = FootBody.gait(lastGait, pose.walkerSpeed, pose.airborne);
		if (gait != lastGait)
		{
			bits |= GhostCodec.EV_GAIT;
			lastGait = gait;
		}
		share(wv, frame, bits, null, inPvpArea, now, frameDt);
	}

	/**
	 * The hold sent to the party: the grab, manual or grind held, else a rolling grab (sent as a grab hold while
	 * ROLLING, the existing field, so the ghost crouches and grabs its board on the ground; it never makes a label,
	 * which needs a trick event).
	 */
	static Trick sharedHold(SkatePhysics physics)
	{
		Trick hold = physics.getActiveHold();
		return hold != null ? hold : physics.getGroundGrab();
	}

	private void share(WorldView wv, GhostFrame frame, int bits, Trick eventTrick, boolean inPvpArea, float now,
		float frameDt)
	{
		// a duel ending started since the last frame: it goes out with this one
		bits |= extraEvents;
		extraEvents = 0;
		// pruned first: whether anyone else is still skating decides how much we send
		hub.prune(now);
		// instance coordinates of two different instances can coincide, so nothing is shared from one
		sendBlocked = inPvpArea || wv.isInstance();
		hub.onLocalFrame(frame, bits, eventTrick, sendBlocked, config.shareWithParty(), now);
		boolean show = GhostVisibility.showGhosts(true, config.showPartySkaters(), inPvpArea, wv.isInstance());
		renderer.setBakedBoards(config.boardModel() == GielinorSkateConfig.BoardType.RUNESKATE);
		renderer.update(hub.ghosts(), this::memberName, show, now, frameDt, ground(wv.getPlane()));
	}

	/**
	 * A duel ending (GhostCodec.EV_TANTRUM or EV_CELEBRATE) started on the local skater: it goes out with the next
	 * shared frame, at once (as the shared frames' own events do, within the same rules and limits), with its time.
	 * Client thread.
	 */
	public void addEvents(int bits)
	{
		extraEvents |= bits;
	}

	/** The local board's designs, shown to party members (custom ones as their small pictures, while shared). */
	public void setLocalLook(BoardLook look)
	{
		hub.setLocalLook(look);
		if (look == null)
		{
			return;
		}
		for (DesignPart part : DesignPart.values())
		{
			BoardDesign d = look.get(part);
			hub.setLocalDesign(part, d.custom ? DesignShare.outgoing(d.id, d.name, customDesigns.sharedPicture(d.id))
				: null);
		}
	}

	/** Local skate mode ended, client thread. */
	public void onSkateEnd()
	{
		sendBlocked = false;
		renderer.setCollisionWorld(null, 0, 0, GhostPredictor.GRAVITY);
		collisionGround = null;
		turnMeter.reset();
		bodyFlipMeter.reset();
		lastOffBoard = null;
		lastGait = FootBody.Gait.IDLE;
		extraEvents = 0;
		lastKnockStage = null;
		hub.onLocalSkateEnd(clock.now());
		renderer.despawnAll();
	}

	/**
	 * A ghost frame threw, client thread: hide every ghost and tell the party we stopped sharing. Ghosts stay off
	 * for the rest of this skate session (the caller stops calling {@link #onSkateFrame}).
	 */
	public void onGhostFailure()
	{
		try
		{
			hub.onLocalSkateEnd(clock.now());
		}
		finally
		{
			renderer.despawnAll();
		}
	}

	/** World hop or logout: the ghosts' worlds and scenes no longer apply. Client thread. */
	public void onSceneLost()
	{
		hub.clearGhosts();
		renderer.despawnAll();
	}

	/** Client thread (PlayerDespawned is posted there). */
	@Subscribe
	public void onPlayerDespawned(PlayerDespawned e)
	{
		renderer.forgetPlayer(e.getPlayer());
	}

	GhostHub hub()
	{
		return hub;
	}

	private boolean isLocal(long memberId)
	{
		PartyMember local = party.getLocalMember();
		return local != null && local.getMemberId() == memberId;
	}

	@Subscribe
	public void onSkateGhostUpdate(SkateGhostUpdate m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> hub.onRemoteUpdate(id, m, clock.now(), ground(m.p)));
	}

	@Subscribe
	public void onSkateGhostStop(SkateGhostStop m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> hub.onRemoteStop(id));
	}

	@Subscribe
	public void onSkateDesignOffer(SkateDesignOffer m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> partyDesigns.onOffer(id, m, clock.now()));
	}

	@Subscribe
	public void onSkateDesignChunk(SkateDesignChunk m)
	{
		long id = m.getMemberId();
		if (isLocal(id))
		{
			return;
		}
		clientThread.invoke(() -> partyDesigns.onChunk(id, m, clock.now()));
	}

	@Subscribe
	public void onUserPart(UserPart e)
	{
		long id = e.getMemberId();
		clientThread.invoke(() ->
		{
			hub.onMemberLeft(id);
			partyDesigns.forgetMember(id);
		});
	}

	@Subscribe
	public void onPartyChanged(PartyChanged e)
	{
		clientThread.invoke(() ->
		{
			hub.onPartyChanged();
			// left (or changed) the party: its members' designs go
			partyDesigns.clear();
		});
	}

	/**
	 * The ground for a ghost on {@code plane}, or null when that is not this client's plane: the local skater's
	 * collision world (terrain plus platform and box tops, so a played-back ghost follows steps, stairs and ledges
	 * as the local skater would) while it covers this scene, else the scene's tile heights. Client thread.
	 */
	private GhostPredictor.Ground ground(int plane)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || wv.getPlane() != plane)
		{
			return null;
		}
		if (collisionGround != null && collisionBaseX == wv.getBaseX() && collisionBaseY == wv.getBaseY()
			&& collisionPlane == plane)
		{
			return collisionGround;
		}
		return GhostRenderer.sceneGround(client, wv);
	}

	/**
	 * The local skater's collision world for this skate session, built for the scene based at ({@code baseX},
	 * {@code baseY}) on {@code plane}, {@code sizeTiles} tiles across; null when there is none. Client thread.
	 */
	public void setCollisionWorld(CollisionWorld world, int baseX, int baseY, int plane, int sizeTiles)
	{
		collisionBaseX = baseX;
		collisionBaseY = baseY;
		collisionPlane = plane;
		collisionGround = world == null ? null : collisionGround(world, baseX, baseY, sizeTiles);
		renderer.setCollisionWorld(world, baseX, baseY, GhostPredictor.GRAVITY);
	}

	/** {@code world}'s ground at absolute coordinates; NaN off its grid (it would clamp to its edge). */
	static GhostPredictor.Ground collisionGround(CollisionWorld world, int baseX, int baseY, int sizeTiles)
	{
		float size = sizeTiles * 128f;
		return (x, y) ->
		{
			float lx = GhostCodec.toLocal(x, baseX);
			float ly = GhostCodec.toLocal(y, baseY);
			if (lx < 0f || ly < 0f || lx >= size || ly >= size)
			{
				return Float.NaN;
			}
			return world.groundHeight(lx, ly);
		};
	}

	/**
	 * The ::skateghosts lines: per party ghost, its distance, detail level, what its body is drawn as and the exact
	 * reason for any fallback. Client thread.
	 */
	public List<String> ghostStatusLines()
	{
		return renderer.statusLines(hub.ghosts(), this::memberName);
	}

	/** This frame's ghost labels, for the overlay. */
	public List<GhostLabel> getLabels()
	{
		return renderer.labels();
	}

	// ---- Skate Duel: its messages share this budget, and its opponents come from the ghosts

	/**
	 * Queues a duel message: it goes before any ghost update, within the shared per-second limit; {@code lastWord}:
	 * a duel's forfeit, the one message still sent from a PvP area or instance.
	 */
	public void sendDuel(PartyMessage message, boolean lastWord)
	{
		hub.sendDuel(message, lastWord);
	}

	/** Sends the waiting duel messages the limit allows now (also while not skating), as last told blocked or not. */
	public void flushDuel(float now)
	{
		hub.flushDuel(now);
	}

	/**
	 * Sends the waiting duel messages the limit allows now; {@code blocked} (a PvP area or instance): only a duel's
	 * one last word.
	 */
	public void flushDuel(float now, boolean blocked)
	{
		// once a frame (the duel tick), client thread: the design settings, then what may go out
		hub.setDesignSharing(config.shareWithParty() && config.shareCustomDesigns());
		partyDesigns.setShowOthers(config.showPartyCustomDesigns());
		partyDesigns.expire(now);
		hub.flushDuel(now, blocked);
	}

	/** Whether this client's updates say it takes duel challenges. */
	public void setDuelCapable(boolean capable)
	{
		hub.setDuelCapable(capable);
	}

	/** The latest skate frame was in a PvP area or instance (false when not skating). */
	public boolean isSendBlocked()
	{
		return sendBlocked;
	}

	/**
	 * Party members skating (a live ghost) on {@code world} whose ghosts advertise duel support, in member ID
	 * order. Client thread.
	 */
	public List<Long> duelCandidates(int world, float now)
	{
		List<Long> out = new ArrayList<>();
		for (Map.Entry<Long, GhostPredictor> e : hub.ghosts().entrySet())
		{
			GhostPredictor g = e.getValue();
			GhostState s = g.latest();
			if (g.duelVersion() >= GhostHub.DUEL_VERSION && !g.expired(now) && s != null && s.world == world
				&& !isLocal(e.getKey()))
			{
				out.add(e.getKey());
			}
		}
		out.sort(null);
		return out;
	}

	/** This client's party member ID, or 0 when not in a party. */
	public long localMemberId()
	{
		PartyMember local = party.getLocalMember();
		return local == null ? 0L : local.getMemberId();
	}

	public boolean inParty()
	{
		return party.isInParty();
	}

	/** A party member's display name, or null. */
	public String memberName(long memberId)
	{
		PartyMember m = party.getMemberById(memberId);
		return m == null ? null : m.getDisplayName();
	}
}
