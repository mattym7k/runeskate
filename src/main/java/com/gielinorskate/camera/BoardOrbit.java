package com.gielinorskate.camera;

import com.gielinorskate.physics.Angles;

/**
 * Turning the chase camera by hand on the board (the Tony Hawk's American Wasteland preset's right stick): the turn
 * is kept as an offset from where the chase camera would look, so the camera stays where the stick put it while it
 * is moved. After {@link #IDLE_SECONDS} without stick input the offset is let go and the chase camera eases back
 * behind the skater at its usual follow rate. Pure.
 */
public final class BoardOrbit
{
	/** Seconds without stick input before the camera goes back behind the skater. */
	public static final float IDLE_SECONDS = 1.5f;

	private float offset;
	private float idle = IDLE_SECONDS;

	/** The stick turned the camera by {@code radians} (clockwise). */
	public void turn(float radians)
	{
		if (radians == 0f)
			return;
		offset = Angles.wrap(offset + radians);
		idle = 0f;
	}

	/** A frame of {@code dt} seconds passed: once idle for {@link #IDLE_SECONDS} the offset goes. */
	public void update(float dt)
	{
		idle = Math.min(IDLE_SECONDS, idle + dt);
		if (idle >= IDLE_SECONDS)
			offset = 0f;
	}

	/** How far (radians, clockwise) the camera is turned from behind the skater by hand. */
	public float offset()
	{
		return offset;
	}

	/** Off the board (or a new session): no offset. */
	public void reset()
	{
		offset = 0f;
		idle = IDLE_SECONDS;
	}
}
