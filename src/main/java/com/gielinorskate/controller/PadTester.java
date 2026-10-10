package com.gielinorskate.controller;

import java.awt.event.KeyEvent;
import java.util.*;

/**
 * The "Test your pad" state: which pad buttons are down (from their pad keys), which arrow keys the left stick
 * holds, and how fast the right stick moves the mouse. It only watches: it never consumes or changes input. Fed
 * from the AWT thread and read by the painter on the same thread.
 */
public final class PadTester
{
	/** The right stick's speed decays to nothing this long (ms) after the cursor stops. */
	private static final long STICK_HOLD_MS = 150;
	/** Cursor speed (px/ms) drawn as a full tilt: AntiMicroX's full tilt in the profile. */
	public static final float FULL_TILT = 1.2f;

	/** Key codes of the pad keys and arrows held. */
	private final Set<Integer> held = new HashSet<>();
	private final Set<PadButton> seen = EnumSet.noneOf(PadButton.class);
	private boolean mouseKnown;
	private int lastX;
	private int lastY;
	private long lastMoveMs;
	private float vx;
	private float vy;

	/** A key went down or up; true when the tester shows it (a pad key or an arrow). */
	public synchronized boolean key(int keyCode, boolean pressed)
	{
		PadButton b = PadButton.forKey(keyCode);
		// the arrows are VK_LEFT, VK_UP, VK_RIGHT, VK_DOWN in a row
		if (b == null && (keyCode < KeyEvent.VK_LEFT || keyCode > KeyEvent.VK_DOWN))
			return false;
		if (!pressed)
			held.remove(keyCode);
		else if (held.add(keyCode) && b != null)
			seen.add(b);
		return true;
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
		long dt = ms - lastMoveMs;
		if (x == lastX && y == lastY)
		{
			if (dt > STICK_HOLD_MS)
				vx = vy = 0;
			return;
		}
		if (dt > 0 && dt <= STICK_HOLD_MS)
		{
			vx = (x - lastX) / (float) dt;
			vy = (y - lastY) / (float) dt;
		}
		else
			// the first move after a pause: no speed to tell yet
			vx = vy = 0;
		lastX = x;
		lastY = y;
		lastMoveMs = ms;
	}

	/** Lets go of everything (the window lost focus, or the test closed). */
	public synchronized void reset()
	{
		held.clear();
		vx = vy = 0;
		mouseKnown = false;
	}

	public synchronized boolean isDown(PadButton b)
	{
		return held.contains(b.keyCode);
	}

	/** True once the button has been pressed since the tester opened. */
	public synchronized boolean wasSeen(PadButton b)
	{
		return seen.contains(b);
	}

	/** The left stick as the arrows it holds: x -1 / 0 / 1 (left / right), y -1 / 0 / 1 (up / down). */
	public synchronized int leftX()
	{
		return arrow(KeyEvent.VK_RIGHT) - arrow(KeyEvent.VK_LEFT);
	}

	public synchronized int leftY()
	{
		return arrow(KeyEvent.VK_DOWN) - arrow(KeyEvent.VK_UP);
	}

	private int arrow(int keyCode)
	{
		return held.contains(keyCode) ? 1 : 0;
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

	private static float clamp(float v)
	{
		return Math.max(-1f, Math.min(1f, v));
	}
}
