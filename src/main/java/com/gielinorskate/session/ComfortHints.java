package com.gielinorskate.session;

import com.gielinorskate.controller.PadButton;
import com.gielinorskate.input.InputController;
import com.gielinorskate.input.KeyboardTricks;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.HitsplatID;

/** Pure timing and wording for the comfort hints: idle-logout warning, loaded-area edge, stumble flash. */
public final class ComfortHints
{
	/** Said once per session on the first plain left click: clicks never reach the world while skating. */
	public static final String CLICK_HINT = "Clicks are paused while skating: use W to push and A/D to steer. Esc to stop.";

	/** The first-run welcome, said once ever (the hidden seenIntro setting remembers it). */
	public static String welcome(String toggleKey)
	{
		return "RuneSkate installed: stand still and press " + toggleKey + " to skate (or use the skateboard "
			+ "button in the sidebar).";
	}

	/**
	 * The manual key's name for the controls card: the configured one when usable, else its stand-in (C with
	 * keyboard tricks on, Space otherwise; see {@link InputController#effectiveManualKey}).
	 */
	public static String manualKeyLabel(String configuredName, int code, boolean keyboardTricks)
	{
		if (InputController.isValidManualKey(code, keyboardTricks))
		{
			return configuredName;
		}
		return keyboardTricks ? "C" : "Space";
	}

	/** Why the configured manual key is not used, for chat; null when it is used as set. */
	public static String manualKeyNote(String configuredName, int code, boolean keyboardTricks)
	{
		if (InputController.isValidManualKey(code, keyboardTricks))
		{
			return null;
		}
		String standIn = manualKeyLabel(configuredName, code, keyboardTricks);
		if (keyboardTricks && KeyboardTricks.isTrickKey(code))
		{
			return "Keyboard tricks use " + configuredName + ", so your wheelie (manual) key is " + standIn
				+ " while skating.";
		}
		return "Wheelie (manual) key " + configuredName + " clashes with a skate control or is not a single key - using "
			+ standIn + " instead.";
	}

	/** The board on/off key's name in use: the configured one, or F when it is not usable (see {@link InputController#effectiveBoardKey}). */
	public static String boardKeyLabel(String configuredName, int code, boolean keyboardTricks)
	{
		return InputController.effectiveBoardKey(code, keyboardTricks) == code ? configuredName : "F";
	}

	/** Why the configured board key is not used, for chat; null when it is used as set (or unset). */
	public static String boardKeyNote(String configuredName, int code, boolean keyboardTricks)
	{
		if (InputController.effectiveBoardKey(code, keyboardTricks) == code)
		{
			return null;
		}
		String why = keyboardTricks && KeyboardTricks.isTrickKey(code) ? "Keyboard tricks use " + configuredName
			: "Your Board on/off key (" + configuredName + ") is a skate control";
		return why + ", so F steps on and off the board instead. Pick another key in the RuneSkate settings.";
	}

	/**
	 * The board on/off key set to the same key as other settings ({@code others}: setting name to key code, in
	 * order): the board key wins, so the others never see that key. One note naming every clashing setting, for
	 * chat; null when none clash or the board key is unset. Keys match by key code, as the input does.
	 */
	public static String boardKeyClashNote(String boardKeyName, int boardCode, Map<String, Integer> others)
	{
		if (boardCode == KeyEvent.VK_UNDEFINED)
		{
			return null;
		}
		List<String> clashes = new ArrayList<>();
		for (Map.Entry<String, Integer> e : others.entrySet())
		{
			if (e.getValue() != null && e.getValue() == boardCode)
			{
				clashes.add(e.getKey());
			}
		}
		if (clashes.isEmpty())
		{
			return null;
		}
		String names = joinNames(clashes);
		return "Your Board on/off key (" + boardKeyName + ") is also your " + names + ": " + boardKeyName
			+ " only steps on and off the board while skating. Change one of them in the RuneSkate settings.";
	}

	/**
	 * The AntiMicroX profile's version: a Controller mode player who was told about an older one is told once to
	 * load the new one. 2: the universal profile (every button its own pad key) of controller presets.
	 */
	public static final int CONTROLLER_PROFILE_VERSION = 2;

	public static final String NEW_CONTROLLER_PROFILE = "RuneSkate has a new controller profile: every button now "
		+ "sends its own key, so load the new RuneSkate.amgp in AntiMicroX (side panel: Controller setup, Save "
		+ "RuneSkate profile). Then pick your Controller preset in the settings.";

	/** The first time Controller mode is on: where to set the controller up. */
	public static final String CONTROLLER_SETUP = "Controller mode is on. To set up your controller, open the RuneSkate "
		+ "side panel and click Controller setup: get AntiMicroX, save the RuneSkate profile and load it, then test "
		+ "your pad. Pick a Controller preset in the settings.";

	/** Custom is the Controller preset but its saved layout can't be read. */
	public static final String BROKEN_CUSTOM_LAYOUT = "Your Custom controller layout can't be read, so the pad plays "
		+ "as Skate 3. Set it again in the side panel (Customise controller).";

	/**
	 * In Controller mode, a key setting on a pad key wins over the pad button that sends it (see
	 * {@link InputController#isPadKeyFree}), for chat: one note per such setting. {@code taken}: the key settings
	 * in use, name to key code, in order.
	 */
	public static List<String> padKeyNotes(Map<String, Integer> taken)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : taken.entrySet())
		{
			PadButton b = e.getValue() == null ? null : PadButton.forKey(e.getValue());
			if (b != null)
			{
				out.add("Your " + e.getKey() + " (" + b.keyName + ") is the key the controller's " + b.label
					+ " button sends, so that button does nothing in Controller mode. Pick another key in the "
					+ "RuneSkate settings.");
			}
		}
		return out;
	}

	private static String joinNames(List<String> names)
	{
		return names.size() == 1 ? names.get(0)
			: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	/** Warn this many client ticks (20 ms) before the idle logout: one minute. */
	static final int IDLE_WARN_BEFORE_TICKS = 3000;
	private static final int TICKS_PER_MINUTE = 3000;
	/** The edge hint shows within EDGE_HINT_TILES of the loaded area's edge, fully from EDGE_HINT_FULL_TILES. */
	static final float EDGE_HINT_TILES = 6f;
	static final float EDGE_HINT_FULL_TILES = 3f;
	/** Starting closer than this to the edge suggests a more central spot. */
	static final float ROOM_TIP_TILES = 20f;
	/** "Stumble!" shows fully for STUMBLE_HOLD seconds, then fades over STUMBLE_FADE. */
	static final float STUMBLE_HOLD = 0.5f;
	static final float STUMBLE_FADE = 0.3f;

	private ComfortHints()
	{
	}

	/**
	 * Watches the game's idle time. Skate keys and right-drags are consumed, so the game does not count them as
	 * activity; this only warns, it never keeps the player logged in.
	 */
	public static final class IdleWarning
	{
		private boolean warning;

		/**
		 * @param idleTicks client ticks since the last input the game saw (keyboard and mouse, whichever is less)
		 * @param timeoutTicks the client's idle logout timeout in client ticks
		 * @return true once, when the idle time first comes within a minute of the logout
		 */
		public boolean update(int idleTicks, int timeoutTicks)
		{
			boolean near = idleTicks > timeoutTicks - IDLE_WARN_BEFORE_TICKS;
			boolean crossed = near && !warning;
			warning = near;
			return crossed;
		}

		public boolean isWarning()
		{
			return warning;
		}

		public void reset()
		{
			warning = false;
		}
	}

	/** Whole minutes of idle time. */
	static int idleMinutes(int idleTicks)
	{
		return idleTicks / TICKS_PER_MINUTE;
	}

	/** The chat and HUD wording of the idle warning. */
	static String idleMessage(int idleTicks)
	{
		int minutes = idleMinutes(idleTicks);
		return "The game hasn't seen input for " + minutes + (minutes == 1 ? " minute" : " minutes")
			+ " and will log you out soon. Skate keys don't count: move the mouse to stay logged in.";
	}

	/** Opacity of the "Edge of the loaded area" hint at {@code tiles} from the edge. */
	public static float edgeHintAlpha(float tiles)
	{
		if (!(tiles < EDGE_HINT_TILES))
		{
			return 0f;
		}
		if (tiles <= EDGE_HINT_FULL_TILES)
		{
			return 1f;
		}
		return (EDGE_HINT_TILES - tiles) / (EDGE_HINT_TILES - EDGE_HINT_FULL_TILES);
	}

	/** True when skating starts this close to the edge of the loaded area (in tiles). */
	static boolean needsRoomTip(float tiles)
	{
		return tiles < ROOM_TIP_TILES;
	}

	/** Opacity of the "Stumble!" flash {@code since} seconds after the stumble. */
	public static float stumbleFlashAlpha(float since)
	{
		if (since < 0f || since >= STUMBLE_HOLD + STUMBLE_FADE)
		{
			return 0f;
		}
		return since <= STUMBLE_HOLD ? 1f : 1f - (since - STUMBLE_HOLD) / STUMBLE_FADE;
	}

	/** Why damage of this hitsplat type ended skating. */
	public static String damageMessage(int hitsplatType)
	{
		switch (hitsplatType)
		{
			case HitsplatID.POISON:
				return "Poison damage knocks you off your board. Cure the poison to skate without interruptions.";
			case HitsplatID.VENOM:
				return "Venom damage knocks you off your board. Cure the venom to skate without interruptions.";
			default:
				return "You took damage, so you hop off your board.";
		}
	}
}
