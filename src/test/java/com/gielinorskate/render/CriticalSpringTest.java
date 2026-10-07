package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CriticalSpringTest
{
	@Test
	public void settlesOnTargetWithoutOvershoot()
	{
		CriticalSpring s = new CriticalSpring();
		float prev = 0f;
		for (int i = 0; i < 120; i++)
		{
			s.step(1f, 10f, 1 / 60f);
			assertTrue(s.value <= 1f + 1e-6f);
			assertTrue("monotonic", s.value >= prev - 1e-6f);
			prev = s.value;
		}
		assertEquals(1f, s.value, 1e-3f);
	}

	@Test
	public void frameRateIndependent()
	{
		CriticalSpring a = new CriticalSpring();
		CriticalSpring b = new CriticalSpring();
		a.step(1f, 8f, 0.1f);
		for (int i = 0; i < 10; i++)
		{
			b.step(1f, 8f, 0.01f);
		}
		assertEquals(a.value, b.value, 1e-4f);
		assertEquals(a.velocity, b.velocity, 1e-3f);
	}

	@Test
	public void kickPeaksAtTheRequestedAmountThenReturns()
	{
		CriticalSpring s = new CriticalSpring();
		float omega = 20f;
		s.kick(CriticalSpring.kickForPeak(0.2f, omega));
		float peak = 0f;
		float peakTime = 0f;
		for (int i = 1; i <= 400; i++)
		{
			s.step(0f, omega, 0.001f);
			if (s.value > peak)
			{
				peak = s.value;
				peakTime = i * 0.001f;
			}
		}
		assertEquals(0.2f, peak, 1e-3f);
		// peaks at 1 / omega = 0.05 s
		assertEquals(0.05f, peakTime, 2e-3f);
		// spring-back: 0.4 s = 8 / omega later it is almost home: (1 + 8) e^-8 * peak-scale
		assertTrue(s.value < 0.01f);
	}

	@Test
	public void zeroDtDoesNothing()
	{
		CriticalSpring s = new CriticalSpring();
		s.reset(0.5f);
		s.step(1f, 10f, 0f);
		assertEquals(0.5f, s.value, 0f);
	}
}
