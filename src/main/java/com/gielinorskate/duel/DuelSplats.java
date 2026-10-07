package com.gielinorskate.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The OSRS-style hitsplats showing over the two duellists: each lives {@link #LIFE} s in one of
 * {@link #SLOTS} places around the skater (as the game stacks hits that land together), a fifth at once
 * replacing the oldest. Drawn by our own overlay only; no real actor's hitsplats are touched. Client thread.
 */
public final class DuelSplats
{
	public static final float LIFE = 1.2f;
	public static final int SLOTS = 4;
	private static final float FADE = 0.3f;

	/** One hitsplat. */
	public static final class Splat
	{
		/** Over the local skater (else over the opponent's ghost). */
		public final boolean onMe;
		public final int damage;
		/** A max hit: drawn in the max-hit style. */
		public final boolean max;
		public final float born;
		/** 0..SLOTS-1: where it sits around the skater. */
		public final int slot;

		Splat(boolean onMe, int damage, float born, int slot)
		{
			this.onMe = onMe;
			this.damage = damage;
			this.max = damage >= DuelDamage.MAX;
			this.born = born;
			this.slot = slot;
		}

		/** Opacity at {@code now}: full, then fading over the last 0.3 s. */
		public float alpha(float now)
		{
			float left = LIFE - (now - born);
			return Math.max(0f, Math.min(1f, left / FADE));
		}
	}

	private final List<Splat> splats = new ArrayList<>();

	public void add(boolean onMe, int damage, float now)
	{
		prune(now);
		boolean[] used = new boolean[SLOTS];
		Splat oldest = null;
		for (Splat s : splats)
		{
			if (s.onMe == onMe)
			{
				used[s.slot] = true;
				if (oldest == null || s.born < oldest.born)
				{
					oldest = s;
				}
			}
		}
		int slot = -1;
		for (int i = 0; i < SLOTS; i++)
		{
			if (!used[i])
			{
				slot = i;
				break;
			}
		}
		if (slot < 0)
		{
			splats.remove(oldest);
			slot = oldest.slot;
		}
		splats.add(new Splat(onMe, damage, now, slot));
	}

	/** The splats showing over one skater at {@code now}, oldest first. */
	public List<Splat> live(boolean onMe, float now)
	{
		prune(now);
		if (splats.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Splat> out = new ArrayList<>(splats.size());
		for (Splat s : splats)
		{
			if (s.onMe == onMe)
			{
				out.add(s);
			}
		}
		return out;
	}

	public void clear()
	{
		splats.clear();
	}

	private void prune(float now)
	{
		for (Iterator<Splat> it = splats.iterator(); it.hasNext(); )
		{
			if (now - it.next().born >= LIFE)
			{
				it.remove();
			}
		}
	}
}
