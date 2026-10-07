package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

public class SafetyRulesMinigameTest
{
	private static final int LUMBRIDGE = SafetyRules.regionId(3222, 3218);

	private static String always(boolean instance, boolean lms, boolean dmmWild, EnumSet<WorldType> types, int region)
	{
		return SafetyRules.alwaysBlockedReason(instance, lms, dmmWild, types, region);
	}

	@Test
	public void regionIdMatchesTheGameFormula()
	{
		// (x >> 6) << 8 | (y >> 6): Lumbridge castle is region 12850
		assertEquals(12850, SafetyRules.regionId(3222, 3218));
	}

	@Test
	public void normalPlaceIsNotAlwaysBlocked()
	{
		assertNull(always(false, false, false, EnumSet.of(WorldType.MEMBERS), LUMBRIDGE));
	}

	@Test
	public void instancesAlwaysBlocked()
	{
		assertNotNull(always(true, false, false, EnumSet.noneOf(WorldType.class), LUMBRIDGE));
	}

	@Test
	public void lastManStandingInGameAlwaysBlocked()
	{
		assertNotNull(always(false, true, false, EnumSet.noneOf(WorldType.class), LUMBRIDGE));
	}

	@Test
	public void deadmanWildernessAlwaysBlocked()
	{
		assertNotNull(always(false, false, true, EnumSet.noneOf(WorldType.class), LUMBRIDGE));
	}

	@Test
	public void pvpMinigameWorldTypesAlwaysBlocked()
	{
		assertNotNull(always(false, false, false, EnumSet.of(WorldType.PVP_ARENA), LUMBRIDGE));
		assertNotNull(always(false, false, false, EnumSet.of(WorldType.LAST_MAN_STANDING), LUMBRIDGE));
		assertNotNull(always(false, false, false, EnumSet.of(WorldType.BOUNTY, WorldType.MEMBERS), LUMBRIDGE));
	}

	@Test
	public void optInPvpWorldTypesAreNotAlwaysBlocked()
	{
		assertNull(always(false, false, false, EnumSet.of(WorldType.PVP), LUMBRIDGE));
		assertNull(always(false, false, false, EnumSet.of(WorldType.HIGH_RISK), LUMBRIDGE));
		assertNull(always(false, false, false, EnumSet.of(WorldType.DEADMAN), LUMBRIDGE));
	}

	@Test
	public void optInPvpWorlds()
	{
		assertTrue(SafetyRules.isOptInPvpWorld(EnumSet.of(WorldType.PVP)));
		assertTrue(SafetyRules.isOptInPvpWorld(EnumSet.of(WorldType.HIGH_RISK, WorldType.MEMBERS)));
		assertTrue(SafetyRules.isOptInPvpWorld(EnumSet.of(WorldType.DEADMAN)));
		assertFalse(SafetyRules.isOptInPvpWorld(EnumSet.of(WorldType.MEMBERS)));
		assertFalse(SafetyRules.isOptInPvpWorld(EnumSet.noneOf(WorldType.class)));
	}

	@Test
	public void castleWarsRegionsAlwaysBlocked()
	{
		assertNotNull(always(false, false, false, EnumSet.noneOf(WorldType.class), 9520));
		assertNotNull(always(false, false, false, EnumSet.noneOf(WorldType.class), 9620));
		// a tile in the Castle Wars arena, not just the raw region number
		assertTrue(SafetyRules.isBlockedMinigameRegion(SafetyRules.regionId(2400, 3100)));
	}

	@Test
	public void soulWarsRegionsAlwaysBlocked()
	{
		for (int r : new int[]{8493, 8748, 8749, 9005})
		{
			assertTrue(SafetyRules.isBlockedMinigameRegion(r));
		}
	}

	@Test
	public void clanWarsRegionsAlwaysBlocked()
	{
		for (int r : new int[]{12621, 12622, 12623, 13130, 13131, 13133, 13134, 13135, 13386, 13387, 13390,
			13641, 13642, 13643, 13644, 13645, 13646, 13647, 13899, 13900, 14155, 14156})
		{
			assertTrue(SafetyRules.isBlockedMinigameRegion(r));
		}
	}

	@Test
	public void pvpArenaAndLastManStandingRegionsAlwaysBlocked()
	{
		for (int r : new int[]{13362, 13363, 13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920,
			14174, 14175, 14176, 14430, 14431, 14432})
		{
			assertTrue(SafetyRules.isBlockedMinigameRegion(r));
		}
	}

	@Test
	public void ordinaryRegionsNotMinigames()
	{
		assertFalse(SafetyRules.isBlockedMinigameRegion(LUMBRIDGE));
		assertFalse(SafetyRules.isBlockedMinigameRegion(SafetyRules.regionId(3165, 3485))); // Grand Exchange
		assertFalse(SafetyRules.isBlockedMinigameRegion(SafetyRules.regionId(2440, 3090))); // Castle Wars lobby
	}

	@Test
	public void skaterRollingIntoAMinigameEndsSkating()
	{
		assertNotNull(SafetyRules.skaterAreaBlockReason(2400, 3100, true));
		assertNotNull(SafetyRules.skaterAreaBlockReason(2400, 3100, false));
	}

	@Test
	public void skaterRollingIntoTheWildernessEndsSkatingUnlessOptedIn()
	{
		assertNotNull(SafetyRules.skaterAreaBlockReason(3100, 3600, false));
		assertNull(SafetyRules.skaterAreaBlockReason(3100, 3600, true));
		assertNotNull(SafetyRules.skaterAreaBlockReason(3100, 10000, false));
	}

	@Test
	public void skaterInOrdinaryPlaceKeepsSkating()
	{
		assertNull(SafetyRules.skaterAreaBlockReason(3222, 3218, false));
		assertNull(SafetyRules.skaterAreaBlockReason(3100, 3500, false)); // Edgeville, south of the ditch
	}

	@Test
	public void pvpAreaVarbitCountsAsAPvpArea()
	{
		// PVP_AREA_CLIENT is 1 throughout the Wilderness and PvP worlds, so it follows the opt-in like them
		assertNotNull(SafetyRules.blockReason(SafetyRules.inPvpArea(false, true), false, false, false));
		assertNull(SafetyRules.blockReason(SafetyRules.inPvpArea(false, true), false, false, true));
		assertTrue(SafetyRules.inPvpArea(true, false));
		assertFalse(SafetyRules.inPvpArea(false, false));
	}
}
