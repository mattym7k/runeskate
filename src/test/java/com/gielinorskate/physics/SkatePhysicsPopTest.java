package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.input.MousePath;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Random;
import org.junit.Test;

/** Pop height (charge) and the full mouse-path-to-pop pipeline. */
public class SkatePhysicsPopTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setSpeed(speed);
		return p;
	}

	@Test
	public void chargeIsNotLostOnThePopStep()
	{
		// 18 steps (0.36 s) crouched, then the flick arrives with crouch already false (the recognizer
		// re-arms on the flick). The pop step must not decay crouchTime before reading it:
		// 0.36 s >= crouchChargeTime -> full charge -> vh = ollieImpulse = 820.
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 18; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(820f, p.getVerticalSpeed(), 1e-3f);
	}

	@Test
	public void crouchingForCrouchChargeTimeGivesAFullPop()
	{
		// crouchChargeTime 0.20 s = 10 steps of 0.02 s -> charge 1 -> vh 820
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(820f, p.getVerticalSpeed(), 1e-3f);
	}

	@Test
	public void uncrouchedPopIsMinPopFraction()
	{
		// 0.92 * 820 = 754.4
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(0.92f * 820f, p.getVerticalSpeed(), 1e-2f);
	}

	@Test
	public void nollieWindUpChargesWithoutCrouching()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.charge = true; // nollie wind-up: charging, but no crouch pose
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.charge = false;
		in.gestures.add(new Gesture(Direction.DOWN, true, 0f));
		p.step(DT, in);
		assertEquals(820f, p.getVerticalSpeed(), 1e-3f);
		assertTrue(p.isLastPopNollie());
	}

	@Test
	public void chargingAloneDoesNotBlockPushing()
	{
		SkatePhysics p = rolling(0);
		SkateInput in = new SkateInput();
		in.charge = true;
		in.pushPressed = true;
		p.step(DT, in);
		assertTrue(p.drainEvents().contains(SkateEvent.PUSH));
	}

	@Test
	public void fiftyQuickOlliesPopToWithinTenPercentOfEachOther()
	{
		// quick ollies: a 40-50 px pull over 120-180 ms, then a 60-75 px flick up in 45-70 ms.
		// They all pop at or near minPopFraction: 20 ms steps peak at 0.02 * (18 * 754.4 - 40 * 171) = 134.8 for
		// 0.92 (was 114.6 at 0.85) and 160.0 for a full 820, and these must land within 10% of each other.
		Random rnd = new Random(11);
		float min = Float.MAX_VALUE;
		float max = 0f;
		for (int k = 0; k < 50; k++)
		{
			double pullMs = 120 + rnd.nextDouble() * 60;
			double pull = 40 + rnd.nextDouble() * 10;
			double flickMs = 45 + rnd.nextDouble() * 25;
			MousePath path = MousePath.of(k, 0.6, MousePath.at(0, 0, 0), MousePath.at(pullMs, 0, pull),
				MousePath.at(pullMs + flickMs, rnd.nextGaussian() * 4, pull - 60 - rnd.nextDouble() * 15));
			GestureRig rig = new GestureRig(rolling(800)).play(path, 1800, 144);
			assertEquals("ollie " + k + " " + rig.trickEvents, TrickEvent.trick(Trick.OLLIE), rig.trickEvents.get(0));
			min = Math.min(min, rig.peak);
			max = Math.max(max, rig.peak);
		}
		assertTrue("min peak " + min, min >= 130f);
		assertTrue("spread " + min + ".." + max, (max - min) / max < 0.10f);
	}

	@Test
	public void windUpPausedForASecondThenFlickedIsAFullOllieAndPushWorksAfterRelease()
	{
		MousePath path = MousePath.of(21, 0.6, MousePath.at(0, 0, 0), MousePath.at(150, 0, 45),
			MousePath.at(1150, 1, 46), MousePath.at(1210, 2, -25));
		GestureRig rig = new GestureRig(rolling(800)).play(path, 2600, 144);
		assertEquals(TrickEvent.trick(Trick.OLLIE), rig.trickEvents.get(0));
		// a full 1 s crouch: vh 820, peak 0.02 * (20 * 820 - 40 * 210) = 160.0 in 20 ms steps
		assertTrue("peak " + rig.peak, rig.peak > 159f);
		assertEquals(SkaterState.ROLLING, rig.physics.getState());

		rig.recognizer.end();
		assertFalse(rig.recognizer.isCrouching());
		rig.in.crouch = rig.recognizer.isCrouching();
		rig.in.charge = rig.recognizer.isCharging();
		rig.in.pushPressed = true;
		rig.physics.step(DT, rig.in);
		assertTrue(rig.physics.drainEvents().contains(SkateEvent.PUSH));
	}
}
