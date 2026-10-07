package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Random;
import org.junit.Test;

/** The board flying off after a bail: spins, bounces off walls and the ground, rests flat; always finite. */
public class BoardBounceTest
{
	private static final float DT = 0.02f;
	private static final float G = new SkateTuning().gravity;

	private static BoardBounce thrown(CollisionWorld w, float speed, long seed)
	{
		BoardBounce b = new BoardBounce(w, G);
		b.start(0, 0, w.groundHeight(0, 0), 0f, speed, 0f, new Random(seed));
		return b;
	}

	private static float runToRest(BoardBounce b, float seconds)
	{
		float t = 0f;
		while (t < seconds && !b.isAtRest())
		{
			b.step(DT);
			assertFinite(b);
			t += DT;
		}
		return t;
	}

	private static void assertFinite(BoardBounce b)
	{
		float[] v = {b.getX(), b.getY(), b.getH(), b.getYaw(), b.getRoll(), b.getPitch()};
		for (float f : v)
		{
			assertFalse("not finite: " + f, Float.isNaN(f) || Float.isInfinite(f));
		}
	}

	@Test
	public void itFliesOffSpinningAndComesToRestFlat()
	{
		for (long seed = 0; seed < 20; seed++)
		{
			for (float speed : new float[]{0f, 400f, 1500f, 2600f})
			{
				BoardBounce b = thrown(TestWorlds.flat(), speed, seed);
				assertTrue(b.getVerticalSpeed() > 0f);
				float t = runToRest(b, 5f);
				assertTrue("rests (seed " + seed + ", " + speed + ")", b.isAtRest());
				assertTrue("rests in time: " + t, t < BoardBounce.REST_TIMEOUT + 1e-3f);
				// wheels down, flat on the ground
				assertEquals(0f, b.getRoll(), 0f);
				assertEquals(0f, b.getPitch(), 0f);
				assertEquals(0f, b.getH(), 0f);
			}
		}
	}

	@Test
	public void itGoesTheWayTheSkaterWas()
	{
		BoardBounce b = thrown(TestWorlds.flat(), 1500f, 3);
		runToRest(b, 5f);
		assertTrue(b.getY() > 200f);
	}

	@Test
	public void itSpinsAndFlipsInTheAir()
	{
		BoardBounce b = thrown(TestWorlds.flat(), 1500f, 7);
		float yaw0 = b.getYaw();
		for (int i = 0; i < 5; i++)
		{
			b.step(DT);
		}
		assertTrue(Math.abs(b.getYaw() - yaw0) > 0.1f);
		assertTrue(Math.abs(b.getRoll()) > 0.1f);
	}

	@Test
	public void neverPassesThroughAWall()
	{
		CollisionWorld w = TestWorlds.wallAtY(150f, 300f);
		for (long seed = 0; seed < 10; seed++)
		{
			BoardBounce b = thrown(w, 2600f, seed);
			for (int i = 0; i < 300; i++)
			{
				b.step(DT);
				assertTrue("through the wall: y " + b.getY(), b.getY() < 150f);
			}
			assertTrue(b.isAtRest());
		}
	}

	@Test
	public void staysInsideTheLoadedArea()
	{
		BoardBounce b = thrown(TestWorlds.edgeAtY(100f), 2600f, 1);
		runToRest(b, 5f);
		assertTrue(b.getY() < 100f);
	}

	@Test
	public void restNowPutsItFlatOnTheGroundWhereItIs()
	{
		BoardBounce b = thrown(TestWorlds.flat(), 1500f, 2);
		b.step(DT);
		b.step(DT);
		b.restNow();
		assertTrue(b.isAtRest());
		assertEquals(0f, b.getH(), 0f);
		assertEquals(0f, b.getRoll(), 0f);
		float y = b.getY();
		b.step(DT);
		assertEquals(y, b.getY(), 0f);
	}

	@Test
	public void aNonFiniteStartStaysFinite()
	{
		BoardBounce b = new BoardBounce(TestWorlds.flat(), G);
		b.start(Float.NaN, 0, 0, Float.POSITIVE_INFINITY, 0f, Float.NaN, new Random(0));
		runToRest(b, 5f);
		assertTrue(b.isAtRest());
	}
}
