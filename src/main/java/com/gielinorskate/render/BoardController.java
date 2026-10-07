package com.gielinorskate.render;

import net.runelite.api.Model;
import net.runelite.api.RuneLiteObjectController;

/**
 * Draws the board. The board's roll and pitch are set each frame by {@link SkaterRenderer}; the body flip
 * angle is read from the shared {@link BodyPose} when the client draws, the same moment the puppet reads
 * it, so the board and the flipping skater can never be a frame apart (the pose rig runs after the
 * renderer in a frame). The mesh is rebuilt only when something changed. Client thread only.
 */
public final class BoardController extends RuneLiteObjectController
{
	/** Flip change (radians) that rebuilds the mesh: 0.002 rad is 0.1 units at the 61-unit pivot radius. */
	private static final float FLIP_EPSILON = 0.002f;

	/** The board part this object draws (0 for the classic board; one object per part of the baked board). */
	private final int part;
	private BoardPoser boardModel;
	private BodyPose body;
	private Model model;
	private float roll;
	private float pitch;
	private float pivotY;
	private boolean flipAllowed;
	private boolean dirty = true;
	private float builtFlip;

	public BoardController()
	{
		this(0);
	}

	public BoardController(int part)
	{
		this.part = part;
	}

	public void setBoard(BoardPoser boardModel, BodyPose body)
	{
		this.boardModel = boardModel;
		this.body = body;
		dirty = true;
	}

	/** The board's colours changed: rebuild the model when next drawn. */
	public void repaint()
	{
		dirty = true;
	}

	/**
	 * @param flipPivotY the skater's centre of mass in board model space
	 * @param flipAllowed false when the skater cannot flip with the board (bailed, or not deformable)
	 */
	public void setPose(float roll, float pitch, float flipPivotY, boolean flipAllowed)
	{
		if (roll != this.roll || pitch != this.pitch || flipPivotY != pivotY || flipAllowed != this.flipAllowed)
		{
			this.roll = roll;
			this.pitch = pitch;
			this.pivotY = flipPivotY;
			this.flipAllowed = flipAllowed;
			dirty = true;
		}
	}

	@Override
	public Model getModel()
	{
		if (boardModel == null)
		{
			return model;
		}
		float flip = flipAllowed && body != null ? body.flip : 0f;
		if (dirty || Math.abs(flip - builtFlip) > FLIP_EPSILON)
		{
			model = boardModel.pose(part, roll, pitch, flip, pivotY);
			builtFlip = flip;
			dirty = false;
		}
		return model;
	}
}
