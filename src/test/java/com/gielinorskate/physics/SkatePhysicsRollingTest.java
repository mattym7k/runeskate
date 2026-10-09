package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkatePhysicsRollingTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	static void run(SkatePhysics p, SkateInput in, float seconds)
	{
		int steps = Math.round(seconds / DT);
		for (int i = 0; i < steps; i++)
		{
			p.step(DT, in);
		}
	}

	@Test
	public void pushAddsImpulseAndMovesNorth()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		// the first push from rest is pushFromRest (360, stronger than the 240 pushImpulse), then one
		// step of friction and drag: (45 + 0.00012 * 360^2) * 0.02 = 1.21
		assertEquals(t.pushFromRest - (t.rollingFriction + t.drag * 360f * 360f) * DT, p.getSpeed(), 0.05f);
		assertTrue(p.drainEvents().contains(SkateEvent.PUSH));
		run(p, in, 1f);
		assertTrue(p.getY() > 50f);
		assertEquals(0f, p.getX(), 0.01f);
	}

	@Test
	public void pushHasCooldown()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		in.pushPressed = true;
		p.step(DT, in);
		// one push only: under the 360 first push (a second one would add ~200 more)
		assertTrue(p.getSpeed() < t.pushFromRest);
	}

	@Test
	public void pushingIsCappedAtMaxPushSpeed()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 20; i++)
		{
			in.pushPressed = true;
			run(p, in, 0.5f);
		}
		assertTrue(p.getSpeed() <= t.maxPushSpeed);
		// Air drag (0.00012 * v^2) now limits flat push speed below maxPushSpeed: a push every 0.5 s adds
		// max(60, 240 * (1 - s/1500)^0.5) and 0.5 s of friction + drag takes 0.5 * (45 + 0.00012 * s^2);
		// they balance at ~1280 just after a push, ~1166 at the end of each 0.5 s (simulated), so 0.75.
		assertTrue(p.getSpeed() > t.maxPushSpeed * 0.75f);
	}

	@Test
	public void holdingPushKeepsPushing()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		run(p, in, 2.0f);
		assertTrue(p.getSpeed() > 3 * t.pushImpulse);
		assertTrue(p.getSpeed() <= t.maxPushSpeed);
		int pushCount = 0;
		for (SkateEvent e : p.drainEvents())
		{
			if (e == SkateEvent.PUSH)
			{
				pushCount++;
			}
		}
		assertTrue(pushCount >= 3);
	}

	@Test
	public void holdingPushRespectsCooldown()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		run(p, in, t.pushCooldown * 0.9f);
		int pushCount = 0;
		for (SkateEvent e : p.drainEvents())
		{
			if (e == SkateEvent.PUSH)
			{
				pushCount++;
			}
		}
		assertEquals(1, pushCount);
	}

	@Test
	public void crouchingBlocksHeldPush()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		in.crouch = true;
		run(p, in, 1f);
		assertEquals(0f, p.getSpeed(), 0f);
		assertTrue(p.drainEvents().isEmpty());
	}

	@Test
	public void steerRightCarvesClockwise()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		in.steer = 1f;
		run(p, in, 0.5f);
		assertTrue(p.getHeading() > 0.3f);
		assertTrue(p.getX() > 0f);
	}

	@Test
	public void powerslideWithSteerCarvesTightlyAndScrubsSpeed()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(800);
		SkateInput in = new SkateInput();
		in.powerslide = true;
		in.steer = 1f;
		// Shift + steer used to brake 900 u/s^2 and ignore the steer; it is now a tight carve (carve x2.2,
		// bleeding 200 + 0.3 v u/s^2), and Shift alone still brakes (SkatePhysicsTurnTest). Over 0.5 s from
		// 800 it bleeds ~(200 + 0.3 * 700 + 45 + 60) * 0.5 = 258 -> ~540, turning ~2 rad.
		run(p, in, 0.5f);
		assertTrue(p.getSpeed() < 600f && p.getSpeed() > 450f);
		assertTrue(p.getHeading() > 1.5f);
	}

	@Test
	public void rollsDownhillFromRest()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(0.2f), 0, 0, 0);
		run(p, new SkateInput(), 1f);
		// slope gravity is scaled: 2000 * 0.2 * slopeGravityScale 0.4 = 160, less friction 45 -> ~115
		// after 1 s (was 2000 * 0.2 - 45 = 355, asserted > 250)
		assertTrue(p.getSpeed() > 100f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void rollsBackwardsDownhillWhenFacingUphill()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(0.2f), 0, 0, Angles.PI);
		run(p, new SkateInput(), 1f);
		// see rollsDownhillFromRest: ~-115 after 1 s with the scaled slope gravity
		assertTrue(p.getSpeed() < -100f);
		assertTrue(p.getY() > 0f);
	}

	@Test
	public void slowIntoWallStops()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, 0);
		p.setRollingSpeed(500);
		run(p, new SkateInput(), 1f);
		assertTrue(p.getY() < 200f);
		assertEquals(0f, p.getSpeed(), 0f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void fastIntoWallBails()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, 0);
		// wallBailSpeed=1100 (speed * cos(incidence), head-on here so cos = 1); friction + drag trim the speed slightly before impact.
		// y(k) = dt*(v0*k - 0.45*k*(k+1)); with v0=1500, y(7) = 0.02*(1500*7 - 0.45*7*8) = 209.5 >= 200,
		// y(6) = 0.02*(9000 - 0.45*42) = 179.6 < 200, so the wall is hit on step 7 at
		// speed = 1500 - 0.9*7 = 1493.7 > 1300 -> bails with margin. Heading is due north (straight
		// into the wall), so both the x-only and y-only slide probes are blocked (dx=0 trivially,
		// dy crosses the wall) and it falls back to hitWall()/bail() as before.
		p.setRollingSpeed(1500);
		run(p, new SkateInput(), 0.5f);
		assertEquals(SkaterState.BAILED, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void headOnWallAt1400Bails()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, 0);
		// wallBailSpeed=1300; v0=1400, friction trims 0.9/step.
		// y(k) = dt*(v0*k - 0.45*k*(k+1)); y(8) = 0.02*(1400*8 - 0.45*72) = 223.3 >= 200,
		// y(7) = 0.02*(9800 - 0.45*56) = 195.5 < 200, so the wall is hit on step 8 at
		// speed = 1400 - 0.9*8 = 1392.8 > 1300 -> bails. Head-on (heading 0 == wall normal), so
		// neither the x-only nor y-only slide probe finds an escape and it bails as a direct hit.
		p.setRollingSpeed(1400);
		run(p, new SkateInput(), 0.5f);
		assertEquals(SkaterState.BAILED, p.getState());
		assertEquals(BailReason.WALL, p.getLastBailReason());
		assertTrue(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void glancingWallAt35DegreesSlidesWithoutBailing()
	{
		// Was 30 degrees: walls now bail only when speed * cos(incidence) > wallBailSpeed 1100 AND the
		// incidence is within wallBailAngleDeg 30 of head-on, so exactly 30 degrees at 1400 now bails (see
		// SkatePhysicsWallTest). At 35 degrees: 1400 * cos 35 = 1147 > 1100, but 35 > 30, so the skater
		// scrapes along the wall (x keeps advancing, y stays pinned below 200) instead of bailing.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, (float) Math.toRadians(35));
		p.setRollingSpeed(1400);
		run(p, new SkateInput(), 1f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getY() < 200f);
		assertTrue(p.getX() > 300f);
		assertFalse(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void fakieGlancingWallSlideStaysFakie()
	{
		// Rolling backwards (speed -300) with the heading 180 + 30 degrees: the actual motion is 30 degrees
		// east of north, into the wall at y=60. The slide runs east along the wall; the board's closer
		// axis heading is west (-90 degrees: |210 - 270| = 60 vs |210 - 90| = 120), so it must keep
		// rolling backwards towards the east instead of being flipped round to regular.
		// The board now eases along the wall at wallAlignRate 1.5 rad/s (was a 0.5 rad/step snap): contact
		// starts after 60 / (300 * cos 30) = 0.23 s and the 60 degrees (1.05 rad) take 0.7 s more, so this
		// runs 1.2 s (was 0.6). Speed: 300 * (1 - 0.25 * 0.866) = 235 at contact, minus at most
		// 0.7 * (400 * 0.866 + 45 + 7) = 278 of scrape (into falls to 0 as it aligns, so really ~150) -> fakie.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(60, 300), 0, 0, (float) Math.toRadians(210));
		p.setRollingSpeed(-300);
		float x0 = p.getX();
		run(p, new SkateInput(), 1.2f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getY() < 60f);
		assertTrue("slid east", p.getX() > x0 + 20f);
		assertTrue("still fakie: " + p.getSpeed(), p.getSpeed() < 0f);
		assertEquals(-Math.PI / 2, p.getHeading(), 1e-3);
	}

	@Test
	public void bigStepUpActsAsWall()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.stepAtY(200, 0, 100), 0, 0, 0);
		p.setRollingSpeed(500);
		run(p, new SkateInput(), 1f);
		assertTrue(p.getY() < 200f);
		assertEquals(0f, p.getH(), 0f);
	}

	@Test
	public void resetAfterBailOnlyWhenTimerExpired()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 0, 0);
		// wallBailSpeed=1300; wall at y=100, v0=1500. y(k) = dt*(v0*k - 0.45*k*(k+1));
		// y(4) = 0.02*(6000 - 0.45*20) = 119.8 >= 100, y(3) = 0.02*(4500 - 0.45*12) = 89.9 < 100,
		// so the wall is hit on step 4 at speed = 1500 - 0.9*4 = 1496.4 > 1300 -> bails.
		p.setRollingSpeed(1500);
		run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
		SkateInput in = new SkateInput();
		in.resetRequested = true;
		p.step(DT, in);
		assertEquals(SkaterState.BAILED, p.getState());
		run(p, new SkateInput(), t.bailDuration);
		in.resetRequested = true;
		p.step(DT, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(0f, p.getSpeed(), 0f);
	}

	@Test
	public void bailRecoversAutomatically()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 0, 0);
		// See resetAfterBailOnlyWhenTimerExpired for the step/speed math (v0=1500, wall at y=100).
		p.setRollingSpeed(1500);
		run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
		p.drainEvents();
		run(p, new SkateInput(), t.bailDuration + t.bailAutoReset + 0.1f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.RESET));
	}

	@Test
	public void resetPressedDuringBailIsRemembered()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 0, 0);
		// See resetAfterBailOnlyWhenTimerExpired for the step/speed math (v0=1500, wall at y=100).
		p.setRollingSpeed(1500);
		run(p, new SkateInput(), 0.2f);
		SkateInput in = new SkateInput();
		in.resetRequested = true;
		p.step(DT, in);
		assertEquals(SkaterState.BAILED, p.getState());
		// no further R presses: back on the board as soon as the bail finishes, before auto-recovery
		run(p, new SkateInput(), t.bailDuration + 0.05f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}
}
