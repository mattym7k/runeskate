package com.gielinorskate.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.event.KeyEvent;
import org.junit.Test;

/** The pad tester lights the buttons whose pad keys arrive and shows the sticks. */
public class PadTesterTest
{
	private final PadTester t = new PadTester();

	@Test
	public void padKeysLightTheirButtonsWhileDown()
	{
		assertTrue(t.key(KeyEvent.VK_F17, true));
		assertTrue(t.isDown(PadButton.LB));
		assertTrue(t.wasSeen(PadButton.LB));
		assertFalse(t.isDown(PadButton.A));
		t.key(KeyEvent.VK_F17, false);
		assertFalse(t.isDown(PadButton.LB));
		assertTrue("stays ticked", t.wasSeen(PadButton.LB));
		assertTrue(t.key(KeyEvent.VK_HOME, true));
		assertTrue(t.isDown(PadButton.DPAD_LEFT));
	}

	@Test
	public void otherKeysAreNotItsBusiness()
	{
		assertFalse(t.key(KeyEvent.VK_W, true));
		assertFalse(t.key(KeyEvent.VK_SHIFT, true));
		for (PadButton b : PadButton.values())
		{
			assertFalse(t.isDown(b));
		}
	}

	@Test
	public void theArrowsAreTheLeftStick()
	{
		t.key(KeyEvent.VK_LEFT, true);
		assertEquals(-1, t.leftX());
		t.key(KeyEvent.VK_LEFT, false);
		t.key(KeyEvent.VK_DOWN, true);
		assertEquals(0, t.leftX());
		assertEquals(1, t.leftY());
	}

	@Test
	public void cursorSpeedIsTheRightStickAndAStillCursorCentresIt()
	{
		t.mouse(100, 100, 1000);
		t.mouse(160, 100, 1050);
		assertEquals(1.2f / PadTester.FULL_TILT, t.rightX(), 1e-4);
		assertEquals(0f, t.rightY(), 1e-4);
		t.mouse(160, 100, 1100);
		assertEquals("still, but not for long yet", 1f, t.rightX(), 1e-4);
		t.mouse(160, 100, 1300);
		assertEquals(0f, t.rightX(), 1e-4);
		// fast: clamped to a full tilt
		t.mouse(160, 90, 1310);
		t.mouse(160, 0, 1320);
		assertEquals(-1f, t.rightY(), 1e-4);
	}

	@Test
	public void theFirstMoveAfterAPauseHasNoSpeedYet()
	{
		t.mouse(100, 100, 1000);
		t.mouse(500, 100, 5000);
		assertEquals(0f, t.rightX(), 1e-4);
	}

	@Test
	public void resetLetsGoOfEverything()
	{
		t.key(KeyEvent.VK_F13, true);
		t.key(KeyEvent.VK_UP, true);
		t.reset();
		assertFalse(t.isDown(PadButton.A));
		assertEquals(0, t.leftY());
	}
}
