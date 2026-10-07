package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.feedback.HudAnim;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Pop-in scales are snapped so Java2D can reuse its glyph rasters, without visibly changing the animation. */
public class ScoreScaleQuantiseTest
{
	@Test
	public void restingScaleIsExactlyOne()
	{
		assertEquals(Float.floatToRawIntBits(1f), Float.floatToRawIntBits(ScoreOverlay.quantiseScale(1f)));
		assertEquals(Float.floatToRawIntBits(1f), Float.floatToRawIntBits(ScoreOverlay.quantiseScale(HudAnim.popScale(1f))));
	}

	@Test
	public void snappedScaleIsWithinHalfAStep()
	{
		for (float s = 0.5f; s <= 2f; s += 0.0013f)
		{
			float q = ScoreOverlay.quantiseScale(s);
			assertTrue(s + " -> " + q, Math.abs(q - s) <= 0.5f / ScoreOverlay.SCALE_STEPS + 1e-6f);
		}
	}

	@Test
	public void aPopUsesABoundedSetOfScales()
	{
		Set<Float> trick = new HashSet<>();
		Set<Float> callout = new HashSet<>();
		for (int frame = 0; frame < 2000; frame++)
		{
			float age = frame / 10000f;
			trick.add(ScoreOverlay.quantiseScale(HudAnim.popScale(age)));
			callout.add(ScoreOverlay.quantiseScale(HudAnim.calloutScale(age)));
		}
		// 1.35 down past ~0.9 and back to 1: a bounded handful of strikes, not one per frame
		assertTrue("trick scales " + trick.size(), trick.size() <= 25);
		assertTrue("callout scales " + callout.size(), callout.size() <= 25);
		assertTrue(trick.contains(1f));
	}
}
