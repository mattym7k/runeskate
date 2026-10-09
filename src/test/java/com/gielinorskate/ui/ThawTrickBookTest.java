package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** The Trick Book and the pad words with the Tony Hawk's American Wasteland preset selected. */
public class ThawTrickBookTest
{
	private static TrickBook.Settings thaw(boolean controller)
	{
		return new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false, controller, "B", "Up",
			"Down", PadPreset.thaw(), "Tony Hawk's American Wasteland");
	}

	private static TrickBook.Entry entry(List<TrickBook.Section> book, Trick t)
	{
		for (TrickBook.Section s : book)
		{
			for (TrickBook.Entry e : s.entries)
			{
				if (e.trick == t)
				{
					return e;
				}
			}
		}
		throw new AssertionError(t);
	}

	private static TrickBook.Section section(List<TrickBook.Section> book, String title)
	{
		return book.stream().filter(s -> s.title.equals(title)).findFirst().get();
	}

	private static String text(TrickBook.Section s)
	{
		List<String> parts = new ArrayList<>();
		parts.add(String.valueOf(s.intro));
		for (TrickBook.Entry e : s.entries)
		{
			parts.add(e.name + ": " + e.detail);
		}
		for (List<String> row : s.table)
		{
			parts.add(String.join(" | ", row));
		}
		return String.join("\n", parts);
	}

	@Test
	public void flipsAreTheFlipButtonAndADirection()
	{
		List<TrickBook.Section> book = TrickBook.build(thaw(true));
		TrickBook.Entry kickflip = entry(book, Trick.KICKFLIP);
		assertEquals("{X} + {LS} left (or no direction)", kickflip.pad);
		assertNull("no flick picture: the right stick is the camera", kickflip.gesture);
		assertEquals("{X} + {LS} right", entry(book, Trick.HEELFLIP).pad);
		assertEquals("{X} + {LS} up", entry(book, Trick.IMPOSSIBLE).pad);
		assertEquals("{X} + {LS} down", entry(book, Trick.POP_SHOVE_IT).pad);
		assertTrue(entry(book, Trick.VARIAL_KICKFLIP).pad.startsWith("{X} + {DUP}{DLEFT}"));
		assertTrue(entry(book, Trick.VARIAL_HEELFLIP).pad.startsWith("{X} + {DUP}{DRIGHT}"));
		assertTrue(entry(book, Trick.TRE_FLIP).pad.startsWith("{X} + {DDOWN}{DLEFT}"));
		assertTrue(entry(book, Trick.HARDFLIP).pad.startsWith("{X} + {DDOWN}{DRIGHT}"));
		assertEquals("Hold {LB} or {RB}: {X} + {LS} right", entry(book, Trick.INWARD_HEELFLIP).pad);
		assertEquals("Hold {LB} or {RB}: {X} + {LS} down", entry(book, Trick.BIGSPIN).pad);
		assertEquals("Kickflip, then {X} again mid-flip", entry(book, Trick.DOUBLE_KICKFLIP).pad);
		assertEquals("Hold {A} to crouch, let go to pop", entry(book, Trick.OLLIE).pad);
		assertTrue(entry(book, Trick.LASER_FLIP).detail.startsWith("Not on the buttons"));
		assertTrue(entry(book, Trick.NOLLIE).detail.startsWith("Not on the buttons"));
		String flips = text(section(book, "Flip tricks"));
		assertTrue(flips, flips.contains("press X"));
		assertTrue(flips, flips.contains("again mid-flip for a double"));
	}

	@Test
	public void grabsAreTheGrabButtonAndADirection()
	{
		List<TrickBook.Section> book = TrickBook.build(thaw(true));
		assertEquals("Hold {B} + {LS} left in the air", entry(book, Trick.MELON).pad);
		assertEquals("Hold {B} + {LS} right in the air (or no direction)", entry(book, Trick.INDY).pad);
		assertEquals("Hold {B} + {LS} up in the air", entry(book, Trick.NOSEGRAB).pad);
		assertTrue(entry(book, Trick.CRAIL).pad.startsWith("Hold {B} + {DUP}{DLEFT}"));
		assertTrue(entry(book, Trick.MUTE).pad.startsWith("Hold {B} + {DUP}{DRIGHT}"));
		assertTrue(entry(book, Trick.STALEFISH).pad.startsWith("Hold {B} + {DDOWN}{DLEFT}"));
		String grabs = text(section(book, "Grabs"));
		assertTrue(grabs, grabs.contains("Left | Melon"));
		assertTrue(grabs, grabs.contains("None | Indy"));
		assertTrue(grabs, grabs.contains("Down-left | Stalefish"));
		assertFalse(grabs, grabs.contains("Aim (stick)"));
	}

	@Test
	public void manualsAndTheSticks()
	{
		List<TrickBook.Section> book = TrickBook.build(thaw(true));
		assertTrue(entry(book, Trick.MANUAL).pad.startsWith("{LS} up, then down"));
		assertTrue(entry(book, Trick.NOSE_MANUAL).pad.startsWith("{LS} down, then up"));
		String pad = text(section(book, "Controller"));
		assertTrue(pad, pad.contains("Preset: Tony Hawk's American Wasteland"));
		assertTrue(pad, pad.contains("Turns the camera"));
		assertFalse(pad, pad.contains("Pull down, flick"));
		assertTrue(pad, pad.contains("Flip trick"));
		assertTrue(pad, pad.contains("Near a rail, hold"));
		assertTrue(pad, pad.contains("spin faster"));
		String basics = text(section(book, "Basics"));
		assertTrue(basics, basics.contains("Push: left stick up"));
		String foot = text(section(book, "Off the board"));
		assertTrue(foot, foot.contains("Turn the camera: right stick"));
		assertTrue(text(section(book, "Grinds")).contains("Hold Y near a rail"));
	}

	@Test
	public void withoutControllerModeTheBookIsTheKeyboardOne()
	{
		List<TrickBook.Section> pad = TrickBook.build(thaw(false));
		List<TrickBook.Section> keys = TrickBook.build(new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false,
			true, false, false, "B", "Up", "Down", PadPreset.skate3(), "Skate 3"));
		assertEquals(text(section(keys, "Flip tricks")), text(section(pad, "Flip tricks")));
		assertEquals(text(section(keys, "Grabs")), text(section(pad, "Grabs")));
	}

	@Test
	public void theLayoutListNamesTheButtonActions()
	{
		Map<String, String> layout = PadWords.layout(PadPreset.thaw());
		assertTrue(layout.get("{X}"), layout.get("{X}").startsWith("Flip trick"));
		assertTrue(layout.get("{B}"), layout.get("{B}").contains("On foot: drop or pick up the board"));
		assertTrue(layout.get("{Y}"), layout.get("{Y}").contains("On foot: get on"));
		assertTrue(layout.get("{LB} {RB}"), layout.get("{LB} {RB}").contains("On foot: sprint"));
		assertTrue(layout.get("{A}"), layout.get("{A}").contains("On foot: jump"));
		for (PadAction a : PadAction.values())
		{
			assertFalse(a.name(), PadWords.describe(a, false).isEmpty());
		}
		assertEquals("{X}", PadWords.resolve("{@flip}", PadPreset.thaw(), "F"));
		assertEquals("{LB}/{RB}", PadWords.resolve("{@modSlash}", PadPreset.thaw(), "F"));
		assertEquals("{LB}/{RB}", PadWords.resolve("{@modSlash}", PadPreset.skate3(), "F"));
		assertEquals("the flip button", PadWords.resolve("{@flip}", PadPreset.skate3(), "F"));
	}

	@Test
	public void theLayoutEditorCanStartFromThaw()
	{
		ControllerLayoutView v = new ControllerLayoutView(() -> { }, code -> { });
		v.showFor(PadPreset.skate3(), null);
		javax.swing.JComboBox<?> from = (javax.swing.JComboBox<?>) find(v, "layout:startFrom");
		from.setSelectedIndex(3);
		((javax.swing.JButton) find(v, "layout:load")).doClick();
		assertEquals(PadPreset.thaw(), v.editing);
	}

	private static java.awt.Component find(java.awt.Container c, String name)
	{
		for (java.awt.Component k : c.getComponents())
		{
			if (name.equals(k.getName()))
			{
				return k;
			}
			if (k instanceof java.awt.Container)
			{
				java.awt.Component f = find((java.awt.Container) k, name);
				if (f != null)
				{
					return f;
				}
			}
		}
		return null;
	}
}
