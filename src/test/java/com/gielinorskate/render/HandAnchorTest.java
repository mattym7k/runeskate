package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HandAnchorTest
{
	private static final float DT = 1f / 50f;

	private static HandAnchor active()
	{
		HandAnchor a = new HandAnchor();
		a.setActive(true);
		return a;
	}

	@Test
	public void withNoReadingItIsTheFixedAnchor()
	{
		HandAnchor a = active();
		a.step(DT);
		assertEquals(-CarryPose.HAND_SIDE, a.x(), 0f);
		assertEquals(-CarryPose.HAND_HEIGHT, a.y(), 0f);
		assertEquals(0f, a.z(), 0f);
	}

	@Test
	public void followsTheRightHandOfTheDrawnMesh()
	{
		Humanoid b = new Humanoid();
		HandAnchor a = active();
		a.sample(b.xs, b.ys, b.zs, b.n);
		a.step(DT);
		// the first reading is taken as is
		assertEquals(Humanoid.HAND_Y, a.y(), 3f);
		assertEquals(-(Humanoid.ARM_INNER + Humanoid.ARM_OUTER) / 2f, a.x(), 3.5f);
	}

	@Test
	public void aSwingingHandIsFollowedWithinAFewFrames()
	{
		Humanoid b = new Humanoid();
		HandAnchor a = active();
		a.sample(b.xs, b.ys, b.zs, b.n);
		a.step(DT);
		float z0 = a.z();
		// the hand swings 30 forward
		for (int i = 0; i < b.n; i++)
		{
			if (b.isArm(i, -1f) && b.ys[i] > -100f)
			{
				b.zs[i] -= 30f;
			}
		}
		for (int k = 0; k < 4; k++)
		{
			a.sample(b.xs, b.ys, b.zs, b.n);
			a.step(DT);
		}
		// 0.08 s against a 0.025 s easing: 96% of the way
		assertEquals(z0 - 30f, a.z(), 2f);
	}

	@Test
	public void aStaleOrBadReadingEasesBackToTheFixedAnchor()
	{
		Humanoid b = new Humanoid();
		HandAnchor a = active();
		a.sample(b.xs, b.ys, b.zs, b.n);
		for (int k = 0; k < 100; k++)
		{
			a.step(DT);
		}
		assertEquals(-CarryPose.HAND_HEIGHT, a.y(), 0.5f);
		a.sample(new float[]{0f}, new float[]{0f}, new float[]{0f}, 1);
		a.step(DT);
		assertEquals(-CarryPose.HAND_HEIGHT, a.y(), 0.5f);
	}

	@Test
	public void inactiveItReadsNothing()
	{
		Humanoid b = new Humanoid();
		HandAnchor a = new HandAnchor();
		a.sample(b.xs, b.ys, b.zs, b.n);
		a.setActive(true);
		a.step(DT);
		assertEquals(-CarryPose.HAND_HEIGHT, a.y(), 0f);
	}
}
