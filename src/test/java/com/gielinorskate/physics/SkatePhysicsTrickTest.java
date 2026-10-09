package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SkatePhysicsTrickTest
{
	static final float DT = 0.02f;
	static final float TWO_PI = (float) (2 * Math.PI);
	final SkateTuning t = new SkateTuning();

	static Gesture flick(Direction d)
	{
		return new Gesture(d, false, 0f);
	}

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(speed);
		return p;
	}

	/** Crouches for 0.4 s (full charge: vh 820, hang time 2*820/2000 = 0.82 s) then pops with `g`. */
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

	private static void stepUntilGrounded(SkatePhysics p, SkateInput in)
	{
		for (int i = 0; i < 500 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
	}

	@Test
	public void kickflipGestureInTheAirCompletesAndLands()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP));
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in);
		stepUntilGrounded(p, in);

		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.KICKFLIP), TrickEvent.landed(true)),
			p.drainTrickEvents());
		assertEquals(0f, p.getBoardRoll(), 1e-6f);
	}

	@Test
	public void flipGestureWhileRollingPopsAndFlips()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_LEFT));
		assertTrue(p.drainEvents().contains(SkateEvent.POP));
		stepUntilGrounded(p, in);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.KICKFLIP), TrickEvent.landed(true)), p.drainTrickEvents());
	}

	@Test
	public void nollieGesturePopsLikeAnOllie()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, new Gesture(Direction.DOWN, true, 0f));
		stepUntilGrounded(p, in);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.NOLLIE), TrickEvent.landed(true)), p.drainTrickEvents());
	}

	@Test
	public void reflickUpgradesToDoubleKickflip()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_LEFT));
		p.step(DT, in);
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in);
		stepUntilGrounded(p, in);

		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.KICKFLIP),
			TrickEvent.upgrade(Trick.DOUBLE_KICKFLIP, Trick.KICKFLIP), TrickEvent.landed(true)), p.drainTrickEvents());
	}

	@Test
	public void reflickingADoubleUpgradesToATripleThatLands()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_RIGHT));
		p.step(DT, in);
		in.gestures.add(flick(Direction.UP_RIGHT));
		p.step(DT, in);
		p.step(DT, in);
		float rollBefore = p.getBoardRoll();
		in.gestures.add(flick(Direction.UP_RIGHT));
		p.step(DT, in);
		// the roll carries on from where the double was (no jump back), in the heelflip direction
		assertTrue(p.getBoardRoll() < rollBefore);
		assertTrue(rollBefore - p.getBoardRoll() < 1.5f);
		// after the full 0.65 s the board has turned three times
		for (int i = 0; i < 33; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(-3 * TWO_PI, p.getBoardRoll(), 1e-4f);
		stepUntilGrounded(p, in);

		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.HEELFLIP),
			TrickEvent.upgrade(Trick.DOUBLE_HEELFLIP, Trick.HEELFLIP),
			TrickEvent.upgrade(Trick.TRIPLE_HEELFLIP, Trick.DOUBLE_HEELFLIP), TrickEvent.landed(true)),
			p.drainTrickEvents());
	}

	@Test
	public void boardRollAfterAFullKickflipIsTwoPi()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_LEFT));
		for (int i = 0; i < 20; i++) // 0.4 s > 0.35 s duration, < 0.82 s hang time
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(TWO_PI, p.getBoardRoll(), 1e-4f);
		assertEquals(0f, p.getBoardYawOffset(), 1e-6f);
	}

	@Test
	public void popShoveItSpinsTheBoardHalfATurn()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.LEFT));
		// duration 0.30 s; 0.32 s later the half turn is complete
		for (int i = 0; i < 16; i++) // 0.32 s
		{
			p.step(DT, in);
		}
		assertEquals((float) Math.PI, p.getBoardYawOffset(), 1e-4f);
		assertEquals(0f, p.getBoardRoll(), 1e-6f);
	}

	@Test
	public void flipRollIsEasedOut()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_LEFT));
		// re-flick on the first air step upgrades to the 0.5 s double kickflip; the upgrade rescales
		// flipTime so the roll carries over continuously (see rescaleFlipTime), so the expected curve
		// here is computed the same way: match the KICKFLIP roll at flipTime 0.02 onto the
		// DOUBLE_KICKFLIP curve by inverting the ease-out as the production code does, then run the
		// eased curve forward another 0.22 s.
		float oldEased = eased(0.02f / 0.35f);
		float targetRoll = 1f * TWO_PI * oldEased;
		float tPrime = rescale(2f, 0.5f, targetRoll);
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in); // flipTime 0.02 before upgrade, rescaled to tPrime after
		for (int i = 0; i < 11; i++) // 0.22 s further
		{
			p.step(DT, in);
		}
		float finalEased = eased((tPrime + 0.22f) / 0.5f);
		assertEquals(2 * TWO_PI * finalEased, p.getBoardRoll(), 1e-3f);
	}

	@Test
	public void reflickUpgradeKeepsRollContinuous()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP_LEFT));
		// run the kickflip close to completion (0.3 of its 0.35 s) before upgrading, where the old
		// (buggy) behaviour of keeping flipTime literally would jump the roll by over 2 radians
		for (int i = 0; i < 15; i++)
		{
			p.step(DT, in);
		}
		float rollBefore = p.getBoardRoll();
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in);
		float rollAfter = p.getBoardRoll();
		assertTrue(Math.abs(rollAfter - rollBefore) < 0.3f);
	}

	/** Ease-out 1 - (1 - u)^2: fast off the flick, gentle into the catch. */
	private static float eased(float u)
	{
		u = Math.max(0f, Math.min(1f, u));
		return 1f - (1f - u) * (1f - u);
	}

	/** Mirrors SkatePhysics' upgrade rescale: the time on the new flip whose eased roll is targetRoll. */
	private static float rescale(float rollTurns, float duration, float targetRoll)
	{
		float targetEased = Math.max(0f, Math.min(1f, targetRoll / (rollTurns * TWO_PI)));
		return (1f - (float) Math.sqrt(1f - targetEased)) * duration;
	}

	@Test
	public void holdingQForPointThreeSecondsGrabsIndy()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP));
		p.drainTrickEvents();
		in.grabLeft = true;
		for (int i = 0; i < 15; i++)
		{
			p.step(DT, in);
		}
		assertEquals(Trick.INDY, p.getActiveHold());
		in.grabLeft = false;
		p.step(DT, in);
		assertNull(p.getActiveHold());
		stepUntilGrounded(p, in);

		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(3, ev.size());
		assertEquals(TrickEvent.holdStart(Trick.INDY), ev.get(0));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(Trick.INDY, ev.get(1).trick);
		assertEquals(0.3f, ev.get(1).seconds, 0.021f);
		assertEquals(TrickEvent.landed(true), ev.get(2));
	}

	@Test
	public void holdingEGrabsMelonAndLandingAutoReleasesWithoutBail()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP));
		p.drainTrickEvents();
		in.grabRight = true;
		stepUntilGrounded(p, in);

		assertEquals(SkaterState.ROLLING, p.getState());
		assertNull(p.getActiveHold());
		List<TrickEvent> ev = p.drainTrickEvents();
		// held past 0.5 s the Melon turns into its tweak, the Method
		assertEquals(4, ev.size());
		assertEquals(TrickEvent.holdStart(Trick.MELON), ev.get(0));
		assertEquals(TrickEvent.holdStart(Trick.METHOD), ev.get(1));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(2).type);
		assertEquals(Trick.METHOD, ev.get(2).trick);
		assertTrue(ev.get(2).seconds > 0.5f);
		assertEquals(TrickEvent.landed(true), ev.get(3));
	}

	@Test
	public void grabKeysDoNothingWhileRolling()
	{
		SkatePhysics p = rolling(300);
		SkateInput in = new SkateInput();
		in.grabLeft = true;
		p.step(DT, in);
		assertNull(p.getActiveHold());
		assertTrue(p.drainTrickEvents().isEmpty());
	}

	@Test
	public void manualEnterAndExit()
	{
		SkatePhysics p = rolling(300);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.MANUAL, p.getActiveHold());
		assertEquals(0.25f, p.getBoardPitch(), 1e-6f);
		for (int i = 0; i < 9; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.MANUAL, p.getState());
		in.manualHeld = false;
		p.step(DT, in);

		assertEquals(SkaterState.ROLLING, p.getState());
		assertNull(p.getActiveHold());
		assertEquals(0f, p.getBoardPitch(), 1e-6f);
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(2, ev.size());
		assertEquals(TrickEvent.holdStart(Trick.MANUAL), ev.get(0));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(Trick.MANUAL, ev.get(1).trick);
		assertEquals(0.2f, ev.get(1).seconds, 0.021f);
	}

	@Test
	public void noseManualTiltsTheNoseDown()
	{
		SkatePhysics p = rolling(300);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		in.pushHeld = true; // Space + W
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.NOSE_MANUAL, p.getActiveHold());
		assertEquals(-0.25f, p.getBoardPitch(), 1e-6f);
		assertEquals(Arrays.asList(TrickEvent.holdStart(Trick.NOSE_MANUAL)), p.drainTrickEvents());
	}

	@Test
	public void manualRollsExactlyLikeRolling()
	{
		SkatePhysics a = rolling(400);
		SkatePhysics b = rolling(400);
		SkateInput ia = new SkateInput();
		SkateInput ib = new SkateInput();
		ib.manualHeld = true;
		for (int i = 0; i < 30; i++)
		{
			ia.steer = 0.5f;
			ib.steer = 0.5f;
			a.step(DT, ia);
			b.step(DT, ib);
		}
		assertEquals(SkaterState.MANUAL, b.getState());
		assertEquals(a.getX(), b.getX(), 1e-3f);
		assertEquals(a.getY(), b.getY(), 1e-3f);
		assertEquals(a.getHeading(), b.getHeading(), 1e-5f);
		assertEquals(a.getSpeed(), b.getSpeed(), 1e-3f);
	}

	@Test
	public void popFromManualLinksTheCombo()
	{
		SkatePhysics p = rolling(400);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		p.step(DT, in);
		p.step(DT, in);
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(0f, p.getBoardPitch(), 1e-6f);
		in.manualHeld = false;
		stepUntilGrounded(p, in);

		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(4, ev.size());
		assertEquals(TrickEvent.holdStart(Trick.MANUAL), ev.get(0));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(TrickEvent.trick(Trick.OLLIE), ev.get(2));
		assertEquals(TrickEvent.landed(true), ev.get(3));
	}

	@Test
	public void landingIntoAManualDoesNotEmitLandedAgainOnExit()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP));
		stepUntilGrounded(p, in);
		p.drainTrickEvents();
		in.manualHeld = true;
		p.step(DT, in);
		in.manualHeld = false;
		p.step(DT, in);
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(2, ev.size());
		assertFalse(ev.contains(TrickEvent.landed()));
	}

	@Test
	public void airGestureThatIsOnlyAPopIsIgnored()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		chargedPop(p, in, flick(Direction.UP));
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE)), p.drainTrickEvents());
	}

	@Test
	public void poppingWhileRidingFakieNamesTheTricksFakie()
	{
		SkatePhysics p = rolling(-600);
		SkateInput in = new SkateInput();
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE, true), TrickEvent.trick(Trick.KICKFLIP, true)),
			p.drainTrickEvents());
	}

	@Test
	public void slowlyRollingBackIsNotFakie()
	{
		SkatePhysics p = rolling(-100);
		SkateInput in = new SkateInput();
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE)), p.drainTrickEvents());
	}
}
