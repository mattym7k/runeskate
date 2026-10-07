package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.world.BlockerSet;
import com.gielinorskate.world.GridCollisionWorld;
import org.junit.Test;

/**
 * Wall scrapes and bails against the world's real contact shapes ({@link CollisionWorld#contact}): a rotated
 * box is scraped along its own face (not a grid axis), and a bail recovers along that face's normal.
 */
public class SkatePhysicsContactTest
{
	private static final float DT = 0.02f;
	private static final float C = 10 * 128f;
	private final SkateTuning t = new SkateTuning();

	/** A long solid box at the centre of a 20-tile world, its long axis along (cos a, sin a) in x/y. */
	private static GridCollisionWorld rotatedWall(float aDeg)
	{
		GridCollisionWorld w = new GridCollisionWorld(20);
		float a = (float) Math.toRadians(aDeg);
		w.addBlocker(C, C, 600, 32, (float) Math.cos(a), (float) Math.sin(a), 200, BlockerSet.SOLID, 0);
		return w;
	}

	private static float deg(float rad)
	{
		return (float) Math.toDegrees(rad);
	}

	@Test
	public void scrapingARotatedBoxFollowsItsFaceNotAGridAxis()
	{
		// box axis at 30 degrees from +x, i.e. heading 60 (0 = north, clockwise); its south-east face normal is
		// (0.5, -0.866), heading 150. Riding at heading 50 grazes that face at 10 degrees.
		GridCollisionWorld w = rotatedWall(30);
		float ux = (float) Math.cos(Math.toRadians(30));
		float uy = (float) Math.sin(Math.toRadians(30));
		float vx = -uy;
		float vy = ux;
		float sx = C - vx * 60 - ux * 400;
		float sy = C - vy * 60 - uy * 400;
		SkatePhysics p = new SkatePhysics(t, w, sx, sy, (float) Math.toRadians(50));
		p.setSpeed(1000);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
			assertEquals(SkaterState.ROLLING, p.getState());
		}
		// a 10-degree graze costs 25% * sin 10 = 4% on contact plus the scrape; the grid-axis normal used to
		// read it as a 30-60 degree hit, zig-zag between axes and end near 740, 1-2 degrees short of the face
		assertTrue("keeps its speed: " + p.getSpeed(), p.getSpeed() > 850f);
		assertEquals("eased along the face, heading " + deg(p.getHeading()), 60f, deg(p.getHeading()), 0.5f);
		// moved along the face, not stuck: well past where it first touched
		float along = (p.getX() - sx) * ux + (p.getY() - sy) * uy;
		assertTrue("along " + along, along > 500f);
		float off = (p.getX() - C) * vx + (p.getY() - C) * vy;
		assertTrue("stays outside the box: " + off, off <= -32f);
	}

	@Test
	public void headOnIntoARockAt1400Bails()
	{
		GridCollisionWorld w = new GridCollisionWorld(20);
		w.addBlocker(C, C, 32, 32, 1f, 0f, 100, BlockerSet.SOLID, 0);
		SkatePhysics p = new SkatePhysics(t, w, C, C - 400, 0);
		p.setSpeed(1400);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 30 && p.getState() == SkaterState.ROLLING; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.BAILED, p.getState());
	}

	@Test
	public void diagonalPassSixtyFromARockCentreIsClean()
	{
		// a 64 x 64 rock: its corner is 32 * sqrt 2 = 45.3 from the centre, so a 45-degree line 60 from the centre
		// clears it by 14.7, more than the 12 skater radius
		GridCollisionWorld rock = new GridCollisionWorld(20);
		rock.addBlocker(C, C, 32, 32, 1f, 0f, 100, BlockerSet.SOLID, 0);
		GridCollisionWorld open = new GridCollisionWorld(20);
		float s = (float) Math.sqrt(0.5);
		float sx = C + s * 60 - s * 400;
		float sy = C - s * 60 - s * 400;
		SkatePhysics p = new SkatePhysics(t, rock, sx, sy, (float) Math.toRadians(45));
		SkatePhysics q = new SkatePhysics(t, open, sx, sy, (float) Math.toRadians(45));
		p.setSpeed(1000);
		q.setSpeed(1000);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 40; i++)
		{
			p.step(DT, in);
			q.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(q.getSpeed(), p.getSpeed(), 1e-3f);
		assertEquals(q.getX(), p.getX(), 1e-3f);
		assertEquals(q.getY(), p.getY(), 1e-3f);
		assertEquals(45f, deg(p.getHeading()), 1e-3f);
	}

	@Test
	public void aBailIntoARotatedBoxRecoversAlongItsFace()
	{
		// into the south-east face (normal heading 150) at heading -10, 20 degrees off head-on, at 1400: 1400 *
		// cos 20 = 1316 > 1100 bails. The recovery faces along the face (normal 150 - 90 = 60, 70 degrees from the
		// old heading; the other way, 240, is 110 away), not a grid axis, and is pushed clear of the box
		GridCollisionWorld w = rotatedWall(30);
		float ux = (float) Math.cos(Math.toRadians(30));
		float uy = (float) Math.sin(Math.toRadians(30));
		float vx = -uy;
		float vy = ux;
		SkatePhysics p = new SkatePhysics(t, w, C - vx * 300, C - vy * 300, (float) Math.toRadians(-10));
		p.setSpeed(1400);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 30 && p.getState() == SkaterState.ROLLING; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.BAILED, p.getState());
		for (int i = 0; i < 200 && p.getState() == SkaterState.BAILED; i++)
		{
			p.step(DT, in);
		}
		assertEquals(SkaterState.ROLLING, p.getState());
		assertEquals(60f, deg(p.getHeading()), 2f);
		Contact c = new Contact();
		assertFalse("pushed clear of the box", w.contact(p.getX(), p.getY(), t.skaterRadius, p.getH(), t.maxStepUp, c));
	}

	@Test
	public void smallerHitboxPassesTenFromABoxCleanly()
	{
		// forgiving collisions: radius 8. Riding north 10 units west of a box's west face never touches it
		t.skaterRadius = 8f;
		GridCollisionWorld w = new GridCollisionWorld(20);
		w.addBlocker(C, C, 32, 32, 1f, 0f, 200, BlockerSet.SOLID, 0);
		SkatePhysics p = new SkatePhysics(t, w, C - 32 - 10, C - 300, 0);
		p.setSpeed(800);
		SkateInput in = new SkateInput();
		float startSpeed = 800;
		for (int i = 0; i < 40; i++)
		{
			p.step(DT, in);
			assertEquals(SkaterState.ROLLING, p.getState());
		}
		assertTrue("passed the box: y " + p.getY(), p.getY() > C + 100);
		assertEquals("no scrape, x unchanged", C - 42f, p.getX(), 1e-3f);
		assertEquals(0f, p.getHeading(), 1e-6f);
		assertTrue("no wall speed loss: " + p.getSpeed(), p.getSpeed() > startSpeed - 100f);
	}

	@Test
	public void smallerHitboxScrapesARotatedBoxAlongItsOwnFace()
	{
		// as scrapingARotatedBoxFollowsItsFaceNotAGridAxis with radius 8: blocking must use the same 8 so the
		// contact always finds the real face (no grid-axis fallback in the 8-12 band)
		t.skaterRadius = 8f;
		GridCollisionWorld w = rotatedWall(30);
		float ux = (float) Math.cos(Math.toRadians(30));
		float uy = (float) Math.sin(Math.toRadians(30));
		float vx = -uy;
		float vy = ux;
		float sx = C - vx * 60 - ux * 400;
		float sy = C - vy * 60 - uy * 400;
		SkatePhysics p = new SkatePhysics(t, w, sx, sy, (float) Math.toRadians(50));
		p.setSpeed(1000);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 50; i++)
		{
			p.step(DT, in);
			assertEquals(SkaterState.ROLLING, p.getState());
		}
		assertTrue("keeps its speed: " + p.getSpeed(), p.getSpeed() > 850f);
		assertEquals("eased along the face, heading " + deg(p.getHeading()), 60f, deg(p.getHeading()), 0.5f);
		float off = (p.getX() - C) * vx + (p.getY() - C) * vy;
		assertTrue("stays outside the box: " + off, off <= -32f && off >= -32f - 12f);
	}
}
