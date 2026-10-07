package com.gielinorskate.party;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;

/** A party member's skater state decoded from a {@link SkateGhostUpdate}. Pure value. */
public final class GhostState
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
	public final Trick hold;
	/** GhostCodec.EV_* bitmask. */
	public final int events;
	/** The flip in progress (or the TRICK event's trick), or null. */
	public final Trick trick;
	/** Seconds into the trick's nominal duration. */
	public final float flipTime;
	public final int seq;
	/** Heading turn rate, rad/s, + = clockwise from above (a flip's own body turn not included). */
	public final float turnRate;
	/** Front / back flip angle of the whole skater, radians, + = frontflip; 0 off the air. */
	public final float bodyFlip;
	/** Its rate, rad/s: a held flip at least BodyFlipMeter.HELD_RATE, slower is easing onto a whole turn. */
	public final float bodyFlipRate;
	/** Off the board (x / y / h are the walker's): the board CARRIED or DROPPED; null on the board. */
	public final BoardState offBoard;
	/** A DROPPED board: absolute position, up-positive height, nose heading. */
	public final float boardX;
	public final float boardY;
	public final float boardH;
	public final float boardHeading;
	/** Pop charge 0..1 (the crouch before a pop); 0 when none, or from an older version. */
	public final float charge;
	/**
	 * Knocked off the board: the knockdown's stage (x / y / h are the body's lowest point, the heading its facing,
	 * the board DROPPED); null otherwise, or from an older version.
	 */
	public final KnockdownPose.Stage knockStage;
	/** The tumble angle the knocked-off body comes to lie at (radians, + tips the head toward the facing). */
	public final float knockLie;

	public GhostState(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, 0f);
	}

	public GhostState(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, turnRate, 0f, 0f);
	}

	public GhostState(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate,
		float bodyFlip, float bodyFlipRate)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, turnRate, bodyFlip,
			bodyFlipRate, null, 0f, 0f, 0f, 0f);
	}

	public GhostState(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate,
		float bodyFlip, float bodyFlipRate, BoardState offBoard, float boardX, float boardY, float boardH,
		float boardHeading)
	{
		this(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq, turnRate, bodyFlip,
			bodyFlipRate, offBoard, boardX, boardY, boardH, boardHeading, 0f, null, 0f);
	}

	private GhostState(int world, int plane, float x, float y, float h, float heading, float vx, float vy, float vh,
		SkaterState state, Trick hold, int events, Trick trick, float flipTime, int seq, float turnRate,
		float bodyFlip, float bodyFlipRate, BoardState offBoard, float boardX, float boardY, float boardH,
		float boardHeading, float charge, KnockdownPose.Stage knockStage, float knockLie)
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
		this.events = events;
		this.trick = trick;
		this.flipTime = flipTime;
		this.seq = seq;
	}

	/** This state with the body fields: the pop charge, and the knockdown's stage (null: none) and lying angle. */
	public GhostState withBody(float charge, KnockdownPose.Stage knockStage, float knockLie)
	{
		return new GhostState(world, plane, x, y, h, heading, vx, vy, vh, state, hold, events, trick, flipTime, seq,
			turnRate, bodyFlip, bodyFlipRate, offBoard, boardX, boardY, boardH, boardHeading, charge, knockStage,
			knockLie);
	}
}
