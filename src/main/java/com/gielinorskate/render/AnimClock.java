package com.gielinorskate.render;

import java.util.Arrays;

/**
 * Plays a classic (frame-based) OSRS animation on our own copy of a model: which frame is showing after each
 * render frame at some multiple of the game's pace, looping, or the frame a progress through it falls on. Frame
 * lengths are client ticks (20 ms). Pure; no allocation.
 */
public final class AnimClock
{
	/** Client ticks per second. */
	static final float TICKS_PER_SECOND = 50f;

	private int frame;
	private float ticks;

	/** Back to the first frame. */
	public void reset()
	{
		frame = 0;
		ticks = 0f;
	}

	/**
	 * Advances {@code dt} seconds at {@code rate} times the game's pace, looping; returns the frame now, or -1 when
	 * there are no frames. A long hitch skips at most one cycle and forgets the rest.
	 */
	public int loop(int[] lengths, float rate, float dt)
	{
		int n = lengths == null ? 0 : lengths.length;
		if (n == 0)
		{
			reset();
			return -1;
		}
		if (frame >= n)
		{
			reset();
		}
		if (dt > 0f && rate > 0f)
		{
			ticks += rate * dt * TICKS_PER_SECOND;
		}
		for (int i = 0; i < n; i++)
		{
			int len = Math.max(1, lengths[frame]);
			if (ticks < len)
			{
				return frame;
			}
			ticks -= len;
			frame = (frame + 1) % n;
		}
		if (ticks >= Math.max(1, lengths[frame]))
		{
			ticks = 0f;
		}
		return frame;
	}

	/**
	 * Frame lengths for a Maya (skeletal) animation, whose "frame" is the client tick within its {@code duration}:
	 * one tick each, so {@link #loop} and {@link #seek} give the tick. Allocated once per animation, not per frame.
	 */
	public static int[] tickFrames(int duration)
	{
		int[] t = new int[Math.max(0, duration)];
		Arrays.fill(t, 1);
		return t;
	}

	/** The frame {@code progress} (0..1) of the way through; -1 when there are no frames. */
	public int seek(int[] lengths, float progress)
	{
		int f = AnimationScrub.frameAt(progress, lengths);
		frame = Math.max(0, f);
		ticks = 0f;
		return f;
	}
}
