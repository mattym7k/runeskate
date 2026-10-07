package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.input.StickManual.Kind;
import org.junit.Test;

/** The up-then-down manual gesture's timing. */
public class StickManualTest
{
	private final StickManual m = new StickManual();

	@Test
	public void upThenDownWithinTheWindowIsAManual()
	{
		assertEquals(Kind.NONE, m.up(1000));
		assertEquals(Kind.MANUAL, m.down(1000 + StickManual.WINDOW_MS));
	}

	@Test
	public void downThenUpIsANoseManual()
	{
		assertEquals(Kind.NONE, m.down(1000));
		assertEquals(Kind.NOSE, m.up(1200));
	}

	@Test
	public void tooSlowIsNothing()
	{
		m.up(1000);
		assertEquals(Kind.NONE, m.down(1000 + StickManual.WINDOW_MS + 1));
		// but that down starts a nose manual's gesture
		assertEquals(Kind.NOSE, m.up(1000 + StickManual.WINDOW_MS + 100));
	}

	@Test
	public void aGestureIsUsedOnce()
	{
		m.up(1000);
		assertEquals(Kind.MANUAL, m.down(1100));
		assertEquals(Kind.NONE, m.up(1150));
		assertEquals(Kind.NONE, m.up(1200));
	}

	@Test
	public void resetForgetsTheFirstHalf()
	{
		m.up(1000);
		m.reset();
		assertEquals(Kind.NONE, m.down(1100));
	}

	@Test
	public void twoUpsAreNotAGesture()
	{
		m.up(1000);
		assertEquals(Kind.NONE, m.up(1100));
		assertEquals(Kind.MANUAL, m.down(1200));
	}
}
