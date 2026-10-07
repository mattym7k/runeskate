package com.gielinorskate.session;

import net.runelite.api.MenuAction;

/**
 * Menu actions that act on the game world (walking, objects, NPCs, players, ground items, world entities). While
 * skating the camera is detached from the player, and Plugin Hub rules forbid interacting with the world from a
 * detached camera, so these are cancelled; interface actions (inventory, chat, tabs, logout) still work.
 */
public final class WorldActions
{
	private WorldActions()
	{
	}

	public static boolean isWorldAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}
		switch (action)
		{
			case WALK:
			case SET_HEADING:
			case EXAMINE_OBJECT:
			case EXAMINE_NPC:
			case EXAMINE_ITEM_GROUND:
			case EXAMINE_WORLD_ENTITY:
			case RUNELITE_PLAYER:
				return true;
			default:
				break;
		}
		String n = action.name();
		return n.contains("GAME_OBJECT") || n.contains("NPC") || n.startsWith("PLAYER_") || n.endsWith("_ON_PLAYER")
			|| n.contains("GROUND_ITEM") || n.startsWith("WORLD_ENTITY");
	}
}
