package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.BodyFlip;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.BodyPose;
import org.junit.Test;

public class GhostRigTest
{
	private static final float FRAME = GhostFeed.FRAME;
	private static final float DELAY = GhostFeed.DELAY;

	private final GhostFeed feed = new GhostFeed();
	private final GhostPredictor predictor = feed.predictor;
	private final GhostRig rig = new GhostRig();
	private final BodyPose out = new BodyPose();

	public GhostRigTest()
	{
		feed.warmUp();
	}

	private void accept(SkaterState st, float h, float vy, float vh, int events, float turnRate, float bodyFlip,
		float bodyFlipRate)
	{
		feed.send(GhostFeed.state(420, 0, 0f, 0f, h, 0f, 0f, vy, vh, st, null, events, null, 0f, 0, turnRate, bodyFlip,
			bodyFlipRate));
	}

	private void run(float seconds)
	{
		feed.run(seconds, (p, now) -> rig.update(p, p.pose(now, GhostFeed.FLAT), now, FRAME, out));
	}

	@Test
	public void aCarvingGhostLeansIntoTheTurn()
	{
		// clockwise (right) at 2 rad/s and 1200 u/s: the physical lean is atan(1200 * 2 / 2000) = 0.88 rad
		accept(SkaterState.ROLLING, 0f, 1200f, 0f, 0, 2f, 0f, 0f);
		run(DELAY + 0.5f);
		assertTrue("leans right: " + out.roll, out.roll < -0.3f);
	}

	@Test
	public void aStraightRollingGhostKeepsTheStandingPose()
	{
		accept(SkaterState.ROLLING, 0f, 1200f, 0f, 0, 0f, 0f, 0f);
		run(DELAY + 0.5f);
		assertTrue(out.isNeutral());
	}

	@Test
	public void aPopTucksInTheAirAndALandingSquashesAndRecovers()
	{
		accept(SkaterState.AIRBORNE, 0f, 0f, 800f, GhostCodec.EV_POP, 0f, 0f, 0f);
		run(DELAY + 0.3f);
		assertTrue("tucked: " + out.legScale, out.legScale < 0.85f);
		accept(SkaterState.AIRBORNE, 100f, 0f, -800f, 0, 0f, 0f, 0f);
		run(0.1f);
		accept(SkaterState.ROLLING, 0f, 0f, 0f, GhostCodec.EV_LAND, 0f, 0f, 0f);
		run(DELAY + 0.1f);
		float squashed = out.legScale;
		assertTrue("squashed: " + squashed, squashed < 0.9f);
		run(0.8f);
		assertEquals(1f, out.legScale, 0.02f);
	}

	@Test
	public void theBodyFlipsWithTheGhost()
	{
		float held = Angles.TWO_PI / 0.55f;
		accept(SkaterState.AIRBORNE, 200f, 0f, 0f, GhostCodec.EV_POP, 0f, 0f, held);
		run(DELAY + 0.2f);
		assertEquals(predictor.bodyFlip(feed.now), out.flip, 0.01f);
		assertTrue(out.flip > 1f);
	}

	@Test
	public void aBailEasesTheFlipOutInsteadOfSnapping()
	{
		float held = Angles.TWO_PI / 0.55f;
		accept(SkaterState.AIRBORNE, 200f, 0f, 0f, GhostCodec.EV_POP, 0f, 0f, held);
		run(DELAY + 0.2f);
		accept(SkaterState.BAILED, 0f, 0f, 0f, GhostCodec.EV_BAIL, 0f, 0f, 0f);
		float[] step = {out.flip, 0f};
		feed.run(DELAY + 0.1f, (p, now) ->
		{
			rig.update(p, p.pose(now, GhostFeed.FLAT), now, FRAME, out);
			// a whole turn apart looks the same
			step[1] = Math.max(step[1], Math.abs(BodyFlip.residual(out.flip - step[0])));
			step[0] = out.flip;
		});
		assertTrue("no snap: " + step[1], step[1] < 0.5f);
		run(1f);
		assertEquals(0f, out.flip, 0.01f);
	}

	@Test
	public void aPushingGhostKicks()
	{
		accept(SkaterState.ROLLING, 0f, 300f, 0f, 0, 0f, 0f, 0f);
		run(DELAY + 0.3f);
		assertEquals(0f, out.legWeight, 0f);
		accept(SkaterState.ROLLING, 0f, 400f, 0f, GhostCodec.EV_PUSH, 0f, 0f, 0f);
		run(DELAY + 0.2f);
		assertTrue("the push leg swings: " + out.legWeight, out.legWeight > 0.1f);
		// it plays out and stands again
		run(1f);
		assertEquals(0f, out.legWeight, 0.01f);
	}

	@Test
	public void aChargingGhostCrouchesAndExtendsOnThePop()
	{
		feed.send(GhostFeed.withBody(GhostFeed.state(420, 0, 0f, 0f, 0f, 0f, 0f, 600f, 0f, SkaterState.ROLLING, null,
			0, null, 0f, 0), 1f, null, 0f));
		run(DELAY + 0.4f);
		assertTrue("crouched: " + out.legScale, out.legScale < 0.8f);
		accept(SkaterState.AIRBORNE, 0f, 600f, 800f, GhostCodec.EV_POP, 0f, 0f, 0f);
		run(DELAY + 0.05f);
		assertTrue("extends: " + out.legScale, out.legScale > 0.95f);
	}

	@Test
	public void aKnockedDownGhostTumblesLiesAndGetsUp()
	{
		float lie = 3 * (float) Math.PI / 2;
		rig.knockdown(new com.gielinorskate.render.KnockdownPose(
			com.gielinorskate.render.KnockdownPose.Stage.AIR, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			false, out);
		assertEquals(1f, out.flip, 0f);
		assertTrue(out.legScale < 1f);
		// an OSRS lie-down plays: the body is left as animated
		rig.knockdown(new com.gielinorskate.render.KnockdownPose(
			com.gielinorskate.render.KnockdownPose.Stage.LIE, 0.5f, 0f, 0f, 0f, 0f, lie, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			true, out);
		assertTrue(out.isNeutral());
		// back on the board the rig starts from standing (no left-over spring)
		accept(SkaterState.ROLLING, 0f, 300f, 0f, 0, 0f, 0f, 0f);
		run(FRAME);
		assertEquals(0f, out.flip, 0.01f);
		assertEquals(1f, out.legScale, 0.01f);
	}
}
