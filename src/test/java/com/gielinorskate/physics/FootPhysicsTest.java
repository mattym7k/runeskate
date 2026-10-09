package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.world.GridCollisionWorld;
import com.gielinorskate.world.WorldTests;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** On-foot walking, sprinting, turning, stepping, jumping and collision. */
public class FootPhysicsTest
{
	static final float DT = 0.02f;
	static final float G = new SkateTuning().gravity;
	static final float R = new SkateTuning().skaterRadius;

	private static FootPhysics at(CollisionWorld w, float x, float y, float heading)
	{
		return new FootPhysics(w, G, R, x, y, w.groundHeight(x, y), heading);
	}

	private static FootInput move(float mx, float my, boolean sprint)
	{
		FootInput in = new FootInput();
		in.moveX = mx;
		in.moveY = my;
		in.sprint = sprint;
		return in;
	}

	/** Steps for {@code seconds}, collecting every event. */
	private static List<FootEvent> run(FootPhysics p, FootInput in, float seconds)
	{
		List<FootEvent> all = new ArrayList<>();
		int n = Math.round(seconds / DT);
		for (int i = 0; i < n; i++)
		{
			p.step(DT, in);
			all.addAll(p.drainEvents());
		}
		return all;
	}

	@Test
	public void walkReachesWalkSpeedWithinATenthOfASecond()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		run(p, move(0, 1, false), 0.1f);
		assertEquals(FootPhysics.WALK_SPEED, p.getSpeed(), 0.01f);
		float y0 = p.getY();
		run(p, move(0, 1, false), 1f);
		assertEquals(FootPhysics.WALK_SPEED, p.getY() - y0, 1f);
	}

	@Test
	public void walkIsOneAndAHalfTimesTheGamesWalk()
	{
		// the game's walk is one tile (128) per 0.6 s tick; Skate 3 on foot is brisker
		assertEquals(128f / 0.6f, FootPhysics.GAME_WALK_SPEED, 0.01f);
		assertEquals(1.5f, FootPhysics.WALK_SPEED_SCALE, 0f);
		assertEquals(320f, FootPhysics.WALK_SPEED, 0.01f);
	}

	@Test
	public void sprintIsTwiceTheGamesRunAndReachedWithinATenthOfASecond()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		run(p, move(0, 1, true), 0.1f);
		assertEquals(FootPhysics.SPRINT_SPEED, p.getSpeed(), 0.01f);
		// the game's run: two tiles per tick
		assertEquals(256f / 0.6f, FootPhysics.GAME_RUN_SPEED, 0.01f);
		assertEquals(2f, FootPhysics.SPRINT_SPEED_SCALE, 0f);
		assertEquals(512f / 0.6f, FootPhysics.SPRINT_SPEED, 0.01f);
		assertTrue(p.sprinting);
	}

	@Test
	public void sprintStaysWellUnderThePushTopSpeed()
	{
		// the board is still the fast way round
		assertTrue(FootPhysics.SPRINT_SPEED < 0.6f * new SkateTuning().maxPushSpeed);
	}

	@Test
	public void sprintWithoutMovingIsNotSprinting()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		run(p, move(0, 0, true), 0.2f);
		assertFalse(p.sprinting);
		assertEquals(0f, p.getSpeed(), 0f);
	}

	@Test
	public void releasingTheKeysStopsWithinATenthOfASecond()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		run(p, move(0, 1, true), 0.5f);
		run(p, move(0, 0, false), 0.1f);
		assertEquals(0f, p.getSpeed(), 0.01f);
	}

	@Test
	public void bodyTurnsTowardTheMoveDirectionAt720DegreesASecond()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		p.step(DT, move(1, 0, false));
		assertEquals(720f * DT, (float) Math.toDegrees(p.getHeading()), 0.01f);
		// 90 degrees takes 0.125 s
		run(p, move(1, 0, false), 0.12f);
		assertEquals(90f, (float) Math.toDegrees(p.getHeading()), 0.01f);
	}

	@Test
	public void turnTowardTakesTheShortWayRound()
	{
		float h = Angles.turnToward((float) Math.toRadians(170), (float) Math.toRadians(-170), 0.1f);
		assertTrue(Math.toDegrees(h) > 170 || Math.toDegrees(h) < -170);
	}

	@Test
	public void cameraRelativeKeysFollowTheCameraYaw()
	{
		FootInput in = new FootInput();
		float yaw = (float) Math.toRadians(90); // camera looking east
		in.setCameraRelative(true, false, false, false, yaw);
		assertEquals(1f, in.moveX, 1e-5f);
		assertEquals(0f, in.moveY, 1e-5f);
		in.setCameraRelative(false, false, false, true, yaw);
		// right of east is south
		assertEquals(0f, in.moveX, 1e-5f);
		assertEquals(-1f, in.moveY, 1e-5f);
		in.setCameraRelative(true, false, false, true, 0f);
		// a diagonal is normalised: same speed in all eight directions
		assertEquals(1f, Math.hypot(in.moveX, in.moveY), 1e-5f);
		assertEquals(in.moveX, in.moveY, 1e-5f);
		in.setCameraRelative(true, true, true, true, 0f);
		assertEquals(0f, in.moveX, 0f);
		assertEquals(0f, in.moveY, 0f);
	}

	@Test
	public void aFootStepsUp48()
	{
		FootPhysics p = at(TestWorlds.stepAtY(100, 0, 48), 0, 50, 0);
		run(p, move(0, 1, false), 1f);
		assertTrue("y " + p.getY(), p.getY() > 150f);
		assertEquals(48f, p.getH(), 0f);
	}

	@Test
	public void aFootStepsOntoAPlatformTheBoardCannot()
	{
		FootPhysics p = at(TestWorlds.platformAtY(100, 40), 0, 50, 0);
		run(p, move(0, 1, false), 1f);
		assertEquals(40f, p.getH(), 0f);
		assertTrue(p.getY() > 150f);
	}

	@Test
	public void aRiseOverAStepBlocksWalkingButNeverBails()
	{
		FootPhysics p = at(TestWorlds.stepAtY(100, 0, 49), 0, 50, 0);
		List<FootEvent> events = run(p, move(0, 1, true), 1f);
		assertTrue(p.getY() < 100f);
		assertEquals(0f, p.getH(), 0f);
		assertTrue(events.isEmpty());
	}

	@Test
	public void aJumpRisesAbout68AndLandsOnTheFeet()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		FootInput in = move(0, 0, false);
		in.jumpPressed = true;
		p.step(DT, in);
		assertTrue(p.isAirborne());
		assertTrue(p.drainEvents().contains(FootEvent.JUMP));
		float peak = 0f;
		List<FootEvent> events = new ArrayList<>();
		for (int i = 0; i < 100 && p.isAirborne(); i++)
		{
			p.step(DT, in);
			peak = Math.max(peak, p.getH());
			events.addAll(p.drainEvents());
		}
		// 520^2 / (2 * 2000) = 67.6; the stepped arc peaks a little lower
		assertTrue("peak " + peak, peak > 58f && peak < 68f);
		assertFalse(p.isAirborne());
		assertTrue(events.contains(FootEvent.LAND));
		assertEquals(0f, p.getH(), 0f);
		assertTrue(p.isJump());
		assertEquals(0.52f, p.getLastAirTime(), 0.03f);
	}

	@Test
	public void jumpIsAnEdgeAndOnlyFromTheGround()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		FootInput in = move(0, 0, false);
		in.jumpPressed = true;
		p.step(DT, in);
		assertFalse("cleared after the step", in.jumpPressed);
		p.drainEvents();
		float vh = p.getVerticalVelocity();
		in.jumpPressed = true;
		p.step(DT, in);
		// no double jump
		assertTrue(p.getVerticalVelocity() < vh);
		assertFalse(p.drainEvents().contains(FootEvent.JUMP));
	}

	@Test
	public void aJumpReachesABench90Tall()
	{
		CollisionWorld w = TestWorlds.platformAtY(100, 90);
		FootPhysics walker = at(w, 0, 40, 0);
		run(walker, move(0, 1, false), 1f);
		assertTrue("walking is blocked", walker.getY() < 100f);

		FootPhysics p = at(w, 0, 40, 0);
		FootInput in = move(0, 1, false);
		in.jumpPressed = true;
		run(p, in, 1.5f);
		assertFalse(p.isAirborne());
		assertEquals(90f, p.getH(), 0f);
		assertTrue(p.getY() > 100f);
	}

	@Test
	public void aJumpCannotClearA120Wall()
	{
		FootPhysics p = at(TestWorlds.wallAtY(100, 120), 0, 40, 0);
		for (int i = 0; i < 10; i++)
		{
			FootInput in = move(0, 1, true);
			in.jumpPressed = true;
			run(p, in, 0.6f);
		}
		assertTrue(p.getY() < 100f);
	}

	@Test
	public void wallsAreSlidAlong()
	{
		FootPhysics p = at(TestWorlds.wallAtY(100, 300), 0, 90, 0);
		float d = (float) Math.sqrt(0.5);
		List<FootEvent> events = run(p, move(d, d, false), 1f);
		assertTrue(p.getY() < 100f);
		// the eastward part of the walk carries on along the wall
		assertTrue("x " + p.getX(), p.getX() > 100f);
		assertTrue(events.isEmpty());
	}

	@Test
	public void shapedBoxesAreSlidAlong()
	{
		// a 64 x 64 rock at (2.5, 2.5) tiles; walking north-east into its south face slides east along it
		GridCollisionWorld w = oneObject(32, 200, false);
		float t = GridCollisionWorld.TILE;
		FootPhysics p = at(w, 2.4f * t, 2.5f * t - 32 - R - 4, 0);
		float d = (float) Math.sqrt(0.5);
		run(p, move(d, d, false), 0.6f);
		assertTrue("y " + p.getY(), p.getY() < 2.5f * t - 32 - R + 0.5f || p.getX() > 2.5f * t + 32);
		assertTrue("x " + p.getX(), p.getX() > 2.4f * t + 40f);
	}

	@Test
	public void aFallOfAnyHeightLandsOnTheFeet()
	{
		FootPhysics p = new FootPhysics(TestWorlds.flat(), G, R, 0, 0, 2000f, 0);
		assertTrue(p.isAirborne());
		List<FootEvent> events = run(p, move(0, 1, true), 3f);
		assertFalse(p.isAirborne());
		assertTrue(events.contains(FootEvent.LAND));
		assertEquals(0f, p.getH(), 0f);
		assertFalse("a fall is not a jump", p.isJump());
	}

	@Test
	public void walkingOffALedgeFallsAndAStepDownDoesNot()
	{
		FootPhysics p = at(TestWorlds.stepAtY(100, 200, 0), 0, 50, 0);
		List<FootEvent> events = run(p, move(0, 1, false), 2f);
		assertTrue(events.contains(FootEvent.FALL));
		assertTrue(events.contains(FootEvent.LAND));
		assertEquals(0f, p.getH(), 0f);

		FootPhysics q = at(TestWorlds.stepAtY(100, 40, 0), 0, 50, 0);
		List<FootEvent> stepped = run(q, move(0, 1, false), 1f);
		assertTrue(stepped.isEmpty());
		assertEquals(0f, q.getH(), 0f);
	}

	@Test
	public void theEdgeOfTheLoadedAreaIsNeverClimbed()
	{
		FootPhysics p = at(TestWorlds.edgeAtY(300), 0, 200, 0);
		for (int i = 0; i < 20; i++)
		{
			FootInput in = move(0, 1, true);
			in.jumpPressed = true;
			run(p, in, 0.3f);
			assertTrue("y " + p.getY(), p.getY() < 300f);
		}
	}

	@Test
	public void theGridWorldsOuterEdgeIsNeverClimbed()
	{
		GridCollisionWorld w = world(6);
		w.setLoadedTiles(1, 5);
		float t = GridCollisionWorld.TILE;
		FootPhysics p = at(w, 3f * t, 4.5f * t, 0);
		for (int i = 0; i < 20; i++)
		{
			FootInput in = move(0, 1, true);
			in.jumpPressed = true;
			run(p, in, 0.3f);
		}
		assertTrue("y " + p.getY(), p.getY() < 5f * t + 1f);
	}

	@Test
	public void passThroughVegetationIsWalkedThrough()
	{
		float t = GridCollisionWorld.TILE;
		FootPhysics solid = at(oneObject(32, 200, false), 2.5f * t, 1.5f * t, 0);
		run(solid, move(0, 1, false), 1.5f);
		assertTrue("rock blocks: y " + solid.getY(), solid.getY() < 2.5f * t - 32);

		FootPhysics plant = at(oneObject(32, 200, true), 2.5f * t, 1.5f * t, 0);
		run(plant, move(0, 1, false), 1.5f);
		assertTrue("plant is passed: y " + plant.getY(), plant.getY() > 3.5f * t);
	}

	@Test
	public void aJumpRemembersWhereAndWhetherItWasSprinting()
	{
		FootPhysics p = at(TestWorlds.flat(), 10, 20, 0);
		run(p, move(0, 1, true), 0.2f);
		float sx = p.getX();
		float sy = p.getY();
		FootInput in = move(0, 1, true);
		in.jumpPressed = true;
		p.step(DT, in);
		assertEquals(sx, p.getJumpStartX(), 0f);
		assertEquals(sy, p.getJumpStartY(), 0f);
		assertTrue(p.isJumpedSprinting());
	}

	@Test
	public void stepsOutOfABlockerItStartsInside()
	{
		float t = GridCollisionWorld.TILE;
		GridCollisionWorld w = oneObject(32, 200, false);
		FootPhysics p = new FootPhysics(w, G, R, 2.5f * t + 30, 2.5f * t, 0f, 0);
		assertTrue(p.getX() >= 2.5f * t + 32 + R - 0.01f);
	}

	private static GridCollisionWorld world(int size)
	{
		GridCollisionWorld w = new GridCollisionWorld(size);
		for (int x = 0; x <= size; x++)
		{
			for (int y = 0; y <= size; y++)
			{
				WorldTests.setCornerHeight(w, x, y, 0f);
			}
		}
		return w;
	}

	/** A FULL tile (2, 2) of a 6x6 world holding one centred square object, as GridBlockersTest builds it. */
	private static GridCollisionWorld oneObject(float half, float height, boolean pass)
	{
		float t = GridCollisionWorld.TILE;
		GridCollisionWorld w = world(6);
		w.setTile(2, 2, GridCollisionWorld.FULL, height);
		w.markShaped(2, 2);
		w.addObjectBlocker(2.5f * t, 2.5f * t, new float[]{-half, half, -half, half}, 0, height, pass, true);
		return w;
	}

	private static float apex(boolean mountJump)
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		FootInput in = move(0, 1, true);
		in.jumpPressed = true;
		in.mountJump = mountJump;
		float top = 0f;
		for (int i = 0; i < 100; i++)
		{
			p.step(DT, in);
			top = Math.max(top, p.getH());
		}
		return top;
	}

	@Test
	public void aJumpOntoTheBoardIsABiggerHopThanAPlainJump()
	{
		float plain = apex(false);
		float mount = apex(true);
		// v^2 / 2g: 520 -> about 68, the mount hop about 109
		assertEquals(FootPhysics.JUMP_VH * FootPhysics.JUMP_VH / (2 * G), plain, 12f);
		assertEquals(FootPhysics.MOUNT_JUMP_VH * FootPhysics.MOUNT_JUMP_VH / (2 * G), mount, 12f);
		assertTrue(mount > plain * 1.4f);
	}

	@Test
	public void theMountFlagOnlyMattersAtTakeOff()
	{
		FootPhysics p = at(TestWorlds.flat(), 0, 0, 0);
		FootInput in = move(0, 1, true);
		in.mountJump = true;
		run(p, in, 0.5f);
		assertFalse(p.isAirborne());
		assertEquals(0f, p.getH(), 0f);
	}
}
