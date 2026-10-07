package com.gielinorskate.input;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** F tap / hold detection. */
public class BoardKeyTest
{
	private final BoardKey key = new BoardKey();
	private final BoardKey.Edges e = new BoardKey.Edges();

	@Test
	public void aPressIsReportedAtOnce()
	{
		key.press(0);
		key.poll(1, e);
		assertTrue(e.pressed);
		assertFalse(e.tapped);
		assertFalse(e.held);
		key.poll(2, e);
		assertFalse("reported once", e.pressed);
	}

	@Test
	public void aQuickReleaseIsATap()
	{
		key.press(0);
		key.poll(10, e);
		key.release(999);
		key.poll(1000, e);
		assertTrue(e.tapped);
		assertFalse(e.held);
	}

	@Test
	public void holdingOneSecondIsAHoldReportedWhileStillHeldAndOnlyOnce()
	{
		key.press(0);
		key.poll(10, e);
		key.poll(999, e);
		assertFalse(e.held);
		key.poll(1000, e);
		assertTrue(e.held);
		key.poll(1500, e);
		assertFalse(e.held);
		key.release(2000);
		key.poll(2001, e);
		assertFalse("a hold is no tap", e.tapped);
		assertFalse(e.held);
	}

	@Test
	public void aHoldReleasedBeforeAnyPollIsStillAHold()
	{
		key.press(0);
		key.release(1200);
		key.poll(1300, e);
		assertTrue(e.pressed);
		assertTrue(e.held);
		assertFalse(e.tapped);
	}

	@Test
	public void keyRepeatsAreIgnored()
	{
		key.press(0);
		key.poll(1, e);
		key.press(300);
		key.press(600);
		key.poll(700, e);
		assertFalse(e.pressed);
		key.poll(1000, e);
		assertTrue("the hold still counts from the first press", e.held);
	}

	@Test
	public void resetForgetsAPressInProgress()
	{
		key.press(0);
		key.reset();
		key.poll(1500, e);
		assertFalse(e.pressed);
		assertFalse(e.held);
		assertFalse(key.isDown());
	}
}
