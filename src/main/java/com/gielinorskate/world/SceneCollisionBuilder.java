package com.gielinorskate.world;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

/**
 * Converts the loaded scene into a GridCollisionWorld, including its tight object boxes
 * ({@link GridCollisionWorld#getBlockers()}) and grind map ({@link GridCollisionWorld#getGrinds()}).
 * <p>
 * Collision: the flags decide what is solid, the models decide its shape.
 * <ul>
 * <li>Wall edges block along the tile edge, as tall as the wall object's own model (not the tallest
 * model on the tile, so a lamp next to a fence doesn't raise the fence).</li>
 * <li>Tiles the flags block whole (objects, floor decorations) get one box per object on them, cut from
 * the model's vertices up to skater height ({@link ObjectShapes#SLICE_HEIGHT}, so a tree is its trunk)
 * and turned by its model orientation; its kind follows {@link ObjectShapes#classify} (steppable and
 * thin things are ridden through, low wide things are landable, the rest is solid). Such a tile with no
 * usable model gets a 96 x 96 fallback box. Water (BLOCK_MOVEMENT_FLOOR) stays a whole-tile wall.</li>
 * <li>Vegetation, and doors or gates standing open (walls included, both sides of their edge), are ridden through
 * ({@link ObjectNames}, names resolved through impostors). So are small decorative rocks and pebbles; a bigger rock
 * still collides but its box edges are never grind ledges ({@link ObjectNames}).</li>
 * <li>Every name-grindable object gets a landable box whatever the flags say, so a missed grind lands on
 * it rather than inside it ({@link GridCollisionWorld#addGrindableBlocker}).</li>
 * </ul>
 * Grinds come from two sources, merged together:
 * <ul>
 * <li>collision: low wall blockers, low full-tile platforms and LOW box edges (see
 * {@link GridCollisionWorld#rebuildGrinds()});</li>
 * <li>names: any wall, game, decorative or ground object whose (impostor-resolved) name
 * {@link ObjectNames#looksGrindable looks grindable} and whose model height is in
 * [{@link GridCollisionWorld#GRIND_WALL_MIN}, {@link GridCollisionWorld#GRIND_WALL_MAX}]. Walls give a rail
 * along the tile edges their orientation covers (wall fences are drawn there); game, decorative and ground
 * objects give a shape from their model's extents ({@link ObjectShapes}): a centreline rail along a
 * long object such as a fence piece (drawn through the middle of its tile), or the edges of a square-ish
 * object's own bounds. The top is the ground plus the model height.</li>
 * </ul>
 * Terrain steps (stairs, hills) are deliberately not grindable. Call on the client thread.
 */
public final class SceneCollisionBuilder
{
	/** The collision flags blocking each side, in side order (north, east, south, west). */
	private static final int[] SIDE_FLAGS = {CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_EAST,
		CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_WEST};

	private final Client client;
	private final GridCollisionWorld world;
	/**
	 * When true (the default "Pass through vegetation" comfort setting), plants and crops are ridden straight
	 * through; when false they are classified like any other object (solid, low or thin-clutter pass, by
	 * {@link ObjectShapes#classify}). Open doors/gates always pass through either way.
	 */
	private final boolean passVegetation;
	/** Tiles whose whole-tile block should be replaced by the boxes of the objects on them. */
	final boolean[][] shapeable;
	/** Shapeable tiles that got a usable model. */
	final boolean[][] modelled;

	SceneCollisionBuilder(Client client, GridCollisionWorld world, boolean passVegetation)
	{
		this.client = client;
		this.world = world;
		this.passVegetation = passVegetation;
		shapeable = new boolean[world.size()][world.size()];
		modelled = new boolean[world.size()][world.size()];
	}

	public static GridCollisionWorld build(Client client, WorldView wv, int plane, boolean passVegetation)
	{
		Tile[][] tiles = wv.getScene().getTiles()[plane];
		CollisionData[] maps = wv.getCollisionMaps();
		int[][] flags = maps == null || maps[plane] == null ? null : maps[plane].getFlags();
		int size = flags == null ? tiles.length : Math.min(tiles.length, flags.length);
		GridCollisionWorld world = new GridCollisionWorld(size);
		applyTerrain(world, wv.getTileHeights(), wv.getTileSettings(), plane);
		SceneCollisionBuilder b = new SceneCollisionBuilder(client, world, passVegetation);
		if (flags != null)
		{
			// the client's collision maps block tile 0 and tiles flags.length - 5 and up on both axes: the loaded
			// area's edge
			world.setLoadedTiles(1, flags.length - 5);
			b.addCollision(tiles, flags);
		}
		b.addObjects(tiles);
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				if (b.shapeable[tx][ty] && !b.modelled[tx][ty])
					world.addFallbackBlocker(tx, ty);
			}
		}
		world.rebuildBlockers();
		world.rebuildGrinds();
		return world;
	}

	/**
	 * Fills the world's terrain from the scene heightmap {@code heights} [plane][x][y] (RuneLite z, down
	 * positive, size + 1 corners a side) as {@link Perspective#getTileHeight} reads it: a tile flagged as a
	 * bridge ({@code settings[1][x][y] & 2}) takes its heights from the plane above.
	 */
	static void applyTerrain(GridCollisionWorld world, int[][][] heights, byte[][][] settings, int plane)
	{
		// per tile, not per shared corner: a bridge deck's edge corners would otherwise take the river-bed
		// heights of the river tiles beside it (and those the deck's), sloping the deck into the river
		for (int tx = 0; tx < world.size(); tx++)
		{
			for (int ty = 0; ty < world.size(); ty++)
			{
				int[][] hp = heights[effectivePlane(settings, plane, tx, ty)];
				world.setTileCorners(tx, ty, -at(hp, tx, ty), -at(hp, tx + 1, ty), -at(hp, tx, ty + 1),
					-at(hp, tx + 1, ty + 1));
			}
		}
	}

	/** Heightmap corner (cx, cy), clamped to the array. */
	private static int at(int[][] h, int cx, int cy)
	{
		int[] col = h[Math.min(cx, h.length - 1)];
		return col[Math.min(cy, col.length - 1)];
	}

	/** The plane whose heights tile (tx, ty) uses: the one above for a bridge tile, as Perspective does. */
	static int effectivePlane(byte[][][] settings, int plane, int tx, int ty)
	{
		// the bridge flag lives on plane 1's settings
		return plane < 3 && settings != null
			&& (settings[1][tx][ty] & Constants.TILE_FLAG_BRIDGE) == Constants.TILE_FLAG_BRIDGE ? plane + 1 : plane;
	}

	private void addCollision(Tile[][] tiles, int[][] flags)
	{
		List<int[]> open = new ArrayList<>();
		for (int tx = 0; tx < world.size(); tx++)
		{
			for (int ty = 0; ty < world.size(); ty++)
			{
				int f = flags[tx][ty];
				Tile tile = tiles[tx][ty];
				WallObject wall = tile == null ? null : tile.getWallObject();
				boolean pass = wall != null && passThrough(definition(wall.getId()));
				if (pass)
					open.add(new int[]{tx, ty, wallSides(wall.getOrientationA() | wall.getOrientationB())});
				int bits = 0;
				for (int i = 0; i < 4; i++)
					bits |= (f & SIDE_FLAGS[i]) != 0 ? 1 << i : 0;
				boolean water = (f & CollisionDataFlag.BLOCK_MOVEMENT_FLOOR) != 0;
				if ((f & (CollisionDataFlag.BLOCK_MOVEMENT_OBJECT | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
					| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION)) != 0)
				{
					bits |= GridCollisionWorld.FULL;
					shapeable[tx][ty] = !water;
				}
				if (bits != 0)
				{
					world.setTile(tx, ty, bits, blockerHeight(tile, water));
					// an open door or gate swung against this edge is ridden through (height 0 never blocks);
					// other edges are as tall as the wall object's own model (curbs 16-31 keep their real
					// height), or keep the tile's blocker height when the tile has no wall model
					float wallHeight = pass || wall == null ? 0f : height(wall);
					if (pass || wallHeight > 0f)
						world.setWallHeight(tx, ty, wallHeight);
				}
			}
		}
		// The game flags a wall on both tiles it separates; the neighbour's mirrored flag still blocked at the
		// neighbour's own height after the wall's tile was zeroed. Open both sides, once every tile is set.
		open.forEach(o -> world.openWallEdges(o[0], o[1], o[2]));
	}

	/**
	 * Boxes for the objects on shapeable tiles, and grinds plus landable boxes for every object that looks
	 * grindable by name (see the class doc). Marks the shapeable tiles that got a usable model.
	 */
	private void addObjects(Tile[][] tiles)
	{
		for (int tx = 0; tx < world.size(); tx++)
		{
			for (int ty = 0; ty < world.size(); ty++)
			{
				Tile tile = tiles[tx][ty];
				if (tile == null)
					continue;
				WallObject wall = tile.getWallObject();
				float wallHeight = wall == null ? 0f : height(wall);
				if (wall != null && grindable(definition(wall.getId()), wallHeight))
				{
					int sides = wallSides(wall.getOrientationA() | wall.getOrientationB());
					for (int i = 0; i < 4; i++)
					{
						if ((sides & 1 << i) != 0)
							world.addEdgeGrind(tx, ty, 1 << i, wallHeight);
					}
				}
				GameObject[] objects = tile.getGameObjects();
				for (GameObject o : objects == null ? new GameObject[0] : objects)
				{
					Point min = o == null ? null : o.getSceneMinLocation();
					Point max = o == null ? null : o.getSceneMaxLocation();
					// a multi-tile object is listed on every tile it covers: handle it once, from its min tile.
					// The model's vertices already carry the 90-degree rotation from the object's config; the
					// client applies only the model orientation (e.g. 256 for diagonal pieces) when drawing,
					// so that is the angle the extents are turned by
					if (min != null && max != null && min.getX() == tx && min.getY() == ty)
						addObject(o, model(o.getRenderable()), 0, 0, o.getModelOrientation(), tx, ty, max.getX(), max.getY(),
							true);
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null)
				{
					Model m1 = model(deco.getRenderable());
					Model m2 = model(deco.getRenderable2());
					boolean second = height(m2) > height(m1);
					addObject(deco, second ? m2 : m1, second ? deco.getXOffset2() : deco.getXOffset(),
						second ? deco.getYOffset2() : deco.getYOffset(), 0, tx, ty, tx, ty, false);
				}
				GroundObject ground = tile.getGroundObject();
				if (ground != null)
					addObject(ground, model(ground.getRenderable()), 0, 0, 0, tx, ty, tx, ty, true);
			}
		}
	}

	/**
	 * One game/decorative/ground object drawn with model m at its local location plus (ox, oy) covering tiles
	 * [minTx..maxTx] x [minTy..maxTy]: a grind and landable box when it looks grindable, otherwise a
	 * classified box when it stands on a shapeable tile. A ridden-through object frees its tiles even
	 * without a usable model (an animated plant), instead of leaving them to the solid fallback box.
	 */
	private void addObject(TileObject o, Model m, int ox, int oy, int orientation, int minTx, int minTy, int maxTx,
		int maxTy, boolean canFreeTile)
	{
		ObjectComposition def = definition(o.getId());
		LocalPoint lp = o.getLocalLocation();
		String name = def == null ? null : def.getName();
		boolean pass = passThrough(def);
		float[] ext = m == null || lp == null ? null : extents(m, Float.POSITIVE_INFINITY);
		if (ext == null)
		{
			if (pass && canFreeTile)
				shapeTiles(minTx, minTy, maxTx, maxTy, true);
			return;
		}
		float x = lp.getX() + ox;
		float y = lp.getY() + oy;
		float h = height(m);
		// small decorative rocks and pebbles are ridden through; no rock's box edges are grind ledges (ObjectNames)
		boolean rock = ObjectNames.isRockName(name);
		pass |= rock && ObjectNames.isSmallDecorativeRock(name, def.getActions(), h, ext);
		// only objects that can themselves be the tile's blocker (game and ground objects) decide its shape; a wall
		// decoration (painting, sign) on a blocked tile must not free a model-less blocker sharing that tile
		boolean onShapeable = shapeTiles(minTx, minTy, maxTx, maxTy, canFreeTile);
		if (!pass && grindable(def, h))
		{
			world.addObjectGrind(x, y, ext, orientation, h);
			world.addGrindableBlocker(x, y, ext, orientation, h);
		}
		else if (onShapeable)
		{
			float[] slice = extents(m, ObjectShapes.SLICE_HEIGHT);
			if (slice != null)
				world.addObjectBlocker(x, y, slice, orientation, h, pass, !rock);
		}
	}

	/**
	 * True when any tile in the range is shapeable. When {@code mark}, also marks those tiles modelled, and
	 * shaped in the world (they then collide only through their objects' boxes, not as whole tiles).
	 */
	boolean shapeTiles(int minTx, int minTy, int maxTx, int maxTy, boolean mark)
	{
		boolean any = false;
		for (int tx = Math.max(0, minTx); tx <= Math.min(shapeable.length - 1, maxTx); tx++)
		{
			for (int ty = Math.max(0, minTy); ty <= Math.min(shapeable.length - 1, maxTy); ty++)
			{
				if (shapeable[tx][ty])
				{
					any = true;
					if (mark)
					{
						modelled[tx][ty] = true;
						world.markShaped(tx, ty);
					}
				}
			}
		}
		return any;
	}

	/** Height in the grindable range and the (impostor-resolved) definition's name looks grindable. */
	private static boolean grindable(ObjectComposition def, float height)
	{
		return height >= GridCollisionWorld.GRIND_WALL_MIN && height <= GridCollisionWorld.GRIND_WALL_MAX && def != null
			&& ObjectNames.looksGrindable(def.getName());
	}

	/**
	 * Ridden through by name: a door/gate standing open always, and a plant or crop only when
	 * {@link #passVegetation} is on ({@link ObjectNames}).
	 */
	private boolean passThrough(ObjectComposition def)
	{
		return def != null && ObjectNames.passThrough(def.getName(), def.getActions(), passVegetation);
	}

	/** The object's definition, resolved to its current impostor for varbit-driven objects (may be null). */
	private ObjectComposition definition(int id)
	{
		return ObjectNames.resolve(client.getObjectDefinition(id), d -> d.getImpostorIds() != null,
			ObjectComposition::getImpostor);
	}

	/**
	 * WallObject orientation bits (1 = west, 2 = north, 4 = east, 8 = south) to GridCollisionWorld sides.
	 * Diagonal bits (16/32/64/128) are skipped: diagonal wall pieces are corner posts, and full diagonal
	 * fences are GameObjects, which get their tile perimeter instead.
	 */
	static int wallSides(int orientation)
	{
		return orientation >> 1 & 7 | (orientation & 1) << 3;
	}

	/** Whole-tile blocker height (used for water and unshaped tiles): the tallest wall or game object model. */
	private static float blockerHeight(Tile tile, boolean floorBlock)
	{
		if (floorBlock || tile == null)
			return 400f;
		WallObject wall = tile.getWallObject();
		float best = wall == null ? -1f : height(wall);
		GameObject[] objects = tile.getGameObjects();
		for (GameObject o : objects == null ? new GameObject[0] : objects)
		{
			if (o != null)
				best = Math.max(best, height(o.getRenderable()));
		}
		// a tile with no model blocks 400 tall, a low model at least 32
		return best < 0 ? 400f : Math.max(32f, best);
	}

	/** The taller of a wall object's two models, or -1 with neither. */
	private static float height(WallObject wall)
	{
		return Math.max(height(wall.getRenderable1()), height(wall.getRenderable2()));
	}

	private static float height(Renderable r)
	{
		return height(model(r));
	}

	private static Model model(Renderable r)
	{
		return r == null || r instanceof Model ? (Model) r : r.getModel();
	}

	private static float height(Model m)
	{
		if (m == null)
			return -1f;
		m.calculateBoundsCylinder();
		return m.getModelHeight();
	}

	/** Model-space {minX, maxX, minZ, maxZ} of the model's vertices up to {@code maxHeight}, or null. */
	private static float[] extents(Model m, float maxHeight)
	{
		return ObjectShapes.sliceExtents(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount(),
			maxHeight);
	}
}
