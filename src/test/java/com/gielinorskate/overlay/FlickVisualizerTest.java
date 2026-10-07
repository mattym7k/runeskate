package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.input.GestureRecognizer;
import com.gielinorskate.input.LiveStroke;
import com.gielinorskate.tricks.Gesture.Direction;
import org.junit.Test;

public class FlickVisualizerTest
{
	@Test
	public void theRingRadiusStandsForAWholeWindUpAndFlick()
	{
		// 48 px reach (18 + 30) on a 40 px ring: a 24 px pull down sits halfway down the ring
		float[] p = FlickVisualizer.toRing(0f, 24f, 48f, 40f);
		assertEquals(0f, p[0], 1e-4f);
		assertEquals(20f, p[1], 1e-4f);
	}

	@Test
	public void farOffsetsAreClampedToTheRingKeepingTheirDirection()
	{
		float[] p = FlickVisualizer.toRing(300f, -400f, 48f, 40f);
		assertEquals(40f, Math.hypot(p[0], p[1]), 1e-3);
		assertEquals(0.6f, p[0] / 40f, 1e-4f);
		assertEquals(-0.8f, p[1] / 40f, 1e-4f);
	}

	@Test
	public void trailFadesOverItsWindow()
	{
		assertEquals(1f, FlickVisualizer.trailAlpha(0), 0f);
		assertEquals(0.5f, FlickVisualizer.trailAlpha(150), 1e-4f);
		assertEquals(0f, FlickVisualizer.trailAlpha(300), 0f);
		assertEquals(0f, FlickVisualizer.trailAlpha(1000), 0f);
	}

	@Test
	public void flashFadesOverAQuarterSecond()
	{
		assertEquals(1f, FlickVisualizer.flashAlpha(0), 0f);
		assertEquals(0.5f, FlickVisualizer.flashAlpha(125), 1e-4f);
		assertEquals(0f, FlickVisualizer.flashAlpha(250), 0f);
	}

	@Test
	public void sectorsTileTheCircleAndMatchTheRecognizer()
	{
		double total = 0;
		for (Direction d : Direction.values())
		{
			total += FlickVisualizer.sector(d)[1];
		}
		assertEquals(360.0, total, 1e-9);
		// up is centred on 12 o'clock, up-left between up and left, right on 3 o'clock
		double[] up = FlickVisualizer.sector(Direction.UP);
		assertEquals(90.0, up[0] + up[1] / 2, 1e-9);
		assertEquals(2 * GestureRecognizer.OLLIE_SECTOR_DEG, up[1], 1e-9);
		double[] right = FlickVisualizer.sector(Direction.RIGHT);
		assertEquals(0.0, right[0] + right[1] / 2, 1e-9);
		double[] upLeft = FlickVisualizer.sector(Direction.UP_LEFT);
		assertEquals(135.0, upLeft[0] + upLeft[1] / 2, 1e-9);
		double[] downLeft = FlickVisualizer.sector(Direction.DOWN_LEFT);
		assertEquals(225.0, downLeft[0] + downLeft[1] / 2, 1e-9);
	}

	private static LiveStroke stroke(boolean held, Direction fired, long firedMs, long now)
	{
		return new LiveStroke(held, 0, 0, 0, 0, new float[0], new float[0], new long[0], false, false, 0, 0, fired,
			false, firedMs, 48f, now);
	}

	@Test
	public void visibleWhileHeldOrWhileTheWedgeFades()
	{
		assertTrue(FlickVisualizer.visible(stroke(true, null, 0, 1000)));
		assertTrue(FlickVisualizer.visible(stroke(false, Direction.UP, 900, 1000)));
		assertFalse(FlickVisualizer.visible(stroke(false, Direction.UP, 700, 1000)));
		assertFalse(FlickVisualizer.visible(stroke(false, null, 0, 1000)));
		assertFalse(FlickVisualizer.visible(null));
	}

	@Test
	public void recognizerLiveSnapshotTracksTheStroke()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50);
		LiveStroke wound = r.live(50, FlickVisualizer.TRAIL_MS);
		assertTrue(wound.held);
		assertTrue(wound.wound);
		assertFalse(wound.nollie);
		assertEquals(30f, wound.y - wound.originY, 0f);
		assertEquals(GestureRecognizer.WIND_UP_PX + GestureRecognizer.FLICK_PX, wound.reachPx, 1e-4f);
		r.move(100, 195, 120);
		LiveStroke fired = r.live(400, FlickVisualizer.TRAIL_MS);
		assertEquals(Direction.UP, fired.firedDirection);
		assertEquals(120, fired.firedMs);
		// only the last 300 ms of path: the 120 ms sample, not the 0 and 50 ms ones
		assertEquals(1, fired.trailMs.length);
		r.end();
		assertFalse(r.live(500, FlickVisualizer.TRAIL_MS).held);
	}
}
