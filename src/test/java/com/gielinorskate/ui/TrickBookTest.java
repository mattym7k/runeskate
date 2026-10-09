package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class TrickBookTest
{
	private static TrickBook.Settings mouse()
	{
		return new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false, false, "B", "Up", "Down", PadPreset.skate3(), "Skate 3");
	}

	private static List<TrickBook.Entry> entries(List<TrickBook.Section> book)
	{
		List<TrickBook.Entry> out = new ArrayList<>();
		book.forEach(s -> out.addAll(s.entries));
		return out;
	}

	private static TrickBook.Entry entry(List<TrickBook.Section> book, Trick t)
	{
		return entries(book).stream().filter(e -> e.trick == t).findFirst().get();
	}

	private static TrickBook.Section section(List<TrickBook.Section> book, String title)
	{
		return book.stream().filter(s -> s.title.equals(title)).findFirst().get();
	}

	/** Everything the section says, names and details. */
	private static String text(TrickBook.Section s)
	{
		List<String> parts = new ArrayList<>();
		if (s.intro != null)
		{
			parts.add(s.intro);
		}
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
	public void theSectionsComeInReadingOrder()
	{
		List<String> titles = TrickBook.build(mouse()).stream().map(s -> s.title).collect(Collectors.toList());
		assertEquals(Arrays.asList("Basics", "Flip tricks", "Hard flips (Shift)", "Spins & body flips", "Grabs",
			"Manuals", "Grinds", "Off the board", "Controller", "Scoring & levels"), titles);
	}

	@Test
	public void everyTrickIsInTheBookOnce()
	{
		List<TrickBook.Entry> all = entries(TrickBook.build(mouse()));
		for (Trick t : Trick.values())
		{
			assertEquals(t.name(), 1, all.stream().filter(e -> e.trick == t).count());
			assertEquals(t.displayName, entry(TrickBook.build(mouse()), t).name);
		}
	}

	@Test
	public void flipsShowTheirFlickAndPoints()
	{
		List<TrickBook.Section> book = TrickBook.build(mouse());
		TrickBook.Entry kickflip = entry(book, Trick.KICKFLIP);
		assertEquals(Direction.UP_LEFT, kickflip.gesture.direction);
		assertEquals("300", kickflip.points);
		assertEquals("Pull down, flick up-left", kickflip.detail);
		assertNull("no keys in mouse mode", kickflip.key);
		assertTrue(section(book, "Flip tricks").entries.contains(kickflip));
		assertTrue(section(book, "Flip tricks").entries.contains(entry(book, Trick.TRIPLE_KICKFLIP)));
		assertTrue(section(book, "Hard flips (Shift)").entries.contains(entry(book, Trick.HARDFLIP)));
		assertTrue(section(book, "Flip tricks").intro.contains("right mouse button"));
		assertEquals("200/s", entry(book, Trick.FIFTY_FIFTY).points);
	}

	@Test
	public void mirroredFlicksSwapThePictureAndTheWords()
	{
		TrickBook.Settings mirrored = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", true, true, false,
			false, "B", "Up", "Down", PadPreset.skate3(), "Skate 3");
		TrickBook.Entry kickflip = entry(TrickBook.build(mirrored), Trick.KICKFLIP);
		assertEquals(Direction.UP_RIGHT, kickflip.gesture.direction);
		assertEquals("Pull down, flick up-right", kickflip.detail);
	}

	@Test
	public void keyboardModeNamesTheKeysAndDrawsNoFlicks()
	{
		TrickBook.Settings keys = new TrickBook.Settings("Ctrl+K", "F", "C", "right", false, false, true, false, "B",
			"Up", "Down", PadPreset.skate3(), "Skate 3");
		List<TrickBook.Section> book = TrickBook.build(keys);
		TrickBook.Entry kickflip = entry(book, Trick.KICKFLIP);
		assertEquals("1", kickflip.key);
		assertNull(kickflip.gesture);
		assertTrue(entries(book).stream().allMatch(e -> e.gesture == null));
		assertTrue(entry(book, Trick.MANUAL).detail.contains("Hold C"));
		assertFalse(section(book, "Flip tricks").intro.contains("mouse button"));
	}

	@Test
	public void grabsHaveTheAimTableAndTweaks()
	{
		List<TrickBook.Section> book = TrickBook.build(mouse());
		TrickBook.Section grabs = section(book, "Grabs");
		assertEquals(Arrays.asList("Aim (mouse)", "Q (left)", "E (right)"), grabs.table.get(0));
		assertTrue(grabs.table.contains(Arrays.asList("None", "Indy", "Melon")));
		assertTrue(grabs.table.contains(Arrays.asList("Up (nose)", "Nosegrab", "Crail")));
		assertTrue(grabs.table.contains(Arrays.asList("Right (toe)", "Mute", "Indy")));
		assertTrue(grabs.table.contains(Arrays.asList("Left (heel)", "Melon", "Stalefish")));
		assertTrue(entry(book, Trick.METHOD).detail.contains("Melon"));
		assertTrue(text(grabs), text(grabs).contains("Up arrow"));

		TrickBook.Settings mirrored = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", true, true, false,
			false, "B", "Up", "Down", PadPreset.skate3(), "Skate 3");
		assertTrue(section(TrickBook.build(mirrored), "Grabs").table.contains(Arrays.asList("Left (toe)", "Mute",
			"Indy")));
	}

	@Test
	public void theBasicsAndOffTheBoardUseTheConfiguredKeys()
	{
		TrickBook.Settings s = new TrickBook.Settings("Ctrl+J", "G", "Space", "right", false, true, false, false,
			"N", "Up", "Down", PadPreset.skate3(), "Skate 3");
		List<TrickBook.Section> book = TrickBook.build(s);
		String basics = text(section(book, "Basics"));
		assertTrue(basics, basics.contains("Ctrl+J"));
		assertTrue(basics.contains("or N"));
		assertTrue(basics.contains("Shift"));
		assertNotNull(entry(book, Trick.OLLIE).gesture);
		assertTrue(section(book, "Basics").entries.contains(entry(book, Trick.NOLLIE)));
		String foot = text(section(book, "Off the board"));
		assertTrue(foot, foot.contains("G"));
		assertTrue(foot.contains("press G (no need to hold)"));
		assertTrue(foot.contains("Middle mouse"));
		assertTrue(foot.contains("sprint"));
	}

	@Test
	public void controllerModeUsesPadButtons()
	{
		TrickBook.Settings pad = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false, true,
			"B", "Up", "Down", PadPreset.skate3(), "Skate 3");
		List<TrickBook.Section> book = TrickBook.build(pad);
		TrickBook.Entry crail = entry(book, Trick.CRAIL);
		assertEquals("{RT}", crail.pad.substring(crail.pad.indexOf("{RT}"), crail.pad.indexOf("{RT}") + 4));
		assertFalse(crail.detail, crail.detail.contains("{"));
		assertTrue(entry(book, Trick.KICKFLIP).detail.contains("right stick"));
	}

	@Test
	public void theControllerSectionListsThePadLayout()
	{
		TrickBook.Section pad = section(TrickBook.build(mouse()), "Controller");
		assertTrue(pad.entries.size() >= 9);
		assertTrue(pad.entries.stream().allMatch(e -> e.pad != null && ControllerGlyphs.hasGlyphs(e.pad)));
	}

	@Test
	public void scoringNamesTheBonusesAndEveryDesign()
	{
		String scoring = text(section(TrickBook.build(mouse()), "Scoring & levels"));
		assertTrue(scoring, scoring.contains("multiplier"));
		assertTrue(scoring.contains("+25%"));
		assertTrue(scoring.contains("2,000 XP"));
		assertTrue(scoring.contains("2-minute"));
		for (BoardDesign d : BoardDesigns.bundled().all())
		{
			assertTrue(d.name, scoring.contains(d.name));
		}
		assertTrue(scoring.contains("Level 70: Bandos, Armadyl, Guthix, Zamorak, Saradomin"));
		assertTrue(scoring.contains("Level 99: Torva"));
	}
}
