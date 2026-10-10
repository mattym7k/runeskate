package com.gielinorskate.party;

import com.gielinorskate.progression.*;
import com.gielinorskate.tricks.Trick;
import java.util.*;
import net.runelite.client.party.messages.PartyMessage;

/**
 * The party-ghost logic without the client: decides what of the local skater goes to the party (through
 * {@link GhostSendPolicy}) and keeps one {@link GhostPredictor} per party member who is skating. The send side
 * may be called from the plugin's shutdown thread as well as the client thread, so every method is
 * synchronized (they are all short and uncontended). Times are seconds on one monotonic clock.
 */
final class GhostHub
{
	/** The party: in production RuneLite's PartyService, in tests a fake. */
	interface Link
	{
		boolean inParty();

		void send(PartyMessage message);
	}

	private final Link link;
	private final GhostSendPolicy policy;
	private final Map<Long, GhostPredictor> ghosts = new HashMap<>();
	private final Map<Long, GhostPredictor> ghostsView = Collections.unmodifiableMap(ghosts);

	/** The last update sent since sharing (re)started, or null. */
	private SkateGhostUpdate lastSent;
	/** Something was sent since the last stop: party members are showing a ghost of us. */
	private boolean live;
	private int pendingEvents;
	private Trick pendingTrick;
	/** The skater's recent positions and event times, for the timeline each update carries. */
	private final GhostTrail trail = new GhostTrail();
	private boolean closed;
	/** Seconds between updates carrying the grip and wheels (SkateGhostUpdate.gw) while they don't change. */
	static final float LOOK_REFRESH_SECONDS = 5f;

	private final BoardDesigns designs = BoardDesigns.bundled();
	/** The local board's designs: the deck goes in every update, the grip and wheels now and then. */
	private BoardLook localLook = BoardLook.defaults(designs);
	/** The grip and wheels changed (or sharing restarted) since they last went out. */
	private boolean lookPending = true;
	private float lookSentAt = Float.NEGATIVE_INFINITY;

	GhostHub(Link link, int seqSeed)
	{
		this.link = link;
		this.policy = new GhostSendPolicy(seqSeed);
	}

	/**
	 * A sequence start for a plugin run starting at {@code epochMillis}: 4 per second of wall time since 2023, so
	 * a restarted sender (which sends at most 2 a second) starts above every number it used before and its
	 * party members do not drop its updates as stale.
	 */
	static int seqSeed(long epochMillis)
	{
		// 2023-11-14 in epoch seconds: seeds count from here.
		long seconds = Math.max(0L, epochMillis / 1000L - 1_700_000_000L);
		// Seed growth per second of wall time. At least {@link GhostSendPolicy#MAX_PER_SECOND} (2); kept at the old
		// limit of 4 so a run after this change still starts above every number an earlier, faster run used.
		return (int) Math.min(Integer.MAX_VALUE / 2, seconds * 4);
	}

	/**
	 * One local skate frame. Full updates go out only while at least one other party member is skating (has a
	 * live ghost here, from their recent messages); otherwise only an announce every 5 s so they can find us.
	 *
	 * @param events GhostCodec.EV_* bits of this frame
	 * @param eventTrick the trick of this frame's TRICK event, or null
	 * @param inPvpArea the real character or the skater is in a PvP area (or anywhere else not to share)
	 * @param sharing the "Share my skater with my party" setting
	 */
	synchronized void onLocalFrame(GhostState frame, int events, Trick eventTrick, boolean inPvpArea,
		boolean sharing, float now)
	{
		if (closed)
			return;
		boolean inParty = link.inParty();
		if (inPvpArea || !sharing || !inParty)
		{
			stopSharing();
			if (live && !inParty)
				// left the party: nobody to tell (they see us part)
				live = false;
			else if (live && policy.hasToken(now))
				sendStop(now);
			return;
		}
		pendingEvents |= events;
		trail.record(frame, now);
		trail.noteEvents(events, now);
		if (eventTrick != null)
			pendingTrick = eventTrick;
		if (ghosts.isEmpty())
		{
			// nobody else is skating, so nobody draws our events: only the occasional "I'm skating" announce
			pendingEvents = 0;
			pendingTrick = null;
			if (!policy.shouldAnnounce(now))
				return;
		}
		SkateGhostUpdate update = GhostCodec.encode(frame, pendingEvents, pendingTrick);
		update.dk = GhostCodec.deckWire(localLook);
		if ((lookPending || now - lookSentAt >= LOOK_REFRESH_SECONDS) && GhostCodec.mayCarryLook(update))
			update.gw = GhostCodec.lookWire(localLook);
		// new designs are a change worth sending; their refresh alone is not
		boolean changed = !update.sameState(lastSent) || update.gw != null && lookPending;
		if (!ghosts.isEmpty() && !policy.shouldSend(now, changed, (pendingEvents & ~GhostCodec.PASSIVE_EVENTS) != 0))
			return;
		update.seq = policy.nextSeq();
		// the timeline in whatever room the update leaves under the size bound
		trail.fit(update, now);
		link.send(update);
		policy.recordSend(now);
		lastSent = update;
		trail.sent(now);
		if (update.gw != null)
		{
			lookPending = false;
			lookSentAt = now;
		}
		live = true;
		pendingEvents = 0;
		pendingTrick = null;
	}

	/**
	 * Skate mode ended at {@code now}: tell the party once. Not held back by the rate limit (it happens at most once
	 * per session, and an announce only goes out with a token left for its stop), but counted in it, so toggling
	 * skating quickly cannot go over it. {@code now} is NaN at plugin shutdown (not counted: nothing follows).
	 */
	synchronized void onLocalSkateEnd(float now)
	{
		if (live && link.inParty())
			sendStop(now);
		live = false;
		stopSharing();
	}

	private void stopSharing()
	{
		trail.clear();
		lastSent = null;
		pendingEvents = 0;
		pendingTrick = null;
		lookPending = true;
		policy.reset();
	}

	/** Plugin shutdown: the stop goes out now, then nothing more is sent until {@link #open}. */
	synchronized void close()
	{
		onLocalSkateEnd(Float.NaN);
		closed = true;
	}

	synchronized void open()
	{
		closed = false;
		policy.reset();
	}

	private void sendStop(float now)
	{
		link.send(new SkateGhostStop());
		if (!Float.isNaN(now))
			policy.recordSend(now);
		live = false;
	}

	/** The local board's designs, sent so party members draw them. */
	synchronized void setLocalLook(BoardLook look)
	{
		if (look != null && !look.equals(localLook))
		{
			localLook = look;
			lookPending = true;
		}
	}

	/** An update from another party member. */
	synchronized void onRemoteUpdate(long memberId, SkateGhostUpdate m, float now, GhostPredictor.Ground ground)
	{
		GhostPredictor predictor = ghosts.computeIfAbsent(memberId, id -> new GhostPredictor());
		if (predictor.accept(GhostCodec.decode(m), GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd), now,
			ground))
		{
			predictor.deckWire = m.dk;
			if (m.gw != null)
				predictor.lookWire = m.gw;
			predictor.relook(designs);
		}
	}

	/** The member stopped skating or left the party. */
	synchronized void onMemberLeft(long memberId)
	{
		ghosts.remove(memberId);
	}

	/** Party left or changed, world hop, shutdown. */
	synchronized void clearGhosts()
	{
		ghosts.clear();
	}

	/** Drops ghosts that have been silent too long. */
	synchronized void prune(float now)
	{
		ghosts.values().removeIf(g -> g.expired(now));
	}

	/** Read-only view of the ghosts by member ID; client thread only. */
	Map<Long, GhostPredictor> ghosts()
	{
		return ghostsView;
	}
}
