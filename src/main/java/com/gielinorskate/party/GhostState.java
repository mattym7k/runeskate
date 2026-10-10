package com.gielinorskate.party;

import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.KnockdownPose;
import com.gielinorskate.tricks.Trick;
import lombok.AllArgsConstructor;

/**
 * A skater's state: the local skater's on one frame (ready to encode, events and seq 0) or a party member's decoded
 * from a {@link SkateGhostUpdate}. Positions are absolute (world tile * 128 + sub-tile). Pure value.
 */
@AllArgsConstructor
final class GhostState
{
	final int world;
	final int plane;
	final float x;
	final float y;
	/** Up-positive. */
	final float h;
	final float heading;
	final float vx;
	final float vy;
	final float vh;
	final SkaterState state;
	/** Current grab, manual or grind, or null. */
	final Trick hold;
	/** GhostCodec.EV_* bitmask (received only). */
	final int events;
	/** The flip turning the board (received: or the TRICK event's trick), or null. */
	final Trick trick;
	/** Seconds into the trick's nominal duration. */
	final float flipTime;
	final int seq;
	/** Heading turn rate, rad/s, + = clockwise from above (a flip's own body turn not included, TurnRateMeter). */
	final float turnRate;
	/** Front / back flip angle of the whole skater, radians, + = frontflip; 0 off the air. */
	final float bodyFlip;
	/** Its rate, rad/s: a held flip at least BodyFlipMeter.HELD_RATE, slower is easing onto a whole turn. */
	final float bodyFlipRate;
	/** Off the board (x / y / h are the walker's): the board CARRIED or DROPPED; null on the board. */
	final BoardState offBoard;
	/** A DROPPED board: absolute position, up-positive height, nose heading. */
	final float boardX;
	final float boardY;
	final float boardH;
	final float boardHeading;
	/** Pop charge 0..1 (the crouch before a pop); 0 when none, or from an older version. */
	final float charge;
	/**
	 * Knocked off the board: the knockdown's stage (x / y / h are the body's lowest point, the heading its facing,
	 * the board DROPPED); null otherwise, or from an older version.
	 */
	final KnockdownPose.Stage knockStage;
	/** The tumble angle the knocked-off body comes to lie at (radians, + tips the head toward the facing). */
	final float knockLie;
}
