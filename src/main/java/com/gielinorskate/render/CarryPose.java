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
	/** The hand anchor: units above the soles (hip height of a 200-unit character, MeshDeformer.hipHeight). */
	static final float HAND_HEIGHT = 85f;
	/** Board-model y of the held point, the middle of the board between the wheels' bottoms and the deck top. */
	static final float HELD_Y = -9f;

	private CarryPose()
	{
	}

	/**
	 * The board's tilt along the walk at {@code speed} u/s, in 12 steps from standing to a sprint (about 0.02 rad
	 * each): the board's mesh is rebuilt whenever its pose changes, so a speed that wobbles every frame (a ghost's dead
	 * reckoning) must not move it.
	 */
	public static float pitch(float speed)
	{
		float stepped = Math.round(PushCycle.clamp01(speed / FootPhysics.SPRINT_SPEED) * 12) / 12f;
		// 0.12 rad standing, 0.35 at a sprint
		return 0.12f + (0.35f - 0.12f) * stepped;
	}

	/**
	 * Where the board object goes so its middle is in a hand at ({@code mx}, {@code my}, {@code mz}) in the model
	 * space of a walker body drawn facing {@code heading} with its soles at ({@code x}, {@code y}, up-positive
	 * {@code h}): y down, the body facing -z, its right hand on the -x side ({@link HandAnchor}). The board rolls a
	 * little short of on its edge, deck out to the right.
	 */
	public static void placeAt(float x, float y, float h, float heading, float speed, float mx, float my, float mz,
		Placement out)
	{
		int jau = Angles.toJau(heading);
		hold(x, y, h, jau, mx, my, mz, jau, -(Angles.PI / 2 - 0.15f), pitch(speed), out);
	}

	/**
	 * Where the board object goes so its held point (the middle, board-model y {@link #HELD_Y}), rolled and pitched
	 * and turned to {@code boardJau}, is at puppet point ({@code mx}, {@code my}, {@code mz}) of a body with its soles
	 * at ({@code x}, {@code y}, up-positive {@code h}) facing {@code bodyJau}.
	 */
	static void hold(float x, float y, float h, int bodyJau, float mx, float my, float mz, int boardJau, float roll,
		float pitch, Placement out)
	{
		float[] c = BoardPlacement.pose(new float[]{0f, HELD_Y, 0f}, roll, pitch);
		// RuneLite's model-to-local rotation (see MeshDeformer), for the held point and the hand alike
		double tb = boardJau * Math.PI / 1024.0;
		float cb = (float) Math.cos(tb);
		float sb = (float) Math.sin(tb);
		double th = bodyJau * Math.PI / 1024.0;
		float ch = (float) Math.cos(th);
		float sh = (float) Math.sin(th);
		out.set(x + mx * ch + mz * sh - (c[0] * cb + c[2] * sb), y + mz * ch - mx * sh - (c[2] * cb - c[0] * sb),
			-h + my - c[1], boardJau, roll, pitch);
	}

	/**
	 * How far (0 in the hand .. 1 under the feet) the board has come down in a jump onto the carried board, by the
	 * vertical speed: in the hand on the way up, put under the feet on the way down, so the landing is on it.
	 */
	public static float mountDrop(float verticalSpeed)
	{
		// all the way down under the feet falling at 0.6 of the mount jump's take-off speed
		return PushCycle.smoothstep(0f, 0.6f * FootPhysics.MOUNT_JUMP_VH, -verticalSpeed);
	}

	/** The board flat under the feet of a walker with soles at up-positive {@code h}, nose along {@code heading}. */
	public static void underFeet(float x, float y, float h, float heading, Placement out)
	{
		out.set(x, y, -(h - BoardGeometry.BOARD_TOP - BoardPlacement.FOOT_CLEARANCE), Angles.toJau(heading), 0f, 0f);
	}
}
