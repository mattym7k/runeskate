package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.input.NearMiss;
import org.junit.Test;

public class TrickHintsTest
{
	@Test
	public void aHintShowsThenFades()
	{
		TrickHints h = new TrickHints();
		assertNull(h.text(0f));
		assertTrue(h.offer(NearMiss.TOO_SLOW, 10f));
		assertEquals(NearMiss.TOO_SLOW.hint, h.text(10f));
		assertEquals(1f, h.alpha(11.9f), 1e-4f);
		assertEquals(0.5f, h.alpha(12.25f), 1e-4f);
		assertNull(h.text(12.5f));
	}

	@Test
	public void controllerModeHintsNameTheRightStick()
	{
		TrickHints h = new TrickHints();
		h.offer(NearMiss.NO_WIND_UP, 0f);
		assertEquals(NearMiss.NO_WIND_UP.hint, h.text(1f, false));
		assertEquals("Pull {RS} down first, then flick", h.text(1f, true));
		assertNull(h.text(5f, true));
		for (NearMiss m : NearMiss.values())
		{
			assertTrue(m.name(), m.controllerHint.contains("{RS}"));
		}
	}

	@Test
	public void hintsAreThrottled()
	{
		TrickHints h = new TrickHints();
		assertTrue(h.offer(NearMiss.TOO_SLOW, 0f));
		assertFalse(h.offer(NearMiss.NO_WIND_UP, 3.9f));
		assertEquals(NearMiss.TOO_SLOW.hint, h.text(1f));
		assertTrue(h.offer(NearMiss.NO_WIND_UP, 4f));
		assertEquals(NearMiss.NO_WIND_UP.hint, h.text(4f));
	}

	@Test
	public void aTrickClearsTheHintButNotTheThrottle()
	{
		TrickHints h = new TrickHints();
		h.offer(NearMiss.TOO_SHORT, 0f);
		h.onTrick();
		assertNull(h.text(0.5f));
		assertFalse(h.offer(NearMiss.TOO_SHORT, 1f));
	}

	@Test
	public void nullIsIgnoredAndResetClears()
	{
		TrickHints h = new TrickHints();
		assertFalse(h.offer(null, 0f));
		h.offer(NearMiss.TOO_SHORT, 0f);
		h.reset();
		assertNull(h.text(0f));
		assertTrue(h.offer(NearMiss.TOO_SHORT, 0.1f));
	}
}
