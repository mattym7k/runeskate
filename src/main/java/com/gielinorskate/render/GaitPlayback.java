package com.gielinorskate.render;

import com.gielinorskate.physics.FootPhysics;

/**
 * Plays the walker's OSRS walk or run pose animation faster than the game does, so its feet keep up with the
 * walker's (faster than OSRS) speed: the game advances the pose animation at its own rate, and this adds the
 * missing frames on top ({@code Actor.setPoseAnimationFrame}). Frame lengths are client ticks (20 ms). Pure.
 */
public final class GaitPlayback
{
	/** Client ticks (frame-length units) per second. */
	static final float TICKS_PER_SECOND = 50f;
	/** Never more than this times the game's speed (a frame skipped every other tick still reads as a cycle). */
	static final float MAX_RATE = 3f;

	/** Extra client ticks owed to the animation that did not yet make a whole frame. */
	private float extraTicks;

	/**
	 * How many times the game's speed the gait animation should play at for a walker at {@code speed} u/s: the
	 * walk against the game's walk, the run against the game's run. Never below 1 (frames are only ever added).
	 */
	public static float rate(FootBody.Gait gait, float speed)
	{
		float base;
		switch (gait)
		{
			case WALK:
				base = FootPhysics.GAME_WALK_SPEED;
				break;
			case RUN:
				base = FootPhysics.GAME_RUN_SPEED;
				break;
			default:
				return 1f;
		}
		float r = speed / base;
		if (!(r > 1f))
		{
			return 1f;
		}
		return Math.min(MAX_RATE, r);
	}

	/**
	 * One frame of {@code dt} seconds at {@code rate} with the animation at {@code frame}: the frame to jump to, or
	 * -1 to leave the animation alone (nothing owed yet, or an animation this cannot step).
	 */
	public int advance(int frame, int[] frameLengths, float rate, float dt)
	{
		int n = frameLengths == null ? 0 : frameLengths.length;
		if (n == 0 || frame < 0 || frame >= n || !(dt > 0f))
		{
			extraTicks = 0f;
			return -1;
		}
		extraTicks += Math.max(0f, rate - 1f) * dt * TICKS_PER_SECOND;
		int f = frame;
		boolean moved = false;
		// at most one full cycle per frame, so a long hitch never spins here
		for (int i = 0; i < n; i++)
		{
			int len = Math.max(1, frameLengths[f]);
			if (extraTicks < len)
			{
				break;
			}
			extraTicks -= len;
			f = (f + 1) % n;
			moved = true;
		}
		if (extraTicks >= Math.max(1, frameLengths[f]))
		{
			// a hitch: a whole cycle was skipped already, the rest is forgotten
			extraTicks = 0f;
		}
		return moved ? f : -1;
	}

	/** Forgets the time owed (a new animation). */
	public void reset()
	{
		extraTicks = 0f;
	}
}
