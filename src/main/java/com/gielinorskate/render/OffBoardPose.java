package com.gielinorskate.render;

import com.gielinorskate.physics.*;
import lombok.AllArgsConstructor;

/**
 * Everything drawn of the on-foot side of a session, one snapshot per frame: the mode, where the board is, the
 * walker, and the mount / dismount blend. Immutable and pure; the walker fields can be blended between two
 * fixed steps ({@link #lerp}). While ON_BOARD the walker fields hold the skater's position (so a dismount blend
 * starts from it) and the board fields are unused.
 */
@AllArgsConstructor
public final class OffBoardPose
{
	public final SkateMode mode;
	/** CARRIED or DROPPED; CARRIED while ON_BOARD. */
	public final BoardState board;
	/** The dropped board: where it lies (feet height of the ground there) and which way its nose points. */
	public final float boardX;
	public final float boardY;
	public final float boardH;
	public final float boardHeading;
	/** The walker's feet. */
	public final float walkerX;
	public final float walkerY;
	public final float walkerH;
	/** Where the body faces (0 = north, clockwise). */
	public final float walkerHeading;
	/** Horizontal speed (u/s): idle / walk / run animation by speed (walk FootPhysics.WALK_SPEED). */
	public final float walkerSpeed;
	/** In the air: a jump or a fall. */
	public final boolean airborne;
	/** The flight is a jump (not a fall); meaningful while airborne. */
	public final boolean jumping;
	/** Up-positive vertical speed (u/s) in the air; 0 on the ground. */
	public final float verticalSpeed;
	/** The latest mount or dismount, and how far through its blend (0..1, 1 when done). */
	public final BoardTransition transition;
	public final float transitionProgress;
	/** The flight is a jump that lands on the carried board (the mount hop): the board comes down under the feet. */
	public final boolean mountJump;

	/**
	 * The walker {@code alpha} (0..1, clamped) of the way from {@code a} (the older step) to {@code b}; every
	 * other field comes from {@code b}. A different mode, or a jump of more than {@link RenderPose#MAX_LERP_DISTANCE},
	 * is not blended.
	 */
	public static OffBoardPose lerp(OffBoardPose a, OffBoardPose b, float alpha)
	{
		if (a == null || a.mode != b.mode
			|| Math.hypot(b.walkerX - a.walkerX, b.walkerY - a.walkerY) > RenderPose.MAX_LERP_DISTANCE
			|| Math.abs(b.walkerH - a.walkerH) > RenderPose.MAX_LERP_DISTANCE)
			return b;
		float t = PushCycle.clamp01(alpha);
		return new OffBoardPose(b.mode, b.board, b.boardX, b.boardY, b.boardH, b.boardHeading,
			RenderPose.mix(a.walkerX, b.walkerX, t), RenderPose.mix(a.walkerY, b.walkerY, t),
			RenderPose.mix(a.walkerH, b.walkerH, t), RenderPose.mixAngle(a.walkerHeading, b.walkerHeading, t),
			b.walkerSpeed, b.airborne, b.jumping, b.verticalSpeed, b.transition,
			b.transitionProgress, b.mountJump);
	}
}
