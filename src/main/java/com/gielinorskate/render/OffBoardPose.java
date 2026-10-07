package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkateMode;

/**
 * Everything drawn of the on-foot side of a session, one snapshot per frame: the mode, where the board is, the
 * walker, and the mount / dismount blend. Immutable and pure; the walker fields can be blended between two
 * fixed steps ({@link #lerp}). While ON_BOARD the walker fields hold the skater's position (so a dismount blend
 * starts from it) and the board fields are unused.
 */
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
	/** Horizontal speed (u/s): idle / walk / run animation by speed (walk {@link FootPhysics#WALK_SPEED}). */
	public final float walkerSpeed;
	/** Shift held while moving. */
	public final boolean sprinting;
	/** In the air: a jump or a fall. */
	public final boolean airborne;
	/** The flight is a jump (not a fall); meaningful while airborne. */
	public final boolean jumping;
	/** Up-positive vertical speed (u/s) in the air; 0 on the ground. */
	public final float verticalSpeed;
	/** Seconds since take-off (0 on the ground); with verticalSpeed, the jump phase for a tuck. */
	public final float airTime;
	/** The latest mount or dismount, and how far through its blend (0..1, 1 when done). */
	public final BoardTransition transition;
	public final float transitionProgress;
	/** The flight is a jump that lands on the carried board (the mount hop): the board comes down under the feet. */
	public final boolean mountJump;

	public OffBoardPose(SkateMode mode, BoardState board, float boardX, float boardY, float boardH,
		float boardHeading, float walkerX, float walkerY, float walkerH, float walkerHeading, float walkerSpeed,
		boolean sprinting, boolean airborne, boolean jumping, float verticalSpeed, float airTime,
		BoardTransition transition, float transitionProgress)
	{
		this(mode, board, boardX, boardY, boardH, boardHeading, walkerX, walkerY, walkerH, walkerHeading, walkerSpeed,
			sprinting, airborne, jumping, verticalSpeed, airTime, transition, transitionProgress, false);
	}

	public OffBoardPose(SkateMode mode, BoardState board, float boardX, float boardY, float boardH,
		float boardHeading, float walkerX, float walkerY, float walkerH, float walkerHeading, float walkerSpeed,
		boolean sprinting, boolean airborne, boolean jumping, float verticalSpeed, float airTime,
		BoardTransition transition, float transitionProgress, boolean mountJump)
	{
		this.mode = mode;
		this.board = board;
		this.boardX = boardX;
		this.boardY = boardY;
		this.boardH = boardH;
		this.boardHeading = boardHeading;
		this.walkerX = walkerX;
		this.walkerY = walkerY;
		this.walkerH = walkerH;
		this.walkerHeading = walkerHeading;
		this.walkerSpeed = walkerSpeed;
		this.sprinting = sprinting;
		this.airborne = airborne;
		this.jumping = jumping;
		this.verticalSpeed = verticalSpeed;
		this.airTime = airTime;
		this.transition = transition;
		this.transitionProgress = transitionProgress;
		this.mountJump = mountJump;
	}

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
		{
			return b;
		}
		float t = Math.max(0f, Math.min(1f, alpha));
		return new OffBoardPose(b.mode, b.board, b.boardX, b.boardY, b.boardH, b.boardHeading,
			a.walkerX + (b.walkerX - a.walkerX) * t,
			a.walkerY + (b.walkerY - a.walkerY) * t,
			a.walkerH + (b.walkerH - a.walkerH) * t,
			Angles.wrap(a.walkerHeading + Angles.wrap(b.walkerHeading - a.walkerHeading) * t),
			b.walkerSpeed, b.sprinting, b.airborne, b.jumping, b.verticalSpeed, b.airTime, b.transition,
			b.transitionProgress, b.mountJump);
	}
}
