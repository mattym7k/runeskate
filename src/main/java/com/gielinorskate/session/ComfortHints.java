package com.gielinorskate.session;

import com.gielinorskate.Text;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.input.InputController;
import com.gielinorskate.input.KeyboardTricks;
import java.awt.event.KeyEvent;
import java.util.*;
import lombok.Getter;
import net.runelite.api.HitsplatID;

/** Pure timing and wording for the comfort hints: idle-logout warning, loaded-area edge, stumble flash. */
public final class ComfortHints
{
/** The first-run welcome, said once ever (the hidden seenIntro setting remembers it). */
public static String welcome(String toggleKey)
{
return Text.get("ch.welcome", toggleKey);
}

/**
* The manual key's name for the controls card: the configured one when usable, else its stand-in (C with
* keyboard tricks on, Space otherwise; see {@link InputController#effectiveManualKey}).
*/
public static String manualKeyLabel(String configuredName, int code, boolean keyboardTricks)
{
return InputController.isValidManualKey(code, keyboardTricks) ? configuredName : keyboardTricks ? "C" : "Space";
}

/** Why the configured manual key is not used, for chat; null when it is used as set. */
public static String manualKeyNote(String configuredName, int code, boolean keyboardTricks)
{
if (InputController.isValidManualKey(code, keyboardTricks))
return null;
String standIn = manualKeyLabel(configuredName, code, keyboardTricks);
return Text.get("ch.manual." + (keyboardTricks && KeyboardTricks.isTrickKey(code) ? "tricks" : "clash"),
configuredName, standIn);
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
return null;
return Text.get("ch.board." + (keyboardTricks && KeyboardTricks.isTrickKey(code) ? "tricks" : "control"),
configuredName);
}

/**
* The board on/off key set to the same key as other settings ({@code others}: setting name to key code, in
* order): the board key wins, so the others never see that key. One note naming every clashing setting, for
* chat; null when none clash or the board key is unset. Keys match by key code, as the input does.
*/
public static String boardKeyClashNote(String boardKeyName, int boardCode, Map<String, Integer> others)
{
if (boardCode == KeyEvent.VK_UNDEFINED)
return null;
List<String> clashes = new ArrayList<>();
others.forEach((name, code) ->
{
if (code != null && code == boardCode)
clashes.add(name);
});
if (clashes.isEmpty())
return null;
// "a", "a and b", "a, b and c"
int last = clashes.size() - 1;
String names = last == 0 ? clashes.get(0)
: String.join(", ", clashes.subList(0, last)) + " and " + clashes.get(last);
return Text.get("ch.clash", boardKeyName, names);
}

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
out.add(Text.get("ch.padkey", e.getKey(), b.keyName, b.label));
}
return out;
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
@Getter
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
return Text.get("ch.idle", minutes, minutes == 1 ? "" : "s");
}

/** Opacity of the "Edge of the loaded area" hint at {@code tiles} from the edge. */
public static float edgeHintAlpha(float tiles)
{
return !(tiles < EDGE_HINT_TILES) ? 0f : tiles <= EDGE_HINT_FULL_TILES ? 1f
: (EDGE_HINT_TILES - tiles) / (EDGE_HINT_TILES - EDGE_HINT_FULL_TILES);
}

/** True when skating starts this close to the edge of the loaded area (in tiles). */
static boolean needsRoomTip(float tiles)
{
return tiles < ROOM_TIP_TILES;
}

/** Opacity of the "Stumble!" flash {@code since} seconds after the stumble. */
public static float stumbleFlashAlpha(float since)
{
return since < 0f || since >= STUMBLE_HOLD + STUMBLE_FADE ? 0f
: since <= STUMBLE_HOLD ? 1f : 1f - (since - STUMBLE_HOLD) / STUMBLE_FADE;
}

/** Why damage of this hitsplat type ended skating. */
public static String damageMessage(int hitsplatType)
{
return Text.get("ch.dmg." + (hitsplatType == HitsplatID.POISON ? "poison"
: hitsplatType == HitsplatID.VENOM ? "venom" : "hit"));
}
}
