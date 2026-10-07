package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Front and back flips: Shift + W (forward) or Shift + S (backward) held in the air turns the whole skater at
 * 2 pi / 0.55 rad/s. A fully charged pop hangs 2 * 820 / 2000 = 0.82 s, room for one flip.
 */
public class SkatePhysicsBodyFlipTest
{
	static final float DT = 0.02f;
	static final float TWO_PI = (float) (2 * Math.PI);
	static final float RATE = TWO_PI / 0.55f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(speed);
		return p;
	}

	/** Crouches for 0.4 s (full charge) then ollies, in steps of `dt`. */
	private static void chargedOllie(SkatePhysics p, SkateInput in, float dt)
	{
		in.crouch = true;
		for (float s = 0f; s < 0.4f - 1e-4f; s += dt)
		{
			p.step(dt, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(dt, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
	}

	private static void steps(SkatePhysics p, SkateInput in, int n, float dt)
	{
		for (int i = 0; i < n; i++)
		{
			p.step(dt, in);
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
	public void holdingShiftAndWForPoint55SecondsIsOneFrontflip()
	{
		float dt = 0.01f;
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, dt);
		in.powerslide = true;
		in.pushHeld = true; // pressed after take-off
		steps(p, in, 55, dt); // 0.55 s
		assertEquals(TWO_PI, p.getBodyFlipAngle(), 1e-3f);
		in.powerslide = false;
		in.pushHeld = false;
		assertEquals(SkaterState.AIRBORNE, p.getState());
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.FRONTFLIP)), events.subList(0, 2));
		assertEquals(TrickEvent.Type.LANDED, events.get(2).type);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void shiftAndSIsABackflip()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, DT);
		in.powerslide = true;
		in.leanBack = true;
		steps(p, in, 5, DT);
		assertEquals(-5 * DT * RATE, p.getBodyFlipAngle(), 1e-4f);
		// 0.52 s held then released 20 degrees short: the assist turns the rest
		steps(p, in, 21, DT);
		in.powerslide = false;
		in.leanBack = false;
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(TrickEvent.trick(Trick.BACKFLIP), events.get(1));
	}

	@Test
	public void inControllerModeShiftAndLeanUpFlipsLikeShiftAndW()
	{
		float dt = 0.01f;
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.controllerMode = true;
		chargedOllie(p, in, dt);
		in.powerslide = true;
		in.leanForwardKey = true; // the left stick's up arrow, pressed after take-off
		steps(p, in, 55, dt); // 0.55 s: one whole turn
		assertEquals(TWO_PI, p.getBodyFlipAngle(), 1e-3f);
		in.powerslide = false;
		in.leanForwardKey = false;
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.FRONTFLIP)), events.subList(0, 2));
	}

	@Test
	public void inControllerModeShiftAndLeanDownIsABackflip()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.controllerMode = true;
		chargedOllie(p, in, DT);
		in.powerslide = true;
		in.leanBackKey = true;
		steps(p, in, 5, DT);
		assertEquals(-5 * DT * RATE, p.getBodyFlipAngle(), 1e-4f);
		// 0.52 s held then released 20 degrees short: the assist turns the rest, as shiftAndSIsABackflip
		steps(p, in, 21, DT);
		in.powerslide = false;
		in.leanBackKey = false;
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(TrickEvent.trick(Trick.BACKFLIP), events.get(1));
	}

	@Test
	public void leanUpAloneInControllerModeDoesNotFlipWithoutShift()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.controllerMode = true;
		chargedOllie(p, in, DT);
		in.leanForwardKey = true; // no Shift: just a lean, not a flip
		steps(p, in, 10, DT);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void shiftAndLeanUpOutsideControllerModeDoesNotFlip()
	{
		// off the pad, the lean keys are the real Up / Down arrows, not W / S: no body flip without controllerMode
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, DT);
		in.powerslide = true;
		in.leanForwardKey = true;
		steps(p, in, 10, DT);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void wAloneInTheAirDoesNotFlip()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, DT);
		in.pushHeld = true;
		steps(p, in, 10, DT);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
		in.pushHeld = false;
		in.leanBack = true;
		steps(p, in, 10, DT);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void wHeldSinceTakeOffIsIgnoredUntilPressedAgain()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.powerslide = true;
		in.pushHeld = true; // pushing (and Shift held) into the pop
		chargedOllie(p, in, DT);
		steps(p, in, 10, DT);
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
		in.pushHeld = false; // released...
		p.step(DT, in);
		in.pushHeld = true; // ...and pressed again, Shift still held from the ground
		p.step(DT, in);
		assertEquals(DT * RATE, p.getBodyFlipAngle(), 1e-5f);
	}

	@Test
	public void releasingStopsTheFlipWithAGentleAssistNearAWholeTurn()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, DT);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 5, DT); // 5 * 0.02 * 11.42 = 1.14 rad = 65 degrees
		in.pushHeld = false;
		float released = p.getBodyFlipAngle();
		steps(p, in, 3, DT);
		// no auto-continue: 65 degrees is outside the 60 degree assist window, so it stays put
		assertEquals(released, p.getBodyFlipAngle(), 0f);

		in.pushHeld = true;
		steps(p, in, 21, DT); // 26 steps: 0.52 s, 340 degrees
		in.pushHeld = false;
		float before = p.getBodyFlipAngle();
		p.step(DT, in);
		float after = p.getBodyFlipAngle();
		assertTrue("assisted toward 360", after > before);
		assertTrue("gently", after - before < 0.1f);
	}

	@Test
	public void landingHalfwayRoundBails()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedOllie(p, in, DT);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 14, DT); // 0.28 s * 654.5 deg/s = 183 degrees
		in.pushHeld = false;
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.BAILED, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.bailed()), events);
		assertEquals(BailReason.BODY_FLIP, p.getLastBailReason());
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void aBodyFlipAndABoardFlipAreSeparateTricks()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.crouch = true;
		steps(p, in, 20, DT);
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		p.step(DT, in);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 27, DT); // 0.54 s: 353 degrees, assisted the rest
		in.powerslide = false;
		in.pushHeld = false;
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.KICKFLIP), TrickEvent.trick(Trick.FRONTFLIP)), events.subList(0, 2));
		assertEquals(TrickEvent.Type.LANDED, events.get(2).type);
	}

	@Test
	public void noFlipsInAManual()
	{
		SkatePhysics p = rolling(800);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 10, DT);
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	private static GrindMap rail(float y0, float y1)
	{
		GrindMap m = new GrindMap();
		m.add(new GrindSegment(10, y0, 10, y1, 30));
		return m;
	}

	@Test
	public void noFlipsOnARail()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), rail(50, 1500), 0, 0, 0);
		p.setSpeed(500);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 10, DT);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	/** Uncharged ollie at 500 north toward a rail from y = 300, reached on the way down (see SkatePhysicsGrindTest). */
	private SkatePhysics towardLateRail(SkateInput in)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), rail(300, 1500), 0, 0, 0);
		p.setSpeed(500);
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		return p;
	}

	@Test
	public void theLateRailLocksWithoutAFlip()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = towardLateRail(in);
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
	}

	@Test
	public void aFrontflipFinishedOntoARailScoresBeforeTheGrind()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = towardLateRail(in);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 28, DT); // 0.56 s: 366 degrees
		in.pushHeld = false;
		in.powerslide = false;
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.GRINDING, p.getState());
		// the rail comes within reach once the flip is within 40 degrees of whole, while W is still held, so the
		// lock picks W's nosegrind
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.FRONTFLIP),
			TrickEvent.holdStart(Trick.NOSEGRIND)), p.drainTrickEvents());
		assertEquals(0f, p.getBodyFlipAngle(), 0f);
	}

	@Test
	public void noGrindLockHalfwayThroughAFlip()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = towardLateRail(in);
		in.powerslide = true;
		in.pushHeld = true;
		steps(p, in, 8, DT); // 105 degrees, then left there
		in.pushHeld = false;
		in.powerslide = false;
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.BAILED, p.getState());
	}
}
