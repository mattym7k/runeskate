package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import org.junit.Test;

/**
 * Leaving a rail (review P1/P5, extra 5): a pop with no steer comes back down onto the same rail, a slow
 * drop-off lands clear of the rail on the side the skater came from, and the exit hop grows with speed.
 */
public class SkatePhysicsGrindExitTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	/** An 8-tile (1024 unit) rail 10 east of the skater, from y = 50 north, top {@code top}. */
	private static GrindMap eightTileRail(float top)
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(10, 50, 10, 50 + 1024, top));
		return m;
	}

	private SkatePhysics lockOn(GrindMap m, float speed, SkateInput in)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), m, 0, 0, 0);
		p.setSpeed(speed);
		in.crouch = true;
		for (int i = 0; i < 12; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		for (int i = 0; i < 200 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		p.drainTrickEvents();
		return p;
	}

	@Test
	public void popWithNoSteerComesBackDownOntoTheSameRail()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(eightTileRail(60), 600, in);
		for (int i = 0; i < 15; i++) // grind 0.3 s
		{
			p.step(DT, in);
		}
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		float minH = Float.POSITIVE_INFINITY;
		for (int i = 0; i < 200 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
			if (p.getState() == SkaterState.AIRBORNE)
			{
				minH = Math.min(minH, p.getH());
			}
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertTrue("caught again before sinking to top - 12: min h " + minH, minH >= 60 - 12);
		assertEquals(60f, p.getH(), 1e-3f);
	}

	@Test
	public void kickflipOutWithNoSteerLandsBackOnTheRail()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(eightTileRail(30), 500, in);
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		for (int i = 0; i < 200 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(0f, p.getBoardRoll(), 1e-3f);
	}

	@Test
	public void slowDropOffLandsClearOfTheRailOnTheApproachSide()
	{
		// lock at 200 lifts the rail speed to 300; friction 60 u/s^2 drops it below 100 after 3.3 s,
		// about 700 units along the 1024 rail, so the exit is the slow one, not the rail end
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(eightTileRail(60), 200, in);
		for (int i = 0; i < 1000 && p.getState() == SkaterState.GRINDING; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.getY() < 50 + 1024 - 32);
		for (int i = 0; i < 200 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		// the skater came from x = 0, west of the x = 10 rail: 150 u/s west for the ~0.33 s drop is ~50
		assertTrue("lands west of the rail line: x " + p.getX(), p.getX() <= 10 - 40);
	}

	@Test
	public void exitHopGrowsWithRailSpeed()
	{
		SkateInput in = new SkateInput();
		SkatePhysics slow = lockOn(railEndingAt(800), 500, in);
		float slowVh = vhAtExit(slow, in);
		SkateInput in2 = new SkateInput();
		SkatePhysics fast = lockOn(railEndingAt(1500), 1400, in2);
		float fastVh = vhAtExit(fast, in2);
		assertTrue("slow " + slowVh + " fast " + fastVh, fastVh > slowVh + 60f);
	}

	@Test
	public void steeringOffTheRailEndLandsToThatSide()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(railEndingAt(400), 600, in);
		in.steer = 1f;
		for (int i = 0; i < 300 && p.getState() != SkaterState.ROLLING; i++)
		{
			p.step(DT, in);
			in.steer = p.getState() == SkaterState.GRINDING ? 1f : 0f;
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("steered east off the end: x " + p.getX(), p.getX() > 10 + 20);
	}

	private static GrindMap railEndingAt(float endY)
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(10, 50, 10, endY, 30));
		return m;
	}

	private static float vhAtExit(SkatePhysics p, SkateInput in)
	{
		for (int i = 0; i < 1000 && p.getState() == SkaterState.GRINDING; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		return p.getVerticalSpeed();
	}

	@Test
	public void noLockAtTheEndOfARailYouAreLeaving()
	{
		// the closest point is clamped to the y = 100 end; heading north only 10 units of rail are left
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(0, 0, 0, 100, 30));
		assertNull(m.nearest(10, 90, 30, 0f));
		assertTrue(m.nearest(10, 90, 30, (float) Math.PI) != null);
	}

	@Test
	public void slowDropOffDoesNotPingPongBetweenParallelRails()
	{
		// two long flat rails 40 apart (a table's top edges): the slow drop-off hops 150 u/s toward the side
		// the first rail was approached from, right over the other rail. It must not lock that one (and get
		// lifted back to the 300 lock speed) over and over: at most one relock, then the skater lands.
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(10, 50, 10, 50 + 4096, 60));
		m.add(new GrindSegment(-30, 50, -30, 50 + 4096, 60));
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(m, 400, in);
		int relocks = 0;
		for (int i = 0; i < 3000 && p.getState() != SkaterState.ROLLING; i++)
		{
			SkaterState before = p.getState();
			p.step(DT, in);
			if (before == SkaterState.AIRBORNE && p.getState() == SkaterState.GRINDING)
			{
				relocks++;
			}
			if (before == SkaterState.GRINDING && p.getState() == SkaterState.AIRBORNE)
			{
				assertNull("no rail highlighted after a drop-off", p.getPredictedGrind());
			}
		}
		assertTrue("relocks " + relocks, relocks <= 1);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void downhillRailNeverGrindsPastMaxSpeed()
	{
		t.maxSpeed = 700f;
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(10, 50, 10, 50 + 1024, 100, -250));
		SkateInput in = new SkateInput();
		SkatePhysics p = lockOn(m, 600, in);
		float top = 0f;
		for (int i = 0; i < 500 && p.getState() == SkaterState.GRINDING; i++)
		{
			p.step(DT, in);
			if (p.getState() == SkaterState.GRINDING)
			{
				top = Math.max(top, p.getSpeed());
			}
		}
		assertTrue("grind speed " + top, top <= 700f + 1e-3f);
		assertTrue("did speed up to the cap: " + top, top > 690f);
	}
}
