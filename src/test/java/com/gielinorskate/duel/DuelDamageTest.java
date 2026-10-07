package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DuelDamageTest
{
	@Test
	public void theSpecTableHolds()
	{
		assertEquals(1, DuelDamage.of(1));
		assertEquals(1, DuelDamage.of(150));
		assertEquals(1, DuelDamage.of(300));
		assertEquals(3, DuelDamage.of(1_000));
		assertEquals(7, DuelDamage.of(5_000));
		assertEquals(12, DuelDamage.of(15_000));
		assertEquals(18, DuelDamage.of(40_000));
		assertEquals(22, DuelDamage.of(100_000));
	}

	@Test
	public void itCapsAt25()
	{
		assertEquals(DuelDamage.MAX, 25);
		assertEquals(25, DuelDamage.of(250_000));
		assertEquals(25, DuelDamage.of(Integer.MAX_VALUE));
	}

	@Test
	public void nothingLandedIsNoHit()
	{
		assertEquals(0, DuelDamage.of(0));
		assertEquals(0, DuelDamage.of(-5));
	}

	@Test
	public void itNeverGoesDownAsTheComboGrows()
	{
		int last = 0;
		for (int v = 0; v <= 400_000; v += 37)
		{
			int d = DuelDamage.of(v);
			assertTrue("at " + v, d >= last);
			assertTrue(d <= DuelDamage.MAX);
			last = d;
		}
	}
}
