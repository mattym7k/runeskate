package com.gielinorskate.render;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObjectController;

/**
 * Draws the local player's current (animated) model at the controller's position, with the procedural
 * {@link BodyPose} applied when that is provably safe (see {@link DeformingModel}). Client thread only; no
 * allocation per frame.
 */
public final class PuppetController extends RuneLiteObjectController
{
	private final DeformingModel model;

	/** @param hand reads the drawn model's right hand, for the carried board */
	public PuppetController(Client client, BodyPose pose, HandAnchor hand)
	{
		this.model = new DeformingModel(() ->
		{
			Player p = client.getLocalPlayer();
			return p == null ? null : p.getModel();
		}, pose, hand);
	}

	/** Tells {@code report} whether each draw had a model (null: stop telling). */
	public void setDrawnReport(DrawnModelReport report)
	{
		model.setDrawnReport(report);
	}

	/** False once deformation failed and was switched off: the skater then cannot flip with the board. */
	public boolean canDeform()
	{
		return model.canDeform();
	}

	@Override
	public Model getModel()
	{
		return model.get();
	}
}
