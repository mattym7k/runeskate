package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.world.GridCollisionWorld;
import com.gielinorskate.world.WorldTests;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * Flat ground and moderate hills (nothing that blocks or rolls off) must ride bit for bit as they did before
 * the steep-terrain additions (ramp launch, momentum climbs, drop-ins). Each trace hashes every step's raw
 * float bits; the expected hashes were recorded on the code before those additions.
 */
public class SkatePhysicsSteepRegressionTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();
	/** Fastest speed reached by the last trace: the ramp-launch and climb speed gates must be in play. */
	static float topSpeed;

	/** Pushes, carves left and right, pops, grabs and coasts for {@code steps} steps, hashing every step. */
	static long trace(SkatePhysics p, int steps)
	{
		SkateInput in = new SkateInput();
		long hash = 17;
		topSpeed = 0f;
		for (int i = 0; i < steps; i++)
		{
			in.pushHeld = (i / 150) % 2 == 0;
			in.pushPressed = in.pushHeld && i % 20 == 0;
			in.steer = (float) Math.sin(i * 0.013);
			if (Math.abs(in.steer) < 0.3f || p.getState() != SkaterState.ROLLING)
			{
				in.steer = 0f;
			}
			if (i % 90 == 45)
			{
				in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
			}
			in.grabLeft = i % 90 > 50 && i % 90 < 60;
			p.step(DT, in);
			topSpeed = Math.max(topSpeed, Math.abs(p.getSpeed()));
			hash = mix(hash, p.getX());
			hash = mix(hash, p.getY());
			hash = mix(hash, p.getH());
			hash = mix(hash, p.getSpeed());
			hash = mix(hash, p.getHeading());
			hash = mix(hash, p.getVerticalVelocity());
			hash = hash * 31 + p.getState().ordinal();
			List<SkateEvent> ev = p.drainEvents();
			assertFalse("step " + i, ev.contains(SkateEvent.ROLL_OFF));
			assertFalse("step " + i, ev.contains(SkateEvent.BAIL));
			for (SkateEvent e : ev)
			{
				hash = hash * 31 + e.ordinal();
			}
			p.drainTrickEvents();
		}
		return hash;
	}

	private static long mix(long hash, float v)
	{
		return hash * 1_000_003L + Float.floatToIntBits(v);
	}

	private static CollisionWorld rollingHills()
	{
		return new CollisionWorld()
		{
			@Override
			public float groundHeight(float x, float y)
			{
				return terrainHeight(x, y);
			}

			@Override
			public float terrainHeight(float x, float y)
			{
				return 80f * (float) Math.sin(x / 350f) + 60f * (float) Math.cos(y / 280f);
			}

			@Override
			public float blockerTop(float x0, float y0, float x1, float y1)
			{
				return Float.NEGATIVE_INFINITY;
			}
		};
	}

	private static GridCollisionWorld gentleGrid()
	{
		GridCollisionWorld w = new GridCollisionWorld(200);
		Random r = new Random(11);
		for (int cx = 0; cx <= 200; cx++)
		{
			for (int cy = 0; cy <= 200; cy++)
			{
				WorldTests.setCornerHeight(w, cx, cy, 40f * (float) Math.sin(cx * 0.4) + (float) r.nextGaussian() * 6f);
			}
		}
		return w;
	}

	@Test
	public void flatTraceUnchanged()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		assertEquals(1153949014361533376L, trace(p, 1200));
		assertTrue("top speed " + topSpeed, topSpeed > 1000f);
	}

	@Test
	public void rollingHillsTraceUnchanged()
	{
		SkatePhysics p = new SkatePhysics(t, rollingHills(), 0, 0, 0.3f);
		assertEquals(7113641921327721744L, trace(p, 1500));
		assertTrue("top speed " + topSpeed, topSpeed > 1000f);
	}

	@Test
	public void gentleGridTraceUnchanged()
	{
		SkatePhysics p = new SkatePhysics(t, gentleGrid(), 100 * 128, 100 * 128, 0.1f);
		assertEquals(6769500491294778404L, trace(p, 1200));
		assertTrue("top speed " + topSpeed, topSpeed > 1000f);
	}

	@Test
	public void bumpyDownhillTraceUnchanged()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.bumpyDownhillNorth(0.15f, 6f, 30f), 0, 0, 0);
		assertEquals(-2908321794293859373L, trace(p, 900));
		assertTrue("top speed " + topSpeed, topSpeed > 1000f);
	}

	@Test
	public void steadyUphillTraceUnchanged()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.downhillNorth(-0.4f), 0, 0, 0);
		assertEquals(-799882887334767439L, trace(p, 900));
		assertTrue("top speed " + topSpeed, topSpeed > 700f);
	}
}
