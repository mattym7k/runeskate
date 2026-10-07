package com.gielinorskate.duel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class DuelSplatsTest
{
	private final DuelSplats splats = new DuelSplats();

	@Test
	public void aSplatShowsOverItsTargetForItsLifeThenGoes()
	{
		splats.add(true, 7, 0f);
		assertEquals(1, splats.live(true, 0.5f).size());
		assertTrue(splats.live(false, 0.5f).isEmpty());
		assertEquals(7, splats.live(true, 0.5f).get(0).damage);
		assertTrue(splats.live(true, DuelSplats.LIFE).isEmpty());
	}

	@Test
	public void aMaxHitIsMarked()
	{
		splats.add(false, DuelDamage.MAX, 0f);
		splats.add(false, DuelDamage.MAX - 1, 0f);
		List<DuelSplats.Splat> live = splats.live(false, 0f);
		assertTrue(live.get(0).max);
		assertFalse(live.get(1).max);
	}

	@Test
	public void splatsAtOnceTakeTheFreeSlotsAndAFifthReplacesTheOldest()
	{
		for (int i = 0; i < 4; i++)
		{
			splats.add(true, i + 1, i * 0.1f);
		}
		List<DuelSplats.Splat> live = splats.live(true, 0.35f);
		assertEquals(4, live.size());
		boolean[] used = new boolean[DuelSplats.SLOTS];
		for (DuelSplats.Splat s : live)
		{
			assertFalse(used[s.slot]);
			used[s.slot] = true;
		}
		splats.add(true, 9, 0.4f);
		live = splats.live(true, 0.4f);
		assertEquals(4, live.size());
		for (DuelSplats.Splat s : live)
		{
			assertTrue("the oldest (1) went", s.damage != 1);
		}
	}

	@Test
	public void itFadesOnlyAtTheEnd()
	{
		splats.add(true, 3, 0f);
		assertEquals(1f, splats.live(true, 0.2f).get(0).alpha(0.2f), 0f);
		assertTrue(splats.live(true, DuelSplats.LIFE - 0.05f).get(0).alpha(DuelSplats.LIFE - 0.05f) < 0.5f);
	}
}
