package com.gielinorskate.render;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.tricks.Trick;
import org.junit.Test;

public class BoardPlacementTest
{
	/** Lowest point of the mesh (largest y: y negative is up, the ground is y = 0). */
	private static float lowest(float[] tris)
	{
		float maxY = -Float.MAX_VALUE;
		for (int i = 1; i < tris.length; i += 3)
		{
			maxY = Math.max(maxY, tris[i]);
		}
		return maxY;
	}

	/** Lowest point of the vertices with z on the given side (sign) of the board. */
	private static float lowestAtEnd(float[] tris, float sign)
	{
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < tris.length; i += 3)
		{
			if (tris[i + 2] * sign > 20f)
			{
				maxY = Math.max(maxY, tris[i + 1]);
			}
		}
		return maxY;
	}

	@Test
	public void pivotIsTheTruckThatStaysDown()
	{
		assertEquals(-BoardPlacement.TRUCK_Z, BoardPlacement.pivotZ(0.25f), 0f);
		assertEquals(BoardPlacement.TRUCK_Z, BoardPlacement.pivotZ(-0.2f), 0f);
		assertEquals(30f, BoardPlacement.TRUCK_Z, 0f);
	}

	@Test
	public void zeroPitchIsUnchanged()
	{
		float[] base = BoardGeometry.standardBoard().tris;
		assertArrayEquals(BoardGeometry.rotate(base, 0.3f, 0f), BoardPlacement.pose(base, 0.3f, 0f), 1e-5f);
	}

	@Test
	public void manualAndGrindPitchesKeepTheContactWheelsOnTheGround()
	{
		float[] base = BoardGeometry.standardBoard().tris;
		for (float pitch : new float[]{0.25f, -0.25f, 0.2f, -0.2f})
		{
			float[] posed = BoardPlacement.pose(base, 0f, pitch);
			// nothing sinks below the ground or the rail top; the contact wheel still touches it
			assertTrue("pitch " + pitch + " lowest " + lowest(posed), lowest(posed) <= 0.01f);
			assertTrue("pitch " + pitch + " lowest " + lowest(posed), lowest(posed) >= -0.5f);
			// the other truck is lifted clear: about 60 * sin(pitch)
			float otherEnd = lowestAtEnd(posed, Math.signum(pitch));
			assertTrue("pitch " + pitch + " other end " + otherEnd, otherEnd < -10f);
		}
	}

	@Test
	public void centrePivotWouldSinkTheContactTruck()
	{
		// the old centre pivot puts the tail wheels 30 * sin(0.25) = 7.4 units under the ground
		float[] old = BoardGeometry.rotate(BoardGeometry.standardBoard().tris, 0f, 0.25f);
		assertTrue(lowest(old) > 6f);
	}

	@Test
	public void bundledBoardAlsoStaysOnTheGround()
	{
		float[] base = BoardGeometry.defaultBoard().tris;
		for (float pitch : new float[]{0.25f, -0.25f})
		{
			float lowest = lowest(BoardPlacement.pose(base, 0f, pitch));
			assertTrue("pitch " + pitch + " lowest " + lowest, lowest <= 0.6f && lowest >= -0.6f);
		}
	}

	@Test
	public void deckCentreLiftMatchesThePivotMaths()
	{
		assertEquals(0f, BoardPlacement.deckLift(0f), 1e-6f);
		// centre of the deck top (y = -16, z = 0) about the axle (y = -4.5, z = -+30):
		// y' = (-16 + 4.5) cos p - 30 sin|p| - 4.5, so lift = -16 - y' = 30 sin|p| - 11.5 (1 - cos p)
		float p = 0.25f;
		float expected = 30f * (float) Math.sin(p) - 11.5f * (1f - (float) Math.cos(p));
		assertEquals(expected, BoardPlacement.deckLift(p), 1e-4f);
		assertEquals(expected, BoardPlacement.deckLift(-p), 1e-4f);
	}

	@Test
	public void slidesSitTheDeckOnTheRail()
	{
		assertEquals(13f, BoardPlacement.slideDrop(SkaterState.GRINDING, Trick.BOARDSLIDE), 0f);
		assertEquals(13f, BoardPlacement.slideDrop(SkaterState.GRINDING, Trick.CROOKED), 0f);
		assertEquals(0f, BoardPlacement.slideDrop(SkaterState.GRINDING, Trick.FIFTY_FIFTY), 0f);
		assertEquals(0f, BoardPlacement.slideDrop(SkaterState.GRINDING, Trick.NOSEGRIND), 0f);
		assertEquals(0f, BoardPlacement.slideDrop(SkaterState.AIRBORNE, Trick.BOARDSLIDE), 0f);
		assertEquals(0f, BoardPlacement.slideDrop(SkaterState.ROLLING, null), 0f);
	}

	@Test
	public void zeroFlipIsTheRollAndPitchPose()
	{
		float[] tris = BoardGeometry.defaultBoard().tris;
		assertArrayEquals(BoardPlacement.pose(tris, 0.4f, 0.2f), BoardPlacement.pose(tris, 0.4f, 0.2f, 0f, -61f), 0f);
	}

	@Test
	public void flipPivotIsTheCentreOfMassAboveTheDeck()
	{
		// puppet: soles at 0, deck top 4 below them, the centre of mass 45 above the deck
		assertEquals(4f - 45f, BoardPlacement.puppetFlipPivotY(), 0f);
		// board: deck top at -16, so 45 above it is -61 (plus any deck lift)
		assertEquals(-61f, BoardPlacement.boardFlipPivotY(0f), 1e-4f);
		assertEquals(-64f, BoardPlacement.boardFlipPivotY(3f), 1e-4f);
	}

	@Test
	public void flipTurnsTheBoardRigidlyAboutThePivot()
	{
		float[] tris = BoardGeometry.defaultBoard().tris;
		float[] f = BoardPlacement.pose(tris, 0f, 0f, 1.1f, -61f);
		for (int i = 0; i < tris.length; i += 3)
		{
			assertEquals(tris[i], f[i], 1e-4f);
			assertEquals(Math.hypot(tris[i + 1] + 61f, tris[i + 2]), Math.hypot(f[i + 1] + 61f, f[i + 2]), 1e-3f);
		}
		// half a turn puts the board upside down 2 * 61 - 16 above its rest: the deck top at -16 goes to -106
		float[] half = BoardPlacement.pose(new float[]{0f, -16f, 0f}, 0f, 0f, (float) Math.PI, -61f);
		assertEquals(-106f, half[1], 1e-3f);
	}

	/**
	 * The board and the skater turn about one shared pivot: a point expressed in the puppet's model space
	 * and in the board's (board x = puppet z, board y = puppet y - (BOARD_TOP + FOOT_CLEARANCE + deckLift),
	 * board z = -puppet x; see MeshDeformer and SkaterRenderer) lands on the same spot after the flip.
	 */
	@Test
	public void flipKeepsBoardAndSkaterTogether()
	{
		for (float deckLift : new float[]{0f, 3f})
		{
			for (float flip : new float[]{0.4f, -1.7f, 3.0f, 7.5f})
			{
				float offset = -(BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE + deckLift);
				float[][] pts = {{0f, 4f, 0f}, {25f, 0f, -6f}, {-30f, -150f, 10f}, {8f, -41f, 3f}};
				for (float[] p : pts)
				{
					BodyPose pose = new BodyPose();
					pose.flip = flip;
					pose.flipPivotY = BoardPlacement.puppetFlipPivotY();
					float[] xs = {p[0]};
					float[] ys = {p[1]};
					float[] zs = {p[2]};
					MeshDeformer.deform(xs, ys, zs, 1, pose);

					float[] b = BoardPlacement.pose(new float[]{p[2], p[1] + offset, -p[0]}, 0f, 0f, flip,
						BoardPlacement.boardFlipPivotY(deckLift));
					assertEquals(b[0], zs[0], 1e-2f);
					assertEquals(b[1], ys[0] + offset, 1e-2f);
					assertEquals(b[2], -xs[0], 1e-2f);
				}
			}
		}
	}

	@Test
	public void aGrabLiftsTheBoardByTheRigsFeetLiftInWholeUnits()
	{
		assertEquals(20, BoardPlacement.grabLift(20f, false));
		assertEquals(12, BoardPlacement.grabLift(11.6f, false));
		assertEquals(0, BoardPlacement.grabLift(0f, false));
		// not in a bail, never down, never past the cap, nothing odd
		assertEquals(0, BoardPlacement.grabLift(20f, true));
		assertEquals(0, BoardPlacement.grabLift(-5f, false));
		assertEquals(Math.round(BoardPlacement.MAX_GRAB_LIFT), BoardPlacement.grabLift(500f, false));
		assertEquals(0, BoardPlacement.grabLift(Float.NaN, false));
		assertEquals(0, BoardPlacement.grabLift(Float.POSITIVE_INFINITY, false));
	}

	@Test
	public void aLiftedBoardsFlipPivotIsStillTheSkatersCentreOfMass()
	{
		// the board drawn `lift` units higher (and the body not): the centre of mass is that much lower against it
		int lift = 20;
		assertEquals(BoardPlacement.boardFlipPivotY(0) + lift, BoardPlacement.boardFlipPivotY(-lift), 1e-4f);
	}
}
