package com.gielinorskate.party;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.party.messages.PartyMessage;

/**
 * The party-ghost logic without the client: decides what of the local skater goes to the party (through
 * {@link GhostSendPolicy}) and keeps one {@link GhostPredictor} per party member who is skating. The send side
 * may be called from the plugin's shutdown thread as well as the client thread, so every method is
 * synchronized (they are all short and uncontended). Times are seconds on one monotonic clock.
 */
public final class GhostHub
{
	/** The party: in production RuneLite's PartyService, in tests a fake. */
	public interface Link
	{
		boolean inParty();

		void send(PartyMessage message);
	}

	/** 2023-11-14 in epoch seconds: seeds count from here. */
	private static final long SEED_EPOCH_SECONDS = 1_700_000_000L;
	/**
	 * Seed growth per second of wall time. At least {@link GhostSendPolicy#MAX_PER_SECOND} (2); kept at the old
	 * limit of 4 so a run after this change still starts above every number an earlier, faster run used.
	 */
	private static final int SEED_PER_SECOND = 4;

	/** Receiver: a party member's complete custom designs, by hash (see {@link GhostCodec.CustomDesigns}). */
	public interface MemberDesigns
	{
		/** Member {@code member}'s complete design for {@code part} with this hash, or null (draw the default). */
		BoardDesign resolve(long member, DesignPart part, String hash);
	}

	/** The Skate Duel version advertised in updates (SkateGhostUpdate.dv) while duels are allowed. */
	public static final int DUEL_VERSION = 1;

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
	public static final float LOOK_REFRESH_SECONDS = 5f;

	private final BoardDesigns designs = BoardDesigns.bundled();
	/** The local board's designs: the deck goes in every update, the grip and wheels now and then. */
	private BoardLook localLook = BoardLook.defaults(designs);
	/** The grip and wheels changed (or sharing restarted) since they last went out. */
	private boolean lookPending = true;
	private float lookSentAt = Float.NEGATIVE_INFINITY;
	/**
	 * Skate Duel messages waiting for the shared budget. They go before any ghost update and are never dropped
	 * for the budget (only when there is no party to send to).
	 */
	private final ArrayDeque<DuelOut> duelOut = new ArrayDeque<>();
	/**
	 * In a PvP area or instance (as the duel service last said, or a skate frame there): no duel message goes out
	 * but one last word (a duel's forfeit), as a ghost's stop does on entering one.
	 */
	private boolean duelBlocked;
	/** The one last word of this blocked stretch is out: nothing more until it ends. */
	private boolean lastWordSent;
	/** Updates advertise {@link #DUEL_VERSION}: this client takes duel challenges. */
	private boolean duelCapable;

	/** Our custom designs on their way to the party, below every other message. */
	private final DesignOutbox designOut = new DesignOutbox();
	/** "Share my skater" and "Share my custom designs" are both on. */
	private boolean shareDesigns;
	/** In a party at the last design flush (joining one sends our designs). */
	private boolean designsInParty;
	/** The latest skate frame shared the skater: updates (or announces) are going out. */
	private boolean skatingShared;
	/**
	 * With an audience: the latest skate frame's state differed from the last one sent (the skater is moving, so
	 * the next snapshot is due a tick after the last), or an update is held back for a duel message. Otherwise
	 * only the keep-alive is due.
	 */
	private boolean updateWaiting;
	/** When the latest design message went out: they go at most one per {@link GhostSendPolicy#WINDOW}. */
	private float designSentAt = Float.NEGATIVE_INFINITY;
	/** Party members' complete designs, or null for none. */
	private MemberDesigns memberDesigns;
	/** Each ghost's latest deck and grip-and-wheels fields {dk, gw}, so its look can be worked out again. */
	private final Map<Long, String[]> lookWires = new HashMap<>();

	public GhostHub(Link link, int seqSeed)
	{
		this.link = link;
		this.policy = new GhostSendPolicy(seqSeed);
	}

	/**
	 * A sequence start for a plugin run starting at {@code epochMillis}: 4 per second of wall time since 2023, so
	 * a restarted sender (which sends at most 2 a second) starts above every number it used before and its
	 * party members do not drop its updates as stale.
	 */
	public static int seqSeed(long epochMillis)
	{
		long seconds = Math.max(0L, epochMillis / 1000L - SEED_EPOCH_SECONDS);
		return (int) Math.min(Integer.MAX_VALUE / 2, seconds * SEED_PER_SECOND);
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
	public synchronized void onLocalFrame(GhostFrame frame, int events, Trick eventTrick, boolean inPvpArea,
		boolean sharing, float now)
	{
		if (closed)
		{
			return;
		}
		// duel messages first: a ghost update only gets a token none of them is waiting for
		flush(now, inPvpArea || duelBlocked);
		boolean inParty = link.inParty();
		updateWaiting = false;
		if (!GhostSendPolicy.allowed(inPvpArea, sharing, true, inParty))
		{
			skatingShared = false;
			trail.clear();
			pendingEvents = 0;
			pendingTrick = null;
			lastSent = null;
			lookPending = true;
			policy.reset();
			if (live && !inParty)
			{
				// left the party: nobody to tell (they see us part)
				live = false;
			}
			else if (live && policy.hasToken(now))
			{
				sendStop(now);
			}
			return;
		}
		skatingShared = true;
		pendingEvents |= events;
		trail.record(frame, now);
		trail.noteEvents(events, now);
		if (eventTrick != null)
		{
			pendingTrick = eventTrick;
		}
		if (ghosts.isEmpty())
		{
			// nobody else is skating, so nobody draws our events: only the occasional "I'm skating" announce
			pendingEvents = 0;
			pendingTrick = null;
			if (!policy.shouldAnnounce(now))
			{
				return;
			}
		}
		if (!duelOut.isEmpty())
		{
			// events wait (coalesced) for the next token after the duel messages
			updateWaiting = true;
			return;
		}
		SkateGhostUpdate update = GhostCodec.encode(frame, pendingEvents, pendingTrick);
		GhostCodec.CustomRefs refs = shareDesigns ? designOut::hashOf : null;
		update.dk = GhostCodec.deckWire(localLook, refs);
		update.dv = duelCapable ? DUEL_VERSION : null;
		if ((lookPending || now - lookSentAt >= LOOK_REFRESH_SECONDS) && GhostCodec.mayCarryLook(update))
		{
			update.gw = GhostCodec.lookWire(localLook, refs);
		}
		// new designs are a change worth sending; their refresh alone is not
		boolean changed = !update.sameState(lastSent) || update.gw != null && lookPending;
		updateWaiting = changed;
		if (!ghosts.isEmpty() && !policy.shouldSend(now, changed, (pendingEvents & ~GhostCodec.PASSIVE_EVENTS) != 0))
		{
			return;
		}
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
	public synchronized void onLocalSkateEnd(float now)
	{
		if (live && link.inParty())
		{
			sendStop(now);
		}
		live = false;
		skatingShared = false;
		trail.clear();
		lastSent = null;
		pendingEvents = 0;
		pendingTrick = null;
		lookPending = true;
		policy.reset();
	}

	/** Plugin shutdown: a duel's last word and the stop go out now, then nothing more is sent until {@link #open}. */
	public synchronized void close()
	{
		// a duel's last word (a forfeit) goes out now, while the message types are still registered; hits still
		// waiting are dropped, so the shutdown cannot burst over the per-second limit
		DuelOut last = null;
		for (DuelOut o : duelOut)
		{
			if (o.lastWord)
			{
				last = o;
			}
		}
		duelOut.clear();
		designOut.clearQueue();
		if (last != null && link.inParty() && !(duelBlocked && lastWordSent))
		{
			link.send(last.message);
		}
		onLocalSkateEnd(Float.NaN);
		closed = true;
	}

	/**
	 * Queues a Skate Duel message: it goes at the next {@link #flushDuel} (or skate frame) with a token free in the
	 * shared limit, before any ghost update. Nothing is queued while closed.
	 */
	public synchronized void sendDuel(PartyMessage message)
	{
		sendDuel(message, false);
	}

	/**
	 * Queues a Skate Duel message; {@code lastWord}: it closes this client's part of a duel (a forfeit), the one
	 * message that may still go out from a PvP area or instance.
	 */
	public synchronized void sendDuel(PartyMessage message, boolean lastWord)
	{
		if (!closed)
		{
			duelOut.add(new DuelOut(message, lastWord));
		}
	}

	/** {@link #flushDuel(float, boolean)} with the blocked state last given. */
	public synchronized void flushDuel(float now)
	{
		flush(now, duelBlocked);
		flushDesigns(now, duelBlocked);
	}

	/**
	 * Sends the waiting duel messages the per-second limit allows at {@code now}; drops them outside a party.
	 *
	 * @param blocked in a PvP area or instance: everything waiting is dropped but the latest last word, and only
	 * one last word goes out until this is false again
	 */
	public synchronized void flushDuel(float now, boolean blocked)
	{
		duelBlocked = blocked;
		flush(now, blocked);
		flushDesigns(now, blocked);
	}

	/**
	 * Sends one waiting design message if it can delay nothing due: sharing designs, in a party, not blocked (a PvP
	 * area or instance), no duel message waiting, a token of the per-second limit free, no other design message in
	 * the last {@link GhostSendPolicy#WINDOW} (so a design never holds both tokens), and no ghost update due before
	 * that token comes back ({@link #updateDueAt}). While skating with nobody else skating, not in the second before
	 * an announce is due either (an announce waits for every token). Design messages do not move the update timing
	 * ({@link GhostSendPolicy#recordSideSend}). An update that becomes due only later (the skater sets off, an
	 * event) may still wait for the design's token, at most {@link GhostSendPolicy#WINDOW}.
	 */
	private void flushDesigns(float now, boolean blocked)
	{
		if (closed || !shareDesigns)
		{
			return;
		}
		if (!link.inParty())
		{
			designOut.clearQueue();
			designsInParty = false;
			return;
		}
		if (!designsInParty)
		{
			// joined a party: the members get our designs
			designsInParty = true;
			designOut.queueAll();
		}
		if (blocked || !duelOut.isEmpty() || designOut.isEmpty() || policy.freeTokens(now) < 1
			|| now - designSentAt < GhostSendPolicy.WINDOW || updateDueAt(now) - now < GhostSendPolicy.WINDOW)
		{
			return;
		}
		if (skatingShared && ghosts.isEmpty()
			&& now - policy.lastSend() >= GhostSendPolicy.ANNOUNCE_INTERVAL - GhostSendPolicy.WINDOW)
		{
			return;
		}
		link.send(designOut.poll(now));
		policy.recordSideSend(now);
		designSentAt = now;
	}

	/**
	 * When the next ghost update is due as things stand: never when not skating (or with nobody else skating:
	 * announces are guarded on their own); with an audience, at once for a waiting event, at the next snapshot time
	 * for a changed state waiting, else at the keep-alive.
	 */
	private float updateDueAt(float now)
	{
		if (!skatingShared || ghosts.isEmpty())
		{
			return Float.POSITIVE_INFINITY;
		}
		float last = policy.lastSend();
		if ((pendingEvents & ~GhostCodec.PASSIVE_EVENTS) != 0)
		{
			return now;
		}
		if (updateWaiting)
		{
			return Math.max(now, last + GhostSendPolicy.SNAPSHOT_INTERVAL);
		}
		return last + GhostSendPolicy.KEEP_ALIVE;
	}

	/**
	 * Whether our custom designs go to the party ("Share my skater" and "Share my custom designs"). Turning it on
	 * sends them; off, nothing more of them goes and updates name the parts' defaults again.
	 */
	public synchronized void setDesignSharing(boolean on)
	{
		if (on == shareDesigns)
		{
			return;
		}
		shareDesigns = on;
		lookPending = true;
		if (on)
		{
			designOut.queueAll();
		}
		else
		{
			designOut.clearQueue();
		}
	}

	/** Our shared design for {@code part}: its picture's messages, or null (not custom, or no picture). */
	public synchronized void setLocalDesign(DesignPart part, DesignShare.Outgoing design)
	{
		if (designOut.set(part, design, shareDesigns && link.inParty()))
		{
			lookPending = true;
		}
	}

	/** Where party members' complete designs come from (null: none, every custom part draws its default). */
	public synchronized void setMemberDesigns(MemberDesigns designs)
	{
		memberDesigns = designs;
	}

	/** Works out every ghost's designs again (a member's design completed or was let go, or a setting changed). */
	public synchronized void relook()
	{
		for (Map.Entry<Long, GhostPredictor> e : ghosts.entrySet())
		{
			String[] w = lookWires.get(e.getKey());
			if (w != null)
			{
				GhostPredictor g = e.getValue();
				g.setLook(GhostCodec.decodeLook(w[0], w[1], g.look(), designs, resolver(e.getKey())));
			}
		}
	}

	private GhostCodec.CustomDesigns resolver(long member)
	{
		MemberDesigns md = memberDesigns;
		return md == null ? null : (part, hash) -> md.resolve(member, part, hash);
	}

	/** Design messages waiting to go out. */
	synchronized int designsQueued()
	{
		return designOut.queued();
	}

	private void flush(float now, boolean blocked)
	{
		if (!blocked)
		{
			lastWordSent = false;
		}
		if (closed || duelOut.isEmpty())
		{
			return;
		}
		if (!link.inParty())
		{
			duelOut.clear();
			return;
		}
		if (blocked)
		{
			DuelOut last = null;
			for (DuelOut o : duelOut)
			{
				if (o.lastWord)
				{
					last = o;
				}
			}
			duelOut.clear();
			if (last == null || lastWordSent)
			{
				return;
			}
			duelOut.add(last);
		}
		while (!duelOut.isEmpty() && policy.hasToken(now))
		{
			link.send(duelOut.poll().message);
			policy.recordSend(now);
			if (blocked)
			{
				lastWordSent = true;
			}
		}
	}

	/** Whether updates advertise duel support ("Allow duel challenges"). */
	public synchronized void setDuelCapable(boolean capable)
	{
		duelCapable = capable;
	}

	public synchronized void open()
	{
		closed = false;
		policy.reset();
	}

	/** A queued duel message. */
	private static final class DuelOut
	{
		final PartyMessage message;
		final boolean lastWord;

		DuelOut(PartyMessage message, boolean lastWord)
		{
			this.message = message;
			this.lastWord = lastWord;
		}
	}

	private void sendStop(float now)
	{
		link.send(new SkateGhostStop());
		if (!Float.isNaN(now))
		{
			policy.recordSend(now);
		}
		live = false;
	}

	/** The local board's designs, sent so party members draw them. */
	public synchronized void setLocalLook(BoardLook look)
	{
		if (look != null && !look.equals(localLook))
		{
			localLook = look;
			lookPending = true;
		}
	}

	/** An update from another party member. */
	public synchronized void onRemoteUpdate(long memberId, SkateGhostUpdate m, float now, GhostPredictor.Ground ground)
	{
		boolean appeared = !ghosts.containsKey(memberId);
		GhostPredictor predictor = ghosts.computeIfAbsent(memberId, id -> new GhostPredictor());
		if (predictor.accept(GhostCodec.decode(m), GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd), now,
			ground))
		{
			String[] w = lookWires.computeIfAbsent(memberId, id -> new String[2]);
			w[0] = m.dk;
			if (m.gw != null)
			{
				w[1] = m.gw;
			}
			predictor.setLook(GhostCodec.decodeLook(w[0], w[1], predictor.look(), designs, resolver(memberId)));
			predictor.setDuelVersion(m.dv == null ? 0 : m.dv);
		}
		if (appeared && shareDesigns && link.inParty())
		{
			// a member's ghost appeared here: ours probably just did there, so our designs go (again)
			designOut.memberAppeared(memberId, now);
		}
	}

	public synchronized void onRemoteStop(long memberId)
	{
		ghosts.remove(memberId);
		lookWires.remove(memberId);
	}

	public synchronized void onMemberLeft(long memberId)
	{
		ghosts.remove(memberId);
		lookWires.remove(memberId);
	}

	/** Party left or changed, world hop, shutdown. */
	public synchronized void clearGhosts()
	{
		ghosts.clear();
		lookWires.clear();
	}

	/** The party changed (joined, left, another): no ghosts, and every member is new to our designs. */
	public synchronized void onPartyChanged()
	{
		clearGhosts();
		designOut.forgetMembers();
		designOut.clearQueue();
		designsInParty = false;
	}

	/** Drops ghosts that have been silent too long. */
	public synchronized void prune(float now)
	{
		ghosts.values().removeIf(g -> g.expired(now));
		lookWires.keySet().retainAll(ghosts.keySet());
	}

	/** Read-only view of the ghosts by member ID; client thread only. */
	public Map<Long, GhostPredictor> ghosts()
	{
		return ghostsView;
	}
}
