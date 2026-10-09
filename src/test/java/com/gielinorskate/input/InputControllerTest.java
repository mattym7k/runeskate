package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Test;

public class InputControllerTest
{
	private static final Component SOURCE = new Canvas();

	private static KeyEvent key(int code, boolean pressed)
	{
		return new KeyEvent(SOURCE, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
			System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
	}

	private static MouseEvent rightMouse(int id, int x, int y, long when)
	{
		return new MouseEvent(SOURCE, id, when, InputEvent.BUTTON3_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON3);
	}

	@Test
	public void disabledControllerDoesNotConsumeOrRegisterPush()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		KeyEvent press = key(KeyEvent.VK_W, true);

		controller.keyPressed(press);

		assertFalse(press.isConsumed());

		SkateInput in = new SkateInput();
		controller.drainInto(in);
		assertFalse(in.pushPressed);
	}

	@Test
	public void enabledWPressIsConsumedAndSetsPushPressedOnce()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		controller.setEnabled(true);
		KeyEvent press = key(KeyEvent.VK_W, true);

		controller.keyPressed(press);

		assertTrue(press.isConsumed());

		SkateInput first = new SkateInput();
		controller.drainInto(first);
		assertTrue(first.pushPressed);

		SkateInput second = new SkateInput();
		controller.drainInto(second);
		assertFalse(second.pushPressed);
	}

	@Test
	public void heldHTogglesTheControlsCardOnceDespiteKeyRepeat()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		controller.setEnabled(true);
		controller.keyPressed(key(KeyEvent.VK_H, true));
		assertTrue(controller.consumeControlsToggle());
		// the OS repeats KEY_PRESSED while H is held
		controller.keyPressed(key(KeyEvent.VK_H, true));
		controller.keyPressed(key(KeyEvent.VK_H, true));
		assertFalse(controller.consumeControlsToggle());
		controller.keyReleased(key(KeyEvent.VK_H, false));
		controller.keyPressed(key(KeyEvent.VK_H, true));
		assertTrue(controller.consumeControlsToggle());
	}

	@Test
	public void holdingWSetsPushHeldUntilRelease()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		controller.setEnabled(true);
		controller.keyPressed(key(KeyEvent.VK_W, true));

		SkateInput first = new SkateInput();
		controller.drainInto(first);
		assertTrue(first.pushHeld);

		SkateInput second = new SkateInput();
		controller.drainInto(second);
		assertTrue(second.pushHeld);

		controller.keyReleased(key(KeyEvent.VK_W, false));
		SkateInput third = new SkateInput();
		controller.drainInto(third);
		assertFalse(third.pushHeld);
	}

	@Test
	public void arrowKeyAliasesSteerLeftAndRight()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		controller.setEnabled(true);

		controller.keyPressed(key(KeyEvent.VK_LEFT, true));
		SkateInput left = new SkateInput();
		controller.drainInto(left);
		assertEquals(-1f, left.steer, 0f);

		controller.keyReleased(key(KeyEvent.VK_LEFT, false));
		controller.keyPressed(key(KeyEvent.VK_RIGHT, true));
		SkateInput right = new SkateInput();
		controller.drainInto(right);
		assertEquals(1f, right.steer, 0f);
	}

	@Test
	public void focusLostClearsSteer()
	{
		InputController controller = new InputController(System::currentTimeMillis);
		controller.setEnabled(true);
		controller.keyPressed(key(KeyEvent.VK_A, true));

		controller.focusLost();

		SkateInput in = new SkateInput();
		controller.drainInto(in);
		assertEquals(0f, in.steer, 0f);
	}

	@Test
	public void clickingTheGameWhileSkatingReclaimsKeyboardFocus()
	{
		final boolean[] focusRequested = {false};
		Canvas canvas = new Canvas()
		{
			@Override
			public boolean requestFocusInWindow()
			{
				focusRequested[0] = true;
				return true;
			}
		};
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_PRESSED,
			0L, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, 10, 10, 1, false, java.awt.event.MouseEvent.BUTTON1);
		c.mousePressed(press);
		assertTrue(focusRequested[0]);
		assertTrue(press.isConsumed());
	}

	private static java.awt.event.MouseWheelEvent wheel(int notches)
	{
		return new java.awt.event.MouseWheelEvent(SOURCE, java.awt.event.MouseEvent.MOUSE_WHEEL, 0L, 0, 10, 10, 0,
			false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches);
	}

	@Test
	public void wheelIsLeftAloneWhenNotSkating()
	{
		InputController c = new InputController(System::currentTimeMillis);
		java.awt.event.MouseWheelEvent e = wheel(2);
		c.mouseWheelMoved(e);
		assertFalse(e.isConsumed());
		assertEquals(0, c.drainZoomNotches());
	}

	@Test
	public void wheelNotchesAccumulateWhileSkating()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		java.awt.event.MouseWheelEvent e = wheel(2);
		c.mouseWheelMoved(e);
		c.mouseWheelMoved(wheel(-1));
		assertTrue(e.isConsumed());
		assertEquals(1, c.drainZoomNotches());
		assertEquals(0, c.drainZoomNotches());
	}

	@Test
	public void rightMouseFlickUpQueuesGesture()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);

		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50)); // down 30px -> wound up
		SkateInput crouching = new SkateInput();
		c.drainInto(crouching);
		assertTrue(crouching.crouch);
		assertFalse("mouse wind-up is not the S key", crouching.leanBack);

		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120)); // 35px up -> flick (OLLIE)

		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(1, in.gestures.size());
		assertEquals(Gesture.Direction.UP, in.gestures.get(0).direction);

		SkateInput next = new SkateInput();
		c.drainInto(next);
		assertTrue(next.gestures.isEmpty());
	}

	@Test
	public void slowRightMouseDragQueuesNoGesture()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);

		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 0, 0, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 0, 25, 10)); // wound up
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 40, 25, 500)); // far, but slow

		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.gestures.isEmpty());
	}

	@Test
	public void spaceIsConsumedAndHeldAsTheManualKey()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		KeyEvent press = key(KeyEvent.VK_SPACE, true);
		c.keyPressed(press);
		assertTrue(press.isConsumed());
		SkateInput held = new SkateInput();
		c.drainInto(held);
		assertTrue(held.manualHeld);

		// W during the manual is a nose manual (physics reads manualHeld + pushHeld)
		c.keyPressed(key(KeyEvent.VK_W, true));
		SkateInput nose = new SkateInput();
		c.drainInto(nose);
		assertTrue(nose.manualHeld);
		assertTrue(nose.pushHeld);

		KeyEvent release = key(KeyEvent.VK_SPACE, false);
		c.keyReleased(release);
		assertTrue(release.isConsumed());
		SkateInput released = new SkateInput();
		c.drainInto(released);
		assertFalse(released.manualHeld);
	}

	@Test
	public void holdingAStillMouseWindUpNeverStartsAManual()
	{
		long[] now = {0};
		InputController c = new InputController(() -> now[0]);
		c.setEnabled(true);
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 0, 0, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 0, 20, 10)); // wound up down
		now[0] = 2000;
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertFalse(in.manualHeld);
		assertTrue(in.crouch);
	}

	@Test
	public void nollieWindUpChargesWithoutCrouching()
	{
		InputController c = new InputController(() -> 60L);
		c.setEnabled(true);
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 170, 50)); // pushed up 30 px: nollie wind-up
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.charge);
		assertFalse(in.crouch);
	}

	@Test
	public void releasingTheLeftButtonWhileRightIsHeldKeepsTheGesture()
	{
		InputController c = new InputController(() -> 60L);
		c.setEnabled(true);
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50)); // wound up
		// left button released while right is still down: the button-3 down mask is still set
		c.mouseReleased(new MouseEvent(SOURCE, MouseEvent.MOUSE_RELEASED, 55, InputEvent.BUTTON3_DOWN_MASK,
			50, 230, 1, false, MouseEvent.BUTTON1));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue("still wound up", in.crouch);
	}

	@Test
	public void disabledControllerIgnoresMouseFlicks()
	{
		InputController c = new InputController(System::currentTimeMillis);
		MouseEvent press = rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0);

		c.mousePressed(press);
		assertFalse(press.isConsumed());
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120));

		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.gestures.isEmpty());
	}

	@Test
	public void qAndEHoldGrabLeftAndGrabRight()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);

		c.keyPressed(key(KeyEvent.VK_Q, true));
		SkateInput left = new SkateInput();
		c.drainInto(left);
		assertTrue(left.grabLeft);
		assertFalse(left.grabRight);

		c.keyReleased(key(KeyEvent.VK_Q, false));
		c.keyPressed(key(KeyEvent.VK_E, true));
		SkateInput right = new SkateInput();
		c.drainInto(right);
		assertFalse(right.grabLeft);
		assertTrue(right.grabRight);

		c.keyReleased(key(KeyEvent.VK_E, false));
		SkateInput none = new SkateInput();
		c.drainInto(none);
		assertFalse(none.grabLeft);
		assertFalse(none.grabRight);
	}

	private static MouseEvent move(int x, int y)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false, MouseEvent.NOBUTTON);
	}

	private static SkateInput drain(InputController c)
	{
		SkateInput in = new SkateInput();
		c.drainInto(in);
		return in;
	}

	@Test
	public void movingTheMouseWithAGrabKeyHeldAimsTheGrab()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mouseMoved(move(100, 100));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		assertEquals(null, drain(c).grabAim);
		c.mouseMoved(move(100, 90));
		assertEquals(null, drain(c).grabAim); // not far enough yet
		c.mouseMoved(move(100, 70));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.NOSE, drain(c).grabAim);
		// the first aim sticks for this press
		c.mouseMoved(move(100, 140));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.NOSE, drain(c).grabAim);
		c.keyReleased(key(KeyEvent.VK_Q, false));
		assertEquals(null, drain(c).grabAim);
		// a new press measures from where the mouse is now
		c.keyPressed(key(KeyEvent.VK_E, true));
		assertEquals(null, drain(c).grabAim);
		c.mouseMoved(move(130, 140));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.TOE, drain(c).grabAim);
	}

	@Test
	public void mouseMovesWithoutAGrabKeyDoNotAim()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mouseMoved(move(100, 100));
		c.mouseMoved(move(100, 40));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		assertEquals(null, drain(c).grabAim);
	}

	@Test
	public void theFirstMoveAfterEnablingIsTheOriginNotAnAim()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_Q, true)); // the mouse position is not known yet
		c.mouseMoved(move(400, 300));
		assertEquals(null, drain(c).grabAim);
		c.mouseMoved(move(370, 300));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.HEEL, drain(c).grabAim);
	}

	@Test
	public void aRightDragWithAGrabHeldIsStillAFlickAndNotAnAim()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mouseMoved(move(50, 200));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120));
		SkateInput in = drain(c);
		assertEquals(null, in.grabAim);
		assertEquals(1, in.gestures.size());
	}

	@Test
	public void aFlickDragMadeWithAGrabHeldDoesNotCountTowardsItsAim()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mouseMoved(move(50, 200));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		// an ollie: pull down 60, snap up 140, release, then a 1 px nudge
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 260, 50));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 120, 120));
		c.mouseReleased(rightMouse(MouseEvent.MOUSE_RELEASED, 50, 120, 130));
		c.mouseMoved(move(51, 120));
		assertEquals(null, drain(c).grabAim);
		// a button-free move from where the flick ended still aims
		c.mouseMoved(move(51, 90));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.NOSE, drain(c).grabAim);
	}

	@Test
	public void aGrabPressedMidFlickMeasuresItsAimFromTheRelease()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mouseMoved(move(50, 200));
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 260, 50));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 120, 120));
		c.mouseReleased(rightMouse(MouseEvent.MOUSE_RELEASED, 50, 120, 130));
		c.mouseMoved(move(52, 121));
		assertEquals(null, drain(c).grabAim);
	}

	@Test
	public void aGrabIsAimedOnlyJustAfterItsKeyGoesDown()
	{
		long[] now = {1000L};
		InputController c = new InputController(() -> now[0]);
		c.setEnabled(true);
		c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, 900, 0, 100, 100, 0, false, MouseEvent.NOBUTTON));
		c.keyPressed(key(KeyEvent.VK_Q, true));
		// held on the ground, the mouse re-centred long after the press: no aim
		now[0] = 1000 + InputController.AIM_MS + 1;
		c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, now[0], 0, 100, 60, 0, false,
			MouseEvent.NOBUTTON));
		assertEquals(null, drain(c).grabAim);
		c.keyReleased(key(KeyEvent.VK_Q, false));
		// within the window it aims
		c.keyPressed(key(KeyEvent.VK_Q, true));
		c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, now[0] + InputController.AIM_MS, 0, 100, 30, 0,
			false, MouseEvent.NOBUTTON));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.NOSE, drain(c).grabAim);
	}

	@Test
	public void keyRemappingsWAndSStillPushAndCrouchButNeverLean()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		// Key Remapping rewrites W / S to the arrow key codes and keeps the letter as the key char
		KeyEvent w = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_UP, 'w');
		c.keyPressed(w);
		assertTrue(w.isConsumed());
		SkateInput in = drain(c);
		assertTrue(in.pushHeld);
		assertFalse("a grab-flip needs the real arrow key", in.leanForwardKey);
		c.keyReleased(new KeyEvent(SOURCE, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_UP, 'w'));
		c.keyPressed(new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, 's'));
		in = drain(c);
		assertTrue(in.leanBack);
		assertFalse(in.leanBackKey);
	}

	@Test
	public void mirroredFlicksMirrorTheGrabAim()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.configureTricks(com.gielinorskate.GielinorSkateConfig.TrickControls.MOUSE,
			com.gielinorskate.GielinorSkateConfig.FlickButton.RIGHT, true);
		c.setEnabled(true);
		c.mouseMoved(move(100, 100));
		c.keyPressed(key(KeyEvent.VK_E, true));
		c.mouseMoved(move(130, 100));
		assertEquals(com.gielinorskate.tricks.Grabs.Aim.HEEL, drain(c).grabAim);
	}

	@Test
	public void arrowKeysAreTheLeanKeysAndStillPushAndCrouch()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		KeyEvent up = key(KeyEvent.VK_UP, true);
		c.keyPressed(up);
		assertTrue(up.isConsumed());
		SkateInput in = drain(c);
		assertTrue(in.leanForwardKey);
		assertTrue(in.pushHeld);
		assertFalse(in.leanBackKey);
		c.keyReleased(key(KeyEvent.VK_UP, false));
		c.keyPressed(key(KeyEvent.VK_DOWN, true));
		in = drain(c);
		assertFalse(in.leanForwardKey);
		assertTrue(in.leanBackKey);
		assertTrue(in.leanBack);
		c.keyReleased(key(KeyEvent.VK_DOWN, false));
		in = drain(c);
		assertFalse(in.leanBackKey);
		// W and S are not lean keys
		c.keyPressed(key(KeyEvent.VK_W, true));
		c.keyPressed(key(KeyEvent.VK_S, true));
		in = drain(c);
		assertFalse(in.leanForwardKey);
		assertFalse(in.leanBackKey);
	}

	@Test
	public void leanKeysCanBeRebound()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.configureLeanKeys(KeyEvent.VK_I, KeyEvent.VK_K);
		c.setEnabled(true);
		KeyEvent i = key(KeyEvent.VK_I, true);
		c.keyPressed(i);
		assertTrue(i.isConsumed());
		c.keyPressed(key(KeyEvent.VK_K, true));
		SkateInput in = drain(c);
		assertTrue(in.leanForwardKey);
		assertTrue(in.leanBackKey);
		assertFalse(in.pushHeld);
		c.keyPressed(key(KeyEvent.VK_UP, true));
		c.keyReleased(key(KeyEvent.VK_I, false));
		in = drain(c);
		assertFalse(in.leanForwardKey);
		assertTrue(in.pushHeld);
	}

	@Test
	public void setEnabledFalseDiscardsAnyPendingGesture()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120)); // queues an UP gesture

		c.setEnabled(false);
		c.setEnabled(true);

		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.gestures.isEmpty());
	}

	@Test
	public void sKeySetsLeanBackAndCrouchUntilRelease()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_S, true));
		SkateInput held = new SkateInput();
		c.drainInto(held);
		assertTrue(held.leanBack);
		assertTrue(held.crouch);

		c.keyReleased(key(KeyEvent.VK_S, false));
		SkateInput released = new SkateInput();
		c.drainInto(released);
		assertFalse(released.leanBack);
	}

	@Test
	public void manualKeyCanBeRebound()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.configure(KeyEvent.VK_M, 100);
		c.setEnabled(true);
		KeyEvent m = key(KeyEvent.VK_M, true);
		c.keyPressed(m);
		assertTrue(m.isConsumed());
		KeyEvent space = key(KeyEvent.VK_SPACE, true);
		c.keyPressed(space);
		assertFalse("Space is free again", space.isConsumed());
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.manualHeld);
	}

	@Test
	public void manualKeyOnASkateKeyFallsBackToSpace()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.configure(KeyEvent.VK_W, 100);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_W, true));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue("W still pushes", in.pushPressed);
		assertFalse("W is not the manual key", in.manualHeld);
		c.keyPressed(key(KeyEvent.VK_SPACE, true));
		SkateInput in2 = new SkateInput();
		c.drainInto(in2);
		assertTrue("Space is the manual key instead", in2.manualHeld);
	}

	@Test
	public void manualKeyValidity()
	{
		int[] bad = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_H, KeyEvent.VK_R,
			KeyEvent.VK_Q, KeyEvent.VK_E, KeyEvent.VK_ESCAPE, KeyEvent.VK_SHIFT, KeyEvent.VK_UNDEFINED,
			KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_UP, KeyEvent.VK_LEFT};
		for (int code : bad)
		{
			assertFalse(KeyEvent.getKeyText(code), InputController.isValidManualKey(code));
			assertEquals(KeyEvent.VK_SPACE, InputController.effectiveManualKey(code, false));
		}
		assertTrue(InputController.isValidManualKey(KeyEvent.VK_SPACE));
		assertTrue(InputController.isValidManualKey(KeyEvent.VK_M));
		assertEquals(KeyEvent.VK_M, InputController.effectiveManualKey(KeyEvent.VK_M, false));
	}

	/** Winds up and flicks an ollie with the right mouse button; `shiftAtFlick` decides Shift as the flick fires. */
	private static Gesture flickWithShift(InputController c, boolean shiftDuringWindUp, boolean shiftAtFlick)
	{
		if (shiftDuringWindUp)
		{
			c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		}
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50)); // wound up
		if (shiftAtFlick)
		{
			c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		}
		else
		{
			c.keyReleased(key(KeyEvent.VK_SHIFT, false));
		}
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120)); // flick
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(1, in.gestures.size());
		return in.gestures.get(0);
	}

	@Test
	public void shiftHeldAsTheFlickFiresMarksTheGestureModified()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		assertTrue(flickWithShift(c, false, true).modified);
	}

	@Test
	public void onlyShiftAtTheMomentOfTheFlickCounts()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		// held through the wind-up but released before the flick: a plain trick
		assertFalse(flickWithShift(c, true, false).modified);
	}

	@Test
	public void shiftIsForgottenWhenFocusIsLost()
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		c.focusLost();
		c.mousePressed(rightMouse(MouseEvent.MOUSE_PRESSED, 50, 200, 0));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 230, 50));
		c.mouseDragged(rightMouse(MouseEvent.MOUSE_DRAGGED, 50, 195, 120));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertFalse(in.gestures.get(0).modified);
	}
}
