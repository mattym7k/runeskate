package com.gielinorskate.render;

import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;

/**
 * Pure placement maths for a pitched or sliding board, in board model space (long axis z, y negative =
 * up, wheels resting on y = 0). A pitched board turns about the axle of the truck that stays down (the
 * tail truck for nose-up manuals and 5-0s, the nose truck for nose manuals and nosegrinds), so the
 * contact wheels stay on the ground or rail instead of sinking 30 * sin(pitch) into it as they would
 * turning about the board's centre.
 */
public final class BoardPlacement
{
	/** Distance of each truck's axle from the board's centre along z. */
	static final float TRUCK_Z = 30f;
	/** Axle height: wheel centre, one wheel radius (4.5) above the ground. */
	static final float AXLE_Y = -4.5f;
	/**
	 * Boardslides and crooked grinds: physics puts the wheels on the rail top, but the deck sits on it, so
	 * the board and skater are drawn this much lower. Deck underside at y = -16 + 3 = -13.
	 */
	static final float SLIDE_DROP = 13f;
	/** Gap between the deck surface and the soles so feet never sink into the board (the crouch animation already lowers the body). */
	public static final int FOOT_CLEARANCE = 4;
	/** Height of the skater's centre of mass above the deck top: the pivot of front and back flips. */
	static final float COM_HEIGHT = 45f;

	private BoardPlacement()
	{
	}

	/** z of the pivot axle: the tail truck (-z) when the nose is up, the nose truck when it is down. */
	static float pivotZ(float pitch)
	{
		return pitch > 0f ? -TRUCK_Z : pitch < 0f ? TRUCK_Z : 0f;
	}

	/** Rolls {@code tris} about the long axis, then pitches it about the contact truck's axle. Returns a new array. */
	public static float[] pose(float[] tris, float roll, float pitch)
	{
		return poseInto(tris, roll, pitch, new float[tris.length]);
	}

	/** As {@link #pose(float[], float, float)}, writing into {@code out} (not {@code tris}). Returns {@code out}. */
	static float[] poseInto(float[] tris, float roll, float pitch, float[] out)
	{
		BoardGeometry.rotateInto(tris, roll, 0f, out);
		if (pitch == 0f)
		{
			return out;
		}
		float pz = pivotZ(pitch);
		float cp = (float) Math.cos(pitch);
		float sp = (float) Math.sin(pitch);
		for (int i = 0; i < tris.length; i += 3)
		{
			float y = out[i + 1] - AXLE_Y;
			float z = out[i + 2] - pz;
			out[i + 1] = y * cp - z * sp + AXLE_Y;
			out[i + 2] = y * sp + z * cp + pz;
		}
		return out;
	}

	/**
	 * Rolls and pitches {@code tris} like {@link #pose(float[], float, float)}, then turns the whole board
	 * by {@code flip} radians about the skater's centre of mass at board-model height {@code pivotY} (a front
	 * or back flip of skater and board together). The rotation has the sense of the pitch: positive raises
	 * the +z end. Returns a new array.
	 */
	public static float[] pose(float[] tris, float roll, float pitch, float flip, float pivotY)
	{
		return poseInto(tris, roll, pitch, flip, pivotY, new float[tris.length]);
	}

	/** As {@link #pose(float[], float, float, float, float)}, writing into {@code out} (not {@code tris}). Returns {@code out}. */
	static float[] poseInto(float[] tris, float roll, float pitch, float flip, float pivotY, float[] out)
	{
		poseInto(tris, roll, pitch, out);
		if (flip == 0f)
		{
			return out;
		}
		float c = (float) Math.cos(flip);
		float s = (float) Math.sin(flip);
		for (int i = 0; i < tris.length; i += 3)
		{
			float y = out[i + 1] - pivotY;
			float z = out[i + 2];
			out[i + 1] = y * c - z * s + pivotY;
			out[i + 2] = y * s + z * c;
		}
		return out;
	}

	/**
	 * The flip pivot in the puppet's model space (y down, soles at 0): {@link #COM_HEIGHT} above the deck
	 * top, which is {@link #FOOT_CLEARANCE} below the soles.
	 */
	public static float puppetFlipPivotY()
	{
		return FOOT_CLEARANCE - COM_HEIGHT;
	}

	/**
	 * The same pivot in the board's model space. The renderer draws the puppet's soles BOARD_TOP +
	 * FOOT_CLEARANCE + {@code deckLift} above the board's origin (both share the slide drop and the charge
	 * dip), so the puppet's y maps to board y - (BOARD_TOP + FOOT_CLEARANCE + deckLift).
	 */
	public static float boardFlipPivotY(float deckLift)
	{
		return -(BoardGeometry.BOARD_TOP + FOOT_CLEARANCE + deckLift) + puppetFlipPivotY();
	}

	/** How far (up-positive units) the centre of the deck top rises when pitched about the contact truck. */
	public static float deckLift(float pitch)
	{
		if (pitch == 0f)
		{
			return 0f;
		}
		float pz = pivotZ(pitch);
		float y = -BoardGeometry.BOARD_TOP - AXLE_Y;
		float z = -pz;
		float pitchedY = y * (float) Math.cos(pitch) - z * (float) Math.sin(pitch) + AXLE_Y;
		return -BoardGeometry.BOARD_TOP - pitchedY;
	}

	/**
	 * Whole units a grab draws the board above its physics place: the rig's knees-up ({@link BodyPose#feetLift}),
	 * which the feet fold up to ({@link BodyPose#boardLift}); 0 in a bail or for anything not finite. Render only.
	 */
	public static int grabLift(float feetLift, boolean bailed)
	{
		if (bailed || !(feetLift > 0f) || Float.isInfinite(feetLift))
		{
			return 0;
		}
		return Math.round(Math.min(feetLift, MAX_GRAB_LIFT));
	}

	/** A grab never lifts the board further than this above its physics place. */
	static final float MAX_GRAB_LIFT = 40f;

	/** Units to lower the board and skater: {@link #SLIDE_DROP} in a slide or crooked-family grind, else 0. */
	public static float slideDrop(SkaterState state, Trick hold)
	{
		if (state != SkaterState.GRINDING || hold == null)
		{
			return 0f;
		}
		switch (hold)
		{
			case BOARDSLIDE:
			case NOSESLIDE:
			case TAILSLIDE:
			case LIPSLIDE:
			case CROOKED:
			case SMITH:
			case FEEBLE:
				return SLIDE_DROP;
			default:
				return 0f;
		}
	}
}
