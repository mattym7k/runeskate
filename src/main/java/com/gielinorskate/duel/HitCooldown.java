package com.gielinorskate.duel;

import java.util.ArrayDeque;

/**
 * At least {@link #COOLDOWN} s between a duellist's hits: a combo landing sooner waits in a queue and goes when
 * the cooldown ends, one hit per cooldown, in order. Pure; times are seconds on one monotonic clock.
 */
final class HitCooldown
{
	static final float COOLDOWN = 1.5f;

	/** One hit waiting to go. */
	static final class Pending
	{
		final int damage;
		final int comboValue;
		final int trickCount;

		Pending(int damage, int comboValue, int trickCount)
		{
			this.damage = damage;
			this.comboValue = comboValue;
			this.trickCount = trickCount;
		}
	}

	private final ArrayDeque<Pending> queue = new ArrayDeque<>();
	private float nextAllowed = Float.NEGATIVE_INFINITY;

	void offer(int damage, int comboValue, int trickCount)
	{
		queue.add(new Pending(damage, comboValue, trickCount));
	}

	/** The next hit to apply at {@code now}, or null while the cooldown runs or nothing waits. */
	Pending poll(float now)
	{
		if (queue.isEmpty() || now < nextAllowed)
		{
			return null;
		}
		nextAllowed = now + COOLDOWN;
		return queue.poll();
	}

	int pending()
	{
		return queue.size();
	}

	void clear()
	{
		queue.clear();
		nextAllowed = Float.NEGATIVE_INFINITY;
	}
}
