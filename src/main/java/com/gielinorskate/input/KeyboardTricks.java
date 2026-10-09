package com.gielinorskate.input;

import static com.gielinorskate.tricks.Gesture.Direction.*;
import static java.awt.event.KeyEvent.*;

import com.gielinorskate.tricks.*;
import com.gielinorskate.tricks.Gesture.Direction;

/**
* The "Keyboard" trick controls: each key stands for one mouse flick, turned into the same {@link Gesture} the
* recognizer would produce, so physics and {@link TrickCatalog} treat both alike.
*
* <ul>
* <li>Space: ollie. 1: kickflip, 2: heelflip, 3: pop shove-it, 4: FS pop shove-it.</li>
* <li>5 / 6: varial kickflip / heelflip, 7 / 8: 360 flip / laser flip, 9 / 0: 360 / FS 360 shove-it.</li>
* <li>Shift held: the hard version (impossible, hardflip, inward heelflip, bigspins), as with a Shift flick.</li>
* <li>Alt held when the key goes down: the nollie version (the flick mirrored top to bottom).</li>
* <li>The same key again mid-flip: double (then triple) kickflip / heelflip, as a mouse re-flick.</li>
* </ul>
* The number-pad digits work too. Pure.
*/
public final class KeyboardTricks
{
/** Path turn, in degrees, standing in for a curved flick: a varial (60-150) and a full 360 (150+). */
static final float VARIAL_TURN = 90f;
static final float FULL_TURN = 180f;
/** The regular flick of each digit key 0-9: its direction and path turn. */
private static final Direction[] DIGIT_FLICK = {RIGHT, UP_LEFT, UP_RIGHT, LEFT, RIGHT, UP_LEFT, UP_RIGHT, UP_LEFT,
UP_RIGHT, LEFT};
private static final float[] DIGIT_TURN = {FULL_TURN, 0f, 0f, 0f, 0f, VARIAL_TURN, VARIAL_TURN, FULL_TURN,
FULL_TURN, FULL_TURN};
/** The trick keys in the order the side panel names them. */
private static final int[] KEYS = {VK_SPACE, VK_1, VK_2, VK_3, VK_4, VK_5, VK_6, VK_7, VK_8, VK_9, VK_0};

private KeyboardTricks()
{
}

/** True when {@code code} does a trick in keyboard mode (so it cannot be the wheelie key then). */
public static boolean isTrickKey(int code)
{
return gestureFor(code, false, false) != null;
}

/**
* The gesture for a trick key, or null when {@code code} is not one.
*
* @param nollie Alt was held as the key went down
* @param modified Shift is held as the trick fires
*/
public static Gesture gestureFor(int code, boolean nollie, boolean modified)
{
int digit = code >= VK_0 && code <= VK_9 ? code - VK_0
: code >= VK_NUMPAD0 && code <= VK_NUMPAD9 ? code - VK_NUMPAD0 : -1;
if (digit < 0 && code != VK_SPACE)
return null;
Direction d = digit < 0 ? UP : DIGIT_FLICK[digit];
return new Gesture(nollie ? flipVertically(d) : d, nollie, digit < 0 ? 0f : DIGIT_TURN[digit], modified);
}

/** A nollie flick goes the other way vertically: up becomes down; left and right stay. */
static Direction flipVertically(Direction d)
{
// the directions go round in 45 degree steps from UP, so this reflects the index about LEFT / RIGHT
return Direction.values()[(12 - d.ordinal()) % 8];
}

/**
* The key label for a trick in keyboard mode ("1", "Shift+1", "Alt+Space", "1, 1" for a double), or null when no
* key does it (grabs, grinds, ...). Used by the side panel's trick list.
*/
public static String keyFor(Trick trick)
{
for (int code : KEYS)
{
String key = code == VK_SPACE ? "Space" : String.valueOf(code - VK_0);
// plain, Alt, Shift, Shift+Alt
for (int mods = 0; mods < 4; mods++)
{
boolean alt = (mods & 1) != 0;
boolean shift = mods >= 2;
if (TrickCatalog.forGesture(gestureFor(code, alt, shift)) == trick)
return (shift ? "Shift+" : "") + (alt ? "Alt+" : "") + key;
}
}
// a double or triple: the same key again mid-flip
for (Trick from : Trick.values())
{
for (int code : KEYS)
{
if (TrickCatalog.upgrade(from, gestureFor(code, false, false)) == trick)
{
String first = keyFor(from);
return first == null ? null : first + ", " + (code == VK_SPACE ? "Space" : code - VK_0);
}
}
}
return null;
}

/** The mirror image (left and right swapped) of a mouse flick, for the "Mirror flicks" setting. */
public static Gesture mirror(Gesture g)
{
return new Gesture(mirror(g.direction), g.nollie, g.turnDegrees, g.modified);
}

static Direction mirror(Direction d)
{
// the directions go round in 45 degree steps from UP, so this reflects the index about UP / DOWN
return Direction.values()[(8 - d.ordinal()) % 8];
}
}
