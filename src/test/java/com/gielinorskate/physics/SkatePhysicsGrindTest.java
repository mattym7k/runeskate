package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.world.GridCollisionWorld;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Grinds on flat ground. An uncharged pop (vh = 820 * 0.92 = 754.4) peaks at 754.4^2 / (2 * 2000) = 142 after
 * about 0.38 s. Lock-on needs h in [top - 16, top + 48] = [14, 78] for a top-30 rail, rising or falling, and
 * within 44 units horizontally (the closest point is clamped to the rail, so its end counts too). Rising,
 * h passes 14 about 3 steps after the pop, so a rail alongside the skater from y = 50 is caught almost
 * at once. Falling back through 78 happens about 0.63 s after the pop, near y = 315 heading north at
 * 500 u/s: rails that start at y = 300 are only reached on the way down.
 */
public class SkatePhysicsGrindTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static GrindMap rail(float x0, float y0, float x1, float y1, float top)
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(x0, y0, x1, y1, top));
		return m;
	}

	/** A rail 10 units east of the skater, from y = 50 north to endY, top 30. */
	private static GrindMap northRail(float endY)
	{
		return rail(10, 50, 10, endY, 30);
	}

	private SkatePhysics rolling(GrindMap grinds, float speed, float heading)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), grinds, 0, 0, heading);
		p.setSpeed(speed);
		return p;
	}

	private static void pop(SkatePhysics p, SkateInput in)
	{
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
	}

	private static void stepWhile(SkatePhysics p, SkateInput in, SkaterState s)
	{
		for (int i = 0; i < 1000 && p.getState() == s; i++)
		{
			p.step(DT, in);
		}
	}

	private static List<TrickEvent.Type> types(List<TrickEvent> events)
	{
		List<TrickEvent.Type> out = new ArrayList<>();
		for (TrickEvent e : events)
		{
			out.add(e.type);
		}
		return out;
	}

	/** Pops and flies onto the rail; returns the trick events so far. */
	private static List<TrickEvent> ollieOn(SkatePhysics p, SkateInput in)
	{
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.GRINDING, p.getState());
		return p.drainTrickEvents();
	}

	@Test
	public void ollieOntoRailLocksIntoFiftyFifty()
	{
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		List<TrickEvent> ev = ollieOn(p, in);

		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.holdStart(Trick.FIFTY_FIFTY)), ev);
		assertEquals(Trick.FIFTY_FIFTY, p.getActiveHold());
		assertEquals(30f, p.getH(), 1e-4f);
		assertEquals(10f, p.getX(), 1e-3f);
		assertEquals(0f, p.getHeading(), 1e-4f);
		assertTrue(p.getSpeed() > 450f);

		float y = p.getY();
		p.step(DT, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertTrue("moves north along the rail", p.getY() > y + 9f);
		assertEquals(10f, p.getX(), 1e-3f);
		assertEquals(30f, p.getH(), 1e-4f);
	}

	@Test
	public void wHeldGivesNosegrindAndSHeldGivesFiveO()
	{
		SkatePhysics nose = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		ollieOn(nose, in);
		assertEquals(Trick.NOSEGRIND, nose.getActiveHold());

		SkatePhysics five = rolling(northRail(1500), 500, 0);
		SkateInput in2 = new SkateInput();
		in2.leanBack = true;
		ollieOn(five, in2);
		assertEquals(Trick.FIVE_O, five.getActiveHold());
	}

	@Test
	public void wHeldWhileGrindingDoesNotPush()
	{
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		p.drainEvents();
		float s = p.getSpeed();
		in.pushHeld = true;
		in.pushPressed = true;
		p.step(DT, in);
		assertFalse(p.drainEvents().contains(SkateEvent.PUSH));
		assertTrue(p.getSpeed() < s);
	}

	@Test
	public void switchingKeysMidGrindSwitchesTheStraightGrind()
	{
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		in.pushHeld = true;
		p.step(DT, in);
		assertEquals(Trick.NOSEGRIND, p.getActiveHold());
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(Arrays.asList(TrickEvent.Type.HOLD_END, TrickEvent.Type.HOLD_START), types(ev));
		assertEquals(Trick.FIFTY_FIFTY, ev.get(0).trick);
		assertEquals(Trick.NOSEGRIND, ev.get(1).trick);
	}

	@Test
	public void seventyDegreeBoardAngleGivesBoardslideAndKeepsHeading()
	{
		// the rail starts at y = 300 so the skater cannot lock on while rising, before the spin is done
		SkatePhysics p = rolling(rail(10, 300, 10, 1500, 30), 500, 0);
		SkateInput in = new SkateInput();
		pop(p, in);
		// spin 9 steps at 7 rad/s: 9 * 0.14 = 1.26 rad (72 deg), outside the 70 deg spin-assist window
		in.steer = 1f;
		for (int i = 0; i < 9; i++)
		{
			p.step(DT, in);
		}
		in.steer = 0f;
		float heading = p.getHeading();
		assertEquals(72.2f, (float) Math.toDegrees(heading), 0.5f);
		stepWhile(p, in, SkaterState.AIRBORNE);

		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(Trick.BOARDSLIDE, p.getActiveHold());
		assertEquals(heading, p.getHeading(), 1e-4f);
		p.step(DT, in);
		assertEquals(heading, p.getHeading(), 1e-4f);
	}

	@Test
	public void fortyDegreeRailGivesCrookedGrind()
	{
		// rail through (0, 300) along 40 deg, so it passes the y = 265 lock point (class doc) within
		// (300 - 265) * sin 40 = 22 units < SNAP_DISTANCE 28; the skater travels north with heading 0
		float sx = (float) Math.sin(Math.toRadians(40)) * 300;
		float sy = (float) Math.cos(Math.toRadians(40)) * 300;
		SkatePhysics p = rolling(rail(-sx, 300 - sy, sx, 300 + sy, 30), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		assertEquals(Trick.CROOKED, p.getActiveHold());
	}

	@Test
	public void endOfRailExitsToAirborneThenLands()
	{
		SkatePhysics p = rolling(northRail(300), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		stepWhile(p, in, SkaterState.GRINDING);

		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.getY() >= 300f);
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(Arrays.asList(TrickEvent.Type.HOLD_END), types(ev));
		assertEquals(Trick.FIFTY_FIFTY, ev.get(0).trick);
		assertNull(p.getActiveHold());

		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.landed(true)), p.drainTrickEvents());
		assertTrue(p.getSpeed() > 300f);
	}

	@Test
	public void grindLongerThanOneSecondReportsItsLength()
	{
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		stepWhile(p, in, SkaterState.GRINDING);
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(0).type);
		assertTrue("grind length " + ev.get(0).seconds, ev.get(0).seconds >= 1f);
	}

	@Test
	public void droppingBelowMinimumSpeedPopsOff()
	{
		SkatePhysics p = rolling(northRail(1500), 200, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		// lock-on lifts the rail speed to at least 300; 60 u/s^2 friction then takes (300 - 100) / 60
		// = 3.3 s, about 50 + 300 * 3.3 - 30 * 3.3^2 = 717 units, to drop below the 100 u/s leave speed
		assertEquals(300f, p.getSpeed(), 2f);
		stepWhile(p, in, SkaterState.GRINDING);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.getY() < 1500f);
		assertEquals(TrickEvent.Type.HOLD_END, p.drainTrickEvents().get(0).type);
	}

	@Test
	public void flipGestureWhileGrindingPopsOffWithTheFlip()
	{
		// locks around y = 265 (see class doc), grinds 10 steps (100 units), flicks before the y = 450 end
		SkatePhysics p = rolling(northRail(450), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		p.drainEvents();
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.POP));
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(Arrays.asList(TrickEvent.Type.HOLD_END, TrickEvent.Type.TRICK), types(ev));
		assertEquals(Trick.KICKFLIP, ev.get(1).trick);

		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.landed(true)), p.drainTrickEvents());
	}

	@Test
	public void flipOutOfALongRailWithSteerLeavesItAndLands()
	{
		// review P1: with no steer a flip out comes back down onto the same rail (a hop along it, see
		// SkatePhysicsGrindExitTest); steering off carries the skater 200 * 0.75 = 151 units clear of it
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		in.steer = 1f;
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		p.step(DT, in);
		in.steer = 0f;
		p.drainTrickEvents();
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.landed(true)), p.drainTrickEvents());
		assertEquals(0f, p.getBoardRoll(), 0f);
	}

	@Test
	public void steeringWhilePoppingOffARailPushesSideways()
	{
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		in.steer = 1f;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		in.steer = 0f;
		for (int i = 0; i < 5; i++)
		{
			p.step(DT, in);
		}
		// the pop step itself does not move; then 200 u/s east for 5 steps of 0.02 s = 20 units
		assertEquals(10f + 20f, p.getX(), 1f);
	}

	@Test
	public void popOffOneRailCanStillLockOntoTheNextOne()
	{
		// first rail locks around y = 265 (class doc); the uncharged pop off it flies 0.7 s * 500 = 350
		// units, so the second rail starts at 460 and lands it well inside
		GrindMap m = northRail(450);
		m.add(new GrindSegment(10, 460, 10, 1500, 30));
		SkatePhysics p = rolling(m, 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		for (int i = 0; i < 5; i++)
		{
			p.step(DT, in);
		}
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.GRINDING, p.getState());
		// the 44-unit snap distance can catch the second rail right at its (10, 460) end
		assertTrue("on the second rail: y " + p.getY(), p.getY() >= 460f);
		assertEquals(10f, p.getX(), 1e-3f);
	}

	@Test
	public void seventyDegreeBoardslideRidesOffTheRailEndAndLands()
	{
		// starts at y = 300: no lock while rising, before the spin is done
		SkatePhysics p = rolling(rail(10, 300, 10, 600, 30), 500, 0);
		SkateInput in = new SkateInput();
		pop(p, in);
		in.steer = 1f;
		for (int i = 0; i < 9; i++)
		{
			p.step(DT, in);
		}
		in.steer = 0f;
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(Trick.BOARDSLIDE, p.getActiveHold());
		stepWhile(p, in, SkaterState.GRINDING);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.drainTrickEvents().contains(TrickEvent.landed(true)));
	}

	@Test
	public void rollingIntoALowCurbLocksOn()
	{
		SkatePhysics p = rolling(rail(10, 60, 10, 800, 10), 500, 0);
		SkateInput in = new SkateInput();
		stepWhile(p, in, SkaterState.ROLLING);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(10f, p.getH(), 1e-4f);
		assertEquals(Arrays.asList(TrickEvent.holdStart(Trick.FIFTY_FIFTY)), p.drainTrickEvents());
	}

	@Test
	public void rollingAlongALedgeAtYourOwnHeightDoesNotLock()
	{
		SkatePhysics p = rolling(rail(10, 60, 10, 800, 0), 500, 0);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void perpendicularApproachDoesNotLock()
	{
		SkatePhysics p = rolling(rail(-300, 110, 300, 110, 30), 500, 0);
		SkateInput in = new SkateInput();
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	@Test
	public void oldConstructorHasNoGrinds()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(500);
		SkateInput in = new SkateInput();
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	/**
	 * A 160-tall fence along the east edge of tile column 2 (x = 384), from y = 128 to y = 2048. Tall
	 * fences (GRIND_WALL_MAX 180) are rails and still walls.
	 */
	private static GridCollisionWorld tallFenceWorld()
	{
		GridCollisionWorld w = new GridCollisionWorld(20);
		for (int ty = 1; ty < 16; ty++)
		{
			w.setTile(2, ty, GridCollisionWorld.WALL_E, 160f);
		}
		w.rebuildGrinds();
		return w;
	}

	@Test
	public void chargedOllieLocksOntoA160TallFenceFromFlatGround()
	{
		// Full charge: vh = 820, peak 820^2 / 4000 = 168. Lock window for top 160 is [160 - 16, 160 + 48]
		// = [144, 208], so the skater locks on the way down from the peak, while still above 144:
		// falling 168 - 144 = 24 takes sqrt(2 * 24 / 2000) = 0.15 s, i.e. ~90 units at 600 u/s along the rail.
		GridCollisionWorld w = tallFenceWorld();
		SkatePhysics p = new SkatePhysics(t, w, w.getGrinds(), 374, 150, 0);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 20; i++) // 0.4 s > crouchChargeTime 0.20 s
		{
			p.step(DT, in);
		}
		in.crouch = false;
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(160f, p.getH(), 1e-4f);
		assertEquals(384f, p.getX(), 1e-3f);
	}

	@Test
	public void quickFlickNowReachesA160TallFenceThroughTheMagnet()
	{
		// Uncharged peak (0.92 * 820)^2 / 4000 = 142.3 (134.8 in 20 ms steps). The old window bottom was
		// 160 - 16 = 144, out of reach; the review P3 magnet locks from 160 - 32 = 128 while rising, so the
		// quick flick now catches it (snapping up at most 32)
		GridCollisionWorld w = tallFenceWorld();
		SkatePhysics p = new SkatePhysics(t, w, w.getGrinds(), 374, 150, 0);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(160f, p.getH(), 1e-4f);
	}

	@Test
	public void a160TallFenceStillBlocksASkaterWhoDoesNotClearIt()
	{
		// rolling east into the fence at x = 384: blocker top 160 > h 0 + maxStepUp 24
		GridCollisionWorld w = tallFenceWorld();
		SkatePhysics rollIn = new SkatePhysics(t, w, w.getGrinds(), 200, 600, (float) Math.PI / 2);
		rollIn.setSpeed(600);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 50; i++)
		{
			rollIn.step(DT, in);
		}
		assertTrue("x " + rollIn.getX(), rollIn.getX() < 384f);

		// a quick flick peaks at 134.8 in 20 ms steps, and 160 > 134.8 + 24, so it is stopped in the air too
		SkatePhysics hop = new SkatePhysics(t, w, w.getGrinds(), 200, 600, (float) Math.PI / 2);
		hop.setSpeed(600);
		pop(hop, in);
		for (int i = 0; i < 100 && hop.getState() != SkaterState.ROLLING; i++)
		{
			hop.step(DT, in);
		}
		assertTrue("x " + hop.getX(), hop.getX() < 384f);
	}

	@Test
	public void steepSeventyFiveDegreeOllieOverAFenceLocksAndSlidesAlongIt()
	{
		// fence along x = 100; heading 75 deg (east of north). Rising through the lock window: at
		// x = 100 - 44 = 56 (0.116 s at 500 * sin 75 = 483 u/s east) h = 754.4 * 0.116 - 1000 * 0.116^2 = 74,
		// inside [14, 78], so it locks before it is past the fence
		float heading = (float) Math.toRadians(75);
		SkatePhysics p = rolling(rail(100, -500, 100, 1500, 30), 500, heading);
		SkateInput in = new SkateInput();
		pop(p, in);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(Trick.BOARDSLIDE, p.getActiveHold());
		assertEquals(100f, p.getX(), 1e-3f);
		// speed redirected along the rail: max(500 cos 75 = 129, 0.6 * 500 = 300, 300) = 300, keeping the
		// northward sign of the along-rail component
		assertEquals(300f, p.getSpeed(), 2f);
		assertEquals(0f, p.getTravelHeading(), 1e-4f);
		float y = p.getY();
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(100f, p.getX(), 1e-3f);
		assertTrue("slides north along the fence", p.getY() > y + 50f);
	}

	@Test
	public void locksOnWhileStillRising()
	{
		// rail top 30 alongside: the window [14, 78] is reached about 3 steps after the pop, long before
		// the 0.38 s peak
		SkatePhysics p = rolling(northRail(1500), 500, 0);
		SkateInput in = new SkateInput();
		pop(p, in);
		for (int i = 0; i < 6 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(30f, p.getH(), 1e-4f);
	}

	@Test
	public void rollingStillOnlyLocksOntoRailsAboveTheRidingSurface()
	{
		SkatePhysics p = rolling(rail(10, 60, 10, 800, 3), 500, 0);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
	}

	/** Grinds from the ollie until the grind ends; returns the largest x reached while still grinding. */
	private static float grindOut(SkatePhysics p, SkateInput in)
	{
		float maxX = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < 1000 && p.getState() == SkaterState.GRINDING; i++)
		{
			maxX = Math.max(maxX, p.getX());
			p.step(DT, in);
		}
		return maxX;
	}

	@Test
	public void grindFollowsTheFenceAroundAGentleBend()
	{
		// north along x = 10 to y = 600, then a 45-degree bend north-east for 400 units, 5 units higher
		// and with a 6-unit gap between the pieces
		GrindMap m = northRail(600);
		float d = 400f * (float) Math.sqrt(0.5);
		m.add(new GrindSegment(14, 604, 14 + d, 604 + d, 35));
		SkatePhysics p = rolling(m, 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		float maxX = grindOut(p, in);

		assertTrue("followed the bend east: max x " + maxX, maxX > 14 + d - 20);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(Arrays.asList(TrickEvent.Type.HOLD_END), types(ev));
		assertEquals(Trick.FIFTY_FIFTY, ev.get(0).trick);
		assertTrue("one continuous grind: " + ev.get(0).seconds, ev.get(0).seconds > 0.6f);
	}

	@Test
	public void boardTurnsWithTheBend()
	{
		GrindMap m = northRail(600);
		float d = 400f * (float) Math.sqrt(0.5);
		m.add(new GrindSegment(10, 600, 10 + d, 600 + d, 30));
		SkatePhysics p = rolling(m, 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		for (int i = 0; i < 1000 && p.getState() == SkaterState.GRINDING && p.getX() < 60; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(45f, (float) Math.toDegrees(p.getTravelHeading()), 0.1f);
		assertEquals(45f, (float) Math.toDegrees(p.getHeading()), 0.1f);
	}

	@Test
	public void ninetyDegreeCornerEndsTheGrind()
	{
		GrindMap m = northRail(600);
		m.add(new GrindSegment(10, 600, 600, 600, 30));
		SkatePhysics p = rolling(m, 500, 0);
		SkateInput in = new SkateInput();
		ollieOn(p, in);
		float maxX = grindOut(p, in);
		assertEquals(10f, maxX, 1e-3f);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertTrue(p.getY() >= 600f);
		stepWhile(p, in, SkaterState.AIRBORNE);
		assertEquals(SkaterState.ROLLING, p.getState());
	}
}
