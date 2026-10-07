package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GestureRecognizerTest
{
	@Test
	public void pullDownThenFlickUpProducesOllieDirection()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50); // down 30px >= 18 -> wound up (crouching)
		assertTrue(r.isCrouching());

		r.move(100, 195, 120); // 35px from the extreme in 70ms (0.5 px/ms) -> flick
		Gesture g = r.poll();
		assertEquals(Direction.UP, g.direction);
		assertFalse(g.nollie);
		assertEquals(0f, g.turnDegrees, 0.01f);
		StrokeSnapshot snapshot = r.getLastStrokeSnapshot();
		assertEquals(g, snapshot.gesture);
		assertEquals(100f, snapshot.extremeX, 0.01f);
		assertEquals(230f, snapshot.extremeY, 0.01f);
		assertEquals(120L, snapshot.firedAtMs);
		assertTrue("path should include the wind-up and the flick", snapshot.path.size() >= 2);
		// the path starts fresh at the wind-up (the extreme), not the pre-wind-up base point
		assertEquals(100f, snapshot.path.get(0)[0], 0.01f);
		assertEquals(230f, snapshot.path.get(0)[1], 0.01f);
		float[] last = snapshot.path.get(snapshot.path.size() - 1);
		assertEquals(100f, last[0], 0.01f);
		assertEquals(195f, last[1], 0.01f);
		assertNull(r.poll());
	}

	@Test
	public void slowDragDoesNotFlickEvenPastTheDistanceThreshold()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 25, 10); // wound up down
		r.move(40, 25, 500); // 40px away from the extreme, but 490ms later
		assertNull(r.poll());
	}

	@Test
	public void noFlickWithoutHavingBeenWoundUpFirst()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 5, 20); // only 5px down, not wound up
		r.move(0, -5, 40); // only 5px net up from base, still not wound up
		r.move(0, -50, 9999); // crosses the nollie wind-up threshold only on this very call,
		// so the current point IS the wind-up extreme: no distance has been travelled yet.
		assertNull(r.poll());
	}

	@Test
	public void nolliePathWindsUpOnUpwardPullAndIsNotCrouching()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 100, 0);
		r.move(0, 70, 20); // 30px up >= 18 -> wound up as nollie
		assertFalse(r.isCrouching());

		r.move(0, 100, 60); // 30px back down from the extreme in 40ms (0.75 px/ms) -> flick
		Gesture g = r.poll();
		assertEquals(Direction.DOWN, g.direction);
		assertTrue(g.nollie);
	}

	@Test
	public void curvedPathAccumulatesTurnDegreesWhileStraightDoesNot()
	{
		GestureRecognizer straight = new GestureRecognizer();
		straight.begin(0, 0, 0);
		straight.move(0, 20, 10); // wound up down, extreme (0, 20)
		straight.move(0, -20, 60); // straight flick, single segment
		Gesture sg = straight.poll();
		assertEquals(0f, sg.turnDegrees, 0.01f);

		GestureRecognizer curved = new GestureRecognizer();
		curved.begin(0, 0, 0);
		curved.move(0, 20, 10); // wound up down, extreme (0, 20)
		curved.move(20, 20, 20); // segment of 20px, heading "right"
		curved.move(20, -15, 40); // segment of 35px, heading "up": a 90 degree turn, then flick
		Gesture cg = curved.poll();
		assertEquals(90f, cg.turnDegrees, 0.01f);
	}

	@Test
	public void reFlickAfterGracePeriodLapsesNeedsFreshWindUp()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 20, 10); // wound up down, extreme (0, 20)
		r.move(0, -20, 60); // first flick
		assertEquals(Direction.UP, r.poll().direction);

		// More than 500ms after the flick, with no renewed 18px pull: no second flick.
		r.move(0, -55, 700);
		assertNull(r.poll());
	}

	@Test
	public void crouchEndsAfterAFlickEvenWithTheMouseHeldStill()
	{
		Hand h = new Hand(400, 300).pullDown().stroke(0, -60, 8, 64, false);
		assertEquals(1, h.out.size());
		h.tick(10);
		assertFalse("re-armed, not crouching", h.r.isCrouching());
		h.tick(600); // re-arm window lapses with no motion
		assertFalse(h.r.isCrouching());
	}

	@Test
	public void realisticPlainUpLeftFlickIsExactlyOneKickflip()
	{
		// 40px pull in 4px steps over 120ms, then a 100px up-left flick in ~8px steps over 84ms
		Hand h = new Hand(400, 300).pullDown().stroke(-71, -71, 12, 84, true).tick(50).tick(500);
		assertEquals(1, h.out.size());
		Gesture g = h.out.get(0);
		assertEquals(Direction.UP_LEFT, g.direction);
		assertFalse(g.nollie);
		assertEquals("turn " + g.turnDegrees, Trick.KICKFLIP, TrickCatalog.forGesture(g));
	}

	@Test
	public void realisticPlainUpRightFlickIsExactlyOneHeelflip()
	{
		Hand h = new Hand(400, 300).pullDown().stroke(64, -77, 10, 80, true).tick(50).tick(500);
		assertEquals(1, h.out.size());
		assertEquals(Trick.HEELFLIP, TrickCatalog.forGesture(h.out.get(0)));
	}

	@Test
	public void curvedHalfCircleIsATreFlip()
	{
		// from the bottom of the pull, a counter-clockwise half circle (80px across) that starts heading
		// up-right and ends heading down-left: its chord points up-left and its path turns ~180 degrees
		Hand h = new Hand(400, 300).pullDown().arc(40, 30, 180, 12, 96).tick(40).tick(500);
		assertEquals(1, h.out.size());
		Gesture g = h.out.get(0);
		assertEquals(Direction.UP_LEFT, g.direction);
		assertEquals("turn " + g.turnDegrees, Trick.TRE_FLIP, TrickCatalog.forGesture(g));
	}

	@Test
	public void longContinuousFlickIsOneGesture()
	{
		Hand h = new Hand(400, 300).pullDown().stroke(0, -150, 15, 120, true).tick(50).tick(500);
		assertEquals(1, h.out.size());
		assertEquals(Direction.UP, h.out.get(0).direction);
	}

	@Test
	public void downThenUpReFlickWithinHalfASecondIsASecondGesture()
	{
		Hand h = new Hand(400, 300).pullDown()
			.stroke(-50, -50, 8, 64, true) // first flick, up-left
			.stroke(25, 25, 6, 60, false) // back down-right
			.stroke(-45, -45, 7, 56, true); // re-flick up-left, ~180ms after the first
		assertEquals(2, h.out.size());
		assertEquals(Direction.UP_LEFT, h.out.get(0).direction);
		assertEquals(Direction.UP_LEFT, h.out.get(1).direction);
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, h.out.get(1)));
	}

	@Test
	public void shortReturnStrokeStillReArmsWithoutAFreshWindUp()
	{
		// a 12px return is under the 18px wind-up, but it is a turnaround: the re-flick counts from it
		Hand h = new Hand(400, 300).pullDown()
			.stroke(-50, 0, 7, 56, false) // shove-it flick left
			.stroke(12, 0, 3, 30, false) // small return
			.stroke(-45, 0, 6, 48, false); // flick left again
		assertEquals(2, h.out.size());
		assertEquals(Direction.LEFT, h.out.get(1).direction);
	}

	@Test
	public void endStopsCrouchingAndDiscardsTheActiveWindUp()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 20, 10);
		assertTrue(r.isCrouching());
		r.end();
		assertFalse(r.isCrouching());
	}

	@Test
	public void windUpHeldForASecondThenFlickedIsAnOllie()
	{
		// pull down 45 px in 150 ms, hold 1 s with hand jitter, flick up 70 px in 60 ms
		for (long seed = 0; seed < 50; seed++)
		{
			MousePath p = MousePath.of(seed, 0.6, MousePath.at(0, 0, 0), MousePath.at(150, 0, 45),
				MousePath.at(1150, 1, 46), MousePath.at(1210, 2, -25));
			GestureRecognizer r = new GestureRecognizer();
			List<Gesture> out = p.feed(r, p.endMs() + 200);
			assertEquals("seed " + seed, 1, out.size());
			assertEquals("seed " + seed, Direction.UP, out.get(0).direction);
			assertFalse("seed " + seed, out.get(0).nollie);
			assertFalse("flicked: no longer crouching", r.isCrouching());
		}
	}

	@Test
	public void windUpHeldPerfectlyStillThenFlickedIsAnOllie()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		for (int i = 1; i <= 10; i++)
		{
			r.move(0, 4 * i, 12 * i); // 40 px pull over 120 ms
		}
		r.tick(1120); // a full second with the mouse held still: no drag events at all
		assertTrue(r.isCrouching());
		int[] ys = {37, 30, 20, 8, -2, -8};
		for (int i = 0; i < ys.length; i++)
		{
			r.move(0, ys[i], 1128 + 8 * i);
		}
		Gesture g = r.poll();
		assertEquals(Direction.UP, g.direction);
		assertNull(r.poll());
	}

	@Test
	public void sidewaysDriftAfterTheWindUpIsNoTrick()
	{
		// re-centring the hand: 40 px sideways over 200 ms right after the pull
		for (long seed = 0; seed < 50; seed++)
		{
			float side = seed % 2 == 0 ? 40 : -40;
			MousePath p = MousePath.of(seed, 0.8, MousePath.at(0, 0, 0), MousePath.at(150, 0, 45),
				MousePath.at(350, side, 48));
			List<Gesture> out = p.feed(new GestureRecognizer(), p.endMs() + 300);
			assertTrue("seed " + seed + " " + out, out.isEmpty());
		}
	}

	@Test
	public void slowReturnAfterAMissedFlickClearsTheCrouch()
	{
		// pull down, then drift back up toward neutral far too slowly to flick
		MousePath p = MousePath.of(3, 0.5, MousePath.at(0, 0, 0), MousePath.at(150, 0, 45),
			MousePath.at(750, 0, 10));
		GestureRecognizer r = new GestureRecognizer();
		List<Gesture> out = p.feed(r, p.endMs() + 50);
		assertTrue(out.toString(), out.isEmpty());
		assertFalse("hand back near neutral: crouch released", r.isCrouching());
	}

	@Test
	public void holdingTheWindUpKeepsTheCrouchWithNoTimeLimit()
	{
		MousePath p = MousePath.of(5, 0.6, MousePath.at(0, 0, 0), MousePath.at(150, 0, 45),
			MousePath.at(3000, 2, 46));
		GestureRecognizer r = new GestureRecognizer();
		assertTrue(p.feed(r, 3000).isEmpty());
		assertTrue(r.isCrouching());
	}

	@Test
	public void flickTwentyDegreesOffVerticalIsStillAnOllie()
	{
		assertEquals(Direction.UP, straightFlick(20).direction);
		assertEquals(Direction.UP, straightFlick(-20).direction);
	}

	@Test
	public void flickThirtyFiveDegreesOffVerticalIsADiagonal()
	{
		assertEquals(Direction.UP_LEFT, straightFlick(-35).direction);
		assertEquals(Direction.UP_RIGHT, straightFlick(35).direction);
		assertEquals(Direction.UP_LEFT, straightFlick(-60).direction);
	}

	@Test
	public void flickSeventyDegreesOffVerticalIsHorizontal()
	{
		assertEquals(Direction.LEFT, straightFlick(-70).direction);
		assertEquals(Direction.RIGHT, straightFlick(70).direction);
	}

	@Test
	public void sectorsAreTwentyFiveDegreesEitherSideOfVerticalAndBeyondSixtyFive()
	{
		assertEquals(Direction.UP, sectorAt(24));
		assertEquals(Direction.UP, sectorAt(-24));
		assertEquals(Direction.UP_RIGHT, sectorAt(26));
		assertEquals(Direction.UP_LEFT, sectorAt(-64));
		assertEquals(Direction.LEFT, sectorAt(-66));
		assertEquals(Direction.RIGHT, sectorAt(66));
		assertEquals(Direction.DOWN, sectorAt(180 - 24));
		assertEquals(Direction.DOWN_LEFT, sectorAt(-180 + 40));
		assertEquals(Direction.DOWN_RIGHT, sectorAt(180 - 40));
	}

	/** Sector of a flick `degrees` clockwise from straight up on screen. */
	private static Direction sectorAt(double degrees)
	{
		double a = Math.toRadians(degrees);
		return GestureRecognizer.sector((float) (100 * Math.sin(a)), (float) (-100 * Math.cos(a)));
	}

	@Test
	public void fiftyQuickOlliesAreAllOllies()
	{
		for (long seed = 100; seed < 150; seed++)
		{
			MousePath p = MousePath.of(seed, 0.6, MousePath.at(0, 0, 0), MousePath.at(120, 0, 45),
				MousePath.at(170, 0, -20));
			List<Gesture> out = p.feed(new GestureRecognizer(), p.endMs() + 200);
			assertEquals("seed " + seed, 1, out.size());
			assertEquals("seed " + seed, Trick.OLLIE, TrickCatalog.forGesture(out.get(0)));
		}
	}

	/** Pull 40 px down, then a clean 70 px flick at `degrees` off straight up (+ = to the right) in 60 ms. */
	private static Gesture straightFlick(double degrees)
	{
		double a = Math.toRadians(degrees);
		MousePath p = MousePath.of(1, 0, MousePath.at(0, 0, 0), MousePath.at(120, 0, 40),
			MousePath.at(140, 0, 40), MousePath.at(200, 70 * Math.sin(a), 40 - 70 * Math.cos(a)));
		List<Gesture> out = p.feed(new GestureRecognizer(), p.endMs() + 100);
		assertEquals(1, out.size());
		return out.get(0);
	}


	@Test
	public void highFlickSensitivityShortensTheWindUpAndFlick()
	{
		// 150%: wind-up 18 / 1.5 = 12 px, flick 30 / 1.5 = 20 px. A 15 px pull and a 25 px flick fire;
		// at 100% the same path never even winds up.
		MousePath p = MousePath.of(2, 0, MousePath.at(0, 0, 0), MousePath.at(100, 0, 15),
			MousePath.at(120, 0, 15), MousePath.at(160, 0, -10));
		GestureRecognizer sensitive = new GestureRecognizer();
		sensitive.setSensitivity(150);
		assertEquals(1, p.feed(sensitive, p.endMs() + 100).size());
		assertTrue(p.feed(new GestureRecognizer(), p.endMs() + 100).isEmpty());
	}

	@Test
	public void lowFlickSensitivityNeedsALongerFlick()
	{
		// 70%: flick 30 / 0.7 = 43 px, so a 36 px flick is not enough
		MousePath p = MousePath.of(2, 0, MousePath.at(0, 0, 0), MousePath.at(120, 0, 40),
			MousePath.at(140, 0, 40), MousePath.at(180, 0, 4));
		GestureRecognizer dull = new GestureRecognizer();
		dull.setSensitivity(70);
		assertTrue(p.feed(dull, p.endMs() + 100).isEmpty());
		assertEquals(1, p.feed(new GestureRecognizer(), p.endMs() + 100).size());
	}

	/** Feeds a realistic multi-sample mouse path to a recognizer and collects every gesture it emits. */
	private static final class Hand
	{
		final GestureRecognizer r = new GestureRecognizer();
		final List<Gesture> out = new ArrayList<>();
		float x;
		float y;
		long ms;

		Hand(int x, int y)
		{
			this.x = x;
			this.y = y;
			r.begin(x, y, 0);
		}

		/** 40px straight down in 4px steps over 120ms, wobbling a pixel sideways. */
		Hand pullDown()
		{
			return stroke(0, 40, 10, 120, true);
		}

		/** A straight stroke in equal steps; `wobble` adds an alternating 1px sideways error per sample. */
		Hand stroke(float dx, float dy, int steps, long durMs, boolean wobble)
		{
			float sx = x;
			float sy = y;
			long s0 = ms;
			float len = (float) Math.hypot(dx, dy);
			float px = -dy / len;
			float py = dx / len;
			for (int i = 1; i <= steps; i++)
			{
				float f = (float) i / steps;
				int w = wobble && i < steps ? (i % 2 == 0 ? 1 : -1) : 0;
				feed(sx + dx * f + px * w, sy + dy * f + py * w, s0 + durMs * i / steps);
			}
			x = sx + dx;
			y = sy + dy;
			return this;
		}

		/**
		 * A circular arc of `radius` px starting along `startDeg` (maths angle, y up on screen) and
		 * sweeping `sweepDeg` counter-clockwise, in equal steps.
		 */
		Hand arc(float radius, double startDeg, double sweepDeg, int steps, long durMs)
		{
			double c = Math.toRadians(startDeg + 90);
			double cx = x + radius * Math.cos(c);
			double cy = y - radius * Math.sin(c);
			long s0 = ms;
			double px = x;
			double py = y;
			for (int i = 1; i <= steps; i++)
			{
				double phi = Math.toRadians(startDeg - 90 + sweepDeg * i / steps);
				px = cx + radius * Math.cos(phi);
				py = cy - radius * Math.sin(phi);
				feed((float) px, (float) py, s0 + durMs * i / steps);
			}
			x = (float) px;
			y = (float) py;
			return this;
		}

		/** Time passes with the mouse held still. */
		Hand tick(long afterMs)
		{
			ms += afterMs;
			r.tick(ms);
			drain();
			return this;
		}

		private void feed(float fx, float fy, long t)
		{
			ms = t;
			r.move(Math.round(fx), Math.round(fy), t);
			drain();
		}

		private void drain()
		{
			Gesture g;
			while ((g = r.poll()) != null)
			{
				out.add(g);
			}
		}
	}

	@Test
	public void theModifierIsSampledWhenTheFlickFires()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50); // wound up
		r.setModifier(true);
		r.move(100, 195, 120); // flick
		r.setModifier(false); // released after the flick: too late to matter
		Gesture g = r.poll();
		assertEquals(Direction.UP, g.direction);
		assertTrue(g.modified);
	}

	@Test
	public void noModifierMeansAPlainGesture()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.setModifier(true);
		r.begin(100, 200, 0);
		r.move(100, 230, 50);
		r.setModifier(false);
		r.move(100, 195, 120);
		assertFalse(r.poll().modified);
	}
}
