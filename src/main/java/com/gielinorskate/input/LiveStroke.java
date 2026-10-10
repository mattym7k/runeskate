package com.gielinorskate.input;

import com.gielinorskate.tricks.Gesture.Direction;
import lombok.AllArgsConstructor;

/**
 * A read-only copy of the flick stroke in progress, for the HUD's live flick visualizer: where the mouse is
 * relative to where the flick button went down, the last moments of its path, the wind-up state and the latest
 * recognised flick. Copied under the recognizer's lock, so it can be read on any thread. Screen pixels, y down;
 * times on the input clock ({@link #nowMs} is "now" on it).
 */
@AllArgsConstructor
public final class LiveStroke
{
	/** The flick button is held. */
	public final boolean held;
	/** Where the button went down, and where the mouse is now. */
	public final float originX;
	public final float originY;
	public final float x;
	public final float y;
	/** The recent path, oldest first (the last {@link GestureRecognizer#LIVE_TRAIL_MS} or so). */
	public final float[] trailX;
	public final float[] trailY;
	public final long[] trailMs;
	/** Wound up (a pull down, or a push up for a nollie) and waiting for the flick. */
	public final boolean wound;
	public final boolean nollie;
	/** The wind-up's turnaround point; meaningful while {@link #wound}. */
	public final float extremeX;
	public final float extremeY;
	/** The latest flick (mouse or keyboard trick): its direction, nollie flag and time; null direction when none. */
	public final Direction firedDirection;
	public final boolean firedNollie;
	public final long firedMs;
	/** Wind-up plus flick distance at the current sensitivity: the ring's radius stands for this many pixels. */
	public final float reachPx;
	public final long nowMs;

	/** Just a fired flick (a keyboard trick or a button flip), for the visualizer's sector flash. */
	static LiveStroke fired(Direction direction, boolean nollie, long ms)
	{
		return new LiveStroke(false, 0, 0, 0, 0, new float[0], new float[0], new long[0], false, false, 0, 0, direction,
			nollie, ms, 1f, 0);
	}

	/** This stroke with a different latest flick (a keyboard trick that came after the last mouse flick). */
	public LiveStroke withFired(Direction direction, boolean nollie, long ms)
	{
		return new LiveStroke(held, originX, originY, x, y, trailX, trailY, trailMs, wound, this.nollie, extremeX,
			extremeY, direction, nollie, ms, reachPx, nowMs);
	}
}
