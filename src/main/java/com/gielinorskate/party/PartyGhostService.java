package com.gielinorskate.party;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.physics.*;
import com.gielinorskate.progression.*;
import com.gielinorskate.render.*;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.*;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
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
import net.runelite.client.party.messages.PartyMemberMessage;
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
/** The latest skate frame was somewhere nothing is shared from (PvP area, instance; false when not skating). */
@Getter
private volatile boolean sendBlocked;
/** The local skater's collision world as a ghost ground (absolute coordinates), and the scene it covers. */
private GhostPredictor.Ground collisionGround;
private int collisionBaseX;
private int collisionBaseY;
private int collisionPlane;
/** Events of our own (a duel ending) for the next shared frame, GhostCodec.EV_* bits. Client thread. */
private int extraEvents;

@Inject
PartyGhostService(Client client, ClientThread clientThread, PartyService party, GielinorSkateConfig config,
ScoreClock clock, SkateFeedback feedback)
{
this.client = client;
this.clientThread = clientThread;
this.party = party;
this.config = config;
this.clock = clock;
// a ghost's duel ending: its crack or sparkle, by the sound and particle settings
this.renderer = new GhostRenderer(client, feedback::playEndingCue);
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
return;
Trick flip = physics.getFlipTrick();
float turnRate = turnMeter.update(physics.getHeading(), flip, physics.getFlipProgress(), physicsDt);
bodyFlipMeter.update(physics.getBodyFlipAngle(), physics.getState() == SkaterState.AIRBORNE, physicsDt);
float[] v = GhostCodec.velocity(physics.getSpeed(), physics.getTravelHeading());
lastGait = FootBody.Gait.IDLE;
share(wv, new GhostState(client.getWorld(), wv.getPlane(), physics.getX() + wv.getBaseX() * 128f,
physics.getY() + wv.getBaseY() * 128f, physics.getH(), physics.getHeading(), v[0], v[1],
physics.getVerticalVelocity(), physics.getState(), sharedHold(physics), 0, flip,
flip == null ? 0f : physics.getFlipProgress() * flip.duration, 0, turnRate, physics.getBodyFlipAngle(),
bodyFlipMeter.rate(), null, 0f, 0f, 0f, 0f, physics.getPopCharge(), null, 0f),
GhostCodec.eventBits(events, trickEvents, stateBefore, physics.getState())
| (bodyFlipMeter.directionChanged() ? GhostCodec.EV_BODY_FLIP : 0), GhostCodec.eventTrick(trickEvents),
inPvpArea, frameDt);
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
return;
turnMeter.update(k.facing, null, 0f, physicsDt);
bodyFlipMeter.update(0f, false, physicsDt);
float baseX = wv.getBaseX() * 128f;
float baseY = wv.getBaseY() * 128f;
lastGait = FootBody.Gait.IDLE;
// no vertical speed, angle or progress goes: the update stays small
share(wv, new GhostState(client.getWorld(), wv.getPlane(), k.bodyX + baseX, k.bodyY + baseY, k.bodyH, k.facing,
vx, vy, 0f, airborne ? SkaterState.AIRBORNE : SkaterState.BAILED, null, 0, null, 0f, 0, 0f, 0f, 0f,
BoardState.DROPPED, k.boardX + baseX, k.boardY + baseY, k.boardH, k.boardYaw, 0f, k.stage, lieAngle),
GhostCodec.eventBits(events, trickEvents, stateBefore, SkaterState.BAILED),
GhostCodec.eventTrick(trickEvents), inPvpArea, frameDt);
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
return;
float turnRate = turnMeter.update(pose.walkerHeading, null, 0f, physicsDt);
bodyFlipMeter.update(0f, false, physicsDt);
float baseX = wv.getBaseX() * 128f;
float baseY = wv.getBaseY() * 128f;
// starting or stopping goes out at once, so a ghost does not walk on past where the walker stopped
FootBody.Gait gait = FootBody.gait(lastGait, pose.walkerSpeed, pose.airborne);
int bits = GhostCodec.footEventBits(events) | (gait != lastGait ? GhostCodec.EV_GAIT : 0);
lastGait = gait;
share(wv, new GhostState(client.getWorld(), wv.getPlane(), pose.walkerX + baseX, pose.walkerY + baseY,
pose.walkerH, pose.walkerHeading, vx, vy, pose.airborne ? pose.verticalSpeed : 0f,
pose.airborne ? SkaterState.AIRBORNE : SkaterState.ROLLING, null, 0, null, 0f, 0, turnRate, 0f, 0f,
pose.board, pose.boardX + baseX, pose.boardY + baseY, pose.boardH, pose.boardHeading, 0f, null, 0f), bits,
null, inPvpArea, frameDt);
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

/** Shares {@code frame} (local coordinates made absolute) under the rules and limits, and draws the ghosts. */
private void share(WorldView wv, GhostState frame, int bits, Trick eventTrick, boolean inPvpArea, float frameDt)
{
float now = clock.now();
// getting on or off (or dropping or picking up the board), a new knockdown stage and a duel ending started
// since the last frame go out at once, with this frame
bits |= GhostCodec.swapBits(lastOffBoard, frame.offBoard) | extraEvents
| (frame.knockStage != null && frame.knockStage != lastKnockStage ? GhostCodec.EV_KNOCK : 0);
lastOffBoard = frame.offBoard;
lastKnockStage = frame.knockStage;
extraEvents = 0;
// pruned first: whether anyone else is still skating decides how much we send
hub.prune(now);
// instance coordinates of two different instances can coincide, so nothing is shared from one
sendBlocked = inPvpArea || wv.isInstance();
hub.onLocalFrame(frame, bits, eventTrick, sendBlocked, config.shareWithParty(), now);
boolean show = GhostVisibility.showGhosts(true, config.showPartySkaters(), inPvpArea, wv.isInstance());
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

/** The local board's designs, shown to party members. */
public void setLocalLook(BoardLook look)
{
hub.setLocalLook(look);
}

/** Local skate mode ended, client thread. */
public void onSkateEnd()
{
sendBlocked = false;
renderer.setCollisionWorld(null, 0, 0);
collisionGround = null;
turnMeter.reset();
bodyFlipMeter.reset();
lastOffBoard = null;
lastGait = FootBody.Gait.IDLE;
extraEvents = 0;
lastKnockStage = null;
onGhostFailure();
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

/** Runs {@code action} on the client thread for a message from another member (our own are ignored). */
private void fromOther(PartyMemberMessage m, LongConsumer action)
{
long id = m.getMemberId();
if (id != localMemberId())
clientThread.invoke(() -> action.accept(id));
}

@Subscribe
public void onSkateGhostUpdate(SkateGhostUpdate m)
{
fromOther(m, id -> hub.onRemoteUpdate(id, m, clock.now(), ground(m.p)));
}

@Subscribe
public void onSkateGhostStop(SkateGhostStop m)
{
fromOther(m, hub::onMemberLeft);
}

@Subscribe
public void onUserPart(UserPart e)
{
long id = e.getMemberId();
clientThread.invoke(() -> hub.onMemberLeft(id));
}

@Subscribe
public void onPartyChanged(PartyChanged e)
{
clientThread.invoke(hub::clearGhosts);
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
return null;
return collisionGround != null && collisionBaseX == wv.getBaseX() && collisionBaseY == wv.getBaseY()
&& collisionPlane == plane ? collisionGround : GhostRenderer.sceneGround(client, wv);
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
renderer.setCollisionWorld(world, baseX, baseY);
}

/** {@code world}'s ground at absolute coordinates; NaN off its grid (it would clamp to its edge). */
static GhostPredictor.Ground collisionGround(CollisionWorld world, int baseX, int baseY, int sizeTiles)
{
return GhostRenderer.localGround(baseX, baseY, sizeTiles, sizeTiles, world::groundHeight);
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

/**
* Sends the waiting duel messages the limit allows now; {@code blocked} (a PvP area or instance): only a duel's
* one last word.
*/
public void flushDuel(float now, boolean blocked)
{
hub.flushDuel(now, blocked);
}

/** Whether this client's updates say it takes duel challenges. */
public void setDuelCapable(boolean capable)
{
hub.setDuelCapable(capable);
}

/**
* Party members skating (a live ghost) on {@code world} whose ghosts advertise duel support, in member ID
* order. Client thread.
*/
public List<Long> duelCandidates(int world, float now)
{
long local = localMemberId();
return hub.ghosts().entrySet().stream().filter(e ->
{
GhostPredictor g = e.getValue();
return g.duelVersion() >= GhostHub.DUEL_VERSION && !g.expired(now) && g.latest().world == world
&& e.getKey() != local;
}).map(Map.Entry::getKey).sorted().collect(Collectors.toList());
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
