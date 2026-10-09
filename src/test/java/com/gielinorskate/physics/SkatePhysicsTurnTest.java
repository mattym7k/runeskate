package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import org.junit.Test;

/** Shift turns, standstill pivots, air steering held through the pop and landing blends. */
public class SkatePhysicsTurnTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	/** Result of a hold-A turn until the board has turned 180 degrees. */
	private static final class Turn
	{
		float seconds;
		float radius;
		float exitSpeed;
	}

	private Turn turnAround(float speed, boolean shift)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(speed);
		SkateInput in = new SkateInput();
		in.steer = -1f;
		in.powerslide = shift;
		float turned = 0f;
		float maxSide = 0f;
		int steps = 0;
		while (turned < Angles.PI && steps < 500)
		{
			float before = p.getHeading();
			p.step(DT, in);
			turned += Angles.absDiff(before, p.getHeading());
			maxSide = Math.max(maxSide, Math.abs(p.getX()));
			steps++;
		}
		Turn r = new Turn();
		r.seconds = steps * DT;
		// turning left from north: after 180 degrees the skater is one diameter west of the start
		r.radius = maxSide / 2f;
		r.exitSpeed = p.getSpeed();
		return r;
	}

	@Test
	public void shiftWithoutSteerStillBrakes()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(800);
		SkateInput in = new SkateInput();
		in.powerslide = true;
		SkatePhysicsRollingTest.run(p, in, 0.5f);
		// 900 u/s^2 brake plus friction and drag: under 400 after 0.5 s
		assertTrue(p.getSpeed() < 400f);
		assertEquals(0f, p.getHeading(), 0f);
	}

	@Test
	public void shiftThatIsNotYetABrakeDoesNotBrake()
	{
		// the input layer blocks the brake while Shift is a trick modifier (W held, a flick wind-up, or not yet
		// 0.25 s alone): only friction and drag, (45 + 0.00006 * 800^2) * 0.5 = 41.7, so still over 750
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(800);
		SkateInput in = new SkateInput();
		in.powerslide = true;
		in.brakeBlocked = true;
		SkatePhysicsRollingTest.run(p, in, 0.5f);
		assertTrue("speed " + p.getSpeed(), p.getSpeed() > 750f);
	}

	@Test
	public void shiftPlusSteerIsATightCarveFrom900()
	{
		// carve 2.6 / (1 + 900/2200) = 1.85 rad/s, x2.2 = 4.06 rad/s and rising as the speed bleeds at
		// (200 + 0.3 v) u/s^2 (+ friction and drag): 180 deg in ~0.75 s, radius ~200, exit ~500.
		// A normal carve at 900 has a radius of 900 / 1.85 = 487.
		Turn tight = turnAround(900f, true);
		Turn normal = turnAround(900f, false);
		assertTrue("seconds " + tight.seconds, tight.seconds >= 0.6f && tight.seconds <= 0.9f);
		assertTrue("radius " + tight.radius, tight.radius > 150f && tight.radius < 300f);
		assertTrue("exit " + tight.exitSpeed, tight.exitSpeed > 400f && tight.exitSpeed < 650f);
		assertTrue("normal radius " + normal.radius, normal.radius > 400f);
	}

	@Test
	public void shiftPlusSteerIsATightCarveFrom1500()
	{
		// carve 2.6 / (1 + 1500/2200) = 1.55 rad/s, x2.2 = 3.4: 180 deg in ~0.95 s, exit ~800
		Turn tight = turnAround(1500f, true);
		assertTrue("seconds " + tight.seconds, tight.seconds >= 0.8f && tight.seconds <= 1.1f);
		assertTrue("exit " + tight.exitSpeed, tight.exitSpeed > 650f && tight.exitSpeed < 1000f);
	}

	@Test
	public void standstillPivotTurnsAroundInAboutSevenTenths()
	{
		// below 250 u/s the carve blends toward a 4.5 rad/s pivot: from rest pi / 4.5 = 0.70 s
		Turn pivot = turnAround(0f, false);
		assertEquals(0.70f, pivot.seconds, 0.03f);
	}

	@Test
	public void carveFalloffIsGentlerAtSpeed()
	{
		// carveSpeedFalloff 1600 -> 2200: at 1100, 2.6 / (1 + 1100/2200) = 1.733 rad/s
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(1100f);
		SkateInput in = new SkateInput();
		in.steer = 1f;
		p.step(DT, in);
		assertEquals(1.7333f * DT, p.getHeading(), 1e-4f);
	}

	@Test
	public void ollieWhileHoldingSteerLands()
	{
		// carving with A held into the pop used to spin 7 rad/s through an uncharged ollie (0.7 s, 280 deg)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(900f);
		SkateInput in = new SkateInput();
		in.steer = -1f;
		SkatePhysicsRollingTest.run(p, in, 0.3f);
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void steerReleasedAndPressedAgainInTheAirSpins()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500f);
		SkateInput in = new SkateInput();
		in.steer = 1f;
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in); // the pop step still carves on the ground first
		float atPop = p.getHeading();
		p.step(DT, in);
		assertEquals("held from before the pop: ignored", atPop, p.getHeading(), 1e-6f);
		in.steer = 0f;
		p.step(DT, in);
		in.steer = 1f;
		p.step(DT, in);
		// re-pressed: 7 rad/s for one step
		assertEquals(atPop + t.airSpinRate * DT, p.getHeading(), 1e-4f);
	}

	@Test
	public void landingBlendsTheTravelOntoTheBoardInsteadOfSnapping()
	{
		// as SkatePhysicsAirTest.fiftyDegreeOffAxisLandingNowLands: a 0.152 steer held through a charged
		// ollie lands ~50 deg off the travel (north). The travel then turns onto the board over ~0.15 s.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500f);
		SkateInput in = new SkateInput();
		in.crouch = true;
		SkatePhysicsRollingTest.run(p, in, 0.4f);
		in.crouch = false;
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		in.steer = 0.152f;
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("landed off-axis " + p.getHeading(), p.getHeading() > 0.7f);
		assertEquals("still travelling north", 0f, p.getTravelHeading(), 0.05f);
		in.steer = 0f;
		p.step(DT, in);
		float oneStep = p.getTravelHeading();
		assertTrue("blending " + oneStep, oneStep > 0.05f && oneStep < p.getHeading() - 0.3f);
		SkatePhysicsRollingTest.run(p, in, 0.16f);
		assertEquals(p.getHeading(), p.getTravelHeading(), 1e-5f);
	}
}
