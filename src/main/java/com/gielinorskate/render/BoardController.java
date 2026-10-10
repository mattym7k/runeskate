package com.gielinorskate.render;

import lombok.RequiredArgsConstructor;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObjectController;

/**
 * Draws one part of the board (one object per part of the baked board). The board's roll and pitch are set each
 * frame by {@link SkaterRenderer}; the body flip angle is read from the shared {@link BodyPose} when the client draws,
 * the same moment the puppet reads it, so the board and the flipping skater can never be a frame apart (the pose rig
 * runs after the renderer in a frame). The mesh is rebuilt only when something changed. Client thread only.
 */
@RequiredArgsConstructor
public final class BoardController extends RuneLiteObjectController
{
	private final int part;
	private BakedBoardModel boardModel;
	private BodyPose body;
	private Model model;
	private float roll;
	private float pitch;
	private float pivotY;
	private boolean flipAllowed;
	private boolean dirty = true;
	private float builtFlip;

	public void setBoard(BakedBoardModel boardModel, BodyPose body)
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
		dirty |= roll != this.roll || pitch != this.pitch || flipPivotY != pivotY || flipAllowed != this.flipAllowed;
		this.roll = roll;
		this.pitch = pitch;
		this.pivotY = flipPivotY;
		this.flipAllowed = flipAllowed;
	}

	@Override
	public Model getModel()
	{
		float flip = flipAllowed && body != null ? body.flip : 0f;
		// a flip change of 0.002 rad (0.1 units at the 61-unit pivot radius) rebuilds the mesh
		if (boardModel != null && (dirty || Math.abs(flip - builtFlip) > 0.002f))
		{
			model = boardModel.pose(part, roll, pitch, flip, pivotY);
			builtFlip = flip;
			dirty = false;
		}
		return model;
	}
}
