package com.gielinorskate.party;

import com.gielinorskate.physics.*;
import com.gielinorskate.render.PoseSmoothing;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.util.Arrays;

/**
 * A party member's ghost played back on the member's own clock: every received position (the updates' own and the
 * positions batched into them, {@link GhostTrajectory}) is kept in time order, and the ghost is drawn at the
 * playback time, a delay behind the member's clock ({@link GhostClockSync#delay}), between the positions on either
 * side of it instead of guessing ahead of the last one:
 * <ul>
 * <li>position and heading along cubic Hermite curves through them with their velocities and turn rates (sent ones,
 * else from their neighbours), so neither position nor velocity jumps at a sample;</li>
 * <li>in the air, height along the gravity parabola through the two samples (split at the pop or landing when that
 * falls between them); on the ground, the height above the receiver's ground follows the samples and the ground
 * under the ghost is added back, so slopes, steps and stairs look right here;</li>
 * <li>events (pops, landings, tricks...) and flips happen when the playback time reaches when they happened.</li>
 * </ul>
 * The playback clock runs at the speed of ours, within a few percent: it catches up or falls back gently to the
 * target delay, slows down when it is about to run past the last sample, and only past the last sample does the ghost
 * dead-reckon (for {@link #MAX_EXTRAPOLATION}, then it settles to a stop). Whatever changes what is drawn at once (late or
 * reordered samples, a resumed stream) is blended out ({@link #BLEND_TAU}), never jumped, unless it is a teleport.
 * Pure; nothing is allocated per frame. The subclass ({@link GhostPredictor}) hears the events as they are played.
 */
abstract class GhostTimeline
{
	/**
	 * Past the last sample, dead reckoning lasts this long (one lost update), then the ghost settles to a stop
	 * (SETTLE_TIME).
	 */
	static final float MAX_EXTRAPOLATION = 0.5f;
	/** Past MAX_EXTRAPOLATION, playback (and the ghost with it) slows to a stop within this much more member time. */
	static final float SETTLE_TIME = 0.3f;
	/** Playback speeds toward the target delay by this much per second of error... */
	static final float GAIN = 0.5f;
	/**
	 * ...but never more than this much slower or faster than our clock while it is near the target (an error under
	 * MAX_RATE_CHANGE / GAIN = 0.12 s: the usual jitter), so no speed change shows...
	 */
	static final float MAX_RATE_CHANGE = 0.06f;
	/** ...and never more than this much faster catching up after the buffer ran dry (a slight fast-forward). */
	static final float MAX_CATCH_UP = 0.25f;
	/**
	 * About to run past the last sample of a moving ghost: playback slows to this, and once dead reckoning is over
	 * it slows to a stop with the ghost (SETTLE_TIME), so when updates come again it carries on from about where the
	 * ghost was drawn and catches up, instead of the ghost sliding there.
	 */
	static final float UNDERRUN_RATE = 0.9f;
	/** The playback speed changes by at most this much per second (outside a dry buffer's slowdown). */
	static final float RATE_SLEW = 0.6f;
	/** A sample closer than this (seconds) to one already kept is the same one. */
	static final double SAME_TIME = 0.008;
	/** Neighbours further apart than this (seconds) say nothing about a sample's velocity. */
	static final double MAX_TANGENT_SPAN = 1.0;
	/** Events this late when received still play from their start, at most this far in. */
	static final float LATE_EVENT = 0.1f;

	private static final int CAP = 64;
	private static final int KEYS = 32;
	private static final int EVENTS = 48;
	private static final int MARKS = 16;
	private static final SkaterState[] STATES = SkaterState.values();
	private static final int AIR = SkaterState.AIRBORNE.ordinal();
	private static final int ROLL = SkaterState.ROLLING.ordinal();
	private static final int MANUAL = SkaterState.MANUAL.ordinal();
	private static final int BAIL = SkaterState.BAILED.ordinal();

	// samples, in time order
	final double[] t = new double[CAP];
	private final float[] x = new float[CAP];
	private final float[] y = new float[CAP];
	private final float[] h = new float[CAP];
	/** Unwrapped against the sample before. */
	private final float[] hd = new float[CAP];
	private final float[] vx = new float[CAP];
	private final float[] vy = new float[CAP];
	private final float[] vh = new float[CAP];
	private final float[] turn = new float[CAP];
	/** Height above the receiver's ground on the ground; NaN when unknown or not on the ground. */
	private final float[] clear = new float[CAP];
	/** The sample is an update's own: its velocity was sent. */
	private final boolean[] exact = new boolean[CAP];
	/** Its turn rate was sent and is the heading's (no bigspin turning the body as well). */
	private final boolean[] exactTurn = new boolean[CAP];
	private final byte[] st = new byte[CAP];
	private final Object[] samples = {t, x, y, h, hd, vx, vy, vh, turn, clear, exact, exactTurn, st};
	int n;
	private int seg;

	// updates (keys), in time order
	private final double[] kt = new double[KEYS];
	private final GhostState[] ks = new GhostState[KEYS];
	private final Object[] keys = {kt, ks};
	private int kn;
	private GhostState current;

	// events waiting to be played, in time order
	private final double[] et = new double[EVENTS];
	private final int[] eb = new int[EVENTS];
	private final Trick[] etr = new Trick[EVENTS];
	private final boolean[] enl = new boolean[EVENTS];
	private final Object[] events = {et, eb, etr, enl};
	private int en;

	// pops and landings (a ring), splitting the height curve between samples
	private final double[] mt = new double[MARKS];
	private final int[] mb = new int[MARKS];
	private int mNext;
	private int mCount;

	GhostClockSync sync = new GhostClockSync();
	double tp;
	private boolean started;
	private float lastNow;
	float rate = 1f;
	/** The last advance ran past the last sample, or was held back from it. */
	boolean dry;
	private boolean evaluated;

	/**
	 * Corrections still blending out (x, y, h, heading) and their velocities: the corrections are springs, so drawn
	 * velocity never jumps either.
	 */
	private final float[] off = new float[4];
	private final float[] offV = new float[4];

	// the raw curve at a time (scratch of evalRaw)
	private float rx;
	private float ry;
	private float rh;
	private float rhd;
	private float rvx;
	private float rvy;
	private float rvh;
	private float rturn;
	private int rstate;

	// what is drawn now (after advance): heading wrapped to (-PI, PI], turn rate the curve's own (smooth through the
	// samples)
	float ox;
	float oy;
	float oh;
	float ohd;
	float ovx;
	float ovy;
	float ovh;
	float oturn;
	private int ostate;
	/** The ground last given. */
	GhostPredictor.Ground lastGround;

	/** EV_* {@code bits} happened; {@code at} is our clock's time they were played at. */
	abstract void event(int bits, Trick trick, boolean nollie, float at);

	/** The drawn update changed to {@code key}, which was played at {@code at} (our clock). */
	abstract void keyChanged(GhostState key, float at);

	/**
	 * Takes in update {@code s}, received at {@code now}, with its timeline {@code tj}, or none (an update whose
	 * timeline did not fit: placed by its arrival, as early as it can have been sent). Returns false, taking nothing
	 * in, for an update without a timeline before any with one.
	 */
	boolean receive(GhostState s, GhostTrajectory tj, float now, GhostPredictor.Ground ground)
	{
		if (tj == null && !sync.has())
			return false;
		advance(now, ground);
		boolean had = n > 0 && evaluated;
		if (kn > 0 && (ks[kn - 1].world != s.world || ks[kn - 1].plane != s.plane))
		{
			// another world or plane: nothing to blend from
			clearBuffers();
			had = false;
		}
		double ts;
		if (tj != null)
		{
			ts = sync.unwrap(tj.timeMs);
			if (!sync.observe(now, ts))
			{
				// the sender started again: a new clock
				sync = new GhostClockSync();
				clearBuffers();
				started = false;
				ts = sync.unwrap(tj.timeMs);
				sync.observe(now, ts);
			}
		}
		else
		{
			ts = now - sync.floor();
			if (kn > 0)
				// never before (nor at the same time as) the update before it
				ts = Math.max(ts, kt[kn - 1] + 2 * SAME_TIME);
		}
		insertKey(ts, s);
		boolean bigspin = s.trick != null && s.trick.bodyYawTurns != 0f && s.trick.kind == TrickKind.FLIP;
		insertSample(ts, s.x, s.y, s.h, s.heading, s.vx, s.vy, s.vh, s.turnRate, true, !bigspin, s.state, ground);
		for (int i = 0; tj != null && i < tj.count; i++)
			insertSample(ts - tj.ago[i], tj.x[i], tj.y[i], tj.h[i], tj.heading[i], 0f, 0f, 0f, 0f, false, false,
				tj.state[i], ground);
		for (int bit = 0; bit < GhostTrajectory.EVENT_BITS; bit++)
		{
			int b = 1 << bit;
			if ((s.events & b) == 0)
				continue;
			double at = ts - (tj == null || Float.isNaN(tj.eventAgo[bit]) ? 0f : tj.eventAgo[bit]);
			insertEvent(at, b, b == GhostCodec.EV_TRICK ? (s.trick != null ? s.trick : s.hold) : null,
				b == GhostCodec.EV_POP && PoseSmoothing.isNollie(s.trick));
			if (b == GhostCodec.EV_POP || b == GhostCodec.EV_LAND)
			{
				mt[mNext] = at;
				mb[mNext] = b;
				mNext = (mNext + 1) % MARKS;
				mCount = Math.min(MARKS, mCount + 1);
			}
		}
		if (!started)
		{
			started = true;
			tp = target(now);
			lastNow = now;
		}
		evalRaw(tp, ground);
		if (had)
			// what was drawn a moment ago stays drawn, moving as it was: the difference settles out
			rebase();
		output();
		evaluated = true;
		return true;
	}

	/**
	 * The corrections that keep what is drawn (o*) where it is and moving and turning as it was over the curve as now
	 * evaluated (r*), so neither the position nor the velocity jumps; a correction further than a teleport is dropped
	 * (the ghost snaps).
	 */
	private void rebase()
	{
		off[0] = ox - rx;
		off[1] = oy - ry;
		off[2] = oh - rh;
		off[3] = Angles.wrap(ohd - rhd);
		offV[0] = ovx - rvx * rate;
		offV[1] = ovy - rvy * rate;
		// height eases in from rest: a landing's stop is real, and must not carry the ghost through the ground
		offV[2] = 0f;
		offV[3] = oturn + offV[3] - rturn * rate;
		if (Math.hypot(off[0], off[1]) > GhostPredictor.SNAP_DISTANCE
			|| Math.abs(off[2]) > GhostPredictor.SNAP_DISTANCE)
			clearOffsets();
	}

	/**
	 * Moves the playback time on to our clock's {@code now} (once per {@code now}), plays the events it passes and
	 * works out what is drawn.
	 */
	void advance(float now, GhostPredictor.Ground ground)
	{
		if (n == 0 || !started || evaluated && now == lastNow)
			return;
		float dt = Math.max(0f, now - lastNow);
		lastNow = now;
		double target = target(now);
		// Further than this (seconds) from the target, the playback time is set to it (after a long silence).
		if (Math.abs(target - tp) > 3f)
		{
			// a long silence or a new clock: on to the target, what was drawn blending into it
			tp = target;
			evalRaw(tp, ground);
			rebase();
			rate = 1f;
			dry = false;
		}
		else
		{
			float err = (float) (target - tp);
			// near the target a few percent either way; well behind it (after a dry buffer) a slight fast-forward
			float fast = Math.max(MAX_RATE_CHANGE, Math.min(MAX_CATCH_UP, (err - MAX_RATE_CHANGE / GAIN) * GAIN));
			float desired = 1f + GhostCodec.clamp(err * GAIN, -MAX_RATE_CHANGE, fast);
			// the speed itself changes gently (a new delay target is a step), so the ghost's velocity never jumps
			float step = RATE_SLEW * dt;
			rate += GhostCodec.clamp(desired - rate, -step, step);
			float cap = dryRate();
			dry = cap < Float.MAX_VALUE;
			rate = Math.min(rate, cap);
			tp += rate * dt;
		}
		settle(dt);
		int played = 0;
		for (; played < en && et[played] <= tp; played++)
			event(eb[played], etr[played], enl[played], now - (float) Math.min(LATE_EVENT, tp - et[played]));
		move(events, played, 0, en - played);
		en -= played;
		int k = keyIndex(tp);
		if (kn > 0 && ks[k] != current)
		{
			current = ks[k];
			keyChanged(current, now - (float) Math.min(LATE_EVENT, Math.max(0.0, tp - kt[k])));
		}
		evalRaw(tp, ground);
		output();
		evaluated = true;
		prune();
	}

	/**
	 * The fastest playback may run past the last sample of a moving ghost: easing from our clock's speed down to
	 * UNDERRUN_RATE over the dead reckoning, then to a stop over SETTLE_TIME (so the ghost glides to a halt where its
	 * dead reckoning took it, and carries on from there when updates come again); unlimited otherwise.
	 */
	private float dryRate()
	{
		float past = n == 0 ? -1f : (float) (tp - t[n - 1]);
		if (past < 0f || !moving(n - 1))
			return Float.MAX_VALUE;
		if (past < MAX_EXTRAPOLATION)
			return 1f - (1f - UNDERRUN_RATE) * past / MAX_EXTRAPOLATION;
		return UNDERRUN_RATE * Math.max(0f, 1f - (past - MAX_EXTRAPOLATION) / SETTLE_TIME);
	}

	private double target(float now)
	{
		return now - sync.floor() - sync.delay();
	}

	/**
	 * {@code dt} seconds of the corrections settling: each a critically damped spring ({@link #BLEND_TAU}), solved
	 * exactly, so a correction starts at the velocity it was given and comes to rest without overshoot.
	 */
	private void settle(float dt)
	{
		// Seconds (time constant) a correction of what is drawn fades out over.
		float w = 1f / 0.12f;
		float e = (float) Math.exp(-w * dt);
		for (int k = 0; k < 4; k++)
		{
			float a = offV[k] + w * off[k];
			off[k] = (off[k] + a * dt) * e;
			offV[k] = (offV[k] - w * a * dt) * e;
		}
	}

	private void output()
	{
		ox = rx + off[0];
		oy = ry + off[1];
		oh = rh + off[2];
		ohd = Angles.wrap(rhd + off[3]);
		// as drawn on our clock: the curve's velocity at the playback speed, plus the corrections'
		ovx = rvx * rate + offV[0];
		ovy = rvy * rate + offV[1];
		ovh = rvh * rate + offV[2];
		oturn = rturn * rate;
		ostate = rstate;
	}

	private void clearOffsets()
	{
		Arrays.fill(off, 0f);
		Arrays.fill(offV, 0f);
	}

	private void clearBuffers()
	{
		n = 0;
		kn = 0;
		en = 0;
		mCount = 0;
		seg = 0;
		current = null;
		evaluated = false;
		clearOffsets();
	}

	private boolean moving(int i)
	{
		// A ghost moving slower than this (u/s) on the ground is standing: it can be held without slowing playback.
		return st[i] == AIR || Math.hypot(velX(i), velY(i)) > 20f;
	}

	/** Moves entries {@code from}.. of every array in {@code columns} to start at {@code to}. */
	private static void move(Object[] columns, int from, int to, int count)
	{
		for (Object c : columns)
			System.arraycopy(c, from, c, to, count);
	}

	private void prune()
	{
		// Played samples are kept this long (seconds) for the curves' tangents.
		double old = tp - 2f;
		int drop = 0;
		while (n - drop > 2 && t[drop + 1] < old)
			drop++;
		shiftSamples(drop);
		if (n > 0 && Math.abs(hd[0]) > 32 * Angles.TWO_PI)
		{
			// a long carve's unwrapped headings back near zero (whole turns), before floats lose their precision
			float turns = Math.round(hd[0] / Angles.TWO_PI) * Angles.TWO_PI;
			for (int k = 0; k < n; k++)
				hd[k] -= turns;
			rhd -= turns;
		}
		int kd = 0;
		while (kn - kd > 1 && kt[kd + 1] < old)
			kd++;
		move(keys, kd, 0, kn - kd);
		kn -= kd;
	}

	private void shiftSamples(int drop)
	{
		if (drop > 0)
		{
			n -= drop;
			move(samples, drop, 0, n);
			seg = 0;
		}
	}

	/** A sample in time order; its headings, and the ones after it, unwrapped: each the short way from the one before. */
	private void insertSample(double time, float px, float py, float ph, float heading, float pvx, float pvy,
		float pvh, float pturn, boolean isExact, boolean isExactTurn, SkaterState state, GhostPredictor.Ground ground)
	{
		int j = n;
		while (j > 0 && t[j - 1] > time)
			j--;
		// the same time as a kept one: the update's own sample is better than a batched one
		int same = j > 0 && time - t[j - 1] < SAME_TIME ? j - 1 : j < n && t[j] - time < SAME_TIME ? j : -1;
		if (same >= 0)
		{
			if (!isExact || exact[same])
				return;
			j = same;
		}
		else
		{
			if (n == CAP)
			{
				if (j == 0)
					return;
				shiftSamples(1);
				j--;
			}
			move(samples, j, j + 1, n - j);
			n++;
			seg = 0;
		}
		t[j] = time;
		x[j] = px;
		y[j] = py;
		h[j] = ph;
		float ref = j > 0 ? hd[j - 1] : j + 1 < n ? hd[j + 1] : heading;
		hd[j] = ref + Angles.wrap(heading - ref);
		vx[j] = pvx;
		vy[j] = pvy;
		vh[j] = pvh;
		turn[j] = pturn;
		exact[j] = isExact;
		exactTurn[j] = isExactTurn;
		st[j] = (byte) state.ordinal();
		float g = ground != null && (st[j] == ROLL || st[j] == MANUAL) ? ground.heightAt(px, py) : Float.NaN;
		clear[j] = ph - g;
		for (int k = j + 1; k < n; k++)
			hd[k] = hd[k - 1] + Angles.wrap(hd[k] - hd[k - 1]);
	}

	private void insertKey(double time, GhostState s)
	{
		int j = kn;
		while (j > 0 && kt[j - 1] > time)
			j--;
		if (j > 0 && time - kt[j - 1] < SAME_TIME || kn == KEYS && j == 0)
			return;
		if (kn == KEYS)
		{
			move(keys, 1, 0, --kn);
			j--;
		}
		move(keys, j, j + 1, kn - j);
		kt[j] = time;
		ks[j] = s;
		kn++;
	}

	private void insertEvent(double time, int bits, Trick trick, boolean nollie)
	{
		if (en == EVENTS)
			return;
		int j = en;
		while (j > 0 && et[j - 1] > time)
			j--;
		move(events, j, j + 1, en - j);
		et[j] = time;
		eb[j] = bits;
		etr[j] = trick;
		enl[j] = nollie;
		en++;
	}

	private int keyIndex(double time)
	{
		int k = kn - 1;
		while (k > 0 && kt[k] > time)
			k--;
		return Math.max(0, k);
	}

	/** The velocity-like slope of {@code a} at sample {@code i} from its neighbours (exact for a parabola). */
	private float tangent(float[] a, int i)
	{
		boolean left = i > 0 && t[i] - t[i - 1] <= MAX_TANGENT_SPAN && !teleport(i - 1);
		boolean right = i + 1 < n && t[i + 1] - t[i] <= MAX_TANGENT_SPAN && !teleport(i);
		double s0 = left ? (a[i] - a[i - 1]) / (t[i] - t[i - 1]) : 0;
		double s1 = right ? (a[i + 1] - a[i]) / (t[i + 1] - t[i]) : 0;
		if (left && right)
		{
			double h0 = t[i] - t[i - 1];
			double h1 = t[i + 1] - t[i];
			return (float) ((h1 * s0 + h0 * s1) / (h0 + h1));
		}
		return (float) (s0 + s1);
	}

	/** Samples i and i + 1 are too far apart for the time between them. */
	private boolean teleport(int i)
	{
		double d = Math.hypot(x[i + 1] - x[i], y[i + 1] - y[i]);
		// A step faster than this (u/s) and longer than SNAP_DISTANCE between two samples is a teleport.
		return d > GhostPredictor.SNAP_DISTANCE && d > 4000f * (t[i + 1] - t[i]);
	}

	private float velX(int i)
	{
		return exact[i] ? vx[i] : tangent(x, i);
	}

	private float velY(int i)
	{
		return exact[i] ? vy[i] : tangent(y, i);
	}

	private float turnAt(int i)
	{
		return st[i] == BAIL ? 0f : exactTurn[i] ? turn[i] : tangent(hd, i);
	}

	/** The segment [i, i + 1] holding {@code time} (t[0] <= time < t[n - 1]). */
	private int segment(double time)
	{
		int i = Math.max(0, Math.min(seg, n - 2));
		if (t[i] > time)
			i = 0;
		while (i + 1 < n - 1 && t[i + 1] <= time)
			i++;
		seg = i;
		return i;
	}

	/** The curve at {@code time} into the r* scratch. */
	private void evalRaw(double time, GhostPredictor.Ground ground)
	{
		lastGround = ground;
		if (n == 0)
			return;
		if (time > t[0] && time >= t[n - 1])
		{
			extrapolate(n - 1, (float) (time - t[n - 1]), ground);
			return;
		}
		int i = time <= t[0] ? 0 : segment(time);
		int j = i + 1;
		if (time <= t[0] || teleport(i))
		{
			// held at the sample
			rx = x[i];
			ry = y[i];
			rh = h[i];
			rhd = hd[i];
			rvx = 0f;
			rvy = 0f;
			rvh = 0f;
			rturn = 0f;
			rstate = st[i];
			return;
		}
		float dt = (float) (t[j] - t[i]);
		float u = (float) ((time - t[i]) / (t[j] - t[i]));
		float mx0 = velX(i) * dt;
		float mx1 = velX(j) * dt;
		float my0 = velY(i) * dt;
		float my1 = velY(j) * dt;
		rx = along(x, i, mx0, mx1, u);
		ry = along(y, i, my0, my1, u);
		rvx = slope(x, i, mx0, mx1, u) / dt;
		rvy = slope(y, i, my0, my1, u) / dt;
		float mh0 = turnAt(i) * dt;
		float mh1 = turnAt(j) * dt;
		rhd = along(hd, i, mh0, mh1, u);
		rturn = st[i] == BAIL ? 0f
			: GhostCodec.clamp(slope(hd, i, mh0, mh1, u) / dt, -TurnRateMeter.MAX_RATE, TurnRateMeter.MAX_RATE);
		rstate = st[i];
		boolean airI = st[i] == AIR;
		boolean airJ = st[j] == AIR;
		if (airI && airJ)
			parabola(t[i], h[i], t[j], h[j], time);
		else if (airI || airJ)
		{
			// landing between the two: the arc ends at the landing, on the ground there, then the ground; a pop: the
			// ground until it, then the arc from the ground there
			double at = crossing(airI ? i : j, airI ? j : i, airI ? 1 : -1);
			int g = airI ? j : i;
			if (airI ? time >= at : time < at)
				onGround(g, ground);
			else
			{
				float gh = h[g];
				if (ground != null && !Float.isNaN(clear[g]))
				{
					// the ground under the ghost then
					float uu = (float) ((at - t[i]) / dt);
					float g0 = ground.heightAt(hermite(x[i], mx0, x[j], mx1, uu), hermite(y[i], my0, y[j], my1, uu));
					gh = Float.isNaN(g0) ? h[g] : g0 + clear[g];
				}
				if (airI)
					parabola(t[i], h[i], at, gh, time);
				else
					parabola(at, gh, t[j], h[j], time);
			}
		}
		else
		{
			float g = ground != null && !Float.isNaN(clear[i]) && !Float.isNaN(clear[j]) ? ground.heightAt(rx, ry)
				: Float.NaN;
			if (!Float.isNaN(g))
			{
				// above the ground here, as far above it as the samples were above theirs
				rh = g + clear[i] + (clear[j] - clear[i]) * u;
				rvh = (float) ((h[j] - h[i]) / (t[j] - t[i]));
			}
			else
			{
				float m0 = heightSlope(i) * dt;
				float m1 = heightSlope(j) * dt;
				rh = along(h, i, m0, m1, u);
				rvh = slope(h, i, m0, m1, u) / dt;
			}
			return;
		}
		if (rstate == AIR && ground != null)
		{
			float g = ground.heightAt(rx, ry);
			if (!Float.isNaN(g) && rh < g)
				rh = g;
		}
	}

	private float heightSlope(int i)
	{
		return exact[i] && st[i] == AIR ? vh[i] : tangent(h, i);
	}

	/** Sample {@code i}'s height on the ground at (rx, ry): above the ground here as it was there, else as sent. */
	private void onGround(int i, GhostPredictor.Ground ground)
	{
		rh = h[i];
		rvh = 0f;
		rstate = st[i];
		float g = ground != null && !Float.isNaN(clear[i]) ? ground.heightAt(rx, ry) : Float.NaN;
		if (!Float.isNaN(g))
			rh = g + clear[i];
	}

	/**
	 * When the ghost crossed between air sample {@code a} and ground sample {@code g}: going forward in time
	 * ({@code dir} 1) the landing, back (-1) the pop. The event's own time, else where the arc through {@code a}
	 * meets {@code g}'s height (the update with the event was lost, or a roll off a ledge).
	 */
	private double crossing(int a, int g, int dir)
	{
		int bit = dir > 0 ? GhostCodec.EV_LAND : GhostCodec.EV_POP;
		double from = t[Math.min(a, g)];
		double to = t[Math.max(a, g)];
		double best = Double.NaN;
		for (int k = 0; k < mCount; k++)
		{
			if (mb[k] == bit && mt[k] >= from && mt[k] <= to && (Double.isNaN(best) || mt[k] > best))
				best = mt[k];
		}
		if (!Double.isNaN(best))
			return best;
		// the vertical speed at a, along the time direction
		float v = Float.NaN;
		int b = a - dir;
		if (exact[a])
			v = dir * vh[a];
		else if (b >= 0 && b < n && st[b] == AIR)
		{
			float span = (float) Math.abs(t[a] - t[b]);
			v = (h[a] - h[b]) / span - GhostPredictor.GRAVITY / 2f * span;
		}
		double gr = GhostPredictor.GRAVITY;
		double disc = (double) v * v + 2 * gr * (h[a] - h[g]);
		if (Float.isNaN(v) || disc < 0)
			return t[g];
		return Math.max(from, Math.min(to, t[a] + dir * (v + Math.sqrt(disc)) / gr));
	}

	/**
	 * {@code a} from sample i on to sample i + 1 along the Hermite curve with end slopes m0, m1 (per segment) at
	 * {@code u}: relative to a[i], as world coordinates are large and their float sums would round.
	 */
	private static float along(float[] a, int i, float m0, float m1, float u)
	{
		return a[i] + hermite(0f, m0, a[i + 1] - a[i], m1, u);
	}

	/** The rate of change of {@link #along} per segment. */
	private static float slope(float[] a, int i, float m0, float m1, float u)
	{
		float u2 = u * u;
		return (3 * u2 - 4 * u + 1) * m0 + (-6 * u2 + 6 * u) * (a[i + 1] - a[i]) + (3 * u2 - 2 * u) * m1;
	}

	private static float hermite(float p0, float m0, float p1, float m1, float u)
	{
		float u2 = u * u;
		float u3 = u2 * u;
		return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1;
	}

	/** The gravity parabola through (t0, h0) and (t1, h1) at {@code time}. */
	private void parabola(double t0, float h0, double t1, float h1, double time)
	{
		double span = Math.max(1e-4, t1 - t0);
		double s = Math.max(0.0, Math.min(span, time - t0));
		float g = GhostPredictor.GRAVITY;
		rh = (float) (h0 + (h1 - h0) * s / span + g / 2.0 * s * (span - s));
		rvh = (float) ((h1 - h0) / span + g / 2.0 * (span - 2 * s));
		rstate = AIR;
	}

	/** Dead reckoning from the last sample {@code i}, {@code past} seconds after it (at most as far as playback goes). */
	private void extrapolate(int i, float past, GhostPredictor.Ground ground)
	{
		// the playback itself slows to a stop past the dead reckoning (dryRate): the ghost goes where its clock goes
		float dt = Math.min(past, MAX_EXTRAPOLATION + SETTLE_TIME);
		float svx = velX(i);
		float svy = velY(i);
		float w = turnAt(i);
		int state = st[i];
		boolean carving = state == ROLL || state == MANUAL;
		rx = x[i] + svx * dt;
		ry = y[i] + svy * dt;
		rvx = svx;
		rvy = svy;
		if (carving && Math.abs(w) > 1e-3f)
		{
			// the velocity turns with the heading: along the arc, not its tangent
			double theta = Math.atan2(svx, svy);
			double speed = Math.hypot(svx, svy);
			double r = speed / w;
			rx = x[i] + (float) (r * (Math.cos(theta) - Math.cos(theta + w * dt)));
			ry = y[i] + (float) (r * (Math.sin(theta + w * dt) - Math.sin(theta)));
			rvx = (float) (Math.sin(theta + w * dt) * speed);
			rvy = (float) (Math.cos(theta + w * dt) * speed);
		}
		rhd = hd[i] + w * dt;
		rstate = state;
		float svh = heightSlope(i);
		rh = h[i] + svh * dt;
		rvh = svh;
		if (state == AIR)
		{
			rh -= GhostPredictor.GRAVITY / 2f * dt * dt;
			rvh -= GhostPredictor.GRAVITY * dt;
			float g = ground == null ? Float.NaN : ground.heightAt(rx, ry);
			if (!Float.isNaN(g) && rh < g)
			{
				rh = g;
				rvh = 0f;
			}
		}
		else if (carving)
			onGround(i, ground);
		rturn = GhostCodec.clamp(w, -TurnRateMeter.MAX_RATE, TurnRateMeter.MAX_RATE);
	}

	SkaterState state()
	{
		return STATES[ostate];
	}

	/** The update being played: the latest one sent at or before the playback time. */
	GhostState playing()
	{
		return current != null ? current : kn > 0 ? ks[0] : null;
	}

	/** The board flip playing at the playback time, or null. */
	Trick flipTrick()
	{
		int f = flipIndex();
		return f < 0 ? null : ks[f].trick;
	}

	/** Seconds into {@link #flipTrick} at the playback time. */
	float flipTime()
	{
		int f = flipIndex();
		return f < 0 ? 0f : (float) (tp - flipStart(f));
	}

	/** The update of the latest board flip started (on the member's clock) and still turning at the playback time. */
	private int flipIndex()
	{
		int best = -1;
		for (int k = 0; k < kn; k++)
		{
			Trick f = ks[k].trick;
			double start = flipStart(k);
			if (f != null && f.kind == TrickKind.FLIP && f.duration > 0f && start <= tp && tp < start + f.duration
				&& (best < 0 || start > flipStart(best)))
				best = k;
		}
		return best;
	}

	/** When update {@code k}'s flip started. */
	private double flipStart(int k)
	{
		return kt[k] - ks[k].flipTime;
	}

	/**
	 * The front / back flip angle at the playback time (raw, whole turns kept): between two updates in the air along
	 * the curve through both angles and rates, else on from the update being played.
	 */
	float bodyFlip()
	{
		int k = keyIndex(tp);
		GhostState a = ks[k];
		if (kn == 0 || a.state != SkaterState.AIRBORNE)
			return 0f;
		float since = (float) Math.max(0.0, tp - kt[k]);
		if (k + 1 < kn && ks[k + 1].state == SkaterState.AIRBORNE && kt[k] <= tp)
		{
			GhostState b = ks[k + 1];
			float span = (float) (kt[k + 1] - kt[k]);
			return hermite(a.bodyFlip, a.bodyFlipRate * span, b.bodyFlip, b.bodyFlipRate * span, since / span);
		}
		// A body flip goes on from the update being played for at most this long (one lost or rate-limited update).
		float dt = Math.min(1.2f, since);
		float angle = a.bodyFlip;
		float w = a.bodyFlipRate;
		if (Math.abs(w) >= BodyFlipMeter.HELD_RATE)
			angle += w * dt;
		else if (w != 0f)
		{
			float r = BodyFlip.residual(angle);
			angle -= Math.copySign(Math.min(Math.abs(r), Math.abs(w) * dt), r);
		}
		return angle;
	}
}
