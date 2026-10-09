package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;

import java.util.*;
import net.runelite.api.WorldType;
import org.junit.Test;

/** The refusals looked up by world type and area keep their wording. */
public class SafetyRulesTextTest
{
	private static String onWorld(WorldType type)
	{
		return SafetyRules.alwaysBlockedReason(false, false, false, EnumSet.of(type), 0);
	}

	@Test
	public void minigameWorldsAreNamed()
	{
		assertEquals("You can't skate on a PvP Arena world.", onWorld(WorldType.PVP_ARENA));
		assertEquals("You can't skate on a Last Man Standing world.", onWorld(WorldType.LAST_MAN_STANDING));
		assertEquals("You can't skate on a Bounty Hunter world.", onWorld(WorldType.BOUNTY));
		assertEquals("You can't skate in an instance.",
			SafetyRules.alwaysBlockedReason(true, false, false, EnumSet.noneOf(WorldType.class), 0));
		assertEquals("You can't skate in a PvP minigame.",
			SafetyRules.alwaysBlockedReason(false, false, false, EnumSet.noneOf(WorldType.class), 9520));
	}

	@Test
	public void pvpAreasSayWhereTheSettingIs()
	{
		String where = ". To allow it, turn on 'Allow skating in PvP areas' in the RuneSkate settings (Advanced section).";
		assertEquals("You can't skate in the Wilderness" + where, SafetyRules.blockReason(true, false, false, false));
		assertEquals("You can't skate on a PvP world" + where, SafetyRules.blockReason(false, true, false, false));
		assertEquals("Skate mode ended: you can't skate into the Wilderness" + where,
			SafetyRules.skaterAreaBlockReason(3100, 3600, false));
		assertEquals("Skate mode ended: you can't skate into a PvP minigame.",
			SafetyRules.skaterAreaBlockReason(13362 >> 8 << 6, (13362 & 255) << 6, false));
	}

	@Test
	public void everyMinigameRegionIsBlocked()
	{
		for (int region : new int[]{9520, 9620, 8493, 9005, 12621, 14156, 13362, 13363, 13658, 14432})
			assertEquals(true, SafetyRules.isBlockedMinigameRegion(region));
		assertEquals(false, SafetyRules.isBlockedMinigameRegion(12850));
	}
}
