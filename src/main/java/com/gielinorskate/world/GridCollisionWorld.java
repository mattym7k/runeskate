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

	/** A FULL tile with no usable model (see {@link #addFallbackBlocker}) blocks as a 96 x 96 box this tall. */
	public static final float FALLBACK_HALF = 48f;
	public static final float FALLBACK_HEIGHT = 400f;
	/** Wall edges collide in {@link #contact} as boxes this thick (2 either side of the tile edge). */
	public static final float WALL_HALF_THICKNESS = 2f;
	/** Name-grindable boxes are at least 24 thick: a 12 half plus the 12 skater radius = 24 of reach either side. */
	public static final float GRINDABLE_MIN_HALF = 12f;

	private final int size;
	/**
	 * Each tile's own four corner heights, {@link #SW}/{@link #SE}/{@link #NW}/{@link #NE}: shared with the
	 * neighbours by {@link #setCornerHeight}, set apart by {@link #setTileCorners} where the terrain is not
	 * continuous (a bridge deck over a river keeps its edge corners at deck height).
	 */
	private final float[][][] tileCorners;
	private static final int SW = 0;
	private static final int SE = 1;
	private static final int NW = 2;
	private static final int NE = 3;
	private final int[][] flags;
	/** Height of the full-tile blocker or platform (FULL tiles). */
	private final float[][] heights;
	/** Height of the tile's wall edges, kept apart from {@link #heights} so a tree on a fence tile doesn't raise the fence. */
	private final float[][] wallHeights;
	/** FULL tiles whose collision comes from object boxes (or nothing, e.g. plants) rather than the whole tile. */
	private final boolean[][] shaped;
	private GrindMap grinds = new GrindMap();
	/** Extra grind segments from outside the collision flags (e.g. objects named like fences or benches). */
	private final List<GrindSegment> added = new ArrayList<>();
	/** Object boxes as {cx, cy, hx, hy, cos, sin, height above terrain at the centre, kind, flags}. */
	private final List<float[]> pendingBoxes = new ArrayList<>();
	/** Debug name of each pending box (may be null), parallel to {@link #pendingBoxes}. */
	private final List<String> pendingLabels = new ArrayList<>();
	/** Debug name of each tile's wall object ("Fence (wall)"), or null. */
	private final String[][] wallLabels;
	private BlockerSet objects = BlockerSet.empty();
	private BlockerSet walls = BlockerSet.empty();
	private boolean blockersDirty = true;
	/** Loaded (unblocked) tiles are [loadedLo, loadedHi) on both axes; unset (edge unlimited) until set. */
	private boolean hasLoadedArea;
	private int loadedLo;
	private int loadedHi;

	public GridCollisionWorld(int size)
	{
		this.size = size;
		this.tileCorners = new float[size][size][4];
		this.flags = new int[size][size];
		this.heights = new float[size][size];
		this.wallHeights = new float[size][size];
		this.shaped = new boolean[size][size];
		this.wallLabels = new String[size][size];
	}

	/**
	 * Adds a tight object box centred at (cx, cy), half extents hx along (cos, sin) and hy across, whose top
	 * is {@code height} above the terrain under its centre; kind and flags as in {@link BlockerSet}. Does not
	 * by itself change how the FULL tile under it collides: see {@link #markShaped}.
	 */
	public void addBlocker(float cx, float cy, float hx, float hy, float cos, float sin, float height, byte kind, int flags)
	{
		pendingBoxes.add(new float[]{cx, cy, hx, hy, cos, sin, height, kind, flags});
		pendingLabels.add(null);
		blockersDirty = true;
	}

	/**
	 * Marks FULL tile (tx, ty) as shaped by object boxes: it no longer blocks (or rises, as a platform) as a
	 * whole tile; only the boxes added for its objects collide. Unmarked FULL tiles (water, test worlds) keep
	 * whole-tile collision. Out-of-bounds tiles are ignored.
	 */
	/**
	 * Sets the loaded area: tiles [lo, hi) on both axes. The client blocks every tile outside it (tile 0 and
	 * tiles size - 5 and up of its collision maps), which is the edge {@link #edgeDistance} measures to.
	 */
	public void setLoadedTiles(int lo, int hi)
	{
		hasLoadedArea = true;
		loadedLo = lo;
		loadedHi = hi;
	}

	@Override
	public float edgeDistance(float x, float y)
	{
		if (!hasLoadedArea)
		{
			return Float.POSITIVE_INFINITY;
		}
		float lo = loadedLo * (float) TILE;
		float hi = loadedHi * (float) TILE;
		return Math.min(Math.min(x - lo, hi - x), Math.min(y - lo, hi - y));
	}

	public void markShaped(int tx, int ty)
	{
		if (inBounds(tx, ty))
		{
			shaped[tx][ty] = true;
			blockersDirty = true;
		}
	}

	/**
	 * Adds the box of a blocking object at local (originX, originY) whose model slice at skater height
	 * ({@link ClutterRules#sliceExtents}) has extents {@code slice} = {minX, maxX, minZ, maxZ}, drawn with
	 * the given orientation (JAU), {@code height} tall. Its kind follows {@link ClutterRules#classify}; a LOW
	 * box is landable and its top edges are ledges. Returns the kind.
	 */
	public byte addObjectBlocker(float originX, float originY, float[] slice, int orientation, float height, boolean passByName)
	{
		return addObjectBlocker(originX, originY, slice, orientation, height, passByName, null);
	}

	/** {@link #addObjectBlocker(float, float, float[], int, float, boolean)} named {@code label} for the debug view. */
	public byte addObjectBlocker(float originX, float originY, float[] slice, int orientation, float height, boolean passByName,
		String label)
	{
		return addObjectBlocker(originX, originY, slice, orientation, height, passByName, true, label);
	}

	/**
	 * {@link #addObjectBlocker(float, float, float[], int, float, boolean, String)}, where a LOW box's top edges are
	 * grind ledges only when {@code ledges} (not for a rock: {@link RockRules}). It is landable either way.
	 */
	public byte addObjectBlocker(float originX, float originY, float[] slice, int orientation, float height, boolean passByName,
		boolean ledges, String label)
	{
		byte kind = ClutterRules.classify(passByName, height, ClutterRules.minSide(slice));
		float[] b = ClutterRules.box(originX, originY, slice, orientation, 0f);
		int flags = kind == BlockerSet.LOW ? BlockerSet.LANDABLE | (ledges ? BlockerSet.LEDGES : 0) : 0;
		addBlocker(b[0], b[1], b[2], b[3], b[4], b[5], height, kind, flags);
		labelLast(label);
		return kind;
	}

	/**
	 * Adds the box of a name-grindable object (bench, fence piece, crate) with model extents {@code ext}:
	 * its thin side is inflated to {@link #GRINDABLE_MIN_HALF} either side so it cannot be slipped through,
	 * its top is the rail top ({@code height} above the terrain under its centre) and it is always landable,
	 * so a missed grind lands on the object instead of inside it. LOW up to 120 tall, SOLID above.
	 */
	public void addGrindableBlocker(float originX, float originY, float[] ext, int orientation, float height)
	{
		addGrindableBlocker(originX, originY, ext, orientation, height, null);
	}

	/** {@link #addGrindableBlocker(float, float, float[], int, float)} named {@code label} for the debug view. */
	public void addGrindableBlocker(float originX, float originY, float[] ext, int orientation, float height, String label)
	{
		float[] b = ClutterRules.box(originX, originY, ext, orientation, GRINDABLE_MIN_HALF);
		addBlocker(b[0], b[1], b[2], b[3], b[4], b[5], height, ClutterRules.classifyGrindable(height), BlockerSet.LANDABLE);
		labelLast(label);
	}

	/** A FULL tile with no usable model: a centred {@link #FALLBACK_HALF} box {@link #FALLBACK_HEIGHT} tall. */
	public void addFallbackBlocker(int tx, int ty)
	{
		addBlocker((tx + 0.5f) * TILE, (ty + 0.5f) * TILE, FALLBACK_HALF, FALLBACK_HALF, 1f, 0f, FALLBACK_HEIGHT,
			BlockerSet.SOLID, 0);
		labelLast(FALLBACK_LABEL);
		markShaped(tx, ty);
	}

	/** Debug names of the boxes that do not come from a named object. */
	static final String FALLBACK_LABEL = "Blocked tile with no model (fallback box)";
	static final String WHOLE_TILE_LABEL = "Blocked tile (whole tile)";
	static final String WALL_EDGE_LABEL = "Tile edge (wall flag)";

	private void labelLast(String label)
	{
		if (!pendingLabels.isEmpty())
		{
			pendingLabels.set(pendingLabels.size() - 1, label);
		}
	}

	/**
	 * Names tile (tx, ty)'s wall object for the debug view ("Fence (wall)"); its edges, and the neighbours'
	 * mirrored flags of the same edges, report it when hit. Out-of-bounds tiles are ignored.
	 */
	public void setWallLabel(int tx, int ty, String label)
	{
		if (inBounds(tx, ty))
		{
			wallLabels[tx][ty] = label;
			blockersDirty = true;
		}
	}

	/**
	 * Removes the wall on the given sides ({@link #WALL_N}/E/S/W bits) of tile (tx, ty) for a wall object that
	 * is ridden through (vegetation drawn as a wall, an open door). The game flags a wall on both tiles it
	 * separates, so the mirrored side of the neighbour across each edge is cleared too; the neighbour's other
	 * walls stay. Out-of-bounds tiles are ignored.
	 */
	public void openWallEdges(int tx, int ty, int sides)
	{
		if (!inBounds(tx, ty))
		{
			return;
		}
		flags[tx][ty] &= ~(sides & (WALL_N | WALL_E | WALL_S | WALL_W));
		clearSide(tx, ty + 1, (sides & WALL_N) != 0 ? WALL_S : 0);
		clearSide(tx + 1, ty, (sides & WALL_E) != 0 ? WALL_W : 0);
		clearSide(tx, ty - 1, (sides & WALL_S) != 0 ? WALL_N : 0);
		clearSide(tx - 1, ty, (sides & WALL_W) != 0 ? WALL_E : 0);
		blockersDirty = true;
	}

	private void clearSide(int tx, int ty, int side)
	{
		if (side != 0 && inBounds(tx, ty))
		{
			flags[tx][ty] &= ~side;
		}
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

	/** Wall edges as thin boxes (used by {@link #contact} only; {@link #blockerTop} keeps exact edge crossing). */
	public BlockerSet getWallBlockers()
	{
		ensureBlockers();
		return walls;
	}

	/** Re-indexes the boxes; call after the corner heights, tiles and boxes are set (done lazily otherwise). */
	public void rebuildBlockers()
	{
		BlockerSet.Builder ob = new BlockerSet.Builder();
		for (int i = 0; i < pendingBoxes.size(); i++)
		{
			float[] b = pendingBoxes.get(i);
			ob.add(b[0], b[1], b[2], b[3], b[4], b[5], terrainHeight(b[0], b[1]) + b[6], (byte) b[7], (int) b[8])
				.label(pendingLabels.get(i));
		}
		BlockerSet.Builder wb = new BlockerSet.Builder();
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
					boolean platform = heights[tx][ty] <= PLATFORM_MAX_HEIGHT;
					ob.add(cx, cy, half, half, 1f, 0f, terrainHeight(cx, cy) + heights[tx][ty],
						platform ? BlockerSet.LOW : BlockerSet.SOLID, 0).label(WHOLE_TILE_LABEL);
				}
				float wh = wallHeights[tx][ty];
				if ((f & WALL_N) != 0)
				{
					wallBox(wb, tx, ty, WALL_N, wh);
				}
				if ((f & WALL_E) != 0)
				{
					wallBox(wb, tx, ty, WALL_E, wh);
				}
				if ((f & WALL_S) != 0)
				{
					wallBox(wb, tx, ty, WALL_S, wh);
				}
				if ((f & WALL_W) != 0)
				{
					wallBox(wb, tx, ty, WALL_W, wh);
				}
			}
		}
		objects = ob.build(size);
		walls = wb.build(size);
		blockersDirty = false;
	}

	private void ensureBlockers()
	{
		if (blockersDirty)
		{
			rebuildBlockers();
		}
	}

	private void wallBox(BlockerSet.Builder wb, int tx, int ty, int side, float wallHeight)
	{
		GrindSegment e = edge(tx, ty, side, wallHeight);
		float cx = (e.x0 + e.x1) / 2f;
		float cy = (e.y0 + e.y1) / 2f;
		boolean alongX = e.y0 == e.y1;
		wb.add(cx, cy, TILE / 2f, WALL_HALF_THICKNESS, alongX ? 1f : 0f, alongX ? 0f : 1f, e.top, BlockerSet.SOLID, 0)
			.label(wallLabel(tx, ty, side));
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
			// wall boxes are numbered after the object boxes, so every blocker has its own number
			out.box = wall.box == Contact.UNKNOWN_BOX ? Contact.UNKNOWN_BOX : objects.count() + wall.box;
			out.label = wall.label;
			hit = true;
		}
		return hit;
	}

	/** Debug name of one wall edge: its own tile's wall object, else the neighbour's across it, else a plain edge. */
	private String wallLabel(int tx, int ty, int side)
	{
		if (wallLabels[tx][ty] != null)
		{
			return wallLabels[tx][ty];
		}
		int nx = tx + (side == WALL_E ? 1 : side == WALL_W ? -1 : 0);
		int ny = ty + (side == WALL_N ? 1 : side == WALL_S ? -1 : 0);
		if (inBounds(nx, ny) && wallLabels[nx][ny] != null)
		{
			return wallLabels[nx][ny];
		}
		return WALL_EDGE_LABEL;
	}

	/** Scratch for {@link #contact}'s wall query (client thread only, so one is shared). */
	private final Contact scratchWall = new Contact();

	/** Grindable edges extracted by {@link #rebuildGrinds()}; empty until then. */
	public GrindMap getGrinds()
	{
		return grinds;
	}

	/**
	 * Adds a grind segment that does not come from the collision flags (for example a bench or a
	 * non-blocking fence picked by {@link GrindableNames}). It is included, and merged with the
	 * flag-derived segments, by every later {@link #rebuildGrinds()}. Never affects collision.
	 */
	public void addGrindSegment(GrindSegment s)
	{
		added.add(s);
	}

	/**
	 * Adds a grind along one side ({@link #WALL_N}/E/S/W) of tile (tx, ty) at that side's mean corner
	 * ground height plus {@code above}. Set the corner heights first; out-of-bounds tiles are ignored.
	 */
	public void addEdgeGrind(int tx, int ty, int side, float above)
	{
		if (inBounds(tx, ty))
		{
			added.add(edge(tx, ty, side, above));
		}
	}

	/**
	 * Adds the grinds of a scene object at local (originX, originY) whose model has horizontal extents
	 * {@code ext} = {minX, maxX, minZ, maxZ} and the given orientation (JAU): a centreline rail for a long
	 * object, the perimeter of its own bounds for a square-ish one (see {@link ObjectRailShape}). The top
	 * at each end of a segment is the terrain there (platform tops ignored) plus {@code above}, so a handrail
	 * on a slope follows it instead of floating at one end and clipping at the other.
	 */
	public void addObjectGrind(float originX, float originY, float[] ext, int orientation, float above)
	{
		for (GrindSegment s : ObjectRailShape.segments(originX, originY, ext, orientation, 0f))
		{
			added.add(new GrindSegment(s.x0, s.y0, s.x1, s.y1,
				terrainHeight(s.x0, s.y0) + above, terrainHeight(s.x1, s.y1) + above));
		}
	}

	/**
	 * Adds the perimeter of the tile rectangle [minX..maxX] x [minY..maxY] (inclusive tile coordinates,
	 * clipped to the grid) as one-tile edge grinds at ground plus {@code above}; {@link #rebuildGrinds()}
	 * merges each side into one run. Set the corner heights first.
	 */
	public void addFootprintGrind(int minX, int minY, int maxX, int maxY, float above)
	{
		int x0 = Math.max(0, minX);
		int y0 = Math.max(0, minY);
		int x1 = Math.min(size - 1, maxX);
		int y1 = Math.min(size - 1, maxY);
		if (x0 > x1 || y0 > y1)
		{
			return;
		}
		for (int tx = x0; tx <= x1; tx++)
		{
			added.add(edge(tx, y0, WALL_S, above));
			added.add(edge(tx, y1, WALL_N, above));
		}
		for (int ty = y0; ty <= y1; ty++)
		{
			added.add(edge(x0, ty, WALL_W, above));
			added.add(edge(x1, ty, WALL_E, above));
		}
	}

	/**
	 * Re-extracts the grind map from the current tiles (call once after the grid is filled):
	 * <ul>
	 * <li>each wall edge whose wall height is in [{@link #GRIND_WALL_MIN}, {@link #GRIND_WALL_MAX}]
	 * becomes a rail along that edge, at the edge's mean corner ground height plus the wall height;</li>
	 * <li>each (unshaped) platform tile edge that borders an in-bounds non-platform tile becomes a ledge at
	 * the platform top (mean edge corner height plus the platform height). Scene-border edges are skipped.</li>
	 * <li>the four top edges of every object box flagged {@link BlockerSet#LEDGES} (LOW crates, planters).</li>
	 * <li>every segment added with {@link #addGrindSegment}, {@link #addEdgeGrind} or
	 * {@link #addFootprintGrind} (name-based grindables from the scene).</li>
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
				int f = flags[tx][ty];
				float bh = wallHeights[tx][ty];
				if (bh >= GRIND_WALL_MIN && bh <= GRIND_WALL_MAX)
				{
					if ((f & WALL_N) != 0)
					{
						m.add(edge(tx, ty, WALL_N, bh));
					}
					if ((f & WALL_E) != 0)
					{
						m.add(edge(tx, ty, WALL_E, bh));
					}
					if ((f & WALL_S) != 0)
					{
						m.add(edge(tx, ty, WALL_S, bh));
					}
					if ((f & WALL_W) != 0)
					{
						m.add(edge(tx, ty, WALL_W, bh));
					}
				}
				if (isPlatform(tx, ty))
				{
					ledge(m, tx, ty, tx, ty + 1, WALL_N);
					ledge(m, tx, ty, tx + 1, ty, WALL_E);
					ledge(m, tx, ty, tx, ty - 1, WALL_S);
					ledge(m, tx, ty, tx - 1, ty, WALL_W);
				}
			}
		}
		BlockerSet boxes = getBlockers();
		float[] c = new float[8];
		for (int i = 0; i < boxes.count(); i++)
		{
			if ((boxes.flags(i) & BlockerSet.LEDGES) != 0 && boxes.kind(i) != BlockerSet.PASS)
			{
				boxes.corners(i, c);
				for (int k = 0; k < 4; k++)
				{
					int j = (k + 1) % 4;
					m.add(new GrindSegment(c[2 * k], c[2 * k + 1], c[2 * j], c[2 * j + 1], boxes.top(i)));
				}
			}
		}
		for (GrindSegment s : added)
		{
			m.add(s);
		}
		m.merge();
		grinds = m;
	}

	private void ledge(GrindMap m, int tx, int ty, int nx, int ny, int side)
	{
		if (inBounds(nx, ny) && !isPlatform(nx, ny))
		{
			m.add(edge(tx, ty, side, heights[tx][ty]));
		}
	}

	/**
	 * Segment along one side of a tile, `above` the ground at each of that side's two corners, so it slopes
	 * with a hill (its mean top is the old flat height, used for the wall box).
	 */
	private GrindSegment edge(int tx, int ty, int side, float above)
	{
		int ax;
		int ay;
		int bx;
		int by;
		switch (side)
		{
			case WALL_N:
				ax = tx;
				ay = ty + 1;
				bx = tx + 1;
				by = ty + 1;
				break;
			case WALL_E:
				ax = tx + 1;
				ay = ty;
				bx = tx + 1;
				by = ty + 1;
				break;
			case WALL_S:
				ax = tx;
				ay = ty;
				bx = tx + 1;
				by = ty;
				break;
			default:
				ax = tx;
				ay = ty;
				bx = tx;
				by = ty + 1;
				break;
		}
		return new GrindSegment(ax * TILE, ay * TILE, bx * TILE, by * TILE, corner(tx, ty, ax, ay) + above,
			corner(tx, ty, bx, by) + above);
	}

	public int size()
	{
		return size;
	}

	/** Sets grid corner (cx, cy) for every tile that shares it (up to four). */
	public void setCornerHeight(int cx, int cy, float h)
	{
		setIfIn(cx, cy, SW, h);
		setIfIn(cx - 1, cy, SE, h);
		setIfIn(cx, cy - 1, NW, h);
		setIfIn(cx - 1, cy - 1, NE, h);
		blockersDirty = true;
	}

	/**
	 * Sets tile (tx, ty)'s own corner heights without touching its neighbours, for terrain that is not
	 * continuous across the tile edge (a bridge deck beside the river bed under it).
	 */
	public void setTileCorners(int tx, int ty, float sw, float se, float nw, float ne)
	{
		float[] c = tileCorners[tx][ty];
		c[SW] = sw;
		c[SE] = se;
		c[NW] = nw;
		c[NE] = ne;
		blockersDirty = true;
	}

	private void setIfIn(int tx, int ty, int which, float h)
	{
		if (inBounds(tx, ty))
		{
			tileCorners[tx][ty][which] = h;
		}
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
		{
			ground += heights[tx][ty];
		}
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
		float south = c[SW] * (1 - fx) + c[SE] * fx;
		float north = c[NW] * (1 - fx) + c[NE] * fx;
		return south * (1 - fy) + north * fy;
	}

	private float clampLocal(float v)
	{
		return Math.max(0f, Math.min(size * TILE - 0.001f, v));
	}

	/**
	 * {@link #blockerTop(float, float, float, float, float)} with the full {@link BlockerSet#SKATER_R}.
	 */
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
		{
			return Float.POSITIVE_INFINITY;
		}

		ensureBlockers();
		float top = objects.blockTop(x0, y0, x1, y1, r);
		if (!inBounds(ax, ay))
		{
			return top;
		}

		boolean dx = bx != ax;
		boolean dy = by != ay;

		if (dx && dy)
		{
			// Diagonal move a -> b. There are two routes through the corner:
			// via (bx,ay) [cross x-edge a->(bx,ay), then y-edge (bx,ay)->b]
			// via (ax,by) [cross y-edge a->(ax,by), then x-edge (ax,by)->b]
			// Each route is blocked by the max of its two edge blockers (boxes on the
			// corner tiles are covered by the box check above); the move
			// is only blocked if BOTH routes are blocked (min of the two routes).
			float route1 = inBounds(bx, ay) ? routeTop(ax, ay, bx, ay, bx, by, x1, y1) : Float.POSITIVE_INFINITY;
			float route2 = inBounds(ax, by) ? routeTop(ax, ay, ax, by, bx, by, x1, y1) : Float.POSITIVE_INFINITY;
			top = Math.max(top, Math.min(route1, route2));
		}
		else if (dx || dy)
		{
			top = Math.max(top, edgeTop(ax, ay, bx, by, x1, y1));
		}
		return top;
	}

	/** Top of a route through intermediate corner tile (cx,cy) from (ax,ay) to (bx,by). */
	private float routeTop(int ax, int ay, int cx, int cy, int bx, int by, float x1, float y1)
	{
		float top = edgeTop(ax, ay, cx, cy, x1, y1);
		return Math.max(top, edgeTop(cx, cy, bx, by, x1, y1));
	}

	/** Top of the wall blocker (if any) on the shared edge between two orthogonally adjacent tiles. */
	private float edgeTop(int fromX, int fromY, int toX, int toY, float x1, float y1)
	{
		float top = Float.NEGATIVE_INFINITY;
		if (toX != fromX)
		{
			boolean east = toX > fromX;
			if (has(fromX, fromY, east ? WALL_E : WALL_W))
			{
				top = Math.max(top, groundHeight(x1, y1) + wallHeights[fromX][fromY]);
			}
			if (has(toX, toY, east ? WALL_W : WALL_E))
			{
				top = Math.max(top, groundHeight(x1, y1) + wallHeights[toX][toY]);
			}
		}
		else
		{
			boolean north = toY > fromY;
			if (has(fromX, fromY, north ? WALL_N : WALL_S))
			{
				top = Math.max(top, groundHeight(x1, y1) + wallHeights[fromX][fromY]);
			}
			if (has(toX, toY, north ? WALL_S : WALL_N))
			{
				top = Math.max(top, groundHeight(x1, y1) + wallHeights[toX][toY]);
			}
		}
		return top;
	}

	private boolean isPlatform(int tx, int ty)
	{
		return (flags[tx][ty] & FULL) != 0 && !shaped[tx][ty] && heights[tx][ty] <= PLATFORM_MAX_HEIGHT;
	}

	private boolean has(int tx, int ty, int flag)
	{
		return (flags[tx][ty] & flag) != 0;
	}

	private boolean inBounds(int tx, int ty)
	{
		return tx >= 0 && ty >= 0 && tx < size && ty < size;
	}

	private static int tile(float v)
	{
		return (int) Math.floor(v / TILE);
	}
}
