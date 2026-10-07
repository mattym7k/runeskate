package com.gielinorskate.session;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.WorldType;

/**
 * Where skating is allowed. Two tiers:
 * <ul>
 * <li>Always blocked (no setting changes this): instances, PvP minigames (Last Man Standing, Castle Wars,
 * Soul Wars, Clan Wars, the PvP Arena), the Deadman Wilderness and the PvP minigame world types.</li>
 * <li>Blocked unless "Allow skating in PvP areas" is on: the Wilderness and PvP worlds. Other players are
 * hidden while skating there.</li>
 * </ul>
 * Combat always blocks.
 */
public final class SafetyRules
{
	/** World types that only exist for a PvP minigame; never skate there. */
	private static final Set<WorldType> MINIGAME_WORLD_TYPES =
		Collections.unmodifiableSet(EnumSet.of(WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING, WorldType.BOUNTY));

	/** Open-world PvP world types; covered by the "Allow skating in PvP areas" opt-in. */
	private static final Set<WorldType> OPT_IN_PVP_WORLD_TYPES =
		Collections.unmodifiableSet(EnumSet.of(WorldType.PVP, WorldType.HIGH_RISK, WorldType.DEADMAN));

	/**
	 * Map regions of PvP minigames, region id = (worldX >> 6) << 8 | (worldY >> 6). Source: RuneLite's own
	 * Discord plugin, runelite-client/src/main/java/net/runelite/client/plugins/discord/DiscordGameEventType.java
	 * (MG_CASTLE_WARS, MG_SOUL_WARS, MG_CLAN_WARS, MG_EMIRS_ARENA, MG_LAST_MAN_STANDING_*), fetched 2026-10-04.
	 */
	private static final Set<Integer> MINIGAME_REGIONS = regions(
		// Castle Wars arena and its underground tunnels
		9520, 9620,
		// Soul Wars
		8493, 8748, 8749, 9005,
		// Clan Wars arenas, including the free-for-all portals
		12621, 12622, 12623, 13130, 13131, 13133, 13134, 13135, 13386, 13387, 13390,
		13641, 13642, 13643, 13644, 13645, 13646, 13647, 13899, 13900, 14155, 14156,
		// PvP Arena (Emir's Arena)
		13362, 13363,
		// Last Man Standing maps (also caught by the BR_INGAME varbit)
		13658, 13659, 13660, 13914, 13915, 13916,
		13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);

	private SafetyRules()
	{
	}

	/** Where the PvP opt-in lives, appended to the refusal messages that it would lift. */
	static final String SETTINGS_WHERE = "in the RuneSkate settings (Advanced section).";

	/** "a Last Man Standing world" and so on, for the refusal message on a minigame world. */
	static String worldTypeName(WorldType type)
	{
		switch (type)
		{
			case PVP_ARENA:
				return "a PvP Arena world";
			case LAST_MAN_STANDING:
				return "a Last Man Standing world";
			case BOUNTY:
				return "a Bounty Hunter world";
			default:
				return "this world";
		}
	}

	private static Set<Integer> regions(int... ids)
	{
		Set<Integer> s = new HashSet<>();
		for (int id : ids)
		{
			s.add(id);
		}
		return Collections.unmodifiableSet(s);
	}

	/** The game's region id of a world tile. */
	public static int regionId(int worldX, int worldY)
	{
		return (worldX >> 6) << 8 | (worldY >> 6);
	}

	public static boolean isBlockedMinigameRegion(int regionId)
	{
		return MINIGAME_REGIONS.contains(regionId);
	}

	/** PvP, High Risk and Deadman worlds: skating there needs the PvP-areas opt-in. */
	public static boolean isOptInPvpWorld(Collection<WorldType> types)
	{
		for (WorldType t : types)
		{
			if (OPT_IN_PVP_WORLD_TYPES.contains(t))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * In an open-world PvP area: the Wilderness varbit, or PVP_AREA_CLIENT (1 anywhere the player can be attacked
	 * by other players, e.g. the Wilderness and the dangerous parts of PvP worlds).
	 */
	public static boolean inPvpArea(boolean insideWildernessVarbit, boolean pvpAreaVarbit)
	{
		return insideWildernessVarbit || pvpAreaVarbit;
	}

	/**
	 * Returns why skating can never start here, whatever the settings, or null.
	 *
	 * @param instance the top-level world view is an instance
	 * @param lastManStandingInGame the BR_INGAME varbit is set
	 * @param deadmanWilderness the DEADMAN_INWILDERNESS varbit is set
	 * @param regionId the real character's region (see {@link #regionId})
	 */
	public static String alwaysBlockedReason(boolean instance, boolean lastManStandingInGame,
		boolean deadmanWilderness, Collection<WorldType> worldTypes, int regionId)
	{
		if (instance)
		{
			return "You can't skate in an instance.";
		}
		if (lastManStandingInGame || deadmanWilderness || isBlockedMinigameRegion(regionId))
		{
			return "You can't skate in a PvP minigame.";
		}
		for (WorldType t : worldTypes)
		{
			if (MINIGAME_WORLD_TYPES.contains(t))
			{
				return "You can't skate on " + worldTypeName(t) + ".";
			}
		}
		return null;
	}

	/**
	 * Returns a chat message explaining why skate mode can't start, or null if it can.
	 *
	 * @param inWilderness in an open-world PvP area (see {@link #inPvpArea})
	 * @param allowPvpAreas the player opted in to skating in the Wilderness and on PvP worlds (other players are
	 *     hidden there while skating, see {@link #hideOtherPlayers}); combat always blocks
	 */
	public static String blockReason(boolean inWilderness, boolean pvpWorld, boolean inCombat, boolean allowPvpAreas)
	{
		if (inCombat)
		{
			return "You can't skate while in combat (wait until your health bar disappears).";
		}
		if (inWilderness && !allowPvpAreas)
		{
			return "You can't skate in the Wilderness. To allow it, turn on 'Allow skating in PvP areas' " + SETTINGS_WHERE;
		}
		if (pvpWorld && !allowPvpAreas)
		{
			return "You can't skate on a PvP world. To allow it, turn on 'Allow skating in PvP areas' " + SETTINGS_WHERE;
		}
		return null;
	}

	/**
	 * Checked every frame against the skater's own tile (the real character never moves, the skater does):
	 * returns why skating must end now, or null.
	 */
	public static String skaterAreaBlockReason(int worldX, int worldY, boolean allowPvpAreas)
	{
		if (isBlockedMinigameRegion(regionId(worldX, worldY)))
		{
			return "Skate mode ended: you can't skate into a PvP minigame.";
		}
		if (!allowPvpAreas && isWildernessTile(worldX, worldY))
		{
			return "Skate mode ended: you can't skate into the Wilderness. To allow it, turn on "
				+ "'Allow skating in PvP areas' " + SETTINGS_WHERE;
		}
		return null;
	}

	/**
	 * While skating in a PvP area, other players are not drawn at all, so the free skate camera can never be
	 * used to spot other players.
	 */
	public static boolean hideOtherPlayers(boolean inWilderness, boolean pvpWorld)
	{
		return inWilderness || pvpWorld;
	}

	/**
	 * Approximate Wilderness bounds in world tiles (surface north of the ditch, and the Wilderness dungeons), used
	 * to hide other players when the skater rolls into the Wilderness while the real character stays outside it.
	 */
	public static boolean isWildernessTile(int worldX, int worldY)
	{
		boolean inX = worldX >= 2944 && worldX <= 3391;
		boolean surface = worldY >= 3520 && worldY <= 3967;
		boolean dungeons = worldY >= 9920 && worldY <= 10431;
		return inX && (surface || dungeons);
	}
}
