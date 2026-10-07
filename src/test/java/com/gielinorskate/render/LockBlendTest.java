package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

public class LockBlendTest
{
	private static RenderPose pose(float x, float y, float h, float heading, SkaterState state)
	{
		return new RenderPose(x, y, h, heading, heading, 0f, 0f, 0f, state, null);
	}

	@Test
	public void passesThroughWhenNothingLocks()
	{
		LockBlend b = new LockBlend();
		RenderPose p = pose(1f, 2f, 3f, 0f, SkaterState.AIRBORNE);
		assertSame(p, b.apply(p, 0.016f));
		RenderPose q = pose(5f, 2f, 3f, 0f, SkaterState.ROLLING);
		assertSame(q, b.apply(q, 0.016f));
	}

	@Test
	public void lockStartsFromTheLastDrawnPoseAndReachesTheRailIn90Ms()
	{
		assertEquals(0.09f, LockBlend.DURATION, 0f);
		LockBlend b = new LockBlend();
		b.apply(pose(0f, 0f, 100f, 0.4f, SkaterState.AIRBORNE), 0.016f);
		RenderPose rail = pose(30f, 0f, 60f, 0f, SkaterState.GRINDING);
		// the lock frame itself is still drawn where the skater was
		RenderPose first = b.apply(rail, 0.016f);
		assertEquals(0f, first.x, 1e-4f);
		assertEquals(100f, first.h, 1e-4f);
		assertEquals(0.4f, first.heading, 1e-4f);
		assertEquals(SkaterState.GRINDING, first.state);

		RenderPose mid = b.apply(rail, LockBlend.DURATION / 2f);
		assertTrue(mid.x > 0f && mid.x < 30f);
		assertTrue(mid.h < 100f && mid.h > 60f);

		RenderPose done = b.apply(rail, LockBlend.DURATION);
		assertEquals(30f, done.x, 1e-4f);
		assertEquals(60f, done.h, 1e-4f);
		assertEquals(0f, done.heading, 1e-4f);
		assertSame(rail, b.apply(rail, 0.016f));
	}

	@Test
	public void blendIsMonotonic()
	{
		LockBlend b = new LockBlend();
		b.apply(pose(0f, 0f, 0f, 0f, SkaterState.AIRBORNE), 0.016f);
		RenderPose rail = pose(40f, 0f, 0f, 0f, SkaterState.GRINDING);
		float last = b.apply(rail, 0f).x;
		for (int i = 0; i < 20; i++)
		{
			float x = b.apply(rail, 0.006f).x;
			assertTrue(x >= last - 1e-5f);
			last = x;
		}
		assertEquals(40f, last, 1e-4f);
	}

	@Test
	public void popOffBeforeTheBlendEndsKeepsEasing()
	{
		LockBlend b = new LockBlend();
		b.apply(pose(0f, 0f, 0f, 0f, SkaterState.AIRBORNE), 0.016f);
		b.apply(pose(40f, 0f, 0f, 0f, SkaterState.GRINDING), 0.03f);
		RenderPose air = pose(44f, 0f, 10f, 0f, SkaterState.AIRBORNE);
		RenderPose out = b.apply(air, 0.03f);
		assertTrue(out.x < 44f);
		assertSame(air, b.apply(air, LockBlend.DURATION));
	}

	@Test
	public void headingBlendsTheShortWay()
	{
		LockBlend b = new LockBlend();
		b.apply(pose(0f, 0f, 0f, 3.0f, SkaterState.AIRBORNE), 0.016f);
		b.apply(pose(0f, 0f, 0f, -3.0f, SkaterState.GRINDING), 0f);
		RenderPose mid = b.apply(pose(0f, 0f, 0f, -3.0f, SkaterState.GRINDING), LockBlend.DURATION / 2f);
		// from 3.0 to -3.0 the short way passes PI, not 0
		assertTrue(Math.abs(mid.heading) > 3.0f);
	}

	@Test
	public void resetForgetsThePreviousPose()
	{
		LockBlend b = new LockBlend();
		b.apply(pose(0f, 0f, 0f, 0f, SkaterState.AIRBORNE), 0.016f);
		b.reset();
		RenderPose rail = pose(40f, 0f, 0f, 0f, SkaterState.GRINDING);
		assertSame(rail, b.apply(rail, 0.016f));
	}
}
