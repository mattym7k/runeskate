package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.FootEvent;
import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.tricks.TrickKind;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local skater state to and from the compact ints of {@link SkateGhostUpdate}. Positions, heights and
 * velocities round to whole local units (1/128 tile), the heading to 1/1000 rad and flip time to 1 ms.
 * Pure: no client dependency.
 */
public final class GhostCodec
{
	public static final int EV_POP = 1;
	public static final int EV_LAND = 1 << 1;
	public static final int EV_BAIL = 1 << 2;
	public static final int EV_RESET = 1 << 3;
	public static final int EV_GRIND_LOCK = 1 << 4;
	public static final int EV_GRIND_EXIT = 1 << 5;
	/** A trick: a pop, flip, upgrade, or a grab, manual or grind starting. */
	public static final int EV_TRICK = 1 << 6;
	/** A front / back flip started, stopped or reversed (BodyFlipMeter): sent at once, not with the snapshot. */
	public static final int EV_BODY_FLIP = 1 << 7;
	/** Got on or off the board, or dropped or picked it up: sent at once. Older versions ignore the bit. */
	public static final int EV_BOARD_SWAP = 1 << 8;
	/** On foot, started or stopped walking or running: sent at once so the ghost does not walk on past a stop. */
	public static final int EV_GAIT = 1 << 9;
	/** Knocked off: the knockdown started lying down or getting up (sent at once). Older versions ignore the bit. */
	public static final int EV_KNOCK = 1 << 10;
	/**
	 * A push: never sent at once (it rides along with the next update, its time in the timeline), so the ghost's
	 * push kick plays when the member pushed. Older versions ignore the bit (they guess pushes from speed).
	 */
	public static final int EV_PUSH = 1 << 11;
	/**
	 * The local skater lost a Skate Duel by a knockout and throws a tantrum (snapping the board), from the time the
	 * timeline gives it: receivers play the same tantrum on the ghost. Sent at once. Older versions ignore the bit.
	 */
	public static final int EV_TANTRUM = 1 << 12;
	/** The local skater won a Skate Duel and celebrates: receivers play it on the ghost. Older versions ignore it. */
	public static final int EV_CELEBRATE = 1 << 13;
	/** Events that do not make an update go out at once. */
	public static final int PASSIVE_EVENTS = EV_PUSH;
	/** The pop charge goes in this many steps (1..CHARGE_STEPS); below half a step it is not sent. */
	static final int CHARGE_STEPS = 9;
	private static final KnockdownPose.Stage[] KNOCK_STAGES = KnockdownPose.Stage.values();
	/** Added to a lying angle's quarter-turn count k on the wire (kd), so it is never negative. */
	private static final int KNOCK_K_OFFSET = 4;
	/**
	 * A dropped board further than this from its walker (absolute units, about a loaded scene's width), across or
	 * up, is not believed: the ghost is drawn carrying it.
	 */
	public static final float MAX_BOARD_DISTANCE = 104 * 128f;

	private static final Map<String, SkaterState> STATES = byName(SkaterState.values());
	private static final Map<String, Trick> TRICKS = byName(Trick.values());
	private static final Map<String, BoardState> BOARD_STATES = byName(BoardState.values());

	private GhostCodec()
	{
	}

	/**
	 * The update for {@code f} (seq left 0 for the sender to fill in). {@code tr} is the flip turning the board
	 * now; with none, a TRICK event's own trick goes there so the label can name it, unless that trick is a board
	 * flip (already finished by the time a rate-limited event goes out: it must not be animated again). Body flips
	 * have no board rotation of their own, so they go (their event comes on landing).
	 */
	public static SkateGhostUpdate encode(GhostFrame f, int events, Trick eventTrick)
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
		if (f.flipTrick != null)
		{
			m.tr = f.flipTrick.name();
			float progress = Math.max(0f, Math.min(1f, f.flipProgress));
			m.ft = Math.round(progress * f.flipTrick.duration * 1000f);
		}
		else if ((events & EV_TRICK) != 0 && eventTrick != null
			&& (eventTrick.kind != TrickKind.FLIP || eventTrick.duration <= 0f))
		{
			m.tr = eventTrick.name();
		}
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
				{
					m.kd = knockWire(f.knockStage, f.knockLie);
				}
			}
		}
		else if (f.state == SkaterState.ROLLING || f.state == SkaterState.MANUAL || f.state == SkaterState.GRINDING)
		{
			int c = Math.round(Math.max(0f, Math.min(1f, f.charge)) * CHARGE_STEPS);
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
		{
			return null;
		}
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
	public static GhostState decode(SkateGhostUpdate m)
	{
		SkaterState state = m.st == null ? null : STATES.get(m.st);
		if (state == null)
		{
			state = SkaterState.ROLLING;
		}
		BoardState offBoard = m.ob == null ? null : BOARD_STATES.get(m.ob);
		if (offBoard == null)
		{
			return new GhostState(m.w, m.p, m.x, m.y, m.h, Angles.wrap(m.hd / 1000f), m.vx, m.vy, m.vh, state,
				trick(m.hold), m.ev, trick(m.tr), Math.max(0, m.ft) / 1000f, m.seq, turnRate(m.tw),
				m.bf / 1000f, m.bw / 1000f).withBody(charge(m.cr), null, 0f);
		}
		float bx = 0f;
		float by = 0f;
		float bh = 0f;
		float bd = 0f;
		if (offBoard == BoardState.DROPPED)
		{
			if (m.bx == null || m.by == null || m.bh == null
				|| Math.hypot(m.bx - (double) m.x, m.by - (double) m.y) > MAX_BOARD_DISTANCE
				|| Math.abs(m.bh - (double) m.h) > MAX_BOARD_DISTANCE)
			{
				offBoard = BoardState.CARRIED;
			}
			else
			{
				bx = m.bx;
				by = m.by;
				bh = m.bh;
				bd = m.bd == null ? 0f : Angles.wrap(m.bd / 1000f);
			}
		}
		SkaterState walking = state == SkaterState.AIRBORNE ? SkaterState.AIRBORNE : SkaterState.ROLLING;
		KnockdownPose.Stage knock = offBoard == BoardState.DROPPED ? knockStage(m.kd) : null;
		if (knock != null)
		{
			// knocked off: flying (AIRBORNE) or down (BAILED); the receiver times the tumble itself
			return new GhostState(m.w, m.p, m.x, m.y, m.h, Angles.wrap(m.hd / 1000f), m.vx, m.vy, 0f,
				state == SkaterState.AIRBORNE ? SkaterState.AIRBORNE : SkaterState.BAILED, null, m.ev, null, 0f, m.seq,
				0f, 0f, 0f, offBoard, bx, by, bh, bd).withBody(0f, knock, knockLie(m.kd));
		}
		return new GhostState(m.w, m.p, m.x, m.y, m.h, Angles.wrap(m.hd / 1000f), m.vx, m.vy, m.vh, walking,
			null, m.ev, null, 0f, m.seq, turnRate(m.tw), 0f, 0f, offBoard, bx, by, bh, bd);
	}

	/**
	 * A received turn rate (rad/s * 1000) in rad/s, bounded to what a real turn can be
	 * ({@link TurnRateMeter#MAX_RATE}). Missing from older versions' messages, it is 0: the ghost moves straight.
	 */
	static float turnRate(int tw)
	{
		return Math.max(-TurnRateMeter.MAX_RATE, Math.min(TurnRateMeter.MAX_RATE, tw / 1000f));
	}

	/** The EV_* bits of an on-foot frame: a jump goes as a pop, a landing as a landing (a fall is not sent). */
	public static int footEventBits(List<FootEvent> events)
	{
		int bits = 0;
		for (FootEvent e : events)
		{
			if (e == FootEvent.JUMP)
			{
				bits |= EV_POP;
			}
			else if (e == FootEvent.LAND)
			{
				bits |= EV_LAND;
			}
		}
		return bits;
	}

	/** {@link #EV_BOARD_SWAP} when the board's state (null on the board) changed since the last frame, else 0. */
	public static int swapBits(BoardState before, BoardState after)
	{
		return before == after ? 0 : EV_BOARD_SWAP;
	}

	/** A custom design on the wire: this, then the 8 lowercase hex digits of its shared image's hash. */
	public static final String CUSTOM_REF = "C:";
	/** Hex digits of a custom design's hash in ghost updates. */
	public static final int REF_HASH_LENGTH = 8;
	/** Longest design name in an update: a shipped design's wire name or a custom reference. */
	static final int REF_MAX = Math.max(BoardDesigns.WIRE_MAX, CUSTOM_REF.length() + REF_HASH_LENGTH);
	/** Longest grip-and-wheels field a receiver reads; anything longer is junk. */
	static final int MAX_LOOK_WIRE = 2 * REF_MAX + 1;

	/** Sender: the hash (8 lowercase hex) of a custom design's shared image while it is shared, else null. */
	public interface CustomRefs
	{
		String hashOf(BoardDesign design);
	}

	/** Receiver: the member's complete custom design for {@code part} with this hash, or null (draw the default). */
	public interface CustomDesigns
	{
		BoardDesign resolve(DesignPart part, String hash);
	}

	/** The deck design as sent (dk) when custom designs are not shared: see {@link #deckWire(BoardLook, CustomRefs)}. */
	public static String deckWire(BoardLook look)
	{
		return deckWire(look, null);
	}

	/**
	 * The deck design as sent (dk): its wire name, or null (left out of the JSON) for the default deck. A custom deck
	 * goes as {@link #CUSTOM_REF} and its hash while {@code refs} has one (it is shared), else as the default.
	 */
	public static String deckWire(BoardLook look, CustomRefs refs)
	{
		if (look == null)
		{
			return null;
		}
		String w = wire(look.deck, refs);
		return w.equals(BoardDesigns.bundled().defaultFor(DesignPart.DECK).wireName()) ? null : w;
	}

	/** The grip and wheels designs as sent (gw) when custom designs are not shared: custom ones as the defaults. */
	public static String lookWire(BoardLook look)
	{
		return lookWire(look, null);
	}

	/** The grip and wheels designs as sent (gw): "GRIP.WHEELS", each a wire name or a custom reference. */
	public static String lookWire(BoardLook look, CustomRefs refs)
	{
		return wire(look.grip, refs) + "." + wire(look.wheels, refs);
	}

	/** One design's name on the wire: a shipped one's wire name; a custom one's reference, or its part's default. */
	private static String wire(BoardDesign d, CustomRefs refs)
	{
		if (!d.custom)
		{
			return d.wireName();
		}
		String hash = refs == null ? null : refs.hashOf(d);
		if (hash != null && isRefHash(hash))
		{
			return CUSTOM_REF + hash;
		}
		return BoardDesigns.bundled().defaultFor(d.part).wireName();
	}

	/** The hash of a custom reference ("C:" and 8 lowercase hex digits), or null when {@code wire} is not one. */
	public static String customHash(String wire)
	{
		if (wire == null || wire.length() != CUSTOM_REF.length() + REF_HASH_LENGTH || !wire.startsWith(CUSTOM_REF))
		{
			return null;
		}
		String h = wire.substring(CUSTOM_REF.length());
		return isRefHash(h) ? h : null;
	}

	private static boolean isRefHash(String h)
	{
		if (h.length() != REF_HASH_LENGTH)
		{
			return false;
		}
		for (int i = 0; i < h.length(); i++)
		{
			char c = h.charAt(i);
			if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f'))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * True if {@code m} may carry the grip and wheels: it names no trick (tr), places no dropped board (bx...) and
	 * has no pop charge (cr: a crouch is short, the designs wait for the next update), so even the busiest such
	 * update stays within the size of the busiest update without them.
	 */
	public static boolean mayCarryLook(SkateGhostUpdate m)
	{
		return m.tr == null && m.bx == null && m.cr == null;
	}

	/** {@link #decodeLook(String, String, BoardLook, BoardDesigns, CustomDesigns)} with no custom designs. */
	public static BoardLook decodeLook(String dk, String gw, BoardLook previous, BoardDesigns designs)
	{
		return decodeLook(dk, gw, previous, designs, null);
	}

	/**
	 * A party member's designs from an update: the deck from {@code dk} (every update), the grip and wheels from
	 * {@code gw} when it is there, else as before ({@code previous}; the defaults when null). Unknown or junk
	 * names are each part's default; a custom reference is what {@code custom} has for it (complete and wanted),
	 * else the part's default.
	 */
	public static BoardLook decodeLook(String dk, String gw, BoardLook previous, BoardDesigns designs,
		CustomDesigns custom)
	{
		BoardLook before = previous != null ? previous : BoardLook.defaults(designs);
		BoardLook out = before.with(part(DesignPart.DECK, dk, designs, custom));
		if (gw == null)
		{
			return out;
		}
		if (gw.length() > MAX_LOOK_WIRE)
		{
			return out.with(designs.defaultFor(DesignPart.GRIP)).with(designs.defaultFor(DesignPart.WHEELS));
		}
		int dot = gw.indexOf('.');
		String grip = dot < 0 ? gw : gw.substring(0, dot);
		String wheels = dot < 0 ? null : gw.substring(dot + 1);
		return out.with(part(DesignPart.GRIP, grip.isEmpty() ? null : grip, designs, custom))
			.with(part(DesignPart.WHEELS, wheels == null || wheels.isEmpty() ? null : wheels, designs, custom));
	}

	private static BoardDesign part(DesignPart part, String wire, BoardDesigns designs, CustomDesigns custom)
	{
		String hash = customHash(wire);
		if (hash == null)
		{
			return designs.fromWire(part, wire);
		}
		BoardDesign d = custom == null ? null : custom.resolve(part, hash);
		return d != null && d.part == part ? d : designs.defaultFor(part);
	}

	/** The trick named {@code name}, or null when none (or unknown). */
	private static Trick trick(String name)
	{
		return name == null ? null : TRICKS.get(name);
	}

	private static String name(Enum<?> e)
	{
		return e == null ? null : e.name();
	}

	private static <E extends Enum<E>> Map<String, E> byName(E[] values)
	{
		Map<String, E> map = new HashMap<>();
		for (E e : values)
		{
			map.put(e.name(), e);
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * The heading turn {@code t}'s own body turn has made at {@code progress} (a bigspin's 180 eased like the
	 * board, as SkatePhysics applies it); 0 for no trick.
	 */
	public static float bodySpin(Trick t, float progress)
	{
		return t == null || t.bodyYawTurns == 0f ? 0f : t.bodyYawTurns * Angles.TWO_PI * SkatePhysics.flipEase(progress);
	}

	/** Horizontal velocity {vx, vy} of a skater moving at |speed| along {@code travelHeading}. */
	public static float[] velocity(float speed, float travelHeading)
	{
		float s = Math.abs(speed);
		return new float[]{(float) Math.sin(travelHeading) * s, (float) Math.cos(travelHeading) * s};
	}

	/** The EV_* bits of one frame: its physics and trick events, plus locking onto or leaving a rail. */
	public static int eventBits(List<SkateEvent> events, List<TrickEvent> trickEvents, SkaterState before,
		SkaterState after)
	{
		int bits = 0;
		for (SkateEvent e : events)
		{
			switch (e)
			{
				case POP:
					bits |= EV_POP;
					break;
				case LAND:
					bits |= EV_LAND;
					break;
				case BAIL:
					bits |= EV_BAIL;
					break;
				case RESET:
					bits |= EV_RESET;
					break;
				case PUSH:
					bits |= EV_PUSH;
					break;
				default:
					break;
			}
		}
		if (eventTrick(trickEvents) != null)
		{
			bits |= EV_TRICK;
		}
		if (after == SkaterState.GRINDING && before != SkaterState.GRINDING)
		{
			bits |= EV_GRIND_LOCK;
		}
		else if (before == SkaterState.GRINDING && after != SkaterState.GRINDING)
		{
			bits |= EV_GRIND_EXIT;
		}
		return bits;
	}

	/** The latest TRICK or HOLD_START event's trick, or null. */
	public static Trick eventTrick(List<TrickEvent> trickEvents)
	{
		Trick latest = null;
		for (TrickEvent e : trickEvents)
		{
			if ((e.type == TrickEvent.Type.TRICK || e.type == TrickEvent.Type.HOLD_START) && e.trick != null)
			{
				latest = e.trick;
			}
		}
		return latest;
	}

	/** Absolute coordinate of a local one, in a scene whose base tile is {@code base}. */
	public static float toAbsolute(float local, int base)
	{
		return local + base * 128f;
	}

	/** Local coordinate of an absolute one, in a scene whose base tile is {@code base}. */
	public static float toLocal(float absolute, int base)
	{
		return absolute - base * 128f;
	}
}
