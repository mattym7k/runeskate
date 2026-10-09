package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.GielinorSkateConfig.FlickButton;
import com.gielinorskate.GielinorSkateConfig.TrickControls;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Test;

/** Keyboard trick controls, the flick button setting and mirrored flicks through InputController. */
public class KeyboardTrickInputTest
{
	private static final Component SOURCE = new Canvas();

	private static KeyEvent key(int code, boolean pressed, int modifiers)
	{
		return new KeyEvent(SOURCE, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
			System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED);
	}

	private static InputController controller(TrickControls controls)
	{
		InputController c = new InputController(System::currentTimeMillis);
		c.setEnabled(true);
		c.configureTricks(controls, FlickButton.RIGHT, false);
		c.configure(KeyEvent.VK_SPACE, 100);
		return c;
	}

	@Test
	public void heldTrickKeyChargesThenFiresOnRelease()
	{
		InputController c = controller(TrickControls.KEYBOARD);
		KeyEvent press = key(KeyEvent.VK_1, true, 0);
		c.keyPressed(press);
		assertTrue(press.isConsumed());
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.charge);
		assertTrue(in.crouch);
		assertTrue(in.gestures.isEmpty());

		c.keyReleased(key(KeyEvent.VK_1, false, 0));
		in = new SkateInput();
		c.drainInto(in);
		assertFalse(in.charge);
		assertEquals(1, in.gestures.size());
		assertEquals(Trick.KICKFLIP, TrickCatalog.forGesture(in.gestures.get(0)));
	}

	@Test
	public void spaceIsTheOllieAndTheManualMovesToC()
	{
		InputController c = controller(TrickControls.BOTH);
		c.keyPressed(key(KeyEvent.VK_SPACE, true, 0));
		c.keyReleased(key(KeyEvent.VK_SPACE, false, 0));
		c.keyPressed(key(KeyEvent.VK_C, true, 0));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.manualHeld);
		assertEquals(Trick.OLLIE, TrickCatalog.forGesture(in.gestures.get(0)));
	}

	@Test
	public void mouseModeLeavesSpaceAsTheManual()
	{
		InputController c = controller(TrickControls.MOUSE);
		c.keyPressed(key(KeyEvent.VK_SPACE, true, 0));
		c.keyPressed(key(KeyEvent.VK_1, true, 0));
		c.keyReleased(key(KeyEvent.VK_1, false, 0));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.manualHeld);
		assertTrue(in.gestures.isEmpty());
	}

	@Test
	public void altMakesANollieAndShiftAtReleaseTheHardVersion()
	{
		InputController c = controller(TrickControls.KEYBOARD);
		c.keyPressed(key(KeyEvent.VK_1, true, InputEvent.ALT_DOWN_MASK));
		c.keyReleased(key(KeyEvent.VK_1, false, 0));
		c.keyPressed(key(KeyEvent.VK_SHIFT, true, InputEvent.SHIFT_DOWN_MASK));
		c.keyPressed(key(KeyEvent.VK_2, true, InputEvent.SHIFT_DOWN_MASK));
		c.keyReleased(key(KeyEvent.VK_2, false, InputEvent.SHIFT_DOWN_MASK));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(Trick.NOLLIE_KICKFLIP, TrickCatalog.forGesture(in.gestures.get(0)));
		assertEquals(Trick.INWARD_HEELFLIP, TrickCatalog.forGesture(in.gestures.get(1)));
	}

	@Test
	public void keyRepeatWhileHeldFiresOnce()
	{
		InputController c = controller(TrickControls.KEYBOARD);
		c.keyPressed(key(KeyEvent.VK_3, true, 0));
		c.keyPressed(key(KeyEvent.VK_3, true, 0));
		c.keyPressed(key(KeyEvent.VK_3, true, 0));
		c.keyReleased(key(KeyEvent.VK_3, false, 0));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(1, in.gestures.size());
	}

	@Test
	public void manualKeyFallsBackToCOnlyWithKeyboardTricks()
	{
		assertEquals(KeyEvent.VK_C, InputController.effectiveManualKey(KeyEvent.VK_SPACE, true));
		assertEquals(KeyEvent.VK_SPACE, InputController.effectiveManualKey(KeyEvent.VK_SPACE, false));
		assertEquals(KeyEvent.VK_C, InputController.effectiveManualKey(KeyEvent.VK_1, true));
		assertEquals(KeyEvent.VK_X, InputController.effectiveManualKey(KeyEvent.VK_X, true));
		assertEquals(KeyEvent.VK_C, InputController.effectiveManualKey(KeyEvent.VK_W, true));
	}

	@Test
	public void flickButtonSetting()
	{
		assertTrue(InputController.isFlickButton(FlickButton.RIGHT, MouseEvent.BUTTON3));
		assertTrue(InputController.isFlickButton(FlickButton.RIGHT, MouseEvent.BUTTON2));
		assertFalse(InputController.isFlickButton(FlickButton.RIGHT, MouseEvent.BUTTON1));
		assertTrue(InputController.isFlickButton(FlickButton.LEFT, MouseEvent.BUTTON1));
		assertFalse(InputController.isFlickButton(FlickButton.LEFT, MouseEvent.BUTTON3));
		assertTrue(InputController.isFlickButton(FlickButton.MIDDLE, MouseEvent.BUTTON2));
		assertFalse(InputController.isFlickButton(FlickButton.MIDDLE, MouseEvent.BUTTON3));
	}

	private static MouseEvent mouse(int id, int x, int y, long when, int button, int mask)
	{
		return new MouseEvent(SOURCE, id, when, mask, x, y, 1, false, button);
	}

	@Test
	public void leftFlickButtonFlicksAndMirrorSwapsTheTrick()
	{
		InputController c = new InputController(() -> 1000L);
		c.setEnabled(true);
		c.configureTricks(TrickControls.MOUSE, FlickButton.LEFT, true);
		int b = MouseEvent.BUTTON1;
		int m = InputEvent.BUTTON1_DOWN_MASK;
		c.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 100, 100, 1000, b, m));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 100, 125, 1010, 0, m));
		// a fast up-left flick: a kickflip, mirrored to a heelflip
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 85, 110, 1030, 0, m));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 70, 95, 1050, 0, m));
		c.mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 70, 95, 1060, b, 0));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(1, in.gestures.size());
		assertEquals(Trick.HEELFLIP, TrickCatalog.forGesture(in.gestures.get(0)));
	}

	@Test
	public void keyboardOnlyIgnoresMouseFlicks()
	{
		InputController c = new InputController(() -> 1000L);
		c.setEnabled(true);
		c.configureTricks(TrickControls.KEYBOARD, FlickButton.RIGHT, false);
		int b = MouseEvent.BUTTON3;
		int m = InputEvent.BUTTON3_DOWN_MASK;
		c.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, 100, 100, 1000, b, m));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 100, 125, 1010, 0, m));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 100, 80, 1030, 0, m));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, 100, 60, 1050, 0, m));
		c.mouseReleased(mouse(MouseEvent.MOUSE_RELEASED, 100, 60, 1060, b, 0));
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.gestures.isEmpty());
	}
}
