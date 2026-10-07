package com.gielinorskate.camera;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;

/**
 * Pure chase-camera behaviour modelled on how Skate-style cameras feel: low, behind the direction of travel,
 * lagging through carves, ignoring spins, leading the skater at speed, rising less than the skater in the air,
 * dipping on landings (harder ones further, big airs with a short jolt) and holding still during bails. Units: local units, seconds, radians.
 */
public final class CameraRig
{
	/** Height of the look-at point above the skater's feet. */
	static final float FOCUS_HEIGHT = 70f;
	static final float MAX_LOOK_AHEAD = 96f;

	private static final float YAW_FOLLOW_RATE = 3.0f;
	/** Faster follow while the camera is more than YAW_FAR_ERROR behind (a pivot or a sharp turn). */
	private static final float YAW_FOLLOW_RATE_FAR = 4.0f;
	private static final float YAW_FAR_ERROR = (float) Math.toRadians(60);
	private static final float YAW_FOLLOW_RATE_AIR = 0.6f;
	/**
	 * The yaw never turns faster than this (rad/s): one turn a second. The exponential follow at
	 * YAW_FOLLOW_RATE_FAR = 4 turns faster than 2 PI only while more than 2 PI / 4 = 90 degrees behind, so
	 * ordinary turns, pivots (pivotRate 4.5 rad/s) and the 60-90 degree catch-up are unchanged; a half-turn
	 * swing (getting back on facing away from a wall after a bail) no longer whips round at 4 PI = 720 deg/s.
	 */
	static final float MAX_YAW_RATE = 2 * Angles.PI;
	private static final float LOOK_AHEAD_SECONDS = 0.18f;
	private static final float LOOK_AHEAD_RATE = 4f;
	private static final float HEIGHT_RATE_GROUND = 12f;
	private static final float HEIGHT_RATE_AIR = 2.5f;
	/**
	 * The landing dip (local units) scales with the landing's downward speed, from {@link #MIN_LANDING_DIP} at
	 * {@link #SOFT_LANDING_SPEED} to {@link #MAX_LANDING_DIP} at {@link #HARD_LANDING_SPEED}. A flat-ground
	 * ollie lands at about its pop speed, ollieImpulse = 820 u/s: 8 + 22 * (820 - 500) / 1200 = 13.9, the old
	 * fixed dip of 14; a drop off a ledge kicks harder.
	 */
	static final float MIN_LANDING_DIP = 8f;
	static final float MAX_LANDING_DIP = 30f;
	static final float SOFT_LANDING_SPEED = 500f;
	static final float HARD_LANDING_SPEED = 1700f;
	/**
	 * Landing speed guess when the flight's fall was not seen (u/s per second of air): a flight that starts and
	 * ends at the same height lands at gravity * airtime / 2, with gravity = 2000 u/s^2 (skate-tuning.json).
	 */
	static final float AIRTIME_SPEED_PER_SECOND = 2000f / 2f;
	/** Only landings after more than this many seconds in the air dip the camera (not bumps or tiny hops). */
	static final float MIN_DIP_AIRTIME = 0.25f;
	/**
	 * Big airs also shake the focus a little. The review's 0.8 s would shake every full ollie (2 * 820 / 2000 =
	 * 0.82 s in the air), so only flights longer than 1 s (a drop, or a pop off a ramp or rail) do.
	 */
	static final float JITTER_MIN_AIRTIME = 1.0f;
	static final float JITTER_AMPLITUDE = 4f;
	/** Exponential decay of the jitter (1/s): 4 * e^(-18 * 0.25) = 0.04 units after a quarter second. */
	static final float JITTER_DECAY_RATE = 18f;
	/** Done after this; the remaining amplitude is 4 * e^(-18 * 0.4) < 0.003 units. */
	static final float JITTER_SECONDS = 0.4f;
	/** Shake frequencies (Hz), different per axis so it reads as a jolt rather than a sway. */
	private static final float JITTER_HZ_UP = 11f;
	private static final float JITTER_HZ_SIDE = 7f;
	private static final float DIP_RECOVER_RATE = 6f;
	private static final float SPEED_PULLBACK = 0.15f;
	private static final float PULLBACK_SPEED = 1500f;

	private float yaw;
	private float leadX;
	private float leadY;
	private float x;
	private float y;
	private float height;
	private float dip;
	private float zoomFactor = 1f;
	/** Skater height on the previous update, to measure the fall speed of a flight. */
	private float lastSkaterH;
	/** Vertical speed (u/s, up > 0) on the latest airborne update. */
	private float airVerticalSpeed;
	/** Seconds since a big-air landing started the jitter; past {@link #JITTER_SECONDS} when none. */
	private float jitterAge = Float.MAX_VALUE;
	private float jitterUp;
	private float jitterSide;

	/** Converts a heading (0 = north, clockwise) to RuneLite camera yaw: JAU14, 0 = north, counter-clockwise. */
	public static int yawJau14(float heading)
	{
		return Math.floorMod(Math.round(-heading * 8192f / Angles.PI), 16384);
	}

	public void reset(float x, float y, float h, float heading)
	{
		this.yaw = heading;
		this.x = x;
		this.y = y;
		this.height = h;
		this.leadX = 0f;
		this.leadY = 0f;
		this.dip = 0f;
		this.zoomFactor = 1f;
		this.lastSkaterH = h;
		this.airVerticalSpeed = 0f;
		this.jitterAge = Float.MAX_VALUE;
		this.jitterUp = 0f;
		this.jitterSide = 0f;
	}

	/** Landing dip for a landing at {@code landingSpeed} (downward u/s), see {@link #MIN_LANDING_DIP}. */
	static float landingDip(float landingSpeed)
	{
		float u = (landingSpeed - SOFT_LANDING_SPEED) / (HARD_LANDING_SPEED - SOFT_LANDING_SPEED);
		return MIN_LANDING_DIP + (MAX_LANDING_DIP - MIN_LANDING_DIP) * Math.max(0f, Math.min(1f, u));
	}

	/**
	 * @param cameraHeading heading to sit behind ({@code SkatePhysics.getCameraHeading()}: the board, not the
	 *     roll direction, so it is followed at any speed, even turning in place)
	 * @param speed signed or unsigned speed; only its magnitude is used
	 * @param landedAirtime seconds in the air of a landing on this frame, 0 when none
	 */
	public void update(float skaterX, float skaterY, float h, float cameraHeading, float speed, SkaterState state,
		float landedAirtime, float dt)
	{
		float absSpeed = Math.abs(speed);
		boolean airborne = state == SkaterState.AIRBORNE;

		if (state != SkaterState.BAILED)
		{
			float error = Angles.wrap(cameraHeading - yaw);
			float rate = airborne ? YAW_FOLLOW_RATE_AIR
				: Math.abs(error) > YAW_FAR_ERROR ? YAW_FOLLOW_RATE_FAR : YAW_FOLLOW_RATE;
			float turn = error * blend(rate, dt);
			float maxTurn = MAX_YAW_RATE * dt;
			yaw = Angles.wrap(yaw + Math.max(-maxTurn, Math.min(maxTurn, turn)));
		}

		float lead = state == SkaterState.BAILED ? 0f : Math.min(MAX_LOOK_AHEAD, absSpeed * LOOK_AHEAD_SECONDS);
		float targetLeadX = absSpeed > 1f ? (float) Math.sin(cameraHeading) * lead : 0f;
		float targetLeadY = absSpeed > 1f ? (float) Math.cos(cameraHeading) * lead : 0f;
		float leadBlend = blend(LOOK_AHEAD_RATE, dt);
		leadX += (targetLeadX - leadX) * leadBlend;
		leadY += (targetLeadY - leadY) * leadBlend;
		x = skaterX + leadX;
		y = skaterY + leadY;

		height += (h - height) * blend(airborne ? HEIGHT_RATE_AIR : HEIGHT_RATE_GROUND, dt);
		if (airborne && dt > 0f)
		{
			airVerticalSpeed = (h - lastSkaterH) / dt;
		}
		lastSkaterH = h;
		if (landedAirtime > MIN_DIP_AIRTIME)
		{
			// the fall seen on the last airborne update, or the flat-ground guess from the airtime if larger
			float landingSpeed = Math.max(-airVerticalSpeed, landedAirtime * AIRTIME_SPEED_PER_SECOND);
			dip = landingDip(landingSpeed);
			if (landedAirtime > JITTER_MIN_AIRTIME)
			{
				jitterAge = 0f;
			}
		}
		if (landedAirtime > 0f)
		{
			airVerticalSpeed = 0f;
		}
		dip -= dip * blend(DIP_RECOVER_RATE, dt);
		updateJitter(dt);

		zoomFactor = 1f - SPEED_PULLBACK * Math.min(1f, absSpeed / PULLBACK_SPEED);
	}

	/** A decaying jolt of the focus after a big air: up/down and across the view. */
	private void updateJitter(float dt)
	{
		if (jitterAge >= JITTER_SECONDS)
		{
			jitterUp = 0f;
			jitterSide = 0f;
			return;
		}
		float amplitude = JITTER_AMPLITUDE * (float) Math.exp(-JITTER_DECAY_RATE * jitterAge);
		jitterUp = amplitude * (float) Math.cos(2 * Math.PI * JITTER_HZ_UP * jitterAge);
		jitterSide = amplitude * (float) Math.sin(2 * Math.PI * JITTER_HZ_SIDE * jitterAge);
		jitterAge += dt;
	}

	private static float blend(float rate, float dt)
	{
		return 1f - (float) Math.exp(-rate * dt);
	}

	/** Heading the camera looks along (radians, 0 = north). */
	public float getYaw()
	{
		return yaw;
	}

	/** Turns the camera by hand (middle-button orbit on foot), radians clockwise. */
	public void orbit(float radians)
	{
		yaw = Angles.wrap(yaw + radians);
	}

	public int getYawJau14()
	{
		return yawJau14(yaw);
	}

	public float getFocusX()
	{
		// across the view: the camera looks along yaw (sin, cos), so its right is (cos, -sin)
		return x + jitterSide * (float) Math.cos(yaw);
	}

	public float getFocusY()
	{
		return y - jitterSide * (float) Math.sin(yaw);
	}

	/** Up-positive height of the look-at point. */
	public float getFocusHeight()
	{
		return height + FOCUS_HEIGHT - dip + jitterUp;
	}

	/** 1 when slow, smaller (camera further back) at speed. */
	public float getZoomFactor()
	{
		return zoomFactor;
	}
}
