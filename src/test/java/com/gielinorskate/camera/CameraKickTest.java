package com.gielinorskate.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

/** The landing camera kick: a dip scaled by landing speed, and a short jolt after big airs. */
public class CameraKickTest
{
	private static final float DT = 1f / 50f;

	@Test
	public void dipScalesFromEightToThirtyWithLandingSpeed()
	{
		assertEquals(8f, CameraRig.landingDip(0f), 1e-4f);
		assertEquals(8f, CameraRig.landingDip(500f), 1e-4f);
		// a flat ollie: 8 + 22 * 320 / 1200
		assertEquals(13.87f, CameraRig.landingDip(820f), 0.01f);
		assertEquals(19f, CameraRig.landingDip(1100f), 1e-4f);
		assertEquals(30f, CameraRig.landingDip(1700f), 1e-4f);
		assertEquals(30f, CameraRig.landingDip(5000f), 1e-4f);
	}

	/** Settles on the ground, falls at {@code fallSpeed} for five frames, lands; returns the dip seen. */
	private static float dipAfterFall(float fallSpeed, float airtime)
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		for (int i = 0; i < 50; i++)
		{
			rig.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0f, DT);
		}
		float settled = rig.getFocusHeight();
		// a short stretch of falling, ending at the ground
		float h = fallSpeed * DT * 5;
		for (int i = 0; i < 5; i++)
		{
			h -= fallSpeed * DT;
			rig.update(0, 0, h, 0f, 0, SkaterState.AIRBORNE, 0f, DT);
		}
		float before = rig.getFocusHeight();
		rig.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, airtime, DT);
		// the focus height without the dip would have kept settling toward 0 from 'before'
		float noDip = before + (settled - before) * (1f - (float) Math.exp(-12f * DT));
		return noDip - rig.getFocusHeight();
	}

	@Test
	public void harderLandingsDipFurther()
	{
		float soft = dipAfterFall(600f, 0.5f);
		float hard = dipAfterFall(1600f, 0.5f);
		assertTrue("soft " + soft + " hard " + hard, hard > soft + 10f);
		// the dip decays at rate 6 within the landing frame: 1 - (1 - e^(-0.12)) = e^(-0.12) of it remains
		float decay = (float) Math.exp(-6f * DT);
		assertEquals(CameraRig.landingDip(1600f) * decay, hard, 0.6f);
	}

	@Test
	public void unseenFallsGuessTheSpeedFromAirtime()
	{
		// no airborne updates seen: 0.9 s of flat-ground air lands at 2000 * 0.9 / 2 = 900 u/s
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		float settled = rig.getFocusHeight();
		rig.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0.9f, DT);
		float decay = (float) Math.exp(-6f * DT);
		assertEquals(CameraRig.landingDip(900f) * decay, settled - rig.getFocusHeight(), 0.01f);
	}

	@Test
	public void onlyBigAirsJolt()
	{
		CameraRig small = new CameraRig();
		small.reset(0, 0, 0, 0f);
		small.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0.9f, DT);
		for (int i = 0; i < 10; i++)
		{
			small.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0f, DT);
			assertEquals(0f, small.getFocusX(), 1e-6f);
		}

		CameraRig big = new CameraRig();
		big.reset(0, 0, 0, 0f);
		big.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 1.2f, DT);
		float maxSide = 0f;
		for (int i = 0; i < 10; i++)
		{
			big.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0f, DT);
			maxSide = Math.max(maxSide, Math.abs(big.getFocusX()));
		}
		assertTrue("jolts sideways " + maxSide, maxSide > 0.3f && maxSide <= CameraRig.JITTER_AMPLITUDE);
	}

	@Test
	public void joltDiesOutWithinHalfASecond()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		rig.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 1.5f, DT);
		for (int i = 0; i < 25; i++)
		{
			rig.update(0, 0, 0, 0f, 0, SkaterState.ROLLING, 0f, DT);
		}
		assertEquals(0f, rig.getFocusX(), 0f);
		assertEquals(0f, rig.getFocusY(), 0f);
	}

	@Test
	public void sidewaysJoltIsAcrossTheView()
	{
		// looking east (yaw PI/2), across the view is north-south: x stays put
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, (float) Math.PI / 2);
		rig.update(0, 0, 0, (float) Math.PI / 2, 0, SkaterState.ROLLING, 1.5f, DT);
		float maxY = 0f;
		for (int i = 0; i < 10; i++)
		{
			rig.update(0, 0, 0, (float) Math.PI / 2, 0, SkaterState.ROLLING, 0f, DT);
			assertEquals(0f, rig.getFocusX(), 1e-4f);
			maxY = Math.max(maxY, Math.abs(rig.getFocusY()));
		}
		assertTrue(maxY > 0.3f);
	}
}
