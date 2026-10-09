package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Grabs.Aim;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.List;
import org.junit.Test;

/**
 * The rolling grab: Q / E held while rolling crouches and grabs the board on the ground. Pose only: no trick events,
 * no points, no push while held; a pop lets go and the held key grabs again in the air, and landing with the key
 * still held settles back into it.
 */
public class SkatePhysicsRollingGrabTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(400);
		return p;
	}

	private static void steps(SkatePhysics p, SkateInput in, int n)
	{
		for (int i = 0; i < n; i++)
		{
			p.step(DT, in);
		}
	}

	@Test
	public void holdingAGrabKeyWhileRollingGrabsTheBoardWithNoTrick()
	{
		for (boolean left : new boolean[]{true, false})
		{
			SkateInput in = new SkateInput();
			SkatePhysics p = rolling();
			in.grabLeft = left;
			in.grabRight = !left;
			steps(p, in, 30);
			assertEquals(SkaterState.ROLLING, p.getState());
			assertEquals(left ? Trick.INDY : Trick.MELON, p.getGroundGrab());
			assertEquals(left, p.isGrabLeftHand());
			// not a hold: nothing to score, nothing for the combo
			assertNull(p.getActiveHold());
			assertTrue(p.drainTrickEvents().isEmpty());
			in.grabLeft = in.grabRight = false;
			p.step(DT, in);
			assertNull(p.getGroundGrab());
			assertTrue(p.drainTrickEvents().isEmpty());
		}
	}

	@Test
	public void theAimPicksTheRollingGrabLikeInTheAir()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.grabRight = true;
		steps(p, in, 3);
		assertEquals(Trick.MELON, p.getGroundGrab());
		in.grabAim = Aim.NOSE;
		p.step(DT, in);
		assertEquals(Trick.CRAIL, p.getGroundGrab());
		// a rolling grab never tweaks
		steps(p, in, 50);
		assertEquals(Trick.CRAIL, p.getGroundGrab());
		in.grabRight = false;
		in.grabAim = null;
		in.grabLeft = true;
		p.step(DT, in);
		p.step(DT, in);
		in.grabAim = Aim.TAIL;
		p.step(DT, in);
		assertEquals(Trick.TAILGRAB, p.getGroundGrab());
		assertTrue(p.drainTrickEvents().isEmpty());
	}

	@Test
	public void noPushWhileGrabbingAndWDoesNotLetGo()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.grabLeft = true;
		steps(p, in, 5);
		p.drainEvents();
		float before = p.getSpeed();
		in.pushPressed = true;
		in.pushHeld = true;
		steps(p, in, 40);
		assertFalse(p.drainEvents().contains(SkateEvent.PUSH));
		assertTrue("no push speed: " + p.getSpeed(), p.getSpeed() < before);
		assertEquals(Trick.INDY, p.getGroundGrab());
		// let go with W still held: pushing again
		in.grabLeft = false;
		steps(p, in, 2);
		assertNull(p.getGroundGrab());
		assertTrue(p.drainEvents().contains(SkateEvent.PUSH));
	}

	@Test
	public void theRollingGrabDoesNotChangeTheRoll()
	{
		SkateInput plain = new SkateInput();
		SkateInput grab = new SkateInput();
		grab.grabRight = true;
		SkatePhysics a = rolling();
		SkatePhysics b = rolling();
		plain.steer = grab.steer = 0.6f;
		steps(a, plain, 60);
		steps(b, grab, 60);
		assertEquals(a.getSpeed(), b.getSpeed(), 1e-4f);
		assertEquals(a.getHeading(), b.getHeading(), 1e-5f);
		assertEquals(a.getX(), b.getX(), 1e-3f);
		assertEquals(a.getY(), b.getY(), 1e-3f);
	}

	@Test
	public void aManualWinsOverTheRollingGrab()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.grabLeft = true;
		steps(p, in, 3);
		in.manualHeld = true;
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.MANUAL, p.getActiveHold());
		assertNull(p.getGroundGrab());
	}

	@Test
	public void aPopLetsGoAndTheHeldKeyGrabsAgainInTheAirAndScores()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.grabLeft = true;
		in.grabAim = Aim.TOE;
		steps(p, in, 10);
		assertEquals(Trick.MUTE, p.getGroundGrab());
		assertTrue(p.drainTrickEvents().isEmpty());
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertNull(p.getGroundGrab());
		p.step(DT, in);
		assertEquals(Trick.MUTE, p.getActiveHold());
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(TrickEvent.trick(Trick.OLLIE, false), ev.get(0));
		assertEquals(TrickEvent.holdStart(Trick.MUTE), ev.get(1));
	}

	@Test
	public void landingWithTheKeyHeldReleasesTheAirGrabThenSettlesIntoTheRollingGrab()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.crouch = true;
		steps(p, in, 20);
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		p.drainTrickEvents();
		in.grabRight = true;
		in.grabAim = Aim.HEEL;
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		List<TrickEvent> ev = p.drainTrickEvents();
		// the air grab scores as always: held, tweaked, released at touchdown, landed
		assertEquals(TrickEvent.holdStart(Trick.STALEFISH), ev.get(0));
		assertEquals(TrickEvent.holdStart(Trick.TWEAKED_STALEFISH), ev.get(1));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(2).type);
		assertEquals(TrickEvent.Type.LANDED, ev.get(3).type);
		assertEquals(4, ev.size());
		assertNull(p.getActiveHold());
		// the same grab carries on, on the ground, at once
		assertEquals(Trick.TWEAKED_STALEFISH, p.getGroundGrab());
		steps(p, in, 20);
		assertEquals(Trick.TWEAKED_STALEFISH, p.getGroundGrab());
		assertTrue(p.drainTrickEvents().isEmpty());
	}

	@Test
	public void landingIntoAManualWithTheKeyHeldIsAManual()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		in.grabLeft = true;
		in.manualHeld = true;
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.MANUAL, p.getState());
		assertNull(p.getGroundGrab());
	}

	@Test
	public void airGrabScoringIsUnchangedWhenTheKeyIsLetGoBeforeTouchdown()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = rolling();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		p.drainTrickEvents();
		in.grabRight = true;
		steps(p, in, 5);
		in.grabRight = false;
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(TrickEvent.holdStart(Trick.MELON), ev.get(0));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(TrickEvent.Type.LANDED, ev.get(2).type);
		assertNull(p.getGroundGrab());
	}
}
