package com.gielinorskate.party;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.render.KnockdownPose;

/**
* A knocked-off ghost's tumble, timed on this side from when each stage was first seen (updates carry only the
* stage and the lying angle): a spin from upright onto the lying angle, lying there, then the body easing round to
* the nearest whole turn (upright) through the get-up, as the sender's knockdown draws it. Pure.
*/
final class GhostKnockdown
{
/** The sender's lie-down and get-up (session.Knockdown), seconds. */
static final float LIE_SECONDS = 0.5f;
static final float GET_UP_SECONDS = 0.4f;
/** The tumble reaches its lying angle this long after being thrown off (a typical flight, MIN_SPIN_TIME up). */
static final float SPIN_SECONDS = 0.35f;

private GhostKnockdown()
{
}

/** 0..1 through the lie-down or get-up {@code age} seconds into it; 0 tumbling. */
static float progress(KnockdownPose.Stage stage, float age)
{
return stage == KnockdownPose.Stage.AIR ? 0f
: GhostCodec.clamp(age / (stage == KnockdownPose.Stage.LIE ? LIE_SECONDS : GET_UP_SECONDS), 0f, 1f);
}

/** The body's tumble angle {@code age} seconds into {@code stage}, lying at {@code lie}. */
static float angle(KnockdownPose.Stage stage, float lie, float age)
{
switch (stage)
{
case AIR:
return lie * GhostCodec.clamp(age / SPIN_SECONDS, 0f, 1f);
case LIE:
return lie;
default:
float u = progress(stage, age);
float s = u * u * (3f - 2f * u);
float upright = Math.round(lie / Angles.TWO_PI) * Angles.TWO_PI;
return lie + (upright - lie) * s;
}
}
}
