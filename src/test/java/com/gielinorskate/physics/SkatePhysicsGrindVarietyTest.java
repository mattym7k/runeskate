package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import org.junit.Test;

/** Sloped rails under the physics, and the slide / crooked variations picked by W / S and the spin. */
public class SkatePhysicsGrindVarietyTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static GrindMap map(GrindSegment s)
	{
		GrindMap m = new GrindMap();
		m.add(s);
		return m;
	}

	private static void flyOut(SkatePhysics p, SkateInput in)
	{
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
	}

	@Test
	public void grindingASlopedRailFollowsItsHeight()
	{
		// 30 high at y = 30 rising to 130 at y = 1030 (0.1 per unit)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(10, 30, 10, 1030, 30, 130)), 0, 0, 0);
		p.setSpeed(600);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(30 + 0.1f * (p.getY() - 30), p.getH(), 1e-2f);
		for (int i = 0; i < 20; i++)
		{
			p.step(DT, in);
			assertEquals(30 + 0.1f * (p.getY() - 30), p.getH(), 1e-2f);
		}
	}

	@Test
	public void grindingDownASlopedRailIsFasterThanUpIt()
	{
		SkatePhysics up = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(10, 30, 10, 2030, 30, 330)), 0, 0, 0);
		SkatePhysics down = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(10, 30, 10, 2030, 30, -270)), 0, 0, 0);
		float[] speeds = new float[2];
		int k = 0;
		for (SkatePhysics p : new SkatePhysics[]{up, down})
		{
			p.setSpeed(600);
			SkateInput in = new SkateInput();
			in.gestures.add(new Gesture(Direction.UP, false, 0f));
			p.step(DT, in);
			flyOut(p, in);
			assertEquals(SkaterState.GRINDING, p.getState());
			for (int i = 0; i < 25; i++)
			{
				p.step(DT, in);
			}
			speeds[k++] = p.getSpeed();
		}
		assertTrue("up " + speeds[0] + " down " + speeds[1], speeds[1] > speeds[0] + 50f);
	}

	/** Pops and spins right (clockwise) for {@code spinSteps} steps, then flies onto a north rail starting at y = 300. */
	private SkatePhysics slideOn(int spinSteps, SkateInput keys)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(10, 300, 10, 1500, 30)), 0, 0, 0);
		p.setSpeed(500);
		SkateInput in = keys;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		in.steer = 1f;
		for (int i = 0; i < spinSteps; i++)
		{
			p.step(DT, in);
		}
		in.steer = 0f;
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		return p;
	}

	/** Ollies (no spin) onto a rail along the 40-degree line through (0, 300): a crooked-angle lock. */
	private SkatePhysics crookedOn(SkateInput in)
	{
		float sx = (float) Math.sin(Math.toRadians(40)) * 300;
		float sy = (float) Math.cos(Math.toRadians(40)) * 300;
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(-sx, 300 - sy, sx, 300 + sy, 30)), 0, 0, 0);
		p.setSpeed(500);
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		return p;
	}

	@Test
	public void slidesAndTheirVariations()
	{
		// 9 steps at 7 rad/s = 72 degrees off the rail: a boardslide
		assertEquals(Trick.BOARDSLIDE, slideOn(9, new SkateInput()).getActiveHold());
		SkateInput w = new SkateInput();
		w.pushHeld = true;
		assertEquals(Trick.NOSESLIDE, slideOn(9, w).getActiveHold());
		SkateInput s = new SkateInput();
		s.leanBack = true;
		assertEquals(Trick.TAILSLIDE, slideOn(9, s).getActiveHold());

		assertEquals(Trick.CROOKED, crookedOn(new SkateInput()).getActiveHold());
		SkateInput s2 = new SkateInput();
		s2.leanBack = true;
		assertEquals(Trick.SMITH, crookedOn(s2).getActiveHold());
		SkateInput w2 = new SkateInput();
		w2.pushHeld = true;
		assertEquals(Trick.FEEBLE, crookedOn(w2).getActiveHold());
	}

	@Test
	public void wAndSSwitchTheSlideMidGrind()
	{
		SkateInput in = new SkateInput();
		SkatePhysics p = slideOn(9, in);
		in.pushHeld = true;
		p.step(DT, in);
		assertEquals(Trick.NOSESLIDE, p.getActiveHold());
		in.pushHeld = false;
		in.leanBack = true;
		p.step(DT, in);
		assertEquals(Trick.TAILSLIDE, p.getActiveHold());
	}

	@Test
	public void spinningPastNinetyDegreesGivesALipslide()
	{
		// 12 steps at 7 rad/s = 96 degrees from the approach (outside the 70-degree spin assist either way):
		// across the rail like a boardslide, but turned past square
		assertEquals(Trick.LIPSLIDE, slideOn(12, new SkateInput()).getActiveHold());
	}

	@Test
	public void newGrindsAreGrindTricksWithPoints()
	{
		for (Trick g : new Trick[]{Trick.NOSESLIDE, Trick.TAILSLIDE, Trick.SMITH, Trick.FEEBLE, Trick.LIPSLIDE})
		{
			assertEquals(TrickKind.GRIND, g.kind);
			assertTrue(g.points > Trick.FIFTY_FIFTY.points);
		}
	}
}
