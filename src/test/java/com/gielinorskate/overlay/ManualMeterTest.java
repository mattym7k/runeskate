package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class ManualMeterTest
{
	@Test
	public void inactiveUntilAManualStarts()
	{
		ManualMeter m = new ManualMeter();
		m.update(null, 10f);
		assertNull(m.text(0f));
		assertNull(m.text(10f));
	}

	@Test
	public void textFormatsOneDecimalSecond()
	{
		ManualMeter m = new ManualMeter();
		m.update(Trick.MANUAL, 2.0f);
		m.update(Trick.MANUAL, 3.4f);
		assertEquals("Manual 1.4s", m.text(3.4f));
	}

	@Test
	public void endingTheManualClearsTheText()
	{
		ManualMeter m = new ManualMeter();
		m.update(Trick.MANUAL, 0f);
		m.update(Trick.MANUAL, 1f);
		assertNotNull(m.text(1f));

		m.update(null, 1.5f);
		assertNull(m.text(0f));
		assertNull(m.text(1.5f));
	}

	@Test
	public void startingASecondManualResetsTheTimer()
	{
		ManualMeter m = new ManualMeter();
		m.update(Trick.MANUAL, 0f);
		m.update(null, 1f);
		m.update(Trick.MANUAL, 5f);
		assertEquals("Manual 0.0s", m.text(5f));
		assertEquals("Manual 0.7s", m.text(5.7f));
	}

	@Test
	public void swappingBetweenManualAndNoseManualRestartsTheTimer()
	{
		// the scorer counts the two as separate holds, so the meter does too
		ManualMeter m = new ManualMeter();
		m.update(Trick.MANUAL, 0f);
		m.update(Trick.MANUAL, 2f);
		m.update(Trick.NOSE_MANUAL, 2f);
		assertEquals("Nose Manual 0.0s", m.text(2f));
		m.update(Trick.NOSE_MANUAL, 2.5f);
		m.update(Trick.MANUAL, 2.5f);
		assertEquals("Manual 0.3s", m.text(2.8f));
	}
}
