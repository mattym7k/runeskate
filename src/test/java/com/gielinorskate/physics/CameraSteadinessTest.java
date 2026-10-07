package com.gielinorskate.physics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.camera.CameraRig;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.world.BlockerSet;
import com.gielinorskate.world.GridCollisionWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The chase camera stays steady when the skater runs into objects: physics, the session's render
 * interpolation and the camera rig together, at 144 frames a second, against a rotated box, the inside corner
 * of two boxes, a fence and a box at the corner of a wall, with W held, idle, W tapped, and hops (ollies)
 * into them. Without steering the camera must never jump (no camera heading step over 20 degrees), never
 * turn faster than {@link #MAX_RATE_DEG} and never swing back and forth.
 */
public class CameraSteadinessTest
{
	private static final float T = GridCollisionWorld.TILE;
	private static final float STEP = 0.02f;
	private static final float FRAME = 1f / 144f;
	/** The rig's yaw rate cap (CameraRig.MAX_YAW_RATE, 2 PI rad/s), plus rounding. */
	private static final float MAX_RATE_DEG = 361f;
	/** A swing counts once the yaw moved this far one way; a back-and-forth is two such swings in turn. */
	private static final float SWING_DEG = 5f;

	private static final int HOLD_W = 0;
	private static final int IDLE = 1;
	private static final int TAP_W = 2;
	private static final int HOLD_W_HOPS = 3;
	private static final int HOPS = 4;
	private static final int STEER_SLOW = 5;
	private static final int SHIFT_TURN = 6;

	static GridCollisionWorld rotatedBox()
	{
		GridCollisionWorld w = new GridCollisionWorld(40);
		float a = (float) Math.toRadians(37);
		w.addBlocker(20 * T, 20 * T, 30, 20, (float) Math.cos(a), (float) Math.sin(a), 300, BlockerSet.SOLID, 0);
		return w;
	}

	static GridCollisionWorld boxCorner()
	{
		GridCollisionWorld w = new GridCollisionWorld(40);
		w.addBlocker(20 * T, 20 * T + 60, 80, 20, 1f, 0f, 300, BlockerSet.SOLID, 0);
		w.addBlocker(20 * T + 60, 20 * T, 20, 80, 1f, 0f, 300, BlockerSet.SOLID, 0);
		return w;
	}

	static GridCollisionWorld fence()
	{
		GridCollisionWorld w = new GridCollisionWorld(40);
		for (int tx = 5; tx <= 35; tx++)
		{
			w.setTile(tx, 20, GridCollisionWorld.WALL_N, 300);
			w.setTile(tx, 21, GridCollisionWorld.WALL_S, 300);
		}
		return w;
	}

	static GridCollisionWorld boxAtWallCorner()
	{
		GridCollisionWorld w = new GridCollisionWorld(40);
		for (int tx = 10; tx <= 20; tx++)
		{
			w.setTile(tx, 20, GridCollisionWorld.WALL_N, 300);
		}
		for (int ty = 10; ty <= 20; ty++)
		{
			w.setTile(20, ty, GridCollisionWorld.WALL_E | (ty == 20 ? GridCollisionWorld.WALL_N : 0), 300);
		}
		w.addBlocker(21 * T - 25, 21 * T - 25, 20, 20, 1f, 0f, 300, BlockerSet.SOLID, 0);
		return w;
	}

	/** Measurements of one run, up to its first bail (a bail and its recovery are not "steady riding"). */
	static final class Run
	{
		int cameraJumps;
		float maxRateDeg;
		int backAndForth;
		/** The board's own back-and-forth turns (pinballing in a corner): the camera may follow those. */
		int boardBackAndForth;
		String worst = "";
	}

	static Run ride(GridCollisionWorld w, float x, float y, float headingDeg, float speed, int input)
	{
		SkatePhysics p = new SkatePhysics(new SkateTuning(), w, x, y, (float) Math.toRadians(headingDeg));
		p.setSpeed(speed);
		CameraRig rig = new CameraRig();
		rig.reset(x, y, 0, p.getCameraHeading());
		SkateInput in = new SkateInput();
		Run run = new Run();
		float acc = 0f;
		RenderPose prev = RenderPose.of(p);
		float lastYaw = rig.getYaw();
		Swings cameraSwings = new Swings(lastYaw);
		Swings boardSwings = new Swings(p.getHeading());
		boolean hopQueued = false;
		for (int f = 0; f < Math.round(4f / FRAME); f++)
		{
			in.pushHeld = input == HOLD_W || input == HOLD_W_HOPS;
			in.pushPressed = input == TAP_W && f % 72 == 0;
			in.steer = input == STEER_SLOW ? 0.3f : input == SHIFT_TURN ? 1f : 0f;
			in.powerslide = input == SHIFT_TURN;
			if ((input == HOLD_W_HOPS || input == HOPS) && f % 72 == 36)
			{
				hopQueued = true;
			}
			acc += FRAME;
			while (acc >= STEP)
			{
				if (hopQueued)
				{
					in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
					hopQueued = false;
				}
				prev = RenderPose.of(p);
				float camBefore = p.getCameraHeading();
				p.step(STEP, in);
				acc -= STEP;
				if (p.getState() == SkaterState.BAILED)
				{
					return run;
				}
				float jump = (float) Math.toDegrees(Angles.absDiff(p.getCameraHeading(), camBefore));
				if (jump > 20f && in.steer == 0f)
				{
					run.cameraJumps++;
					run.worst = String.format("camera heading jumped %.0f deg at t=%.2f (%s)", jump, f * FRAME,
						p.getState());
				}
			}
			RenderPose pose = RenderPose.lerp(prev, RenderPose.of(p), acc / STEP);
			rig.update(pose.x, pose.y, pose.h, pose.cameraHeading, p.getSpeed(), p.getState(), 0f, FRAME);
			float yaw = rig.getYaw();
			run.maxRateDeg = Math.max(run.maxRateDeg, (float) Math.toDegrees(Angles.absDiff(yaw, lastYaw) / FRAME));
			lastYaw = yaw;
			run.backAndForth = cameraSwings.feed(yaw);
			run.boardBackAndForth = boardSwings.feed(p.getHeading());
		}
		return run;
	}

	/** Counts reversals of an angle that swings at least SWING_DEG each way (smaller dither is ignored). */
	static final class Swings
	{
		private float extreme;
		private int dir;
		private int count;

		Swings(float start)
		{
			extreme = start;
		}

		int feed(float angle)
		{
			float fromExtreme = (float) Math.toDegrees(Angles.wrap(angle - extreme));
			if (dir == 0)
			{
				if (Math.abs(fromExtreme) >= SWING_DEG)
				{
					dir = fromExtreme > 0 ? 1 : -1;
					extreme = angle;
				}
			}
			else if (fromExtreme * dir > 0)
			{
				extreme = angle;
			}
			else if (-fromExtreme * dir >= SWING_DEG)
			{
				count++;
				dir = -dir;
				extreme = angle;
			}
			return count;
		}
	}

	private interface Scene
	{
		GridCollisionWorld make();
	}

	private static List<String> failures(int[] inputs, boolean steadyOnly)
	{
		Scene[] scenes = {CameraSteadinessTest::rotatedBox, CameraSteadinessTest::boxCorner,
			CameraSteadinessTest::fence, CameraSteadinessTest::boxAtWallCorner};
		String[] names = {"rotated box", "box corner", "fence", "box at wall corner"};
		List<String> bad = new ArrayList<>();
		for (int s = 0; s < scenes.length; s++)
		{
			// the fence runs east-west north of the skater; the others are approached toward their corner
			float base = s == 1 || s == 3 ? 45f : 0f;
			for (float ang = -30; ang <= 30; ang += 5)
			{
				for (float speed : new float[]{0f, 150f, 400f, 700f})
				{
					for (int input : inputs)
					{
						float h = (float) Math.toRadians(base + ang);
						float x = s == 2 ? 20 * T : 20 * T - (float) Math.sin(h) * 140;
						float y = s == 2 ? 21 * T - 140 : 20 * T - (float) Math.cos(h) * 140;
						Run r = ride(scenes[s].make(), x, y, base + ang, speed, input);
						String at = String.format("%s ang %.0f speed %.0f input %d: ", names[s], ang, speed, input);
						if (r.cameraJumps > 0)
						{
							bad.add(at + r.worst);
						}
						if (r.maxRateDeg > MAX_RATE_DEG)
						{
							bad.add(at + "yaw rate " + r.maxRateDeg + " deg/s");
						}
						if (steadyOnly && r.backAndForth > r.boardBackAndForth)
						{
							bad.add(at + r.backAndForth + " back-and-forth swings, the board made " + r.boardBackAndForth);
						}
					}
				}
			}
		}
		return bad;
	}

	@Test
	public void withoutSteeringTheCameraNeverJumpsRacesOrSwingsBackAndForth()
	{
		List<String> bad = failures(new int[]{HOLD_W, IDLE, TAP_W, HOLD_W_HOPS, HOPS}, true);
		assertTrue(bad.size() + " runs:\n" + String.join("\n", bad.subList(0, Math.min(20, bad.size()))),
			bad.isEmpty());
	}

	@Test
	public void steeringIntoObjectsNeverJumpsTheCamera()
	{
		List<String> bad = failures(new int[]{STEER_SLOW, SHIFT_TURN}, false);
		assertTrue(bad.size() + " runs:\n" + String.join("\n", bad.subList(0, Math.min(20, bad.size()))),
			bad.isEmpty());
	}

	@Test
	public void aHopIntoAFenceNearlyHeadOnKeepsTheCameraBehindTheBoard()
	{
		// the evidence case: 2 degrees off head-on, the wall leaves only a sliver of the flight along it, and
		// the camera used to follow that sliver: 90 degrees to one side in the air, then back on landing
		for (float ang : new float[]{2f, -2f})
		{
			SkatePhysics p = new SkatePhysics(new SkateTuning(), fence(), 20 * T + 10, 21 * T - 60,
				(float) Math.toRadians(ang));
			p.setSpeed(600f);
			SkateInput in = new SkateInput();
			in.gestures.add(new Gesture(Gesture.Direction.UP, false, 0f));
			for (int i = 0; i < 40; i++)
			{
				p.step(STEP, in);
				assertEquals("step " + i + " " + p.getState(), Math.toRadians(ang), p.getCameraHeading(), 0.02f);
			}
		}
	}
}
