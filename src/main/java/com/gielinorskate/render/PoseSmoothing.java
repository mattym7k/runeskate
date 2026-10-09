package com.gielinorskate.render;

import com.gielinorskate.tricks.Trick;

/** Pure easing curves for the drawn board pose, so nothing visibly jumps in one frame. */
public final class PoseSmoothing
{
/** Peak nose-up pitch of the pop, in radians. */
static final float POP_PITCH = 0.45f;
/** Seconds for the pop pitch to rise to its peak. */
static final float POP_RISE = 0.05f;
/** Seconds for the pop pitch to ease back to flat after the peak. */
static final float POP_FALL = 0.2f;
/** Time constant (seconds) of the carve lean, manual/grind pitch, slide drop and charge dip smoothing. */
public static final float POSE_TAU = 0.08f;
/** A grab's board tweak snaps in faster than the other board poses: 94% of the way in 0.1 s. */
public static final float GRAB_TAU = 0.035f;

private PoseSmoothing()
{
}

/**
* Pop pitch {@code since} seconds after the pop: rises to {@link #POP_PITCH} over {@link #POP_RISE}
* (ease-out), then eases back to 0 over {@link #POP_FALL} (smoothstep). A nollie pops off the nose,
* so its pitch is the same nose-down.
*/
public static float popPitch(float since, boolean nollie)
{
float u = (since - POP_RISE) / POP_FALL;
float pitch = since <= 0f || since >= POP_RISE + POP_FALL ? 0f : since < POP_RISE
? POP_PITCH * (float) Math.sin(Math.PI / 2 * since / POP_RISE) : POP_PITCH * (1f - u * u * (3f - 2f * u));
return nollie ? -pitch : pitch;
}

/** True for the tricks popped off the nose (Nollie, Nollie Kickflip, ...). */
public static boolean isNollie(Trick trick)
{
return trick != null && trick.name().startsWith("NOLLIE");
}

/**
* Exponential smoothing toward {@code target}: closes 1 - exp(-dt / tau) of the gap, so the result
* does not depend on the frame rate and never overshoots.
*/
public static float approach(float current, float target, float dt, float tau)
{
return dt <= 0f ? current : current + (target - current) * (1f - (float) Math.exp(-dt / tau));
}

static final float STUMBLE_TIME = 0.35f;

/**
* Extra board roll {@code since} seconds after a stumble: a sine shaking side to side that dies away
* linearly, 0 at the start and from STUMBLE_TIME on.
*/
public static float stumbleWobble(float since)
{
float u = since / STUMBLE_TIME;
// 0.22 rad at most, 2.5 shakes
return since > 0f && since < STUMBLE_TIME ? 0.22f * (1f - u) * (float) Math.sin(2 * Math.PI * 2.5f * u) : 0f;
}
}
