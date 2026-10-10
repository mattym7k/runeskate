package com.gielinorskate.render;

/**
 * The procedural body pose layered on the skater's stance animation, in the puppet's model space (x
 * along the board, y down, z out of the back: the body faces -z). Written by {@link SkaterPoseRig} on the
 * client thread each frame and read by {@link PuppetController} when the client draws the puppet.
 */
public final class BodyPose
{
	private static final float EPSILON = 1e-4f;

	/** Radians about the board's long axis (through the feet); negative leans the head toward the chest. */
	public float roll;
	/** Radians about the contact truck, the same sense as the board's pitch. */
	public float pitch;
	/** Model x of the pitch pivot (the contact truck). */
	public float pivotX;
	/** Leg length factor: below 1 bends the knees (squash), above 1 extends them (stretch). */
	public float legScale = 1f;
	/** Radians the upper body folds forward at the hips (toward the chest). */
	public float torsoBend;
	/** Radians the upper body tips at the hips along the board, the pitch sense: positive tips the head toward +x. */
	public float torsoLean;

	/** 0..1: how much the push leg follows the foot target below instead of the plain crouch. */
	public float legWeight;
	/** The side of the body's centre plane (sign of model x) the push leg is on: -1 or +1. */
	public float legSide = -1f;
	/** Push-foot target, model space offset from where the foot rests (y down: positive is below the soles). */
	public float footX;
	public float footY;
	public float footZ;

	/**
	 * Radians the whole body turns about the centre of mass (a front or back flip), the pitch sense: positive
	 * tips the head toward +x. Raw, not wrapped, so it never snaps at a full turn.
	 */
	public float flip;
	/** Model y of the flip pivot (the centre of mass), y down. */
	public float flipPivotY;

	/** 0..1: how far the grabbing arm has reached for the board (0: the arm as animated). */
	public float armWeight;
	/** The grabbing arm's side of the body (sign of model x): +1 the left hand, -1 the right. */
	public float armSide = -1f;
	/** The grab point: units along the board (+ toward the nose), across it (- the toe edge), board-model height. */
	public float grabAlong;
	public float grabAcross;
	public float grabBoardY;

	/**
	 * The board as drawn this frame (written by the renderer, not the rig): roll, pitch and the deck lift a pitch
	 * gives the soles. The grabbing hand goes to the grab point on the board as it is actually drawn.
	 */
	public float boardRoll;
	public float boardPitch;
	public float deckLift;
	/**
	 * Units the board is drawn above its physics place this frame (a grab's {@link #feetLift}, rounded; written by
	 * the renderer, not the rig): the feet fold up to it and the hand aims at the board there.
	 */
	public float boardLift;

	/**
	 * 0..1: how far the knees fold up toward the chest in a grab (the thighs swing forward, the shins back, the feet
	 * stay flat) instead of the plain squash; the arms also go with the upper body rather than the legs. 0 everywhere
	 * else.
	 */
	public float kneeFold;
	/** Units the rig wants the feet (and the board under them) pulled up toward the hips: a grab's knees-up. */
	public float feetLift;

	public void neutral()
	{
		roll = 0f;
		pitch = 0f;
		pivotX = 0f;
		legScale = 1f;
		torsoBend = 0f;
		torsoLean = 0f;
		legWeight = 0f;
		legSide = -1f;
		footX = 0f;
		footY = 0f;
		footZ = 0f;
		flip = 0f;
		flipPivotY = 0f;
		armWeight = 0f;
		armSide = -1f;
		grabAlong = 0f;
		grabAcross = 0f;
		grabBoardY = 0f;
		boardRoll = 0f;
		boardPitch = 0f;
		deckLift = 0f;
		boardLift = 0f;
		kneeFold = 0f;
		feetLift = 0f;
	}

	/** True when the pose would not visibly change the mesh. */
	public boolean isNeutral()
	{
		return Math.abs(roll) < EPSILON && Math.abs(pitch) < EPSILON && Math.abs(legScale - 1f) < EPSILON
			&& Math.abs(torsoBend) < EPSILON && Math.abs(torsoLean) < EPSILON && legWeight < EPSILON
			&& Math.abs(flip) < EPSILON && armWeight < EPSILON && kneeFold < EPSILON && boardLift < EPSILON;
	}
}
