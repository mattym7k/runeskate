package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.physics.SkatePhysics;
import com.gielinorskate.physics.SkateTuning;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

/**
 * A bridge deck takes its heights from the plane above (tile settings bridge flag, as
 * {@code Perspective.getTileHeight} reads them). Corner heights shared between tiles let the deck's edge
 * corners take the river-bed heights of the neighbouring river tiles, so the deck sloped into the river and
 * the skater sank through it; each tile now keeps its own corners.
 * <p>
 * Synthetic scene, 12 x 12: a river (bed at 0) runs north-south over columns 4..7, banks at 200 either
 * side; a bridge 3 tiles wide crosses it east-west over rows 5..7, its deck on plane 1 at 200.
 */
public class BridgeTerrainTest
{
	private static final float T = GridCollisionWorld.TILE;
	private static final int N = 12;
	private static final float DECK = 200f;

	private static GridCollisionWorld bridgeWorld()
	{
		int[][][] heights = new int[4][N + 1][N + 1];
		byte[][][] settings = new byte[4][N][N];
		for (int cx = 0; cx <= N; cx++)
		{
			for (int cy = 0; cy <= N; cy++)
			{
				boolean bank = cx <= 4 || cx >= 8;
				heights[0][cx][cy] = bank ? -(int) DECK : 0; // RuneLite z is down-positive
				heights[1][cx][cy] = -(int) DECK;
				heights[2][cx][cy] = -(int) DECK - 240;
				heights[3][cx][cy] = -(int) DECK - 480;
			}
		}
		for (int tx = 4; tx <= 7; tx++)
		{
			for (int ty = 5; ty <= 7; ty++)
			{
				settings[1][tx][ty] = 2;
			}
		}
		GridCollisionWorld w = new GridCollisionWorld(N);
		SceneCollisionBuilder.applyTerrain(w, heights, settings, 0);
		return w;
	}

	@Test
	public void theWholeDeckIsFlatAtDeckHeight()
	{
		GridCollisionWorld w = bridgeWorld();
		for (int tx = 4; tx <= 7; tx++)
		{
			for (int ty = 5; ty <= 7; ty++)
			{
				for (float fx : new float[]{0.01f, 0.5f, 0.99f})
				{
					for (float fy : new float[]{0.01f, 0.5f, 0.99f})
					{
						float x = (tx + fx) * T;
						float y = (ty + fy) * T;
						assertEquals("deck at tile " + tx + "," + ty + " (" + fx + "," + fy + ")", DECK,
							w.groundHeight(x, y), 0.5f);
						assertEquals(DECK, w.terrainHeight(x, y), 0.5f);
					}
				}
			}
		}
	}

	@Test
	public void theRiverBesideTheBridgeKeepsItsBed()
	{
		GridCollisionWorld w = bridgeWorld();
		// river tiles just north and south of the deck are still at the bed, not dragged up to the deck
		assertEquals(0f, w.groundHeight(5.5f * T, 8.5f * T), 0.5f);
		assertEquals(0f, w.groundHeight(5.5f * T, 4.5f * T), 0.5f);
		assertEquals(0f, w.groundHeight(5.5f * T, 8.01f * T), 0.5f);
	}

	@Test
	public void ridingAlongTheBridgeEdgeRowNeverSinks()
	{
		for (float rowY : new float[]{5.15f, 5.5f, 7.5f, 7.85f})
		{
			GridCollisionWorld w = bridgeWorld();
			SkatePhysics p = new SkatePhysics(new SkateTuning(), w, 2.5f * T, rowY * T, (float) (Math.PI / 2));
			SkateInput in = new SkateInput();
			in.pushHeld = true;
			float minH = Float.POSITIVE_INFINITY;
			for (int i = 0; i < 400 && p.getX() < 9.5f * T; i++)
			{
				p.step(0.02f, in);
				assertNotEquals(SkaterState.BAILED, p.getState());
				minH = Math.min(minH, p.getH());
				// no sideways drift off the row: the deck is level across
				assertEquals("row " + rowY, rowY * T, p.getY(), 1f);
			}
			assertTrue("crossed the bridge on row " + rowY + ": x " + p.getX(), p.getX() >= 9.5f * T);
			assertEquals("never sank on row " + rowY, DECK, minH, 0.5f);
		}
	}

	@Test
	public void aTileFlaggedAsBridgeReadsThePlaneAbove()
	{
		byte[][][] settings = new byte[4][2][2];
		settings[1][1][0] = 2;
		assertEquals(1, SceneCollisionBuilder.effectivePlane(settings, 0, 1, 0));
		assertEquals(0, SceneCollisionBuilder.effectivePlane(settings, 0, 0, 0));
		assertEquals(3, SceneCollisionBuilder.effectivePlane(settings, 3, 1, 0));
	}
}
