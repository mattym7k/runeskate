package com.gielinorskate.world;

import com.gielinorskate.physics.Contact;
import java.util.Arrays;

/**
 * Tight per-object collision boxes: oriented rectangles with an absolute top height and a kind, indexed per
 * tile for constant-time lookups. Pure; no client dependency. Immutable once built; query from one thread.
 * <p>
 * A box is centred at (cx, cy) with half extents hx along its u axis (cos, sin) and hy along its v axis
 * (-sin, cos). Each box is registered (compressed-sparse-row index) in every tile its axis-aligned bounds,
 * grown by the skater radius {@link #SKATER_R}, overlap, so a query for a skater of radius up to
 * {@link #SKATER_R} reads only the list of the tile it stands on.
 * <ul>
 * <li>{@link #SOLID}: collides on its sides; tall things (rocks, trees, walls).</li>
 * <li>{@link #LOW}: collides on its sides; you can land on top when {@link #LANDABLE} (crates, platforms).
 * Rolling into one is blocked by its raised ground, not by {@link #blockTop}.</li>
 * <li>{@link #PASS}: kept only so the debug view can show it; never collides (plants, open doors).</li>
 * </ul>
 */
public final class BlockerSet
{
	/** Skater/board radius in local units. */
	public static final float SKATER_R = 12f;
	public static final byte SOLID = 0;
	public static final byte LOW = 1;
	public static final byte PASS = 2;
	/** groundHeight is raised to the top inside the box (not its skater margin). */
	public static final int LANDABLE = 1;
	/** The box's top edges are grindable ledges. */
	public static final int LEDGES = 2;

	private static final float TILE = GridCollisionWorld.TILE;
	/** A move only counts as going deeper into a box when its penetration grows by more than this. */
	private static final float DEEPER_EPS = 1e-3f;

	private final int count;
	private final float[] cx;
	private final float[] cy;
	private final float[] hx;
	private final float[] hy;
	private final float[] cos;
	private final float[] sin;
	private final float[] top;
	private final byte[] kind;
	private final byte[] flags;
	/** What each box is, for the debug view ("Rocks (game object)"); entries may be null. */
	private final String[] labels;
	private final int size;
	private final int[] tileStart;
	private final int[] items;
	/** Scratch normal for {@link #depth}; queries run on the client thread only, so one is shared. */
	private final float[] scratchN = new float[2];

	private BlockerSet(Builder b, int size)
	{
		this.count = b.n;
		this.cx = Arrays.copyOf(b.cx, count);
		this.cy = Arrays.copyOf(b.cy, count);
		this.hx = Arrays.copyOf(b.hx, count);
		this.hy = Arrays.copyOf(b.hy, count);
		this.cos = Arrays.copyOf(b.cos, count);
		this.sin = Arrays.copyOf(b.sin, count);
		this.top = Arrays.copyOf(b.top, count);
		this.kind = Arrays.copyOf(b.kind, count);
		this.flags = Arrays.copyOf(b.flags, count);
		this.labels = Arrays.copyOf(b.labels, count);
		this.size = Math.max(0, size);

		int tiles = this.size * this.size;
		int[] perTile = new int[tiles + 1];
		int[] range = new int[4];
		for (int i = 0; i < count; i++)
		{
			if (tileRange(i, range))
			{
				for (int tx = range[0]; tx <= range[2]; tx++)
				{
					for (int ty = range[1]; ty <= range[3]; ty++)
					{
						perTile[tx * this.size + ty + 1]++;
					}
				}
			}
		}
		for (int t = 0; t < tiles; t++)
		{
			perTile[t + 1] += perTile[t];
		}
		this.tileStart = perTile;
		this.items = new int[perTile[tiles]];
		int[] fill = Arrays.copyOf(perTile, tiles);
		for (int i = 0; i < count; i++)
		{
			if (tileRange(i, range))
			{
				for (int tx = range[0]; tx <= range[2]; tx++)
				{
					for (int ty = range[1]; ty <= range[3]; ty++)
					{
						items[fill[tx * this.size + ty]++] = i;
					}
				}
			}
		}
	}

	public static BlockerSet empty()
	{
		return new Builder().build(0);
	}

	/** Inclusive tile range {x0, y0, x1, y1} of box i's bounds plus the skater radius; false when off the grid. */
	private boolean tileRange(int i, int[] out)
	{
		float ac = Math.abs(cos[i]);
		float as = Math.abs(sin[i]);
		float ex = ac * hx[i] + as * hy[i] + SKATER_R;
		float ey = as * hx[i] + ac * hy[i] + SKATER_R;
		int x0 = Math.max(0, tile(cx[i] - ex));
		int y0 = Math.max(0, tile(cy[i] - ey));
		int x1 = Math.min(size - 1, tile(cx[i] + ex));
		int y1 = Math.min(size - 1, tile(cy[i] + ey));
		out[0] = x0;
		out[1] = y0;
		out[2] = x1;
		out[3] = y1;
		return x0 <= x1 && y0 <= y1;
	}

	/**
	 * Deepest overlap of a circle of radius r at (x, y) with any non-PASS box whose top is above
	 * feetH + maxStep; see {@link com.gielinorskate.physics.CollisionWorld#contact}.
	 */
	public boolean contact(float x, float y, float r, float feetH, float maxStep, Contact out)
	{
		float ignoreAtOrBelow = feetH + maxStep;
		float spill = Math.max(0f, r - SKATER_R);
		int x0 = Math.max(0, tile(x - spill));
		int y0 = Math.max(0, tile(y - spill));
		int x1 = Math.min(size - 1, tile(x + spill));
		int y1 = Math.min(size - 1, tile(y + spill));
		boolean hit = false;
		float best = 0f;
		float[] n = scratchN;
		for (int tx = x0; tx <= x1; tx++)
		{
			for (int ty = y0; ty <= y1; ty++)
			{
				int t = tx * size + ty;
				for (int k = tileStart[t]; k < tileStart[t + 1]; k++)
				{
					int i = items[k];
					if (kind[i] == PASS || top[i] <= ignoreAtOrBelow)
					{
						continue;
					}
					float d = depth(i, x, y, r, n);
					if (d > 0f && (!hit || d > best))
					{
						hit = true;
						best = d;
						out.set(n[0], n[1], d, top[i]);
						out.box = i;
						out.label = labels[i];
					}
				}
			}
		}
		return hit;
	}

	/** Highest top of a {@link #LANDABLE} non-PASS box containing (x, y), or -infinity. */
	public float landTop(float x, float y)
	{
		float best = Float.NEGATIVE_INFINITY;
		int t = tileIndex(x, y);
		if (t < 0)
		{
			return best;
		}
		for (int k = tileStart[t]; k < tileStart[t + 1]; k++)
		{
			int i = items[k];
			if (kind[i] != PASS && (flags[i] & LANDABLE) != 0 && top[i] > best && inside(i, x, y))
			{
				best = top[i];
			}
		}
		return best;
	}

	/** {@link #blockTop(float, float, float, float, float)} with the full {@link #SKATER_R}. */
	public float blockTop(float x0, float y0, float x1, float y1)
	{
		return blockTop(x0, y0, x1, y1, SKATER_R);
	}

	/**
	 * Highest top of a {@link #SOLID} box the skater (radius {@code r}, capped at the {@link #SKATER_R} the
	 * tile index is grown by) would push into when moving from (x0, y0) to (x1, y1), or -infinity. A move counts when its end is more deeply inside a box than
	 * its start, or when it starts outside a box and its midpoint is inside (so a fast step cannot skip a thin
	 * box): a skater already overlapping a box can always slide along it or move out of it, never further in
	 * (least-penetration escape).
	 */
	public float blockTop(float x0, float y0, float x1, float y1, float r)
	{
		r = Math.min(r, SKATER_R);
		float best = Float.NEGATIVE_INFINITY;
		float mx = (x0 + x1) / 2f;
		float my = (y0 + y1) / 2f;
		float[] n = scratchN;
		for (int pass = 0; pass < 2; pass++)
		{
			float px = pass == 0 ? x1 : mx;
			float py = pass == 0 ? y1 : my;
			int t = tileIndex(px, py);
			if (t < 0)
			{
				continue;
			}
			for (int k = tileStart[t]; k < tileStart[t + 1]; k++)
			{
				int i = items[k];
				if (kind[i] != SOLID || top[i] <= best)
				{
					continue;
				}
				float d = depth(i, px, py, r, n);
				if (d <= 0f)
				{
					continue;
				}
				float d0 = depth(i, x0, y0, r, n);
				// the midpoint only catches a step over a thin box from outside it, so only a move whose path
				// crosses the box itself counts: one that grazes past a corner within the skater radius, its end
				// clear, is not stepping over anything (it used to block, and with no contact at the clear end to
				// slide along the skater stopped dead against the corner). From inside, only where the move
				// ends matters (crossing the middle of a box on the way out is fine).
				if (pass == 0 ? d > Math.max(0f, d0) + DEEPER_EPS : d0 <= 0f && crossesBox(i, x0, y0, x1, y1))
				{
					best = top[i];
				}
			}
		}
		return best;
	}

	/**
	 * Penetration of a circle of radius r at (x, y) into box i (positive = overlapping), with the push-out
	 * normal in n. Outside the box: r minus the distance to it. Inside: r plus the distance to the nearest
	 * face, along that face's normal.
	 */
	private float depth(int i, float x, float y, float r, float[] n)
	{
		float c = cos[i];
		float s = sin[i];
		float dx = x - cx[i];
		float dy = y - cy[i];
		float lu = dx * c + dy * s;
		float lv = -dx * s + dy * c;
		float du = lu - clamp(lu, hx[i]);
		float dv = lv - clamp(lv, hy[i]);
		if (du != 0f || dv != 0f)
		{
			float d = (float) Math.sqrt(du * du + dv * dv);
			float nu = du / d;
			float nv = dv / d;
			n[0] = nu * c - nv * s;
			n[1] = nu * s + nv * c;
			return r - d;
		}
		float eu = hx[i] - Math.abs(lu);
		float ev = hy[i] - Math.abs(lv);
		if (eu < ev)
		{
			float su = lu < 0f ? -1f : 1f;
			n[0] = su * c;
			n[1] = su * s;
			return r + eu;
		}
		float sv = lv < 0f ? -1f : 1f;
		n[0] = -sv * s;
		n[1] = sv * c;
		return r + ev;
	}

	/** True when the segment (x0, y0)-(x1, y1) passes through box i itself (no skater radius): a slab clip. */
	private boolean crossesBox(int i, float x0, float y0, float x1, float y1)
	{
		float c = cos[i];
		float s = sin[i];
		float ax = x0 - cx[i];
		float ay = y0 - cy[i];
		float bx = x1 - cx[i];
		float by = y1 - cy[i];
		float u0 = ax * c + ay * s;
		float v0 = -ax * s + ay * c;
		float du = (bx * c + by * s) - u0;
		float dv = (-bx * s + by * c) - v0;
		float[] range = {0f, 1f};
		return clip(u0, du, hx[i], range) && clip(v0, dv, hy[i], range);
	}

	/** Narrows range {tMin, tMax} to where p + t * d lies within [-h, h]; false when that is empty. */
	private static boolean clip(float p, float d, float h, float[] range)
	{
		if (Math.abs(d) < 1e-9f)
		{
			return Math.abs(p) <= h;
		}
		float ta = (-h - p) / d;
		float tb = (h - p) / d;
		range[0] = Math.max(range[0], Math.min(ta, tb));
		range[1] = Math.min(range[1], Math.max(ta, tb));
		return range[0] <= range[1];
	}

	private boolean inside(int i, float x, float y)
	{
		float dx = x - cx[i];
		float dy = y - cy[i];
		return Math.abs(dx * cos[i] + dy * sin[i]) <= hx[i] && Math.abs(-dx * sin[i] + dy * cos[i]) <= hy[i];
	}

	private static float clamp(float v, float h)
	{
		return Math.max(-h, Math.min(h, v));
	}

	private int tileIndex(float x, float y)
	{
		int tx = tile(x);
		int ty = tile(y);
		if (tx < 0 || ty < 0 || tx >= size || ty >= size)
		{
			return -1;
		}
		return tx * size + ty;
	}

	private static int tile(float v)
	{
		return (int) Math.floor(v / TILE);
	}

	public int count()
	{
		return count;
	}

	/** Number of boxes registered in tile (tx, ty) (0 off the grid). */
	public int countAt(int tx, int ty)
	{
		if (tx < 0 || ty < 0 || tx >= size || ty >= size)
		{
			return 0;
		}
		int t = tx * size + ty;
		return tileStart[t + 1] - tileStart[t];
	}

	public byte kind(int i)
	{
		return kind[i];
	}

	public int flags(int i)
	{
		return flags[i];
	}

	/** What box i is ("Rocks (game object)"), or null when unnamed. */
	public String label(int i)
	{
		return labels[i];
	}

	public float top(int i)
	{
		return top[i];
	}

	public float centreX(int i)
	{
		return cx[i];
	}

	public float centreY(int i)
	{
		return cy[i];
	}

	/** The four corners of box i into out = {x0, y0, .. x3, y3}: -u-v, +u-v, +u+v, -u+v (counter-clockwise). */
	public void corners(int i, float[] out)
	{
		float ux = cos[i] * hx[i];
		float uy = sin[i] * hx[i];
		float vx = -sin[i] * hy[i];
		float vy = cos[i] * hy[i];
		out[0] = cx[i] - ux - vx;
		out[1] = cy[i] - uy - vy;
		out[2] = cx[i] + ux - vx;
		out[3] = cy[i] + uy - vy;
		out[4] = cx[i] + ux + vx;
		out[5] = cy[i] + uy + vy;
		out[6] = cx[i] - ux + vx;
		out[7] = cy[i] - uy + vy;
	}

	/** Collects boxes, then indexes them for a grid of size x size tiles. */
	public static final class Builder
	{
		private int n;
		private float[] cx = new float[16];
		private float[] cy = new float[16];
		private float[] hx = new float[16];
		private float[] hy = new float[16];
		private float[] cos = new float[16];
		private float[] sin = new float[16];
		private float[] top = new float[16];
		private byte[] kind = new byte[16];
		private byte[] flags = new byte[16];
		private String[] labels = new String[16];

		/**
		 * Adds a box centred at (cx, cy) with half extents hx along (cos, sin) and hy across it, absolute
		 * top height {@code top}, kind {@link #SOLID}/{@link #LOW}/{@link #PASS} and {@link #LANDABLE} /
		 * {@link #LEDGES} flags. (cos, sin) must be a unit vector.
		 */
		public Builder add(float cx, float cy, float hx, float hy, float cos, float sin, float top, byte kind, int flags)
		{
			if (n == this.cx.length)
			{
				int cap = n * 2;
				this.cx = Arrays.copyOf(this.cx, cap);
				this.cy = Arrays.copyOf(this.cy, cap);
				this.hx = Arrays.copyOf(this.hx, cap);
				this.hy = Arrays.copyOf(this.hy, cap);
				this.cos = Arrays.copyOf(this.cos, cap);
				this.sin = Arrays.copyOf(this.sin, cap);
				this.top = Arrays.copyOf(this.top, cap);
				this.kind = Arrays.copyOf(this.kind, cap);
				this.flags = Arrays.copyOf(this.flags, cap);
				this.labels = Arrays.copyOf(this.labels, cap);
			}
			this.cx[n] = cx;
			this.cy[n] = cy;
			this.hx[n] = Math.abs(hx);
			this.hy[n] = Math.abs(hy);
			this.cos[n] = cos;
			this.sin[n] = sin;
			this.top[n] = top;
			this.kind[n] = kind;
			this.flags[n] = (byte) flags;
			this.labels[n] = null;
			n++;
			return this;
		}

		/** Names the box added last, for the debug view (e.g. "Rocks (game object)"); null for none. */
		public Builder label(String label)
		{
			if (n > 0)
			{
				labels[n - 1] = label;
			}
			return this;
		}

		public int size()
		{
			return n;
		}

		public BlockerSet build(int gridSize)
		{
			return new BlockerSet(this, gridSize);
		}
	}
}
