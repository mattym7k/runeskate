package com.gielinorskate.input;

/** The latest pointer samples (position and time) in a ring of fixed size. Not thread-safe: its owner locks. */
final class Samples
{
	final float[] x;
	final float[] y;
	final long[] ms;
	/** How many are kept (at most the ring's size). */
	int count;
	private int next;

	Samples(int size)
	{
		x = new float[size];
		y = new float[size];
		ms = new long[size];
	}

	void add(float px, float py, long pms)
	{
		x[next] = px;
		y[next] = py;
		ms[next] = pms;
		next = (next + 1) % ms.length;
		count = Math.min(ms.length, count + 1);
	}

	/** The ring index of the sample {@code k} before the newest. */
	int ago(int k)
	{
		return (next - 1 - k + ms.length) % ms.length;
	}

	/**
	 * Speed in px/ms to (px, py, pms) from the newest sample at least {@code spanMs} older (or else the oldest one of
	 * the newest {@code limit}, and none from before {@code notBefore}); {@code none} when there is no older sample.
	 * The current sample must already be added.
	 */
	float speed(float px, float py, long pms, long spanMs, int limit, long notBefore, float none)
	{
		int from = -1;
		for (int k = 1; k < Math.min(count, limit); k++)
		{
			int i = ago(k);
			if (ms[i] < notBefore)
				break;
			from = i;
			if (ms[i] <= pms - spanMs)
				break;
		}
		return from < 0 || ms[from] >= pms ? none : (float) (Math.hypot(px - x[from], py - y[from]) / (pms - ms[from]));
	}
}
