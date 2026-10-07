package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AnimClockTest
{
	/** Three frames of 5, 10 and 5 client ticks (0.1, 0.2 and 0.1 s). */
	private static final int[] LENGTHS = {5, 10, 5};

	@Test
	public void loopsAtTheGamesPace()
	{
		AnimClock c = new AnimClock();
		assertEquals(0, c.loop(LENGTHS, 1f, 0.05f));
		assertEquals(1, c.loop(LENGTHS, 1f, 0.06f));
		assertEquals(1, c.loop(LENGTHS, 1f, 0.15f));
		assertEquals(2, c.loop(LENGTHS, 1f, 0.05f));
		assertEquals(0, c.loop(LENGTHS, 1f, 0.1f));
	}

	@Test
	public void playsFasterAtAHigherRate()
	{
		AnimClock c = new AnimClock();
		// 0.1 s at twice the pace is 10 ticks: past frame 0 (5) and halfway through frame 1
		assertEquals(1, c.loop(LENGTHS, 2f, 0.1f));
	}

	@Test
	public void aHitchSkipsAtMostOneCycle()
	{
		AnimClock c = new AnimClock();
		int f = c.loop(LENGTHS, 1f, 30f);
		// whatever frame it stops on, the owed time is forgotten: the next short step does not spin on
		int g = c.loop(LENGTHS, 1f, 0.01f);
		assertEquals(f, g);
	}

	@Test
	public void seeksByProgressAndResets()
	{
		AnimClock c = new AnimClock();
		assertEquals(0, c.seek(LENGTHS, 0f));
		assertEquals(1, c.seek(LENGTHS, 0.5f));
		assertEquals(2, c.seek(LENGTHS, 1f));
		c.reset();
		assertEquals(0, c.loop(LENGTHS, 1f, 0f));
		// junk is never a frame
		assertEquals(-1, c.loop(new int[0], 1f, 0.1f));
		assertEquals(-1, c.loop(null, 1f, 0.1f));
		assertEquals(-1, c.seek(null, 0.5f));
	}

	@Test
	public void aShorterAnimationStartsOver()
	{
		AnimClock c = new AnimClock();
		c.loop(new int[]{5, 5, 5, 5}, 1f, 0.35f);
		assertEquals(0, c.loop(new int[]{5}, 1f, 0f));
	}

	@Test
	public void aMayaAnimationPlaysTickByTickThroughItsDuration()
	{
		int[] ticks = AnimClock.tickFrames(4);
		assertArrayEquals(new int[]{1, 1, 1, 1}, ticks);
		assertEquals(0, AnimClock.tickFrames(0).length);
		assertEquals(0, AnimClock.tickFrames(-3).length);
		AnimClock c = new AnimClock();
		// 50 ticks a second: 0.05 s is 2.5 ticks in, so frame (tick) 2
		assertEquals(2, c.loop(ticks, 1f, 0.05f));
		// half way through is tick 2 of 4
		assertEquals(2, c.seek(ticks, 0.5f));
		assertEquals(-1, c.loop(AnimClock.tickFrames(0), 1f, 0.05f));
	}
}
