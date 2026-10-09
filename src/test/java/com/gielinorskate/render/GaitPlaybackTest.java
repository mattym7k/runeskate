package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import com.gielinorskate.physics.FootPhysics;
import org.junit.Test;

public class GaitPlaybackTest
{
	private static final int[] LENGTHS = {4, 4, 4, 4};

	@Test
	public void rateMatchesTheWalkerSpeedToTheGamesGaitSpeed()
	{
		assertEquals(1.5f, GaitPlayback.rate(FootBody.Gait.WALK, FootPhysics.WALK_SPEED), 1e-4f);
		assertEquals(2f, GaitPlayback.rate(FootBody.Gait.RUN, FootPhysics.SPRINT_SPEED), 1e-4f);
		assertEquals(1f, GaitPlayback.rate(FootBody.Gait.IDLE, FootPhysics.SPRINT_SPEED), 0f);
	}

	@Test
	public void rateIsNeverSlowerThanTheGameOrWildlyFaster()
	{
		assertEquals(1f, GaitPlayback.rate(FootBody.Gait.WALK, 50f), 0f);
		assertEquals(Tuning.MAX_RATE, GaitPlayback.rate(FootBody.Gait.RUN, 100000f), 0f);
		assertEquals(1f, GaitPlayback.rate(FootBody.Gait.WALK, Float.NaN), 0f);
	}

	@Test
	public void atTheGamesRateNothingIsAdded()
	{
		GaitPlayback g = new GaitPlayback();
		for (int i = 0; i < 100; i++)
		{
			assertEquals(-1, g.advance(0, LENGTHS, 1f, 0.02f));
		}
	}

	@Test
	public void doubleRateAddsOneExtraTickPerTick()
	{
		GaitPlayback g = new GaitPlayback();
		// 1 s at 2x is 50 extra client ticks: 12 whole 4-tick frames (48 ticks), 2 left over
		int frame = 0;
		int skips = 0;
		for (int i = 0; i < 50; i++)
		{
			int f = g.advance(frame, LENGTHS, 2f, 0.02f);
			if (f >= 0)
			{
				skips += Math.floorMod(f - frame, LENGTHS.length);
				frame = f;
			}
		}
		assertEquals(12, skips);
		assertEquals(0, frame);
	}

	@Test
	public void framesWrapRoundTheCycle()
	{
		GaitPlayback g = new GaitPlayback();
		// 0.12 s at 3x: 12 extra ticks, 3 frames on from frame 3 -> frame 2
		assertEquals(2, g.advance(3, LENGTHS, 3f, 0.12f));
	}

	@Test
	public void aLongHitchSkipsAtMostOneCycleAndOwesNoMore()
	{
		GaitPlayback g = new GaitPlayback();
		assertEquals(0, g.advance(0, LENGTHS, 3f, 10f));
		// nothing piled up from the hitch: one more tick is not a frame
		assertEquals(-1, g.advance(0, LENGTHS, 2f, 0.02f));
	}

	@Test
	public void unusableAnimationsAreLeftAlone()
	{
		GaitPlayback g = new GaitPlayback();
		assertEquals(-1, g.advance(0, null, 2f, 1f));
		assertEquals(-1, g.advance(0, new int[0], 2f, 1f));
		assertEquals(-1, g.advance(7, LENGTHS, 2f, 1f));
		assertEquals(-1, g.advance(-1, LENGTHS, 2f, 1f));
		// zero-length frames count as one tick, so the loop always ends
		assertEquals(1, g.advance(0, new int[]{0, 0}, 2f, 0.02f));
	}

	@Test
	public void resetDropsTheCarriedOverTime()
	{
		GaitPlayback g = new GaitPlayback();
		assertEquals(-1, g.advance(0, LENGTHS, 2f, 0.06f));
		g.reset();
		// 3 + 2 ticks would make a whole frame without the reset
		assertEquals(-1, g.advance(0, LENGTHS, 2f, 0.04f));
	}
}
