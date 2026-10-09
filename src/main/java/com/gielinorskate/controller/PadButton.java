package com.gielinorskate.controller;

import com.gielinorskate.Text;
import java.awt.event.KeyEvent;
import java.util.stream.Stream;

/**
* A controller button and the pad key the universal AntiMicroX profile sends for it. The keys are ones keyboards
* don't have or games rarely use (F13 to F24, and the navigation block), the same on every keyboard layout, so the
* plugin can tell a pad button from a keyboard key. The profile never changes between presets: the plugin turns the
* pad key into an action through the selected {@link PadPreset}.
*/
public enum PadButton
{
A, B, X, Y, LB, RB, LT, RT, BACK, START, L3, R3,
DPAD_UP(KeyEvent.VK_INSERT),
DPAD_DOWN(KeyEvent.VK_DELETE),
DPAD_LEFT(KeyEvent.VK_HOME),
DPAD_RIGHT(KeyEvent.VK_END);

/** The button's name, as players know it. */
public final String label;
/** Its one-character id in a layout code ({@link LayoutCode}); never changes once released. */
public final char id;
/** The Java key code the pad key arrives as. */
public final int keyCode;
/** The pad key's name ("F13", "Insert"). */
public final String keyName;

/** A button sent as function key F13 to F24 (its key name in text/overlay.properties). */
PadButton()
{
this(0);
}

/** Its id, key name and label, from text/overlay.properties ("pad.button." + name). */
PadButton(int keyCode)
{
String[] f = Text.get("pad.button." + name()).split(",", 3);
id = f[0].charAt(0);
keyName = f[1];
label = f[2];
this.keyCode = keyCode != 0 ? keyCode : KeyEvent.VK_F13 + Integer.parseInt(keyName.substring(1)) - 13;
}

/** The button whose pad key is {@code keyCode}, or null for any other key. */
public static PadButton forKey(int keyCode)
{
return Stream.of(values()).filter(b -> b.keyCode == keyCode).findFirst().orElse(null);
}

/** The button with layout-code id {@code id}, or null. */
public static PadButton forId(char id)
{
return Stream.of(values()).filter(b -> b.id == id).findFirst().orElse(null);
}

@Override
public String toString()
{
return label;
}
}
