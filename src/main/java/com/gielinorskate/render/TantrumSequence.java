package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import net.runelite.api.gameval.AnimationID;

/**
 * The loser's tantrum after a Skate Duel knockout, as a function of the seconds since it started: grab the board in
 * both hands at the chest, stamp the feet with it (the game's own stamp-feet emote), heave it overhead, slam it down
 * in front, where it snaps in two ({@link SnappedBoard}), then a huff while the arms drop. The same timing drives the
 * local skater and every party ghost of them. Puppet model space throughout: x across the body (the right hand on
 * -x), y down with the soles at 0, z out of the back (the body faces -z). Pure.
 */
public final class TantrumSequence
{
	public enum Phase
	{
		GRAB, STOMP, LIFT, SLAM, HUFF, DONE
	}

	static final float GRAB = 0.3f;
	static final float STOMP = 0.9f;
	static final float LIFT = 0.4f;
	static final float SLAM = 0.18f;
	static final float HUFF = 0.55f;
	/** The board hits the ground and snaps. */
	public static final float SNAP_AT = GRAB + STOMP + LIFT + SLAM;
	/** The tantrum is over: on foot with a fresh board in hand, the controls back. */
	public static final float DURATION = SNAP_AT + HUFF;
	/** The wind-up's emote: the game's stamp-feet (the Stronghold of Security "Stamp" emote). */
	public static final int STOMP_ANIMATION = AnimationID.EMOTE_STAMPFEET;

	/** Where the hands hold the board: at the chest, overhead, and down on the ground in front. */
	static final float CHEST_Y = -105f;
	static final float CHEST_Z = -24f;
	static final float OVERHEAD_Y = -228f;
	static final float OVERHEAD_Z = -6f;
	static final float GROUND_Z = -62f;
	/** Half the distance between the hands (they hold the board either side of its middle). */
	static final float SPREAD = 17f;
	/** The board's middle is this far in front of the hands while held. */
	static final float BOARD_AHEAD = 6f;
	/** The stamping shakes the held board up and down this much, this often. */
	static final float SHAKE = 6f;
	static final float SHAKE_HZ = 5f;
	static final float SHAKE_PITCH = 0.08f;
	/** Upper body: hunched in the stamp, leaning back under the board, folded forward by the slam. */
	static final float STOMP_BEND = 0.12f;
	static final float LIFT_BEND = -0.12f;
	static final float SLAM_BEND = 0.55f;
	/** The arms let go of the (gone) board over this much of the huff. */
	static final float LET_GO = 0.35f;

	private TantrumSequence()
	{
	}

	public static Phase phase(float t)
	{
		if (!(t >= 0f))
		{
			return Phase.GRAB;
		}
		if (t < GRAB)
		{
			return Phase.GRAB;
		}
		if (t < GRAB + STOMP)
		{
			return Phase.STOMP;
		}
		if (t < GRAB + STOMP + LIFT)
		{
			return Phase.LIFT;
		}
		if (t < SNAP_AT)
		{
			return Phase.SLAM;
		}
		return t < DURATION ? Phase.HUFF : Phase.DONE;
	}

	/** 0..1 through the phase {@code t} is in (1 once done). */
	public static float progress(float t)
	{
		switch (phase(t))
		{
			case GRAB:
				return clamp01(t / GRAB);
			case STOMP:
				return clamp01((t - GRAB) / STOMP);
			case LIFT:
				return clamp01((t - GRAB - STOMP) / LIFT);
			case SLAM:
				return clamp01((t - GRAB - STOMP - LIFT) / SLAM);
			case HUFF:
				return clamp01((t - SNAP_AT) / HUFF);
			default:
				return 1f;
		}
	}

	/** The OSRS animation at {@code t}: the stamp-feet during the wind-up, else none (-1). */
	public static int animation(float t)
	{
		return phase(t) == Phase.STOMP ? STOMP_ANIMATION : -1;
	}

	/** The board is in the hands (not yet snapped). */
	public static boolean holding(float t)
	{
		return t < SNAP_AT;
	}

	/** 0..1: how far the board has come from where it was to the hands (the grab). */
	public static float grabBlend(float t)
	{
		return PushCycle.smoothstep(0f, GRAB, t);
	}

	/**
	 * The procedural body at {@code t}: both hands reaching for the board where it is held (the arms ease in over the
	 * grab and let go in the huff) and the upper body's fold. Everything else neutral.
	 */
	public static void writeBody(float t, BodyPose out)
	{
		out.neutral();
		float[] hold = new float[3];
		hands(t, hold);
		out.liftX = hold[0];
		out.liftY = hold[1];
		out.liftZ = hold[2];
		out.liftSpread = SPREAD;
		Phase ph = phase(t);
		float u = progress(t);
		switch (ph)
		{
			case GRAB:
				out.liftWeight = PushCycle.smoothstep(0f, 1f, u);
				out.torsoBend = STOMP_BEND * u;
				break;
			case STOMP:
				out.liftWeight = 1f;
				out.torsoBend = STOMP_BEND;
				break;
			case LIFT:
				out.liftWeight = 1f;
				out.torsoBend = STOMP_BEND + (LIFT_BEND - STOMP_BEND) * smooth(u);
				break;
			case SLAM:
				out.liftWeight = 1f;
				out.torsoBend = LIFT_BEND + (SLAM_BEND - LIFT_BEND) * u * u;
				break;
			case HUFF:
				float since = t - SNAP_AT;
				out.liftWeight = 1f - PushCycle.smoothstep(0f, LET_GO, since);
				out.torsoBend = SLAM_BEND * (1f - smooth(u));
				break;
			default:
				break;
		}
	}

	/** Where the hands hold the board at {@code t} (their middle), into {@code out} (x, y, z). */
	static void hands(float t, float[] out)
	{
		out[0] = 0f;
		switch (phase(t))
		{
			case GRAB:
				out[1] = CHEST_Y;
				out[2] = CHEST_Z;
				return;
			case STOMP:
				float s = t - GRAB;
				out[1] = CHEST_Y + SHAKE * (float) Math.sin(2 * Math.PI * SHAKE_HZ * s);
				out[2] = CHEST_Z;
				return;
			case LIFT:
				float u = smooth(progress(t));
				out[1] = CHEST_Y + (OVERHEAD_Y - CHEST_Y) * u;
				out[2] = CHEST_Z + (OVERHEAD_Z - CHEST_Z) * u;
				return;
			case SLAM:
				// faster and faster down
				float v = progress(t);
				v *= v;
				out[1] = OVERHEAD_Y + (groundY() - OVERHEAD_Y) * v;
				out[2] = OVERHEAD_Z + (GROUND_Z - OVERHEAD_Z) * v;
				return;
			default:
				// where they let go of it
				out[1] = groundY();
				out[2] = GROUND_Z;
		}
	}

	/** The hands' height when the board is on the ground: its held point, the middle of the board. */
	private static float groundY()
	{
		return CarryPose.HELD_Y;
	}

	/**
	 * The board's held point (the middle of the board, board-model y {@link CarryPose#HELD_Y}) at {@code t} in puppet
	 * space into {@code out} (x, y, z), and its pitch along its length (the stamping's shake) as {@code out[3]}. The
	 * board lies across the body (its length along x), deck up.
	 */
	public static void board(float t, float[] out)
	{
		hands(t, out);
		Phase ph = phase(t);
		// in front of the hands while held; on the ground at the slam's end
		float ahead = ph == Phase.SLAM ? BOARD_AHEAD * (1f - progress(t)) : ph == Phase.HUFF || ph == Phase.DONE ? 0f
			: BOARD_AHEAD;
		out[2] -= ahead;
		out[3] = ph == Phase.STOMP
			? SHAKE_PITCH * (float) Math.sin(2 * Math.PI * SHAKE_HZ * (t - GRAB) + 1.3) : 0f;
	}

	/**
	 * Where the board object goes for a body with its soles at ({@code x}, {@code y}, up-positive {@code h}) facing
	 * {@code heading}, holding the board's middle at puppet point {@code p} (x, y, z, pitch from {@link #board}):
	 * across the body, so its nose points to the body's left.
	 */
	public static void place(float x, float y, float h, float heading, float[] p, Placement out)
	{
		int bodyJau = Angles.toJau(heading);
		// the board's long axis (its model z) along the body's x: turned a quarter from the body
		int boardJau = (bodyJau - 512) & 2047;
		float pitch = p[3];
		float[] c = BoardPlacement.pose(new float[]{0f, CarryPose.HELD_Y, 0f}, 0f, pitch);
		double tb = boardJau * Math.PI / 1024.0;
		float cb = (float) Math.cos(tb);
		float sb = (float) Math.sin(tb);
		float dx = c[0] * cb + c[2] * sb;
		float dy = c[2] * cb - c[0] * sb;
		// RuneLite's model-to-local rotation (see MeshDeformer), for the held point in the body's space
		double th = bodyJau * Math.PI / 1024.0;
		float ch = (float) Math.cos(th);
		float sh = (float) Math.sin(th);
		float px = x + p[0] * ch + p[2] * sh;
		float py = y + p[2] * ch - p[0] * sh;
		float pz = -h + p[1];
		out.set(px - dx, py - dy, pz - c[1], boardJau, 0f, pitch);
	}

	/**
	 * The board's nose heading (0 north, clockwise) while held: a quarter turn from the body's {@code heading}, the
	 * way {@link #place} turns it. The halves fly apart along it.
	 */
	public static float boardHeading(float heading)
	{
		return Angles.wrap(heading - Angles.PI / 2);
	}

	/** Where the board breaks for that body: the ground point under its middle, local (x, y), into {@code out}. */
	public static void snapPoint(float x, float y, float heading, float[] out)
	{
		double th = Angles.toJau(heading) * Math.PI / 1024.0;
		float ch = (float) Math.cos(th);
		float sh = (float) Math.sin(th);
		out[0] = x + GROUND_Z * sh;
		out[1] = y + GROUND_Z * ch;
	}

	private static float smooth(float u)
	{
		return PushCycle.smoothstep(0f, 1f, u);
	}

	private static float clamp01(float v)
	{
		return v > 0f ? Math.min(1f, v) : 0f;
	}
}
