package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class TurnRateMeterTest
{
	@Test
	public void steadyCarveIsMeasuredEvenWithUnevenPhysicsSteps()
	{
		TurnRateMeter m = new TurnRateMeter();
		float heading = 0f;
		float rate = 0f;
		// 1.5 rad/s; render frames run 0, 1, 2 or 3 physics steps of 0.02 s and pass the physics time
		for (int i = 0; i < 100; i++)
		{
			int steps = i % 4;
			heading = Angles.wrap(heading + 1.5f * 0.02f * steps);
			rate = m.update(heading, null, 0f, 0.02f * steps);
		}
		assertEquals(1.5f, rate, 0.05f);
	}

	@Test
	public void wrapsAroundThroughPi()
	{
		TurnRateMeter m = new TurnRateMeter();
		float heading = Angles.PI - 0.2f;
		float rate = 0f;
		for (int i = 0; i < 30; i++)
		{
			heading = Angles.wrap(heading - 2f / 60f);
			rate = m.update(heading, null, 0f, 1f / 60f);
		}
		assertEquals(-2f, rate, 0.01f);
	}

	@Test
	public void aHeadingSnapIsNotATurn()
	{
		TurnRateMeter m = new TurnRateMeter();
		float rate = 0f;
		for (int i = 0; i < 30; i++)
		{
			rate = m.update(i == 15 ? 2f : 0f + (i > 15 ? 2f : 0f), null, 0f, 1f / 60f);
		}
		assertEquals(0f, rate, 1e-4f);
	}

	@Test
	public void aBigspinsOwnBodyTurnIsLeftOut()
	{
		// the receiver animates the bigspin's body 180 from the trick table: the rate is only the steering
		TurnRateMeter m = new TurnRateMeter();
		float rate = 0f;
		for (int i = 0; i <= 27; i++)
		{
			float progress = Math.min(1f, i / 60f / Trick.BIGSPIN.duration);
			float heading = Angles.wrap(GhostCodec.bodySpin(Trick.BIGSPIN, progress) + 0.5f * i / 60f);
			rate = m.update(heading, Trick.BIGSPIN, progress, 1f / 60f);
		}
		assertEquals(0.5f, rate, 0.02f);
		// the flip clears: its body turn is kept in the heading, not unwound
		rate = m.update(Angles.wrap(Angles.PI + 0.5f * 28 / 60f), null, 0f, 1f / 60f);
		for (int i = 29; i < 40; i++)
		{
			rate = m.update(Angles.wrap(Angles.PI + 0.5f * i / 60f), null, 0f, 1f / 60f);
		}
		assertEquals(0.5f, rate, 0.02f);
	}
}
