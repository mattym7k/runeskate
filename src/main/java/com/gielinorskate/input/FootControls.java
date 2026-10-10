package com.gielinorskate.input;

/** The on-foot controls as read from the keys: held directions and sprint, and the jump / drop edges. */
public final class FootControls
{
	/** Held: walk away from the camera (W, or a lean forward key in controller mode). */
	public boolean forward;
	/** Held: walk toward the camera (S, or a lean back key in controller mode). */
	public boolean back;
	public boolean left;
	public boolean right;
	/** Held: Shift. */
	public boolean sprint;
	/** Edge: Space went down since the last drain. */
	public boolean jumpPressed;
	/** Edge: Q or E went down since the last drain (drop the board, or pick it up). */
	public boolean dropPickupPressed;
}
