package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class EffectPickerTest
{
	private static final float DT = 0.02f;
	private static final List<SkateEvent> NONE = Collections.emptyList();

	@Test
	public void dustOnlyAfterRealAir()
	{
		EffectPicker picker = new EffectPicker();
		List<SkateEvent> land = Arrays.asList(SkateEvent.LAND);
		assertTrue(picker.pick(land, false, 0.3f, null, DT).isEmpty());
		assertEquals(Arrays.asList(EffectPicker.LANDING_PUFF), picker.pick(land, false, 0.5f, null, DT));
	}

	@Test
	public void sparksOnContactThenEvery150ms()
	{
		EffectPicker picker = new EffectPicker();
		assertEquals(Arrays.asList(EffectPicker.GRIND_SPARKS), picker.pick(NONE, true, 0f, null, DT));
		int bursts = 0;
		// 0.3 s more (float steps): two more bursts
		for (int i = 0; i < 16; i++)
		{
			bursts += picker.pick(NONE, true, 0f, null, DT).size();
		}
		assertEquals(2, bursts);
		assertTrue(picker.pick(NONE, false, 0f, null, DT).isEmpty());
	}

	@Test
	public void bailSmokeAndBigComboConfetti()
	{
		EffectPicker picker = new EffectPicker();
		assertEquals(Arrays.asList(EffectPicker.BAIL_SMOKE), picker.pick(Arrays.asList(SkateEvent.BAIL), false, 0f,
			null, DT));
		assertTrue(picker.pick(NONE, false, 0f, ComboScorer.Result.landed(249_999), DT).isEmpty());
		assertEquals(Arrays.asList(EffectPicker.BIG_COMBO_CONFETTI),
			picker.pick(NONE, false, 0f, ComboScorer.Result.landed(250_000), DT));
		assertTrue("a lost combo drops no pet", picker.pick(NONE, false, 0f, ComboScorer.Result.bailed(300_000), DT)
			.isEmpty());
	}
}
