package com.gielinorskate.session;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
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
		InterfaceID.ToplevelOsrsStretch.SIDE_TOP, InterfaceID.ToplevelOsrsStretch.SIDE_BOTTOM,
		InterfaceID.ToplevelOsrsStretch.SIDE0, InterfaceID.ToplevelOsrsStretch.SIDE1,
		InterfaceID.ToplevelOsrsStretch.SIDE2, InterfaceID.ToplevelOsrsStretch.SIDE3,
		InterfaceID.ToplevelOsrsStretch.SIDE4, InterfaceID.ToplevelOsrsStretch.SIDE5,
		InterfaceID.ToplevelOsrsStretch.SIDE6, InterfaceID.ToplevelOsrsStretch.SIDE7,
		InterfaceID.ToplevelOsrsStretch.SIDE8, InterfaceID.ToplevelOsrsStretch.SIDE9,
		InterfaceID.ToplevelOsrsStretch.SIDE10, InterfaceID.ToplevelOsrsStretch.SIDE11,
		InterfaceID.ToplevelOsrsStretch.SIDE12, InterfaceID.ToplevelOsrsStretch.SIDE13,
	};

	/** Resizable modern: the stone bar and each tab's panel (only the open one is not hidden). */
	private static final int[] PRE_EOC = {
		InterfaceID.ToplevelPreEoc.SIDE_STATIC, InterfaceID.ToplevelPreEoc.SIDE_MOVABLE,
		InterfaceID.ToplevelPreEoc.SIDE0, InterfaceID.ToplevelPreEoc.SIDE1,
		InterfaceID.ToplevelPreEoc.SIDE2, InterfaceID.ToplevelPreEoc.SIDE3,
		InterfaceID.ToplevelPreEoc.SIDE4, InterfaceID.ToplevelPreEoc.SIDE5,
		InterfaceID.ToplevelPreEoc.SIDE6, InterfaceID.ToplevelPreEoc.SIDE7,
		InterfaceID.ToplevelPreEoc.SIDE8, InterfaceID.ToplevelPreEoc.SIDE9,
		InterfaceID.ToplevelPreEoc.SIDE10, InterfaceID.ToplevelPreEoc.SIDE11,
		InterfaceID.ToplevelPreEoc.SIDE12, InterfaceID.ToplevelPreEoc.SIDE13,
	};

	private ClickThroughAreas()
	{
	}

	static List<Rectangle> collect(Client client)
	{
		List<Rectangle> out = new ArrayList<>();
		boolean resized = client.isResized();
		if (!resized)
		{
			addAll(client, FIXED, out);
		}
		else
		{
			addAll(client, STRETCH, out);
			addAll(client, PRE_EOC, out);
		}
		// the chat tab buttons are always solid; the message area only when its clicks don't fall through
		add(client, InterfaceID.Chatbox.CONTROLS, out);
		boolean transparent = resized && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
		if (!transparent || client.getVarbitValue(VarbitID.TRANSPARENT_CHATBOX_BLOCKCLICK) == 1)
		{
			add(client, InterfaceID.Chatbox.CHATAREA, out);
		}
		return out.isEmpty() ? Collections.emptyList() : out;
	}

	private static void addAll(Client client, int[] ids, List<Rectangle> out)
	{
		for (int id : ids)
		{
			add(client, id, out);
		}
	}

	private static void add(Client client, int id, List<Rectangle> out)
	{
		Widget w = client.getWidget(id);
		if (w == null || w.isHidden())
		{
			return;
		}
		Rectangle r = w.getBounds();
		if (r != null && r.width > 0 && r.height > 0)
		{
			out.add(r);
		}
	}
}
