package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class CalloutTest
{
	@Test
	public void thresholdsPickTheBiggestWordEarned()
	{
		assertNull(Callout.forValue(0));
		assertNull(Callout.forValue(4_999));
		assertEquals(Callout.NICE, Callout.forValue(5_000));
		assertEquals(Callout.NICE, Callout.forValue(14_999));
		assertEquals(Callout.GZ, Callout.forValue(15_000));
		assertEquals(Callout.SPICY, Callout.forValue(40_000));
		assertEquals(Callout.PURPLE, Callout.forValue(100_000));
		assertEquals(Callout.PURPLE, Callout.forValue(249_999));
		assertEquals(Callout.PET_DROP, Callout.forValue(250_000));
		assertEquals(Callout.PET_DROP, Callout.forValue(Integer.MAX_VALUE));
	}

	@Test
	public void varietyLinesNameNewTricksAndLongCombos()
	{
		assertTrue(Callout.varietyLines(0, false).isEmpty());
		assertEquals(Arrays.asList("New trick +25%"), Callout.varietyLines(1, false));
		assertEquals(Arrays.asList("3 new tricks +25%", "Long combo +1"), Callout.varietyLines(3, true));
		assertEquals(Arrays.asList("Long combo +1"), Callout.varietyLines(0, true));
	}

	@Test
	public void cleanOnlyShowsUnderAWord()
	{
		assertTrue(Callout.extras(null, true, 2, 2).isEmpty());
		assertEquals(Arrays.asList("Clean"), Callout.extras(Callout.NICE, true, 2, 2));
		assertTrue(Callout.extras(Callout.NICE, false, 2, 2).isEmpty());
	}

	@Test
	public void multiAndMaxComboLines()
	{
		assertTrue(Callout.extras(null, false, 9, 98).isEmpty());
		assertEquals(Arrays.asList("x10 Multi"), Callout.extras(null, false, 10, 12));
		assertEquals(Arrays.asList("Clean", "x12 Multi", "Max combo!"),
			Callout.extras(Callout.GZ, true, 12, 99));
	}
}
