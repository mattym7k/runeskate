package com.gielinorskate.party;

import com.gielinorskate.physics.*;
import com.gielinorskate.progression.*;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.*;
import java.util.List;

/**
 * Local skater state to and from the compact ints of {@link SkateGhostUpdate}. Positions, heights and
 * velocities round to whole local units (1/128 tile), the heading to 1/1000 rad and flip time to 1 ms.
 * Pure: no client dependency.
 */
public final class GhostCodec
{
	static final int EV_POP = 1;
	static final int EV_LAND = 1 << 1;
	static final int EV_BAIL = 1 << 2;
	static final int EV_RESET = 1 << 3;
	static final int EV_GRIND_LOCK = 1 << 4;
	static final int EV_GRIND_EXIT = 1 << 5;
	/** A trick: a pop, flip, upgrade, or a grab, manual or grind starting. */
	static final int EV_TRICK = 1 << 6;
	/** A front / back flip started, stopped or reversed (BodyFlipMeter): sent at once, not with the snapshot. */
	static final int EV_BODY_FLIP = 1 << 7;
	/** Got on or off the board, or dropped or picked it up: sent at once. Older versions ignore the bit. */
	static final int EV_BOARD_SWAP = 1 << 8;
	/** On foot, started or stopped walking or running: sent at once so the ghost does not walk on past a stop. */
	static final int EV_GAIT = 1 << 9;
	/** Knocked off: the knockdown started lying down or getting up (sent at once). Older versions ignore the bit. */
	static final int EV_KNOCK = 1 << 10;
	/**
	 * A push: never sent at once (it rides along with the next update, its time in the timeline), so the ghost's
	 * push kick plays when the member pushed.
	 */
	static final int EV_PUSH = 1 << 11;
	// bits 12 and 13 are sent by other builds of the plugin: unknown here, so their events are ignored
	/** Events that do not make an update go out at once. */
	static final int PASSIVE_EVENTS = EV_PUSH;
	/** The pop charge goes in this many steps (1..CHARGE_STEPS); below half a step it is not sent. */
	static final int CHARGE_STEPS = 9;
	private static final KnockdownPose.Stage[] KNOCK_STAGES = KnockdownPose.Stage.values();
	/** Added to a lying angle's quarter-turn count k on the wire (kd), so it is never negative. */
	private static final int KNOCK_K_OFFSET = 4;
	/**
	 * A dropped board further than this from its walker (absolute units, about a loaded scene's width), across or
	 * up, is not believed: the ghost is drawn carrying it.
	 */
	static final float MAX_BOARD_DISTANCE = 104 * 128f;


	private GhostCodec()
	{
	}

	/**
	 * The update for {@code f} (seq left 0 for the sender to fill in). {@code tr} is the flip turning the board
	 * now; with none, a TRICK event's own trick goes there so the label can name it, unless that trick is a board
	 * flip (already finished by the time a rate-limited event goes out: it must not be animated again). Body flips
	 * have no board rotation of their own, so they go (their event comes on landing).
	 */
	static SkateGhostUpdate encode(GhostState f, int events, Trick eventTrick)
	{
		SkateGhostUpdate m = new SkateGhostUpdate();
		m.w = f.world;
		m.p = f.plane;
		m.x = Math.round(f.x);
		m.y = Math.round(f.y);
		m.h = Math.round(f.h);
		m.hd = Math.round(Angles.wrap(f.heading) * 1000f);
		m.tw = Math.round(f.turnRate * 1000f);
		m.bf = Math.round(f.bodyFlip * 1000f);
		m.bw = Math.round(f.bodyFlipRate * 1000f);
		m.vx = Math.round(f.vx);
		m.vy = Math.round(f.vy);
		m.vh = Math.round(f.vh);
		m.st = name(f.state);
		m.hold = name(f.hold);
		m.ev = events;
		if (f.trick != null)
		{
			m.tr = f.trick.name();
			m.ft = Math.round(clamp(f.flipTime, 0f, f.trick.duration) * 1000f);
		}
		else if ((events & EV_TRICK) != 0 && eventTrick != null
			&& (eventTrick.kind != TrickKind.FLIP || eventTrick.duration <= 0f))
			m.tr = eventTrick.name();
		if (f.offBoard != null)
		{
			m.ob = f.offBoard.name();
			if (f.offBoard == BoardState.DROPPED)
			{
				m.bx = Math.round(f.boardX);
				m.by = Math.round(f.boardY);
				m.bh = Math.round(f.boardH);
				m.bd = Math.round(Angles.wrap(f.boardHeading) * 1000f);
				if (f.knockStage != null)
					m.kd = knockWire(f.knockStage, f.knockLie);
			}
		}
		else if (f.state == SkaterState.ROLLING || f.state == SkaterState.MANUAL || f.state == SkaterState.GRINDING)
		{
			int c = Math.round(clamp(f.charge, 0f, 1f) * CHARGE_STEPS);
			m.cr = c > 0 ? c : null;
		}
		return m;
	}

	/** The pop charge (0..1) of a received {@code cr}: 0 when missing or junk. */
	static float charge(Integer cr)
	{
		return cr == null || cr <= 0 ? 0f : Math.min(1f, cr / (float) CHARGE_STEPS);
	}

	/**
	 * {@code kd} for a knockdown in {@code stage} lying at {@code lie}: the stage (1 tumbling, 2 lying, 3 getting up)
	 * plus 10 times the lying angle's odd quarter turn ((2k + 1) PI / 2, k clamped to -4..5) as k + 4.
	 */
	static int knockWire(KnockdownPose.Stage stage, float lie)
	{
		int k = Math.round((lie - Angles.PI / 2) / Angles.PI);
		k = Math.max(-KNOCK_K_OFFSET, Math.min(9 - KNOCK_K_OFFSET, k));
		return stage.ordinal() + 1 + 10 * (k + KNOCK_K_OFFSET);
	}

	/** The knockdown stage of a received {@code kd}, or null when missing or junk. */
	static KnockdownPose.Stage knockStage(Integer kd)
	{
		if (kd == null || kd < 0 || kd > 99)
			return null;
		int i = kd % 10 - 1;
		return i >= 0 && i < KNOCK_STAGES.length ? KNOCK_STAGES[i] : null;
	}

	/** The lying angle of a received {@code kd}. */
	static float knockLie(Integer kd)
	{
		int k = kd == null ? 0 : Math.max(0, Math.min(99, kd)) / 10 - KNOCK_K_OFFSET;
		return (2 * k + 1) * Angles.PI / 2;
	}

	/**
	 * Decodes {@code m}; unknown names (another plugin version) become ROLLING / null. Off the board an unknown
	 * board state means on the board (as from an older version); a walker is only ever standing or in the air, with
	 * no trick; a dropped board with no position, or implausibly far from its walker, is drawn carried.
	 */
	static GhostState decode(SkateGhostUpdate m)
	{
		SkaterState state = named(SkaterState.values(), m.st, SkaterState.ROLLING);
		float heading = Angles.wrap(m.hd / 1000f);
		BoardState offBoard = named(BoardState.values(), m.ob, null);
		if (offBoard == null)
			return new GhostState(m.w, m.p, m.x, m.y, m.h, heading, m.vx, m.vy, m.vh, state,
				named(Trick.values(), m.hold, null), m.ev, named(Trick.values(), m.tr, null), Math.max(0, m.ft) / 1000f,
				m.seq, turnRate(m.tw), m.bf / 1000f, m.bw / 1000f, null, 0f, 0f, 0f, 0f, charge(m.cr), null, 0f);
		boolean dropped = offBoard == BoardState.DROPPED && m.bx != null && m.by != null && m.bh != null
			&& Math.hypot(m.bx - (double) m.x, m.by - (double) m.y) <= MAX_BOARD_DISTANCE
			&& Math.abs(m.bh - (double) m.h) <= MAX_BOARD_DISTANCE;
		KnockdownPose.Stage knock = dropped ? knockStage(m.kd) : null;
		boolean airborne = state == SkaterState.AIRBORNE;
		// knocked off: flying (AIRBORNE) or down (BAILED), the receiver timing the tumble itself; else a walker
		return new GhostState(m.w, m.p, m.x, m.y, m.h, heading, m.vx, m.vy, knock == null ? m.vh : 0f,
			airborne ? state : knock != null ? SkaterState.BAILED : SkaterState.ROLLING, null, m.ev, null, 0f, m.seq,
			knock == null ? turnRate(m.tw) : 0f, 0f, 0f, dropped ? offBoard : BoardState.CARRIED, dropped ? m.bx : 0f,
			dropped ? m.by : 0f, dropped ? m.bh : 0f, dropped && m.bd != null ? Angles.wrap(m.bd / 1000f) : 0f, 0f,
			knock, knock == null ? 0f : knockLie(m.kd));
	}

	/**
	 * A received turn rate (rad/s * 1000) in rad/s, bounded to what a real turn can be
	 * ({@link TurnRateMeter#MAX_RATE}). Missing from older versions' messages, it is 0: the ghost moves straight.
	 */
	static float turnRate(int tw)
	{
		return clamp(tw / 1000f, -TurnRateMeter.MAX_RATE, TurnRateMeter.MAX_RATE);
	}

	/** The EV_* bits of an on-foot frame: a jump goes as a pop, a landing as a landing (a fall is not sent). */
	static int footEventBits(List<FootEvent> events)
	{
		int bits = 0;
		for (FootEvent e : events)
			bits |= e == FootEvent.JUMP ? EV_POP : e == FootEvent.LAND ? EV_LAND : 0;
		return bits;
	}

	/** {@link #EV_BOARD_SWAP} when the board's state (null on the board) changed since the last frame, else 0. */
	static int swapBits(BoardState before, BoardState after)
	{
		return before == after ? 0 : EV_BOARD_SWAP;
	}

	/**
	 * Longest grip-and-wheels field a receiver reads; anything longer is junk. Room for two 10-character names and the
	 * dot, so every name another version may send still fits (an unknown one draws the part's default).
	 */
	static final int MAX_LOOK_WIRE = 21;

	/** The deck design as sent (dk): its wire name, or null (left out of the JSON) for the default deck. */
	static String deckWire(BoardLook look)
	{
		if (look == null)
			return null;
		String w = look.deck.wireName();
		return w.equals(BoardDesigns.bundled().defaultFor(DesignPart.DECK).wireName()) ? null : w;
	}

	/** The grip and wheels designs as sent (gw): "GRIP.WHEELS", each a wire name. */
	static String lookWire(BoardLook look)
	{
		return look.grip.wireName() + "." + look.wheels.wireName();
	}

	/**
	 * True if {@code m} may carry the grip and wheels: it names no trick (tr), places no dropped board (bx...) and
	 * has no pop charge (cr: a crouch is short, the designs wait for the next update), so even the busiest such
	 * update stays within the size of the busiest update without them.
	 */
	static boolean mayCarryLook(SkateGhostUpdate m)
	{
		return m.tr == null && m.bx == null && m.cr == null;
	}

	/**
	 * A party member's designs from an update: the deck from {@code dk} (every update), the grip and wheels from
	 * {@code gw} when it is there, else as before ({@code previous}; the defaults when null). Unknown or junk
	 * names are each part's default.
	 */
	static BoardLook decodeLook(String dk, String gw, BoardLook previous, BoardDesigns designs)
	{
		BoardLook before = previous != null ? previous : BoardLook.defaults(designs);
		BoardLook out = before.with(designs.fromWire(DesignPart.DECK, dk));
		if (gw == null)
			return out;
		if (gw.length() > MAX_LOOK_WIRE)
			return out.with(designs.defaultFor(DesignPart.GRIP)).with(designs.defaultFor(DesignPart.WHEELS));
		int dot = gw.indexOf('.');
		String grip = dot < 0 ? gw : gw.substring(0, dot);
		String wheels = dot < 0 ? null : gw.substring(dot + 1);
		return out.with(designs.fromWire(DesignPart.GRIP, grip.isEmpty() ? null : grip))
			.with(designs.fromWire(DesignPart.WHEELS, wheels == null || wheels.isEmpty() ? null : wheels));
	}

	/** The value named {@code name} (an enum name on the wire), or {@code fallback} when none or unknown. */
	private static <E extends Enum<E>> E named(E[] values, String name, E fallback)
	{
		for (E e : values)
		{
			if (e.name().equals(name))
				return e;
		}
		return fallback;
	}

	private static String name(Enum<?> e)
	{
		return e == null ? null : e.name();
	}

	/**
	 * The heading turn {@code t}'s own body turn has made at {@code progress} (a bigspin's 180 eased like the
	 * board, as SkatePhysics applies it); 0 for no trick.
	 */
	static float bodySpin(Trick t, float progress)
	{
		return t == null || t.bodyYawTurns == 0f ? 0f
			: t.bodyYawTurns * Angles.TWO_PI * SkatePhysics.flipEase(progress);
	}

	/** Horizontal velocity {vx, vy} of a skater moving at |speed| along {@code travelHeading}. */
	static float[] velocity(float speed, float travelHeading)
	{
		float s = Math.abs(speed);
		return new float[]{(float) Math.sin(travelHeading) * s, (float) Math.cos(travelHeading) * s};
	}

	/** The EV_* bits of one frame: its physics and trick events, plus locking onto or leaving a rail. */
	static int eventBits(List<SkateEvent> events, List<TrickEvent> trickEvents, SkaterState before,
		SkaterState after)
	{
		int bits = 0;
		for (SkateEvent e : events)
			bits |= e == SkateEvent.POP ? EV_POP : e == SkateEvent.LAND ? EV_LAND : e == SkateEvent.BAIL ? EV_BAIL
				: e == SkateEvent.RESET ? EV_RESET : e == SkateEvent.PUSH ? EV_PUSH : 0;
		if (eventTrick(trickEvents) != null)
			bits |= EV_TRICK;
		if (after == SkaterState.GRINDING && before != SkaterState.GRINDING)
			bits |= EV_GRIND_LOCK;
		else if (before == SkaterState.GRINDING && after != SkaterState.GRINDING)
			bits |= EV_GRIND_EXIT;
		return bits;
	}

	/** The latest TRICK or HOLD_START event's trick, or null. */
	static Trick eventTrick(List<TrickEvent> trickEvents)
	{
		Trick latest = null;
		for (TrickEvent e : trickEvents)
		{
			if ((e.type == TrickEvent.Type.TRICK || e.type == TrickEvent.Type.HOLD_START) && e.trick != null)
				latest = e.trick;
		}
		return latest;
	}

	/** Local coordinate of an absolute one, in a scene whose base tile is {@code base}. */
	static float toLocal(float absolute, int base)
	{
		return absolute - base * 128f;
	}

	static float clamp(float v, float lo, float hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
