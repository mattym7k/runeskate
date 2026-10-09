package com.gielinorskate.camera;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import lombok.Getter;

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
/**
* The yaw never turns faster than this (rad/s): one turn a second. The exponential follow at the far rate
* (4/s) turns faster than 2 PI only while more than 2 PI / 4 = 90 degrees behind, so ordinary turns, pivots
* (pivotRate 4.5 rad/s) and the 60-90 degree catch-up are unchanged; a half-turn swing (getting back on facing
* away from a wall after a bail) no longer whips round at 4 PI = 720 deg/s.
*/
static final float MAX_YAW_RATE = 2 * Angles.PI;
/** Big airs also shake the focus a little, by up to this much (see updateJitter). */
static final float JITTER_AMPLITUDE = 4f;
/** Done after this; the remaining amplitude is 4 * e^(-18 * 0.4) < 0.003 units. */
private static final float JITTER_SECONDS = 0.4f;

/** Heading the camera looks along (radians, 0 = north). */
@Getter
private float yaw;
private float leadX, leadY, x, y, height, dip;
/** 1 when slow, smaller (camera further back) at speed. */
@Getter
private float zoomFactor = 1f;
/** Skater height on the previous update, to measure the fall speed of a flight. */
private float lastSkaterH;
/** Vertical speed (u/s, up > 0) on the latest airborne update. */
private float airVerticalSpeed;
/** Seconds since a big-air landing started the jitter; past {@link #JITTER_SECONDS} when none. */
private float jitterAge = Float.MAX_VALUE;
private float jitterUp, jitterSide;

/** Converts a heading (0 = north, clockwise) to RuneLite camera yaw: JAU14, 0 = north, counter-clockwise. */
public static int yawJau14(float heading)
{
return Math.floorMod(Math.round(-heading * 8192f / Angles.PI), 16384);
}

public void reset(float x, float y, float h, float heading)
{
yaw = heading;
this.x = x;
this.y = y;
height = h;
leadX = 0f;
leadY = 0f;
dip = 0f;
zoomFactor = 1f;
lastSkaterH = h;
airVerticalSpeed = 0f;
jitterAge = Float.MAX_VALUE;
jitterUp = 0f;
jitterSide = 0f;
}

/**
* Landing dip (local units) for a landing at {@code landingSpeed} (downward u/s): from 8 at 500 u/s to 30 at
* 1700 u/s. A flat-ground ollie lands at about its pop speed, ollieImpulse = 820 u/s: 8 + 22 * (820 - 500) /
* 1200 = 13.9, the old fixed dip of 14; a drop off a ledge kicks harder.
*/
static float landingDip(float landingSpeed)
{
float u = (landingSpeed - 500f) / (1700f - 500f);
return 8f + (30f - 8f) * Math.max(0f, Math.min(1f, u));
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
// follows at 3/s, 4/s while more than 60 degrees behind (a pivot or a sharp turn), 0.6/s in the air
float rate = airborne ? 0.6f : Math.abs(error) > (float) Math.toRadians(60) ? 4f : 3f;
float turn = error * blend(rate, dt);
float maxTurn = MAX_YAW_RATE * dt;
yaw = Angles.wrap(yaw + Math.max(-maxTurn, Math.min(maxTurn, turn)));
}

// leads by 0.18 s of travel, at most MAX_LOOK_AHEAD, easing at 4/s
float lead = state == SkaterState.BAILED ? 0f : Math.min(MAX_LOOK_AHEAD, absSpeed * 0.18f);
float targetLeadX = absSpeed > 1f ? (float) Math.sin(cameraHeading) * lead : 0f;
float targetLeadY = absSpeed > 1f ? (float) Math.cos(cameraHeading) * lead : 0f;
float leadBlend = blend(4f, dt);
leadX += (targetLeadX - leadX) * leadBlend;
leadY += (targetLeadY - leadY) * leadBlend;
x = skaterX + leadX;
y = skaterY + leadY;

// the height follows at 12/s on the ground, 2.5/s in the air
height += (h - height) * blend(airborne ? 2.5f : 12f, dt);
if (airborne && dt > 0f)
airVerticalSpeed = (h - lastSkaterH) / dt;
lastSkaterH = h;
// only landings after more than 0.25 s in the air dip the camera (not bumps or tiny hops)
if (landedAirtime > 0.25f)
{
// the fall seen on the last airborne update, or the flat-ground guess from the airtime if larger: a
// flight that starts and ends at the same height lands at gravity * airtime / 2, with gravity = 2000
// u/s^2 (skate-tuning.json)
float landingSpeed = Math.max(-airVerticalSpeed, landedAirtime * (2000f / 2f));
dip = landingDip(landingSpeed);
// the review's 0.8 s would shake every full ollie (2 * 820 / 2000 = 0.82 s in the air), so only flights
// longer than 1 s (a drop, or a pop off a ramp or rail) shake the focus
if (landedAirtime > 1f)
jitterAge = 0f;
}
if (landedAirtime > 0f)
airVerticalSpeed = 0f;
dip -= dip * blend(6f, dt);
updateJitter(dt);

// pulls back up to 0.15 by 1500 u/s
zoomFactor = 1f - 0.15f * Math.min(1f, absSpeed / 1500f);
}

/**
* A decaying jolt of the focus after a big air: up/down and across the view, at different frequencies per axis
* (11 Hz and 7 Hz) so it reads as a jolt rather than a sway. Decays at 18/s: 4 * e^(-18 * 0.25) = 0.04 units
* after a quarter second.
*/
private void updateJitter(float dt)
{
if (jitterAge >= JITTER_SECONDS)
{
jitterUp = 0f;
jitterSide = 0f;
return;
}
float amplitude = JITTER_AMPLITUDE * (float) Math.exp(-18f * jitterAge);
jitterUp = amplitude * (float) Math.cos(2 * Math.PI * 11f * jitterAge);
jitterSide = amplitude * (float) Math.sin(2 * Math.PI * 7f * jitterAge);
jitterAge += dt;
}

private static float blend(float rate, float dt)
{
return 1f - (float) Math.exp(-rate * dt);
}

/** Turns the camera by hand (middle-button orbit on foot), radians clockwise. */
public void orbit(float radians)
{
yaw = Angles.wrap(yaw + radians);
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
}
