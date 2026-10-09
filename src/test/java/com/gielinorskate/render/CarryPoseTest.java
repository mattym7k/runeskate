package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.FootPhysics;
import org.junit.Test;

public class CarryPoseTest
{
	/** Where the board's held point (its middle) ends up in local coordinates: {x, y, up-positive height}. */
	private static float[] heldPointWorld(Placement p)
	{
		float[] c = BoardPlacement.pose(new float[]{0f, CarryPose.HELD_Y, 0f}, p.roll, p.pitch);
		// RuneLite's model-to-local rotation (see MeshDeformer): x' = x cos + z sin, y' = z cos - x sin
		double theta = p.jau * Math.PI / 1024.0;
		float lx = (float) (c[0] * Math.cos(theta) + c[2] * Math.sin(theta));
		float ly = (float) (c[2] * Math.cos(theta) - c[0] * Math.sin(theta));
		return new float[]{p.x + lx, p.y + ly, -(p.z + c[1])};
	}

	@Test
	public void theBoardsMiddleIsInTheRightHandAtHipHeight()
	{
		Placement p = new Placement();
		// facing north: the right hand is east (+x)
		CarryPose.placeAt(1000f, 2000f, 50f, 0f, 0f, -CarryPose.HAND_SIDE, -CarryPose.HAND_HEIGHT, 0f, p);
		float[] w = heldPointWorld(p);
		assertEquals(1000f + CarryPose.HAND_SIDE, w[0], 0.5f);
		assertEquals(2000f, w[1], 0.5f);
		assertEquals(50f + CarryPose.HAND_HEIGHT, w[2], 0.5f);

		// facing east: the right hand is south (-y)
		CarryPose.placeAt(1000f, 2000f, 50f, Angles.PI / 2, 0f, -CarryPose.HAND_SIDE, -CarryPose.HAND_HEIGHT, 0f, p);
		w = heldPointWorld(p);
		assertEquals(1000f, w[0], 0.5f);
		assertEquals(2000f - CarryPose.HAND_SIDE, w[1], 0.5f);
		assertEquals(50f + CarryPose.HAND_HEIGHT, w[2], 0.5f);
	}

	@Test
	public void theBoardPointsAlongTheWalkAndStandsNearlyOnEdge()
	{
		Placement p = new Placement();
		CarryPose.placeAt(0f, 0f, 0f, 1.2f, FootPhysics.WALK_SPEED, -CarryPose.HAND_SIDE, -CarryPose.HAND_HEIGHT, 0f, p);
		assertEquals(Angles.toJau(1.2f), p.jau, 0f);
		assertTrue(Math.abs(p.roll) > 1.3f && Math.abs(p.roll) <= Angles.PI / 2);
		assertTrue(p.pitch != 0f);
	}

	@Test
	public void itTiltsMoreTheFasterTheWalk()
	{
		float still = CarryPose.pitch(0f);
		float walk = CarryPose.pitch(FootPhysics.WALK_SPEED);
		float sprint = CarryPose.pitch(FootPhysics.SPRINT_SPEED);
		assertTrue(Math.abs(walk) > Math.abs(still));
		assertTrue(Math.abs(sprint) > Math.abs(walk));
		assertEquals(sprint, CarryPose.pitch(10_000f), 0f);
		assertTrue(Math.abs(sprint) < 0.7f);
	}

	@Test
	public void theTiltMovesInSmallStepsSoASpeedJitterDoesNotRebuildTheBoard()
	{
		float walk = FootPhysics.WALK_SPEED;
		assertEquals(CarryPose.pitch(walk), CarryPose.pitch(walk * 1.01f), 0f);
		assertEquals(Tuning.STILL_TILT, CarryPose.pitch(0.5f), 0f);
		assertEquals(Tuning.SPRINT_TILT, CarryPose.pitch(FootPhysics.SPRINT_SPEED), 1e-6f);
		for (float v = 0f; v <= FootPhysics.SPRINT_SPEED; v += FootPhysics.SPRINT_SPEED / 97f)
		{
			float exact = Tuning.STILL_TILT
				+ (Tuning.SPRINT_TILT - Tuning.STILL_TILT) * (v / FootPhysics.SPRINT_SPEED);
			assertEquals(exact, CarryPose.pitch(v), 0.011f);
		}
	}

	@Test
	public void aMountHopBringsTheBoardDownUnderTheFeetOnTheWayDown()
	{
		assertEquals(0f, CarryPose.mountDrop(FootPhysics.MOUNT_JUMP_VH), 0f);
		assertEquals(0f, CarryPose.mountDrop(0f), 0f);
		assertEquals(1f, CarryPose.mountDrop(-FootPhysics.MOUNT_JUMP_VH), 0f);
		float half = CarryPose.mountDrop(-0.5f * Tuning.MOUNT_DROP_SPEED);
		assertTrue(half > 0.3f && half < 0.7f);
	}

	@Test
	public void underTheFeetTheDeckIsJustBelowTheSoles()
	{
		Placement p = new Placement();
		CarryPose.underFeet(1000f, 2000f, 50f, Angles.PI / 2, p);
		assertEquals(1000f, p.x, 0f);
		assertEquals(2000f, p.y, 0f);
		// board origin at the wheels' bottoms: deck top BOARD_TOP above it, FOOT_CLEARANCE under the soles
		assertEquals(-(50f - BoardGeometry.BOARD_TOP - BoardPlacement.FOOT_CLEARANCE), p.z, 1e-3f);
		assertEquals(Angles.toJau(Angles.PI / 2), p.jau, 0f);
		assertEquals(0f, p.roll, 0f);
		assertEquals(0f, p.pitch, 0f);
	}

	@Test
	public void theFixedAnchorAsAModelHandIsTheRightHandBesideTheBody()
	{
		Placement p = new Placement();
		for (float heading : new float[]{0f, 1f, -2.5f, Angles.PI / 2})
		{
			CarryPose.placeAt(100f, 200f, 30f, heading, 400f, -CarryPose.HAND_SIDE, -CarryPose.HAND_HEIGHT, 0f, p);
			float[] w = heldPointWorld(p);
			// to the right of the heading (cos, -sin)
			assertEquals(100f + (float) Math.cos(heading) * CarryPose.HAND_SIDE, w[0], 0.2f);
			assertEquals(200f - (float) Math.sin(heading) * CarryPose.HAND_SIDE, w[1], 0.2f);
			assertEquals(30f + CarryPose.HAND_HEIGHT, w[2], 0.2f);
		}
	}

	@Test
	public void aHandSwungForwardCarriesTheBoardForward()
	{
		Placement p = new Placement();
		// facing north, the hand 25 forward (model -z) and 10 lower than the fixed anchor
		CarryPose.placeAt(0f, 0f, 0f, 0f, 0f, -CarryPose.HAND_SIDE, -CarryPose.HAND_HEIGHT + 10f, -25f, p);
		float[] w = heldPointWorld(p);
		assertEquals(CarryPose.HAND_SIDE, w[0], 0.5f);
		assertEquals(25f, w[1], 0.5f);
		assertEquals(CarryPose.HAND_HEIGHT - 10f, w[2], 0.5f);
	}
}
