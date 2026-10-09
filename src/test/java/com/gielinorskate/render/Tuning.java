package com.gielinorskate.render;

import com.gielinorskate.physics.FootPhysics;

/** The render tuning values the tests check against (the main code has them inline). */
final class Tuning
{
	// SkaterPoseRig: crouch amounts (1 - legScale; negative extends the legs) and the other channels
	static final float BODY_PITCH_FRACTION = 0.7f;
	static final float GRIND_CROUCH = 0.14f;
	static final float POP_EXTEND = -0.18f;
	static final float AIR_TUCK = 0.26f;
	static final float TRICK_TUCK = 0.38f;
	static final float FOOT_JUMP_TUCK = 0.2f;
	static final float FOOT_FALL_TUCK = 0.05f;
	static final float LAND_REACH_SPEED = 350f;
	static final float LAND_REACH = 0.05f;
	static final float TORSO_BEND_PER_CROUCH = 0.9f;
	static final float LANDING_COMPRESSION = 0.22f;
	static final float MIN_LANDING_COMPRESSION = 0.04f;
	static final float MAX_LANDING_COMPRESSION = 0.3f;
	static final float GRIND_SWAY = 0.05f;
	static final float MANUAL_SWAY = 0.035f;
	static final float BAIL_TUMBLE = 1.35f;
	static final float GRAB_TUCK = 0.48f;
	static final float GRAB_BEND = 0.45f;
	static final float GRAB_LIFT = 20f;

	static final float MAX_GRAB_LIFT = 40f;
	static final float MOUNT_DROP_SPEED = 0.6f * FootPhysics.MOUNT_JUMP_VH;
	static final float STILL_TILT = 0.12f;
	static final float SPRINT_TILT = 0.35f;
	static final float MAX_RATE = 3f;
	static final float TWEAK = 1.6f;
	static final float MIN_HIP = 50f;
	static final float MAX_HIP = 120f;
	static final int MISSING_FRAMES_BEFORE_REVERT = 3;
	static final float STUMBLE_ROLL = 0.22f;
	static final float BOARD_MOVE_SECONDS = 0.25f;
	static final float SINK_DEPTH = 40f;
	static final float HOP = 16f;

	private Tuning()
	{
	}
}
