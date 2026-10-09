package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.TrickEvent;
import org.junit.Test;

/**
 * Walls scrape instead of steering. Incidence is measured from head-on: 0 deg = straight into the wall,
 * "into" = cos(incidence). All walls here are the line y = wallY across the skater's path (normal south).
 */
public class SkatePhysicsWallTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static float deg(float rad)
	{
		return (float) Math.toDegrees(rad);
	}

	@Test
	public void steeringIsNeverOverriddenByAWall()
	{
		// 30 deg incidence at 800; the old slide turned the board 30 -> 87 deg in two steps. A barely-held
		// steer (0.01 * <= 2.6 rad/s = 1.5 deg/s at most) must be all that turns it.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 90, (float) Math.toRadians(30));
		p.setRollingSpeed(800f);
		SkateInput in = new SkateInput();
		in.steer = 0.01f;
		SkatePhysicsRollingTest.run(p, in, 0.5f);
		assertTrue("x " + p.getX(), p.getX() > 60f);
		assertTrue(p.getY() < 100f);
		assertEquals(30f, deg(p.getHeading()), 1f);
	}

	@Test
	public void withoutSteerTheBoardEasesAlongTheWallSlowly()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 90, (float) Math.toRadians(30));
		p.setRollingSpeed(800f);
		SkateInput in = new SkateInput();
		float maxTurn = 0f;
		for (int i = 0; i < 60; i++)
		{
			float before = p.getHeading();
			p.step(DT, in);
			maxTurn = Math.max(maxTurn, Angles.absDiff(before, p.getHeading()));
		}
		// at most wallAlignRate 1.5 rad/s: 0.03 rad per 0.02 s step
		assertTrue("max turn " + maxTurn, maxTurn <= 1.5f * DT + 1e-4f);
		// 60 deg to go at 1.5 rad/s takes 0.7 s of the 1.2 s
		assertEquals(90f, deg(p.getHeading()), 0.01f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void glancingScrapeKeepsMostOfTheSpeed()
	{
		// 80 deg incidence (10 deg off the wall line) at 1400: into = cos 80 = 0.174, the first contact
		// costs 25% * 0.174 = 4.3% (1400 -> 1339); then 0.5 s of friction + drag (45 + 0.00012 * 1300^2 =
		// 248 u/s^2, ~124) and a scrape of 400 * into while the board eases the last 10 deg (~0.12 s, < 5):
		// about 1210 left. The old slide re-projected the speed every step.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(80));
		p.setRollingSpeed(1400f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.5f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("speed " + p.getSpeed(), p.getSpeed() > 1150f);
		assertEquals(90f, deg(p.getHeading()), 0.01f);
	}

	@Test
	public void holdingSteerAndPushEscapesAWallWithinASecondAndAHalf()
	{
		// stopped right at the wall, facing it 10 deg off head-on; A + W: the old slide forced the board
		// back toward the wall line every step ("impossible to turn around")
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(10));
		SkateInput in = new SkateInput();
		in.steer = -1f;
		in.pushHeld = true;
		boolean left = false;
		for (int i = 0; i < 75 && !left; i++)
		{
			p.step(DT, in);
			left = p.getY() < 60f;
		}
		assertTrue("left the wall, y " + p.getY() + " heading " + deg(p.getHeading()), left);
	}

	@Test
	public void bailsWithin30DegreesOfHeadOnAboveBailSpeed()
	{
		// 1400 at 30 deg: 1400 * cos 30 = 1212 > 1100 and within the 30 deg cone -> bail
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 90, (float) Math.toRadians(30));
		p.setRollingSpeed(1400f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
	}

	@Test
	public void noBailAt31DegreesEvenAboveBailSpeed()
	{
		// 1400 * cos 31 = 1200 > 1100, but outside the 30 deg cone: a hard scrape, not a bail
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 90, (float) Math.toRadians(31));
		p.setRollingSpeed(1400f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertFalse(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void headOnBelowBailSpeedStopsWithoutBailing()
	{
		// 1000 head-on: 1000 * 1 < wallBailSpeed 1100
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, 0f);
		p.setRollingSpeed(1000f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.3f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(0f, p.getSpeed(), 0f);
		assertTrue(p.getY() < 100f);
	}

	@Test
	public void hardHitStumblesWithSteeringLockedButKeepsTheCombo()
	{
		// 45 deg at 1000: impact 1000 * cos 45 = 707 > wallStumbleSpeed 600 -> 0.3 s with steering locked
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(45));
		p.setRollingSpeed(1000f);
		SkateInput in = new SkateInput();
		in.steer = -1f; // into the wall: would turn the board left (toward north) if it worked
		p.step(DT, in);
		p.drainTrickEvents();
		float atContact = p.getHeading();
		for (int i = 0; i < 12; i++)
		{
			p.step(DT, in);
		}
		// locked: the held left steer does nothing, and a wall never turns a board that is being steered
		float afterLock = p.getHeading();
		assertTrue("heading " + deg(afterLock), afterLock >= atContact);
		assertFalse(p.drainTrickEvents().contains(TrickEvent.bailed()));
		assertEquals(SkaterState.ROLLING, p.getState());
		// the 0.3 s lock ends during the next steps; then the steer works again (8 steps at ~1.7 rad/s
		// left = -0.27 rad)
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		assertTrue("heading " + deg(p.getHeading()), p.getHeading() < afterLock);
	}

	@Test
	public void lightHitDoesNotLockSteering()
	{
		// 60 deg at 1000 (58 after the first step's steer): impact 1000 * cos 58 = 530 < 600, no stumble
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(60));
		p.setRollingSpeed(1000f);
		SkateInput in = new SkateInput();
		in.steer = -1f;
		p.step(DT, in);
		float atContact = p.getHeading();
		p.step(DT, in);
		assertTrue(p.getHeading() < atContact);
	}

	@Test
	public void airborneHeadOnAbove1100Bails()
	{
		// a dropped-in ollie at 1200 head-on into a 300-high wall: 1200 * 1 > 1100 (was only > 1300)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, 0f);
		p.setRollingSpeed(1200f);
		SkateInput in = new SkateInput();
		in.gestures.add(new com.gielinorskate.tricks.Gesture(com.gielinorskate.tricks.Gesture.Direction.UP, false, 0f));
		SkatePhysicsRollingTest.run(p, in, 0.3f);
		assertEquals(SkaterState.BAILED, p.getState());
	}

	@Test
	public void afterAWallBailTheSkaterRecoversAlongTheWallAndCanRideOff()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, 0f);
		p.setRollingSpeed(1500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
		// bailAutoReset 1.0 -> 0.5
		SkatePhysicsRollingTest.run(p, new SkateInput(), t.bailDuration + 0.55f);
		assertEquals(SkaterState.ROLLING, p.getState());
		// along the wall (a 90 degree turn at most), not straight away from it (a 180 degree camera swing)
		assertEquals(90f, Math.abs(deg(p.getHeading())), 0.01f);
		float x0 = p.getX();
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		SkatePhysicsRollingTest.run(p, in, 0.3f);
		assertTrue(Math.abs(p.getX() - x0) > 50f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void stumbleRunsOutInTheAirToo()
	{
		// stumble (as above), pop straight away and land ~0.75 s later: the 0.3 s steering lock is long over
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(45));
		p.setRollingSpeed(1000f);
		SkateInput in = new SkateInput();
		p.step(DT, in);
		in.gestures.add(new com.gielinorskate.tricks.Gesture(com.gielinorskate.tricks.Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		float landed = p.getHeading();
		in.steer = -1f;
		p.step(DT, in);
		p.step(DT, in);
		assertTrue("steers straight after landing: " + deg(landed) + " -> " + deg(p.getHeading()),
			p.getHeading() < landed - 0.01f);
	}
}
