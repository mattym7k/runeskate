package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ArmLocatorTest
{
	@Test
	public void findsTheRightHandAtTheEndOfTheRightArm()
	{
		Humanoid b = new Humanoid();
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(b.xs, b.ys, b.zs, b.n, -1f));
		// the hand: the bottom of the arm, outside the body on the -x side
		assertEquals(Humanoid.HAND_Y, a.hand[1], 4f);
		assertTrue("hand x " + a.hand[0], a.hand[0] < -Humanoid.ARM_INNER + 1f && a.hand[0] > -Humanoid.ARM_OUTER - 1f);
		assertEquals(0f, a.hand[2], 1f);
		// the shoulder: inside the arm at 3/4 of the height
		assertEquals(-150f, a.shoulder[1], 0.5f);
		assertTrue(a.shoulder[0] < -10f && a.shoulder[0] > -Humanoid.ARM_OUTER);
	}

	@Test
	public void findsTheLeftHandOnThePlusXSide()
	{
		Humanoid b = new Humanoid();
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(b.xs, b.ys, b.zs, b.n, 1f));
		assertTrue(a.hand[0] > Humanoid.ARM_INNER - 1f);
		assertEquals(Humanoid.HAND_Y, a.hand[1], 4f);
	}

	@Test
	public void theArmIsWeightedInAndTheHeadTorsoAndLegsOut()
	{
		Humanoid b = new Humanoid();
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(b.xs, b.ys, b.zs, b.n, -1f));
		for (int i = 0; i < b.n; i++)
		{
			float w = a.weight(b.xs[i], b.ys[i]);
			assertTrue(w >= 0f && w <= 1f);
			if (b.isArm(i, -1f) && b.ys[i] > -146f)
			{
				assertTrue("arm vertex " + i + " weight " + w, w > 0.75f);
			}
			else if (!b.isArm(i, -1f))
			{
				assertTrue("body vertex " + i + " (" + b.xs[i] + ", " + b.ys[i] + ") weight " + w, w < 0.1f);
			}
		}
	}

	@Test
	public void aHandSwungForwardIsFollowed()
	{
		Humanoid b = new Humanoid();
		// swing the right arm forward (-z) about the shoulder by 0.5 rad
		float c = (float) Math.cos(0.5);
		float s = (float) Math.sin(0.5);
		for (int i = 0; i < b.n; i++)
		{
			if (b.isArm(i, -1f))
			{
				float dy = b.ys[i] + 150f;
				float dz = b.zs[i];
				b.ys[i] = -150f + dy * c;
				b.zs[i] = dz - dy * s;
			}
		}
		ArmLocator a = new ArmLocator();
		assertTrue(a.locate(b.xs, b.ys, b.zs, b.n, -1f));
		// the hand went forward and up
		assertEquals(-64f * s, a.hand[2], 4f);
		assertEquals(-150f + 64f * c, a.hand[1], 4f);
	}

	@Test
	public void notABodyIsNotFound()
	{
		ArmLocator a = new ArmLocator();
		assertFalse(a.locate(new float[]{0f, 1f}, new float[]{0f, -10f}, new float[]{0f, 0f}, 2, -1f));
		assertFalse(a.locate(new float[0], new float[0], new float[0], 0, -1f));
		assertFalse(a.locate(null, null, null, 3, -1f));
		float[] nan = {Float.NaN, Float.NaN};
		assertFalse(a.locate(nan, nan, nan, 2, 1f));
	}
}
