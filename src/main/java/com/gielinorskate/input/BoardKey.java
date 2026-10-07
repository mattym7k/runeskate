package com.gielinorskate.input;

/**
 * Tap / hold detection for the board key (F): a press is reported at once, then either a tap (released within
 * {@link #HOLD_MS}) or one hold (held for {@link #HOLD_MS}, reported while still held, or on release when no poll
 * saw it in time). Key repeats while held are ignored. Pure, with explicit times; synchronized because keys
 * arrive on the AWT thread and are polled on the client thread.
 */
public final class BoardKey
{
	/** Held this long (ms) it is a hold (acts like a press: a tap already calls a far board back). */
	public static final long HOLD_MS = 1000;

	/** What happened since the last {@link #poll}. */
	public static final class Edges
	{
		/** The key went down (not a repeat). */
		public boolean pressed;
		/** The key came up within {@link #HOLD_MS} of going down. */
		public boolean tapped;
		/** The key has been held {@link #HOLD_MS} (once per press). */
		public boolean held;

		void clear()
		{
			pressed = false;
			tapped = false;
			held = false;
		}
	}

	private boolean down;
	private long downAt;
	private boolean holdReported;
	private boolean pressPending;
	private boolean tapPending;
	private boolean holdPending;

	public synchronized void press(long now)
	{
		if (down)
		{
			return; // a key repeat
		}
		down = true;
		downAt = now;
		holdReported = false;
		pressPending = true;
	}

	public synchronized void release(long now)
	{
		if (!down)
		{
			return;
		}
		down = false;
		if (holdReported)
		{
			return;
		}
		if (now - downAt >= HOLD_MS)
		{
			holdPending = true;
		}
		else
		{
			tapPending = true;
		}
	}

	/** Fills {@code out} with what happened since the last poll (a hold is reported once it is due). */
	public synchronized void poll(long now, Edges out)
	{
		out.clear();
		if (down && !holdReported && now - downAt >= HOLD_MS)
		{
			holdPending = true;
		}
		if (holdPending)
		{
			holdReported = true;
		}
		out.pressed = pressPending;
		out.tapped = tapPending;
		out.held = holdPending;
		pressPending = false;
		tapPending = false;
		holdPending = false;
	}

	/** True while the key is down. */
	public synchronized boolean isDown()
	{
		return down;
	}

	/** Forgets everything (focus lost, skating toggled). */
	public synchronized void reset()
	{
		down = false;
		holdReported = false;
		pressPending = false;
		tapPending = false;
		holdPending = false;
	}
}
