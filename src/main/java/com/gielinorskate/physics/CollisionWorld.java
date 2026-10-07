package com.gielinorskate.physics;

/** The world as physics sees it. Heights are up-positive local units. */
public interface CollisionWorld
{
	/** Height of the skateable surface at a local point (terrain plus any platform top). */
	float groundHeight(float x, float y);

	/**
	 * Height of the bare terrain at a local point, ignoring platform tops (curbs, crates, ledges). The
	 * slope that pulls the skater down hills is sampled from this, so a platform edge never reads as a hill.
	 */
	float terrainHeight(float x, float y);

	/**
	 * Absolute top height of any blocker crossed when moving from (x0, y0) to (x1, y1).
	 * {@link Float#NEGATIVE_INFINITY} if nothing blocks, {@link Float#POSITIVE_INFINITY} when leaving the world.
	 */
	float blockerTop(float x0, float y0, float x1, float y1);

	/**
	 * {@link #blockerTop(float, float, float, float)} for a skater of radius {@code r} (worlds with shaped
	 * blockers collide them with that radius, up to their index margin); by default the radius is ignored.
	 */
	default float blockerTop(float x0, float y0, float x1, float y1, float r)
	{
		return blockerTop(x0, y0, x1, y1);
	}

	/**
	 * Deepest overlap of a skater circle of radius {@code r} centred at (x, y) with any blocker whose top is
	 * above {@code feetH + maxStep} (lower blockers are stepped onto or cleared, not collided with). When the
	 * centre is already inside a blocker the normal is the least-penetration way out. Fills {@code out} and
	 * returns true on contact; returns false and leaves {@code out} untouched otherwise. Worlds without
	 * shaped blockers report no contacts.
	 */
	default boolean contact(float x, float y, float r, float feetH, float maxStep, Contact out)
	{
		return false;
	}

	/**
	 * Distance (local units) from (x, y) to the nearest side of the loaded area, past which the client blocks
	 * every tile; negative outside it. {@link Float#POSITIVE_INFINITY} when the world has no such edge.
	 */
	default float edgeDistance(float x, float y)
	{
		return Float.POSITIVE_INFINITY;
	}
}
