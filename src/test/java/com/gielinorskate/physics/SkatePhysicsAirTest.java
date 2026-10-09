package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import java.util.List;
import org.junit.Test;

public class SkatePhysicsAirTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	/** Crouches for `crouchSeconds`, pops, then steps until landed or bailed. Returns peak height. */
	static float ollie(SkatePhysics p, float crouchSeconds, float steer, float steerSeconds)
	{
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < Math.round(crouchSeconds / DT); i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		float peak = p.getH();
		int steerSteps = Math.round(steerSeconds / DT);
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			in.steer = i < steerSteps ? steer : 0f;
			p.step(DT, in);
			peak = Math.max(peak, p.getH());
		}
		return peak;
	}

	@Test
	public void fullyChargedOllieReachesExpectedHeightAndLands()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		float peak = ollie(p, 0.4f, 0f, 0f);
		// v^2 / 2g = 820^2 / 4000 = 168.1 (ollieImpulse 700 -> 820; was 122.5)
		assertTrue("peak " + peak, peak > 155f && peak < 170f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getSpeed() > 400f);
		List<SkateEvent> ev = p.drainEvents();
		assertTrue(ev.contains(SkateEvent.POP));
		assertTrue(ev.contains(SkateEvent.LAND));
	}

	@Test
	public void uncrouchedOllieIsSmaller()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		float peak = ollie(p, 0f, 0f, 0f);
		// (0.92 * 820)^2 / 4000 = 754.4^2 / 4000 = 142.3 analytic; 20 ms steps reach 0.02 * (18 * 754.4 - 40 * 171)
		// = 134.8 (minPopFraction 0.85 -> 0.92; was 121.5)
		assertTrue("peak " + peak, peak > 130f && peak < 143f);
	}

	@Test
	public void ollieClearsLowWall()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(640, 80), 0, 0, 0);
		p.setRollingSpeed(600);
		SkateInput idle = new SkateInput();
		// pop lands around y=490 after the 0.4 s crouch, leaving ~150 units of climb before the wall
		while (p.getY() < 250)
		{
			p.step(DT, idle);
		}
		ollie(p, 0.4f, 0f, 0f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("y " + p.getY(), p.getY() > 640f);
	}

	@Test
	public void quarterSpinLandingBails()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		ollie(p, 0.4f, 1f, 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
		assertEquals(BailReason.SIDEWAYS, p.getLastBailReason());
	}

	@Test
	public void halfSpinLandsFakie()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		// 7 rad/s for 0.44 s = 3.08 rad (176.5 deg), within landingToleranceDeg (55, was 35) of 180
		ollie(p, 0.4f, 1f, 0.44f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getSpeed() < -400f);
		assertTrue(Angles.absDiff(p.getTravelHeading(), 0f) < 0.1f);
	}

	@Test
	public void fiftyDegreeOffAxisLandingNowLands()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		// Full charge -> vh = ollieImpulse = 820, continuous hang time T = 2*vh/gravity = 0.82 s.
		// Steer at 0.1520 (|steer| > 0.1, so spin assist never engages) for the whole flight:
		// rotation = steer * airSpinRate * T = 0.1520 * 7 * 0.82 = 0.8725 rad = 50 deg
		// (was 0.1781 for the old 0.7 s hang time).
		// 50 > old landingToleranceDeg (35) -> would have bailed; 50 <= new tolerance (55) -> lands,
		// and forward (50 < 90, so it lands nose-first rather than fakie).
		ollie(p, 0.4f, 0.1520f, 1.0f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getSpeed() > 0f);
	}

	@Test
	public void spinAssistClosesTheGapAndLandsFakie()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		// Uncharged pop -> charge = minPopFraction = 0.92, vh = 0.92 * 820 = 754.4, continuous hang time
		// T = 2*754.4/2000 = 0.754 s. Steer at full (1) for 0.3 s: rotation = 7*0.3 = 2.1 rad = 120.3 deg,
		// leaving 0.454 s of hang time (T - 0.3).
		// diff to travel+pi (180) = 180 - 120.3 = 59.7 deg, within the 70 deg spin-assist window
		// (diff to travel (0) is 120.3, further away, so 180 is the nearer target) and > tolerance
		// (55), so without spin assist this would bail. With |steer| < 0.1 for the remaining 0.454 s,
		// spin assist eases heading toward 180 at 6 rad/s: up to 6*0.454 = 2.72 rad, more than the
		// 59.7 deg (1.04 rad) gap, so it snaps fully to 180 -> lands fakie. (With the old 0.42 s hang
		// time this was only a partial ease, to 161.6 deg.)
		ollie(p, 0f, 1f, 0.3f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getSpeed() < 0f);
		assertTrue(Angles.absDiff(p.getTravelHeading(), 0f) < 0.4f);
	}

	@Test
	public void rollingOffALedgeGoesAirborneThenLands()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.stepAtY(300, 100, 0), 0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput idle = new SkateInput();
		boolean wasAirborne = false;
		for (int i = 0; i < 100; i++)
		{
			p.step(DT, idle);
			wasAirborne |= p.getState() == SkaterState.AIRBORNE;
		}
		assertTrue(wasAirborne);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(0f, p.getH(), 0.01f);
		assertTrue(p.drainEvents().contains(SkateEvent.ROLL_OFF));
	}
}
