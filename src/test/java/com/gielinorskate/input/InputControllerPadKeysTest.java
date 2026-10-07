package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * The pad keys (one per controller button, see {@link PadButton}) turned into actions by the preset: only in
 * controller mode, and never on a key another key setting uses.
 */
public class InputControllerPadKeysTest
{
	private static final Component SOURCE = new Canvas();
	private static final int PAD_A = PadButton.A.keyCode;
	private static final int PAD_X = PadButton.X.keyCode;

	private long now = 1000;
	private final InputController c = new InputController(() -> now);

	@Before
	public void controllerMode()
	{
		c.configureController(true, KeyEvent.VK_B);
	}

	private KeyEvent press(int code)
	{
		KeyEvent e = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
		c.keyPressed(e);
		return e;
	}

	private KeyEvent release(int code)
	{
		KeyEvent e = new KeyEvent(SOURCE, KeyEvent.KEY_RELEASED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
		c.keyReleased(e);
		return e;
	}

	private SkateInput skate()
	{
		SkateInput in = new SkateInput();
		c.drainInto(in);
		return in;
	}

	private FootControls foot()
	{
		FootControls f = new FootControls();
		c.drainFoot(f);
		return f;
	}

	@Test
	public void everyPadKeyIsReadAndNeverTypedInControllerMode()
	{
		c.setEnabled(true);
		for (PadButton b : PadButton.values())
		{
			assertTrue(b.label, press(b.keyCode).isConsumed());
			assertTrue(b.label, release(b.keyCode).isConsumed());
		}
	}

	@Test
	public void padAPushesOnTheBoardTapOrHold()
	{
		c.setEnabled(true);
		assertTrue(press(PAD_A).isConsumed());
		SkateInput in = skate();
		assertTrue(in.pushPressed);
		assertTrue(in.pushHeld);
		// a key repeat while held is no new push
		press(PAD_A);
		in = skate();
		assertFalse(in.pushPressed);
		assertTrue(in.pushHeld);
		assertTrue(release(PAD_A).isConsumed());
		assertFalse(skate().pushHeld);
	}

	@Test
	public void padXPushesOnTheBoardTooAndIsNoManual()
	{
		c.setEnabled(true);
		press(PAD_X);
		SkateInput in = skate();
		assertTrue(in.pushPressed);
		assertTrue(in.pushHeld);
		assertFalse(in.manualHeld);
		assertFalse(in.powerslide);
		release(PAD_X);
		assertFalse(skate().pushHeld);
	}

	@Test
	public void releasingOnePushButtonKeepsTheOtherHeld()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_W);
		press(PAD_A);
		release(PAD_A);
		assertTrue(skate().pushHeld);
		press(PAD_X);
		release(KeyEvent.VK_W);
		assertTrue(skate().pushHeld);
		release(PAD_X);
		assertFalse(skate().pushHeld);
	}

	@Test
	public void padBBrakesAtOnceAndPadPushBlocksIt()
	{
		c.setEnabled(true);
		press(PadButton.B.keyCode);
		SkateInput in = skate();
		assertTrue(in.powerslide);
		assertFalse(in.brakeBlocked);
		press(PAD_A);
		in = skate();
		assertFalse(in.powerslide);
		assertTrue(in.brakeBlocked);
	}

	@Test
	public void onFootPadASprintsWhileHeldAndDoesNotWalk()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(PAD_A);
		FootControls f = foot();
		assertTrue(f.sprint);
		assertFalse(f.forward);
		assertFalse(f.jumpPressed);
		release(PAD_A);
		assertFalse(foot().sprint);
	}

	@Test
	public void onFootPadXJumpsOncePerPressAndDoesNotWalk()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(PAD_X);
		FootControls f = foot();
		assertTrue(f.jumpPressed);
		assertFalse(f.forward);
		assertFalse(f.sprint);
		press(PAD_X);
		assertFalse(foot().jumpPressed);
		release(PAD_X);
		press(PAD_X);
		assertTrue(foot().jumpPressed);
	}

	@Test
	public void onFootThePushButtonsDoNotPush()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(PAD_A);
		press(PAD_X);
		SkateInput in = skate();
		assertFalse(in.pushPressed);
		assertFalse(in.pushHeld);
	}

	@Test
	public void aButtonHeldAcrossADismountDoesItsOnFootActionButIsNoNewPress()
	{
		c.setEnabled(true);
		press(PAD_X);
		press(PAD_A);
		c.setOnFoot(true);
		press(PAD_X);
		FootControls f = foot();
		assertFalse(f.jumpPressed);
		assertTrue(f.sprint);
	}

	@Test
	public void theKeyboardControlsAreUnchanged()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_SHIFT);
		press(KeyEvent.VK_SPACE);
		FootControls f = foot();
		assertTrue(f.sprint);
		assertTrue(f.jumpPressed);
		release(KeyEvent.VK_SHIFT);
		c.setOnFoot(false);
		release(KeyEvent.VK_SPACE);
		press(KeyEvent.VK_W);
		SkateInput in = skate();
		assertTrue(in.pushPressed);
		assertTrue(in.pushHeld);
	}

	@Test
	public void withControllerModeOffThePadKeysAreOrdinaryKeys()
	{
		c.configureController(false, KeyEvent.VK_B);
		c.setEnabled(true);
		for (PadButton b : PadButton.values())
		{
			assertFalse(b.label, press(b.keyCode).isConsumed());
		}
		assertFalse(skate().pushHeld);
		c.setOnFoot(true);
		press(PAD_X);
		assertFalse(foot().jumpPressed);
	}

	@Test
	public void withControllerModeOffAManualKeyOnAPadKeyIsStillTheManual()
	{
		c.configureController(false, KeyEvent.VK_B);
		c.configure(PAD_A, 100);
		c.setEnabled(true);
		assertTrue(press(PAD_A).isConsumed());
		SkateInput in = skate();
		assertTrue(in.manualHeld);
		assertFalse(in.pushHeld);
	}

	@Test
	public void aKeySettingOnAPadKeyWinsOverItsButton()
	{
		c.configure(PAD_A, 100);
		c.setEnabled(true);
		press(PAD_A);
		SkateInput in = skate();
		assertTrue(in.manualHeld);
		assertFalse(in.pushHeld);
		release(PAD_A);

		c.configure(KeyEvent.VK_SPACE, 100);
		c.configureController(true, PAD_X);
		press(PAD_X);
		in = skate();
		assertFalse(in.pushHeld);
		assertTrue(in.powerslide);
		release(PAD_X);

		c.configureController(true, KeyEvent.VK_B);
		c.configureLeanKeys(PAD_A, PAD_X);
		press(PAD_A);
		in = skate();
		assertTrue(in.leanForwardKey);
		assertFalse(in.pushHeld);
		release(PAD_A);

		c.configureLeanKeys(KeyEvent.VK_UP, KeyEvent.VK_DOWN);
		c.configureBoardKey(PAD_X);
		press(PAD_X);
		assertFalse(skate().pushHeld);
		BoardKey.Edges e = new BoardKey.Edges();
		c.pollBoardKey(e);
		assertTrue(e.pressed);
		// the other buttons are still read
		press(PadButton.B.keyCode);
		assertTrue(skate().powerslide);
	}

	@Test
	public void aPadKeyOnAPlainSkateModeKeyIsOffButNotOnOneWithModifiers()
	{
		c.configureToggleKey(PAD_A, 0);
		c.setEnabled(true);
		assertFalse(press(PAD_A).isConsumed());
		release(PAD_A);
		c.configureToggleKey(PAD_A, KeyEvent.CTRL_DOWN_MASK);
		assertTrue(press(PAD_A).isConsumed());
	}

	@Test
	public void focusLostReleasesThePadButtons()
	{
		c.setEnabled(true);
		press(PAD_A);
		press(PadButton.LB.keyCode);
		c.focusLost();
		SkateInput in = skate();
		assertFalse(in.pushHeld);
		assertFalse(in.powerslide);
	}

	@Test
	public void aKeySettingMovingOntoAHeldPadKeyDoesNotLeaveItStuck()
	{
		c.setEnabled(true);
		press(PAD_A);
		c.configureBoardKey(PAD_A);
		assertFalse(skate().pushHeld);
		c.setOnFoot(true);
		assertFalse(foot().sprint);
	}

	@Test
	public void aCustomPresetRemapsTheButtons()
	{
		PadPreset custom = PadPreset.skate3()
			.with(PadButton.A, new PadPreset.Binding(PadAction.BRAKE, PadAction.JUMP))
			.with(PadButton.LB, new PadPreset.Binding(PadAction.PUSH, PadAction.SPRINT));
		c.configurePreset(custom);
		c.setEnabled(true);
		press(PAD_A);
		SkateInput in = skate();
		assertFalse(in.pushHeld);
		assertTrue(in.powerslide);
		release(PAD_A);
		press(PadButton.LB.keyCode);
		in = skate();
		assertTrue(in.pushPressed);
		assertTrue(in.pushHeld);
		assertFalse(in.powerslide);
		release(PadButton.LB.keyCode);
		c.setOnFoot(true);
		press(PAD_A);
		assertTrue(foot().jumpPressed);
	}

	@Test
	public void changingThePresetLetsGoOfTheButtonsHeld()
	{
		c.setEnabled(true);
		press(PAD_A);
		c.configurePreset(PadPreset.skate3().with(PadButton.A, PadContext.BOARD, PadAction.BRAKE));
		SkateInput in = skate();
		assertFalse(in.pushHeld);
		assertFalse(in.powerslide);
		// its release afterwards does nothing
		release(PAD_A);
		assertFalse(skate().powerslide);
	}

	@Test
	public void anOllieButtonChargesWhileHeldAndPopsWhenLetGo()
	{
		c.configurePreset(PadPreset.skate3().with(PadButton.L3, PadContext.BOARD, PadAction.OLLIE));
		c.setEnabled(true);
		press(PadButton.L3.keyCode);
		SkateInput in = skate();
		assertTrue(in.charge);
		assertTrue(in.crouch);
		assertTrue(in.gestures.isEmpty());
		release(PadButton.L3.keyCode);
		in = skate();
		assertFalse(in.charge);
		assertEquals(1, in.gestures.size());
		assertEquals(Gesture.Direction.UP, in.gestures.get(0).direction);
		assertFalse(in.gestures.get(0).modified);
	}

	@Test
	public void anOllieLetGoOnFootDoesNothing()
	{
		c.configurePreset(PadPreset.skate3().with(PadButton.L3, PadContext.BOARD, PadAction.OLLIE));
		c.setEnabled(true);
		press(PadButton.L3.keyCode);
		c.setOnFoot(true);
		release(PadButton.L3.keyCode);
		c.setOnFoot(false);
		assertTrue(skate().gestures.isEmpty());
	}

	@Test
	public void aCameraButtonOnFootOrbitsWithTheRightStick()
	{
		c.configurePreset(PadPreset.skate3().with(PadButton.DPAD_DOWN, PadContext.FOOT, PadAction.CAMERA_ORBIT));
		c.setEnabled(true);
		c.setOnFoot(true);
		moved(400, 300);
		press(PadButton.DPAD_DOWN.keyCode);
		moved(430, 300);
		moved(450, 310);
		assertEquals(50, c.drainOrbitPixels());
		release(PadButton.DPAD_DOWN.keyCode);
		moved(500, 300);
		assertEquals(0, c.drainOrbitPixels());
	}

	@Test
	public void inTheAirAButtonDoesItsAirAction()
	{
		c.configurePreset(PadPreset.skate3().with(PadButton.LT, PadContext.AIR, PadAction.LEAN_FORWARD));
		c.setEnabled(true);
		press(PadButton.LT.keyCode);
		SkateInput in = skate();
		assertTrue(in.grabLeft);
		assertFalse(in.leanForwardKey);
		c.setAirborne(true);
		in = skate();
		assertFalse(in.grabLeft);
		assertTrue(in.leanForwardKey);
	}

	@Test
	public void unboundButtonsDoNothing()
	{
		c.setEnabled(true);
		for (PadButton b : new PadButton[]{PadButton.L3, PadButton.R3, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT,
			PadButton.DPAD_RIGHT})
		{
			press(b.keyCode);
		}
		SkateInput in = skate();
		assertFalse(in.pushHeld || in.powerslide || in.grabLeft || in.grabRight || in.charge || in.resetRequested);
		assertFalse(c.consumeExit());
		assertFalse(c.consumeControlsToggle());
	}

	private void moved(int x, int y)
	{
		now += 10;
		c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false, MouseEvent.NOBUTTON));
	}
}
