package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.world.GridCollisionWorld;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Hills, bumps, curbs and pushing: the riding-feel review's terrain scenarios. */
public class SkatePhysicsTerrainTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static float drag(SkateTuning t, float v)
	{
		return t.rollingFriction + t.drag * v * v;
	}

	@Test
	public void slopeGravityIsScaledDown()
	{
		// grade 0.2: 2000 * 0.2 * 0.4 = 160 u/s^2 down the hill, less friction 45 and drag
		// (0.00012 * 115^2 = 1.6 at most): about 115 after 1 s (was 2000 * 0.2 - 45 = 355)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(0.2f), 0, 0, 0);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertEquals(114.5f, p.getSpeed(), 1f);
	}

	@Test
	public void slopeIsClampedBeforeScaling()
	{
		// grade 2 is clamped to 0.6: 2000 * 0.6 * 0.4 = 480, less friction 45 -> 435 u/s^2,
		// so about 217 after 0.5 s (drag at most 0.00012 * 217^2 = 5.7 u/s^2)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(2f), 0, 0, 0);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 0.5f);
		assertEquals(216f, p.getSpeed(), 2f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void steepUphillHoldingPushNeverRollsBack()
	{
		// 80 per tile uphill (0.625, clamped 0.6): 480 + 45 = 525 u/s^2 against the skater. Pushes every
		// 0.4 s: 360 from rest, then 240 * (1 - s/1500)^0.5. The pushes balance the 0.4 * 525 = 210 lost
		// per cycle at s = 1500 * (1 - (210/240)^2) = 352, so the speed saw-tooths between ~350 and ~560.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(-80f / 128f), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		float min = Float.MAX_VALUE;
		for (int i = 0; i < 150; i++)
		{
			p.step(DT, in);
			min = Math.min(min, p.getSpeed());
		}
		assertTrue("min " + min, min >= 0f);
		assertTrue("end " + p.getSpeed(), p.getSpeed() > 300f);
		assertFalse(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void firstPushFromRestIsStronger()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		assertEquals(t.pushFromRest - drag(t, 360f) * DT, p.getSpeed(), 0.05f);
	}

	@Test
	public void pushImpulseTapersWithSpeed()
	{
		// at 1000: 240 * (1 - 1000/1500)^0.5 = 138.56 -> 1138.56, then friction + drag for one step:
		// (45 + 0.00012 * 1138.56^2) * 0.02 = 4.01
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(1000f);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		assertEquals(1138.56f - drag(t, 1138.56f) * DT, p.getSpeed(), 0.05f);
	}

	@Test
	public void pushNearMaxPushSpeedStillGivesTheMinimum()
	{
		// at 1450: 240 * (50/1500)^0.5 = 43.8 < the 60 minimum, capped at maxPushSpeed 1500
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(1450f);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		assertEquals(1500f - drag(t, 1500f) * DT, p.getSpeed(), 0.05f);
	}

	@Test
	public void pushWhileRollingBackSlowlyPushesForward()
	{
		// stalled and rolling back at 200 (not a fakie landing): max(60, -200 + 360) = 160 forward
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(-200f);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		assertEquals(160f - drag(t, 160f) * DT, p.getSpeed(), 0.05f);
	}

	@Test
	public void pushAfterAFakieLandingPushesFakie()
	{
		// half spin at 300: 7 rad/s * 0.44 s = 176.5 deg, lands fakie at about -300 (above -400)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(300f);
		SkatePhysicsAirTest.ollie(p, 0.4f, 1f, 0.44f);
		assertEquals(SkaterState.ROLLING, p.getState());
		float landed = p.getSpeed();
		assertTrue("landed fakie " + landed, landed < -200f && landed > -400f);
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		p.step(DT, in);
		assertTrue("pushed fakie " + p.getSpeed(), p.getSpeed() < landed - 100f);
	}

	@Test
	public void coastingFastIsSlowedByAirDrag()
	{
		// (45 + 0.00006 * 2000^2) * 0.02 = 5.7 in one step
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(2000f);
		p.step(DT, new SkateInput());
		assertEquals(1994.3f, p.getSpeed(), 0.05f);
	}

	@Test
	public void popOnAnUphillAddsTheGroundsVerticalSpeed()
	{
		// grade 0.25 uphill at 800: the pop step first loses (200 + 45 + 0.00006 * 800^2) * 0.02 = 5.67
		// -> 794.33; vh = 0.92 * 820 + 794.33 * 0.25 = 952.98 (minPopFraction 0.85 -> 0.92). The next step:
		// vh -= 40, h += 912.98 * 0.02 = 18.26
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(-0.25f), 0, 0, 0);
		p.setSpeed(800f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		float h0 = p.getH();
		p.step(DT, in);
		assertEquals(18.26f, p.getH() - h0, 0.05f);
	}

	@Test
	public void steepUphillAtSpeedDoesNotBail()
	{
		// 110 per tile = 0.86: a whole 0.02 s step at 1500 used to rise 26 > maxStepUp 24 and hit a "wall"
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(-110f / 128f), 0, 0, 0);
		p.setSpeed(1500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getSpeed() > 500f);
		assertEquals(0f, p.getHeading(), 0f);
	}

	@Test
	public void steepDownhillAtSpeedStaysGlued()
	{
		// 80 per tile down from 1500 used to reach ~1930 and roll off (a 0.02 s step dropping > 24)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(80f / 128f), 0, 0, 0);
		p.setSpeed(1500f);
		for (int i = 0; i < 150; i++)
		{
			p.step(DT, new SkateInput());
			assertEquals(SkaterState.ROLLING, p.getState());
			assertEquals(-80f / 128f * p.getY(), p.getH(), 0.01f);
		}
		assertFalse(p.drainEvents().contains(SkateEvent.ROLL_OFF));
	}

	@Test
	public void roughTerrainGivesNoHopsBailsOrTurns()
	{
		for (float sigma : new float[]{35f, 50f})
		{
			for (float speed : new float[]{1500f, 2000f})
			{
				GridCollisionWorld w = new GridCollisionWorld(40);
				Random r = new Random(7);
				for (int cx = 0; cx <= 40; cx++)
				{
					for (int cy = 0; cy <= 40; cy++)
					{
						w.setCornerHeight(cx, cy, (float) r.nextGaussian() * sigma);
					}
				}
				SkatePhysics p = new SkatePhysics(t, w, 20 * 128 + 40, 4 * 128 + 20, 0);
				p.setSpeed(speed);
				for (int i = 0; i < 60; i++)
				{
					p.step(DT, new SkateInput());
					assertEquals("sigma " + sigma + " at " + speed, SkaterState.ROLLING, p.getState());
				}
				List<SkateEvent> ev = p.drainEvents();
				assertFalse(ev.contains(SkateEvent.ROLL_OFF));
				assertFalse(ev.contains(SkateEvent.LAND));
				assertFalse(ev.contains(SkateEvent.BAIL));
				assertEquals(0f, p.getHeading(), 0f);
			}
		}
	}

	@Test
	public void sixtyUnitPlatformStillBlocks()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(200, 60), 0, 0, 0);
		p.setSpeed(500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertTrue(p.getY() < 200f);
		assertEquals(0f, p.getH(), 0f);
	}

	@Test
	public void sixtyUnitPlatformStillRollsOff()
	{
		// on the platform (y >= 200 is 60 high) heading south off its edge
		SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(200, 60), 0, 300, Angles.PI);
		p.setSpeed(500f);
		boolean airborne = false;
		for (int i = 0; i < 100; i++)
		{
			p.step(DT, new SkateInput());
			airborne |= p.getState() == SkaterState.AIRBORNE;
		}
		assertTrue(airborne);
		assertTrue(p.drainEvents().contains(SkateEvent.ROLL_OFF));
		assertEquals(0f, p.getH(), 0f);
	}

	@Test
	public void twentyUnitCurbRollsUp()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(200, 20), 0, 0, 0);
		p.setSpeed(500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertTrue(p.getY() > 300f);
		assertEquals(20f, p.getH(), 0f);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void stoppedNextToACurbOrPlatformStaysStill()
	{
		for (float top : new float[]{20f, 60f})
		{
			// the slope sampler (+-16 along the heading) straddles the edge both facing it and facing away
			for (float heading : new float[]{0f, Angles.PI})
			{
				SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(200, top), 0, 190, heading);
				SkatePhysicsRollingTest.run(p, new SkateInput(), 2f);
				assertEquals("top " + top + " heading " + heading, 0f, p.getSpeed(), 0f);
				assertEquals(190f, p.getY(), 0f);
			}
		}
	}
}
