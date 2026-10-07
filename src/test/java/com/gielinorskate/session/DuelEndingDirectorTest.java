package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.duel.DuelEnding;
import com.gielinorskate.input.FootControls;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.render.CelebrationSequence;
import com.gielinorskate.render.TantrumSequence;
import com.gielinorskate.session.DuelEndingDirector.Where;
import org.junit.Test;

public class DuelEndingDirectorTest
{
	@Test
	public void aTantrumWaitsForTheGetUpThenStarts()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.offer(DuelEnding.TANTRUM, 10f);
		// knocked out: lying and getting up
		assertEquals(DuelEnding.NONE, d.poll(11f, Where.KNOCKED_DOWN, true, false));
		assertEquals(DuelEnding.NONE, d.poll(15f, Where.KNOCKED_DOWN, true, false));
		assertEquals(DuelEnding.TANTRUM, d.poll(16f, Where.FOOT_GROUND, true, false));
		assertEquals(DuelEnding.NONE, d.pending());
	}

	@Test
	public void onTheBoardItStartsOnTheGroundNotInTheAir()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.offer(DuelEnding.TANTRUM, 0f);
		assertEquals(DuelEnding.NONE, d.poll(0.1f, Where.BOARD_BUSY, true, false));
		assertEquals(DuelEnding.NONE, d.poll(0.2f, Where.FOOT_AIR, true, false));
		assertEquals(DuelEnding.TANTRUM, d.poll(0.3f, Where.BOARD_GROUND, true, false));
		d.offer(DuelEnding.CELEBRATE, 1f);
		assertEquals(DuelEnding.CELEBRATE, d.poll(1f, Where.BOARD_GROUND, true, false));
	}

	@Test
	public void anEndingThatCannotStartInTimeIsDropped()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.offer(DuelEnding.CELEBRATE, 0f);
		assertEquals(DuelEnding.NONE, d.poll(DuelEndingDirector.CELEBRATE_WAIT + 0.1f, Where.BOARD_BUSY, true, false));
		assertEquals(DuelEnding.NONE, d.poll(DuelEndingDirector.CELEBRATE_WAIT + 0.2f, Where.BOARD_GROUND, true, false));
		d.offer(DuelEnding.TANTRUM, 0f);
		assertEquals(DuelEnding.NONE, d.poll(DuelEndingDirector.TANTRUM_WAIT + 0.1f, Where.KNOCKED_DOWN, true, false));
		assertEquals(DuelEnding.NONE, d.poll(DuelEndingDirector.TANTRUM_WAIT + 0.2f, Where.FOOT_GROUND, true, false));
	}

	@Test
	public void theSettingOffOrAPvpAreaDropsIt()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.offer(DuelEnding.TANTRUM, 0f);
		assertEquals(DuelEnding.NONE, d.poll(0f, Where.FOOT_GROUND, false, false));
		assertEquals(DuelEnding.NONE, d.pending());
		d.offer(DuelEnding.CELEBRATE, 0f);
		assertEquals(DuelEnding.NONE, d.poll(0f, Where.FOOT_GROUND, true, true));
		assertEquals(DuelEnding.NONE, d.poll(0.1f, Where.FOOT_GROUND, true, false));
		// nothing offered: nothing
		d.offer(DuelEnding.NONE, 0f);
		d.offer(null, 0f);
		assertEquals(DuelEnding.NONE, d.poll(0f, Where.FOOT_GROUND, true, false));
	}

	@Test
	public void theTantrumSnapsOnceAndEndsOnTime()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.startTantrum();
		assertTrue(d.inTantrum());
		int snaps = 0;
		float t = 0f;
		// uneven frames, one long one
		float[] frames = {0.016f, 0.033f, 0.1f, 0.007f};
		int i = 0;
		while (d.inTantrum() && t < 10f)
		{
			float dt = frames[i++ % frames.length];
			if (d.advanceTantrum(dt))
			{
				snaps++;
				assertTrue(t + dt >= TantrumSequence.SNAP_AT);
			}
			t += dt;
		}
		assertEquals(1, snaps);
		assertEquals(TantrumSequence.DURATION, t, 0.11f);
		assertFalse(d.advanceTantrum(1f));
		// a pending ending waits for it to finish
		d.startTantrum();
		d.offer(DuelEnding.CELEBRATE, 0f);
		assertEquals(DuelEnding.NONE, d.poll(0f, Where.FOOT_GROUND, true, false));
	}

	@Test
	public void theCelebrationEndsOnAnyInputOrOnTime()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.startCelebration();
		assertTrue(d.advanceCelebration(0.5f, false));
		assertFalse(d.advanceCelebration(0.016f, true));
		assertFalse(d.celebrating());
		d.startCelebration();
		float t = 0f;
		while (d.advanceCelebration(0.02f, false))
		{
			t += 0.02f;
		}
		assertEquals(CelebrationSequence.DURATION, t, 0.03f);
	}

	@Test
	public void anyControlCountsAsInput()
	{
		SkateInput in = new SkateInput();
		assertFalse(DuelEndingDirector.anyInput(in, false));
		assertTrue(DuelEndingDirector.anyInput(in, true));
		in.steer = -1f;
		assertTrue(DuelEndingDirector.anyInput(in, false));
		in.steer = 0f;
		in.pushPressed = true;
		assertTrue(DuelEndingDirector.anyInput(in, false));
		FootControls fc = new FootControls();
		assertFalse(DuelEndingDirector.anyInput(fc, false));
		fc.left = true;
		assertTrue(DuelEndingDirector.anyInput(fc, false));
	}

	@Test
	public void clearingStopsEverything()
	{
		DuelEndingDirector d = new DuelEndingDirector();
		d.startTantrum();
		d.offer(DuelEnding.CELEBRATE, 0f);
		d.clear();
		assertFalse(d.isPlaying());
		assertEquals(DuelEnding.NONE, d.pending());
	}
}
