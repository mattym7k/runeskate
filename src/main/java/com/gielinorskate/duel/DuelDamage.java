package com.gielinorskate.duel;

/**
 * Skate Duel damage for a landed (banked) combo value: roughly log-shaped, 1 at up to 300, about 3 at 1k, 7 at
 * 5k, 12 at 15k, 18 at 40k, 22 at 100k and the 25 cap from 250k. Straight lines in log(value) between those
 * anchors, rounded; tune by moving the anchors. Pure.
 */
public final class DuelDamage
{
	/** The highest hit; a hit of this much shows the max-hit splat. */
	public static final int MAX = 25;

	/** {combo value, damage} anchors, increasing in both. */
	private static final double[][] ANCHORS = {
		{300, 1}, {1_000, 3}, {5_000, 7}, {15_000, 12}, {40_000, 18}, {100_000, 22}, {250_000, MAX},
	};

	private DuelDamage()
	{
	}

	/** Damage for a combo of {@code comboValue}; 0 for nothing landed. */
	public static int of(int comboValue)
	{
		if (comboValue <= 0)
		{
			return 0;
		}
		if (comboValue <= ANCHORS[0][0])
		{
			return (int) ANCHORS[0][1];
		}
		double lv = Math.log(comboValue);
		for (int i = 1; i < ANCHORS.length; i++)
		{
			if (comboValue <= ANCHORS[i][0])
			{
				double l0 = Math.log(ANCHORS[i - 1][0]);
				double l1 = Math.log(ANCHORS[i][0]);
				double t = (lv - l0) / (l1 - l0);
				return (int) Math.round(ANCHORS[i - 1][1] + t * (ANCHORS[i][1] - ANCHORS[i - 1][1]));
			}
		}
		return MAX;
	}
}
