package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import org.junit.Test;

/**
 * Controller mode's right stick, as AntiMicroX's cursor-mode mouse output: flicks with no button held, and small
 * held tilts as manuals. Speeds are px/ms; the shipped profile's full tilt is about 1.2 px/ms.
 */
public class ControllerStickTest
{
	private static final double FULL = 1.2;

	private final GestureRecognizer gesture = new GestureRecognizer();
	private final ControllerStick stick = new ControllerStick(gesture);

	private StickSim sim()
	{
		return new StickSim(stick::move, 400, 300, 1000);
	}

	private StickSim grabbing()
	{
		return new StickSim(stick::blockedMove, 400, 300, 1000);
	}

	@Test
	public void aFullPullDownThenFlickUpWithNoButtonIsAnOllie()
	{
		StickSim s = sim().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		stick.tick(s.ms);
		Gesture g = gesture.poll();
		assertEquals(Direction.UP, g.direction);
		assertFalse(g.nollie);
		assertNull(gesture.poll());
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
		assertFalse("the stroke ends once the stick is back at centre", stick.isActive());
		assertFalse(gesture.isStrokeActive());
	}

	@Test
	public void aPushUpThenFlickDownIsANollie()
	{
		StickSim s = sim().tilt(0, -FULL, 80).tilt(0, FULL, 80).rest(100);
		stick.tick(s.ms);
		Gesture g = gesture.poll();
		assertEquals(Direction.DOWN, g.direction);
		assertTrue(g.nollie);
	}

	@Test
	public void aFlickUpLeftIsAKickflipDirection()
	{
		StickSim s = sim().tilt(0, FULL, 80).tilt(-0.85, -0.85, 80).rest(100);
		stick.tick(s.ms);
		assertEquals(Direction.UP_LEFT, gesture.poll().direction);
	}

	@Test
	public void aFlickWithAShortPauseAtTheTurnaroundStillCounts()
	{
		// passing through the stick's dead zone takes a moment: no cursor events for a few ms
		StickSim s = sim().tilt(0, FULL, 80).rest(30).tilt(0, -FULL, 80).rest(100);
		stick.tick(s.ms);
		assertEquals(Direction.UP, gesture.poll().direction);
	}

	@Test
	public void aSmallTiltUpHeldIsAManualUntilTheStickIsReleased()
	{
		StickSim s = sim().tilt(0, -0.12, 200);
		stick.tick(s.ms);
		assertEquals("not before 0.25 s", ControllerStick.Manual.NONE, stick.manual());
		s.tilt(0, -0.12, 100);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.MANUAL, stick.manual());
		s.tilt(0, -0.12, 1000);
		stick.tick(s.ms);
		assertEquals("held as long as the tilt is", ControllerStick.Manual.MANUAL, stick.manual());
		s.rest(ControllerStick.STOP_MS + 10);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
		assertNull("a manual is never a flick", gesture.poll());
		assertNull("and gives no near-miss hint", gesture.pollNearMiss());
	}

	@Test
	public void aSmallTiltDownHeldIsANoseManual()
	{
		StickSim s = sim().tilt(0, 0.12, 300);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.NOSE, stick.manual());
		assertFalse("the slow pull never showed a crouch", !stick.hidesWindUp() && gesture.isCrouching());
		assertNull(gesture.poll());
	}

	@Test
	public void theSlowDriftBeforeAManualDoesNotCrouch()
	{
		StickSim s = sim().tilt(0, 0.12, 200);
		stick.tick(s.ms);
		assertTrue("the recogniser is wound up...", gesture.isCrouching());
		assertTrue("...but a slow stroke hides it until it is fast", stick.hidesWindUp());
	}

	@Test
	public void aFastPullShowsTheWindUp()
	{
		StickSim s = sim().tilt(0, FULL, 60);
		stick.tick(s.ms);
		assertTrue(gesture.isCrouching());
		assertFalse(stick.hidesWindUp());
	}

	@Test
	public void aFlickFollowedByASlowDriftIsNeverAManual()
	{
		StickSim s = sim().tilt(0, FULL, 80).tilt(0, -FULL, 80).tilt(0, -0.12, 500);
		stick.tick(s.ms);
		assertEquals(Direction.UP, gesture.poll().direction);
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
	}

	@Test
	public void aSidewaysDriftIsNoManual()
	{
		StickSim s = sim().tilt(0.12, 0, 500);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
	}

	@Test
	public void aTinyDriftIsNoManual()
	{
		StickSim s = sim().tilt(0, -0.01, 400);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
	}

	@Test
	public void aFlickOutOfAManualEndsItAndPops()
	{
		StickSim s = sim().tilt(0, -0.12, 400);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.MANUAL, stick.manual());
		s.tilt(0, FULL, 80).tilt(0, -FULL, 80);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
		assertEquals(Direction.UP, gesture.poll().direction);
	}

	@Test
	public void movingTheStickWithAGrabHeldIsNeverAFlick()
	{
		StickSim s = grabbing().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		stick.tick(s.ms);
		assertNull(gesture.poll());
		assertFalse(stick.isActive());
	}

	@Test
	public void aGrabPressedMidStrokeCancelsTheStrokeAndItsTailIsNoFlick()
	{
		StickSim s = sim().tilt(0, FULL, 80);
		stick.block(s.ms);
		assertFalse(gesture.isStrokeActive());
		// the grab key is let go while the stick is still moving: the rest of that motion is not a flick
		StickSim tail = new StickSim(stick::move, 400, 396, s.ms);
		tail.tilt(0, -FULL, 80).rest(100);
		stick.tick(tail.ms);
		assertNull(gesture.poll());
		// once the stick has been still, flicks work again
		tail.tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		stick.tick(tail.ms);
		assertEquals(Direction.UP, gesture.poll().direction);
	}

	@Test
	public void flicksCanBeTurnedOffLeavingManuals()
	{
		stick.setFlicks(false);
		StickSim s = sim().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		stick.tick(s.ms);
		assertNull(gesture.poll());
		s.tilt(0, -0.12, 300);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.MANUAL, stick.manual());
	}

	@Test
	public void resetForgetsTheStrokeWithoutTouchingTheRecogniser()
	{
		StickSim s = sim().tilt(0, -0.12, 300);
		stick.tick(s.ms);
		assertEquals(ControllerStick.Manual.MANUAL, stick.manual());
		stick.reset();
		assertEquals(ControllerStick.Manual.NONE, stick.manual());
		assertFalse(stick.isActive());
		// a right-drag that starts now is the recogniser's alone: the stick never ends it
		gesture.begin(400, 300, s.ms);
		stick.tick(s.ms + 500);
		assertTrue(gesture.isStrokeActive());
	}

	@Test
	public void flickSensitivityIsTheRecognisersOwn()
	{
		// a short flick: 12 px pull, 24 px snap (too small at 100%, enough at 150%)
		StickSim s = sim().tilt(0, FULL, 10).tilt(0, -FULL, 20).rest(100);
		stick.tick(s.ms);
		assertNull(gesture.poll());
		gesture.setSensitivity(150);
		StickSim t = new StickSim(stick::move, 400, 300, s.ms + 200).tilt(0, FULL, 15).tilt(0, -FULL, 25).rest(100);
		stick.tick(t.ms);
		assertEquals(Direction.UP, gesture.poll().direction);
	}
}
