package com.gielinorskate.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

public class CameraRigTest
{
	private static final float DT = 1f / 50f;

	private static void run(CameraRig rig, float seconds, float x, float y, float h, float travel, float speed,
		SkaterState state)
	{
		for (int i = 0; i < Math.round(seconds / DT); i++)
		{
			rig.update(x, y, h, travel, speed, state, 0f, DT);
		}
	}

	@Test
	public void yawUsesFourteenBitCounterClockwiseUnits()
	{
		// RuneLite camera yaw is JAU14 (16384 per turn); 0 looks north and yaw grows counter-clockwise
		assertEquals(0, CameraRig.yawJau14(0f));
		assertEquals(12288, CameraRig.yawJau14(Angles.PI / 2)); // east
		assertEquals(8192, CameraRig.yawJau14(Angles.PI));      // south
		assertEquals(4096, CameraRig.yawJau14(-Angles.PI / 2)); // west
	}

	@Test
	public void swingsBehindTheDirectionOfTravelWithLag()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		rig.update(0, 0, 0, Angles.PI / 2, 800, SkaterState.ROLLING, 0f, DT);
		float afterOneFrame = rig.getYaw();
		assertTrue("lags behind", afterOneFrame > 0f && afterOneFrame < 0.3f);
		run(rig, 2f, 0, 0, 0, Angles.PI / 2, 800, SkaterState.ROLLING);
		assertEquals(Angles.PI / 2, rig.getYaw(), 0.01f);
	}

	@Test
	public void followsTheBoardTurningInPlace()
	{
		// the heading passed in is now the board's camera heading (not the noisy roll direction), so the
		// camera follows a standstill pivot instead of freezing below 60 u/s
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 2f, 0, 0, 0, Angles.PI / 2, 0, SkaterState.ROLLING);
		assertEquals(Angles.PI / 2, rig.getYaw(), 0.01f);
	}

	@Test
	public void followsFasterWhenFarBehind()
	{
		// error > 60 deg: rate 4 (else 3). One 0.02 s frame toward 90 deg: 90 * (1 - e^(-0.08)) = 6.92 deg
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		rig.update(0, 0, 0, Angles.PI / 2, 800, SkaterState.ROLLING, 0f, DT);
		assertEquals(Angles.PI / 2 * (1f - (float) Math.exp(-4f * DT)), rig.getYaw(), 1e-4f);
		// within 60 deg: rate 3. One frame toward 30 deg: 30 * (1 - e^(-0.06))
		CameraRig near = new CameraRig();
		near.reset(0, 0, 0, 0f);
		float target = (float) Math.toRadians(30);
		near.update(0, 0, 0, target, 800, SkaterState.ROLLING, 0f, DT);
		assertEquals(target * (1f - (float) Math.exp(-3f * DT)), near.getYaw(), 1e-4f);
	}

	@Test
	public void shortHopsDoNotDipTheCamera()
	{
		// only landings after more than 0.25 s in the air dip (bumps and tiny hops do not)
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 1f, 0, 0, 0, 0f, 500, SkaterState.ROLLING);
		float settled = rig.getFocusHeight();
		rig.update(0, 0, 0, 0f, 500, SkaterState.ROLLING, 0.2f, DT);
		assertEquals(settled, rig.getFocusHeight(), 0.01f);
	}

	@Test
	public void holdsYawWhileBailed()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 1f, 0, 0, 0, Angles.PI / 2, 900, SkaterState.BAILED);
		assertEquals(0f, rig.getYaw(), 1e-4f);
	}

	@Test
	public void turnsSlowerInTheAir()
	{
		CameraRig ground = new CameraRig();
		CameraRig air = new CameraRig();
		ground.reset(0, 0, 0, 0f);
		air.reset(0, 0, 0, 0f);
		run(ground, 0.3f, 0, 0, 0, 1f, 800, SkaterState.ROLLING);
		run(air, 0.3f, 0, 0, 0, 1f, 800, SkaterState.AIRBORNE);
		assertTrue(air.getYaw() < ground.getYaw());
	}

	@Test
	public void focusLeadsTheSkaterAtSpeed()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 2f, 0, 1000, 0, 0f, 1000, SkaterState.ROLLING);
		assertTrue("ahead of the skater", rig.getFocusY() > 1000f + 50f);
		assertTrue("capped", rig.getFocusY() <= 1000f + CameraRig.MAX_LOOK_AHEAD + 0.01f);
		assertEquals(0f, rig.getFocusX(), 0.5f);
	}

	@Test
	public void cameraRisesLessThanTheSkaterInTheAir()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 0.3f, 0, 0, 120, 0f, 500, SkaterState.AIRBORNE);
		float rise = rig.getFocusHeight() - CameraRig.FOCUS_HEIGHT;
		assertTrue("rise " + rise, rise > 10f && rise < 100f);
	}

	@Test
	public void landingDipsThenRecovers()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 1f, 0, 0, 0, 0f, 500, SkaterState.ROLLING);
		float settled = rig.getFocusHeight();
		rig.update(0, 0, 0, 0f, 500, SkaterState.ROLLING, 0.5f, DT);
		assertTrue(rig.getFocusHeight() < settled - 5f);
		run(rig, 1.5f, 0, 0, 0, 0f, 500, SkaterState.ROLLING);
		assertEquals(settled, rig.getFocusHeight(), 0.5f);
	}

	@Test
	public void pullsBackWithSpeed()
	{
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		run(rig, 0.1f, 0, 0, 0, 0f, 0, SkaterState.ROLLING);
		float slow = rig.getZoomFactor();
		run(rig, 0.1f, 0, 0, 0, 0f, 1500, SkaterState.ROLLING);
		assertEquals(1f, slow, 1e-4f);
		assertTrue(rig.getZoomFactor() < 0.9f);
	}

	@Test
	public void aHalfTurnSwingIsCappedAtOneTurnPerSecond()
	{
		// getting back on after a bail into a wall faces the skater away from it: a 179 degree swing. The
		// exponential follow alone started it at 4 * PI rad/s = 720 deg/s, a whip-around
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		float frame = 1f / 144f;
		float last = rig.getYaw();
		float maxRate = 0f;
		for (int i = 0; i < 288; i++)
		{
			rig.update(0, 0, 0, (float) Math.toRadians(179), 0, SkaterState.ROLLING, 0f, frame);
			maxRate = Math.max(maxRate, Angles.absDiff(rig.getYaw(), last) / frame);
			last = rig.getYaw();
		}
		assertTrue("max " + Math.toDegrees(maxRate) + " deg/s", maxRate <= 2 * Angles.PI + 1e-3f);
		assertEquals("still gets there", Math.toRadians(179), rig.getYaw(), 0.02f);
	}
}
