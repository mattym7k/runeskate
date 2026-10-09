package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.SkateMode;
import org.junit.Test;

public class OffBoardPoseTest
{
	private static OffBoardPose walker(SkateMode mode, float x, float heading)
	{
		return new OffBoardPose(mode, BoardState.DROPPED, 1, 2, 3, 4, x, 0, 0, heading, 192f, false, false, 0f,
			BoardTransition.DISMOUNT, 0.5f, false);
	}

	@Test
	public void theWalkerIsBlendedBetweenSteps()
	{
		OffBoardPose a = walker(SkateMode.ON_FOOT, 0, 3.0f);
		OffBoardPose b = walker(SkateMode.ON_FOOT, 4, -3.0f);
		OffBoardPose m = OffBoardPose.lerp(a, b, 0.5f);
		assertEquals(2f, m.walkerX, 1e-5f);
		// the short way round through PI
		assertEquals(Math.PI, Math.abs(m.walkerHeading), 1e-3);
		assertEquals(BoardState.DROPPED, m.board);
		assertEquals(1f, m.boardX, 0f);
	}

	@Test
	public void aModeChangeOrATeleportIsNotBlended()
	{
		OffBoardPose b = walker(SkateMode.ON_FOOT, 4, 0);
		assertSame(b, OffBoardPose.lerp(walker(SkateMode.ON_BOARD, 0, 0), b, 0.5f));
		assertSame(b, OffBoardPose.lerp(walker(SkateMode.ON_FOOT, 400, 0), b, 0.5f));
		assertSame(b, OffBoardPose.lerp(null, b, 0.5f));
	}
}
