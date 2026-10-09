package com.gielinorskate.render;

import com.gielinorskate.physics.*;
import com.gielinorskate.tricks.Trick;
import lombok.AllArgsConstructor;

/**
* An immutable snapshot of everything the renderer and camera draw from physics, so a frame can be drawn
* between two fixed physics steps ({@link #lerp}). Pure: no client dependency.
*/
@AllArgsConstructor
public final class RenderPose
{
/**
* A snapshot pair further apart than this is a teleport, not motion: drawn without blending. The
* fastest physics step is maxSpeed 2600 * 0.02 s = 52 units, so this only catches real jumps.
*/
static final float MAX_LERP_DISTANCE = 96f;
/**
* Board roll/yaw changes larger than this between two steps are a snap (e.g. a shove-it's half turn
* cleared on landing), not rotation. The fastest real flip step: a double kickflip (2 turns in 0.5 s)
* with an ease-out curve peaking at 2x the mean rate is 2 * 4 PI / 0.5 * 0.02 s = 1.0 rad.
*/
static final float MAX_ANGLE_STEP = 1.5f;

public final float x;
public final float y;
/** Up-positive height. */
public final float h;
public final float heading;
public final float cameraHeading;
public final float boardRoll;
public final float boardYaw;
public final float boardPitch;
public final SkaterState state;
/** Current grab, manual or grind, or null. */
public final Trick hold;

public static RenderPose of(SkatePhysics p)
{
return new RenderPose(p.getX(), p.getY(), p.getH(), p.getHeading(), p.getCameraHeading(), p.getBoardRoll(),
p.getBoardYawOffset(), p.getBoardPitch(), p.getState(), p.getActiveHold());
}

/**
* The pose {@code alpha} (0..1, clamped) of the way from {@code a} (the older step) to {@code b}.
* Returns {@code b} itself when the two are not one continuous motion: locking onto a rail (the grind
* lock has its own blend), entering or leaving a bail (which includes the reset), or a teleport.
* Headings take the short way round. Board roll and yaw are blended as raw values (a double flip
* goes past one turn), except that a completed flip snapping back to zero blends the short way, and
* a snap larger than {@link #MAX_ANGLE_STEP} is not blended. State and hold come from {@code b}.
*/
public static RenderPose lerp(RenderPose a, RenderPose b, float alpha)
{
if (!continuous(a, b))
return b;
float t = PushCycle.clamp01(alpha);
return new RenderPose(
mix(a.x, b.x, t),
mix(a.y, b.y, t),
mix(a.h, b.h, t),
mixAngle(a.heading, b.heading, t),
mixAngle(a.cameraHeading, b.cameraHeading, t),
mixBoardAngle(a.boardRoll, b.boardRoll, t),
mixBoardAngle(a.boardYaw, b.boardYaw, t),
mix(a.boardPitch, b.boardPitch, t),
b.state,
b.hold);
}

private static boolean continuous(RenderPose a, RenderPose b)
{
if (b.state == SkaterState.GRINDING && a.state != SkaterState.GRINDING)
return false;
if ((a.state == SkaterState.BAILED) != (b.state == SkaterState.BAILED))
return false;
return Math.hypot(b.x - a.x, b.y - a.y) <= MAX_LERP_DISTANCE && Math.abs(b.h - a.h) <= MAX_LERP_DISTANCE;
}

private static float mixBoardAngle(float a, float b, float t)
{
float raw = b - a;
if (Math.abs(raw) <= MAX_ANGLE_STEP)
return a + raw * t;
float shortWay = Angles.wrap(raw);
if (Math.abs(shortWay) <= MAX_ANGLE_STEP)
return a + shortWay * t;
return b;
}

static float mix(float a, float b, float t)
{
return a + (b - a) * t;
}

/** {@code t} of the way from heading {@code a} to {@code b}, the short way round. */
static float mixAngle(float a, float b, float t)
{
return Angles.wrap(a + Angles.wrap(b - a) * t);
}
}
