package com.gielinorskate.render;

/**
 * Where a grabbing hand goes: the grab's point on the board ({@link GrabPose}), turned with the board's drawn roll
 * and pitch (the tweak, a flip in progress), in the puppet's model space before the front/back flip (which turns
 * body and board together, so it is applied after the reach). Puppet space: x along the board (+ toward the nose
 * for {@code forwardX} = +1), y down with the soles at 0, z out of the back (the toe edge on the chest side, -z).
 * The board's own model space is (board x, y, board z) = (puppet z, y, -puppet x), with the soles
 * {@code BOARD_TOP + FOOT_CLEARANCE + deckLift} above the board's origin (see {@link BoardPlacement}). Pure,
 * allocation-free.
 */
final class GrabReach
{
	private GrabReach()
	{
	}

	/**
	 * Writes the hand target into {@code out} (x, y, z).
	 *
	 * @param along units along the board, + toward the nose
	 * @param across units across it, - toward the toe edge
	 * @param boardY board-model height of the point (y down)
	 * @param forwardX model x of the nose: +1 regular stance, -1 goofy
	 * @param roll the board's drawn roll (BoardPlacement.pose)
	 * @param pitch the board's drawn pitch
	 * @param deckLift how far the pitched deck lifts the soles (BoardPlacement.deckLift)
	 */
	static void target(float along, float across, float boardY, float forwardX, float roll, float pitch,
		float deckLift, float[] out)
	{
		// puppet (along * forwardX, -, across) -> board (across, boardY, -along * forwardX)
		float bx = across;
		float by = boardY;
		float bz = -along * forwardX;
		// roll about the long axis (BoardGeometry.rotate)
		float cr = (float) Math.cos(roll);
		float sr = (float) Math.sin(roll);
		float x1 = bx * cr - by * sr;
		float y1 = bx * sr + by * cr;
		// pitch about the contact truck's axle (BoardPlacement.pose)
		if (pitch != 0f)
		{
			float pz = BoardPlacement.pivotZ(pitch);
			float cp = (float) Math.cos(pitch);
			float sp = (float) Math.sin(pitch);
			float y = y1 - BoardPlacement.AXLE_Y;
			float z = bz - pz;
			y1 = y * cp - z * sp + BoardPlacement.AXLE_Y;
			bz = y * sp + z * cp + pz;
		}
		out[0] = -bz;
		out[1] = y1 + BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE + deckLift;
		out[2] = x1;
	}
}
