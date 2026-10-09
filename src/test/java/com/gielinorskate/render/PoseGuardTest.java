package com.gielinorskate.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PoseGuardTest
{
	@Test
	public void oneMissingFrameIsTolerated()
	{
		PoseGuard g = new PoseGuard();
		assertFalse(g.frame(false));
		assertFalse(g.frame(true));
		assertFalse(g.frame(false));
	}

	@Test
	public void revertsAfterSeveralFramesWithoutAModel()
	{
		PoseGuard g = new PoseGuard();
		boolean revert = false;
		for (int i = 0; i < Tuning.MISSING_FRAMES_BEFORE_REVERT; i++)
		{
			revert = g.frame(false);
		}
		assertTrue(revert);
		// counter restarts after a revert
		assertFalse(g.frame(false));
	}
}
