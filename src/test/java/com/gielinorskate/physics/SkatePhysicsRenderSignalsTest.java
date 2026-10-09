package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import org.junit.Test;

/** The read-only signals the renderer's body animation reads: pop charge, vertical speed, landing speed. */
public class SkatePhysicsRenderSignalsTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	@Test
	public void popChargeRisesWithCrouchAndCapsAtOne()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		assertEquals(0f, p.getPopCharge(), 0f);
		in.crouch = true;
		// 5 steps of 0.02 s = 0.1 s of crouchChargeTime 0.2 s -> half charge
		for (int i = 0; i < 5; i++)
		{
			p.step(DT, in);
		}
		assertEquals(0.5f, p.getPopCharge(), 1e-3f);
		for (int i = 0; i < 20; i++)
		{
			p.step(DT, in);
		}
		assertEquals(1f, p.getPopCharge(), 0f);
		// released: decays at twice the charge rate (0.2 s back to empty from 0.5 s held is clamped at 1 first)
		in.crouch = false;
		for (int i = 0; i < 40; i++)
		{
			p.step(DT, in);
		}
		assertEquals(0f, p.getPopCharge(), 0f);
	}

	@Test
	public void verticalVelocityAndLandingSpeedOfAnOllie()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(0f, p.getPopCharge(), 0f);
		int guard = 0;
		while (p.getState() == SkaterState.AIRBORNE && guard++ < 200)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(0f, p.getVerticalVelocity(), 0f);
		// lands at about its take-off speed: 0.92 * 820 = 754, within a step of gravity (2000 * 0.02 = 40)
		float landing = p.getLastLandingSpeed();
		assertTrue("landing " + landing, landing > 700f && landing < 800f);
	}
}
