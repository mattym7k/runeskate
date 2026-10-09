package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.render.AnimationPicker;
import com.gielinorskate.tricks.Gesture;
import java.util.List;
import org.junit.Test;

/** How a bail moves the skater: what the renderer draws while the bail animation plays. */
public class SkatePhysicsBailTest
{
	private static final float DT = 0.02f;
	private final SkateTuning t = new SkateTuning();

	@Test
	public void aBailInTheAirFallsToTheGroundInsteadOfSnappingDown()
	{
		// an ollie at 1200 head-on into a 300-high wall bails in the air, well above the ground. It used to be put
		// straight on the ground in the next step (an 80-unit drop in 0.02 s): now it falls under gravity
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(200, 300), 0, 0, 0f);
		p.setRollingSpeed(1200f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		for (int i = 0; i < 30 && p.getState() != SkaterState.BAILED; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.BAILED, p.getState());
		float h = p.getH();
		assertTrue("bailed in the air: h " + h, h > 40f);
		int steps = 0;
		float fallSpeed = 0f;
		while (p.getH() > 0f && steps < 100)
		{
			p.step(DT, in);
			float drop = h - p.getH();
			assertTrue("never rises: " + drop, drop >= 0f);
			// gravity 2000: after t seconds the fall speed is 2000 t, so a step drops by at most (2000 t + 40) * 0.02
			fallSpeed += t.gravity * DT;
			assertTrue("falls, not snaps: dropped " + drop + " in one step", drop <= fallSpeed * DT + 1e-3f);
			h = p.getH();
			steps++;
		}
		assertEquals(0f, p.getH(), 0f);
		assertEquals(SkaterState.BAILED, p.getState());
	}

	@Test
	public void theBailAnimationIsPickedOnceForTheWholeBail()
	{
		// the animator restarts an animation only when the picked action changes: a bail into a wall, W held
		// throughout, picks BAIL exactly once and keeps it until the reset
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, 0f);
		p.setRollingSpeed(1500f);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		AnimationPicker.Action current = AnimationPicker.Action.NONE;
		int bailPicks = 0;
		boolean reset = false;
		for (int i = 0; i < 150 && !reset; i++)
		{
			p.step(DT, in);
			List<SkateEvent> events = p.drainEvents();
			AnimationPicker.Action next =
				AnimationPicker.pick(p.getState(), events, current, 0f);
			if (next != current && next == AnimationPicker.Action.BAIL)
			{
				bailPicks++;
			}
			current = next;
			reset = events.contains(SkateEvent.RESET);
		}
		assertTrue("got back up", reset);
		assertEquals(1, bailPicks);
	}

	@Test
	public void aBailOnTheGroundStaysOnTheGround()
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.wallAtY(100, 300), 0, 50, 0f);
		p.setRollingSpeed(1500f);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 10 && p.getState() != SkaterState.BAILED; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.BAILED, p.getState());
		float x = p.getX();
		float y = p.getY();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
			assertEquals(0f, p.getH(), 0f);
			// the bail does not shuffle about against the wall it hit
			assertEquals(x, p.getX(), 0f);
			assertEquals(y, p.getY(), 0f);
		}
	}
}
