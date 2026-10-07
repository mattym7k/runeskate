package com.gielinorskate.render;

/**
 * Plays a classic (frame-based) OSRS animation at a chosen pace by picking its frame from how far through a
 * window of our own we are: the get-up after a knockdown is squeezed into its time, whatever the animation's own
 * length. Pure.
 */
public final class AnimationScrub
{
	private AnimationScrub()
	{
	}

	/**
	 * The frame {@code progress} (0..1, clamped) of the way through an animation with these frame lengths (client
	 * ticks; zero counts as one), or -1 when there is nothing to pick.
	 */
	public static int frameAt(float progress, int[] frameLengths)
	{
		if (frameLengths == null || frameLengths.length == 0 || Float.isNaN(progress))
		{
			return -1;
		}
		long total = 0;
		for (int len : frameLengths)
		{
			total += Math.max(1, len);
		}
		float u = Math.max(0f, Math.min(1f, progress));
		double at = u * total;
		long start = 0;
		for (int i = 0; i < frameLengths.length; i++)
		{
			long end = start + Math.max(1, frameLengths[i]);
			if (at < end)
			{
				return i;
			}
			start = end;
		}
		return frameLengths.length - 1;
	}
}
