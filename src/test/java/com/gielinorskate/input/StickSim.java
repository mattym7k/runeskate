package com.gielinorskate.input;

/**
 * Test helper: an AntiMicroX right stick in "Mouse (Normal)" cursor mode. A held tilt moves the cursor at a
 * steady speed (px/ms) and a centred stick moves it not at all. The cursor is sampled every 5 ms (AntiMicroX's
 * mouse refresh), keeping the sub-pixel remainder, and a move event is only sent when the whole-pixel position
 * changes.
 */
final class StickSim
{
	interface Sink
	{
		void move(int x, int y, long ms);
	}

	static final long STEP_MS = 5;

	private final Sink sink;
	private double x;
	private double y;
	long ms;
	private int lastX;
	private int lastY;

	StickSim(Sink sink, int x, int y, long ms)
	{
		this.sink = sink;
		this.x = x;
		this.y = y;
		this.ms = ms;
		lastX = x;
		lastY = y;
	}

	/** Holds the tilt that moves the cursor at (vx, vy) px/ms for {@code duration} ms. */
	StickSim tilt(double vx, double vy, long duration)
	{
		for (long t = 0; t < duration; t += STEP_MS)
		{
			ms += STEP_MS;
			x += vx * STEP_MS;
			y += vy * STEP_MS;
			int ix = (int) Math.round(x);
			int iy = (int) Math.round(y);
			if (ix != lastX || iy != lastY)
			{
				lastX = ix;
				lastY = iy;
				sink.move(ix, iy, ms);
			}
		}
		return this;
	}

	/** The stick centred for {@code duration} ms: no events. */
	StickSim rest(long duration)
	{
		ms += duration;
		return this;
	}
}
