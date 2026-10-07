package com.gielinorskate.input;

import com.gielinorskate.tricks.Gesture;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A realistic right-drag mouse path for tests: waypoints (ms, x, y) joined by smoothstep (ease-in-out)
 * motion, sampled every 7-9 ms like AWT drag events, with a low-passed random hand wobble of
 * {@code sigma} px. Only samples whose rounded position changed are emitted, as a real mouse sends no
 * drag event while held still. Seeded, so every run is identical.
 */
public final class MousePath
{
	/** One drag sample: time and integer screen position. */
	public static final class Sample
	{
		public final long ms;
		public final int x;
		public final int y;

		Sample(long ms, int x, int y)
		{
			this.ms = ms;
			this.x = x;
			this.y = y;
		}
	}

	public final List<Sample> samples = new ArrayList<>();

	private MousePath()
	{
	}

	/** A waypoint: at {@code ms} the hand is at (x, y) relative to the press point. */
	public static double[] at(double ms, double x, double y)
	{
		return new double[]{ms, x, y};
	}

	/** Builds the path; the press point is (500, 500) at time 0 and is the first sample. */
	public static MousePath of(long seed, double sigma, double[]... waypoints)
	{
		Random rnd = new Random(seed);
		MousePath p = new MousePath();
		double end = waypoints[waypoints.length - 1][0];
		double wx = 0;
		double wy = 0;
		int lx = Integer.MIN_VALUE;
		int ly = Integer.MIN_VALUE;
		double t = 0;
		while (t <= end)
		{
			int i = 0;
			while (i < waypoints.length - 2 && waypoints[i + 1][0] < t)
			{
				i++;
			}
			double[] a = waypoints[i];
			double[] b = waypoints[Math.min(i + 1, waypoints.length - 1)];
			double u = b[0] == a[0] ? 1 : Math.min(1, Math.max(0, (t - a[0]) / (b[0] - a[0])));
			u = u * u * (3 - 2 * u);
			if (t > 0)
			{
				wx = wx * 0.7 + rnd.nextGaussian() * sigma;
				wy = wy * 0.7 + rnd.nextGaussian() * sigma;
			}
			int x = (int) Math.round(500 + a[1] + (b[1] - a[1]) * u + wx);
			int y = (int) Math.round(500 + a[2] + (b[2] - a[2]) * u + wy);
			if (x != lx || y != ly)
			{
				p.samples.add(new Sample((long) t, x, y));
			}
			lx = x;
			ly = y;
			t += 7 + rnd.nextInt(3);
		}
		return p;
	}

	/** Time of the last sample. */
	public long endMs()
	{
		return samples.get(samples.size() - 1).ms;
	}

	/**
	 * Presses at the first sample, drags through the rest (ticking the recognizer every ms in between,
	 * as the client frame loop does) and keeps ticking until {@code untilMs}, without releasing.
	 */
	public List<Gesture> feed(GestureRecognizer r, long untilMs)
	{
		List<Gesture> out = new ArrayList<>();
		Sample first = samples.get(0);
		r.begin(first.x, first.y, first.ms);
		int idx = 1;
		for (long now = 0; now <= untilMs; now++)
		{
			while (idx < samples.size() && samples.get(idx).ms <= now)
			{
				Sample s = samples.get(idx++);
				r.move(s.x, s.y, s.ms);
			}
			r.tick(now);
			Gesture g;
			while ((g = r.poll()) != null)
			{
				out.add(g);
			}
		}
		return out;
	}
}
