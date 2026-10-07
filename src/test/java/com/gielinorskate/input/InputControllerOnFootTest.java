package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.physics.SkateInput;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Test;

/** The board key and the on-foot reading of the keys. */
public class InputControllerOnFootTest
{
	private static final Component SOURCE = new Canvas();

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

	private KeyEvent press(int code)
	{
		KeyEvent e = key(code, true);
		c.keyPressed(e);
		return e;
	}

	private KeyEvent release(int code)
	{
		KeyEvent e = key(code, false);
		c.keyReleased(e);
		return e;
	}

	private FootControls foot()
	{
		FootControls f = new FootControls();
		c.drainFoot(f);
		return f;
	}

	private BoardKey.Edges board(long at)
	{
		now = at;
		BoardKey.Edges e = new BoardKey.Edges();
		c.pollBoardKey(e);
		return e;
	}

	@Test
	public void fIsTheBoardKeyAndIsConsumedOnTheBoard()
	{
		c.setEnabled(true);
		KeyEvent e = press(KeyEvent.VK_F);
		assertTrue(e.isConsumed());
		assertTrue(board(1010).pressed);
		release(KeyEvent.VK_F);
		assertTrue(board(1020).tapped);
	}

	@Test
	public void holdingFOneSecondIsAHold()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_F);
		assertTrue(board(1001).pressed);
		// key repeats while held
		press(KeyEvent.VK_F);
		assertFalse(board(1999).held);
		assertTrue(board(2000).held);
		release(KeyEvent.VK_F);
		BoardKey.Edges after = board(2100);
		assertFalse(after.tapped);
		assertFalse(after.held);
	}

	@Test
	public void anUnsetBoardKeyDoesNothing()
	{
		c.configureBoardKey(KeyEvent.VK_UNDEFINED);
		c.setEnabled(true);
		assertFalse(press(KeyEvent.VK_F).isConsumed());
		assertFalse(board(1010).pressed);
	}

	@Test
	public void wasdWalkAndShiftSprintsOnFoot()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_W);
		press(KeyEvent.VK_A);
		press(KeyEvent.VK_SHIFT);
		FootControls f = foot();
		assertTrue(f.forward);
		assertTrue(f.left);
		assertFalse(f.back);
		assertFalse(f.right);
		assertTrue(f.sprint);
		release(KeyEvent.VK_W);
		press(KeyEvent.VK_S);
		press(KeyEvent.VK_D);
		f = foot();
		assertFalse(f.forward);
		assertTrue(f.back);
		assertTrue(f.right);
	}

	@Test
	public void keyRemappingsArrowsWalkToo()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		// Key Remapping rewrites W to the Up arrow, keeping the letter
		c.keyPressed(key(KeyEvent.VK_UP, 'w', true));
		assertTrue(foot().forward);
		c.keyReleased(key(KeyEvent.VK_UP, 'w', false));
		c.keyPressed(key(KeyEvent.VK_DOWN, 's', true));
		FootControls f = foot();
		assertFalse(f.forward);
		assertTrue(f.back);
	}

	@Test
	public void controllerModesLeanArrowsWalkForwardAndBack()
	{
		c.configureController(true, KeyEvent.VK_B);
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_UP);
		assertTrue(foot().forward);
		release(KeyEvent.VK_UP);
		press(KeyEvent.VK_DOWN);
		FootControls f = foot();
		assertFalse(f.forward);
		assertTrue(f.back);
	}

	@Test
	public void spaceJumpsOnFootOncePerPressAndIsNoTrick()
	{
		c.configureTricks(GielinorSkateConfig.TrickControls.BOTH, GielinorSkateConfig.FlickButton.RIGHT, false);
		c.setEnabled(true);
		c.setOnFoot(true);
		assertTrue(press(KeyEvent.VK_SPACE).isConsumed());
		press(KeyEvent.VK_SPACE);
		assertTrue(foot().jumpPressed);
		assertFalse(foot().jumpPressed);
		release(KeyEvent.VK_SPACE);
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue("no ollie fired", in.gestures.isEmpty());
		assertFalse("no manual", in.manualHeld);
		press(KeyEvent.VK_SPACE);
		assertTrue(foot().jumpPressed);
	}

	@Test
	public void spaceOnTheBoardIsNoJump()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_SPACE);
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue("still the manual", in.manualHeld);
		release(KeyEvent.VK_SPACE);
		c.setOnFoot(true);
		assertFalse(foot().jumpPressed);
	}

	@Test
	public void spaceHeldAcrossADismountIsNotANewJump()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_SPACE);
		c.setOnFoot(true);
		press(KeyEvent.VK_SPACE); // a key repeat
		assertFalse(foot().jumpPressed);
	}

	@Test
	public void qAndEDropOrPickUpOnFootOnly()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_Q);
		release(KeyEvent.VK_Q);
		c.setOnFoot(true);
		assertFalse("a grab on the board is no drop", foot().dropPickupPressed);
		press(KeyEvent.VK_E);
		press(KeyEvent.VK_E);
		assertTrue(foot().dropPickupPressed);
		assertFalse("once per press", foot().dropPickupPressed);
		release(KeyEvent.VK_E);
		press(KeyEvent.VK_Q);
		assertTrue(foot().dropPickupPressed);
	}

	@Test
	public void switchingModesDropsPendingEdges()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_SPACE);
		press(KeyEvent.VK_Q);
		c.setOnFoot(false);
		c.setOnFoot(true);
		FootControls f = foot();
		assertFalse(f.jumpPressed);
		assertFalse(f.dropPickupPressed);
	}

	@Test
	public void escStillExitsOnFoot()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_ESCAPE);
		assertTrue(c.consumeExit());
	}

	@Test
	public void enablingStartsOnTheBoard()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		c.setEnabled(true);
		press(KeyEvent.VK_SPACE);
		assertFalse(foot().jumpPressed);
	}

	@Test
	public void boardKeyFollowsTheConfiguredKey()
	{
		c.configureBoardKey(KeyEvent.VK_G);
		c.setEnabled(true);
		assertFalse(press(KeyEvent.VK_F).isConsumed());
		assertTrue(press(KeyEvent.VK_G).isConsumed());
		assertEquals(true, board(1010).pressed);
	}

	@Test
	public void aBoardKeyOnEscStillLetsEscExitAndFIsTheBoardKey()
	{
		c.configureBoardKey(KeyEvent.VK_ESCAPE);
		c.setEnabled(true);
		press(KeyEvent.VK_ESCAPE);
		assertTrue(c.consumeExit());
		assertFalse(board(1010).pressed);
		assertTrue(press(KeyEvent.VK_F).isConsumed());
		assertTrue(board(1020).pressed);
	}

	@Test
	public void aBoardKeyOnAHardCodedSkateKeyLeavesThatKeyAlone()
	{
		for (int code : new int[]{KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_SHIFT,
			KeyEvent.VK_Q, KeyEvent.VK_E, KeyEvent.VK_H, KeyEvent.VK_R, KeyEvent.VK_UP, KeyEvent.VK_SPACE})
		{
			assertEquals(KeyEvent.VK_F, InputController.effectiveBoardKey(code, false));
		}
		c.configureBoardKey(KeyEvent.VK_W);
		c.setEnabled(true);
		press(KeyEvent.VK_W);
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertTrue(in.pushPressed);
		assertFalse(board(1010).pressed);
	}

	@Test
	public void spaceIsNeverTheBoardKeySoItStillJumpsOnFoot()
	{
		c.configureBoardKey(KeyEvent.VK_SPACE);
		c.setEnabled(true);
		c.setOnFoot(true);
		press(KeyEvent.VK_SPACE);
		assertTrue(foot().jumpPressed);
		assertFalse(board(1010).pressed);
	}

	@Test
	public void withKeyboardTricksATrickKeyIsNotTheBoardKey()
	{
		assertEquals(KeyEvent.VK_1, InputController.effectiveBoardKey(KeyEvent.VK_1, false));
		assertEquals(KeyEvent.VK_F, InputController.effectiveBoardKey(KeyEvent.VK_1, true));
		c.configureBoardKey(KeyEvent.VK_1);
		c.configureTricks(GielinorSkateConfig.TrickControls.KEYBOARD, GielinorSkateConfig.FlickButton.RIGHT, false);
		c.setEnabled(true);
		press(KeyEvent.VK_1);
		assertFalse(board(1010).pressed);
		release(KeyEvent.VK_1);
		SkateInput in = new SkateInput();
		c.drainInto(in);
		assertEquals(1, in.gestures.size());
		// switching keyboard tricks off gives the key back to the board
		c.configureTricks(GielinorSkateConfig.TrickControls.MOUSE, GielinorSkateConfig.FlickButton.RIGHT, false);
		press(KeyEvent.VK_1);
		assertTrue(board(1020).pressed);
	}

	@Test
	public void anUnsetBoardKeyStaysUnset()
	{
		assertEquals(KeyEvent.VK_UNDEFINED, InputController.effectiveBoardKey(KeyEvent.VK_UNDEFINED, true));
		assertEquals(KeyEvent.VK_G, InputController.effectiveBoardKey(KeyEvent.VK_G, true));
	}

	@Test
	public void changingTheBoardKeyWhileItIsHeldDoesNotLeaveItStuck()
	{
		c.setEnabled(true);
		press(KeyEvent.VK_F);
		board(1010);
		c.configureBoardKey(KeyEvent.VK_G);
		release(KeyEvent.VK_F);
		press(KeyEvent.VK_G);
		assertTrue(board(1020).pressed);
	}

	private static MouseEvent middle(int id, int x, int y)
	{
		return new MouseEvent(SOURCE, id, 0, InputEvent.BUTTON2_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON2);
	}

	@Test
	public void middleDragOrbitsTheCameraOnFoot()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		MouseEvent down = middle(MouseEvent.MOUSE_PRESSED, 100, 100);
		c.mousePressed(down);
		assertTrue(down.isConsumed());
		c.mouseDragged(middle(MouseEvent.MOUSE_DRAGGED, 130, 105));
		c.mouseDragged(middle(MouseEvent.MOUSE_DRAGGED, 110, 90));
		assertEquals(10, c.drainOrbitPixels());
		assertEquals(0, c.drainOrbitPixels());
		c.mouseReleased(middle(MouseEvent.MOUSE_RELEASED, 110, 90));
		c.mouseDragged(middle(MouseEvent.MOUSE_DRAGGED, 200, 90));
		assertEquals(0, c.drainOrbitPixels());
	}

	@Test
	public void middleDragDoesNotOrbitOnTheBoard()
	{
		c.setEnabled(true);
		c.mousePressed(middle(MouseEvent.MOUSE_PRESSED, 100, 100));
		c.mouseDragged(middle(MouseEvent.MOUSE_DRAGGED, 160, 100));
		assertEquals(0, c.drainOrbitPixels());
	}

	@Test
	public void gettingOnTheBoardEndsAnOrbit()
	{
		c.setEnabled(true);
		c.setOnFoot(true);
		c.mousePressed(middle(MouseEvent.MOUSE_PRESSED, 100, 100));
		c.setOnFoot(false);
		c.mouseDragged(middle(MouseEvent.MOUSE_DRAGGED, 160, 100));
		assertEquals(0, c.drainOrbitPixels());
	}
}
