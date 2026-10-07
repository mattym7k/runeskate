package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

/** Refusal messages say what to do about them (newcomer review item 5). */
public class SafetyRulesWordingTest
{
	@Test
	public void combatSaysToWaitForTheHealthBar()
	{
		assertEquals("You can't skate while in combat (wait until your health bar disappears).",
			SafetyRules.blockReason(false, false, true, false));
	}

	@Test
	public void pvpRefusalsNameTheSettingToTurnOn()
	{
		assertTrue(SafetyRules.blockReason(true, false, false, false).contains("turn on 'Allow skating in PvP areas'"));
		assertTrue(SafetyRules.blockReason(false, true, false, false).contains("turn on 'Allow skating in PvP areas'"));
		assertTrue(SafetyRules.skaterAreaBlockReason(3100, 3600, false).contains("turn on 'Allow skating in PvP areas'"));
	}

	@Test
	public void minigameWorldsAreNamed()
	{
		assertEquals("You can't skate on a Last Man Standing world.",
			SafetyRules.alwaysBlockedReason(false, false, false, EnumSet.of(WorldType.LAST_MAN_STANDING), 0));
		assertEquals("You can't skate on a PvP Arena world.",
			SafetyRules.alwaysBlockedReason(false, false, false, EnumSet.of(WorldType.PVP_ARENA), 0));
		assertEquals("You can't skate on a Bounty Hunter world.",
			SafetyRules.alwaysBlockedReason(false, false, false, EnumSet.of(WorldType.BOUNTY), 0));
	}
}
