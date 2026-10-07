package com.gielinorskate.controller;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;

/**
 * The "Test your pad" state: which pad buttons are down (from their pad keys), which arrow keys the left stick
 * holds, and how fast the right stick moves the mouse. It only watches: it never consumes or changes input. Fed
 * from the AWT thread and read by the painter on the same thread.
 */
public final class PadTester
{
	/** The right stick's speed decays to nothing this long after the cursor stops. */
	static final long STICK_HOLD_MS = 150;
	/** Cursor speed (px/ms) drawn as a full tilt: AntiMicroX's full tilt in the profile. */
	public static final float FULL_TILT = 1.2f;

	private final Set<PadButton> down = EnumSet.noneOf(PadButton.class);
	private final Set<PadButton> seen = EnumSet.noneOf(PadButton.class);
	private boolean up;
	private boolean downArrow;
	private boolean left;
	private boolean right;
	private boolean mouseKnown;
	private int lastX;
	private int lastY;
	private long lastMoveMs;
	private float vx;
	private float vy;
	private boolean rightStickSeen;

	/** A key went down or up; true when the tester shows it (a pad key or an arrow). */
	public synchronized boolean key(int keyCode, boolean pressed)
	{
		PadButton b = PadButton.forKey(keyCode);
		if (b != null)
		{
			if (pressed)
			{
				down.add(b);
				seen.add(b);
			}
			else
			{
				down.remove(b);
			}
			return true;
		}
		switch (keyCode)
		{
			case KeyEvent.VK_UP:
				up = pressed;
				return true;
			case KeyEvent.VK_DOWN:
				downArrow = pressed;
				return true;
			case KeyEvent.VK_LEFT:
				left = pressed;
				return true;
			case KeyEvent.VK_RIGHT:
				right = pressed;
				return true;
			default:
				return false;
		}
	}

	/**
	 * The cursor is at (x, y) on screen at {@code ms} (sampled a few times a second, or on each move): its motion
	 * is the right stick. A still cursor is a centred stick once {@link #STICK_HOLD_MS} has passed.
	 */
	public synchronized void mouse(int x, int y, long ms)
	{
		if (!mouseKnown)
		{
			mouseKnown = true;
			lastX = x;
			lastY = y;
			lastMoveMs = ms;
			return;
		}
		if (x == lastX && y == lastY)
		{
			if (ms - lastMoveMs > STICK_HOLD_MS)
			{
				vx = vy = 0;
			}
			return;
		}
		long dt = ms - lastMoveMs;
		if (dt > 0 && dt <= STICK_HOLD_MS)
		{
			vx = (x - lastX) / (float) dt;
			vy = (y - lastY) / (float) dt;
			rightStickSeen |= Math.hypot(vx, vy) > 0.05;
		}
		else
		{
			// the first move after a pause: no speed to tell yet
			vx = vy = 0;
		}
		lastX = x;
		lastY = y;
		lastMoveMs = ms;
	}

	/** Lets go of everything (the window lost focus, or the test closed). */
	public synchronized void reset()
	{
		down.clear();
		up = downArrow = left = right = false;
		vx = vy = 0;
		mouseKnown = false;
	}

	public synchronized boolean isDown(PadButton b)
	{
		return down.contains(b);
	}

	/** True once the button has been pressed since the tester opened. */
	public synchronized boolean wasSeen(PadButton b)
	{
		return seen.contains(b);
	}

	/** The left stick as the arrows it holds: x -1 / 0 / 1 (left / right), y -1 / 0 / 1 (up / down). */
	public synchronized int leftX()
	{
		return (right ? 1 : 0) - (left ? 1 : 0);
	}

	public synchronized int leftY()
	{
		return (downArrow ? 1 : 0) - (up ? 1 : 0);
	}

	/** The right stick's tilt from the cursor's speed, each axis clamped to -1..1 (y grows downward). */
	public synchronized float rightX()
	{
		return clamp(vx / FULL_TILT);
	}

	public synchronized float rightY()
	{
		return clamp(vy / FULL_TILT);
	}

	/** True once the cursor has moved like a stick since the tester opened. */
	public synchronized boolean rightStickSeen()
	{
		return rightStickSeen;
	}

	private static float clamp(float v)
	{
		return Math.max(-1f, Math.min(1f, v));
	}
}
