package com.gielinorskate.render;

import lombok.AllArgsConstructor;

/**
* The knocked-off skater and its board as drawn this frame (the session's knockdown, snapshotted per fixed step and
* blended between steps). Immutable and pure.
*/
@AllArgsConstructor
public final class KnockdownPose
{
public enum Stage
{
/** Thrown off and tumbling. */
AIR,
/** Down, after the impact. */
LIE,
/** Getting up. */
GET_UP
}

public final Stage stage;
/** 0..1 through the lie-down or get-up. */
public final float progress;
/** The body's lowest point (its feet when upright). */
public final float bodyX;
public final float bodyY;
public final float bodyH;
/** Where the body faces (0 = north, clockwise): the tumble turns it about the side axis of this. */
public final float facing;
/** Tumble about the side axis, raw: + tips the head toward the facing. */
public final float bodyAngle;
/** Sideways roll. */
public final float bodyRoll;
/** The board: where its wheels are, its nose, its flip (roll) and end-over-end (pitch), raw. */
public final float boardX;
public final float boardY;
public final float boardH;
public final float boardYaw;
public final float boardRoll;
public final float boardPitch;

/**
* {@code alpha} (0..1, clamped) of the way from {@code a} (the older step) to {@code b}; the stage and progress
* are {@code b}'s. Not blended (b itself) without an a, or across a jump of more than
* {@link RenderPose#MAX_LERP_DISTANCE}.
*/
public static KnockdownPose lerp(KnockdownPose a, KnockdownPose b, float alpha)
{
if (a == null
|| Math.hypot(b.bodyX - a.bodyX, b.bodyY - a.bodyY) > RenderPose.MAX_LERP_DISTANCE
|| Math.abs(b.bodyH - a.bodyH) > RenderPose.MAX_LERP_DISTANCE
|| Math.hypot(b.boardX - a.boardX, b.boardY - a.boardY) > RenderPose.MAX_LERP_DISTANCE)
return b;
float t = PushCycle.clamp01(alpha);
return new KnockdownPose(b.stage, b.progress,
RenderPose.mix(a.bodyX, b.bodyX, t), RenderPose.mix(a.bodyY, b.bodyY, t), RenderPose.mix(a.bodyH, b.bodyH, t),
RenderPose.mixAngle(a.facing, b.facing, t),
RenderPose.mix(a.bodyAngle, b.bodyAngle, t), RenderPose.mix(a.bodyRoll, b.bodyRoll, t),
RenderPose.mix(a.boardX, b.boardX, t), RenderPose.mix(a.boardY, b.boardY, t), RenderPose.mix(a.boardH, b.boardH, t),
RenderPose.mixAngle(a.boardYaw, b.boardYaw, t),
RenderPose.mix(a.boardRoll, b.boardRoll, t), RenderPose.mix(a.boardPitch, b.boardPitch, t));
}

/** How far up the board is drawn above its wheels' height when flipped by {@code roll}: its deck upside down. */
public static float boardLift(float roll)
{
return BoardGeometry.BOARD_TOP * (1f - (float) Math.cos(roll)) / 2f;
}
}
