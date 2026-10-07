package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GrabReachTest
{
	private static float[] target(float along, float across, float by, float forwardX, float roll, float pitch,
		float lift)
	{
		float[] out = new float[3];
		GrabReach.target(along, across, by, forwardX, roll, pitch, lift, out);
		return out;
	}

	@Test
	public void aFlatBoardsToeEdgeIsJustBelowTheSolesOnTheChestSide()
	{
		float[] t = target(-6f, -13f, -14f, 1f, 0f, 0f, 0f);
		assertEquals(-6f, t[0], 1e-4f);
		// board y -14 is 2 under the deck top (-16), which is FOOT_CLEARANCE under the soles
		assertEquals(-14f + BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE, t[1], 1e-4f);
		assertEquals(-13f, t[2], 1e-4f);
	}

	@Test
	public void goofyStanceHasTheNoseOnTheOtherSide()
	{
		assertEquals(-43f, target(43f, 0f, -20f, -1f, 0f, 0f, 0f)[0], 1e-4f);
	}

	@Test
	public void theTargetTurnsWithTheBoardsDrawnRollAndPitch()
	{
		float along = 30f;
		float across = -13f;
		float by = -14f;
		float roll = 0.4f;
		float pitch = 0.3f;
		float lift = BoardPlacement.deckLift(pitch);
		float[] t = target(along, across, by, 1f, roll, pitch, lift);
		// the board's own pose of the same point, mapped to puppet space
		float[] b = BoardPlacement.pose(new float[]{across, by, -along}, roll, pitch);
		assertEquals(-b[2], t[0], 1e-3f);
		assertEquals(b[1] + BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE + lift, t[1], 1e-3f);
		assertEquals(b[0], t[2], 1e-3f);
	}
}
