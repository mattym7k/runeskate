package com.gielinorskate.input;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Forgiving recognizer for the right-mouse-drag "wind up, then flick" gesture used to trigger
 * tricks. Screen y grows downward. Pure, thread-safe and synchronized; replaces FlickDetector.
 *
 * <p>A stroke is measured from its turnaround point (the "extreme"): the bottom of the pull-down
 * while wound up, or, after a flick, the point where the hand turned back. Turning is measured
 * only along the flick stroke itself. After a flick the recognizer is re-armed: motion continuing
 * along the flick just extends it (one long flick is one gesture), and a re-flick counts only once
 * the hand has turned around.
 */
public final class GestureRecognizer
{
	/** Y displacement from the wind-up reference point that counts as "wound up". */
	public static final int WIND_UP_PX = 18;
	/** Distance from the wind-up extreme that completes a flick. */
	public static final int FLICK_PX = 30;
	/**
	 * A flick must reach FLICK_PX within this long of the hand LEAVING the extreme (the last sample
	 * within LEAVE_PX of it), so holding the wind-up has no time limit; the clock only starts once the
	 * hand moves off. 30 px in 130 ms is the speed gate (about 0.23 px/ms) that tells a flick from a
	 * slow re-centring drift.
	 */
	public static final long FLICK_WINDOW_MS = 130;
	/**
	 * The stroke must also be moving at least this fast (px/ms, over the last SPEED_SPAN_MS) as it
	 * crosses FLICK_PX. The 130 ms window alone still let a 40 px sideways re-centring drift over 200 ms
	 * fire a shove-it (it covers 6 -> 30 px in 74-121 ms); measured on jittered smoothstep paths the
	 * drift crosses at 0.08-0.45 px/ms while flicks cross at 0.58-1.47 px/ms (45-60 px in 60-100 ms)
	 * and a lazy 55 px / 150 ms ollie at 0.46-0.67 px/ms, so 0.5 sits between drift and flicks.
	 */
	public static final float FLICK_MIN_SPEED = 0.5f;
	public static final long SPEED_SPAN_MS = 24;
	private static final int HISTORY = 8;
	/** Samples within this distance of the extreme still count as "holding" it (hand jitter). */
	public static final int LEAVE_PX = 6;
	/**
	 * Flick sectors, in degrees off the vertical: within OLLIE_SECTOR_DEG it is straight up/down, past
	 * HORIZONTAL_SECTOR_DEG it is left/right, and in between a diagonal. A plain 45 degree split turned
	 * 16% of slightly-off ollie flicks into kickflips.
	 */
	public static final double OLLIE_SECTOR_DEG = 25;
	public static final double HORIZONTAL_SECTOR_DEG = 65;
	/** Minimum segment length counted towards turnDegrees. */
	public static final int TURN_SEGMENT_PX = 4;
	/** Window after a flick during which another flick needs no fresh wind-up. */
	public static final long REARM_WINDOW_MS = 500;
	/**
	 * The extreme only moves once the hand has gone more than this much further along the pull, so
	 * 1-2 px of hand jitter does not move the flick's start point.
	 */
	public static final int EXTREME_JITTER_PX = 3;
	/**
	 * A stroke whose last two segments turn by at least this much in total (same way) is still
	 * curving: its flick waits for the curve to finish so a 360 flip's full turn is counted.
	 * Alternating 1 px wobble on a straight 8 px-step flick turns about +-14 degrees per segment,
	 * which cancels over two segments; a 12-step half circle turns 15 + 15 = 30 degrees.
	 */
	public static final float CURVE_TURN_DEG = 16f;
	/**
	 * Segments longer than this are not "still curving": a 3 px/ms flick sampled every 8 ms moves
	 * 24 px per drag event, so a longer single jump means the stroke has already finished.
	 */
	public static final float CURVE_SEGMENT_MAX_PX = 24f;
	/** A curving flick still waiting fires once no drag event has arrived for this long. */
	public static final long STROKE_IDLE_MS = 30;
	/**
	 * Letting go while wound up after moving at least this fraction of the flick distance off the extreme (but
	 * less than all of it) is a "too short" near miss; less than that is just a crouch, with no hint.
	 */
	public static final float NEAR_MISS_SHORT_FRACTION = 0.33f;
	/** How much of the recent path the live visualizer gets, ms, and how many samples are kept for it. */
	public static final long LIVE_TRAIL_MS = 300;
	private static final int LIVE_POINTS = 64;

	private enum Phase
	{
		/** Not wound up; waiting for an 18 px pull from the base point. */
		IDLE,
		/** Wound up (crouching for a regular pull); the extreme is the bottom of the pull. */
		WOUND,
		/** Just flicked; still moving along the flick, which pushes the extreme forward. */
		OUTGOING,
		/** Re-armed and turned back; the extreme is the turnaround point a re-flick counts from. */
		RETURNING
	}

	private final Deque<Gesture> queue = new ArrayDeque<>();
	/** Live visualizer: where the button went down, the recent path (a ring), and the latest flick. */
	private float pressX;
	private float pressY;
	/** The recent path (for the stroke speed and the live visualizer). */
	private final Samples path = new Samples(LIVE_POINTS);
	private Direction firedDirection;
	private boolean firedNollie;
	private long firedMs;

	/** The latest near miss not yet polled (null when none). */
	private NearMiss nearMiss;
	/** This stroke already reported a near miss (or fired): at most one hint per attempt. */
	private boolean nearMissReported;
	/** Farthest the hand got from the wind-up extreme since the wind-up, px. */
	private float maxFromExtreme;


	private boolean active;
	private Phase phase = Phase.IDLE;
	private boolean nollie;

	// Reference point a fresh wind-up is measured from (the peak of the last flick while re-armed).
	private float baseX;
	private float baseY;

	/** Unit vector the current stroke is expected to flick along (screen coordinates). */
	private float axisX;
	private float axisY;

	private float extremeX;
	private float extremeY;
	/** Time of the last sample within LEAVE_PX of the extreme: the flick window runs from here. */
	private long leaveMs;

	/** While re-armed: the time after which an unused re-arm lapses. */
	private long rearmDeadlineMs;

	/** A flick that reached FLICK_PX while still curving waits here until the curve ends. */
	private boolean pending;
	private float pendingX;
	private float pendingY;

	// Turn tracking, reset whenever the extreme moves.
	private float turnX;
	private float turnY;
	private boolean hasTurnSegment;
	private double lastSegmentAngle;
	private float signedTurn;
	private float lastDiff;
	private float prevDiff;
	private int diffCount;
	private float lastSegmentLength;

	private float lastX;
	private float lastY;
	private long lastMs;

	/** WIND_UP_PX and FLICK_PX scaled by the flick sensitivity (see {@link #setSensitivity}). */
	private float windUpPx = WIND_UP_PX;
	private float flickPx = FLICK_PX;

	/**
	 * Flick sensitivity in percent (clamped to 70-150): the wind-up and flick distances are divided by
	 * percent / 100, so 150% needs 12 px / 20 px and 70% needs 26 px / 43 px. Timing is unchanged.
	 */
	/** True while the trick modifier (Shift) is held; stamped on a gesture as {@link Gesture#modified} when it fires. */
	private boolean modifier;

	/** Sets whether the trick modifier (Shift) is held. Only its state at the moment a flick fires matters. */
	public synchronized void setModifier(boolean held)
	{
		modifier = held;
	}

	public synchronized void setSensitivity(int percent)
	{
		float scale = Math.max(70, Math.min(150, percent)) / 100f;
		windUpPx = WIND_UP_PX / scale;
		flickPx = FLICK_PX / scale;
	}

	public synchronized void begin(int x, int y, long ms)
	{
		active = true;
		pressX = x;
		pressY = y;
		path.count = 0;
		path.add(x, y, ms);
		nearMissReported = false;
		lastX = x;
		lastY = y;
		lastMs = ms;
		idle(x, y, ms);
	}

	public synchronized void move(int x, int y, long ms)
	{
		if (!active)
			return;
		path.add(x, y, ms);

		if (rearmed() && !pending && ms > rearmDeadlineMs)
			idle(x, y, ms);

		switch (phase)
		{
			case IDLE:
				if (y - baseY >= windUpPx)
					windUp(x, y, ms, false);
				else if (baseY - y >= windUpPx)
					windUp(x, y, ms, true);
				else if (Math.abs(x - baseX) >= flickPx)
					// a sideways flick straight from rest: no pull down (or push up) first
					reportNearMiss(NearMiss.NO_WIND_UP);
				break;
			case OUTGOING:
				if (along(x, y) > along(extremeX, extremeY))
				{
					// still travelling with the flick: it is the same stroke, not a re-flick
					setExtreme(x, y, ms);
					baseX = x;
					baseY = y;
				}
				else if (along(extremeX, extremeY) - along(x, y) > EXTREME_JITTER_PX)
				{
					phase = Phase.RETURNING;
					setExtreme(x, y, ms);
				}
				windUpAgain(x, y, ms);
				break;
			case RETURNING:
				if (!pending && !windUpAgain(x, y, ms) && along(x, y) < along(extremeX, extremeY) - EXTREME_JITTER_PX)
					setExtreme(x, y, ms);
				break;
			case WOUND:
				if (!pending && along(x, y) < along(extremeX, extremeY) - EXTREME_JITTER_PX)
					setExtreme(x, y, ms);
				break;
		}

		if (phase == Phase.WOUND || phase == Phase.RETURNING)
		{
			trackTurn(x, y);
			double dist = Math.hypot(x - extremeX, y - extremeY);
			if (phase == Phase.WOUND)
				maxFromExtreme = Math.max(maxFromExtreme, (float) dist);
			if (dist <= LEAVE_PX)
				leaveMs = ms;
			boolean inWindow = ms - leaveMs <= FLICK_WINDOW_MS;
			boolean far = dist >= flickPx;
			if (phase == Phase.WOUND && !pending && !inWindow
				&& along(x, y) - along(extremeX, extremeY) >= windUpPx / 2f)
			{
				// a missed flick: the hand drifted back toward neutral too slowly, so let go of the crouch
				// (a fresh pull from here winds up again)
				reportNearMiss(NearMiss.TOO_SLOW);
				idle(x, y, ms);
			}
			// the stroke speed: from the newest sample at least SPEED_SPAN_MS older (or the oldest of the newest
			// HISTORY) to here
			else if (pending || (far && inWindow
				&& path.speed(x, y, ms, SPEED_SPAN_MS, HISTORY, Long.MIN_VALUE, Float.MAX_VALUE) >= FLICK_MIN_SPEED))
			{
				// a curving flick waits for the curve to end (or the window to close), moving on with the hand
				pending = true;
				if (far)
				{
					pendingX = x;
					pendingY = y;
				}
				if (!inWindow || !curving())
					fire(ms);
			}
		}

		lastX = x;
		lastY = y;
		lastMs = ms;
	}

	/**
	 * Lets time pass with no drag event (the mouse held still): fires a curving flick whose stroke
	 * has stopped and lapses an unused re-arm. Call with the same clock as the event times.
	 */
	public synchronized void tick(long nowMs)
	{
		if (!active)
			return;
		if (pending && (nowMs - lastMs >= STROKE_IDLE_MS || nowMs - leaveMs > FLICK_WINDOW_MS))
			fire(lastMs);
		if (rearmed() && nowMs > rearmDeadlineMs)
			idle(lastX, lastY, nowMs);
	}

	public synchronized void end()
	{
		if (active && phase == Phase.WOUND && !pending)
			// let go while still wound up: a flick that went far but slowly, or one too short to count
			reportNearMiss(maxFromExtreme >= flickPx ? NearMiss.TOO_SLOW
				: maxFromExtreme >= flickPx * NEAR_MISS_SHORT_FRACTION ? NearMiss.TOO_SHORT : null);
		if (active && pending)
			fire(lastMs);
		active = false;
		idle(lastX, lastY, lastMs);
	}

	/**
	 * Drops the stroke in progress with no flick and no near-miss hint (controller mode: a slow stick tilt turned
	 * out to be a manual, or a grab key went down).
	 */
	public synchronized void cancel()
	{
		active = false;
		pending = false;
		nearMiss = null;
		nearMissReported = true;
		idle(lastX, lastY, lastMs);
	}

	/** True while the flick button is held down (a stroke or wind-up in progress). */
	public synchronized boolean isStrokeActive()
	{
		return active;
	}

	/** True while a regular (pull-down) wind-up is held; false once it has flicked (re-armed). */
	public synchronized boolean isCrouching()
	{
		return active && phase == Phase.WOUND && !nollie;
	}

	/** True while any wind-up (regular or nollie) is held: the pop charges, with or without the crouch pose. */
	public synchronized boolean isCharging()
	{
		return active && phase == Phase.WOUND;
	}

	public synchronized Gesture poll()
	{
		return queue.pollFirst();
	}

	/**
	 * A copy of the stroke for the live flick visualizer, with the path of the last {@code trailMs} before
	 * {@code nowMs} (same clock as the event times).
	 */
	public synchronized LiveStroke live(long nowMs, long trailMs)
	{
		int keep = 0;
		for (int k = 0; k < path.count; k++)
		{
			if (nowMs - path.ms[path.ago(k)] > trailMs)
				break;
			keep++;
		}
		float[] tx = new float[keep];
		float[] ty = new float[keep];
		long[] tm = new long[keep];
		for (int k = 0; k < keep; k++)
		{
			// oldest first
			int i = path.ago(keep - 1 - k);
			tx[k] = path.x[i];
			ty[k] = path.y[i];
			tm[k] = path.ms[i];
		}
		boolean wound = active && phase == Phase.WOUND;
		return new LiveStroke(active, pressX, pressY, lastX, lastY, tx, ty, tm, wound, wound && nollie, extremeX,
			extremeY, firedDirection, firedNollie, firedMs, windUpPx + flickPx, nowMs);
	}

	/** The latest near miss since the last call, or null; at most one per stroke. */
	public synchronized NearMiss pollNearMiss()
	{
		NearMiss m = nearMiss;
		nearMiss = null;
		return m;
	}

	private void reportNearMiss(NearMiss m)
	{
		if (m != null && !nearMissReported)
		{
			nearMissReported = true;
			nearMiss = m;
		}
	}

	private boolean rearmed()
	{
		return phase == Phase.OUTGOING || phase == Phase.RETURNING;
	}

	/** While re-armed, a fresh 18 px pull past the flick's peak is a real wind-up (crouch, charge). */
	private boolean windUpAgain(float x, float y, long ms)
	{
		boolean pulled = nollie ? baseY - y >= windUpPx : y - baseY >= windUpPx;
		if (pulled)
			windUp(x, y, ms, nollie);
		return pulled;
	}

	private void fire(long ms)
	{
		float dx = pendingX - extremeX;
		float dy = pendingY - extremeY;
		Gesture fired = new Gesture(sector(dx, dy), nollie, Math.abs(signedTurn), modifier);
		firedDirection = fired.direction;
		firedNollie = nollie;
		firedMs = ms;
		queue.addLast(fired);
		// a stroke that made a trick gives no hint
		nearMissReported = true;
		pending = false;

		float len = (float) Math.hypot(dx, dy);
		if (len > 0f)
		{
			axisX = dx / len;
			axisY = dy / len;
		}
		phase = Phase.OUTGOING;
		setExtreme(pendingX, pendingY, ms);
		baseX = pendingX;
		baseY = pendingY;
		rearmDeadlineMs = ms + REARM_WINDOW_MS;
	}

	private void windUp(float x, float y, long ms, boolean asNollie)
	{
		nearMissReported = false;
		maxFromExtreme = 0f;
		phase = Phase.WOUND;
		nollie = asNollie;
		pending = false;
		// the flick is expected opposite the pull: up for a regular wind-up, down for a nollie
		axisX = 0f;
		axisY = asNollie ? 1f : -1f;
		setExtreme(x, y, ms);
	}

	private void idle(float x, float y, long ms)
	{
		phase = Phase.IDLE;
		nollie = false;
		pending = false;
		baseX = x;
		baseY = y;
		setExtreme(x, y, ms);
	}

	private void setExtreme(float x, float y, long ms)
	{
		extremeX = x;
		extremeY = y;
		leaveMs = ms;
		resetTurnTracking(x, y);
	}

	/** Progress of a point along the current flick axis. */
	private float along(float x, float y)
	{
		return x * axisX + y * axisY;
	}

	private void resetTurnTracking(float x, float y)
	{
		turnX = x;
		turnY = y;
		hasTurnSegment = false;
		signedTurn = 0f;
		lastDiff = 0f;
		prevDiff = 0f;
		diffCount = 0;
		lastSegmentLength = 0f;
	}

	/**
	 * Accumulates the signed turn between successive segments of at least TURN_SEGMENT_PX. The net
	 * (signed) turn is used, so side-to-side hand wobble cancels instead of adding up to a varial.
	 */
	private void trackTurn(float x, float y)
	{
		float dx = x - turnX;
		float dy = y - turnY;
		double len = Math.hypot(dx, dy);
		if (len < TURN_SEGMENT_PX)
			return;
		double angle = Math.atan2(dy, dx);
		if (hasTurnSegment)
		{
			double diff = Math.toDegrees(angle - lastSegmentAngle);
			diff = ((diff + 180) % 360 + 360) % 360 - 180;
			signedTurn += (float) diff;
			prevDiff = lastDiff;
			lastDiff = (float) diff;
			diffCount++;
		}
		lastSegmentAngle = angle;
		lastSegmentLength = (float) len;
		hasTurnSegment = true;
		turnX = x;
		turnY = y;
	}

	/** True while the newest segments still bend the same way (see {@link #CURVE_TURN_DEG}). */
	private boolean curving()
	{
		if (diffCount == 0 || lastSegmentLength > CURVE_SEGMENT_MAX_PX)
			return false;
		float recent = diffCount >= 2 ? lastDiff + prevDiff : 2 * lastDiff;
		return Math.abs(recent) >= CURVE_TURN_DEG;
	}

	/**
	 * Screen-space 8-way sector of (dx, dy); y grows downward. Up/down within OLLIE_SECTOR_DEG of the
	 * vertical, left/right beyond HORIZONTAL_SECTOR_DEG, a diagonal in between.
	 */
	static Direction sector(float dx, float dy)
	{
		// angle off the vertical, 0..90 degrees, in 45 degree steps (the directions go round that way from UP)
		double off = Math.toDegrees(Math.atan2(Math.abs(dx), Math.abs(dy)));
		int steps = off <= OLLIE_SECTOR_DEG ? 0 : off > HORIZONTAL_SECTOR_DEG ? 2 : 1;
		int i = dy <= 0 ? (dx >= 0 ? 8 - steps : steps) : (dx >= 0 ? 4 + steps : 4 - steps);
		return Direction.values()[i % 8];
	}
}
