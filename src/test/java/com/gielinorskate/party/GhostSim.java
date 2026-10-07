package com.gielinorskate.party;

import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.client.party.messages.WebsocketMessage;
import net.runelite.client.util.RuntimeTypeAdapterFactory;

/**
 * A test bench for ghosts: a synthetic skater (carving over hills, pushing, popping kickflips) sent through a real
 * {@link GhostHub}, its updates through Gson and a simulated network (latency, jitter, loss, stalls), into a
 * receiving hub drawn every frame. Records the true path and what was drawn.
 */
final class GhostSim
{
	static final Gson GSON = new GsonBuilder()
		.registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(WebsocketMessage.class)
			.registerSubtype(SkateGhostUpdate.class)
			.registerSubtype(SkateGhostStop.class))
		.create();
	static final float SEND_DT = 0.02f;
	static final float DRAW_DT = 1f / 60f;
	static final long MEMBER = 7L;
	/** Our clock minus the sender's. */
	static final float CLOCK_OFFSET = 1000f;

	/** The hills both clients stand on. */
	static float terrain(float x, float y)
	{
		return (float) (40 * Math.sin(x / 900.0) + 25 * Math.cos(y / 700.0));
	}

	static final GhostPredictor.Ground GROUND = GhostSim::terrain;

	/** Network conditions. */
	static final class Net
	{
		float latency = 0.15f;
		float jitter = 0.08f;
		float loss = 0.05f;
		/** Every this many seconds, a stall this long holds every update back until it ends. */
		float stallEvery = 9f;
		float stall = 0.5f;
		long seed = 42;
		/** Leave the timeline out of every update (an older version sending). */
		boolean old;
	}

	// the true path, every sender frame
	final List<float[]> truth = new ArrayList<>();
	// what was drawn: {our time, x, y, h, heading, playback time (sender s; NaN dead reckoning), rate}
	final List<double[]> drawn = new ArrayList<>();
	final List<float[]> pops = new ArrayList<>();
	int sentUpdates;
	int maxJson;
	int delivered;
	/** Pop counts seen by the receiver at each draw. */
	final List<Integer> popCounts = new ArrayList<>();

	private static final class Delivery implements Comparable<Delivery>
	{
		final float at;
		final String json;
		final int order;

		Delivery(float at, String json, int order)
		{
			this.at = at;
			this.json = json;
			this.order = order;
		}

		@Override
		public int compareTo(Delivery o)
		{
			return at != o.at ? Float.compare(at, o.at) : Integer.compare(order, o.order);
		}
	}

	/** Runs {@code seconds} of skating under {@code net}. */
	void run(float seconds, Net net)
	{
		Random rnd = new Random(net.seed);
		List<PartyMessage> out = new ArrayList<>();
		GhostHub sender = new GhostHub(new GhostHub.Link()
		{
			@Override
			public boolean inParty()
			{
				return true;
			}

			@Override
			public void send(PartyMessage message)
			{
				out.add(message);
			}
		}, 1000);
		// someone is watching, so full updates go out
		SkateGhostUpdate hello = new SkateGhostUpdate();
		hello.w = 330;
		hello.st = "ROLLING";
		hello.seq = 1;
		sender.onRemoteUpdate(99L, hello, 0f, null);
		GhostHub receiver = new GhostHub(new GhostHub.Link()
		{
			@Override
			public boolean inParty()
			{
				return true;
			}

			@Override
			public void send(PartyMessage message)
			{
			}
		}, 5000);

		PriorityQueue<Delivery> wire = new PriorityQueue<>();
		int order = 0;
		// the skater
		float x = 1_409_664f;
		float y = 1_411_200f;
		float heading = 0.3f;
		float speed = 600f;
		float h = terrain(x, y);
		float vh = 0f;
		SkaterState state = SkaterState.ROLLING;
		float nextPush = 1.3f;
		float nextPop = 2.1f;
		float flipStart = Float.NaN;
		Trick flip = Trick.KICKFLIP;
		float nextDraw = 0f;
		int drawNo = 0;
		for (int frameNo = 0; frameNo * SEND_DT < seconds; frameNo++)
		{
			float s = frameNo * SEND_DT;
			int events = 0;
			float w = (float) (1.4 * Math.sin(0.7 * s) + 0.6 * Math.sin(1.9 * s));
			heading += w * SEND_DT;
			if (heading > Math.PI)
			{
				heading -= 2 * Math.PI;
			}
			else if (heading <= -Math.PI)
			{
				heading += 2 * Math.PI;
			}
			speed = Math.max(0f, speed - 40f * SEND_DT);
			if (state == SkaterState.ROLLING && s >= nextPush)
			{
				speed = Math.min(1400f, speed + 120f);
				events |= GhostCodec.EV_PUSH;
				nextPush = s + 2.5f;
			}
			float vx = (float) Math.sin(heading) * speed;
			float vy = (float) Math.cos(heading) * speed;
			x += vx * SEND_DT;
			y += vy * SEND_DT;
			if (state == SkaterState.ROLLING && s >= nextPop)
			{
				state = SkaterState.AIRBORNE;
				vh = 820f;
				events |= GhostCodec.EV_POP | GhostCodec.EV_TRICK;
				flipStart = s;
				pops.add(new float[]{s});
				nextPop = s + 3.7f;
			}
			if (state == SkaterState.AIRBORNE)
			{
				h += vh * SEND_DT - GhostPredictor.GRAVITY / 2f * SEND_DT * SEND_DT;
				vh -= GhostPredictor.GRAVITY * SEND_DT;
				float g = terrain(x, y);
				if (h <= g && vh < 0f)
				{
					h = g;
					vh = 0f;
					state = SkaterState.ROLLING;
					events |= GhostCodec.EV_LAND;
				}
			}
			else
			{
				h = terrain(x, y);
			}
			Trick flipNow = null;
			float progress = 0f;
			if (!Float.isNaN(flipStart) && s - flipStart < flip.duration)
			{
				flipNow = flip;
				progress = (s - flipStart) / flip.duration;
			}
			truth.add(new float[]{s, x, y, h, heading});
			GhostFrame frame = new GhostFrame(330, 0, x, y, h, heading, vx, vy,
				state == SkaterState.AIRBORNE ? vh : 0f, state, null, flipNow, progress, w);
			out.clear();
			sender.onLocalFrame(frame, events, (events & GhostCodec.EV_TRICK) != 0 ? flip : null, false, true, s);
			for (PartyMessage m : out)
			{
				if (!(m instanceof SkateGhostUpdate))
				{
					continue;
				}
				SkateGhostUpdate u = (SkateGhostUpdate) m;
				sentUpdates++;
				if (net.old)
				{
					u.tj = null;
				}
				String json = GSON.toJson(u, WebsocketMessage.class);
				maxJson = Math.max(maxJson, json.length());
				if (rnd.nextFloat() < net.loss)
				{
					continue;
				}
				float at = s + net.latency + (rnd.nextFloat() * 2f - 1f) * net.jitter;
				float phase = s % net.stallEvery;
				if (net.stall > 0f && s > net.stallEvery && phase < net.stall)
				{
					// held back until the stall ends, then out in a burst
					at = Math.max(at, s - phase + net.stall + net.latency);
				}
				wire.add(new Delivery(at, json, order++));
			}
			// our frames up to this sender frame
			while (nextDraw <= s)
			{
				while (!wire.isEmpty() && wire.peek().at <= nextDraw)
				{
					Delivery d = wire.poll();
					SkateGhostUpdate u = (SkateGhostUpdate) GSON.fromJson(d.json, WebsocketMessage.class);
					receiver.onRemoteUpdate(MEMBER, u, nextDraw + CLOCK_OFFSET, GROUND);
					delivered++;
				}
				GhostPredictor g = receiver.ghosts().get(MEMBER);
				if (g != null)
				{
					float now = nextDraw + CLOCK_OFFSET;
					com.gielinorskate.render.RenderPose p = g.pose(now, GROUND);
					drawn.add(new double[]{nextDraw, p.x, p.y, p.h, p.heading, g.playbackTime(), g.playbackRate(),
						g.playbackDelay(), g.playbackUnderrun() ? 1 : 0});
					popCounts.add(g.popCount());
				}
				drawNo++;
				nextDraw = drawNo * DRAW_DT;
			}
		}
	}

	/** The true position {x, y, h} at sender time {@code s} (linear between frames). */
	float[] truthAt(double s)
	{
		int i = (int) Math.floor(s / SEND_DT + 1e-6);
		i = Math.max(0, Math.min(truth.size() - 2, i));
		float[] a = truth.get(i);
		float[] b = truth.get(i + 1);
		float u = (float) Math.max(0, Math.min(1, (s - a[0]) / (b[0] - a[0])));
		return new float[]{a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u, a[3] + (b[3] - a[3]) * u};
	}

	/** Error statistics of what was drawn against the truth. */
	static final class Stats
	{
		double mean;
		double p95;
		double max;
		double lag;
		/** Largest frame-to-frame change of velocity, u/s per frame, and its 99th percentile. */
		double maxDv;
		double p99Dv;

		@Override
		public String toString()
		{
			return String.format("mean %.1f, p95 %.1f, max %.1f units (lag %.2f s); velocity change per frame p99 %.0f,"
				+ " max %.0f u/s", mean, p95, max, lag, p99Dv, maxDv);
		}
	}

	/**
	 * Errors from {@code skip} seconds on: against the truth at the playback time when {@code atPlayback}, else at a
	 * constant lag {@code lag} behind the sender's clock.
	 */
	Stats stats(float skip, boolean atPlayback, double lag)
	{
		List<Double> errors = new ArrayList<>();
		for (double[] d : drawn)
		{
			if (d[0] < skip)
			{
				continue;
			}
			double s = atPlayback ? d[5] : d[0] - lag;
			float[] t = truthAt(s);
			errors.add(Math.sqrt(sq(d[1] - t[0]) + sq(d[2] - t[1]) + sq(d[3] - t[2])));
		}
		Stats st = new Stats();
		errors.sort(null);
		double sum = 0;
		for (double e : errors)
		{
			sum += e;
		}
		st.mean = sum / errors.size();
		st.p95 = errors.get((int) (errors.size() * 0.95));
		st.max = errors.get(errors.size() - 1);
		st.lag = lag;
		List<Double> dvs = new ArrayList<>();
		for (int i = 2; i < drawn.size(); i++)
		{
			double[] a = drawn.get(i - 2);
			double[] b = drawn.get(i - 1);
			double[] c = drawn.get(i);
			if (a[0] < skip)
			{
				continue;
			}
			double dvx = (c[1] - 2 * b[1] + a[1]) / DRAW_DT;
			double dvy = (c[2] - 2 * b[2] + a[2]) / DRAW_DT;
			dvs.add(Math.hypot(dvx, dvy));
		}
		dvs.sort(null);
		st.maxDv = dvs.get(dvs.size() - 1);
		st.p99Dv = dvs.get((int) (dvs.size() * 0.99));
		return st;
	}

	/** {@link #stats} at the constant lag that fits best (0..1.5 s). */
	Stats bestLag(float skip)
	{
		Stats best = null;
		for (int i = 0; i <= 150; i++)
		{
			Stats s = stats(skip, false, i * 0.01);
			if (best == null || s.mean < best.mean)
			{
				best = s;
			}
		}
		return best;
	}

	private static double sq(double v)
	{
		return v * v;
	}
}
