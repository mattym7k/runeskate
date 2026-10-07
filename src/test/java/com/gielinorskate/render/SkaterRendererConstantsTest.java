package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import com.gielinorskate.session.BoardSwap;
import org.junit.Test;

public class SkaterRendererConstantsTest
{
	@Test
	public void aDroppedOrPickedUpBoardMovesAsLongAsAMount()
	{
		assertEquals(BoardSwap.TRANSITION_SECONDS, SkaterRenderer.BOARD_MOVE_SECONDS, 0f);
	}

	@Test
	public void aBoardInReachMovesButACalledBackOneAppears()
	{
		// stepping on or picking up works within 1.5 tiles: those boards move; a call-back from further appears
		assertEquals(true, SkaterRenderer.BOARD_MOVE_REACH > BoardSwap.PICKUP_RADIUS);
		assertEquals(true, SkaterRenderer.BOARD_MOVE_REACH < 3 * 128f);
	}
}
