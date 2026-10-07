package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture.Direction;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Test;

/** Controller mode: the right stick as button-free mouse motion, lean-only arrows and the brake key. */
public class InputControllerControllerModeTest
{
	private static final Component SOURCE = new Canvas();
	private static final double FULL = 1.2;

	private long now = 1000;
	private final InputController c = new InputController(() -> now);

	private static KeyEvent key(int code, boolean pressed)
	{
		return key(code, KeyEvent.CHAR_UNDEFINED, pressed);
	}

	private static KeyEvent key(int code, char ch, boolean pressed)
	{
		return new KeyEvent(SOURCE, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, 0, 0, code, ch);
	}

	private void moved(int x, int y, long ms)
	{
		c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, ms, 0, x, y, 0, false, MouseEvent.NOBUTTON));
	}

	private StickSim stick()
	{
		return new StickSim(this::moved, 400, 300, now);
	}

	private SkateInput drain(long at)
	{
		now = at;
		SkateInput in = new SkateInput();
		c.drainInto(in);
		return in;
	}

	private void controllerMode()
	{
		c.configureController(true, KeyEvent.VK_B);
		c.setEnabled(true);
	}

	@Test
	public void aStickFlickWithNoButtonIsATrickInControllerMode()
	{
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		SkateInput in = drain(s.ms);
		assertEquals(1, in.gestures.size());
		assertEquals(Direction.UP, in.gestures.get(0).direction);
	}

	@Test
	public void aStickFlickWithShiftHeldIsTheHardTrickJustLikeAMouseFlick()
	{
		// LB / RB send Shift: the stick path shares the same GestureRecognizer (and its modifier) as mouse flicks,
		// so a held Shift makes a stick flick the hard version exactly as it would a right-drag flick.
		controllerMode();
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		StickSim s = stick().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		SkateInput in = drain(s.ms);
		assertEquals(1, in.gestures.size());
		assertEquals(Direction.UP, in.gestures.get(0).direction);
		assertTrue("Shift held makes it the hard trick", in.gestures.get(0).modified);
	}

	@Test
	public void theSameMotionOutsideControllerModeIsNoTrick()
	{
		c.setEnabled(true);
		StickSim s = stick().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		assertTrue(drain(s.ms).gestures.isEmpty());
	}

	@Test
	public void mirroredFlicksAreMirroredFromTheStickToo()
	{
		c.configureTricks(GielinorSkateConfig.TrickControls.MOUSE, GielinorSkateConfig.FlickButton.RIGHT, true);
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 80).tilt(-0.85, -0.85, 80).rest(100);
		assertEquals(Direction.UP_RIGHT, drain(s.ms).gestures.get(0).direction);
	}

	@Test
	public void keyboardOnlyTrickControlsMakeNoStickFlicks()
	{
		c.configureTricks(GielinorSkateConfig.TrickControls.KEYBOARD, GielinorSkateConfig.FlickButton.RIGHT, false);
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		assertTrue(drain(s.ms).gestures.isEmpty());
	}

	@Test
	public void aSmallHeldTiltUpIsAManualAndDownANoseManual()
	{
		controllerMode();
		StickSim s = stick().tilt(0, -0.12, 300);
		SkateInput in = drain(s.ms);
		assertTrue(in.manualHeld);
		assertFalse(in.noseManualHeld);
		s.rest(ControllerStick.STOP_MS + 10);
		in = drain(s.ms);
		assertFalse("releasing the stick ends it", in.manualHeld);

		s.tilt(0, 0.12, 300);
		in = drain(s.ms);
		assertTrue(in.manualHeld);
		assertTrue(in.noseManualHeld);
		assertFalse("the nose manual never pushes", in.pushHeld);
		assertFalse("nor crouches", in.crouch);
	}

	@Test
	public void spaceStillDoesAManualInControllerMode()
	{
		controllerMode();
		c.keyPressed(key(KeyEvent.VK_SPACE, true));
		assertTrue(drain(now).manualHeld);
	}

	@Test
	public void theSlowStartOfAStickManualDoesNotCrouch()
	{
		controllerMode();
		StickSim s = stick().tilt(0, 0.12, 200);
		SkateInput in = drain(s.ms);
		assertFalse(in.crouch);
		assertFalse(in.charge);
	}

	@Test
	public void aFastStickPullCrouchesLikeAMouseWindUp()
	{
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 60);
		SkateInput in = drain(s.ms);
		assertTrue(in.crouch);
		assertTrue(in.charge);
	}

	@Test
	public void stickMotionWithAGrabHeldAimsTheGrabAndIsNeverAFlick()
	{
		controllerMode();
		moved(400, 300, now);
		c.keyPressed(key(KeyEvent.VK_Q, true));
		StickSim s = stick().tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		SkateInput in = drain(s.ms);
		assertTrue(in.gestures.isEmpty());
		assertNotNull("it aimed the grab", in.grabAim);
		c.keyReleased(key(KeyEvent.VK_Q, false));
		assertTrue(drain(s.ms + 200).gestures.isEmpty());
	}

	@Test
	public void rollingWithAGrabHeldTheStickFlicksAgainAfterTheAimWindow()
	{
		controllerMode();
		c.setRolling(true);
		moved(400, 300, now);
		c.keyPressed(key(KeyEvent.VK_Q, true));
		// within the aim window the stick aims the rolling grab, as in the air
		StickSim s = stick().tilt(FULL, 0, 60).rest(200);
		SkateInput in = drain(s.ms);
		assertTrue(in.gestures.isEmpty());
		assertNotNull(in.grabAim);
		// past it (the key auto-repeating) a flick pops out of the rolling grab
		now = s.ms;
		c.keyPressed(key(KeyEvent.VK_Q, true));
		s.tilt(0, FULL, 80);
		c.keyPressed(key(KeyEvent.VK_Q, true));
		s.tilt(0, -FULL, 80).rest(100);
		in = drain(s.ms);
		assertEquals(1, in.gestures.size());
		assertEquals(Direction.UP, in.gestures.get(0).direction);
		assertTrue("still grabbing", in.grabLeft);
	}

	@Test
	public void inTheAirTheStickWithAGrabHeldNeverFlicks()
	{
		controllerMode();
		c.setRolling(false);
		moved(400, 300, now);
		c.keyPressed(key(KeyEvent.VK_Q, true));
		StickSim s = stick().rest(300).tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		assertTrue(drain(s.ms).gestures.isEmpty());
	}

	@Test
	public void pressingAGrabMidFlickDropsTheFlick()
	{
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 80);
		c.keyPressed(key(KeyEvent.VK_E, true));
		s.tilt(0, -FULL, 80).rest(100);
		assertTrue(drain(s.ms).gestures.isEmpty());
	}

	@Test
	public void aRightDragStillFlicksInControllerModeAndTheStickNeverEndsIt()
	{
		controllerMode();
		StickSim s = stick().tilt(0, 0.12, 100);
		c.mousePressed(new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, s.ms, InputEvent.BUTTON3_DOWN_MASK, 400, 312,
			1, false, MouseEvent.BUTTON3));
		SkateInput in = drain(s.ms + 500);
		assertTrue(drain(s.ms + 500).gestures.isEmpty());
		c.mouseDragged(new MouseEvent(SOURCE, MouseEvent.MOUSE_DRAGGED, s.ms + 550, InputEvent.BUTTON3_DOWN_MASK, 400,
			342, 1, false, MouseEvent.BUTTON3));
		c.mouseDragged(new MouseEvent(SOURCE, MouseEvent.MOUSE_DRAGGED, s.ms + 620, InputEvent.BUTTON3_DOWN_MASK, 400,
			307, 1, false, MouseEvent.BUTTON3));
		in = drain(s.ms + 630);
		assertEquals(1, in.gestures.size());
		assertEquals(Direction.UP, in.gestures.get(0).direction);
	}

	@Test
	public void arrowsAreLeanOnlyInControllerMode()
	{
		controllerMode();
		KeyEvent up = key(KeyEvent.VK_UP, true);
		c.keyPressed(up);
		assertTrue(up.isConsumed());
		SkateInput in = drain(now);
		assertTrue(in.leanForwardKey);
		assertFalse("the left stick up never pushes", in.pushHeld);
		assertFalse(in.pushPressed);
		c.keyReleased(key(KeyEvent.VK_UP, false));
		c.keyPressed(key(KeyEvent.VK_DOWN, true));
		in = drain(now);
		assertTrue(in.leanBackKey);
		assertFalse("the left stick down never crouches", in.crouch);
		assertFalse(in.leanBack);
		c.keyReleased(key(KeyEvent.VK_DOWN, false));
		in = drain(now);
		assertFalse(in.leanBackKey);
		assertFalse(in.leanForwardKey);
	}

	@Test
	public void wRemappedToTheUpArrowByKeyRemappingStillPushesInControllerMode()
	{
		// Key Remapping rewrites W's key code to VK_UP but leaves its key char: that is the A button, not the stick
		controllerMode();
		c.keyPressed(key(KeyEvent.VK_UP, 'w', true));
		SkateInput in = drain(now);
		assertTrue(in.pushHeld);
		assertTrue(in.pushPressed);
		c.keyReleased(key(KeyEvent.VK_UP, 'w', false));
		assertFalse(drain(now).pushHeld);
	}

	@Test
	public void theBrakeKeyAloneBrakesStraightAway()
	{
		controllerMode();
		KeyEvent b = key(KeyEvent.VK_B, true);
		c.keyPressed(b);
		assertTrue(b.isConsumed());
		SkateInput in = drain(now);
		assertTrue(in.powerslide);
		assertFalse(in.brakeBlocked);
		c.keyReleased(key(KeyEvent.VK_B, false));
		in = drain(now);
		assertFalse(in.powerslide);
	}

	@Test
	public void theBrakeKeyWithSteerCarvesTightAndWithPushNeverFlips()
	{
		controllerMode();
		c.keyPressed(key(KeyEvent.VK_B, true));
		c.keyPressed(key(KeyEvent.VK_A, true));
		SkateInput in = drain(now);
		assertTrue(in.powerslide);
		assertTrue("steering: not a brake", in.brakeBlocked);
		c.keyReleased(key(KeyEvent.VK_A, false));
		c.keyPressed(key(KeyEvent.VK_W, true));
		in = drain(now);
		// Shift+W is a front flip in the air; the brake key is only ever a brake
		assertFalse(in.powerslide);
	}

	@Test
	public void theBrakeKeyBrakesWhileTheRightStickMoves()
	{
		controllerMode();
		StickSim s = stick().tilt(0, FULL, 80);
		c.keyPressed(key(KeyEvent.VK_B, true));
		SkateInput in = drain(s.ms);
		assertTrue(in.powerslide);
		assertFalse("a stick stroke is no steer: B still brakes", in.brakeBlocked);
	}

	@Test
	public void theManualKeyWinsOverABrakeKeyOnTheSameKey()
	{
		c.configureController(false, KeyEvent.VK_C);
		c.configure(KeyEvent.VK_C, 100);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_C, true));
		SkateInput in = drain(now);
		assertTrue(in.manualHeld);
		assertFalse(in.powerslide);
	}

	@Test
	public void aTrickKeyWinsOverABrakeKeyOnTheSameKey()
	{
		c.configureController(false, KeyEvent.VK_1);
		c.configureTricks(GielinorSkateConfig.TrickControls.KEYBOARD, GielinorSkateConfig.FlickButton.RIGHT, false);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_1, true));
		assertFalse(drain(now).powerslide);
	}

	@Test
	public void aBrakeKeyOnALeanKeyLeansAndBrakes()
	{
		c.configureController(false, KeyEvent.VK_K);
		c.configureLeanKeys(KeyEvent.VK_K, KeyEvent.VK_DOWN);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_K, true));
		SkateInput in = drain(now);
		assertTrue(in.leanForwardKey);
		assertTrue(in.powerslide);
	}

	@Test
	public void theBrakeKeyWorksOnTheKeyboardOutsideControllerModeToo()
	{
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_B, true));
		SkateInput in = drain(now);
		assertTrue(in.powerslide);
		assertFalse(in.brakeBlocked);
	}

	@Test
	public void shiftKeepsItsBrakeDelayAndModifier()
	{
		controllerMode();
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		SkateInput in = drain(now);
		assertTrue(in.powerslide);
		assertTrue("Shift alone still waits before braking", in.brakeBlocked);
		in = drain(now + InputController.BRAKE_DELAY_MS);
		assertFalse(in.brakeBlocked);
	}

	@Test
	public void anUnsetBrakeKeyDoesNothing()
	{
		c.configureController(false, KeyEvent.VK_UNDEFINED);
		c.setEnabled(true);
		KeyEvent b = key(KeyEvent.VK_B, true);
		c.keyPressed(b);
		assertFalse(b.isConsumed());
		assertFalse(drain(now).powerslide);
	}

	@Test
	public void aBrakeKeyOnASkateKeyIsIgnored()
	{
		c.configureController(true, KeyEvent.VK_W);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_W, true));
		SkateInput in = drain(now);
		assertTrue(in.pushHeld);
		assertFalse(in.powerslide);
	}
}
