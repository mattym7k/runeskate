package com.gielinorskate.input;

import com.gielinorskate.tricks.Gesture;
import java.util.List;

/**
 * Read-only snapshot of the last completed right-mouse stroke, for the {@code ::skategesture} dev
 * overlay. Screen coordinates, same space as the {@link java.awt.event.MouseEvent} the recognizer was fed.
 */
public final class StrokeSnapshot
{
	/** Points sampled from the start of the wind-up through the flick, oldest first; {x, y} pairs. */
	public final List<float[]> path;
	/** The wind-up's turnaround point the flick was measured from. */
	public final float extremeX;
	public final float extremeY;
	/** The gesture this stroke produced. */
	public final Gesture gesture;
	/** Same clock as {@link java.awt.event.MouseEvent#getWhen()}, when the flick fired. */
	public final long firedAtMs;

	public StrokeSnapshot(List<float[]> path, float extremeX, float extremeY, Gesture gesture, long firedAtMs)
	{
		this.path = path;
		this.extremeX = extremeX;
		this.extremeY = extremeY;
		this.gesture = gesture;
		this.firedAtMs = firedAtMs;
	}
}
