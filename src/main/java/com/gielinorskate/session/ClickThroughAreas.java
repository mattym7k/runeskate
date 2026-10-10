package com.gielinorskate.session;

import java.awt.Rectangle;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InterfaceID.ToplevelOsrsStretch;
import net.runelite.api.gameval.InterfaceID.ToplevelPreEoc;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * The canvas areas where clicks pass through to the game while skating: the side panel (its tab stones and the
 * open panel) and the chatbox. World clicks stay blocked (no world interaction from the detached camera), so a
 * transparent resizable chatbox whose clicks fall through to the world is left out apart from its tab row.
 * Client thread only.
 */
final class ClickThroughAreas
{
	/** Fixed mode: tab stones, the panel area and the chatbox. */
	private static final int[] FIXED = {
		InterfaceID.Toplevel.SIDE_TOP, InterfaceID.Toplevel.SIDE_BOTTOM, InterfaceID.Toplevel.SIDE,
	};

	/** Resizable classic: tab stones and each tab's panel (only the open one is not hidden). */
	private static final int[] STRETCH = {
		ToplevelOsrsStretch.SIDE_TOP, ToplevelOsrsStretch.SIDE_BOTTOM,
		ToplevelOsrsStretch.SIDE0, ToplevelOsrsStretch.SIDE1,
		ToplevelOsrsStretch.SIDE2, ToplevelOsrsStretch.SIDE3,
		ToplevelOsrsStretch.SIDE4, ToplevelOsrsStretch.SIDE5,
		ToplevelOsrsStretch.SIDE6, ToplevelOsrsStretch.SIDE7,
		ToplevelOsrsStretch.SIDE8, ToplevelOsrsStretch.SIDE9,
		ToplevelOsrsStretch.SIDE10, ToplevelOsrsStretch.SIDE11,
		ToplevelOsrsStretch.SIDE12, ToplevelOsrsStretch.SIDE13,
	};

	/** Resizable modern: the stone bar and each tab's panel (only the open one is not hidden). */
	private static final int[] PRE_EOC = {
		ToplevelPreEoc.SIDE_STATIC, ToplevelPreEoc.SIDE_MOVABLE,
		ToplevelPreEoc.SIDE0, ToplevelPreEoc.SIDE1,
		ToplevelPreEoc.SIDE2, ToplevelPreEoc.SIDE3,
		ToplevelPreEoc.SIDE4, ToplevelPreEoc.SIDE5,
		ToplevelPreEoc.SIDE6, ToplevelPreEoc.SIDE7,
		ToplevelPreEoc.SIDE8, ToplevelPreEoc.SIDE9,
		ToplevelPreEoc.SIDE10, ToplevelPreEoc.SIDE11,
		ToplevelPreEoc.SIDE12, ToplevelPreEoc.SIDE13,
	};

	private ClickThroughAreas()
	{
	}

	static List<Rectangle> collect(Client client)
	{
		List<Rectangle> out = new ArrayList<>();
		boolean resized = client.isResized();
		add(client, out, resized ? STRETCH : FIXED);
		if (resized)
			add(client, out, PRE_EOC);
		// the chat tab buttons are always solid; the message area only when its clicks don't fall through
		add(client, out, InterfaceID.Chatbox.CONTROLS);
		boolean transparent = resized && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
		if (!transparent || client.getVarbitValue(VarbitID.TRANSPARENT_CHATBOX_BLOCKCLICK) == 1)
			add(client, out, InterfaceID.Chatbox.CHATAREA);
		return out.isEmpty() ? Collections.emptyList() : out;
	}

	/** Adds the bounds of each of these widgets that is shown. */
	private static void add(Client client, List<Rectangle> out, int... ids)
	{
		for (int id : ids)
		{
			Widget w = client.getWidget(id);
			Rectangle r = w == null || w.isHidden() ? null : w.getBounds();
			if (r != null && r.width > 0 && r.height > 0)
				out.add(r);
		}
	}
}
