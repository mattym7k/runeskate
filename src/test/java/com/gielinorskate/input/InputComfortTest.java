package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import org.junit.Test;

/** Shift as a brake only when held alone, clicks through the sidebar and chatbox, and remapped flick buttons. */
public class InputComfortTest
{
	private static final Component SOURCE = new Canvas();

	private final long[] now = {1000L};
	private final InputController c = new InputController(() -> now[0]);

	private static KeyEvent key(int code, boolean pressed)
	{
		return new KeyEvent(SOURCE, pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, 0L, 0, code,
			KeyEvent.CHAR_UNDEFINED);
	}

	private static MouseEvent mouse(int id, int button, int x, int y, long when)
	{
		int mask = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK
			: button == MouseEvent.BUTTON2 ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
		return new MouseEvent(SOURCE, id, when, mask, x, y, 1, false, button);
	}

	private SkateInput drainAt(long ms)
	{
		now[0] = ms;
		SkateInput in = new SkateInput();
		c.drainInto(in);
		return in;
	}

	@Test
	public void shiftAloneBrakesOnlyAfterAQuarterSecond()
	{
		c.setEnabled(true);
		now[0] = 1000L;
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		SkateInput first = drainAt(1000L);
		assertTrue(first.powerslide);
		assertTrue("not a brake yet", first.brakeBlocked);
		assertTrue(drainAt(1200L).brakeBlocked);
		assertFalse("held alone for 0.25 s: brake", drainAt(1260L).brakeBlocked);
	}

	@Test
	public void shiftWithWHeldNeverBrakes()
	{
		c.setEnabled(true);
		now[0] = 1000L;
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		c.keyPressed(key(KeyEvent.VK_W, true));
		assertTrue(drainAt(1500L).brakeBlocked);
		assertTrue(drainAt(3000L).brakeBlocked);
		// W released: Shift is alone from now on, and must stay so for another 0.25 s
		c.keyReleased(key(KeyEvent.VK_W, false));
		assertTrue(drainAt(3010L).brakeBlocked);
		assertFalse(drainAt(3300L).brakeBlocked);
	}

	@Test
	public void shiftDuringAFlickWindUpDoesNotBrake()
	{
		c.setEnabled(true);
		now[0] = 1000L;
		c.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 50, 200, 1000L));
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON3, 50, 230, 1050L));
		assertTrue(drainAt(1100L).brakeBlocked);
		assertTrue("a long Shift wind-up still does not brake", drainAt(1400L).brakeBlocked);
	}

	@Test
	public void releasingShiftEndsTheBrake()
	{
		c.setEnabled(true);
		now[0] = 1000L;
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		assertFalse(drainAt(1300L).brakeBlocked);
		c.keyReleased(key(KeyEvent.VK_SHIFT, false));
		SkateInput in = drainAt(1310L);
		assertFalse(in.powerslide);
		now[0] = 1320L;
		c.keyPressed(key(KeyEvent.VK_SHIFT, true));
		assertTrue("a fresh press waits again", drainAt(1330L).brakeBlocked);
	}

	@Test
	public void clicksOverThePassThroughAreasReachTheGame()
	{
		c.setEnabled(true);
		c.setClickThroughAreas(Collections.singletonList(new Rectangle(500, 300, 200, 200)));
		MouseEvent sidebar = mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 550, 350, 0L);
		c.mousePressed(sidebar);
		assertFalse(sidebar.isConsumed());
		MouseEvent sidebarRelease = mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 550, 350, 0L);
		c.mouseReleased(sidebarRelease);
		assertFalse(sidebarRelease.isConsumed());
		MouseEvent sidebarClick = mouse(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1, 550, 350, 0L);
		c.mouseClicked(sidebarClick);
		assertFalse(sidebarClick.isConsumed());

		MouseEvent world = mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 100, 100, 0L);
		c.mousePressed(world);
		assertTrue("world clicks stay blocked", world.isConsumed());
	}

	@Test
	public void aRightPressOverTheSidebarIsNotAFlick()
	{
		c.setEnabled(true);
		c.setClickThroughAreas(Collections.singletonList(new Rectangle(0, 0, 200, 400)));
		MouseEvent press = mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 50, 200, 0L);
		c.mousePressed(press);
		assertFalse(press.isConsumed());
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON3, 50, 230, 50L));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON3, 50, 195, 120L));
		assertTrue(drainAt(130L).gestures.isEmpty());
	}

	@Test
	public void clicksAreBlockedEverywhereWithoutPassThroughAreas()
	{
		c.setEnabled(true);
		MouseEvent press = mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 550, 350, 0L);
		c.mousePressed(press);
		assertTrue(press.isConsumed());
	}

	@Test
	public void aRightDragRemappedToTheMiddleButtonStillFlicks()
	{
		// the core Camera plugin's "Right click moves camera" hands right presses on as the middle button
		c.setEnabled(true);
		c.mousePressed(mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON2, 50, 200, 0L));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON2, 50, 230, 50L));
		c.mouseDragged(mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON2, 50, 195, 120L));
		SkateInput in = drainAt(130L);
		assertEquals(1, in.gestures.size());
		assertEquals(Gesture.Direction.UP, in.gestures.get(0).direction);
	}
}
