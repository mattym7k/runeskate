package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Comfort fixes: the loaded-area edge scrapes, stumbles are announced, wall bails recover along the wall, R. */
public class SkatePhysicsComfortTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static float deg(float rad)
	{
		return (float) Math.toDegrees(rad);
	}

	private static List<SkateEvent> run(SkatePhysics p, SkateInput in, float seconds)
	{
		List<SkateEvent> events = new ArrayList<>();
		for (int i = 0; i < Math.round(seconds / DT); i++)
		{
			p.step(DT, in);
			events.addAll(p.drainEvents());
		}
		return events;
	}

	@Test
	public void headOnIntoTheLoadedAreaEdgeScrapesInsteadOfBailing()
	{
		// 1500 head-on would bail on any wall (1500 > wallBailSpeed 1100); the edge of the loaded area is no
		// real wall, so it stops the skater instead
		SkatePhysics p = new SkatePhysics(t, TestWorlds.edgeAtY(200), 0, 0, 0f);
		p.setSpeed(1500f);
		List<SkateEvent> events = run(p, new SkateInput(), 0.4f);
		assertFalse(events.contains(SkateEvent.BAIL));
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.getY() < 200f);
	}

	@Test
	public void theSameHitOnAnOrdinaryWallStillBails()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, Float.POSITIVE_INFINITY), 0, 0, 0f);
		p.setSpeed(1500f);
		assertTrue(run(p, new SkateInput(), 0.4f).contains(SkateEvent.BAIL));
	}

	@Test
	public void flyingIntoTheLoadedAreaEdgeDoesNotBail()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.edgeAtY(300), 0, 0, 0f);
		p.setSpeed(1500f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		List<SkateEvent> events = run(p, in, 1.2f);
		assertTrue(events.contains(SkateEvent.POP));
		assertFalse(events.contains(SkateEvent.BAIL));
	}

	@Test
	public void aHardHitStumblesOutLoud()
	{
		// 45 deg at 1000: impact 1000 * cos 45 = 707 > wallStumbleSpeed 600
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(45));
		p.setSpeed(1000f);
		List<SkateEvent> events = run(p, new SkateInput(), 0.1f);
		assertEquals(1, events.stream().filter(e -> e == SkateEvent.STUMBLE).count());
		assertFalse(events.contains(SkateEvent.BAIL));
	}

	@Test
	public void aLightHitIsNoStumble()
	{
		// 70 deg at 1000: impact 1000 * cos 70 = 342 < 600
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 95, (float) Math.toRadians(70));
		p.setSpeed(1000f);
		assertFalse(run(p, new SkateInput(), 0.1f).contains(SkateEvent.STUMBLE));
	}

	@Test
	public void aWallBailRecoversAlongTheWallNearestTheOldHeading()
	{
		// 20 deg at 1500: 1500 * cos 20 = 1410 > 1100 within 30 deg of head-on: a bail. Facing straight away
		// (180) swung the camera 160 degrees; along the wall to the east (90) it turns only 70.
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, (float) Math.toRadians(20));
		p.setSpeed(1500f);
		run(p, new SkateInput(), 0.2f);
		assertEquals(SkaterState.BAILED, p.getState());
		List<SkateEvent> events = run(p, new SkateInput(), t.bailDuration + t.bailAutoReset + 0.1f);
		assertTrue(events.contains(SkateEvent.RESET));
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(90f, deg(p.getHeading()), 0.01f);
		// rides off along the wall
		float x0 = p.getX();
		SkateInput in = new SkateInput();
		in.pushPressed = true;
		List<SkateEvent> after = run(p, in, 0.3f);
		assertFalse(after.contains(SkateEvent.BAIL));
		assertTrue("x " + p.getX(), p.getX() > x0 + 50f);
	}

	@Test
	public void aWallBailFromTheLeftRecoversToTheWest()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, (float) Math.toRadians(-20));
		p.setSpeed(1500f);
		run(p, new SkateInput(), 0.2f);
		run(p, new SkateInput(), t.bailDuration + t.bailAutoReset + 0.1f);
		assertEquals(-90f, deg(p.getHeading()), 0.01f);
	}

	@Test
	public void rWhileRollingSlowlyStandsTheSkaterStill()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setSpeed(100f);
		SkateInput in = new SkateInput();
		in.resetRequested = true;
		p.step(DT, in);
		assertEquals(0f, p.getSpeed(), 0f);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.RESET));
	}

	@Test
	public void rWhileRollingFastDoesNothing()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setSpeed(600f);
		SkateInput in = new SkateInput();
		in.resetRequested = true;
		p.step(DT, in);
		assertTrue(p.getSpeed() > 500f);
		assertFalse(p.drainEvents().contains(SkateEvent.RESET));
	}

	@Test
	public void startingFacingAWallTurnsToTheMostOpenWay()
	{
		// a wall 96 units ahead (under 1.5 tiles): face one of the wide open ways instead
		float h = StartHeading.choose(TestWorlds.wallAtY(96, 300), 0, 0, 0f, t.skaterRadius, t.maxStepUp);
		assertTrue("heading " + deg(h), Math.abs(deg(h)) >= 89.9f);
		// nearest the old heading among the equally open ways: east or west, not south
		assertEquals(90f, Math.abs(deg(h)), 0.01f);
	}

	@Test
	public void startingWithRoomAheadKeepsTheHeading()
	{
		float h = StartHeading.choose(TestWorlds.wallAtY(400, 300), 0, 0, 0.3f, t.skaterRadius, t.maxStepUp);
		assertEquals(0.3f, h, 0f);
	}
}
