package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.camera.CameraRig;
import com.gielinorskate.tricks.Gesture;
import org.junit.Test;

/** The heading the chase camera follows: the board, not the roll direction. */
public class SkatePhysicsCameraTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	@Test
	public void cameraHeadingIsTheBoardWhileRollingBackSlowly()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0.5f);
		p.setRollingSpeed(-300f);
		assertEquals(0.5f, p.getCameraHeading(), 1e-6f);
		// the travel direction is behind the board
		assertEquals(Angles.wrap(0.5f + Angles.PI), p.getTravelHeading(), 1e-5f);
	}

	@Test
	public void cameraHeadingFlipsOnlyForRealFakieSpeed()
	{
		// faster than 400 backwards counts as riding fakie: the camera turns to look along the travel
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0.5f);
		p.setRollingSpeed(-500f);
		assertEquals(Angles.wrap(0.5f + Angles.PI), p.getCameraHeading(), 1e-5f);
	}

	@Test
	public void cameraHeadingInTheAirIgnoresSpins()
	{
		// regular ollie heading north, spun in the air: the camera keeps looking along the flight
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setRollingSpeed(500f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		in.steer = 1f;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		assertTrue(p.getHeading() > 1f);
		assertEquals(0f, p.getCameraHeading(), 1e-5f);
	}

	@Test
	public void cameraHeadingInTheAirAfterASlowRollBackStaysOnTheBoard()
	{
		// popped while rolling back slowly: flight goes south, but the camera kept facing the board (north)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setRollingSpeed(-300f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(0f, p.getCameraHeading(), 1e-5f);
	}

	@Test
	public void lastAirtimeIsTheFlightLength()
	{
		// uncharged pop: vh 0.92 * 820 = 754.4, 2 * 754.4 / 2000 = 0.754 s in the air (was 0.85: 0.70 s)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setRollingSpeed(500f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(0.754f, p.getLastAirtime(), 0.03f);
	}

	@Test
	public void uphillCoastIntoRollbackKeepsTheCameraOnTheBoard()
	{
		// 30 per tile uphill from 1000: it stops after ~4 s and rolls back; the old travel heading flipped
		// by 180 deg the moment the speed went negative and the camera spun after it
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(-30f / 128f), 0, 0, 0f);
		p.setRollingSpeed(1000f);
		CameraRig rig = new CameraRig();
		rig.reset(0, 0, 0, 0f);
		SkateInput in = new SkateInput();
		boolean rolledBack = false;
		for (int i = 0; i < 1000 && p.getSpeed() > -390f; i++)
		{
			p.step(DT, in);
			rig.update(p.getX(), p.getY(), p.getH(), p.getCameraHeading(), p.getSpeed(), p.getState(), 0f, DT);
			rolledBack |= p.getSpeed() < -100f;
			float err = (float) Math.toDegrees(Angles.absDiff(rig.getYaw(), p.getHeading()));
			assertTrue("camera " + err + " deg off at step " + i, err < 20f);
		}
		assertTrue(rolledBack);
	}
}
