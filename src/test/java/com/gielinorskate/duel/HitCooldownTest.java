package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class HitCooldownTest
{
	private final HitCooldown cd = new HitCooldown();

	@Test
	public void theFirstHitGoesAtOnce()
	{
		cd.offer(7, 5_000, 3);
		HitCooldown.Pending p = cd.poll(10f);
		assertNotNull(p);
		assertEquals(7, p.damage);
		assertEquals(5_000, p.comboValue);
		assertEquals(3, p.trickCount);
		assertNull(cd.poll(10f));
	}

	@Test
	public void aHitTooSoonWaitsForTheCooldownThenGoes()
	{
		cd.offer(3, 1_000, 1);
		assertNotNull(cd.poll(10f));
		cd.offer(5, 2_000, 2);
		assertNull(cd.poll(10.5f));
		assertNull(cd.poll(11.49f));
		HitCooldown.Pending p = cd.poll(11.5f);
		assertNotNull(p);
		assertEquals(5, p.damage);
	}

	@Test
	public void queuedHitsGoOneCooldownApartInOrder()
	{
		cd.offer(1, 100, 1);
		cd.offer(2, 600, 1);
		cd.offer(3, 1_000, 1);
		assertEquals(1, cd.poll(0f).damage);
		assertNull(cd.poll(1f));
		assertEquals(2, cd.poll(1.5f).damage);
		assertEquals(1, cd.pending());
		assertNull(cd.poll(2.9f));
		assertEquals(3, cd.poll(3.0f).damage);
		assertEquals(0, cd.pending());
	}

	@Test
	public void aLateHitAfterALongGapGoesAtOnce()
	{
		cd.offer(1, 100, 1);
		cd.poll(0f);
		cd.offer(2, 600, 1);
		assertNotNull(cd.poll(60f));
	}

	@Test
	public void clearDropsTheQueueAndTheCooldown()
	{
		cd.offer(1, 100, 1);
		cd.poll(0f);
		cd.offer(2, 600, 1);
		cd.clear();
		assertEquals(0, cd.pending());
		cd.offer(4, 1_500, 1);
		assertNotNull(cd.poll(0.1f));
	}
}
