package com.gielinorskate.render;

import net.runelite.api.Model;

/**
 * A board mesh that can be drawn in a pose: the classic {@link BoardModel} (one model) or the baked
 * {@link BakedBoardModel} (one model per part, each drawn by its own {@link BoardController} placed the same).
 */
public interface BoardPoser
{
	/**
	 * A lit model of the board rotated by roll (around its long axis) and pitch (about the contact truck), then
	 * turned by {@code flip} about the skater's centre of mass at {@code pivotY}. Client thread.
	 */
	Model pose(float roll, float pitch, float flip, float pivotY);

	/** How many models (parts) the board is drawn as. */
	default int partCount()
	{
		return 1;
	}

	/** Part {@code part} (0 .. {@link #partCount()} - 1) of the board in a pose, as {@link #pose(float, float, float, float)}. */
	default Model pose(int part, float roll, float pitch, float flip, float pivotY)
	{
		return part == 0 ? pose(roll, pitch, flip, pivotY) : null;
	}

	/**
	 * One half of this board snapped across the middle of the deck ({@link BoardSplit}): the nose half or the tail
	 * half, in this board's colours (and designs) now, posed on its own. Null when this board can't snap. Client thread.
	 */
	default HalfBoard half(boolean nose)
	{
		return null;
	}
}
