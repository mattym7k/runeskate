package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.world.GridCollisionWorld;
import org.junit.Test;

/** A name-grindable object's box, through the real physics: it blocks rolling and its top is landable. */
public class GrindableBoxPhysicsTest
{
	private static final float DT = 0.02f;
	private static final float T = GridCollisionWorld.TILE;
	private final SkateTuning t = new SkateTuning();

	/** Flat 10x10 world with a 100-tall bench running east-west through (5 T, 5 T): 400 long, 8 deep. */
	private static GridCollisionWorld bench()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.addGrindableBlocker(5 * T, 5 * T, new float[]{-200, 200, -4, 4}, 0, 100);
		return w;
	}

	@Test
	public void rollingIntoABenchIsBlocked()
	{
		SkatePhysics p = new SkatePhysics(t, bench(), 5 * T, 2 * T, 0);
		p.setSpeed(600);
		SkateInput idle = new SkateInput();
		for (int i = 0; i < 150; i++)
		{
			p.step(DT, idle);
			// the 8-deep bench is inflated to 24 deep: its south face is at 5 T - 12
			assertTrue("y " + p.getY(), p.getY() < 5 * T - 12);
			assertEquals(0f, p.getH(), 1e-3f);
		}
	}

	@Test
	public void aSkaterOverTheBenchStandsOnItsTop()
	{
		SkatePhysics p = new SkatePhysics(t, bench(), 5 * T + 50, 5 * T + 6, 0);
		assertEquals(100f, p.getH(), 1e-3f);
	}

	@Test
	public void anOllieOntoALongTableLandsOnItsTop()
	{
		// a 100-tall, 500-deep table across the path (name-grindable, so landable)
		GridCollisionWorld w = new GridCollisionWorld(12);
		w.addGrindableBlocker(5 * T, 650, new float[]{-150, 150, -250, 250}, 0, 100);
		SkatePhysics p = new SkatePhysics(t, w, 5 * T, 0, 0);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 20; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		for (int i = 0; i < 200 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("landed inside the table, y " + p.getY(), p.getY() > 400 && p.getY() < 900);
		assertEquals(100f, p.getH(), 1e-3f);
	}
}
