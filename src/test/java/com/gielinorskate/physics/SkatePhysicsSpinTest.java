package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Air spins: the accumulated heading change from take-off to landing is named in half turns. */
public class SkatePhysicsSpinTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(speed);
		return p;
	}

	/** Crouches for 0.4 s (full charge: vh 820, hang time 0.82 s) then pops with `g`. */
	private static void chargedPop(SkatePhysics p, SkateInput in, Gesture g)
	{
		in.crouch = true;
		for (int i = 0; i < 20; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(g);
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
	}

	/** Holds `steer` for `steps` air steps, then lets go and runs to touchdown; returns the trick events. */
	private static List<TrickEvent> spinAndLand(SkatePhysics p, SkateInput in, float steer, int steps)
	{
		in.steer = steer;
		for (int i = 0; i < steps; i++)
		{
			p.step(DT, in);
		}
		in.steer = 0f;
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		return p.drainTrickEvents();
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

	@Test
	public void aClockwiseHalfTurnLandsAsABackside180()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedPop(p, in, new Gesture(Direction.UP, false, 0f));
		// 0.4 s at 7 rad/s = 160 degrees, then the spin assist turns the last 20 onto travel + 180
		List<TrickEvent> events = spinAndLand(p, in, 1f, 20);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.spin(1)), events.subList(0, 2));
		assertEquals(TrickEvent.Type.LANDED, events.get(2).type);
		assertTrue(p.getSpeed() < 0f); // landed fakie
	}

	@Test
	public void aCounterClockwiseHalfTurnIsAFrontside180()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedPop(p, in, new Gesture(Direction.UP_LEFT, false, 0f));
		List<TrickEvent> events = spinAndLand(p, in, -1f, 20);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.KICKFLIP), TrickEvent.spin(-1)), events.subList(0, 2));
		assertEquals(TrickEvent.Type.LANDED, events.get(2).type);
	}

	@Test
	public void aSmallTurnAssistedBackIsNoSpin()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedPop(p, in, new Gesture(Direction.UP, false, 0f));
		// 0.15 s at 7 rad/s = 60 degrees, assisted back onto the travel
		List<TrickEvent> events = spinAndLand(p, in, 1f, 8);
		assertEquals(Arrays.asList(TrickEvent.Type.TRICK, TrickEvent.Type.LANDED), types(events));
	}

	@Test
	public void airSpinIsTheSignedHeadingChangeSinceTakeOff()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedPop(p, in, new Gesture(Direction.UP, false, 0f));
		assertEquals(0f, p.airSpin, 1e-6f);
		in.steer = -1f;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		// 10 steps of 7 * 0.02 = 0.14 rad, counter-clockwise
		assertEquals(-1.4f, p.airSpin, 1e-3f);
	}

	@Test
	public void aSpinHeldIntoTheTakeOffIsNotCountedUntilRepressed()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		in.steer = 1f; // carving into the pop
		chargedPop(p, in, new Gesture(Direction.UP, false, 0f));
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		assertEquals(0f, p.airSpin, 1e-6f);
	}

	@Test
	public void aRollOffWithASpinIsATrick()
	{
		// rolling south off a 300-high platform: sqrt(2 * 300 / 2000) = 0.55 s of fall, enough for a 180
		SkatePhysics p = new SkatePhysics(t, TestWorlds.platformAtY(10f, 300f), 0, 20, (float) Math.PI);
		p.setRollingSpeed(600);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 20 && p.getState() != SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		List<TrickEvent> events = spinAndLand(p, in, 1f, 20);
		assertFalse(events.isEmpty());
		assertEquals(TrickEvent.spin(1), events.get(0));
	}
}
