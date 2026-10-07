package com.gielinorskate.physics;

import static org.junit.Assert.assertTrue;
import com.gielinorskate.world.BlockerSet;
import com.gielinorskate.world.GridCollisionWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Sweep: ride into fences (tall and low, both sides, a gap's end posts), wall corners (inner and outer), rotated
 * solid boxes and rocks, grindable boxes, a box against a fence and a V of two boxes, at incidences 0-85 degrees
 * and 200-1500 u/s, coasting, pushing or ollieing into it. Then get away as a player would: find the most open
 * way (looked for again every quarter second), steer towards it with W held (also the long way round for a
 * near head-on hit, and with Shift for a tight carve) and ride on once within 15 degrees of it. Whatever was hit,
 * the skater must never be wedged: within 1.5 s back on the board (plus the deliberate stumble steering lock)
 * it is more than 30 units clear of every blocker, or riding away along a neighbouring wall more than a tile from
 * the hit; and every bail ends by itself.
 */
public class WallEscapeSweepTest
{
	private static final float DT = 0.02f;
	private static final float T = GridCollisionWorld.TILE;
	private static final float C = 10 * T;
	private static final float CLEAR = 30f;
	private static final float ESCAPE_TIME = 1.5f;

	/** One geometry with a point on its face and the face's outward normal (the approach side). */
	private static final class Scene
	{
		final String name;
		final GridCollisionWorld world;
		final float px;
		final float py;
		final float nx;
		final float ny;

		Scene(String name, GridCollisionWorld world, float px, float py, float nDeg)
		{
			this.name = name;
			this.world = world;
			this.px = px;
			this.py = py;
			// normal as a heading: 0 = +y, clockwise
			this.nx = (float) Math.sin(Math.toRadians(nDeg));
			this.ny = (float) Math.cos(Math.toRadians(nDeg));
			world.rebuildBlockers();
			world.rebuildGrinds();
		}
	}

	private static GridCollisionWorld fenceRow(float height, int fromTx, int toTx, int gapTx)
	{
		GridCollisionWorld w = new GridCollisionWorld(20);
		for (int tx = fromTx; tx <= toTx; tx++)
		{
			if (tx != gapTx)
			{
				w.setTile(tx, 10, GridCollisionWorld.WALL_N, height);
			}
		}
		return w;
	}

	private static List<Scene> scenes()
	{
		List<Scene> s = new ArrayList<>();
		float fenceY = 11 * T;
		for (float hgt : new float[]{40f, 60f, 140f, 300f})
		{
			s.add(new Scene("fence" + (int) hgt, fenceRow(hgt, 4, 16, -1), C + 40, fenceY, 180));
			// the far side of the same fence line (approached from the north)
			s.add(new Scene("fenceN" + (int) hgt, fenceRow(hgt, 4, 16, -1), C + 40, fenceY, 0));
		}
		// a fence with a one-tile gap: ride into the post at the end of the gap
		s.add(new Scene("fenceGapEnd", fenceRow(60f, 4, 16, 10), 10 * T + 6, fenceY, 180));
		s.add(new Scene("fenceGapEnd2", fenceRow(60f, 4, 16, 10), 11 * T - 6, fenceY, 180));
		// inner corner of an L: north wall over tiles 4..10, east wall up tiles 4..10 of column 10
		for (float hgt : new float[]{60f, 300f})
		{
			GridCollisionWorld w = fenceRow(hgt, 4, 10, -1);
			for (int ty = 4; ty <= 10; ty++)
			{
				w.setTile(10, ty, ty == 10 ? GridCollisionWorld.WALL_N | GridCollisionWorld.WALL_E : GridCollisionWorld.WALL_E, hgt);
			}
			s.add(new Scene("innerCorner" + (int) hgt, w, 11 * T, 11 * T, 225));
			// the outer corner of the same L, from the north-east
			GridCollisionWorld w2 = fenceRow(hgt, 4, 10, -1);
			for (int ty = 4; ty <= 10; ty++)
			{
				w2.setTile(10, ty, ty == 10 ? GridCollisionWorld.WALL_N | GridCollisionWorld.WALL_E : GridCollisionWorld.WALL_E, hgt);
			}
			s.add(new Scene("outerCorner" + (int) hgt, w2, 11 * T, 11 * T, 45));
		}
		// rotated solid boxes (a wall piece, a rock)
		for (float a : new float[]{0f, 30f, 45f, 70f})
		{
			GridCollisionWorld w = new GridCollisionWorld(20);
			float r = (float) Math.toRadians(a);
			w.addBlocker(C, C, 300, 20, (float) Math.cos(r), (float) Math.sin(r), 200, BlockerSet.SOLID, 0);
			// the face normal of the -v face: v = (-sin, cos), outward -v = (sin, -cos) -> heading 180 - a
			float fx = C - (float) -Math.sin(r) * 20;
			float fy = C - (float) Math.cos(r) * 20;
			s.add(new Scene("box" + (int) a, w, fx, fy, 180 - a));
			GridCollisionWorld rock = new GridCollisionWorld(20);
			rock.addBlocker(C, C, 30, 30, (float) Math.cos(r), (float) Math.sin(r), 200, BlockerSet.SOLID, 0);
			s.add(new Scene("rock" + (int) a, rock, C - (float) -Math.sin(r) * 30, C - (float) Math.cos(r) * 30, 180 - a));
		}
		// grindable boxes (a fence piece, a bench): landable, LOW up to 120, rails on top
		for (float hgt : new float[]{40f, 60f, 100f, 150f})
		{
			for (float a : new float[]{0f, 45f})
			{
				GridCollisionWorld w = new GridCollisionWorld(20);
				w.addGrindableBlocker(C, C, new float[]{-200f, 200f, -4f, 4f}, (int) (a / 360f * 2048f), hgt);
				for (com.gielinorskate.world.GrindSegment g : com.gielinorskate.world.ObjectRailShape.segments(C, C,
					new float[]{-200f, 200f, -4f, 4f}, (int) (a / 360f * 2048f), 0f))
				{
					w.addGrindSegment(new com.gielinorskate.world.GrindSegment(g.x0, g.y0, g.x1, g.y1, hgt));
				}
				Scene probe = new Scene("x", w, C, C, 0);
				float[] n = faceOf(probe.world, C, C);
				s.add(new Scene("grindable" + (int) hgt + "a" + (int) a, w, n[0], n[1], n[2]));
			}
		}
		// fence plus a box against it: the corner between them
		for (float hgt : new float[]{60f, 300f})
		{
			GridCollisionWorld w = fenceRow(hgt, 4, 16, -1);
			w.addBlocker(C, fenceY - 40, 30, 40, 1f, 0f, 200, BlockerSet.SOLID, 0);
			s.add(new Scene("fenceBoxCorner" + (int) hgt, w, C + 30, fenceY, 135));
			s.add(new Scene("fenceBoxFace" + (int) hgt, w, C + 30, fenceY - 40, 90));
		}
		// a V of two boxes meeting at a point
		{
			GridCollisionWorld w = new GridCollisionWorld(20);
			float r1 = (float) Math.toRadians(45);
			float r2 = (float) Math.toRadians(135);
			w.addBlocker(C - 140, C + 140, 200, 12, (float) Math.cos(r1), (float) Math.sin(r1), 200, BlockerSet.SOLID, 0);
			w.addBlocker(C + 140, C + 140, 200, 12, (float) Math.cos(r2), (float) Math.sin(r2), 200, BlockerSet.SOLID, 0);
			s.add(new Scene("vee", w, C, C + 10, 180));
		}
		return s;
	}

	/** Point and outward normal heading of the -y-most face of the box near (x, y): probes from the south. */
	private static float[] faceOf(GridCollisionWorld w, float x, float y)
	{
		Contact c = new Contact();
		for (float d = 300; d > 0; d -= 1f)
		{
			if (w.contact(x, y - d, 0.01f, -1e4f, 0f, c))
			{
				return new float[]{x, y - d, (float) Math.toDegrees(Math.atan2(c.nx, c.ny))};
			}
		}
		return new float[]{x, y, 180};
	}

	/** Clear of every blocker taller than a step by more than CLEAR (standing on top of one counts too). */
	private static boolean clear(GridCollisionWorld w, SkatePhysics p, SkateTuning t)
	{
		return !w.contact(p.getX(), p.getY(), CLEAR, p.getH(), t.maxStepUp, new Contact());
	}

	/**
	 * Of 32 headings, the one with the longest free run (up to 3 tiles) from the skater's position, judged by
	 * the world's own blocking in 4-unit steps (moving out of an overlap is always free).
	 */
	private static float openHeading(GridCollisionWorld w, SkatePhysics p, SkateTuning t)
	{
		float best = p.getHeading();
		float bestRun = -1f;
		for (int k = 0; k < 32; k++)
		{
			float a = (float) (k * 2 * Math.PI / 32);
			float sx = (float) Math.sin(a);
			float sy = (float) Math.cos(a);
			float run = 0f;
			while (run < 3 * T)
			{
				float x0 = p.getX() + sx * run;
				float y0 = p.getY() + sy * run;
				float x1 = x0 + sx * 4f;
				float y1 = y0 + sy * 4f;
				if (w.blockerTop(x0, y0, x1, y1, t.skaterRadius) > p.getH() + t.maxStepUp
					|| w.groundHeight(x1, y1) > p.getH() + t.maxStepUp)
				{
					break;
				}
				run += 4f;
			}
			// the longest run; among (nearly) equal runs the one needing the least turn, so a skater along a
			// wall does not flip between its two open ends
			boolean longer = run > bestRun + 8f;
			boolean asLong = run > bestRun - 8f && Angles.absDiff(a, p.getHeading()) < Angles.absDiff(best, p.getHeading());
			if (bestRun < 0f || longer || asLong)
			{
				bestRun = Math.max(run, bestRun);
				best = a;
			}
		}
		return Angles.wrap(best);
	}

	/**
	 * Riding away along a neighbouring wall counts too (out of a corner, along the fence beside a box): more
	 * than a tile from where it hit and still rolling.
	 */
	private static boolean awayAlong(SkatePhysics p, float hitX, float hitY)
	{
		return Math.hypot(p.getX() - hitX, p.getY() - hitY) > T && Math.abs(p.getSpeed()) > 150f;
	}

	private static boolean touching(GridCollisionWorld w, SkatePhysics p, SkateTuning t)
	{
		return w.contact(p.getX(), p.getY(), t.skaterRadius + 2f, p.getH(), t.maxStepUp, new Contact());
	}

	enum Escape
	{
		STEER_AWAY_PUSH, STEER_OTHER_PUSH, SHIFT_STEER_AWAY, NONE
	}

	/** Returns null on success, else a description of how it got stuck. */
	private static String run(Scene sc, float incidenceDeg, float speed, boolean pushIn, boolean ollie, Escape esc,
		SkateTuning t)
	{
		// travel direction: -n turned by the incidence (clockwise)
		float nHead = (float) Math.atan2(sc.nx, sc.ny);
		float travel = Angles.wrap(nHead + Angles.PI + (float) Math.toRadians(incidenceDeg));
		float dx = (float) Math.sin(travel);
		float dy = (float) Math.cos(travel);
		float start = 260f;
		SkatePhysics p = new SkatePhysics(t, sc.world, sc.world.getGrinds(), sc.px - dx * start + sc.nx * 1f,
			sc.py - dy * start + sc.ny * 1f, travel);
		p.setSpeed(speed);
		SkateInput in = new SkateInput();
		in.pushHeld = pushIn;
		boolean hit = false;
		boolean popped = false;
		for (int i = 0; i < 150; i++)
		{
			// ollie in time to be in the air at the face (a pop lasts about 0.75 s; go 0.15 s before)
			float toFace = (sc.px - p.getX()) * dx + (sc.py - p.getY()) * dy;
			if (ollie && !popped && p.getState() == SkaterState.ROLLING && toFace < Math.abs(p.getSpeed()) * 0.15f + 20f)
			{
				popped = true;
				in.gestures.add(new com.gielinorskate.tricks.Gesture(com.gielinorskate.tricks.Gesture.Direction.UP, false, 0f));
			}
			p.step(DT, in);
			if (p.getState() == SkaterState.BAILED
				|| (p.getState() != SkaterState.GRINDING && p.getState() != SkaterState.AIRBORNE && touching(sc.world, p, t)))
			{
				hit = true;
				break;
			}
			if (p.getState() == SkaterState.GRINDING || p.getState() == SkaterState.AIRBORNE)
			{
				continue; // caught the rail or still in the air: carry on until it hits or rides away
			}
		}
		if (!hit)
		{
			return null;
		}
		in.pushHeld = false;
		float hitX = p.getX();
		float hitY = p.getY();
		// away = towards the most open space, as a player looks for it (a hit can end up anywhere: over a low
		// fence into the corner behind it, round the far side of a box)
		float nHeadAway = 0f;
		boolean chosen = false;
		float turn = 0f;
		int bailSteps = 0;
		int freeSteps = 0;
		// a hard hit locks steering for stumbleTime on purpose (a penalty, not a trap): the clock allows for it
		while (freeSteps < (ESCAPE_TIME + t.stumbleTime) / DT)
		{
			if (p.getState() == SkaterState.BAILED)
			{
				if (esc == Escape.STEER_OTHER_PUSH)
				{
					return null; // the recovery already faces away: turning the long way round is not an escape
				}
				// a bail must end by itself; the escape clock starts once back on the board
				if (++bailSteps > (t.bailDuration + t.bailAutoReset + 0.2f) / DT)
				{
					return "bail never ended";
				}
				in.steer = 0f;
				in.pushHeld = false;
				in.powerslide = false;
				chosen = false;
			}
			else
			{
				bailSteps = 0;
				// look again for the open way every quarter second, as a player would while turning
				if (freeSteps % 12 == 0)
				{
					chosen = false;
				}
				if (!chosen)
				{
					chosen = true;
					nHeadAway = openHeading(sc.world, p, t);
					// which way turns the heading toward it (the other way for STEER_OTHER_PUSH); the long way
					// round stays the long way when the target is looked for again
					float away = Angles.wrap(nHeadAway - p.getHeading()) >= 0 ? 1f : -1f;
					if (turn == 0f || esc != Escape.STEER_OTHER_PUSH)
					{
						turn = esc == Escape.STEER_OTHER_PUSH ? -away : away;
					}
				}
				// steer until the board points away from the face (within 15 degrees of the most open way), then ride
				// straight on, as a player would; W held throughout
				boolean facingAway = Math.cos(Angles.wrap(nHeadAway - p.getHeading())) > Math.cos(Math.toRadians(15));
				boolean steering = esc != Escape.NONE && !facingAway;
				in.steer = steering ? turn : 0f;
				in.pushHeld = esc != Escape.NONE;
				in.powerslide = steering && esc == Escape.SHIFT_STEER_AWAY;
				if (esc != Escape.NONE && (clear(sc.world, p, t) || awayAlong(p, hitX, hitY)))
				{
					return null;
				}
				freeSteps++;
			}
			p.step(DT, in);
		}
		if (esc == Escape.NONE)
		{
			return null;
		}
		return "not clear after " + ESCAPE_TIME + " s: " + p.getState() + " at " + p.getX() + "," + p.getY() + " h " + p.getH()
			+ " heading " + Math.toDegrees(p.getHeading()) + " speed " + p.getSpeed();
	}

	private static List<String> sweep(SkateTuning t)
	{
		List<String> fails = new ArrayList<>();
		for (Scene sc : scenes())
		{
			for (float inc = 0; inc <= 85; inc += 5)
			{
				for (float speed : new float[]{200, 400, 700, 1000, 1200, 1500})
				{
					for (int mode = 0; mode < 3; mode++)
					{
						boolean pushIn = mode == 1;
						boolean ollie = mode == 2;
						for (Escape esc : Escape.values())
						{
							if (esc == Escape.STEER_OTHER_PUSH && inc > 15)
							{
								continue; // only near head-on is turning either way a way out
							}
							String r = run(sc, inc, speed, pushIn, ollie, esc, t);
							if (r != null)
							{
								fails.add(sc.name + " inc " + inc + " speed " + speed + " push " + pushIn + " ollie " + ollie + " " + esc + ": " + r);
							}
						}
					}
				}
			}
		}
		return fails;
	}

	private static void assertNoneStuck(List<String> fails)
	{
		StringBuilder sb = new StringBuilder(fails.size() + " stuck configurations");
		for (int i = 0; i < Math.min(5, fails.size()); i++)
		{
			sb.append("; ").append(fails.get(i));
		}
		assertTrue(sb.toString(), fails.isEmpty());
	}

	@Test
	public void neverStuckOnAWallFenceOrBox()
	{
		assertNoneStuck(sweep(new SkateTuning()));
	}

	@Test
	public void neverStuckWithForgivingCollisions()
	{
		// the "Forgiving collisions" option (SkateSession: radius 8, bails above 1500)
		SkateTuning t = new SkateTuning();
		t.skaterRadius = 8f;
		t.wallBailSpeed = 1500f;
		assertNoneStuck(sweep(t));
	}
}
