package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.world.GrindMap;
import com.gielinorskate.world.GrindSegment;
import org.junit.Test;

/**
 * Grind magnetism (review P3/P5): wider lock windows while rising ([top - 32, top + 90]) and falling
 * ([top - 28, top + 160]), approaches up to 88 degrees, flips that lock once half done, and a short global
 * relock cooldown so rail-to-rail transfers work.
 */
public class SkatePhysicsGrindMagnetTest
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

	/** Steps until the state leaves AIRBORNE; returns the seconds that took. */
	private static float flyOut(SkatePhysics p, SkateInput in)
	{
		int i = 0;
		for (; i < 300 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		return i * DT;
	}

	@Test
	public void fastFortyFiveDegreePopCrossingHighAboveARailStillLocks()
	{
		// 1000 u/s at 45 degrees to an east-west rail at y = 0 (top 30): 707 u/s toward it. Popped 150 short,
		// the skater enters the 44-unit band 0.15 s later at h = 754.4 * 0.15 - 1000 * 0.15^2 = 91 and crosses
		// the rail at about 115, i.e. 61..85 above its top: past the old top + 48 limit, inside top + 90
		float heading = (float) Math.toRadians(45);
		float perp = 150f;
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(-2000, 0, 2000, 0, 30)),
			0, -perp, heading);
		p.setRollingSpeed(1000);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(30f, p.getH(), 1e-3f);
	}

	@Test
	public void eightyFiveDegreeApproachLocks()
	{
		// rail along y (north); heading 85 degrees, nearly square to it (old limit 80)
		float heading = (float) Math.toRadians(85);
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(60, -1000, 60, 1000, 30)),
			0, 0, heading);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
	}

	@Test
	public void nearlySquareApproachTakesTheGrindDirectionFromTheSteer()
	{
		// heading 87 degrees to a north rail: cos 87 = 0.05 < 0.2, so the travel does not pick a way along it
		float heading = (float) Math.toRadians(87);
		for (float steer : new float[]{1f, -1f})
		{
			SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(60, -1000, 60, 1000, 30)),
				0, 0, heading);
			p.setRollingSpeed(600);
			SkateInput in = new SkateInput();
			in.gestures.add(new Gesture(Direction.UP, false, 0f));
			p.step(DT, in);
			in.steer = steer;
			flyOut(p, in);
			assertEquals(SkaterState.GRINDING, p.getState());
			// steering right (clockwise) from an 87-degree travel turns south (180); left turns north (0)
			float expected = steer > 0 ? (float) Math.PI : 0f;
			assertEquals("steer " + steer, 0f, Angles.absDiff(expected, p.getTravelHeading()), 1e-3f);
		}
	}

	@Test
	public void aKickflipLocksOnceHalfDone()
	{
		// rail alongside from the start; the kickflip (0.35 s) may lock at 0.175 s (was 75%, and the rising
		// h = 754.4 * 0.26 - 1000 * 0.26^2 = 128 was then far above the old top + 48 window)
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), map(new GrindSegment(10, 30, 10, 1500, 30)), 0, 0, 0);
		p.setRollingSpeed(500);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP_LEFT, false, 0f));
		p.step(DT, in);
		float secs = flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertTrue("locked after " + secs, secs >= 0.15f && secs <= 0.2f);
	}

	@Test
	public void popOffOneRailOntoAParallelOneRightAway()
	{
		// rails at x = 10 and x = 70; popping off the first with full right steer (200 u/s) brings the second
		// within 44 units after 16 / 200 = 0.08 s, still rising at 30 + 754.4 * 0.08 - 1000 * 0.08^2 = 84
		// (inside top + 90): the old 0.45 s global cooldown blocked that transfer
		GrindMap m = map(new GrindSegment(10, 30, 10, 3000, 30), new GrindSegment(70, 30, 70, 3000, 30));
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), m, 0, 0, 0);
		p.setRollingSpeed(600);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(10f, p.getX(), 1e-3f);
		for (int i = 0; i < 10; i++)
		{
			p.step(DT, in);
		}
		in.steer = 1f;
		in.gestures.add(new Gesture(Direction.UP, false, 0f));
		p.step(DT, in);
		in.steer = 0f;
		float secs = flyOut(p, in);
		assertEquals(SkaterState.GRINDING, p.getState());
		assertEquals(70f, p.getX(), 1e-3f);
		assertTrue("transferred after " + secs, secs < 0.2f);
	}
}
