package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardTransition;
import org.junit.Test;

public class SwapBlendTest
{
	private static Placement at(float x, float z, float jau, float roll)
	{
		Placement p = new Placement();
		p.set(x, 0f, z, jau, roll, 0f);
		return p;
	}

	@Test
	public void aNewTransitionStartsABlendFromWhatWasDrawn()
	{
		assertTrue(SwapBlend.starts(BoardTransition.NONE, 1f, BoardTransition.DISMOUNT, 0f));
		assertTrue(SwapBlend.starts(BoardTransition.DISMOUNT, 1f, BoardTransition.MOUNT, 0.1f));
		// the same one going on is not a new start; a restarted one is
		assertFalse(SwapBlend.starts(BoardTransition.MOUNT, 0.1f, BoardTransition.MOUNT, 0.3f));
		assertTrue(SwapBlend.starts(BoardTransition.MOUNT, 1f, BoardTransition.MOUNT, 0.1f));
		// done or none: nothing to blend
		assertFalse(SwapBlend.starts(BoardTransition.NONE, 1f, BoardTransition.MOUNT, 1f));
		assertFalse(SwapBlend.starts(BoardTransition.MOUNT, 1f, BoardTransition.NONE, 1f));
	}

	@Test
	public void theBlendEasesFromStartToTargetAndEndsExactly()
	{
		SwapBlend b = new SwapBlend();
		Placement fromBody = at(0f, 0f, 0f, 0f);
		Placement fromBoard = at(0f, -80f, 0f, -1.4f);
		b.begin(fromBody, fromBoard, false);
		assertTrue(b.isActive());

		Placement body = at(100f, -20f, 512f, 0f);
		Placement board = at(100f, 0f, 0f, 0f);
		b.apply(0f, body, board);
		assertEquals(0f, body.x, 1e-4f);
		assertEquals(0f, body.jau, 1e-4f);
		assertEquals(-80f, board.z, 1e-4f);
		assertEquals(-1.4f, board.roll, 1e-4f);

		body = at(100f, -20f, 512f, 0f);
		board = at(100f, 0f, 0f, 0f);
		b.apply(0.5f, body, board);
		assertEquals(50f, body.x, 1e-3f);
		assertEquals(256f, body.jau, 1e-3f);
		assertEquals(-0.7f, board.roll, 1e-4f);

		body = at(100f, -20f, 512f, 0f);
		board = at(100f, 0f, 0f, 0f);
		b.apply(1f, body, board);
		assertEquals(100f, body.x, 0f);
		assertEquals(-20f, body.z, 0f);
		assertEquals(512f, body.jau, 0f);
		assertFalse(b.isActive());
	}

	@Test
	public void aMountHopsUpInTheMiddle()
	{
		SwapBlend b = new SwapBlend();
		b.begin(at(0f, 0f, 0f, 0f), at(0f, 0f, 0f, 0f), true);
		Placement body = at(0f, 0f, 0f, 0f);
		Placement board = at(0f, 0f, 0f, 0f);
		b.apply(0.5f, body, board);
		// RuneLite z grows downward
		assertEquals(-Tuning.HOP, body.z, 1e-3f);
		assertEquals(0f, board.z, 0f);
	}

	@Test
	public void turningTakesTheShortWayRound()
	{
		assertEquals(2047.5f, SwapBlend.lerpJau(2040f, 7f, 0.5f), 1e-3f);
		assertEquals(4.75f, SwapBlend.lerpJau(7f, 2046f, 0.25f), 1e-3f);
		assertEquals(2047f, SwapBlend.lerpJau(1f, 2045f, 0.5f), 1e-3f);
		assertEquals(1024f, SwapBlend.lerpJau(512f, 1536f, 0.5f), 1e-3f);
		assertEquals(100f, SwapBlend.lerpJau(100f, 300f, 0f), 0f);
	}

	@Test
	public void easingIsSmoothAndClamped()
	{
		assertEquals(0f, SwapBlend.ease(-1f), 0f);
		assertEquals(1f, SwapBlend.ease(2f), 0f);
		assertEquals(0.5f, SwapBlend.ease(0.5f), 1e-6f);
		assertTrue(SwapBlend.ease(0.1f) < 0.1f);
	}

	@Test
	public void withNothingDrawnBeforeThereIsNoBlend()
	{
		SwapBlend b = new SwapBlend();
		Placement none = new Placement();
		b.begin(none, none, true);
		assertFalse(b.isActive());
		Placement body = at(5f, 0f, 0f, 0f);
		b.apply(0f, body, at(0f, 0f, 0f, 0f));
		assertEquals(5f, body.x, 0f);
	}

	@Test
	public void aBoardOnlyBlendLeavesTheBody()
	{
		SwapBlend b = new SwapBlend();
		b.begin(null, at(0f, -80f, 0f, -1.4f), false);
		Placement body = at(5f, 0f, 0f, 0f);
		Placement board = at(10f, 0f, 0f, 0f);
		b.apply(0.5f, body, board);
		assertEquals(5f, body.x, 0f);
		assertEquals(5f, board.x, 1e-4f);
	}
}
