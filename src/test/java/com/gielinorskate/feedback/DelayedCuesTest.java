package com.gielinorskate.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;

public class DelayedCuesTest
{
	@Test
	public void levelUpRingsDingThenDongAThirdOfASecondLater()
	{
		DelayedCues cues = new DelayedCues();
		cues.levelUp(10f);
		List<SoundPicker.Cue> first = cues.due(10f);
		assertEquals(1, first.size());
		assertEquals(SoundEffectID.TOWN_CRIER_BELL_DING, first.get(0).id);
		assertTrue(cues.due(10.29f).isEmpty());
		List<SoundPicker.Cue> second = cues.due(10.3f);
		assertEquals(1, second.size());
		assertEquals(SoundEffectID.TOWN_CRIER_BELL_DONG, second.get(0).id);
		assertTrue(cues.due(20f).isEmpty());
	}

	@Test
	public void goalChimeWaitsForTheLandingChime()
	{
		DelayedCues cues = new DelayedCues();
		cues.goalComplete(5f);
		// at least the throttle gap after the landing's own chime
		assertTrue(cues.due(5f + SkateSounds.MIN_GAP_SECONDS).isEmpty());
		assertTrue(cues.due(5.2f).isEmpty());
		List<SoundPicker.Cue> due = cues.due(5.5f);
		assertEquals(SoundPicker.COMBO_LANDED, due.get(0).id);
	}

	@Test
	public void clearDropsEverything()
	{
		DelayedCues cues = new DelayedCues();
		cues.levelUp(0f);
		cues.clear();
		assertTrue(cues.due(5f).isEmpty());
	}

	@Test
	public void ninetyNineGetsTheBigGraphic()
	{
		assertEquals(SpotanimID.LEVELUP_ANIM, DelayedCues.levelUpGraphic(98));
		assertEquals(SpotanimID.LEVELUP_99_ANIM, DelayedCues.levelUpGraphic(99));
	}
}
