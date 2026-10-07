package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class EffectSlotsTest
{
	@Test
	public void neverHoldsMoreThanTheCapEvictingTheOldest()
	{
		EffectSlots<String> slots = new EffectSlots<>(8);
		for (int i = 0; i < 8; i++)
		{
			assertNull(slots.add("e" + i, i * 0.01f, 5f));
		}
		assertEquals(8, slots.size());
		assertEquals("e0", slots.add("e8", 0.1f, 5f));
		assertEquals("e1", slots.add("e9", 0.1f, 5f));
		assertEquals(8, slots.size());
	}

	@Test
	public void expiresOnlyWhatIsDue()
	{
		EffectSlots<String> slots = new EffectSlots<>(8);
		slots.add("short", 0f, 0.5f);
		slots.add("long", 0f, 2f);
		assertTrue(slots.expire(0.4f).isEmpty());
		assertEquals(Arrays.asList("short"), slots.expire(0.5f));
		assertEquals(1, slots.size());
		assertEquals(Arrays.asList("long"), slots.clear());
		assertEquals(0, slots.size());
	}
}
