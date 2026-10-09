package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.world.BlockerSet;
import com.gielinorskate.world.GridCollisionWorld;
import org.junit.Test;

/**
 * The ways a skater got wedged against walls and fences (found by {@link WallEscapeSweepTest}), one test each.
 */
public class WallStuckTest
{
	private static final float DT = 0.02f;
	private static final float T = GridCollisionWorld.TILE;
	private final SkateTuning t = new SkateTuning();

	@Test
	public void ridingAlongAWallIntoItPassesTheJointBetweenTwoWallTiles()
	{
		// a wall along the east edge of column 10 (x = 1408), one wall box per tile. Riding south down it 16
		// degrees into it, at a tile joint (y = 1280) the next box's end reported a normal along the wall, the
		// slide along it ran into the wall, and the skater stopped dead there for good
		GridCollisionWorld w = new GridCollisionWorld(20);
		for (int ty = 4; ty <= 10; ty++)
		{
			w.setTile(10, ty, GridCollisionWorld.WALL_E, 300f);
		}
		for (float startY = 10 * T + 4; startY <= 10 * T + 60; startY += 4)
		{
			for (float off : new float[]{0.5f, 1.2f, 3f})
			{
				SkatePhysics p = new SkatePhysics(t, w, 11 * T - off, startY, (float) Math.toRadians(164));
				p.setRollingSpeed(450);
				SkateInput in = new SkateInput();
				in.pushHeld = true;
				for (int i = 0; i < 50; i++)
				{
					p.step(DT, in);
				}
				String at = "start y " + startY + " off " + off + ": ";
				assertEquals(at, SkaterState.ROLLING, p.getState());
				assertTrue(at + "past the joint: y " + p.getY(), p.getY() < 10 * T - 150);
				assertTrue(at + "still rolling: " + p.getSpeed(), p.getSpeed() > 100f);
			}
		}
	}

	@Test
	public void slidingOffTheEndOfABoxFaceRoundsItsCorner()
	{
		// a 60 x 60 rock; riding east along its south face, 15 degrees into it. Past the corner the slide along
		// the face clipped the rounded corner on its way, nothing was free and the skater stopped dead
		GridCollisionWorld w = new GridCollisionWorld(20);
		float c = 10 * T;
		w.addBlocker(c, c, 30, 30, 1f, 0f, 200, BlockerSet.SOLID, 0);
		SkatePhysics p = new SkatePhysics(t, w, c - 100, c - 30 - 12.5f, (float) Math.toRadians(75));
		p.setRollingSpeed(200);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		for (int i = 0; i < 60; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertTrue("round the corner: x " + p.getX(), p.getX() > c + 60);
		assertTrue("still rolling: " + p.getSpeed(), p.getSpeed() > 100f);
	}

	@Test
	public void pushingPastARockCornerIsNotBlocked()
	{
		// a skater just clear of a 45-degree rock's east corner, pushing north: the move grazes the corner within
		// the skater radius at its middle but ends clear. That counted as stepping over the rock, and with no
		// contact at the clear end the skater stopped dead against the corner
		GridCollisionWorld w = new GridCollisionWorld(20);
		float c = 10 * T;
		float r = (float) Math.toRadians(45);
		w.addBlocker(c, c, 30, 30, (float) Math.cos(r), (float) Math.sin(r), 200, BlockerSet.SOLID, 0);
		float corner = c + 30 * (float) Math.sqrt(2);
		SkatePhysics p = new SkatePhysics(t, w, corner + 11.76f, c - 2.72f, 0f);
		SkateInput in = new SkateInput();
		in.pushHeld = true;
		for (int i = 0; i < 25; i++)
		{
			p.step(DT, in);
		}
		assertTrue("rode on north: y " + p.getY(), p.getY() > c + 60);
	}

	@Test
	public void aNearVerticalLandingIsNeverFakie()
	{
		// dropping back down after bouncing off a wall in the air: a few u/s backwards at touchdown. That set
		// the fakie flag, so every W push went backwards, straight back into the wall
		SkatePhysics p = new SkatePhysics(t, TestWorlds.flat(), 0, 0, 0f);
		p.setRollingSpeed(-20f);
		SkateInput in = new SkateInput();
		in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
		p.step(DT, in);
		assertEquals(SkaterState.AIRBORNE, p.getState());
		for (int i = 0; i < 100 && p.getState() == SkaterState.AIRBORNE; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		in.pushPressed = true;
		p.step(DT, in);
		assertTrue("a push from a near-standstill goes forwards: " + p.getSpeed(), p.getSpeed() > 0f);
	}
}
