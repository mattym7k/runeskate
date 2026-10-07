package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BailLinesTest
{
	@Test
	public void oneLineEveryTwentySeconds()
	{
		BailLines lines = new BailLines();
		assertEquals(BailLines.ROTATION[0], lines.onBail(0f, false, 500));
		assertNull(lines.onBail(5f, false, 500));
		assertNull(lines.onBail(19.9f, false, 500));
		assertEquals(BailLines.ROTATION[1], lines.onBail(20f, false, 500));
	}

	@Test
	public void rotationNeverRepeatsBackToBackAndWraps()
	{
		BailLines lines = new BailLines();
		String previous = null;
		for (int i = 0; i < BailLines.ROTATION.length * 2; i++)
		{
			String line = lines.onBail(i * 30f, false, 100);
			assertEquals(BailLines.ROTATION[i % BailLines.ROTATION.length], line);
			if (previous != null)
			{
				org.junit.Assert.assertNotEquals(previous, line);
			}
			previous = line;
		}
	}

	@Test
	public void wallAndEmptyBailsHaveTheirOwnLinesAndDoNotAdvanceTheRotation()
	{
		BailLines lines = new BailLines();
		assertEquals(BailLines.WALL, lines.onBail(0f, true, 900));
		assertEquals(BailLines.NOTHING_LOST, lines.onBail(30f, false, 0));
		assertEquals("wall wins over nothing lost", BailLines.WALL, lines.onBail(60f, true, 0));
		assertEquals(BailLines.ROTATION[0], lines.onBail(90f, false, 10));
	}

	@Test
	public void aCooledDownBailStillStartsTheCooldownOnlyWhenALineIsPosted()
	{
		BailLines lines = new BailLines();
		lines.onBail(0f, false, 10);
		assertNull(lines.onBail(10f, true, 10));
		// the skipped bail at 10 s did not restart the clock
		assertEquals(BailLines.ROTATION[1], lines.onBail(20f, false, 10));
	}
}
