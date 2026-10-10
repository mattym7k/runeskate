package com.gielinorskate.overlay;

import com.gielinorskate.tricks.Trick;

/**
 * Pure elapsed-time tracker for the manual meter. {@link com.gielinorskate.physics.SkatePhysics} has no
 * getter for how long the current hold has run, so this times it from the outside using only its
 * existing state getters (which manual is currently held), sampled once per frame. Swapping between a
 * manual and a nose manual restarts it, matching the scorer's two separate holds. No client dependency.
 */
public final class ManualMeter
{
	/** The manual being timed, or null when none. */
	private Trick manual;
	private float startedAt;

	/**
	 * Call once per frame with the manual currently held (MANUAL or NOSE_MANUAL; null when not in a manual)
	 * and the clock's {@code now}.
	 */
	public void update(Trick current, float now)
	{
		if (current != manual)
		{
			manual = current;
			startedAt = now;
		}
	}

	/** "Manual 1.4s" / "Nose Manual 0.3s"-style label, or null when not currently in a manual. */
	public String text(float now)
	{
		return manual != null ? String.format("%s %.1fs", manual.displayName, Math.max(0f, now - startedAt)) : null;
	}
}
