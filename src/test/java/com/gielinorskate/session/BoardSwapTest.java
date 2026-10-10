package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.BoardTransition;
import com.gielinorskate.physics.SkateMode;
import org.junit.Test;

/** Getting on and off, dropping, picking up, calling back and jump-mounting. */
public class BoardSwapTest
{
	private static final float TILE = 128f;
	private final BoardSwap s = new BoardSwap();

	/** On foot with the board dropped at (0, 0). */
	private void droppedAtOrigin()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.DROP, s.onDropPickup(0, 0, 5, 1f));
	}

	@Test
	public void startsOnTheBoard()
	{
		assertEquals(SkateMode.ON_BOARD, s.getMode());
		assertEquals(1f, s.getTransitionProgress(), 0f);
	}

	@Test
	public void theBoardKeyStepsOffOnlyOnTheGround()
	{
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyPressed(false, 0, 0));
		assertEquals(SkateMode.ON_BOARD, s.getMode());
		assertEquals(BoardSwap.Action.DISMOUNT, s.onBoardKeyPressed(true, 0, 0));
		assertEquals(SkateMode.ON_FOOT, s.getMode());
		assertEquals(BoardState.CARRIED, s.getBoard());
		assertEquals(BoardTransition.DISMOUNT, s.getTransition());
		assertEquals(0f, s.getTransitionProgress(), 0f);
		s.tick(0.125f);
		assertEquals(0.5f, s.getTransitionProgress(), 1e-5f);
		s.tick(1f);
		assertEquals(1f, s.getTransitionProgress(), 0f);
	}

	@Test
	public void theBoardKeyWithTheBoardCarriedHopsOn()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.MOUNT_CARRIED, s.onBoardKeyPressed(true, 500, 500));
		assertEquals(SkateMode.ON_BOARD, s.getMode());
		assertEquals(BoardTransition.MOUNT, s.getTransition());
	}

	@Test
	public void theBoardKeyInTheAirDoesNotMount()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyPressed(false, 0, 0));
		assertEquals(SkateMode.ON_FOOT, s.getMode());
	}

	@Test
	public void theBoardKeyInTheAirDoesNotStepOnADroppedBoard()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyPressed(false, 0, TILE));
		assertEquals(SkateMode.ON_FOOT, s.getMode());
		assertEquals(BoardSwap.Action.MOUNT_DROPPED, s.onBoardKeyPressed(true, 0, TILE));
	}

	@Test
	public void aTapCallsAFarBoardBackEveryTime()
	{
		droppedAtOrigin();
		float far = 3 * TILE;
		assertEquals(BoardSwap.Action.CALL_BACK, s.onBoardKeyPressed(true, 0, far));
		assertEquals(BoardState.CARRIED, s.getBoard());
		assertEquals(SkateMode.ON_FOOT, s.getMode());
		assertEquals(BoardSwap.Action.DROP, s.onDropPickup(0, 0, 0, 0));
		assertEquals(BoardSwap.Action.CALL_BACK, s.onBoardKeyPressed(true, 0, far));
		assertEquals(BoardState.CARRIED, s.getBoard());
	}

	@Test
	public void aTapInTheAirStillCallsAFarBoardBack()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.CALL_BACK, s.onBoardKeyPressed(false, 0, 3 * TILE));
		assertEquals(BoardState.CARRIED, s.getBoard());
	}

	@Test
	public void dropAndPickUpWithinOneAndAHalfTiles()
	{
		droppedAtOrigin();
		assertEquals(BoardState.DROPPED, s.getBoard());
		assertEquals(5f, s.getBoardH(), 0f);
		assertEquals(1f, s.getBoardHeading(), 0f);
		assertEquals(BoardSwap.Action.NONE, s.onDropPickup(1.5f * TILE + 1, 0, 0, 0));
		assertEquals(BoardState.DROPPED, s.getBoard());
		assertEquals(BoardSwap.Action.PICK_UP, s.onDropPickup(1.5f * TILE - 1, 0, 0, 0));
		assertEquals(BoardState.CARRIED, s.getBoard());
		assertEquals(SkateMode.ON_FOOT, s.getMode());
	}

	@Test
	public void theBoardKeyStepsOnADroppedBoardWithinReach()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.MOUNT_DROPPED, s.onBoardKeyPressed(true, 0, 1.5f * TILE));
		assertEquals(SkateMode.ON_BOARD, s.getMode());
	}

	@Test
	public void justOutOfReachATapCallsTheBoardBack()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.CALL_BACK, s.onBoardKeyPressed(true, 0, 1.5f * TILE + 1));
		assertEquals(BoardState.CARRIED, s.getBoard());
		assertEquals(SkateMode.ON_FOOT, s.getMode());
		// with it in hand a hold does nothing more
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyHeld());
	}

	@Test
	public void aHoldActsLikeATapForADroppedBoard()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.CALL_BACK, s.onBoardKeyHeld());
		assertEquals(BoardState.CARRIED, s.getBoard());
	}

	@Test
	public void aHoldOnTheBoardDoesNothing()
	{
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyHeld());
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.NONE, s.onBoardKeyHeld());
	}

	@Test
	public void aJumpStartingWithinATileOfTheDroppedBoardMounts()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.JUMP_MOUNT, s.onLanded(TILE, 0, 3 * TILE, 0, false));
		assertEquals(SkateMode.ON_BOARD, s.getMode());
	}

	@Test
	public void aJumpLandingWithinATileOfTheDroppedBoardMounts()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.JUMP_MOUNT, s.onLanded(3 * TILE, 0, 0, TILE - 1, false));
	}

	@Test
	public void aJumpAwayFromTheDroppedBoardDoesNotMount()
	{
		droppedAtOrigin();
		assertEquals(BoardSwap.Action.NONE, s.onLanded(TILE + 1, 0, 3 * TILE, 0, true));
		assertEquals(SkateMode.ON_FOOT, s.getMode());
	}

	@Test
	public void aSprintingJumpWithTheBoardCarriedMounts()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.NONE, s.onLanded(0, 0, 50, 0, false));
		assertEquals(SkateMode.ON_FOOT, s.getMode());
		assertEquals(BoardSwap.Action.JUMP_MOUNT, s.onLanded(0, 0, 50, 0, true));
		assertEquals(SkateMode.ON_BOARD, s.getMode());
	}

	@Test
	public void aFallNeverMounts()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertEquals(BoardSwap.Action.NONE, s.onLanded(Float.NaN, Float.NaN, 0, 0, true));
	}

	@Test
	public void jumpMountSpeedIsCappedToPushTopSpeed()
	{
		assertEquals(1500f, BoardSwap.jumpMountSpeed(2000f, true, 1500f), 0f);
		assertEquals(1500f, BoardSwap.jumpMountSpeed(2000f, false, 1500f), 0f);
	}

	@Test
	public void aSprintingJumpMountRollsAwayWithRealSpeed()
	{
		// a sprint (853) lands rolling at least 65% of the push top speed
		assertEquals(0.65f * 1500f, BoardSwap.jumpMountSpeed(853f, true, 1500f), 0.01f);
		assertEquals(BoardSwap.SPRINT_MOUNT_FRACTION * 1500f, BoardSwap.jumpMountSpeed(400f, true, 1500f), 0.01f);
		// faster than the floor (momentum carried off a slope or the board): kept
		assertEquals(1200f, BoardSwap.jumpMountSpeed(1200f, true, 1500f), 0.01f);
	}

	@Test
	public void aMovingJumpMountRollsAtLeastAThirdOfTopSpeed()
	{
		assertEquals(BoardSwap.MOVING_MOUNT_FRACTION * 1500f, BoardSwap.jumpMountSpeed(320f, false, 1500f), 0.01f);
		assertTrue(BoardSwap.MOVING_MOUNT_FRACTION < BoardSwap.SPRINT_MOUNT_FRACTION);
	}

	@Test
	public void aStandingHopOntoTheBoardGetsNoBoost()
	{
		assertEquals(10f, BoardSwap.jumpMountSpeed(10f, false, 1500f), 0f);
		assertEquals(10f, BoardSwap.jumpMountSpeed(10f, true, 1500f), 0f);
		assertEquals(0f, BoardSwap.jumpMountSpeed(-5f, true, 1500f), 0f);
	}

	@Test
	public void steppingOnWhileMovingKeepsTheWalkersSpeed()
	{
		assertEquals(853f, BoardSwap.stepOnSpeed(853f, 1500f), 0f);
		assertEquals(1500f, BoardSwap.stepOnSpeed(2000f, 1500f), 0f);
		assertEquals(0f, BoardSwap.stepOnSpeed(-1f, 1500f), 0f);
	}

	@Test
	public void aSprintingJumpWithTheBoardCarriedWillMount()
	{
		s.onBoardKeyPressed(true, 0, 0);
		assertTrue(s.willJumpMount(true, 0, 0));
		assertFalse(s.willJumpMount(false, 0, 0));
	}

	@Test
	public void aJumpFromNextToTheDroppedBoardWillMount()
	{
		droppedAtOrigin();
		assertTrue(s.willJumpMount(false, BoardSwap.JUMP_MOUNT_RADIUS - 1f, 0));
		assertFalse(s.willJumpMount(true, BoardSwap.JUMP_MOUNT_RADIUS + 50f, 0));
	}

	@Test
	public void onTheBoardNoJumpIsAMountJump()
	{
		assertFalse(s.willJumpMount(true, 0, 0));
	}

	@Test
	public void resetPutsTheSkaterBackOnTheBoard()
	{
		droppedAtOrigin();
		s.reset();
		assertEquals(SkateMode.ON_BOARD, s.getMode());
		assertEquals(BoardState.CARRIED, s.getBoard());
	}
}
