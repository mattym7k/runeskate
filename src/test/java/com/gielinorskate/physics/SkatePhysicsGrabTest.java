package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Grabs.Aim;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Directional grabs (the aim within 0.15 s of the press picks the grab), tweaks (held past 0.5 s) and grab-flips
 * (a lean key with a grab held turns a front / back flip). A fully charged ollie hangs 0.82 s.
 */
public class SkatePhysicsGrabTest
{
	static final float DT = 0.02f;
	static final float TWO_PI = (float) (2 * Math.PI);
	final SkateTuning t = new SkateTuning();

	private SkatePhysics airborne(SkateInput in)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(500);
		in.crouch = true;
		steps(p, in, 20);
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		p.drainTrickEvents();
		return p;
	}

	private static void steps(SkatePhysics p, SkateInput in, int n)
	{
		for (int i = 0; i < n; i++)
		{
			p.step(DT, in);
		}
	}

	private static List<TrickEvent> land(SkatePhysics p, SkateInput in)
	{
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		return p.drainTrickEvents();
	}

	@Test
	public void unaimedGrabsAreTodaysIndyAndMelon()
	{
		for (boolean left : new boolean[]{true, false})
		{
			SkateInput in = new SkateInput();
			SkatePhysics p = airborne(in);
			in.grabLeft = left;
			in.grabRight = !left;
			steps(p, in, 10); // 0.2 s, past the aim window
			Trick want = left ? Trick.INDY : Trick.MELON;
			assertEquals(want, p.getActiveHold());
			in.grabLeft = in.grabRight = false;
			p.step(DT, in);
			assertEquals(Arrays.asList(TrickEvent.holdStart(want), TrickEvent.holdEnd(want, 0.2f)),
				roundSeconds(p.drainTrickEvents()));
		}
	}

	@Test
	public void anAimAtThePressPicksTheGrabStraightAway()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		in.grabAim = Aim.TOE;
		p.step(DT, in);
		assertEquals(Trick.MUTE, p.getActiveHold());
		assertEquals(Arrays.asList(TrickEvent.holdStart(Trick.MUTE)), p.drainTrickEvents());
	}

	@Test
	public void anAimWithinTheWindowRenamesTheGrab()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabRight = true;
		steps(p, in, 5); // 0.1 s
		assertEquals(Trick.MELON, p.getActiveHold());
		in.grabAim = Aim.NOSE;
		p.step(DT, in);
		assertEquals(Trick.CRAIL, p.getActiveHold());
		// a later change of aim does not move it again
		in.grabAim = Aim.TAIL;
		steps(p, in, 3);
		assertEquals(Trick.CRAIL, p.getActiveHold());
		in.grabRight = false;
		p.step(DT, in);
		assertEquals(Arrays.asList(TrickEvent.holdStart(Trick.MELON), TrickEvent.holdStart(Trick.CRAIL),
			TrickEvent.holdEnd(Trick.CRAIL, 0.18f)), roundSeconds(p.drainTrickEvents()));
	}

	@Test
	public void anAimAfterTheWindowIsIgnored()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		steps(p, in, 9); // 0.18 s
		in.grabAim = Aim.NOSE;
		steps(p, in, 2);
		assertEquals(Trick.INDY, p.getActiveHold());
	}

	@Test
	public void holdingPastHalfASecondTweaksTheGrabOnce()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabRight = true;
		steps(p, in, 25); // 0.5 s: not past it yet
		assertEquals(Trick.MELON, p.getActiveHold());
		p.step(DT, in);
		assertEquals(Trick.METHOD, p.getActiveHold());
		steps(p, in, 5);
		assertEquals(Trick.METHOD, p.getActiveHold());
		in.grabRight = false;
		p.step(DT, in);
		assertEquals(Arrays.asList(TrickEvent.holdStart(Trick.MELON), TrickEvent.holdStart(Trick.METHOD),
			TrickEvent.holdEnd(Trick.METHOD, 0.62f)), roundSeconds(p.drainTrickEvents()));
	}

	@Test
	public void aTweakedGrabAutoReleasesOnTouchdown()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		in.grabAim = Aim.TAIL;
		List<TrickEvent> ev = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(TrickEvent.holdStart(Trick.TAILGRAB), ev.get(0));
		assertEquals(TrickEvent.holdStart(Trick.TAILBONE), ev.get(1));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(2).type);
		assertEquals(Trick.TAILBONE, ev.get(2).trick);
	}

	@Test
	public void leanForwardWithAGrabIsAFrontflip()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		in.leanForwardKey = true;
		steps(p, in, 27); // 0.54 s: nearly a whole turn
		assertEquals(TWO_PI * 27 * DT / 0.55f, p.getBodyFlipAngle(), 1e-3f);
		in.leanForwardKey = false;
		List<TrickEvent> ev = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(ev.toString(), ev.contains(TrickEvent.trick(Trick.FRONTFLIP)));
	}

	@Test
	public void leanBackWithAGrabIsABackflip()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabRight = true;
		in.leanBackKey = true;
		steps(p, in, 5);
		assertEquals(-TWO_PI * 5 * DT / 0.55f, p.getBodyFlipAngle(), 1e-3f);
	}

	@Test
	public void leanKeysWithoutAGrabDoNothing()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.leanForwardKey = true;
		steps(p, in, 10);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void aLeanKeyHeldAtTakeOffCountsOnlyOncePressedAgain()
	{
		SkateInput in = new SkateInput();
		in.leanForwardKey = true; // held into the pop
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		steps(p, in, 5);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
		in.leanForwardKey = false;
		p.step(DT, in);
		in.leanForwardKey = true;
		steps(p, in, 5);
		assertEquals(TWO_PI * 5 * DT / 0.55f, p.getBodyFlipAngle(), 1e-3f);
	}

	@Test
	public void releasingTheGrabStopsTheGrabFlip()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.grabLeft = true;
		in.leanForwardKey = true;
		steps(p, in, 5);
		in.grabLeft = false;
		float before = p.getBodyFlipAngle();
		p.step(DT, in);
		// no longer driven: it stops where it is (65 degrees: outside the 60 degree assist)
		assertEquals(before, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void shiftWStillFlipsWithoutAGrab()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = airborne(in);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 5);
		assertEquals(TWO_PI * 5 * DT / 0.55f, p.getBodyFlipAngle(), 1e-3f);
	}

	/** The events with HOLD_END seconds rounded to 0.01 (they are sums of float steps). */
	private static List<TrickEvent> roundSeconds(List<TrickEvent> events)
	{
		for (int i = 0; i < events.size(); i++)
		{
			TrickEvent e = events.get(i);
			if (e.type == TrickEvent.Type.HOLD_END)
			{
				events.set(i, TrickEvent.holdEnd(e.trick, Math.round(e.seconds * 100f) / 100f));
			}
		}
		return events;
	}
}
