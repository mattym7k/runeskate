package com.gielinorskate.world;

import java.util.ArrayList;
import java.util.List;

/** Collects boxes for a test {@link BlockerSet}, then indexes them for a grid of size x size tiles. */
public final class BlockerSetBuilder
{
	private final List<float[]> boxes = new ArrayList<>();

	/** A box centred at (cx, cy), half extents hx along the unit vector (cos, sin) and hy across it. */
	public BlockerSetBuilder add(float cx, float cy, float hx, float hy, float cos, float sin, float top, byte kind, int flags)
	{
		boxes.add(new float[]{cx, cy, hx, hy, cos, sin, top, kind, flags});
		return this;
	}

	public BlockerSet build(int gridSize)
	{
		return new BlockerSet(boxes, gridSize);
	}
}
