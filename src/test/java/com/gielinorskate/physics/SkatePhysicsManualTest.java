package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Manuals on the manual key (Space): hold for a manual, with W for a nose manual. */
public class SkatePhysicsManualTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private SkatePhysics rolling(float speed)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(speed);
		return p;
	}

	@Test
	public void spaceAndWIsANoseManualThatNeverPushes()
	{
		SkatePhysics p = rolling(400);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		in.pushHeld = true;
		in.pushPressed = true;
		List<SkateEvent> events = new ArrayList<>();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
			events.addAll(p.drainEvents());
		}
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.NOSE_MANUAL, p.getActiveHold());
		assertFalse(events.toString(), events.contains(SkateEvent.PUSH));
		assertTrue("coasting, not pushing", p.getSpeed() < 400f);

		// a flick during the nose manual pops out and links into the same combo
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(TrickEvent.holdStart(Trick.NOSE_MANUAL), ev.get(0));
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(TrickEvent.trick(Trick.OLLIE), ev.get(2));
	}

	@Test
	public void theControllersTiltDownIsANoseManualWithoutW()
	{
		SkatePhysics p = rolling(400);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		in.noseManualHeld = true;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.NOSE_MANUAL, p.getActiveHold());
		in.noseManualHeld = false;
		p.step(DT, in);
		assertEquals(Trick.MANUAL, p.getActiveHold());
	}

	@Test
	public void pressingWMidManualSwapsToANoseManual()
	{
		SkatePhysics p = rolling(500);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.pushHeld = true;
		in.pushPressed = true;
		p.step(DT, in);
		assertEquals(Trick.NOSE_MANUAL, p.getActiveHold());
		assertFalse(p.drainEvents().contains(SkateEvent.PUSH));
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(3, ev.size());
		assertEquals(Trick.MANUAL, ev.get(1).trick);
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);
		assertEquals(TrickEvent.holdStart(Trick.NOSE_MANUAL), ev.get(2));

		in.pushHeld = false;
		p.step(DT, in);
		assertEquals(Trick.MANUAL, p.getActiveHold());
	}

	@Test
	public void wPushesAgainOnceSpaceIsReleased()
	{
		SkatePhysics p = rolling(400);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		in.pushHeld = true;
		p.step(DT, in);
		in.manualHeld = false;
		p.step(DT, in);
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue(p.drainEvents().contains(SkateEvent.PUSH));
	}

	@Test
	public void manualEndsBelowOneHundredTwentyAndDoesNotStartBelowOneHundredFifty()
	{
		// 160 u/s coasting loses about 45 + 0.00006 * 140^2 = 46.2 u/s^2: below 120 after ~0.87 s (44 steps)
		SkatePhysics p = rolling(160);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertNull(p.getActiveHold());
		List<TrickEvent> ev = p.drainTrickEvents();
		assertEquals(2, ev.size());
		assertEquals(TrickEvent.Type.HOLD_END, ev.get(1).type);

		SkatePhysics slow = rolling(100);
		slow.step(DT, in);
		assertEquals(SkaterState.ROLLING, slow.getState());
	}

	@Test
	public void manualNeverBails()
	{
		SkatePhysics p = rolling(1200);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		for (int i = 0; i < 200; i++)
		{
			in.steer = (i / 25) % 2 == 0 ? 1f : -1f;
			p.step(DT, in);
			assertTrue(p.getState() == SkaterState.MANUAL || p.getState() == SkaterState.ROLLING);
		}
		assertFalse(p.drainEvents().contains(SkateEvent.BAIL));
	}

	@Test
	public void kickflipIntoASpaceHeldLandingIsOneComboThatResolvesAfterRelease()
	{
		SkatePhysics p = rolling(600);
		SkateInput in = new SkateInput();
		ComboScorer scorer = new ComboScorer();
		float[] now = {0f};
		List<SkateEvent> events = new ArrayList<>();

		in.crouch = true;
		run(p, in, scorer, now, events, 10);
		in.crouch = false;
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		run(p, in, scorer, now, events, 1);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		in.manualHeld = true; // Space held through the landing
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			run(p, in, scorer, now, events, 1);
		}
		assertEquals(SkaterState.MANUAL, p.getState());
		assertEquals(Trick.MANUAL, p.getActiveHold());
		assertTrue("camera dip / landing anim still fire", events.contains(SkateEvent.LAND));
		assertEquals("combo still open", ComboScorer.Result.NONE, scorer.lastResult());
		assertEquals(Arrays.asList("Kickflip"), scorer.comboNames());

		run(p, in, scorer, now, events, 25); // 0.5 s manual
		assertEquals(ComboScorer.Result.NONE, scorer.lastResult());
		in.manualHeld = false;
		run(p, in, scorer, now, events, 1);
		assertEquals(SkaterState.ROLLING, p.getState());
		float released = now[0];
		run(p, in, scorer, now, events, 20); // 0.4 s: still rolling out
		assertEquals(ComboScorer.Result.NONE, scorer.lastResult());
		run(p, in, scorer, now, events, 6); // past 0.5 s after the release
		assertTrue(now[0] - released >= 0.5f);
		assertTrue(scorer.lastResult().isLanded());
		assertEquals(Arrays.asList("Kickflip", "Manual"), scorer.lastComboNames());
	}

	/** Steps `n` times, feeding trick events and the roll-out clock to the scorer like the session does. */
	private static void run(SkatePhysics p, SkateInput in, ComboScorer scorer, float[] now, List<SkateEvent> events, int n)
	{
		for (int i = 0; i < n; i++)
		{
			p.step(DT, in);
			now[0] += DT;
			events.addAll(p.drainEvents());
			for (TrickEvent e : p.drainTrickEvents())
			{
				assertFalse("no LANDED while the combo carries on into a manual",
					e.type == TrickEvent.Type.LANDED && p.getState() == SkaterState.MANUAL);
				scorer.accept(e, now[0]);
			}
			scorer.update(now[0], p.getState() == SkaterState.ROLLING && p.getActiveHold() == null);
		}
	}

	@Test
	public void manualSpeedHasHysteresisEnterAt150LeaveBelow120()
	{
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		SkatePhysics slow = rolling(140);
		slow.step(DT, in);
		assertEquals("140 is too slow to start a manual", SkaterState.ROLLING, slow.getState());

		SkatePhysics p = rolling(160);
		p.step(DT, in);
		assertEquals(SkaterState.MANUAL, p.getState());
		for (int i = 0; i < 300 && p.getSpeed() >= 125f; i++)
		{
			p.step(DT, in);
		}
		assertEquals("still in the manual at " + p.getSpeed(), SkaterState.MANUAL, p.getState());
		for (int i = 0; i < 300 && p.getState() == SkaterState.MANUAL; i++)
		{
			p.step(DT, in);
		}
		assertTrue("left the manual below 120: " + p.getSpeed(), p.getSpeed() < 120f && p.getSpeed() > 110f);
	}

	@Test
	public void bumpySlopeHoveringNear150DoesNotFlickerTheManual()
	{
		// downhill grade 0.057 * 2000 * 0.4 = 45.6 u/s^2 balances the 45 rolling friction, so the speed hovers
		// at 150; 3-unit bumps every 126 units swing it by about +-16 (slope 0.15 * 800 = 120 u/s^2 over a
		// 0.84 s bump), back and forth across 150
		SkatePhysics p = new SkatePhysics(t, TestWorlds.bumpyDownhillNorth(0.057f, 3f, 20f), 0, 0, 0);
		p.setRollingSpeed(155);
		SkateInput in = new SkateInput();
		in.manualHeld = true;
		int starts = 0;
		for (int i = 0; i < 200; i++)
		{
			p.step(DT, in);
			for (TrickEvent e : p.drainTrickEvents())
			{
				if (e.type == TrickEvent.Type.HOLD_START)
				{
					starts++;
				}
			}
		}
		assertEquals("one manual, no flicker", 1, starts);
	}
}
