package com.gielinorskate.input;

/**
 * The button-trick manual (the Tony Hawk's American Wasteland preset): a quick up-then-down on the left stick while
 * rolling is a manual, down-then-up a nose manual. The second half must come within {@link #WINDOW_MS} of the first.
 * The manual then holds by itself (see {@link InputController}) until the skater pops, lands it off or does the
 * other gesture. Pure, with explicit times; synchronized because the stick arrives on the AWT thread.
 */
public final class StickManual
{
	/** The up and the down (or the down and the up) must come within this many ms. */
	public static final long WINDOW_MS = 300;

	public enum Kind
	{
		NONE,
		MANUAL,
		NOSE
	}

	private long upAt = Long.MIN_VALUE;
	private long downAt = Long.MIN_VALUE;

	/**
	 * The stick went up at {@code now}: a nose manual when it went down within {@link #WINDOW_MS} before, else
	 * {@link Kind#NONE} (the up is remembered for a manual).
	 */
	public synchronized Kind up(long now)
	{
		Kind k = within(downAt, now) ? Kind.NOSE : Kind.NONE;
		downAt = Long.MIN_VALUE;
		upAt = k == Kind.NONE ? now : Long.MIN_VALUE;
		return k;
	}

	/** The stick went down at {@code now}: a manual when it went up within {@link #WINDOW_MS} before. */
	public synchronized Kind down(long now)
	{
		Kind k = within(upAt, now) ? Kind.MANUAL : Kind.NONE;
		upAt = Long.MIN_VALUE;
		downAt = k == Kind.NONE ? now : Long.MIN_VALUE;
		return k;
	}

	/** Forgets a half-done gesture (the skater left the ground, or the stick was let go of for good). */
	public synchronized void reset()
	{
		upAt = Long.MIN_VALUE;
		downAt = Long.MIN_VALUE;
	}

	private static boolean within(long at, long now)
	{
		return at != Long.MIN_VALUE && now - at <= WINDOW_MS && now >= at;
	}
}
