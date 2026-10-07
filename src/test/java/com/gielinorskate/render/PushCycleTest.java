package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PushCycleTest
{
	private static PushCycle.Sample at(float phase)
	{
		PushCycle.Sample s = new PushCycle.Sample();
		PushCycle.sample(phase, s);
		return s;
	}

	@Test
	public void startsAndEndsOnTheBoardWithNoEffect()
	{
		for (float p : new float[]{0f, 1f, -0.5f, 1.5f})
		{
			PushCycle.Sample s = at(p);
			assertEquals(0f, s.back, 1e-5f);
			assertEquals(0f, s.footY, 1e-5f);
			assertEquals(0f, s.side, 1e-5f);
			assertEquals(0f, s.legWeight, 1e-5f);
			assertEquals(0f, s.squash, 1e-5f);
			assertEquals(0f, s.lean, 1e-5f);
		}
	}

	@Test
	public void footLiftsOffTheBoardFirst()
	{
		PushCycle.Sample s = at(PushCycle.LIFT_END);
		assertTrue(s.footY < -5f);
		// off to the toe side, clear of the deck edge
		assertTrue(s.side > 10f);
	}

	@Test
	public void footIsOnTheGroundMidCycleAndTheFrontKneeBends()
	{
		PushCycle.Sample s = at(0.5f);
		assertEquals(PushCycle.GROUND_DEPTH, s.footY, 1e-4f);
		assertEquals(BoardGeometry.BOARD_TOP + BoardPlacement.FOOT_CLEARANCE, PushCycle.GROUND_DEPTH, 0f);
		assertEquals(1f, s.legWeight, 1e-5f);
		assertEquals(PushCycle.PLANT_SQUASH, s.squash, 1e-4f);
		assertTrue(s.lean > 0.05f);
		// beside the deck (half width 13), not through it
		assertTrue(s.side > 13f);
	}

	@Test
	public void plantSweepsBackwardMonotonically()
	{
		float last = -Float.MAX_VALUE;
		for (float p = PushCycle.PLANT_START; p <= PushCycle.PLANT_END + 1e-6f; p += 0.01f)
		{
			PushCycle.Sample s = at(p);
			assertEquals("on the ground at " + p, PushCycle.GROUND_DEPTH, s.footY, 1e-3f);
			assertTrue("sweep goes backward at " + p, s.back >= last - 1e-4f);
			last = s.back;
		}
		assertTrue(last >= 40f && last <= 55f);
		assertTrue(at(PushCycle.PLANT_START).back < 5f);
	}

	@Test
	public void footReturnsForwardAboveTheGround()
	{
		PushCycle.Sample s = at(PushCycle.RECOVER_END);
		assertTrue(s.footY < 0f);
		assertTrue(s.back < at(PushCycle.PLANT_END).back);
	}

	@Test
	public void everyChannelIsContinuous()
	{
		PushCycle.Sample prev = at(0f);
		for (int i = 1; i <= 1000; i++)
		{
			PushCycle.Sample s = at(i / 1000f);
			assertTrue(Math.abs(s.back - prev.back) < 0.5f);
			assertTrue(Math.abs(s.footY - prev.footY) < 0.5f);
			assertTrue(Math.abs(s.side - prev.side) < 0.5f);
			assertTrue(Math.abs(s.legWeight - prev.legWeight) < 0.05f);
			assertTrue(Math.abs(s.squash - prev.squash) < 0.01f);
			assertTrue(Math.abs(s.lean - prev.lean) < 0.01f);
			prev = s;
		}
	}
}
