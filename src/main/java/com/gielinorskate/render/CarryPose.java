package com.gielinorskate.render;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.FootPhysics;

/**
 * The carried board: held by its middle in the right hand at hip height (the hand anchor), turned nearly on its
 * edge with the deck facing out and the wheels toward the leg, its length along the walk and tilted, the more so
 * the faster the walk. Pure placement maths for the board object.
 */
public final class CarryPose
{
	/** The hand anchor: units to the right of the body's centre line. */
	static final float HAND_SIDE = 30f;
	/** The hand anchor: units above the soles (hip height of a 200-unit character, MeshDeformer.HIP_FRACTION). */
	static final float HAND_HEIGHT = 85f;
	/** Board-model y of the held point, the middle of the board between the wheels' bottoms and the deck top. */
	static final float HELD_Y = -9f;
	/** Roll about the board's long axis: a little short of on its edge, deck out to the right. */
	static final float ROLL = -(Angles.PI / 2 - 0.15f);
	/** Tilt along the walk standing, and the most at a sprint (radians). */
	static final float STILL_TILT = 0.12f;
	static final float SPRINT_TILT = 0.35f;

	private CarryPose()
	{
	}

	/** The board's roll while carried. */
	public static float roll()
	{
		return ROLL;
	}

	/** The tilt moves in this many steps from standing to a sprint (about 0.02 rad each). */
	static final int TILT_STEPS = 12;

	/**
	 * The board's tilt along the walk at {@code speed} u/s, in {@link #TILT_STEPS} steps: the board's mesh is rebuilt
	 * whenever its pose changes, so a speed that wobbles every frame (a ghost's dead reckoning) must not move it.
	 */
	public static float pitch(float speed)
	{
		float f = Math.max(0f, Math.min(1f, speed / FootPhysics.SPRINT_SPEED));
		float stepped = Math.round(f * TILT_STEPS) / (float) TILT_STEPS;
		return STILL_TILT + (SPRINT_TILT - STILL_TILT) * stepped;
	}

	/**
	 * Where the board object goes so its middle is in the hand of a walker with feet at ({@code x}, {@code y},
	 * up-positive {@code h}) facing {@code heading} at {@code speed}.
	 */
	public static void place(float x, float y, float h, float heading, float speed, Placement out)
	{
		placeAt(x, y, h, heading, speed, -HAND_SIDE, -HAND_HEIGHT, 0f, out);
	}

	/**
	 * Where the board object goes so its middle is in a hand at ({@code mx}, {@code my}, {@code mz}) in the model
	 * space of a walker body drawn facing {@code heading} with its soles at ({@code x}, {@code y}, up-positive
	 * {@code h}): y down, the body facing -z, its right hand on the -x side ({@link HandAnchor}).
	 */
	public static void placeAt(float x, float y, float h, float heading, float speed, float mx, float my, float mz,
		Placement out)
	{
		float roll = roll();
		float pitch = pitch(speed);
		int jau = Angles.toJau(heading);
		float[] c = BoardPlacement.pose(new float[]{0f, HELD_Y, 0f}, roll, pitch);
		// RuneLite's model-to-local rotation (see MeshDeformer), for the held point and the hand alike
		double theta = jau * Math.PI / 1024.0;
		float cos = (float) Math.cos(theta);
		float sin = (float) Math.sin(theta);
		float dx = c[0] * cos + c[2] * sin;
		float dy = c[2] * cos - c[0] * sin;
		float handX = x + mx * cos + mz * sin;
		float handY = y + mz * cos - mx * sin;
		float handZ = -h + my;
		out.set(handX - dx, handY - dy, handZ - c[1], jau, roll, pitch);
	}

	/** Falling this fast (u/s) in a mount hop, the board is all the way down under the feet. */
	static final float MOUNT_DROP_SPEED = 0.6f * FootPhysics.MOUNT_JUMP_VH;

	/**
	 * How far (0 in the hand .. 1 under the feet) the board has come down in a jump onto the carried board, by the
	 * vertical speed: in the hand on the way up, put under the feet on the way down, so the landing is on it.
	 */
	public static float mountDrop(float verticalSpeed)
	{
		return PushCycle.smoothstep(0f, MOUNT_DROP_SPEED, -verticalSpeed);
	}

	/** The board flat under the feet of a walker with soles at up-positive {@code h}, nose along {@code heading}. */
	public static void underFeet(float x, float y, float h, float heading, Placement out)
	{
		out.set(x, y, -(h - BoardGeometry.BOARD_TOP - BoardPlacement.FOOT_CLEARANCE), Angles.toJau(heading), 0f, 0f);
	}
}
