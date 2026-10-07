package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The knocked-off skater's body: hop, tumble, impact, skid; never through walls, always at rest, always finite. */
public class TumbleBodyTest
{
	private static final float DT = 0.02f;
	private static final float G = new SkateTuning().gravity;
	private static final float R = new SkateTuning().skaterRadius;

	private static TumbleBody flung(CollisionWorld w, float speed, float travel)
	{
		TumbleBody b = new TumbleBody(w, G, R);
		b.start(0, 0, w.groundHeight(0, 0), (float) Math.sin(travel) * speed, (float) Math.cos(travel) * speed,
			travel, false, 0f, 0f, 0.3f);
		return b;
	}

	/** Steps until at rest or {@code seconds} pass; returns the seconds stepped. */
	private static float runToRest(TumbleBody b, float seconds)
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

	private static void assertFinite(TumbleBody b)
	{
		float[] v = {b.getX(), b.getY(), b.getH(), b.getAngle(), b.getRoll(), b.getSpeed()};
		for (float f : v)
		{
			assertFalse("not finite: " + f, Float.isNaN(f) || Float.isInfinite(f));
		}
	}

	@Test
	public void aFastBailHopsTumblesForwardAndLandsQuickly()
	{
		TumbleBody b = flung(TestWorlds.flat(), 1500f, 0f);
		assertTrue(b.isAirborne());
		float t = 0f;
		while (b.isAirborne() && t < 2f)
		{
			b.step(DT);
			t += DT;
		}
		// short and punchy: the hop is about a quarter second
		assertTrue("airtime " + t, t > 0.15f && t < 0.4f);
		assertTrue(b.takeImpact());
		assertFalse("the impact is reported once", b.takeImpact());
		// carried forward along the travel direction (north)
		assertTrue(b.getY() > 100f);
		assertEquals(0f, b.getX(), 1e-3f);
		// over a turn, about the side axis (forward)
		assertTrue("tumbled " + b.getAngle(), b.getAngle() > 2 * Math.PI);
	}

	@Test
	public void aSlowBailHopsLessAndTurnsLess()
	{
		TumbleBody fast = flung(TestWorlds.flat(), 1500f, 0f);
		TumbleBody slow = flung(TestWorlds.flat(), 200f, 0f);
		assertTrue(slow.getVerticalSpeed() < fast.getVerticalSpeed());
		assertTrue(slow.getLieAngle() < fast.getLieAngle());
		assertTrue(slow.getLieAngle() > 0f);
	}

	@Test
	public void itComesToRestLyingFlatOnBackOrFront()
	{
		for (float speed : new float[]{0f, 200f, 800f, 1500f, 2600f})
		{
			TumbleBody b = flung(TestWorlds.flat(), speed, 1f);
			float t = runToRest(b, 3f);
			assertTrue("rests at " + speed + " within " + t, b.isAtRest());
			assertTrue("rests quickly: " + t, t < 1.2f);
			// lying: a quarter turn plus whole half turns
			double lying = (b.getAngle() - Math.PI / 2) / Math.PI;
			assertEquals("lying at " + speed + ": " + b.getAngle(), Math.round(lying), lying, 0.05);
			assertEquals(0f, b.getH(), 0f);
			assertEquals(0f, b.getSpeed(), 0f);
		}
	}

	@Test
	public void neverPassesThroughAWall()
	{
		CollisionWorld w = TestWorlds.wallAtY(150f, 300f);
		for (float speed : new float[]{300f, 1500f, 2600f})
		{
			TumbleBody b = flung(w, speed, 0f);
			for (int i = 0; i < 200; i++)
			{
				b.step(DT);
				assertTrue("through the wall at " + speed + ": y " + b.getY(), b.getY() < 150f);
				assertFinite(b);
			}
			assertTrue(b.isAtRest());
		}
	}

	@Test
	public void aWallBailReboundsOffTheWallAndFallsBackward()
	{
		CollisionWorld w = TestWorlds.wallAtY(20f, 300f);
		TumbleBody b = new TumbleBody(w, G, R);
		// ran north into the wall: its normal points south
		b.start(0, 0, 0, 0f, 1400f, 0f, true, 0f, -1f, 0f);
		runToRest(b, 3f);
		assertTrue("rebounded south: y " + b.getY(), b.getY() < -20f);
		// low speed: it stays near the wall
		assertTrue(b.getY() > -3 * 128f);
		// backward, onto the back
		assertTrue("fell backward: " + b.getAngle(), b.getAngle() < 0f);
		assertEquals(-Math.PI / 2, b.getAngle(), 0.05);
	}

	@Test
	public void fallingOffALedgeLandsBelow()
	{
		CollisionWorld w = TestWorlds.stepAtY(60f, 0f, -300f);
		TumbleBody b = new TumbleBody(w, G, R);
		b.start(0, 0, 0, 0f, 900f, 0f, false, 0f, 0f, 0f);
		runToRest(b, 4f);
		assertTrue(b.isAtRest());
		assertEquals(-300f, b.getH(), 0f);
	}

	@Test
	public void aBailInTheAirFallsToTheGround()
	{
		TumbleBody b = new TumbleBody(TestWorlds.flat(), G, R);
		b.start(0, 0, 200f, 0f, 600f, 0f, false, 0f, 0f, 0f);
		assertTrue(b.isAirborne());
		runToRest(b, 4f);
		assertEquals(0f, b.getH(), 0f);
	}

	@Test
	public void staysInsideTheLoadedArea()
	{
		CollisionWorld w = TestWorlds.edgeAtY(100f);
		TumbleBody b = flung(w, 2600f, 0f);
		runToRest(b, 4f);
		assertTrue(b.getY() < 100f);
	}

	@Test
	public void restsHeldAtRest()
	{
		TumbleBody b = flung(TestWorlds.flat(), 900f, 0f);
		runToRest(b, 3f);
		float x = b.getX();
		float y = b.getY();
		float a = b.getAngle();
		for (int i = 0; i < 50; i++)
		{
			b.step(DT);
		}
		assertEquals(x, b.getX(), 0f);
		assertEquals(y, b.getY(), 0f);
		assertEquals(a, b.getAngle(), 1e-4f);
	}

	@Test
	public void stopNowEndsTheSkid()
	{
		TumbleBody b = flung(TestWorlds.flat(), 1500f, 0f);
		b.step(DT);
		b.stop();
		assertEquals(0f, b.getSpeed(), 0f);
	}

	@Test
	public void theGroundOffsetKeepsTheLowestPointOnTheGround()
	{
		assertEquals(0f, TumbleBody.groundOffset(0f), 1e-3f);
		assertEquals(0f, TumbleBody.groundOffset((float) (2 * Math.PI)), 1e-3f);
		// lying: the centre of mass comes down to the body's half thickness
		float lying = TumbleBody.groundOffset((float) (Math.PI / 2));
		assertTrue("lying drops the body: " + lying, lying < -20f);
		assertEquals(lying, TumbleBody.groundOffset((float) (-Math.PI / 2)), 1e-3f);
		// upside down the head would be under the feet' line: raised
		assertTrue(TumbleBody.groundOffset((float) Math.PI) > 0f);
	}
}
