package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickEvent;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import java.util.Arrays;
import org.junit.Test;

/** Flip easing, catching an unfinished flip, and late flicks. */
public class SkatePhysicsFlipTest
{
	static final float DT = 0.02f;
	static final float TWO_PI = (float) (2 * Math.PI);
	final SkateTuning t = new SkateTuning();

	private static Gesture flick(Direction d)
	{
		return new Gesture(d, false, 0f);
	}

	private SkatePhysics rolling(CollisionWorld w, float speed)
	{
		SkatePhysics p = new SkatePhysics(t, w, 0, 0, 0);
		p.setSpeed(speed);
		return p;
	}

	/** Ease-out 1 - (1 - u)^2. */
	private static float easeOut(float u)
	{
		u = Math.max(0f, Math.min(1f, u));
		return 1f - (1f - u) * (1f - u);
	}

	@Test
	public void flipStartsFastAndEasesOut()
	{
		// a kickflip popped from the ground: flipTime 0.02 per air step. After 3 air steps (0.06 s of 0.35)
		// ease-out has turned 1 - (1 - 0.171)^2 = 31% (smoothstep had only 8%)
		SkatePhysics p = rolling(TestWorlds.flat(), 500);
		SkateInput in = new SkateInput();
		in.gestures.add(flick(Direction.UP_LEFT));
		p.step(DT, in);
		for (int i = 0; i < 3; i++)
		{
			p.step(DT, in);
		}
		assertEquals(TWO_PI * easeOut(0.06f / 0.35f), p.getBoardRoll(), 1e-3f);
		assertTrue(p.getBoardRoll() > 0.3f * TWO_PI);
	}

	@Test
	public void lateFlickIsCompressedToFinishByTouchdown()
	{
		// uncharged pop: vh 754.4. After 25 air steps (0.5 s) h = 127.2 and vh = -245.6, so the ground is
		// (-245.6 + sqrt(245.6^2 + 2 * 2000 * 127.2)) / 2000 = 0.254 s away: the 0.35 s kickflip would be
		// only 0.254 / 0.35 = 73% done (a bail); compressed to 0.254 s (>= 0.6 * 0.35 = 0.21) it is caught
		SkatePhysics p = rolling(TestWorlds.flat(), 500);
		SkateInput in = new SkateInput();
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		for (int i = 0; i < 25; i++)
		{
			p.step(DT, in);
		}
		in.gestures.add(flick(Direction.UP_LEFT));
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.KICKFLIP), TrickEvent.landed()),
			p.drainTrickEvents());
	}

	@Test
	public void flickTooLateToCompressIsIgnored()
	{
		// after 30 air steps (0.6 s) h = 92.6, vh = -445.6: ground in 0.154 s < 0.6 * 0.35 = 0.21 s, so
		// the flick does nothing (no trick, no bail)
		SkatePhysics p = rolling(TestWorlds.flat(), 500);
		SkateInput in = new SkateInput();
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		for (int i = 0; i < 30; i++)
		{
			p.step(DT, in);
		}
		in.gestures.add(flick(Direction.UP_LEFT));
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.landed(true)), p.drainTrickEvents());
		assertEquals(0f, p.getBoardRoll(), 0f);
	}

	@Test
	public void caughtFlipKeepsRotatingAfterTouchdownInsteadOfSnapping()
	{
		// full pop north at 500 onto a 120-high platform from y = 300: in 20 ms steps h = 16.4n - 0.4n(n + 1)
		// reaches 120 at n = 30 (0.6 s). A kickflip flicked on air step 15 (h 145.6, vh 260, flat ground
		// 0.53 s away, so not compressed) has turned for 15 steps = 0.30 s of 0.35 = 86% at touchdown:
		// caught, not finished.
		SkatePhysics p = rolling(TestWorlds.platformAtY(300, 120), 500);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		for (int i = 0; i < 14; i++)
		{
			p.step(DT, in);
		}
		in.gestures.add(flick(Direction.UP_LEFT));
		float before = 0f;
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			before = p.getBoardRoll();
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(120f, p.getH(), 1e-3f);
		float atLanding = p.getBoardRoll();
		assertTrue("still turning at touchdown: " + atLanding, atLanding > 0.75f * TWO_PI && atLanding < TWO_PI);
		assertTrue("no snap at touchdown", atLanding - before < 0.6f);

		float prev = atLanding;
		boolean finished = false;
		for (int i = 0; i < 20 && !finished; i++)
		{
			p.step(DT, in);
			float roll = p.getBoardRoll();
			if (roll == 0f)
			{
				finished = true;
			}
			else
			{
				assertTrue("keeps turning forward", roll > prev);
				assertTrue("small steps", roll - prev < 0.6f);
				prev = roll;
			}
		}
		assertTrue("the flip finishes on the ground", finished);
		assertEquals(Arrays.asList(TrickEvent.trick(Trick.OLLIE), TrickEvent.trick(Trick.KICKFLIP), TrickEvent.landed()),
			p.drainTrickEvents());
	}

	@Test
	public void flipCaughtOnARailKeepsRotatingWhileGrinding()
	{
		// a rail ahead from y = 300 (top 30) is caught falling, about 0.63 s after an uncharged pop. Air
		// kickflips flicked at different times; whichever lock on still turning must finish on the rail.
		int inProgress = 0;
		for (int k = 10; k <= 24; k++)
		{
			GrindMap rail = new GrindMap();
			rail.add(new GrindSegment(10, 300, 10, 3000, 30));
			SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), rail, 0, 0, 0);
			p.setSpeed(500);
			SkateInput in = new SkateInput();
			in.gestures.add(flick(Direction.UP));
			p.step(DT, in);
			for (int i = 0; i < k; i++)
			{
				p.step(DT, in);
			}
			in.gestures.add(flick(Direction.UP_LEFT));
			for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
			{
				p.step(DT, in);
			}
			if (p.getState() != SkaterState.GRINDING || p.getBoardRoll() == 0f)
			{
				continue;
			}
			inProgress++;
			float prev = p.getBoardRoll();
			assertTrue(prev >= 0.75f * TWO_PI);
			boolean finished = false;
			for (int i = 0; i < 20 && !finished; i++)
			{
				p.step(DT, in);
				assertEquals(SkaterState.GRINDING, p.getState());
				float roll = p.getBoardRoll();
				finished = roll == 0f;
				assertTrue(finished || roll > prev);
				prev = roll;
			}
			assertTrue(finished);
		}
		assertTrue("some flick locked on mid-flip", inProgress > 0);
	}

	@Test
	public void ollieWhileTheLastFlipIsStillFinishingClearsIt()
	{
		SkatePhysics p = rolling(TestWorlds.platformAtY(300, 120), 500);
		SkateInput in = new SkateInput();
		in.crouch = true;
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.crouch = false;
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		for (int i = 0; i < 14; i++)
		{
			p.step(DT, in);
		}
		in.gestures.add(flick(Direction.UP_LEFT));
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertTrue(p.getBoardRoll() != 0f);
		in.gestures.add(flick(Direction.UP));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		assertEquals(0f, p.getBoardRoll(), 0f);
		assertFalse(p.drainEvents().contains(SkateEvent.BAIL));
	}
}
