package com.gielinorskate.controller;

import java.awt.event.KeyEvent;

/**
 * A controller button and the pad key the universal AntiMicroX profile sends for it. The keys are ones keyboards
 * don't have or games rarely use (F13 to F24, and the navigation block), the same on every keyboard layout, so the
 * plugin can tell a pad button from a keyboard key. The profile never changes between presets: the plugin turns the
 * pad key into an action through the selected {@link PadPreset}.
 */
public enum PadButton
{
	A("A", 'a', KeyEvent.VK_F13, "F13", PadButton.QT_F1 + 12),
	B("B", 'b', KeyEvent.VK_F14, "F14", PadButton.QT_F1 + 13),
	X("X", 'x', KeyEvent.VK_F15, "F15", PadButton.QT_F1 + 14),
	Y("Y", 'y', KeyEvent.VK_F16, "F16", PadButton.QT_F1 + 15),
	LB("LB", 'l', KeyEvent.VK_F17, "F17", PadButton.QT_F1 + 16),
	RB("RB", 'r', KeyEvent.VK_F18, "F18", PadButton.QT_F1 + 17),
	LT("LT", 'L', KeyEvent.VK_F19, "F19", PadButton.QT_F1 + 18),
	RT("RT", 'R', KeyEvent.VK_F20, "F20", PadButton.QT_F1 + 19),
	BACK("Back", 'k', KeyEvent.VK_F21, "F21", PadButton.QT_F1 + 20),
	START("Start", 's', KeyEvent.VK_F22, "F22", PadButton.QT_F1 + 21),
	L3("L3", '3', KeyEvent.VK_F23, "F23", PadButton.QT_F1 + 22),
	R3("R3", '4', KeyEvent.VK_F24, "F24", PadButton.QT_F1 + 23),
	DPAD_UP("D-pad up", 'u', KeyEvent.VK_INSERT, "Insert", 0x1000006),
	DPAD_DOWN("D-pad down", 'd', KeyEvent.VK_DELETE, "Delete", 0x1000007),
	DPAD_LEFT("D-pad left", 'h', KeyEvent.VK_HOME, "Home", 0x1000010),
	DPAD_RIGHT("D-pad right", 'e', KeyEvent.VK_END, "End", 0x1000011);

	/** Qt::Key_F1; AntiMicroX stores keys as Qt key codes, and F2 to F35 follow F1. */
	static final int QT_F1 = 0x1000030;

	/** The button's name, as players know it. */
	public final String label;
	/** Its one-character id in a layout code ({@link LayoutCode}); never changes once released. */
	public final char id;
	/** The Java key code the pad key arrives as. */
	public final int keyCode;
	/** The pad key's name ("F13", "Insert"). */
	public final String keyName;
	/** The Qt key code AntiMicroX stores for the pad key (it sends it on Windows as the matching VK_ code). */
	public final int qtCode;

	PadButton(String label, char id, int keyCode, String keyName, int qtCode)
	{
		this.label = label;
		this.id = id;
		this.keyCode = keyCode;
		this.keyName = keyName;
		this.qtCode = qtCode;
	}

	/** The button whose pad key is {@code keyCode}, or null for any other key. */
	public static PadButton forKey(int keyCode)
	{
		for (PadButton b : values())
		{
			if (b.keyCode == keyCode)
			{
				return b;
			}
		}
		return null;
	}

	/** The button with layout-code id {@code id}, or null. */
	public static PadButton forId(char id)
	{
		for (PadButton b : values())
		{
			if (b.id == id)
			{
				return b;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
