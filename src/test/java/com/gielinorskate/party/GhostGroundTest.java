package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import com.gielinorskate.render.RenderPose;
import com.gielinorskate.world.GridCollisionWorld;
import org.junit.Test;

/** Played-back ghosts on the ground follow the receiver's ground: the local collision world, steps included. */
public class GhostGroundTest
{
	private static final int BASE_X = 3200;
	private static final int BASE_Y = 3200;

	/** A scene of 16 tiles with a staircase along x: each tile from x = 4 on one 24-unit step higher. */
	private static GridCollisionWorld stairs()
	{
		GridCollisionWorld w = new GridCollisionWorld(16);
		for (int tx = 4; tx < 12; tx++)
		{
			for (int ty = 0; ty < 16; ty++)
			{
				w.setTile(tx, ty, GridCollisionWorld.FULL, 24f * (tx - 3));
			}
		}
		return w;
	}

	@Test
	public void theCollisionGroundIsInAbsoluteCoordinatesAndUnknownOffItsGrid()
	{
		GridCollisionWorld w = stairs();
		GhostPredictor.Ground g = PartyGhostService.collisionGround(w, BASE_X, BASE_Y, w.size());
		float x0 = BASE_X * 128f;
		float y0 = BASE_Y * 128f;
		assertEquals(0f, g.heightAt(x0 + 64f, y0 + 64f), 1e-3f);
		assertEquals(24f, g.heightAt(x0 + 4 * 128f + 64f, y0 + 64f), 1e-3f);
		assertEquals(w.groundHeight(6 * 128f + 10f, 300f), g.heightAt(x0 + 6 * 128f + 10f, y0 + 300f), 0f);
		assertTrue(Float.isNaN(g.heightAt(x0 - 1f, y0 + 64f)));
		assertTrue(Float.isNaN(g.heightAt(x0 + 16 * 128f, y0 + 64f)));
	}

	@Test
	public void aGhostClimbingStairsStepsUpWhereTheStepsAreNotBetweenSamples()
	{
		GridCollisionWorld w = stairs();
		GhostPredictor.Ground ground = PartyGhostService.collisionGround(w, BASE_X, BASE_Y, w.size());
		float x0 = BASE_X * 128f;
		float y = BASE_Y * 128f + 640f;
		float speed = 500f;
		GhostPredictor g = new GhostPredictor();
		int next = 0;
		float maxOff = 0f;
		for (double s = 0; s < 3.4; s += 1 / 60.0)
		{
			while (next * 0.6 + 0.1 <= s)
			{
				double sent = next * 0.6;
				float[] ago = {0.15f, 0.3f, 0.45f};
				float[] xs = new float[3];
				float[] ys = {y, y, y};
				float[] hs = new float[3];
				float[] hds = {Angles.PI / 2, Angles.PI / 2, Angles.PI / 2};
				SkaterState[] sts = {SkaterState.ROLLING, SkaterState.ROLLING, SkaterState.ROLLING};
				for (int i = 0; i < 3; i++)
				{
					xs[i] = x0 + 200f + speed * (float) (sent - ago[i]);
					// the sender stood on its own (same) stairs
					hs[i] = ground.heightAt(xs[i], y);
				}
				float x = x0 + 200f + speed * (float) sent;
				GhostState f = GhostFeed.frame(330, 0, x, y, ground.heightAt(x, y), Angles.PI / 2, speed, 0f, 0f,
					SkaterState.ROLLING, null, null, 0f);
				SkateGhostUpdate m = GhostCodec.encode(f, 0, null);
				m.seq = next + 1;
				m.tj = GhostFeed.wire(GhostTrajectory.timeMs((float) sent), 0, null, 3, ago, xs, ys, hs, hds,
					sts, m.x, m.y, m.h, m.hd);
				g.accept(GhostCodec.decode(m), GhostTrajectory.decode(m.tj, 0, m.x, m.y, m.h, m.hd),
					(float) (sent + 0.1), ground);
				next++;
			}
			if (g.latest() == null || s < 1.2)
			{
				continue;
			}
			RenderPose p = g.pose((float) s, ground);
			// on the step under it, never halfway up a riser
			maxOff = Math.max(maxOff, Math.abs(p.h - ground.heightAt(p.x, p.y)));
		}
		assertTrue("off the steps by " + maxOff, maxOff < 1f);
	}
}
