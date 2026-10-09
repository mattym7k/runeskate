package com.gielinorskate.party;

import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.BoardState;
import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.BodyPose;
import org.junit.Test;

public class GhostRigOnFootTest
{
	private static final float FRAME = GhostFeed.FRAME;
	private static final float DELAY = GhostFeed.DELAY;

	private final GhostFeed feed = new GhostFeed();
	private final GhostRig rig = new GhostRig();
	private final BodyPose out = new BodyPose();

	public GhostRigOnFootTest()
	{
		feed.warmUp();
	}

	private void acceptWalker(SkaterState st, float vy, float vh, int events)
	{
		feed.send(GhostFeed.state(420, 0, 0f, 0f, 0f, 0f, 0f, vy, vh, st, null, events, null, 0f, 0, 0f, 0f, 0f,
			BoardState.CARRIED, 0f, 0f, 0f, 0f));
	}

	private void run(float seconds)
	{
		feed.run(seconds, (p, now) -> rig.updateOnFoot(p, p.pose(now, GhostFeed.FLAT), now, FRAME, out));
	}

	@Test
	public void aWalkingGhostStandsUpright()
	{
		acceptWalker(SkaterState.ROLLING, FootPhysics.SPRINT_SPEED, 0f, 0);
		run(DELAY + 0.5f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void aJumpingGhostTucksAndLands()
	{
		acceptWalker(SkaterState.ROLLING, 0f, 0f, 0);
		run(0.1f);
		acceptWalker(SkaterState.AIRBORNE, 0f, FootPhysics.JUMP_VH, GhostCodec.EV_POP);
		run(DELAY + 0.35f);
		assertTrue("tucked: " + out.legScale, out.legScale < 0.9f);
		acceptWalker(SkaterState.ROLLING, 0f, 0f, GhostCodec.EV_LAND);
		run(1.5f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void backOnTheBoardItSkatesAsBefore()
	{
		acceptWalker(SkaterState.AIRBORNE, 0f, FootPhysics.JUMP_VH, GhostCodec.EV_POP);
		run(DELAY + 0.35f);
		feed.send(GhostFeed.state(420, 0, 0f, 0f, 0f, 0f, 0f, 1200f, 0f, SkaterState.ROLLING, null, GhostCodec.EV_LAND,
			null, 0f, 0, 2f, 0f, 0f));
		feed.run(DELAY + 0.5f, (p, now) -> rig.update(p, p.pose(now, GhostFeed.FLAT), now, FRAME, out));
		assertTrue("leans into the carve: " + out.roll, out.roll < -0.3f);
	}
}
