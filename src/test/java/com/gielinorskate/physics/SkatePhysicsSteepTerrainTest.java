package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Steep terrain: ramp launches off crests, momentum climbs up steep terrain rises, drop-ins down steep slopes. */
public class SkatePhysicsSteepTerrainTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	interface Height
	{
		float at(float y);
	}

	/** Terrain varying northwards only, no blockers, with an optional loaded-area edge at y = edgeY. */
	static CollisionWorld world(Height terrain, float blockerTop, float edgeY)
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return terrain.at(y);
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return terrain.at(y);
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return y1 >= 200f && y0 < 200f ? blockerTop : Float.NEGATIVE_INFINITY;
			}

			@Override
			public float edgeDistance(float x, float y)
			{
				return edgeY - y;
			}
		};
	}

	static CollisionWorld world(Height terrain)
	{
		return world(terrain, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
	}

	/** Uphill at {@code grade} up to the crest at y = 400 (a 3-tile run-up), then {@code after} per unit beyond it. */
	static CollisionWorld crest(float grade, float after)
	{
		return world(y -> y < 400f ? grade * y : grade * 400f + after * (y - 400f));
	}

	/** Flat, then a terrain rise of {@code gradient} per unit from y = 200 up to {@code top}, flat again above. */
	static Height rise(float gradient, float top)
	{
		return y -> y < 200f ? 0f : Math.min(top, gradient * (y - 200f));
	}

	/** Flat at 0, then a terrain fall of {@code gradient} per unit from y = 200 down to {@code -depth}. */
	static CollisionWorld fall(float gradient, float depth)
	{
		return world(y -> y < 200f ? 0f : -Math.min(depth, gradient * (y - 200f)));
	}

	private static List<SkateEvent> ride(SkatePhysics p, int steps, SkaterState mustStay)
	{
		List<SkateEvent> events = new ArrayList<>();
		for (int i = 0; i < steps; i++)
		{
			p.step(DT, new SkateInput());
			if (mustStay != null)
			{
				assertEquals("step " + i, mustStay, p.getState());
			}
			events.addAll(p.drainEvents());
		}
		return events;
	}

	// ---- ramp launch ----

	@Test
	public void fastOverACrestLaunches()
	{
		// grade 0.45 then flat: the slope ahead drops by 0.45 >= 0.35
		SkatePhysics p = new SkatePhysics(t, crest(0.45f, 0f), 0, 0, 0);
		p.setRollingSpeed(1300f);
		float launchSpeed = 0f;
		for (int i = 0; i < 40 && p.getState() == SkaterState.ROLLING; i++)
		{
			launchSpeed = p.getSpeed();
			p.step(DT, new SkateInput());
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.ROLL_OFF));
		// vh = speed * approach slope at take-off (one step of gravity at most since)
		assertEquals(launchSpeed * 0.45f, p.getVerticalVelocity(), 0.02f * t.gravity + 20f);
		assertTrue("left near the crest " + p.getY(), p.getY() > 360f && p.getY() < 440f);
		// a plain launch has no landing grade, like a roll-off
		List<SkateEvent> ev = ride(p, 100, null);
		assertFalse(ev.contains(SkateEvent.BAIL));
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(null, p.lastLandingQuality);
		assertTrue("flew past the crest " + p.getY(), p.getY() > 500f);
	}

	@Test
	public void aLaunchIsANormalAirForGrabs()
	{
		SkatePhysics p = new SkatePhysics(t, crest(0.45f, 0f), 0, 0, 0);
		p.setRollingSpeed(1300f);
		while (p.getState() == SkaterState.ROLLING && p.getY() < 500f)
		{
			p.step(DT, new SkateInput());
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		SkateInput grab = new SkateInput();
		grab.grabLeft = true;
		p.step(DT, grab);
		p.step(DT, grab);
		assertEquals(Trick.INDY, p.getActiveHold());
		ride(p, 100, null);
		assertEquals(SkaterState.ROLLING, p.getState());
		// a grabbed launch lands with a grade, like any trick
		assertTrue(p.lastLandingQuality != null);
	}

	@Test
	public void launchKeepsTheHorizontalSpeed()
	{
		SkatePhysics p = new SkatePhysics(t, crest(0.45f, -0.2f), 0, 0, 0);
		p.setRollingSpeed(1300f);
		float before = 0f;
		while (p.getState() == SkaterState.ROLLING && p.getY() < 500f)
		{
			before = p.getSpeed();
			p.step(DT, new SkateInput());
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(before, p.getSpeed(), 15f);
	}

	@Test
	public void aFlatPlatformOverACrestNeverLaunches()
	{
		// riding a flat platform top at 500 whose terrain underneath climbs at 0.45 to a crest at y = 400
		CollisionWorld w = new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return 500f;
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return y < 400f ? 0.45f * y : 0.45f * 400f;
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return Float.NEGATIVE_INFINITY;
			}

			@Override
			public float edgeDistance(float x, float y)
			{
				return Float.POSITIVE_INFINITY;
			}
		};
		SkatePhysics p = new SkatePhysics(t, w, 0, 0, 0);
		p.setRollingSpeed(1300f);
		List<SkateEvent> ev = ride(p, 40, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
		assertTrue("rolled past the crest " + p.getY(), p.getY() > 440f);
	}

	@Test
	public void aResetStartsANewRunUp()
	{
		SkatePhysics p = new SkatePhysics(t, crest(0.45f, 0f), 0, 0, 0);
		p.setRollingSpeed(1300f);
		while (p.getY() < 300f)
		{
			p.step(DT, new SkateInput());
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		// R while (nearly) stopped, 100 short of the crest: the run-up so far does not count any more
		p.setRollingSpeed(0f);
		SkateInput reset = new SkateInput();
		reset.resetRequested = true;
		p.step(DT, reset);
		assertTrue(p.drainEvents().contains(SkateEvent.RESET));
		p.setRollingSpeed(1300f);
		List<SkateEvent> ev = ride(p, 15, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
		assertTrue("rolled over the crest " + p.getY(), p.getY() > 420f);
	}

	@Test
	public void slowOverACrestStaysDown()
	{
		// 60 % of maxPushSpeed 1500 is 900
		SkatePhysics p = new SkatePhysics(t, crest(0.45f, 0f), 0, 0, 0);
		p.setRollingSpeed(850f);
		List<SkateEvent> ev = ride(p, 50, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
		assertTrue(p.getY() > 440f);
	}

	@Test
	public void aShortSteepBumpIsNotARamp()
	{
		// one tile of 0.45 uphill (less than the 256 run-up) then flat: rough ground, not a ramp
		CollisionWorld bump = world(y -> y < 300f ? 0f : 0.45f * (Math.min(y, 428f) - 300f));
		SkatePhysics p = new SkatePhysics(t, bump, 0, 0, 0);
		p.setRollingSpeed(1500f);
		List<SkateEvent> ev = ride(p, 40, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
		assertTrue(p.getY() > 500f);
	}

	@Test
	public void gentleCrestStaysDown()
	{
		// grade 0.3 then flat: a drop of 0.3 < 0.35
		SkatePhysics p = new SkatePhysics(t, crest(0.3f, 0f), 0, 0, 0);
		p.setRollingSpeed(1500f);
		List<SkateEvent> ev = ride(p, 40, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
	}

	@Test
	public void fastOnFlatOrDownhillOverABreakStaysDown()
	{
		// flat ground, and downhill steepening (not an uphill crest), never launch
		for (CollisionWorld w : new CollisionWorld[]{TestWorlds.flat(), crest(-0.1f, -0.6f)})
		{
			SkatePhysics p = new SkatePhysics(t, w, 0, 0, 0);
			p.setRollingSpeed(1500f);
			List<SkateEvent> ev = ride(p, 40, SkaterState.ROLLING);
			assertFalse(ev.contains(SkateEvent.ROLL_OFF));
		}
	}

	@Test
	public void launchScalesWithThePushSpeedSetting()
	{
		// at 150 % push speed the gate is 0.6 * 2250 = 1350: 1300 no longer launches
		SkatePhysics p = new SkatePhysics(t.scaled(1.5f, 1f), crest(0.45f, 0f), 0, 0, 0);
		p.setRollingSpeed(1300f);
		List<SkateEvent> ev = ride(p, 30, SkaterState.ROLLING);
		assertFalse(ev.contains(SkateEvent.ROLL_OFF));
	}

	// ---- momentum climbs ----

	@Test
	public void fastClimbsASteepTerrainRise()
	{
		// gradient 4: 8-unit substeps rise 32 > maxStepUp 24, a wall today; 1600 >= 40 * 32 = 1280 climbs it
		SkatePhysics p = new SkatePhysics(t, world(rise(4f, 120f)), 0, 0, 0);
		p.setRollingSpeed(1600f);
		ride(p, 20, SkaterState.ROLLING);
		assertEquals(120f, p.getH(), 0f);
		assertTrue(p.getY() > 300f);
		// the climb costs speed as gravity on the rise: well below a flat ride's ~1590 after 0.4 s
		SkatePhysics flat = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		flat.setRollingSpeed(1600f);
		ride(flat, 20, null);
		assertTrue("speed " + p.getSpeed() + " vs " + flat.getSpeed(), p.getSpeed() < flat.getSpeed() - 100f);
		assertEquals(0f, p.getHeading(), 0f);
	}

	@Test
	public void slowIsStillBlockedBySteepTerrain()
	{
		// 1000: 6.67-unit substeps rise 26.7 > 24 (blocked today) and 1000 < 40 * 26.7 = 1067
		SkatePhysics p = new SkatePhysics(t, world(rise(4f, 120f)), 0, 0, 0);
		p.setRollingSpeed(1000f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertTrue("h " + p.getH(), p.getH() < 30f);
		assertTrue("y " + p.getY(), p.getY() < 210f);
	}

	@Test
	public void blockersAreNeverClimbable()
	{
		// the same rise with a tall blocker across its foot
		SkatePhysics p = new SkatePhysics(t, world(rise(4f, 120f), 400f, Float.POSITIVE_INFINITY), 0, 0, 0);
		p.setRollingSpeed(2500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertTrue("y " + p.getY(), p.getY() < 200f);
		assertEquals(0f, p.getH(), 0f);
	}

	@Test
	public void platformsAreNeverClimbable()
	{
		// a 60 platform edge is ground, not terrain
		SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(200, 60), 0, 0, 0);
		p.setRollingSpeed(2500f);
		SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
		assertTrue("y " + p.getY(), p.getY() < 200f);
		assertEquals(0f, p.getH(), 0f);
	}

	@Test
	public void theSceneEdgeIsNeverClimbable()
	{
		// past the loaded area (edgeDistance < 0) the rise stays a wall, as do +INF out-of-bounds heights
		CollisionWorld edge = world(rise(4f, 120f), Float.NEGATIVE_INFINITY, 205f);
		CollisionWorld infinite = world(y -> y < 200f ? 0f : Float.POSITIVE_INFINITY);
		for (CollisionWorld w : new CollisionWorld[]{edge, infinite})
		{
			SkatePhysics p = new SkatePhysics(t, w, 0, 0, 0);
			p.setRollingSpeed(2500f);
			SkatePhysicsRollingTest.run(p, new SkateInput(), 1f);
			assertTrue("y " + p.getY(), p.getY() < 206f);
			assertTrue("h " + p.getH(), p.getH() < 25f);
		}
	}

	// ---- drop-ins ----

	@Test
	public void dropInHugsASteepTerrainSlope()
	{
		// gradient 3.5: 8-unit substeps drop 28 > rollOffDrop 24, a roll-off today
		SkatePhysics p = new SkatePhysics(t, fall(3.5f, 140f), 0, 0, 0);
		p.setRollingSpeed(1200f);
		for (int i = 0; i < 30; i++)
		{
			p.step(DT, new SkateInput());
			assertEquals("step " + i, SkaterState.ROLLING, p.getState());
			assertEquals(-Math.min(140f, Math.max(0f, 3.5f * (p.getY() - 200f))), p.getH(), 0.01f);
		}
		assertFalse(p.drainEvents().contains(SkateEvent.ROLL_OFF));
		assertTrue(p.getY() > 300f);
	}

	@Test
	public void tooSteepATerrainDropStillRollsOff()
	{
		// gradient 6 is past dropInMaxGradient: rolls off as today
		SkatePhysics p = new SkatePhysics(t, fall(6f, 140f), 0, 0, 0);
		p.setRollingSpeed(1200f);
		List<SkateEvent> ev = ride(p, 20, null);
		assertTrue(ev.contains(SkateEvent.ROLL_OFF));
	}

	@Test
	public void ledgesStillRollOff()
	{
		// a terrain step (vertical) and a platform edge are ledges
		SkatePhysics step = new SkatePhysics(t, TestWorlds.stepAtY(200, 0, -100), 0, 0, 0);
		step.setRollingSpeed(1200f);
		assertTrue(ride(step, 20, null).contains(SkateEvent.ROLL_OFF));
		SkatePhysics ledge = new SkatePhysics(t, TestWorlds.platformAtY(200, 60), 0, 300, Angles.PI);
		ledge.setRollingSpeed(1200f);
		assertTrue(ride(ledge, 20, null).contains(SkateEvent.ROLL_OFF));
	}
}
