package com.gielinorskate.world;

import com.gielinorskate.physics.CollisionWorld;
import com.gielinorskate.physics.Contact;
import java.util.ArrayList;
import java.util.List;

/**
 * A square grid of tiles with corner heights, edge walls (with their own heights), full-tile
 * blockers/platforms and tight per-object boxes ({@link BlockerSet}). North = +y.
 * <p>
 * A FULL tile marked {@link #markShaped shaped} collides only through the boxes added for its objects;
 * an unshaped one (water, or a test world) still blocks or rises as a whole tile. Boxes are re-indexed
 * lazily after any change; use from one thread.
 * <p>
 * Sides are bits 1 &lt;&lt; i for i = 0..3: north, east, south, west, each with its neighbour offset
 * ({@link #DX}, {@link #DY}); the opposite side is i + 2.
 */
public final class GridCollisionWorld implements CollisionWorld
{
	public static final int TILE = 128;
	public static final int WALL_N = 1;
	public static final int WALL_E = 2;
	public static final int WALL_S = 4;
	public static final int WALL_W = 8;
	public static final int FULL = 16;
	/** Full tiles at or below this height are platforms you can land on rather than walls. */
	public static final float PLATFORM_MAX_HEIGHT = 120f;
	/**
	 * Wall blockers this tall (fences, low walls, rails) are grindable along their edge. The maximum
	 * (was 100) is set by a fully charged ollie: peak 820^2 / (2 * 2000) = 168, and GrindMap locks on
	 * down to BELOW_TOP 16 under the top, so tops up to 168 + 16 = 184 are reachable. With coarse 0.02 s
	 * physics steps the semi-implicit peak is about 8 lower (160), i.e. 176; 180 is the frame-rate-ideal edge.
	 * Taller walls still block like any wall: grind extraction never changes collision.
	 */
	public static final float GRIND_WALL_MIN = 16f;
	public static final float GRIND_WALL_MAX = 180f;
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DY = {1, 0, -1, 0};

	private final int size;
	/**
	 * Each tile's own four corner heights, SW, SE, NW, NE, set per tile by {@link #setTileCorners}: the terrain
	 * need not be continuous (a bridge deck over a river keeps its edge corners at deck height).
	 */
	final float[][][] tileCorners;
	private final int[][] flags;
	/** Height of the full-tile blocker or platform (FULL tiles). */
	private final float[][] heights;
	/** Height of the tile's wall edges, kept apart from {@link #heights} so a tree on a fence tile doesn't raise the fence. */
	private final float[][] wallHeights;
	/** FULL tiles whose collision comes from object boxes (or nothing, e.g. plants) rather than the whole tile. */
	private final boolean[][] shaped;
	private GrindMap grinds = new GrindMap();
	/** Extra grind segments from outside the collision flags (e.g. objects named like fences or benches). */
	final List<GrindSegment> added = new ArrayList<>();
	/** Object boxes as BlockerSet rows, with the top as the height above the terrain at the centre. */
	private final List<float[]> pendingBoxes = new ArrayList<>();
	private BlockerSet objects = BlockerSet.empty();
	private BlockerSet walls = BlockerSet.empty();
	private boolean blockersDirty = true;
	/** Scratch for {@link #contact}'s wall query (client thread only, so one is shared). */
	private final Contact scratchWall = new Contact();
	/** Edges of the loaded (unblocked) area on both axes, local units; unlimited until set. */
	private float loadedLo = Float.NEGATIVE_INFINITY;
	private float loadedHi = Float.POSITIVE_INFINITY;

	public GridCollisionWorld(int size)
	{
		this.size = size;
		tileCorners = new float[size][size][4];
		flags = new int[size][size];
		heights = new float[size][size];
		wallHeights = new float[size][size];
		shaped = new boolean[size][size];
	}

	/**
	 * Adds a tight object box centred at (cx, cy), half extents hx along (cos, sin) and hy across, whose top
	 * is {@code height} above the terrain under its centre; kind and flags as in {@link BlockerSet}. Does not
	 * by itself change how the FULL tile under it collides: see {@link #markShaped}.
	 */
	public void addBlocker(float cx, float cy, float hx, float hy, float cos, float sin, float height, byte kind, int flags)
	{
		pendingBoxes.add(new float[]{cx, cy, hx, hy, cos, sin, height, kind, flags});
		blockersDirty = true;
	}

	/** {@link #addBlocker} for an oriented box {cx, cy, hx, hy, cos, sin} from {@link ObjectShapes#box}. */
	private void addBlocker(float[] b, float height, byte kind, int flags)
	{
		addBlocker(b[0], b[1], b[2], b[3], b[4], b[5], height, kind, flags);
	}

	/**
	 * Sets the loaded area: tiles [lo, hi) on both axes. The client blocks every tile outside it (tile 0 and
	 * tiles size - 5 and up of its collision maps), which is the edge {@link #edgeDistance} measures to.
	 */
	public void setLoadedTiles(int lo, int hi)
	{
		loadedLo = lo * (float) TILE;
		loadedHi = hi * (float) TILE;
	}

	@Override
	public float edgeDistance(float x, float y)
	{
		return Math.min(Math.min(x - loadedLo, loadedHi - x), Math.min(y - loadedLo, loadedHi - y));
	}

	/**
	 * Marks FULL tile (tx, ty) as shaped by object boxes: it no longer blocks (or rises, as a platform) as a
	 * whole tile; only the boxes added for its objects collide. Unmarked FULL tiles (water, test worlds) keep
	 * whole-tile collision.
	 */
	public void markShaped(int tx, int ty)
	{
		shaped[tx][ty] = true;
		blockersDirty = true;
	}

	/**
	 * Adds the box of a blocking object at local (originX, originY) whose model slice at skater height
	 * ({@link ObjectShapes#sliceExtents}) has extents {@code slice} = {minX, maxX, minZ, maxZ}, drawn with
	 * the given orientation (JAU), {@code height} tall. Its kind follows {@link ObjectShapes#classify}; a LOW
	 * box is landable, and its top edges are grind ledges only when {@code ledges} (not for a rock:
	 * {@link ObjectNames}). Returns the kind.
	 */
	public byte addObjectBlocker(float originX, float originY, float[] slice, int orientation, float height, boolean passByName,
		boolean ledges)
	{
		byte kind = ObjectShapes.classify(passByName, height, ObjectShapes.minSide(slice));
		addBlocker(ObjectShapes.box(originX, originY, slice, orientation, 0f), height, kind,
			kind == BlockerSet.LOW ? BlockerSet.LANDABLE | (ledges ? BlockerSet.LEDGES : 0) : 0);
		return kind;
	}

	/**
	 * Adds the box of a name-grindable object (bench, fence piece, crate) with model extents {@code ext}:
	 * its thin side is inflated to 12 either side (plus the 12 skater radius = 24 of reach) so it cannot be
	 * slipped through, its top is the rail top ({@code height} above the terrain under its centre) and it is
	 * always landable, so a missed grind lands on the object instead of inside it. LOW up to 120 tall, SOLID
	 * above.
	 */
	public void addGrindableBlocker(float originX, float originY, float[] ext, int orientation, float height)
	{
		addBlocker(ObjectShapes.box(originX, originY, ext, orientation, 12f), height,
			height <= PLATFORM_MAX_HEIGHT ? BlockerSet.LOW : BlockerSet.SOLID, BlockerSet.LANDABLE);
	}

	/** A FULL tile with no usable model: a centred 96 x 96 box 400 tall. */
	public void addFallbackBlocker(int tx, int ty)
	{
		addBlocker((tx + 0.5f) * TILE, (ty + 0.5f) * TILE, 48f, 48f, 1f, 0f, 400f, BlockerSet.SOLID, 0);
		markShaped(tx, ty);
	}

	/**
	 * Removes the wall on the given sides ({@link #WALL_N}/E/S/W bits) of tile (tx, ty) for a wall object that
	 * is ridden through (vegetation drawn as a wall, an open door). The game flags a wall on both tiles it
	 * separates, so the mirrored side of the neighbour across each edge is cleared too; the neighbour's other
	 * walls stay.
	 */
	public void openWallEdges(int tx, int ty, int sides)
	{
		flags[tx][ty] &= ~(sides & 15);
		for (int i = 0; i < 4; i++)
		{
			int nx = tx + DX[i];
			int ny = ty + DY[i];
			if ((sides & 1 << i) != 0 && inBounds(nx, ny))
				flags[nx][ny] &= ~(1 << (i + 2) % 4);
		}
		blockersDirty = true;
	}

	/** Sets the height of tile (tx, ty)'s wall edges alone (setTile sets it to the tile's blocker height). */
	public void setWallHeight(int tx, int ty, float h)
	{
		wallHeights[tx][ty] = h;
		blockersDirty = true;
	}

	/**
	 * Object boxes, including whole-tile boxes for unshaped FULL tiles (LOW without {@link BlockerSet#LANDABLE}
	 * for platforms, whose ground rise stays whole-tile). Built on first use after a change.
	 */
	public BlockerSet getBlockers()
	{
		ensureBlockers();
		return objects;
	}

	/**
	 * Re-indexes the boxes; call after the corner heights, tiles and boxes are set (done lazily otherwise).
	 * Wall edges collide as boxes 4 thick (2 either side of the tile edge).
	 */
	public void rebuildBlockers()
	{
		List<float[]> ob = new ArrayList<>();
		for (float[] b : pendingBoxes)
		{
			float[] abs = b.clone();
			abs[6] += terrainHeight(b[0], b[1]);
			ob.add(abs);
		}
		List<float[]> wb = new ArrayList<>();
		float half = TILE / 2f;
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				int f = flags[tx][ty];
				if ((f & FULL) != 0 && !shaped[tx][ty])
				{
					float cx = (tx + 0.5f) * TILE;
					float cy = (ty + 0.5f) * TILE;
					ob.add(new float[]{cx, cy, half, half, 1f, 0f, terrainHeight(cx, cy) + heights[tx][ty],
						heights[tx][ty] <= PLATFORM_MAX_HEIGHT ? BlockerSet.LOW : BlockerSet.SOLID, 0});
				}
				for (int i = 0; i < 4; i++)
				{
					if ((f & 1 << i) != 0)
					{
						GrindSegment e = edge(tx, ty, 1 << i, wallHeights[tx][ty]);
						boolean alongX = e.y0 == e.y1;
						wb.add(new float[]{(e.x0 + e.x1) / 2f, (e.y0 + e.y1) / 2f, half, 2f, alongX ? 1f : 0f,
							alongX ? 0f : 1f, e.top, BlockerSet.SOLID, 0});
					}
				}
			}
		}
		objects = new BlockerSet(ob, size);
		walls = new BlockerSet(wb, size);
		blockersDirty = false;
	}

	private void ensureBlockers()
	{
		if (blockersDirty)
			rebuildBlockers();
	}

	/**
	 * Deepest contact with an object box (including whole-tile boxes of unshaped FULL tiles) or a wall edge
	 * box; see {@link CollisionWorld#contact}. A skater inside a box gets the least-penetration way out.
	 */
	@Override
	public boolean contact(float x, float y, float r, float feetH, float maxStep, Contact out)
	{
		ensureBlockers();
		boolean hit = objects.contact(x, y, r, feetH, maxStep, out);
		Contact wall = scratchWall;
		if (walls.contact(x, y, r, feetH, maxStep, wall) && (!hit || wall.depth > out.depth))
		{
			out.set(wall.nx, wall.ny, wall.depth, wall.top);
			hit = true;
		}
		return hit;
	}

	/** Grindable edges extracted by {@link #rebuildGrinds()}; empty until then. */
	public GrindMap getGrinds()
	{
		return grinds;
	}

	/**
	 * Adds a grind along one side ({@link #WALL_N}/E/S/W) of tile (tx, ty) at that side's mean corner
	 * ground height plus {@code above}. Set the corner heights first.
	 */
	public void addEdgeGrind(int tx, int ty, int side, float above)
	{
		added.add(edge(tx, ty, side, above));
	}

	/**
	 * Adds the grinds of a scene object at local (originX, originY) whose model has horizontal extents
	 * {@code ext} = {minX, maxX, minZ, maxZ} and the given orientation (JAU): a centreline rail for a long
	 * object, the perimeter of its own bounds for a square-ish one (see {@link ObjectShapes}). The top
	 * at each end of a segment is the terrain there (platform tops ignored) plus {@code above}, so a handrail
	 * on a slope follows it instead of floating at one end and clipping at the other.
	 */
	public void addObjectGrind(float originX, float originY, float[] ext, int orientation, float above)
	{
		for (GrindSegment s : ObjectShapes.segments(originX, originY, ext, orientation))
			added.add(new GrindSegment(s.x0, s.y0, s.x1, s.y1,
				terrainHeight(s.x0, s.y0) + above, terrainHeight(s.x1, s.y1) + above));
	}

	/**
	 * Re-extracts the grind map from the current tiles (call once after the grid is filled):
	 * <ul>
	 * <li>each wall edge whose wall height is in [{@link #GRIND_WALL_MIN}, {@link #GRIND_WALL_MAX}]
	 * becomes a rail along that edge, at the edge's mean corner ground height plus the wall height;</li>
	 * <li>each (unshaped) platform tile edge that borders an in-bounds non-platform tile becomes a ledge at
	 * the platform top (mean edge corner height plus the platform height). Scene-border edges are skipped.</li>
	 * <li>the four top edges of every object box flagged {@link BlockerSet#LEDGES} (LOW crates, planters).</li>
	 * <li>every segment added with {@link #addEdgeGrind} or {@link #addObjectGrind} (name-based grindables from
	 * the scene).</li>
	 * </ul>
	 * Collinear neighbours are then merged; the same edge flagged on both neighbouring tiles collapses to
	 * one segment (or stays two if their tops differ by more than the merge tolerance).
	 */
	public void rebuildGrinds()
	{
		GrindMap m = new GrindMap();
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				float bh = wallHeights[tx][ty];
				boolean grindWall = bh >= GRIND_WALL_MIN && bh <= GRIND_WALL_MAX;
				boolean platform = isPlatform(tx, ty);
				for (int i = 0; i < 4; i++)
				{
					if (grindWall && (flags[tx][ty] & 1 << i) != 0)
						m.add(edge(tx, ty, 1 << i, bh));
				}
				for (int i = 0; platform && i < 4; i++)
				{
					int nx = tx + DX[i];
					int ny = ty + DY[i];
					if (inBounds(nx, ny) && !isPlatform(nx, ny))
						m.add(edge(tx, ty, 1 << i, heights[tx][ty]));
				}
			}
		}
		float[] c = new float[8];
		for (float[] b : getBlockers().boxes)
		{
			if (((int) b[8] & BlockerSet.LEDGES) != 0 && b[7] != BlockerSet.PASS)
			{
				BlockerSet.corners(b, c);
				for (int k = 0; k < 4; k++)
				{
					int j = (k + 1) % 4;
					m.add(new GrindSegment(c[2 * k], c[2 * k + 1], c[2 * j], c[2 * j + 1], b[6]));
				}
			}
		}
		added.forEach(m::add);
		m.merge();
		grinds = m;
	}

	/**
	 * Segment along one side of a tile, `above` the ground at each of that side's two corners, so it slopes
	 * with a hill (its mean top is the old flat height, used for the wall box). North and south run west to
	 * east, east and west run south to north.
	 */
	private GrindSegment edge(int tx, int ty, int side, float above)
	{
		int ax = side == WALL_E ? tx + 1 : tx;
		int ay = side == WALL_N ? ty + 1 : ty;
		int bx = side == WALL_W ? tx : tx + 1;
		int by = side == WALL_S ? ty : ty + 1;
		return new GrindSegment(ax * TILE, ay * TILE, bx * TILE, by * TILE, corner(tx, ty, ax, ay) + above,
			corner(tx, ty, bx, by) + above);
	}

	public int size()
	{
		return size;
	}

	/**
	 * Sets tile (tx, ty)'s own corner heights without touching its neighbours, for terrain that is not
	 * continuous across the tile edge (a bridge deck beside the river bed under it).
	 */
	public void setTileCorners(int tx, int ty, float sw, float se, float nw, float ne)
	{
		tileCorners[tx][ty] = new float[]{sw, se, nw, ne};
		blockersDirty = true;
	}

	/** Tile (tx, ty)'s own height at grid corner (cx, cy), one of its four corners. */
	private float corner(int tx, int ty, int cx, int cy)
	{
		return tileCorners[tx][ty][(cy > ty ? 2 : 0) + (cx > tx ? 1 : 0)];
	}

	/** Sets a tile's flags and its blocker height, which is also its wall height until {@link #setWallHeight}. */
	public void setTile(int tx, int ty, int tileFlags, float blockerHeight)
	{
		flags[tx][ty] = tileFlags;
		heights[tx][ty] = blockerHeight;
		wallHeights[tx][ty] = blockerHeight;
		blockersDirty = true;
	}

	/**
	 * Terrain, raised by an unshaped platform tile over the whole tile, or by a {@link BlockerSet#LANDABLE}
	 * object box only inside the box itself.
	 */
	@Override
	public float groundHeight(float x, float y)
	{
		float ground = terrainHeight(x, y);
		int tx = tile(clampLocal(x));
		int ty = tile(clampLocal(y));
		if (isPlatform(tx, ty))
			ground += heights[tx][ty];
		ensureBlockers();
		return Math.max(ground, objects.landTop(x, y));
	}

	/** Terrain height from the tile's own corner heights alone (no platform tops), clamped to the grid. */
	@Override
	public float terrainHeight(float x, float y)
	{
		float cx = clampLocal(x);
		float cy = clampLocal(y);
		int tx = (int) (cx / TILE);
		int ty = (int) (cy / TILE);
		float fx = (cx - tx * TILE) / TILE;
		float fy = (cy - ty * TILE) / TILE;
		float[] c = tileCorners[tx][ty];
		float south = c[0] * (1 - fx) + c[1] * fx;
		float north = c[2] * (1 - fx) + c[3] * fx;
		return south * (1 - fy) + north * fy;
	}

	private float clampLocal(float v)
	{
		return Math.max(0f, Math.min(size * TILE - 0.001f, v));
	}

	/** {@link #blockerTop(float, float, float, float, float)} with the full {@link BlockerSet#SKATER_R}. */
	@Override
	public float blockerTop(float x0, float y0, float x1, float y1)
	{
		return blockerTop(x0, y0, x1, y1, BlockerSet.SKATER_R);
	}

	/**
	 * Tall object boxes (and unshaped tall FULL tiles) the move pushes the skater (radius {@code r}, at most
	 * {@link BlockerSet#SKATER_R}) into, see {@link BlockerSet#blockTop}: a skater already inside one can
	 * always move out or along it but never deeper (least-penetration escape). Wall edges are exact tile
	 * edge crossings. LOW boxes and platforms block through their raised {@link #groundHeight} instead.
	 */
	@Override
	public float blockerTop(float x0, float y0, float x1, float y1, float r)
	{
		int ax = tile(x0);
		int ay = tile(y0);
		int bx = tile(x1);
		int by = tile(y1);
		if (!inBounds(bx, by))
			return Float.POSITIVE_INFINITY;

		ensureBlockers();
		float top = objects.blockTop(x0, y0, x1, y1, r);
		if (!inBounds(ax, ay) || ax == bx && ay == by)
			return top;
		float g = groundHeight(x1, y1);
		if (ax != bx && ay != by)
		{
			// Diagonal move a -> b. There are two routes through the corner:
			// via (bx,ay) [cross x-edge a->(bx,ay), then y-edge (bx,ay)->b]
			// via (ax,by) [cross y-edge a->(ax,by), then x-edge (ax,by)->b]
			// Each route is blocked by the max of its two edge blockers (boxes on the
			// corner tiles are covered by the box check above); the move
			// is only blocked if BOTH routes are blocked (min of the two routes).
			float route1 = inBounds(bx, ay)
				? Math.max(edgeTop(ax, ay, bx, ay, g), edgeTop(bx, ay, bx, by, g)) : Float.POSITIVE_INFINITY;
			float route2 = inBounds(ax, by)
				? Math.max(edgeTop(ax, ay, ax, by, g), edgeTop(ax, by, bx, by, g)) : Float.POSITIVE_INFINITY;
			return Math.max(top, Math.min(route1, route2));
		}
		return Math.max(top, edgeTop(ax, ay, bx, by, g));
	}

	/**
	 * Top of the wall blocker (if any) on the shared edge between two orthogonally adjacent tiles, standing on
	 * ground {@code g} where the move ends.
	 */
	private float edgeTop(int fromX, int fromY, int toX, int toY, float g)
	{
		int i = toX > fromX ? 1 : toX < fromX ? 3 : toY > fromY ? 0 : 2;
		return Math.max((flags[fromX][fromY] & 1 << i) != 0 ? g + wallHeights[fromX][fromY] : Float.NEGATIVE_INFINITY,
			(flags[toX][toY] & 1 << (i + 2) % 4) != 0 ? g + wallHeights[toX][toY] : Float.NEGATIVE_INFINITY);
	}

	private boolean isPlatform(int tx, int ty)
	{
		return (flags[tx][ty] & FULL) != 0 && !shaped[tx][ty] && heights[tx][ty] <= PLATFORM_MAX_HEIGHT;
	}

	private boolean inBounds(int tx, int ty)
	{
		return tx >= 0 && ty >= 0 && tx < size && ty < size;
	}

	static int tile(float v)
	{
		return (int) Math.floor(v / TILE);
	}
}
