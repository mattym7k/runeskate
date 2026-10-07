package com.gielinorskate.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SafetyRulesTest
{
	@Test
	public void allowedWhenSafe()
	{
		assertNull(SafetyRules.blockReason(false, false, false, false));
	}

	@Test
	public void blockedInDangerByDefault()
	{
		assertNotNull(SafetyRules.blockReason(true, false, false, false));
		assertNotNull(SafetyRules.blockReason(false, true, false, false));
		assertNotNull(SafetyRules.blockReason(false, false, true, false));
	}

	@Test
	public void pvpAreasAllowedOnlyWhenOptedIn()
	{
		assertNull(SafetyRules.blockReason(true, false, false, true));
		assertNull(SafetyRules.blockReason(false, true, false, true));
		assertNull(SafetyRules.blockReason(true, true, false, true));
	}

	@Test
	public void combatAlwaysBlocks()
	{
		assertNotNull(SafetyRules.blockReason(true, false, true, true));
		assertNotNull(SafetyRules.blockReason(false, true, true, true));
		assertNotNull(SafetyRules.blockReason(false, false, true, true));
	}

	@Test
	public void otherPlayersHiddenOnlyInPvpAreas()
	{
		assertTrue(SafetyRules.hideOtherPlayers(true, false));
		assertTrue(SafetyRules.hideOtherPlayers(false, true));
		assertFalse(SafetyRules.hideOtherPlayers(false, false));
	}

	@Test
	public void wildernessCoordinates()
	{
		assertTrue(SafetyRules.isWildernessTile(3100, 3600));   // surface Wilderness
		assertTrue(SafetyRules.isWildernessTile(3100, 10000));  // Wilderness dungeons
		assertFalse(SafetyRules.isWildernessTile(3100, 3500));  // just south of the ditch (Edgeville)
		assertFalse(SafetyRules.isWildernessTile(3222, 3218));  // Lumbridge
	}
}
