package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Snapshot interpolation of a party ghost (GhostTimeline through GhostPredictor), with hand-made updates. */
public class GhostTimelineTest
{
	private static final GhostPredictor.Ground FLAT = (x, y) -> 0f;
	/** Our clock minus the sender's. */
	private static final float OFFSET = 7.5f;
	private static final float LATENCY = 0.1f;
	/** Small coordinates: float rounding of world coordinates (1/8 unit there) would hide what these tests measure. */
	private static final float X0 = 12_800f;
	private static final float Y0 = 25_600f;
	private static final float G = GhostPredictor.GRAVITY;

	/** The true skater at a time: {x, y, h, heading, vx, vy, vh, state ordinal, turn rate}. */
	private interface Path
	{
		float[] at(double t);
	}

	/** Carving round a circle at {@code v} u/s, turning at {@code w} rad/s. */
	private static Path carve(float v, float w, float heading0)
	{
		return t ->
		{
			double th = heading0 + w * t;
			return new float[]{(float) (X0 - v / w * Math.cos(th)), (float) (Y0 + v / w * Math.sin(th)), 0f,
				Angles.wrap((float) th), (float) (v * Math.sin(th)), (float) (v * Math.cos(th)), 0f,
				SkaterState.ROLLING.ordinal(), w};
		};
	}

	/** Straight along x at 600 u/s, popping at 820 u/s at {@code pop}, landing on flat ground. */
	private static Path jump(double pop)
	{
		double land = pop + 2 * 820.0 / G;
		return t ->
		{
			boolean air = t > pop && t < land;
			double s = t - pop;
			float h = air ? (float) (820 * s - G / 2 * s * s) : 0f;
			float vh = air ? (float) (820 - G * s) : 0f;
			return new float[]{(float) (X0 + 600 * t), Y0, h, Angles.PI / 2, 600f, 0f, vh,
				(air ? SkaterState.AIRBORNE : SkaterState.ROLLING).ordinal(), 0f};
		};
	}

	private static double landOf(double pop)
	{
		return pop + 2 * 820.0 / G;
	}

	/**
	 * The update sent at {@code sent} with positions {@code ago} seconds before it, events {@code ev} that happened
	 * {@code evAgo} (by bit) before it.
	 */
	private static SkateGhostUpdate update(Path p, double sent, int seq, int ev, float[] evAgo, Trick flip,
		float flipTime, double... ago)
	{
		float[] f = p.at(sent);
		SkaterState st = SkaterState.values()[(int) f[7]];
		Trick flipTrick = flip != null && flipTime < flip.duration ? flip : null;
		GhostState frame = GhostFeed.frame(330, 0, f[0], f[1], f[2], f[3], f[4], f[5], f[6], st, null, flipTrick,
			flipTrick == null ? 0f : flipTime / flip.duration, f[8]);
		SkateGhostUpdate m = GhostCodec.encode(frame, ev, flip);
		m.seq = seq;
		int n = ago.length;
		float[] a = new float[n];
		float[] xs = new float[n];
		float[] ys = new float[n];
		float[] hs = new float[n];
		float[] hds = new float[n];
		SkaterState[] sts = new SkaterState[n];
		for (int i = 0; i < n; i++)
		{
			float[] s = p.at(sent - ago[i]);
			a[i] = (float) ago[i];
			xs[i] = s[0];
			ys[i] = s[1];
			hs[i] = s[2];
			hds[i] = s[3];
			sts[i] = SkaterState.values()[(int) s[7]];
		}
		m.tj = GhostFeed.wire(GhostTrajectory.timeMs((float) sent), ev, evAgo, n, a, xs, ys, hs, hds, sts,
			m.x, m.y, m.h, m.hd);
		return m;
	}

	private static SkateGhostUpdate update(Path p, double sent, int seq)
	{
		return update(p, sent, seq, 0, null, null, 0f, 0.15, 0.3, 0.45);
	}

	private static boolean accept(GhostPredictor g, SkateGhostUpdate m, float now, GhostPredictor.Ground ground)
	{
		return g.accept(GhostCodec.decode(m), GhostTrajectory.decode(m.tj, m.ev, m.x, m.y, m.h, m.hd), now, ground);
	}

	/** A run: updates every 0.6 s of {@code p} delivered after LATENCY, drawn every {@code dt}. */
	private static final class Run
	{
		final List<float[]> drawn = new ArrayList<>();
		final GhostPredictor g = new GhostPredictor();

		/**
		 * Draws from sender time {@code from} to {@code to}; updates sent at multiples of 0.6 s arrive after
		 * LATENCY, except those {@code skip} says not to deliver.
		 */
		Run go(Path p, double from, double to, double dt, java.util.function.IntPredicate skip)
		{
			int next = 0;
			for (double s = from; s < to; s += dt)
			{
				while (next * 0.6 + LATENCY <= s)
				{
					if (!skip.test(next))
					{
						accept(g, update(p, next * 0.6, next + 1), (float) (next * 0.6 + LATENCY) + OFFSET, FLAT);
					}
					next++;
				}
				if (next == 0)
				{
					continue;
				}
				RenderPose pose = g.pose((float) s + OFFSET, FLAT);
				drawn.add(new float[]{(float) s, pose.x, pose.y, pose.h, pose.heading, (float) g.tp,
					g.rate, g.speed()});
			}
			return this;
		}
	}

	@Test
	public void aCarveIsSmoothThroughEverySampleAndOnTheTruePath()
	{
		Path p = carve(800f, 1.5f, 0.2f);
		double dt = 0.001;
		Run r = new Run().go(p, 0, 6, dt, i -> false);
		double maxStep = 0;
		double maxDv = 0;
		double maxErr = 0;
		for (int i = 2; i < r.drawn.size(); i++)
		{
			float[] a = r.drawn.get(i - 2);
			float[] b = r.drawn.get(i - 1);
			float[] c = r.drawn.get(i);
			if (a[0] < 2)
			{
				continue;
			}
			maxStep = Math.max(maxStep, Math.hypot(c[1] - b[1], c[2] - b[2]));
			double dvx = ((c[1] - b[1]) - (b[1] - a[1])) / dt;
			double dvy = ((c[2] - b[2]) - (b[2] - a[2])) / dt;
			maxDv = Math.max(maxDv, Math.hypot(dvx, dvy));
			float[] t = p.at(c[5]);
			maxErr = Math.max(maxErr, Math.hypot(c[1] - t[0], c[2] - t[1]));
		}
		// at most 800 u/s (playback within 6 % of our clock) every millisecond
		assertTrue("step " + maxStep, maxStep <= 800 * 1.06 * dt + 0.01);
		// no velocity jump at a sample: per millisecond the velocity turns by v * w * dt = 1.2 u/s (and the playback
		// speed eases by a fraction of a percent)
		assertTrue("dv " + maxDv, maxDv <= 8);
		// on the circle (positions sent to whole units, headings to 1/100 rad)
		assertTrue("error " + maxErr, maxErr < 2.5);
		// interpolating, never past the last sample
		for (float[] d : r.drawn)
		{
			if (d[0] >= 2)
			{
				assertTrue(d[6] >= 1 - GhostTimeline.MAX_RATE_CHANGE - 1e-6);
			}
		}
	}

	@Test
	public void headingsWrapTheShortWayRound()
	{
		// turning clockwise through the +-PI seam at 2 rad/s
		Path p = carve(500f, 2f, 2.5f);
		Run r = new Run().go(p, 0, 4, 0.005, i -> false);
		for (int i = 1; i < r.drawn.size(); i++)
		{
			float[] a = r.drawn.get(i - 1);
			float[] b = r.drawn.get(i);
			if (a[0] < 1.5)
			{
				continue;
			}
			float turned = Angles.wrap(b[4] - a[4]);
			assertTrue(a[0] + ": " + turned, turned >= 0f && turned <= 2f * 1.07f * 0.005f + 1e-3f);
			float[] t = p.at(b[5]);
			assertTrue(Angles.absDiff(b[4], t[3]) < 0.02f);
		}
	}

	@Test
	public void aDryBufferExtrapolatesThenSettlesAndRecoversWithoutSnapping()
	{
		Path p = carve(900f, 0.8f, 0f);
		double dt = 1 / 60.0;
		// updates 5..7 never arrive: 1.8 s without any
		Run r = new Run().go(p, 0, 9, dt, i -> i >= 5 && i <= 7);
		double maxStep = 0;
		double maxDv = 0;
		boolean underran = false;
		float slowest = Float.MAX_VALUE;
		for (int i = 2; i < r.drawn.size(); i++)
		{
			float[] a = r.drawn.get(i - 2);
			float[] b = r.drawn.get(i - 1);
			float[] c = r.drawn.get(i);
			if (a[0] < 1.5)
			{
				continue;
			}
			maxStep = Math.max(maxStep, Math.hypot(c[1] - b[1], c[2] - b[2]));
			maxDv = Math.max(maxDv, Math.hypot((c[1] - 2 * b[1] + a[1]) / dt, (c[2] - 2 * b[2] + a[2]) / dt));
			assertTrue(c[6] >= 0 && c[6] <= 1 + GhostTimeline.MAX_CATCH_UP + 1e-6);
			if (c[6] < 1 - GhostTimeline.MAX_RATE_CHANGE)
			{
				underran = true;
			}
			if (c[0] > 4.6 && c[0] < 4.85)
			{
				slowest = Math.min(slowest, c[7]);
			}
		}
		assertTrue("the gap was noticed", underran);
		// settled to (nearly) a stop well into the silence instead of running on
		assertTrue("speed " + slowest, slowest < 150);
		// never a jump in position or velocity, even when the updates resume: at most a slight fast-forward
		assertTrue("step " + maxStep, maxStep <= 900 * (1 + GhostTimeline.MAX_CATCH_UP) * dt * 1.1);
		assertTrue("dv " + maxDv, maxDv < 3000);
		// back on the true path afterwards
		float[] last = r.drawn.get(r.drawn.size() - 1);
		float[] t = p.at(last[5]);
		assertEquals(0, Math.hypot(last[1] - t[0], last[2] - t[1]), 2.5);
	}

	@Test
	public void aPopPlaysWhenThePlaybackReachesItAndTheFlipStartsThere()
	{
		double pop = 3.03;
		Path p = jump(pop);
		GhostPredictor g = new GhostPredictor();
		// a quiet stretch first so the delay has settled
		for (int i = 0; i <= 5; i++)
		{
			accept(g, update(p, i * 0.6, i + 1), (float) (i * 0.6 + LATENCY) + OFFSET, FLAT);
		}
		// the pop's update was held back by the rate limit: sent 0.12 s after the pop, which it says
		float[] evAgo = new float[GhostTrajectory.EVENT_BITS];
		evAgo[0] = 0.12f;
		evAgo[6] = 0.12f;
		double sent = pop + 0.12;
		SkateGhostUpdate m = update(p, sent, 7, GhostCodec.EV_POP | GhostCodec.EV_TRICK, evAgo, Trick.KICKFLIP,
			0.12f, 0.1, 0.25);
		float arrive = (float) (sent + LATENCY) + OFFSET;
		int popsBefore = -1;
		boolean seen = false;
		for (double s = 3.0; s < 5.5; s += 0.004)
		{
			float now = (float) s + OFFSET;
			if (!seen && now >= arrive)
			{
				accept(g, m, arrive, FLAT);
				seen = true;
				// received, but not played yet
				assertEquals(0, g.popCount());
			}
			if (s > 3.6 + LATENCY && s < 3.62 + LATENCY)
			{
				accept(g, update(p, 3.6, 8, 0, null, Trick.KICKFLIP, (float) (3.6 - pop), 0.15, 0.3, 0.45),
					(float) (3.6 + LATENCY) + OFFSET, FLAT);
			}
			RenderPose pose = g.pose(now, FLAT);
			double tp = g.tp;
			if (popsBefore == 0 && g.popCount() == 1)
			{
				// the pop plays on the frame the playback passes it
				assertTrue("played at " + tp, tp >= pop && tp < pop + 0.006);
			}
			popsBefore = g.popCount();
			if (tp > pop + 0.05 && tp < pop + Trick.KICKFLIP.duration - 0.01)
			{
				// the board turns from the pop on the member's clock
				float expect = Trick.KICKFLIP.rollTurns * Angles.TWO_PI
					* SkatePhysics.flipEase((float) ((tp - pop) / Trick.KICKFLIP.duration));
				assertEquals(expect, pose.boardRoll, 0.02f);
			}
			if (tp < pop - 0.01)
			{
				assertEquals(0f, pose.boardRoll, 0f);
			}
		}
		assertEquals(1, g.popCount());
		assertNotNull(g.labelTrick());
	}

	@Test
	public void jumpsFollowTheGravityArcAndLandWithoutABump()
	{
		double pop = 2.5;
		Path p = jump(pop);
		double dt = 0.002;
		Run r = new Run().go(p, 0, 5, dt, i -> false);
		double maxErr = 0;
		double maxDvh = 0;
		for (int i = 2; i < r.drawn.size(); i++)
		{
			float[] a = r.drawn.get(i - 2);
			float[] b = r.drawn.get(i - 1);
			float[] c = r.drawn.get(i);
			if (a[0] < 1.5)
			{
				continue;
			}
			float[] t = p.at(c[5]);
			maxErr = Math.max(maxErr, Math.abs(c[3] - t[2]));
			boolean inAir = a[5] > pop + 0.01 && c[5] < landOf(pop) - 0.01;
			if (inAir)
			{
				maxDvh = Math.max(maxDvh, Math.abs((c[3] - 2 * b[3] + a[3]) / dt + G * dt));
			}
		}
		// heights sent to whole units: the arc is the true one within that
		assertTrue("error " + maxErr, maxErr < 1.5);
		// in the air the vertical speed changes by gravity, and at a sample by no more than whole-unit heights make it
		assertTrue("vertical speed jumps by " + maxDvh, maxDvh < 20);
	}

	@Test
	public void aLostLandingIsFoundOnTheArcNotGuessedAtTheNextSample()
	{
		double pop = 2.4;
		Path p = jump(pop);
		double land = landOf(pop);
		// the update just after the landing is lost (with the landing's own time)
		int lost = (int) Math.ceil(land / 0.6);
		Run r = new Run().go(p, 0, 5, 0.004, i -> i == lost);
		for (float[] d : r.drawn)
		{
			if (d[0] < 1.5)
			{
				continue;
			}
			float[] t = p.at(d[5]);
			assertTrue(d[5] + ": " + d[3] + " vs " + t[2], Math.abs(d[3] - t[2]) < 25);
		}
	}

	@Test
	public void aReorderedUpdateFillsInWithoutAJump()
	{
		Path p = carve(700f, -1.1f, 1f);
		GhostPredictor g = new GhostPredictor();
		List<float[]> drawn = new ArrayList<>();
		double dt = 1 / 60.0;
		for (double s = 0; s < 6; s += dt)
		{
			for (int i = 0; i < 10; i++)
			{
				// update 5 overtakes update 4 (which arrives 0.3 s late)
				double arrive = i * 0.6 + LATENCY + (i == 4 ? 0.4 : 0);
				if (arrive <= s && arrive > s - dt)
				{
					accept(g, update(p, i * 0.6, i + 1), (float) arrive + OFFSET, FLAT);
				}
			}
			if (g.latest() != null)
			{
				RenderPose pose = g.pose((float) s + OFFSET, FLAT);
				drawn.add(new float[]{(float) s, pose.x, pose.y, (float) g.tp});
			}
		}
		for (int i = 2; i < drawn.size(); i++)
		{
			float[] a = drawn.get(i - 2);
			float[] b = drawn.get(i - 1);
			float[] c = drawn.get(i);
			if (a[0] < 2)
			{
				continue;
			}
			assertTrue(Math.hypot(c[1] - b[1], c[2] - b[2]) <= 700 * 1.07 * dt + 0.5);
			assertTrue(Math.hypot((c[1] - 2 * b[1] + a[1]) / dt, (c[2] - 2 * b[2] + a[2]) / dt) < 600);
			float[] t = p.at(c[3]);
			assertTrue(Math.hypot(c[1] - t[0], c[2] - t[1]) < 4);
		}
	}

	@Test
	public void updatesWithoutRoomForTheTimelineArePlacedByArrival()
	{
		Path p = carve(600f, 0.5f, 0f);
		GhostPredictor g = new GhostPredictor();
		// with no timeline before it, an update has nothing to be placed on
		SkateGhostUpdate first = update(p, 0, 1);
		first.tj = null;
		assertFalse(accept(g, first, LATENCY + OFFSET, FLAT));
		assertNull(g.latest());
		double dt = 1 / 60.0;
		int next = 1;
		for (double s = 0.6; s < 12; s += dt)
		{
			while (next * 0.6 + LATENCY <= s)
			{
				SkateGhostUpdate m = update(p, next * 0.6, next + 1);
				if (next > 9)
				{
					// from 6 s on, no timeline (it did not fit)
					m.tj = null;
				}
				float arrive = (float) (next * 0.6 + LATENCY) + OFFSET;
				assertTrue(accept(g, m, arrive, FLAT));
				if (next > 9)
				{
					// placed on the timeline by its arrival
					RenderPose pose = g.pose((float) s + OFFSET, FLAT);
					float[] t = p.at(g.tp);
					assertEquals(0, Math.hypot(pose.x - t[0], pose.y - t[1]), 3);
				}
				next++;
			}
			if (g.latest() != null)
			{
				g.pose((float) s + OFFSET, FLAT);
			}
		}
	}

	@Test
	public void aRestartedSenderStartsAFreshTimeline()
	{
		Path p = carve(600f, 0.5f, 0f);
		GhostPredictor g = new GhostPredictor();
		for (int i = 0; i < 6; i++)
		{
			SkateGhostUpdate m = update(p, 100 + i * 0.6, i + 1);
			accept(g, m, (float) (100 + i * 0.6 + LATENCY) + OFFSET, FLAT);
		}
		g.pose(103.8f + OFFSET, FLAT);
		// the plugin restarted: its clock is near 0 again, its sequence numbers higher
		for (int i = 0; i < 6; i++)
		{
			SkateGhostUpdate m = update(p, 0.2 + i * 0.6, 1000 + i);
			accept(g, m, (float) (104 + i * 0.6 + LATENCY) + OFFSET, FLAT);
		}
		RenderPose pose = g.pose(107.4f + OFFSET, FLAT);
		double tp = g.tp;
		assertTrue("playing the new clock: " + tp, tp > 2 && tp < 3.4);
		float[] t = p.at(tp);
		assertEquals(0, Math.hypot(pose.x - t[0], pose.y - t[1]), 3);
	}

	@Test
	public void aTeleportSnapsInsteadOfSliding()
	{
		Path p = carve(600f, 0.5f, 0f);
		GhostPredictor g = new GhostPredictor();
		for (int i = 0; i < 6; i++)
		{
			accept(g, update(p, i * 0.6, i + 1), (float) (i * 0.6 + LATENCY) + OFFSET, FLAT);
		}
		// far away on the same plane, no positions in between (another world or plane is a fresh start)
		Path far = t -> new float[]{X0 + 20_000f, Y0, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
		for (int i = 6; i < 10; i++)
		{
			accept(g, update(far, i * 0.6, i + 1, 0, null, null, 0f), (float) (i * 0.6 + LATENCY) + OFFSET, FLAT);
		}
		RenderPose pose = null;
		for (double s = 4; s < 6.5; s += 1 / 60.0)
		{
			pose = g.pose((float) s + OFFSET, FLAT);
		}
		assertEquals(X0 + 20_000f, pose.x, 1f);
	}
}
