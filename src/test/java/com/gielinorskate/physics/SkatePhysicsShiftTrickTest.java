package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Shift-modified flicks: impossibles, hardflips, inward heelflips and bigspins. */
public class SkatePhysicsShiftTrickTest
{
	static final float DT = 0.02f;
	static final float TWO_PI = (float) (2 * Math.PI);
	static final float PI = (float) Math.PI;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(speed);
		return p;
	}

	/** Crouches for 0.4 s (full charge: hang time 0.82 s) then pops with a Shift-held flick. */
	private static void chargedShiftPop(SkatePhysics p, SkateInput in, Direction d, boolean nollie)
	{
		in.crouch = true;
		for (int i = 0; i < 20; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(d, nollie, 0f, true));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
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
	public void anImpossibleWrapsTheBoardEndOverEnd()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.UP, false);
		steps(p, in, 5);
		assertTrue("nose comes up first", p.getBoardPitch() > 0.5f);
		steps(p, in, 16); // 0.42 s > 0.40 s
		assertEquals(TWO_PI, p.getBoardPitch(), 1e-4f);
		assertEquals(0f, p.getBoardRoll(), 1e-6f);
		assertEquals(0f, p.getBoardYawOffset(), 1e-6f);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.IMPOSSIBLE), TrickEvent.landed(true)), land(p, in));
		assertEquals(0f, p.getBoardPitch(), 1e-6f);
	}

	@Test
	public void aHardflipFlipsAndShovesFrontside()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.UP_LEFT, false);
		steps(p, in, 21);
		assertEquals(TWO_PI, p.getBoardRoll(), 1e-4f);
		assertEquals(-PI, p.getBoardYawOffset(), 1e-4f);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.HARDFLIP), TrickEvent.landed(true)), land(p, in));
	}

	@Test
	public void anInwardHeelflipFlipsAndShovesBackside()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.UP_RIGHT, false);
		steps(p, in, 21);
		assertEquals(-TWO_PI, p.getBoardRoll(), 1e-4f);
		assertEquals(PI, p.getBoardYawOffset(), 1e-4f);
	}

	@Test
	public void nollieShiftFlipsAreNollieHardflipsAndInwardHeels()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.DOWN_LEFT, true);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.NOLLIE_HARDFLIP), TrickEvent.landed(true)), land(p, in));

		SkatePhysics q = rolling(600);
		SkateInput in2 = new SkateInput();
		chargedShiftPop(q, in2, Direction.DOWN_RIGHT, true);
		assertEquals(TrickEvent.trick(Trick.NOLLIE_INWARD_HEELFLIP), land(q, in2).get(0));
	}

	@Test
	public void aBigspinTurnsTheBodyHalfwayWithTheBoardAndLandsFakieWithNoExtraSpinName()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.LEFT, false);
		steps(p, in, 24); // 0.48 s > 0.45 s
		// the body has turned 180 clockwise; the board a whole 360 in the world, i.e. 180 past the body
		assertEquals(PI, Math.abs(p.getHeading()), 1e-3f);
		assertEquals(PI, p.airSpin, 1e-3f);
		assertEquals(PI, p.getBoardYawOffset(), 1e-3f);
		List<TrickEvent> events = land(p, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("landed fakie", p.getSpeed() < 0f);
		// the body 180 is part of the bigspin: no "BS 180" on top
		assertEquals(TrickEvent.trick(Trick.BIGSPIN), events.get(0));
		assertEquals(TrickEvent.Type.LANDED, events.get(1).type);
		assertEquals(2, events.size());
	}

	@Test
	public void aFrontsideBigspinTurnsTheOtherWay()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.RIGHT, false);
		steps(p, in, 24);
		assertEquals(-PI, p.airSpin, 1e-3f);
		assertEquals(-PI, p.getBoardYawOffset(), 1e-3f);
		List<TrickEvent> events = land(p, in);
		assertEquals(TrickEvent.trick(Trick.FS_BIGSPIN), events.get(0));
		assertEquals(TrickEvent.Type.LANDED, events.get(1).type);
	}

	@Test
	public void boardYawDuringABigspinIsTheBoardsTurnLessTheBodys()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		chargedShiftPop(p, in, Direction.LEFT, false);
		steps(p, in, 5);
		// world board yaw (heading + offset) is the whole eased 360 turn: offset = 2 pi e - pi e = pi e
		float e = p.airSpin / PI;
		assertTrue(e > 0.2f && e < 0.9f);
		assertEquals(PI * e, p.getBoardYawOffset(), 1e-3f);
	}
}
