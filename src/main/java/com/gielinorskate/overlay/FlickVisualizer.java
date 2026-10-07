package com.gielinorskate.overlay;

import com.gielinorskate.input.GestureRecognizer;
import com.gielinorskate.input.LiveStroke;
import com.gielinorskate.tricks.Gesture.Direction;

/**
 * Pure maths of the live flick visualizer inside the HUD ring: the mouse offset from where the flick button went
 * down, scaled so the ring's radius stands for a whole wind-up plus flick and clamped to the ring; a trail that
 * fades over {@link #TRAIL_MS}; and a wedge for the recognised flick direction that fades over {@link #FLASH_MS}.
 */
public final class FlickVisualizer
{
	static final long TRAIL_MS = GestureRecognizer.LIVE_TRAIL_MS;
	static final long FLASH_MS = 250;

	private FlickVisualizer()
	{
	}

	/**
	 * Ring coordinates (relative to its centre, px, y down) of a mouse offset (dx, dy): scaled by radius / reachPx
	 * and clamped to the radius, keeping the direction.
	 */
	static float[] toRing(float dx, float dy, float reachPx, float radius)
	{
		float scale = radius / Math.max(1f, reachPx);
		float x = dx * scale;
		float y = dy * scale;
		float len = (float) Math.hypot(x, y);
		if (len > radius)
		{
			x *= radius / len;
			y *= radius / len;
		}
		return new float[]{x, y};
	}

	/** Opacity of a trail point {@code ageMs} old: 1 when new, 0 at TRAIL_MS and older (or from the future). */
	static float trailAlpha(long ageMs)
	{
		if (ageMs < 0 || ageMs >= TRAIL_MS)
		{
			return ageMs < 0 ? 1f : 0f;
		}
		return 1f - ageMs / (float) TRAIL_MS;
	}

	/** Opacity of the direction wedge {@code ageMs} after a flick: fades from 1 to 0 over FLASH_MS. */
	static float flashAlpha(long ageMs)
	{
		if (ageMs < 0 || ageMs >= FLASH_MS)
		{
			return 0f;
		}
		return 1f - ageMs / (float) FLASH_MS;
	}

	/**
	 * The wedge of a flick direction as {start, extent} in degrees for {@link java.awt.geom.Arc2D} (0 = right,
	 * counter-clockwise on screen). Matches the recognizer's sectors: up and down are 25 degrees either side of
	 * vertical, left and right 25 either side of horizontal, the diagonals the 40 degrees between.
	 */
	static double[] sector(Direction d)
	{
		double up = GestureRecognizer.OLLIE_SECTOR_DEG;
		double side = 90 - GestureRecognizer.HORIZONTAL_SECTOR_DEG;
		double diag = 90 - up - side;
		switch (d)
		{
			case UP:
				return new double[]{90 - up, 2 * up};
			case UP_RIGHT:
				return new double[]{side, diag};
			case RIGHT:
				return new double[]{-side, 2 * side};
			case DOWN_RIGHT:
				return new double[]{-side - diag, diag};
			case DOWN:
				return new double[]{-90 - up, 2 * up};
			case DOWN_LEFT:
				return new double[]{180 + side, diag};
			case LEFT:
				return new double[]{180 - side, 2 * side};
			default:
				return new double[]{90 + up, diag};
		}
	}

	/** Whether there is anything to draw: the button is held, or a flick's wedge is still fading. */
	static boolean visible(LiveStroke s)
	{
		return s != null && (s.held || (s.firedDirection != null && flashAlpha(s.nowMs - s.firedMs) > 0f));
	}
}
