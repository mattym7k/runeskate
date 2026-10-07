package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.SkateEvent;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SoundPickerTest
{
	private static final float DT = 0.02f;
	private static final List<SkateEvent> NONE = Collections.emptyList();
	private static final List<TrickEvent> NO_TRICKS = Collections.emptyList();

	private static int[] ids(List<SoundPicker.Cue> cues)
	{
		return cues.stream().mapToInt(c -> c.id).toArray();
	}

	@Test
	public void popAndBailAndPlainTricks()
	{
		SoundPicker picker = new SoundPicker();
		assertEquals(0, picker.pick(NONE, NO_TRICKS, false, 0f, null, DT).size());
		assertEquals(SoundPicker.POP, ids(picker.pick(Arrays.asList(SkateEvent.POP), NO_TRICKS, false, 0f, null, DT))[0]);
		assertEquals(SoundPicker.BAIL, ids(picker.pick(Arrays.asList(SkateEvent.BAIL), NO_TRICKS, false, 0f, null, DT))[0]);
		// a plain kickflip makes no extra sound
		assertEquals(0, picker.pick(NONE, Arrays.asList(TrickEvent.trick(Trick.KICKFLIP)), false, 0f, null, DT).size());
	}

	@Test
	public void bigTricksWoosh()
	{
		assertTrue(SoundPicker.isBig(Trick.TRE_FLIP));
		assertTrue(SoundPicker.isBig(Trick.SHOVE_IT_360));
		assertTrue(SoundPicker.isBig(Trick.DOUBLE_KICKFLIP));
		assertTrue(SoundPicker.isBig(Trick.FRONTFLIP));
		assertFalse(SoundPicker.isBig(Trick.KICKFLIP));
		assertFalse(SoundPicker.isBig(Trick.POP_SHOVE_IT));
		SoundPicker picker = new SoundPicker();
		assertEquals(SoundPicker.BIG_TRICK,
			ids(picker.pick(NONE, Arrays.asList(TrickEvent.trick(Trick.TRE_FLIP)), false, 0f, null, DT))[0]);
	}

	@Test
	public void landingVolumeFollowsLandingSpeed()
	{
		SoundPicker picker = new SoundPicker();
		List<SkateEvent> land = Arrays.asList(SkateEvent.LAND);
		assertEquals(0.3f, picker.pick(land, NO_TRICKS, false, 100f, null, DT).get(0).volume, 1e-5f);
		// 820 / 1400 = 0.586
		assertEquals(820f / 1400f, picker.pick(land, NO_TRICKS, false, 820f, null, DT).get(0).volume, 1e-5f);
		assertEquals(1f, picker.pick(land, NO_TRICKS, false, 3000f, null, DT).get(0).volume, 1e-5f);
	}

	@Test
	public void grindLocksWithATonkThenTinksEveryQuarterSecond()
	{
		SoundPicker picker = new SoundPicker();
		List<SoundPicker.Cue> lock = picker.pick(NONE, Arrays.asList(TrickEvent.holdStart(Trick.FIFTY_FIFTY)), true,
			0f, null, DT);
		assertEquals(1, lock.size());
		assertEquals(SoundPicker.GRIND_LOCK, lock.get(0).id);
		int tinks = 0;
		// just over a second more on the rail (float steps of 0.02 s): 0.25 s apart, so 4 tinks
		for (int i = 0; i < 52; i++)
		{
			tinks += picker.pick(NONE, NO_TRICKS, true, 0f, null, DT).size();
		}
		assertEquals(4, tinks);
		// off the rail: silence, and the beat restarts on the next rail
		assertEquals(0, picker.pick(NONE, NO_TRICKS, false, 0f, null, DT).size());
		assertEquals(0, picker.pick(NONE, NO_TRICKS, true, 0f, null, DT).size());
	}

	@Test
	public void manualHoldsDoNotTonk()
	{
		SoundPicker picker = new SoundPicker();
		assertEquals(0, picker.pick(NONE, Arrays.asList(TrickEvent.holdStart(Trick.MANUAL)), false, 0f, null, DT).size());
	}

	@Test
	public void landedCombosChimeBiggerFromFifteenThousand()
	{
		SoundPicker picker = new SoundPicker();
		assertEquals(SoundPicker.COMBO_LANDED,
			ids(picker.pick(NONE, NO_TRICKS, false, 0f, ComboScorer.Result.landed(14_999), DT))[0]);
		assertEquals(SoundPicker.BIG_COMBO_LANDED,
			ids(picker.pick(NONE, NO_TRICKS, false, 0f, ComboScorer.Result.landed(15_000), DT))[0]);
		assertEquals(0, picker.pick(NONE, NO_TRICKS, false, 0f, ComboScorer.Result.bailed(20_000), DT).size());
	}

	@Test
	public void throttleAllowsEachSoundOncePerTenthOfASecond()
	{
		SoundThrottle throttle = new SoundThrottle();
		assertTrue(throttle.allow(1, 0f));
		assertFalse(throttle.allow(1, 0.05f));
		assertTrue("other sounds are separate", throttle.allow(2, 0.05f));
		assertTrue(throttle.allow(1, 0.1f));
	}

	@Test
	public void repeaterFiresAtMostOnceAFrame()
	{
		Repeater r = new Repeater(0.15f, true);
		assertTrue("fires on contact", r.update(true, 0.02f));
		assertTrue("a huge frame fires once", r.update(true, 1f));
		// owes at most one more beat after the long frame
		assertTrue(r.update(true, 0.001f));
		assertFalse(r.update(true, 0.001f));
		assertFalse(r.update(false, 0.02f));
		assertTrue("restarts", r.update(true, 0.02f));
	}
}
