package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkatePoseCommandTest
{
	@Test
	public void noArgsListsCurrentValues()
	{
		StancePoses poses = new StancePoses();
		String result = SkatePoseCommand.apply(poses, new String[0]);
		assertTrue(result.contains("stance=" + poses.stance));
		assertTrue(result.contains("crouch=" + poses.crouch));
		assertTrue(result.contains("push=" + poses.push));
		assertTrue(result.contains("jump=" + poses.jump));
		assertTrue(result.contains("bail=" + poses.bail));
		assertTrue(result.contains("grind=" + poses.grind));
		assertTrue(result.contains("manual=" + poses.manual));
	}

	@Test
	public void setsAValidSlot()
	{
		StancePoses poses = new StancePoses();
		String result = SkatePoseCommand.apply(poses, new String[]{"stance", "1234"});
		assertEquals(1234, poses.stance);
		assertTrue(result.contains("stance"));
		assertTrue(result.contains("1234"));
	}

	@Test
	public void slotNameIsCaseInsensitive()
	{
		StancePoses poses = new StancePoses();
		SkatePoseCommand.apply(poses, new String[]{"CROUCH", "42"});
		assertEquals(42, poses.crouch);
	}

	@Test
	public void everySlotCanBeSet()
	{
		StancePoses poses = new StancePoses();
		SkatePoseCommand.apply(poses, new String[]{"push", "1"});
		SkatePoseCommand.apply(poses, new String[]{"jump", "2"});
		SkatePoseCommand.apply(poses, new String[]{"bail", "3"});
		SkatePoseCommand.apply(poses, new String[]{"grind", "4"});
		SkatePoseCommand.apply(poses, new String[]{"manual", "5"});
		assertEquals(1, poses.push);
		assertEquals(2, poses.jump);
		assertEquals(3, poses.bail);
		assertEquals(4, poses.grind);
		assertEquals(5, poses.manual);
	}

	@Test
	public void unknownSlotReturnsUsageAndDoesNotThrow()
	{
		StancePoses poses = new StancePoses();
		String result = SkatePoseCommand.apply(poses, new String[]{"nonsense", "1"});
		assertEquals(SkatePoseCommand.USAGE, result);
	}

	@Test
	public void nonNumericIdReturnsUsageAndDoesNotThrow()
	{
		StancePoses poses = new StancePoses();
		String result = SkatePoseCommand.apply(poses, new String[]{"stance", "not-a-number"});
		assertEquals(SkatePoseCommand.USAGE, result);
	}

	@Test
	public void wrongArgumentCountReturnsUsage()
	{
		StancePoses poses = new StancePoses();
		assertEquals(SkatePoseCommand.USAGE, SkatePoseCommand.apply(poses, new String[]{"stance"}));
		assertEquals(SkatePoseCommand.USAGE,
			SkatePoseCommand.apply(poses, new String[]{"stance", "1", "extra"}));
	}

	@Test
	public void idsBelowMinusOneAreRejected()
	{
		StancePoses poses = new StancePoses();
		int before = poses.stance;
		assertEquals(SkatePoseCommand.USAGE, SkatePoseCommand.apply(poses, new String[]{"stance", "-2"}));
		assertEquals(before, poses.stance);
		SkatePoseCommand.apply(poses, new String[]{"push", "-1"});
		assertEquals(-1, poses.push);
	}

	@Test
	public void idsTheClientCannotLoadAreRejected()
	{
		StancePoses poses = new StancePoses();
		int before = poses.jump;
		String result = SkatePoseCommand.apply(poses, new String[]{"jump", "99999"}, id -> id != 99999);
		assertEquals(before, poses.jump);
		assertTrue(result, result.contains("99999"));
		SkatePoseCommand.apply(poses, new String[]{"jump", "808"}, id -> id != 99999);
		assertEquals(808, poses.jump);
	}

	@Test
	public void slotNameIgnoresTheDefaultLocale()
	{
		java.util.Locale saved = java.util.Locale.getDefault();
		try
		{
			java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
			StancePoses poses = new StancePoses();
			SkatePoseCommand.apply(poses, new String[]{"BAIL", "7"}); // Turkish "I" lowercases to a dotless i
			assertEquals(7, poses.bail);
		}
		finally
		{
			java.util.Locale.setDefault(saved);
		}
	}

	@Test
	public void theKnockdownAndGetUpSlotsCanBeSetOrTurnedOff()
	{
		StancePoses poses = new StancePoses();
		assertEquals(net.runelite.api.gameval.AnimationID.HUMAN_KNOCKDOWN_LOOP_NODELAY, poses.knockdown);
		assertEquals(net.runelite.api.gameval.AnimationID.HUMAN_GETUP, poses.getUp);
		assertEquals("skatepose: knockdown set to 7211", SkatePoseCommand.apply(poses, new String[]{"knockdown", "7211"}));
		assertEquals(7211, poses.knockdown);
		SkatePoseCommand.apply(poses, new String[]{"getup", "-1"});
		assertEquals(StancePoses.NONE, poses.getUp);
		String list = SkatePoseCommand.apply(poses, new String[0]);
		assertTrue(list, list.contains("knockdown=7211") && list.contains("getup=-1"));
	}
}
