package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import org.junit.Test;

/** Read-only values for the HUD: the latest landing's quality and the rail the current flight will catch. */
public class SkatePhysicsHudTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics popped(CollisionWorld w, GrindMap m, Direction flick)
	{
		SkatePhysics p = new SkatePhysics(t, w, m, 0, 0, 0);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(flick, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		return p;
	}

	private static void land(SkatePhysics p, SkateInput in)
	{
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
	}

	@Test
	public void aStraightOllieLandsClean()
	{
		SkatePhysics p = popped(TestWorlds.flat(), new GrindMap(), Direction.UP);
		assertNull(p.getLastLandingQuality());
		land(p, new SkateInput());
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(LandingQuality.CLEAN, p.getLastLandingQuality());
	}

	@Test
	public void aFinishedKickflipLandsClean()
	{
		SkatePhysics p = popped(TestWorlds.flat(), new GrindMap(), Direction.UP_LEFT);
		land(p, new SkateInput());
		assertEquals(LandingQuality.CLEAN, p.getLastLandingQuality());
	}

	@Test
	public void landingTwentyDegreesOffTheTravelIsSloppy()
	{
		// steer in the last 3 steps before touchdown: 3 * 0.14 rad = 24 degrees, inside the 55-degree landing
		// tolerance but over the 15 of a clean landing
		SkatePhysics p = popped(TestWorlds.flat(), new GrindMap(), Direction.UP);
		SkateInput in = new SkateInput();
		float prevH = p.getH();
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			boolean falling = p.getH() < prevH;
			prevH = p.getH();
			in.steer = falling && p.getH() < 40 ? 1f : 0f;
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(LandingQuality.SLOPPY, p.getLastLandingQuality());
	}

	@Test
	public void rollingOffALedgeHasNoQuality()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.stepAtY(0, 0, 100), 0, 100, (float) Math.PI);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 20 && p.getState() == SkaterState.ROLLING; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertNull(p.getLastLandingQuality());
	}

	@Test
	public void predictedGrindIsTheRailTheFlightWillCatch()
	{
		// the rail starts at y = 400, reached only on the way down
		GrindSegment rail = new GrindSegment(10, 400, 10, 1500, 30);
		GrindMap m = new GrindMap();
		m.add(rail);
		SkatePhysics rolling = new SkatePhysics(t, TestWorlds.flat(), m, 0, 0, 0);
		assertNull(rolling.getPredictedGrind());

		SkatePhysics p = popped(TestWorlds.flat(), m, Direction.UP);
		assertSame(rail, p.getPredictedGrind());
		SkateInput in = new SkateInput();
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			assertSame(rail, p.getPredictedGrind());
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertNull(p.getPredictedGrind());
	}

	@Test
	public void noPredictionWhenTheFlightMissesEveryRail()
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(300, 0, 300, 1500, 30));
		SkatePhysics p = popped(TestWorlds.flat(), m, Direction.UP);
		assertNull(p.getPredictedGrind());
	}

	@Test
	public void theLandedEventCarriesTheLandingQuality()
	{
		SkatePhysics clean = popped(TestWorlds.flat(), new GrindMap(), Direction.UP);
		clean.drainTrickEvents();
		land(clean, new SkateInput());
		assertTrue(clean.drainTrickEvents().contains(TrickEvent.landed(true)));

		SkatePhysics p = popped(TestWorlds.flat(), new GrindMap(), Direction.UP);
		p.drainTrickEvents();
		SkateInput in = new SkateInput();
		float prevH = p.getH();
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			boolean falling = p.getH() < prevH;
			prevH = p.getH();
			in.steer = falling && p.getH() < 40 ? 1f : 0f;
			p.step(DT, in);
		}
		assertTrue(p.drainTrickEvents().contains(TrickEvent.landed(false)));
	}
}
