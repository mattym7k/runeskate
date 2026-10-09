package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlsCardTimerTest
{
	@Test
	public void beforeSkateModeStartsAlphaIsZero()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		assertEquals(0f, t.alpha(0f), 0f);
		assertEquals(0f, t.alpha(100f), 0f);
	}

	@Test
	public void theBasicsPageStaysUpUntilThePlayerHasPushedAndJumped()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.onSkateStart(true);
		assertEquals(1f, t.alpha(500f), 0f);
		t.onPush(500f);
		assertEquals("pushing alone is not enough", 1f, t.alpha(900f), 0f);
		t.onJump(900f);
		assertTrue(t.learnedBasics());
		// lingers, then fades over a second
		assertEquals(1f, t.alpha(901.9f), 0f);
		assertEquals(0.5f, t.alpha(902.5f), 1e-4f);
		assertEquals(0f, t.alpha(903f), 0f);
	}

	@Test
	public void itComesBackNextSessionUntilTheBasicsAreLearned()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.onSkateStart(true);
		t.onPush(1f);
		t.onSkateStart(true);
		assertEquals(1f, t.alpha(10f), 0f);
		t.onJump(11f);
		t.onSkateStart(true);
		assertEquals(0f, t.alpha(100f), 0f);
	}

	@Test
	public void settingOffOrLearnedEarlierMeansNoAutoShow()
	{
		ControlsCardTimer off = new ControlsCardTimer();
		off.onSkateStart(false);
		assertEquals(0f, off.alpha(0f), 0f);

		ControlsCardTimer learned = new ControlsCardTimer();
		learned.markLearned();
		learned.onSkateStart(true);
		assertEquals(0f, learned.alpha(0f), 0f);
	}

	@Test
	public void hShowsTheCardThenHidesIt()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.toggle(0f);
		assertEquals(1f, t.alpha(0f), 0f);
		assertEquals("stays up while shown by H", 1f, t.alpha(1000f), 0f);
		t.toggle(1001f);
		assertEquals(0f, t.alpha(1001f), 0f);
		t.toggle(1002f);
		assertEquals(1f, t.alpha(1002f), 0f);
	}

	@Test
	public void hDuringTheAutoShowHidesIt()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.onSkateStart(true);
		assertEquals(1f, t.alpha(0.5f), 0f);
		t.toggle(1f);
		assertEquals(0f, t.alpha(1f), 0f);
		// learning the basics afterwards does not bring it back
		t.onPush(2f);
		t.onJump(2f);
		assertEquals(0f, t.alpha(3f), 0f);
	}

	@Test
	public void hShownCardIsNotFadedByLearningTheBasics()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.onSkateStart(true);
		t.toggle(1f);
		t.toggle(2f);
		t.onPush(3f);
		t.onJump(3f);
		assertEquals(1f, t.alpha(100f), 0f);
	}

	@Test
	public void aNewSessionDropsAnHShownCard()
	{
		ControlsCardTimer t = new ControlsCardTimer();
		t.markLearned();
		t.toggle(0f);
		t.onSkateStart(true);
		assertFalse(t.alpha(10f) > 0f);
	}
}
