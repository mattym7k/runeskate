package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.FootPhysics;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.FootBody.Gait;
import org.junit.Test;

public class FootBodyTest
{
	@Test
	public void gaitFollowsSpeedWithHysteresis()
	{
		assertEquals(Gait.IDLE, FootBody.gait(Gait.IDLE, 0f, false));
		assertEquals(Gait.IDLE, FootBody.gait(Gait.IDLE, FootBody.IDLE_TO_WALK - 1f, false));
		assertEquals(Gait.WALK, FootBody.gait(Gait.IDLE, FootPhysics.WALK_SPEED, false));
		// slowing through the band keeps walking, below it stands
		assertEquals(Gait.WALK, FootBody.gait(Gait.WALK, FootBody.IDLE_TO_WALK - 1f, false));
		assertEquals(Gait.IDLE, FootBody.gait(Gait.WALK, FootBody.WALK_TO_IDLE - 1f, false));
		assertEquals(Gait.RUN, FootBody.gait(Gait.WALK, FootPhysics.SPRINT_SPEED, false));
		assertEquals(Gait.RUN, FootBody.gait(Gait.IDLE, FootPhysics.SPRINT_SPEED, false));
		assertEquals(Gait.RUN, FootBody.gait(Gait.RUN, FootBody.WALK_TO_RUN - 1f, false));
		assertEquals(Gait.WALK, FootBody.gait(Gait.RUN, FootBody.RUN_TO_WALK - 1f, false));
		assertEquals(Gait.IDLE, FootBody.gait(Gait.RUN, 0f, false));
	}

	@Test
	public void walkAndSprintSpeedsSitClearOfTheBands()
	{
		assertTrue(FootPhysics.WALK_SPEED > FootBody.IDLE_TO_WALK);
		assertTrue(FootPhysics.WALK_SPEED < FootBody.RUN_TO_WALK);
		assertTrue(FootPhysics.SPRINT_SPEED > FootBody.WALK_TO_RUN);
		assertTrue(FootBody.WALK_TO_IDLE < FootBody.IDLE_TO_WALK);
		assertTrue(FootBody.RUN_TO_WALK < FootBody.WALK_TO_RUN);
	}

	@Test
	public void inTheAirTheBodyStandsForTheTuck()
	{
		assertEquals(Gait.IDLE, FootBody.gait(Gait.RUN, FootPhysics.SPRINT_SPEED, true));
	}

	@Test
	public void aJumpPopsTucksAndLands()
	{
		FootBody body = new FootBody();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		body.fill(s, FootPhysics.WALK_SPEED, false, false, 0f);
		assertEquals(SkaterState.ROLLING, s.state);
		assertTrue(s.onFoot);
		assertFalse(s.popped);
		assertEquals(Gait.WALK, body.gait());

		body.fill(s, FootPhysics.WALK_SPEED, true, true, 500f);
		assertEquals(SkaterState.AIRBORNE, s.state);
		assertTrue(s.popped);
		assertFalse(s.rolledOff);
		body.fill(s, FootPhysics.WALK_SPEED, true, true, -480f);
		assertFalse(s.popped);
		assertFalse(s.landed);

		body.fill(s, FootPhysics.WALK_SPEED, false, false, 0f);
		assertTrue(s.landed);
		assertEquals(480f, s.landingSpeed, 0f);
		assertEquals(SkaterState.ROLLING, s.state);
		body.fill(s, FootPhysics.WALK_SPEED, false, false, 0f);
		assertFalse(s.landed);
	}

	@Test
	public void aFallIsNotAJump()
	{
		FootBody body = new FootBody();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		body.fill(s, 0f, false, false, 0f);
		body.fill(s, 0f, true, false, -10f);
		assertFalse(s.popped);
		assertTrue(s.rolledOff);
	}

	@Test
	public void theOnFootTuckIsLighterThanATrickAndAFallBarelyBends()
	{
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		s.state = SkaterState.AIRBORNE;
		s.onFoot = true;
		s.verticalSpeed = 100f;
		assertEquals(SkaterPoseRig.FOOT_JUMP_TUCK, SkaterPoseRig.crouchTarget(s, 0.3f, true), 0f);
		assertEquals(SkaterPoseRig.FOOT_FALL_TUCK, SkaterPoseRig.crouchTarget(s, 0.3f, false), 0f);
		// straight legs just after take-off, reaching for the ground when falling fast
		assertEquals(SkaterPoseRig.POP_EXTEND, SkaterPoseRig.crouchTarget(s, 0.05f, true), 0f);
		s.verticalSpeed = -SkaterPoseRig.LAND_REACH_SPEED - 1f;
		assertEquals(SkaterPoseRig.LAND_REACH, SkaterPoseRig.crouchTarget(s, 0.3f, true), 0f);
		assertTrue(SkaterPoseRig.FOOT_JUMP_TUCK < SkaterPoseRig.TRICK_TUCK);
		assertTrue(SkaterPoseRig.FOOT_FALL_TUCK < SkaterPoseRig.FOOT_JUMP_TUCK);
	}

	@Test
	public void theRigTucksOnAFootJumpAndSettlesAfter()
	{
		FootBody body = new FootBody();
		SkaterPoseRig rig = new SkaterPoseRig();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		BodyPose pose = new BodyPose();
		float dt = 1f / 50f;
		body.fill(s, 0f, false, false, 0f);
		rig.update(s, dt);
		float vh = FootPhysics.JUMP_VH;
		float minLeg = 1f;
		for (int i = 0; i < 25; i++)
		{
			body.fill(s, 0f, true, true, vh);
			rig.update(s, dt);
			rig.writeTo(pose);
			minLeg = Math.min(minLeg, pose.legScale);
			vh -= 2000f * dt;
		}
		assertTrue("knees bend in the jump: " + minLeg, minLeg < 0.9f);
		for (int i = 0; i < 100; i++)
		{
			body.fill(s, 0f, false, false, 0f);
			rig.update(s, dt);
		}
		rig.writeTo(pose);
		assertTrue(pose.isNeutral());
	}

	@Test
	public void resetForgetsTheFlight()
	{
		FootBody body = new FootBody();
		SkaterPoseRig.Signals s = new SkaterPoseRig.Signals();
		body.fill(s, FootPhysics.SPRINT_SPEED, true, true, 100f);
		body.reset();
		assertEquals(Gait.IDLE, body.gait());
		body.fill(s, 0f, false, false, 0f);
		assertFalse(s.landed);
	}
}
