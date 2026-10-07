package com.gielinorskate.world;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
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
 * the model's vertices up to skater height ({@link ClutterRules#SLICE_HEIGHT}, so a tree is its trunk)
 * and turned by its model orientation; its kind follows {@link ClutterRules#classify} (steppable and
 * thin things are ridden through, low wide things are landable, the rest is solid). Such a tile with no
 * usable model gets a 96 x 96 fallback box. Water (BLOCK_MOVEMENT_FLOOR) stays a whole-tile wall.</li>
 * <li>Vegetation, and doors or gates standing open (walls included, both sides of their edge), are ridden through
 * ({@link ClutterNames}, names resolved through impostors). So are small decorative rocks and pebbles; a bigger rock
 * still collides but its box edges are never grind ledges ({@link RockRules}).</li>
 * <li>Every name-grindable object gets a landable box whatever the flags say, so a missed grind lands on
 * it rather than inside it ({@link GridCollisionWorld#addGrindableBlocker}).</li>
 * </ul>
 * Grinds come from two sources, merged together:
 * <ul>
 * <li>collision: low wall blockers, low full-tile platforms and LOW box edges (see
 * {@link GridCollisionWorld#rebuildGrinds()});</li>
 * <li>names: any wall, game, decorative or ground object whose (impostor-resolved) name
 * {@link GrindableNames#looksGrindable looks grindable} and whose model height is in
 * [{@link GridCollisionWorld#GRIND_WALL_MIN}, {@link GridCollisionWorld#GRIND_WALL_MAX}]. Walls give a rail
 * along the tile edges their orientation covers (wall fences are drawn there); game, decorative and ground
 * objects give a shape from their model's extents ({@link ObjectRailShape}): a centreline rail along a
 * long object such as a fence piece (drawn through the middle of its tile), or the edges of a square-ish
 * object's own bounds. The top is the ground plus the model height.</li>
 * </ul>
 * Terrain steps (stairs, hills) are deliberately not grindable. Call on the client thread.
 */
public final class SceneCollisionBuilder
{
	private static final float DEFAULT_BLOCKER_HEIGHT = 400f;
	/** The client's collision maps leave tiles [1, size - 5) unblocked; the rest is the loaded area's edge. */
	private static final int LOADED_LO = 1;
	private static final int LOADED_HI_INSET = 5;
	private static final float MIN_BLOCKER_HEIGHT = 32f;
	private static final int FULL_MASK = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
		| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
		| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;

	private SceneCollisionBuilder()
	{
	}

	/**
	 * @param passVegetation when true (the default "Pass through vegetation" comfort setting), plants and
	 * crops are ridden straight through as before; when false they are classified like any other object
	 * (solid, low or thin-clutter pass, by {@link ClutterRules#classify}). Open doors/gates always pass
	 * through either way.
	 */
	public static GridCollisionWorld build(Client client, WorldView wv, int plane, boolean passVegetation)
	{
		Scene scene = wv.getScene();
		Tile[][] tiles = scene.getTiles()[plane];
		CollisionData[] maps = wv.getCollisionMaps();
		int[][] flags = maps == null || maps[plane] == null ? null : maps[plane].getFlags();

		int size = tiles.length;
		if (flags != null)
		{
			size = Math.min(size, flags.length);
		}
		GridCollisionWorld world = new GridCollisionWorld(size);
		applyTerrain(world, wv.getTileHeights(), wv.getTileSettings(), plane);

		// tiles whose whole-tile block should be replaced by the boxes of the objects on them
		boolean[][] shapeable = new boolean[size][size];
		if (flags != null)
		{
			// the client blocks tile 0 and tiles flags.length - 5 and up on both axes: the loaded area's edge
			world.setLoadedTiles(LOADED_LO, flags.length - LOADED_HI_INSET);
			addCollision(client, world, tiles, flags, size, shapeable, passVegetation);
		}
		boolean[][] modelled = new boolean[size][size];
		addObjects(client, world, tiles, size, shapeable, modelled, passVegetation);
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				if (shapeable[tx][ty] && !modelled[tx][ty])
				{
					world.addFallbackBlocker(tx, ty);
				}
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
		int size = world.size();
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
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
		if (plane < 3 && settings != null
			&& (settings[1][tx][ty] & Constants.TILE_FLAG_BRIDGE) == Constants.TILE_FLAG_BRIDGE)
		{
			return plane + 1;
		}
		return plane;
	}

	private static void addCollision(Client client, GridCollisionWorld world, Tile[][] tiles, int[][] flags, int size,
		boolean[][] shapeable, boolean passVegetation)
	{
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				int f = flags[tx][ty];
				int bits = 0;
				if ((f & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0)
				{
					bits |= GridCollisionWorld.WALL_N;
				}
				if ((f & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0)
				{
					bits |= GridCollisionWorld.WALL_E;
				}
				if ((f & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0)
				{
					bits |= GridCollisionWorld.WALL_S;
				}
				if ((f & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0)
				{
					bits |= GridCollisionWorld.WALL_W;
				}
				boolean water = (f & CollisionDataFlag.BLOCK_MOVEMENT_FLOOR) != 0;
				if ((f & FULL_MASK) != 0)
				{
					bits |= GridCollisionWorld.FULL;
					shapeable[tx][ty] = !water;
				}
				if (bits != 0)
				{
					Tile tile = tiles[tx][ty];
					world.setTile(tx, ty, bits, blockerHeight(tile, water));
					WallObject wall = tile == null ? null : tile.getWallObject();
					if (wall != null)
					{
						world.setWallLabel(tx, ty, label(client, wall.getId(), "wall"));
					}
					if (wall != null && passThrough(client, wall.getId(), passVegetation))
					{
						// an open door or gate swung against this edge: ride through it (height 0 never blocks)
						world.setWallHeight(tx, ty, 0f);
					}
					else
					{
						float wallHeight = wallHeight(tile);
						if (wallHeight > 0f)
						{
							world.setWallHeight(tx, ty, wallHeight);
						}
					}
				}
			}
		}
		// The game flags a wall on both tiles it separates; the neighbour's mirrored flag still blocked at the
		// neighbour's own height after the wall's tile was zeroed. Open both sides, once every tile is set.
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				Tile tile = tiles[tx][ty];
				WallObject wall = tile == null ? null : tile.getWallObject();
				if (wall != null && passThrough(client, wall.getId(), passVegetation))
				{
					world.openWallEdges(tx, ty, wallSides(wall.getOrientationA() | wall.getOrientationB()));
				}
			}
		}
	}

	/**
	 * Boxes for the objects on shapeable tiles, and grinds plus landable boxes for every object that looks
	 * grindable by name (see the class doc). Marks the shapeable tiles that got a usable model.
	 */
	private static void addObjects(Client client, GridCollisionWorld world, Tile[][] tiles, int size,
		boolean[][] shapeable, boolean[][] modelled, boolean passVegetation)
	{
		for (int tx = 0; tx < size; tx++)
		{
			for (int ty = 0; ty < size; ty++)
			{
				Tile tile = tiles[tx][ty];
				if (tile == null)
				{
					continue;
				}
				WallObject wall = tile.getWallObject();
				if (wall != null)
				{
					float h = Math.max(height(wall.getRenderable1()), height(wall.getRenderable2()));
					if (grindable(client, wall.getId(), h))
					{
						int sides = wallSides(wall.getOrientationA() | wall.getOrientationB());
						for (int side : SIDES)
						{
							if ((sides & side) != 0)
							{
								world.addEdgeGrind(tx, ty, side, h);
							}
						}
					}
				}
				GameObject[] objects = tile.getGameObjects();
				if (objects != null)
				{
					for (GameObject o : objects)
					{
						if (o == null)
						{
							continue;
						}
						Point min = o.getSceneMinLocation();
						Point max = o.getSceneMaxLocation();
						// a multi-tile object is listed on every tile it covers: handle it once, from its min tile
						if (min == null || max == null || min.getX() != tx || min.getY() != ty)
						{
							continue;
						}
						LocalPoint lp = o.getLocalLocation();
						// the model's vertices already carry the 90-degree rotation from the object's config; the
						// client applies only the model orientation (e.g. 256 for diagonal pieces) when drawing,
						// so that is the angle the extents are turned by
						addObject(client, world, o.getId(), model(o.getRenderable()), lp == null ? Float.NaN : lp.getX(),
							lp == null ? Float.NaN : lp.getY(), o.getModelOrientation(),
							min.getX(), min.getY(), max.getX(), max.getY(), shapeable, modelled, passVegetation,
							"game object", true);
					}
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null)
				{
					Model m1 = model(deco.getRenderable());
					Model m2 = model(deco.getRenderable2());
					boolean second = height(m2) > height(m1);
					LocalPoint lp = deco.getLocalLocation();
					int ox = second ? deco.getXOffset2() : deco.getXOffset();
					int oy = second ? deco.getYOffset2() : deco.getYOffset();
					addObject(client, world, deco.getId(), second ? m2 : m1, lp == null ? Float.NaN : lp.getX() + ox,
						lp == null ? Float.NaN : lp.getY() + oy, 0, tx, ty, tx, ty, shapeable, modelled, passVegetation,
						"decoration", false);
				}
				GroundObject ground = tile.getGroundObject();
				if (ground != null)
				{
					LocalPoint lp = ground.getLocalLocation();
					addObject(client, world, ground.getId(), model(ground.getRenderable()), lp == null ? Float.NaN : lp.getX(),
						lp == null ? Float.NaN : lp.getY(), 0, tx, ty, tx, ty, shapeable, modelled, passVegetation,
						"ground object", true);
				}
			}
		}
	}

	/**
	 * One game/decorative/ground object at local (x, y) (NaN when unknown) covering tiles
	 * [minTx..maxTx] x [minTy..maxTy]: a grind and landable box when it looks grindable, otherwise a
	 * classified box when it stands on a shapeable tile. A ridden-through object frees its tiles even
	 * without a usable model (an animated plant), instead of leaving them to the solid fallback box.
	 */
	private static void addObject(Client client, GridCollisionWorld world, int id, Model m, float x, float y,
		int orientation, int minTx, int minTy, int maxTx, int maxTy, boolean[][] shapeable, boolean[][] modelled,
		boolean passVegetation, String kind, boolean canFreeTile)
	{
		ObjectComposition def = definition(client, id);
		String name = def == null ? null : def.getName();
		boolean pass = def != null && ClutterNames.passThrough(name, def.getActions(), passVegetation);
		float[] ext = m == null || Float.isNaN(x) ? null : extents(m);
		if (ext == null)
		{
			if (pass && canFreeTile)
			{
				markModelled(world, shapeable, modelled, minTx, minTy, maxTx, maxTy);
			}
			return;
		}
		float h = height(m);
		// small decorative rocks and pebbles are ridden through; no rock's box edges are grind ledges (RockRules)
		boolean rock = RockRules.isRockName(name);
		if (rock && RockRules.isSmallDecorativeRock(name, def.getActions(), h, ext))
		{
			pass = true;
		}
		// only objects that can themselves be the tile's blocker (game and ground objects) decide its shape; a wall
		// decoration (painting, sign) on a blocked tile must not free a model-less blocker sharing that tile
		boolean onShapeable = canFreeTile
			? markModelled(world, shapeable, modelled, minTx, minTy, maxTx, maxTy)
			: anyShapeable(shapeable, minTx, minTy, maxTx, maxTy);
		if (!pass && grindable(client, id, h))
		{
			world.addObjectGrind(x, y, ext, orientation, h);
			world.addGrindableBlocker(x, y, ext, orientation, h, label(client, id, kind));
		}
		else if (onShapeable)
		{
			float[] slice = ClutterRules.sliceExtents(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(),
				m.getVerticesCount(), ClutterRules.SLICE_HEIGHT);
			if (slice != null)
			{
				world.addObjectBlocker(x, y, slice, orientation, h, pass, !rock, label(client, id, kind));
			}
		}
	}

	/** True when any tile in the range is shapeable (without marking it modelled). */
	static boolean anyShapeable(boolean[][] shapeable, int minTx, int minTy, int maxTx, int maxTy)
	{
		int size = shapeable.length;
		for (int tx = Math.max(0, minTx); tx <= Math.min(size - 1, maxTx); tx++)
		{
			for (int ty = Math.max(0, minTy); ty <= Math.min(size - 1, maxTy); ty++)
			{
				if (shapeable[tx][ty])
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Marks the shapeable tiles in the range as modelled, and shaped in the world (they then collide only
	 * through their objects' boxes, not as whole tiles); true when there was any.
	 */
	static boolean markModelled(GridCollisionWorld world, boolean[][] shapeable, boolean[][] modelled, int minTx,
		int minTy, int maxTx, int maxTy)
	{
		boolean any = false;
		int size = shapeable.length;
		for (int tx = Math.max(0, minTx); tx <= Math.min(size - 1, maxTx); tx++)
		{
			for (int ty = Math.max(0, minTy); ty <= Math.min(size - 1, maxTy); ty++)
			{
				if (shapeable[tx][ty])
				{
					modelled[tx][ty] = true;
					world.markShaped(tx, ty);
					any = true;
				}
			}
		}
		return any;
	}

	private static final int[] SIDES = {
		GridCollisionWorld.WALL_N, GridCollisionWorld.WALL_E, GridCollisionWorld.WALL_S, GridCollisionWorld.WALL_W,
	};

	/** Height in the grindable range and the (impostor-resolved) definition's name looks grindable. */
	private static boolean grindable(Client client, int id, float height)
	{
		if (height < GridCollisionWorld.GRIND_WALL_MIN || height > GridCollisionWorld.GRIND_WALL_MAX)
		{
			return false;
		}
		ObjectComposition def = definition(client, id);
		return def != null && GrindableNames.looksGrindable(def.getName());
	}

	/**
	 * Ridden through by name: a door/gate standing open always, and a plant or crop only when
	 * {@code passVegetation} (the "Pass through vegetation" comfort setting) is on ({@link ClutterNames}).
	 */
	private static boolean passThrough(Client client, int id, boolean passVegetation)
	{
		ObjectComposition def = definition(client, id);
		if (def == null)
		{
			return false;
		}
		return ClutterNames.passThrough(def.getName(), def.getActions(), passVegetation);
	}

	/** Debug name of an object's box, "Name (kind)", from its impostor-resolved definition. */
	private static String label(Client client, int id, String kind)
	{
		ObjectComposition def = definition(client, id);
		String name = def == null ? null : def.getName();
		return HitLog.label(name, id, kind);
	}

	/** The object's definition, resolved to its current impostor for varbit-driven objects (may be null). */
	private static ObjectComposition definition(Client client, int id)
	{
		return ClutterNames.resolve(client.getObjectDefinition(id), d -> d.getImpostorIds() != null,
			ObjectComposition::getImpostor);
	}

	/**
	 * WallObject orientation bits (1 = west, 2 = north, 4 = east, 8 = south) to GridCollisionWorld sides.
	 * Diagonal bits (16/32/64/128) are skipped: diagonal wall pieces are corner posts, and full diagonal
	 * fences are GameObjects, which get their tile perimeter instead.
	 */
	static int wallSides(int orientation)
	{
		int sides = 0;
		if ((orientation & 1) != 0)
		{
			sides |= GridCollisionWorld.WALL_W;
		}
		if ((orientation & 2) != 0)
		{
			sides |= GridCollisionWorld.WALL_N;
		}
		if ((orientation & 4) != 0)
		{
			sides |= GridCollisionWorld.WALL_E;
		}
		if ((orientation & 8) != 0)
		{
			sides |= GridCollisionWorld.WALL_S;
		}
		return sides;
	}

	/** Whole-tile blocker height (used for water and unshaped tiles): the tallest wall or game object model. */
	private static float blockerHeight(Tile tile, boolean floorBlock)
	{
		if (floorBlock || tile == null)
		{
			return DEFAULT_BLOCKER_HEIGHT;
		}
		float best = -1f;
		WallObject wall = tile.getWallObject();
		if (wall != null)
		{
			best = Math.max(best, height(wall.getRenderable1()));
			best = Math.max(best, height(wall.getRenderable2()));
		}
		GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject o : objects)
			{
				if (o != null)
				{
					best = Math.max(best, height(o.getRenderable()));
				}
			}
		}
		return best < 0 ? DEFAULT_BLOCKER_HEIGHT : Math.max(MIN_BLOCKER_HEIGHT, best);
	}

	/**
	 * The wall object's own model height (curbs 16-31 keep their real height), or 0 when the tile has no
	 * wall model, in which case the edges keep the tile's blocker height.
	 */
	private static float wallHeight(Tile tile)
	{
		WallObject wall = tile == null ? null : tile.getWallObject();
		if (wall == null)
		{
			return 0f;
		}
		return Math.max(0f, Math.max(height(wall.getRenderable1()), height(wall.getRenderable2())));
	}

	private static float height(Renderable r)
	{
		return height(model(r));
	}

	private static Model model(Renderable r)
	{
		if (r == null)
		{
			return null;
		}
		return r instanceof Model ? (Model) r : r.getModel();
	}

	private static float height(Model m)
	{
		if (m == null)
		{
			return -1f;
		}
		m.calculateBoundsCylinder();
		return m.getModelHeight();
	}

	/** Model-space {minX, maxX, minZ, maxZ} of the model's vertices, or null. */
	private static float[] extents(Model m)
	{
		return m == null ? null : ObjectRailShape.extents(m.getVerticesX(), m.getVerticesZ(), m.getVerticesCount());
	}
}
