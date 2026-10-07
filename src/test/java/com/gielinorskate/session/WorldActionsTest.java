package com.gielinorskate.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.MenuAction;
import org.junit.Test;

public class WorldActionsTest
{
	@Test
	public void everythingThatTouchesTheGameWorldIsBlocked()
	{
		MenuAction[] world = {
			MenuAction.WALK, MenuAction.SET_HEADING,
			MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION, MenuAction.ITEM_USE_ON_GAME_OBJECT,
			MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, MenuAction.EXAMINE_OBJECT,
			MenuAction.NPC_FIRST_OPTION, MenuAction.ITEM_USE_ON_NPC, MenuAction.WIDGET_TARGET_ON_NPC, MenuAction.EXAMINE_NPC,
			MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_EIGHTH_OPTION, MenuAction.ITEM_USE_ON_PLAYER,
			MenuAction.WIDGET_TARGET_ON_PLAYER, MenuAction.RUNELITE_PLAYER,
			MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.ITEM_USE_ON_GROUND_ITEM, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
			MenuAction.EXAMINE_ITEM_GROUND,
			MenuAction.WORLD_ENTITY_FIRST_OPTION, MenuAction.EXAMINE_WORLD_ENTITY,
		};
		for (MenuAction a : world)
		{
			assertTrue(a.name(), WorldActions.isWorldAction(a));
		}
	}

	@Test
	public void interfaceActionsStillWork()
	{
		MenuAction[] ui = {
			MenuAction.CC_OP, MenuAction.CC_OP_LOW_PRIORITY, MenuAction.WIDGET_TYPE_1, MenuAction.WIDGET_CLOSE,
			MenuAction.WIDGET_CONTINUE, MenuAction.ITEM_FIRST_OPTION, MenuAction.ITEM_USE, MenuAction.ITEM_USE_ON_ITEM,
			MenuAction.WIDGET_TARGET_ON_WIDGET, MenuAction.EXAMINE_ITEM, MenuAction.CANCEL, MenuAction.RUNELITE,
			MenuAction.RUNELITE_OVERLAY,
		};
		for (MenuAction a : ui)
		{
			assertFalse(a.name(), WorldActions.isWorldAction(a));
		}
		assertFalse(WorldActions.isWorldAction(null));
	}
}
