package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import org.junit.Test;

/**
 * What the button-trick preset adds to physics: the spin assist (a faster spin while held in the air), the grind
 * button (a rail catches from further to the side while it is held) and whether a rail is near (the grind button
 * grinds rather than stepping off). Without the buttons nothing changes.
 */
public class SkatePhysicsButtonTricksTest
{
	static final float DT = 0.02f;
	final SkateTuning t = new SkateTuning();

	private static GrindMap map(GrindSegment... segs)
	{
		GrindMap m = new GrindMap();
		for (GrindSegment s : segs)
		{
			m.add(s);
		}
		return m;
	}

	/** The air spin after popping and steering right for {@code steps} steps. */
	private float spin(boolean fast, int steps)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		p.setRollingSpeed(400);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		in.steer = 1f;
		in.spinFast = fast;
		for (int i = 0; i < steps; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.AIRBORNE, p.getState());
		return p.airSpin;
	}

	@Test
	public void theSpinAssistSpinsFasterInTheAir()
	{
		float plain = spin(false, 10);
		float fast = spin(true, 10);
		assertTrue(plain > 0f);
		assertEquals(plain * SkatePhysics.SPIN_FAST_MULT, fast, 1e-3f);
	}

	@Test
	public void theSpinAssistDoesNothingOnTheGround()
	{
		SkatePhysics a = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		SkatePhysics b = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0);
		a.setRollingSpeed(400);
		b.setRollingSpeed(400);
		SkateInput ia = new SkateInput();
		SkateInput ib = new SkateInput();
		ia.steer = ib.steer = 1f;
		ib.spinFast = true;
		for (int i = 0; i < 20; i++)
		{
			a.step(DT, ia);
			b.step(DT, ib);
		}
		assertEquals(a.getHeading(), b.getHeading(), 0f);
	}

	/** Pops north along a north rail {@code side} units to the east and flies out; the state it ends in. */
	private SkaterState popAlongRail(float side, boolean grindHeld)
	{
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(side, -2000, side, 2000, 30)),
			0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		in.grindHeld = grindHeld;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		for (int i = 0; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		return p.getState();
	}

	@Test
	public void theGrindButtonCatchesARailFurtherToTheSide()
	{
		float between = (GrindMap.SNAP_DISTANCE + GrindMap.ASSIST_SNAP_DISTANCE) / 2f;
		assertNotEquals(SkaterState.GRINDING, popAlongRail(between, false));
		assertEquals(SkaterState.GRINDING, popAlongRail(between, true));
		// within the usual snap distance it catches either way: never harder than without the button
		assertEquals(SkaterState.GRINDING, popAlongRail(GrindMap.SNAP_DISTANCE - 4f, false));
		assertEquals(SkaterState.GRINDING, popAlongRail(GrindMap.SNAP_DISTANCE - 4f, true));
		// and beyond the assist it does not
		assertNotEquals(SkaterState.GRINDING, popAlongRail(GrindMap.ASSIST_SNAP_DISTANCE + 8f, true));
	}

	@Test
	public void aRailIsNearWithinOneAndAHalfTiles()
	{
		assertTrue(new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(150, -500, 150, 500, 30)), 0, 0, 0)
			.isRailNear());
		// across the rail: a few tiles of bucket away
		assertTrue(new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(-500, 180, 500, 180, 30)), 0, 0, 0)
			.isRailNear());
		assertFalse(new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(300, -500, 300, 500, 30)), 0, 0, 0)
			.isRailNear());
		// one far overhead is not near
		assertFalse(new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(50, -500, 50, 500, 900)), 0, 0, 0)
			.isRailNear());
		assertFalse(new SkatePhysics(t, TestWorlds.flat(), map(), 0, 0, 0).isRailNear());
	}
}
