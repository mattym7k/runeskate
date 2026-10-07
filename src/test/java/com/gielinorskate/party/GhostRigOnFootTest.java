package com.gielinorskate.party;

import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.BodyPose;
import org.junit.Test;

public class GhostRigOnFootTest
{
	private static final float FRAME = 1f / 60f;
	private static final GhostPredictor.Ground FLAT = (x, y) -> 0f;

	private final GhostPredictor predictor = new GhostPredictor();
	private final GhostRig rig = new GhostRig();
	private final BodyPose out = new BodyPose();
	private int seq;
	private float now;

	private void acceptWalker(SkaterState st, float vy, float vh, int events)
	{
		predictor.accept(new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, vy, vh, st, null, events, null, 0f, ++seq, 0f, 0f,
			0f, BoardState.CARRIED, 0f, 0f, 0f, 0f), now, FLAT);
	}

	private void run(float seconds)
	{
		int frames = Math.round(seconds / FRAME);
		for (int i = 0; i < frames; i++)
		{
			now += FRAME;
			rig.updateOnFoot(predictor, predictor.pose(now, FLAT), now, FRAME, out);
		}
	}

	@Test
	public void aWalkingGhostStandsUpright()
	{
		acceptWalker(SkaterState.ROLLING, FootPhysics.SPRINT_SPEED, 0f, 0);
		run(0.5f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void aJumpingGhostTucksAndLands()
	{
		acceptWalker(SkaterState.ROLLING, 0f, 0f, 0);
		run(0.1f);
		acceptWalker(SkaterState.AIRBORNE, 0f, FootPhysics.JUMP_VH, GhostCodec.EV_POP);
		run(0.35f);
		assertTrue("tucked: " + out.legScale, out.legScale < 0.9f);
		acceptWalker(SkaterState.ROLLING, 0f, 0f, GhostCodec.EV_LAND);
		run(1.5f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void backOnTheBoardItSkatesAsBefore()
	{
		acceptWalker(SkaterState.AIRBORNE, 0f, FootPhysics.JUMP_VH, GhostCodec.EV_POP);
		run(0.35f);
		predictor.accept(new GhostState(420, 0, 0f, 0f, 0f, 0f, 0f, 1200f, 0f, SkaterState.ROLLING, null,
			GhostCodec.EV_LAND, null, 0f, ++seq, 2f, 0f, 0f), now, FLAT);
		now += FRAME;
		for (int i = 0; i < 30; i++)
		{
			now += FRAME;
			rig.update(predictor, predictor.pose(now, FLAT), now, FRAME, out);
		}
		assertTrue("leans into the carve: " + out.roll, out.roll < -0.3f);
	}
}
