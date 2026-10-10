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

	int frame;
	/** Client ticks owed to the animation that did not yet make a whole frame. */
	float ticks;

	/** Back to the first frame. */
	public void reset()
	{
		frame = 0;
		ticks = 0f;
	}

	/**
	 * Advances {@code dt} seconds at {@code rate} times the game's pace, looping; returns the frame now, or -1 when
	 * there are no frames.
	 */
	public int loop(int[] lengths, float rate, float dt)
	{
		if (lengths == null || lengths.length == 0)
		{
			reset();
			return -1;
		}
		if (frame >= lengths.length)
			reset();
		if (dt > 0f && rate > 0f)
			ticks += rate * dt * TICKS_PER_SECOND;
		run(lengths);
		return frame;
	}

	/**
	 * Steps on through the whole frames the ticks owed make, at most one cycle: a long hitch skips at most one cycle
	 * and forgets the rest. True if any frame was stepped.
	 */
	boolean run(int[] lengths)
	{
		boolean moved = false;
		for (int i = 0; i < lengths.length && ticks >= Math.max(1, lengths[frame]); i++)
		{
			ticks -= Math.max(1, lengths[frame]);
			frame = (frame + 1) % lengths.length;
			moved = true;
		}
		if (ticks >= Math.max(1, lengths[frame]))
			ticks = 0f;
		return moved;
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
		int f = frameAt(progress, lengths);
		frame = Math.max(0, f);
		ticks = 0f;
		return f;
	}

	/**
	 * The frame {@code progress} (0..1, clamped) of the way through an animation with these frame lengths (client
	 * ticks; zero counts as one), or -1 when there is nothing to pick: plays an animation at a chosen pace by picking
	 * its frame from how far through a window of our own we are (the get-up after a knockdown is squeezed into its
	 * time, whatever the animation's own length).
	 */
	public static int frameAt(float progress, int[] frameLengths)
	{
		if (frameLengths == null || frameLengths.length == 0 || Float.isNaN(progress))
			return -1;
		long total = 0;
		for (int len : frameLengths)
			total += Math.max(1, len);
		double at = Math.max(0f, Math.min(1f, progress)) * total;
		for (int i = 0; i < frameLengths.length; i++)
		{
			at -= Math.max(1, frameLengths[i]);
			if (at < 0)
				return i;
		}
		return frameLengths.length - 1;
	}
}
