package com.gielinorskate.party;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;

/** The local skater's state on one frame, in absolute coordinates, ready to encode. Pure value. */
public final class GhostFrame
{
	public final int world;
	public final int plane;
	/** Absolute: world tile * 128 + sub-tile. */
	public final float x;
	public final float y;
	/** Up-positive. */
	public final float h;
	public final float heading;
	public final float vx;
	public final float vy;
	public final float vh;
	public final SkaterState state;
	/** Current grab, manual or grind, or null. */
	public final Trick hold;
	/** Flip turning the board now, or null. */
	public final Trick flipTrick;
	/** Progress 0..1 of that flip. */
	public final float flipProgress;
	/** Heading turn rate, rad/s, + = clockwise from above; without a flip's own body turn (TurnRateMeter). */
	public final float turnRate;
	/** Front / back flip angle of the whole skater, radians, + = frontflip (SkatePhysics.getBodyFlipAngle). */
	public final float bodyFlip;
	/** Its rate, rad/s (BodyFlipMeter). */
	public final float bodyFlipRate;
	/** Off the board (the position is the walker's): the board CARRIED or DROPPED; null on the board. */
	public final BoardState offBoard;
	/** A DROPPED board: absolute position, up-positive height, nose heading. */
	public final float boardX;
	public final float boardY;
	public final float boardH;
	public final float boardHeading;
	/** Pop charge 0..1 (the crouch before a pop); 0 when none. */
	public final float charge;
	/**
	 * Knocked off the board: the knockdown's stage (the position is the body's lowest point, the heading its facing,
	 * the board DROPPED where it is); null otherwise.
	 */
	public final KnockdownPose.Stage knockStage;
	/** The tumble angle the knocked-off body comes to lie at (radians, + tips the head toward the facing). */
	public final float knockLie;

	public GhostFrame(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, Trick flipTrick, float flipProgress)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress, 0f);
	}

	public GhostFrame(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, Trick flipTrick, float flipProgress, float turnRate)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress, turnRate, 0f, 0f);
	}

	public GhostFrame(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, Trick flipTrick, float flipProgress, float turnRate, float bodyFlip,
		float bodyFlipRate)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress, turnRate, bodyFlip,
			bodyFlipRate, null, 0f, 0f, 0f, 0f, 0f, null, 0f);
	}

	/**
	 * A walker (off the board) at the feet ({@code x}, {@code y}, {@code h}), moving at ({@code vx}, {@code vy},
	 * {@code vh}), with its board CARRIED or DROPPED at absolute ({@code boardX}, {@code boardY}, {@code boardH})
	 * nose toward {@code boardHeading}.
	 */
	public static GhostFrame onFoot(int world, int plane, float x, float y, float h, float heading, float vx, float vy,
		float vh, boolean airborne, float turnRate, BoardState board, float boardX, float boardY, float boardH,
		float boardHeading)
	{
		return new GhostFrame(world, plane, x, y, h, heading, vx, vy, vh,
			airborne ? SkaterState.AIRBORNE : SkaterState.ROLLING, null, null, 0f, turnRate, 0f, 0f,
			board == null ? BoardState.CARRIED : board, boardX, boardY, boardH, boardHeading, 0f, null, 0f);
	}

	/**
	 * Knocked off the board in {@code stage}: the body's lowest point at ({@code x}, {@code y}, {@code h}) facing
	 * {@code facing}, moving at ({@code vx}, {@code vy}) and in the air or not, to lie tumbled by {@code lieAngle}
	 * (radians, + tips the head toward the facing); the board where it is. Receivers time the tumble, lie-down and
	 * get-up themselves (GhostKnockdown), so no vertical speed, angle or progress goes: the update stays small.
	 */
	public static GhostFrame knockdown(int world, int plane, float x, float y, float h, float facing, float vx,
		float vy, boolean airborne, KnockdownPose.Stage stage, float lieAngle, float boardX, float boardY,
		float boardH, float boardHeading)
	{
		return new GhostFrame(world, plane, x, y, h, facing, vx, vy, 0f,
			airborne ? SkaterState.AIRBORNE : SkaterState.BAILED, null, null, 0f, 0f, 0f, 0f,
			BoardState.DROPPED, boardX, boardY, boardH, boardHeading, 0f, stage, lieAngle);
	}

	/** This frame with the pop charge {@code charge} (0..1). */
	public GhostFrame withCharge(float charge)
	{
		return new GhostFrame(world, plane, x, y, h, heading, vx, vy, vh, state, hold, flipTrick, flipProgress,
			turnRate, bodyFlip, bodyFlipRate, offBoard, boardX, boardY, boardH, boardHeading, charge, knockStage,
			knockLie);
	}

	private GhostFrame(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, Trick flipTrick, float flipProgress, float turnRate, float bodyFlip,
		float bodyFlipRate, BoardState offBoard, float boardX, float boardY, float boardH, float boardHeading,
		float charge, KnockdownPose.Stage knockStage, float knockLie)
	{
		this.charge = charge;
		this.knockStage = knockStage;
		this.knockLie = knockLie;
		this.offBoard = offBoard;
		this.boardX = boardX;
		this.boardY = boardY;
		this.boardH = boardH;
		this.boardHeading = boardHeading;
		this.turnRate = turnRate;
		this.bodyFlip = bodyFlip;
		this.bodyFlipRate = bodyFlipRate;
		this.world = world;
		this.plane = plane;
		this.x = x;
		this.y = y;
		this.h = h;
		this.heading = heading;
		this.vx = vx;
		this.vy = vy;
		this.vh = vh;
		this.state = state;
		this.hold = hold;
		this.flipTrick = flipTrick;
		this.flipProgress = flipProgress;
	}
}
