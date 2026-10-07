package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** The design manifest (designs.json), id fallbacks, the old deck names, unlocks and wire names. */
public class BoardDesignsTest
{
	private static final String SMALL = "{\"designs\": ["
		+ "{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1},"
		+ "{\"id\": \"GRIP_B\", \"name\": \"B\", \"part\": \"grip\", \"unlock\": 10, \"credit\": \"someone\"},"
		+ "{\"id\": \"DECK_A\", \"name\": \"A\", \"part\": \"deck\", \"unlock\": 1},"
		+ "{\"id\": \"RUNE\", \"name\": \"Rune\", \"part\": \"deck\", \"unlock\": 50},"
		+ "{\"id\": \"WHEELS_A\", \"name\": \"A\", \"part\": \"wheels\", \"unlock\": 1},"
		+ "{\"id\": \"WHEELS_RUNE\", \"name\": \"Rune\", \"part\": \"wheels\", \"unlock\": 50}"
		+ "]}";

	private static BoardDesigns small()
	{
		try
		{
			return BoardDesigns.parse(new StringReader(SMALL));
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void refused(String json)
	{
		try
		{
			BoardDesigns.parse(new StringReader(json));
			fail("accepted " + json);
		}
		catch (IllegalArgumentException | IOException expected)
		{
			// refused
		}
	}

	@Test
	public void parsesTheManifestInOrder()
	{
		BoardDesigns d = small();
		assertEquals(6, d.all().size());
		BoardDesign b = d.byId("GRIP_B");
		assertEquals("B", b.name);
		assertEquals(DesignPart.GRIP, b.part);
		assertEquals(10, b.unlock);
		assertEquals("someone", b.credit);
		assertNull(d.byId("GRIP_A").credit);
		assertEquals(Arrays.asList("GRIP_A", "GRIP_B"), ids(d.of(DesignPart.GRIP)));
		assertEquals(Arrays.asList("DECK_A", "RUNE"), ids(d.of(DesignPart.DECK)));
		assertNull(d.byId("NOPE"));
	}

	@Test
	public void theFirstDesignOfEachPartIsItsDefault()
	{
		BoardDesigns d = small();
		assertEquals("GRIP_A", d.defaultFor(DesignPart.GRIP).id);
		assertEquals("DECK_A", d.defaultFor(DesignPart.DECK).id);
		assertEquals("WHEELS_A", d.defaultFor(DesignPart.WHEELS).id);
	}

	@Test
	public void badManifestsAreRefused()
	{
		refused("[]");
		refused("{\"designs\": []}");
		// a part without designs
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1}]}");
		String deck = "{\"id\": \"DECK_A\", \"name\": \"A\", \"part\": \"deck\", \"unlock\": 1},"
			+ "{\"id\": \"WHEELS_A\", \"name\": \"A\", \"part\": \"wheels\", \"unlock\": 1}";
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 5}," + deck + "]}");
		refused("{\"designs\": [{\"id\": \"grip_a\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1}," + deck + "]}");
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"tail\", \"unlock\": 1}," + deck + "]}");
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 100}," + deck + "]}");
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"\", \"part\": \"grip\", \"unlock\": 1}," + deck + "]}");
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1},"
			+ "{\"id\": \"GRIP_A\", \"name\": \"B\", \"part\": \"grip\", \"unlock\": 1}," + deck + "]}");
		// wire names (the id without the part's prefix) are short and unique within a part
		refused("{\"designs\": [{\"id\": \"GRIP_MUCH_TOO_LONG\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1},"
			+ deck + "]}");
		refused("{\"designs\": [{\"id\": \"GRIP_A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1},"
			+ "{\"id\": \"A\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1}," + deck + "]}");
		refused("not json");
	}

	@Test
	public void unknownOrWrongPartIdsFallBackToTheDefault()
	{
		BoardDesigns d = small();
		assertEquals("GRIP_B", d.find(DesignPart.GRIP, "GRIP_B").id);
		assertEquals("GRIP_A", d.find(DesignPart.GRIP, null).id);
		assertEquals("GRIP_A", d.find(DesignPart.GRIP, "").id);
		assertEquals("GRIP_A", d.find(DesignPart.GRIP, "GOLDEN_GNOME").id);
		assertEquals("GRIP_A", d.find(DesignPart.GRIP, "grip_b").id);
		// a deck id saved as the grip
		assertEquals("GRIP_A", d.find(DesignPart.GRIP, "RUNE").id);
		assertEquals("RUNE", d.find(DesignPart.DECK, " RUNE ").id);
	}

	@Test
	public void aLockedSavedDesignIsTheDefaultUntilItsLevel()
	{
		BoardDesigns d = small();
		assertEquals("DECK_A", d.usable(DesignPart.DECK, "RUNE", 49).id);
		assertEquals("RUNE", d.usable(DesignPart.DECK, "RUNE", 50).id);
		assertTrue(d.byId("RUNE").isUnlocked(50));
		assertFalse(d.byId("RUNE").isUnlocked(49));
	}

	@Test
	public void unlocksBetweenLevelsInManifestOrder()
	{
		BoardDesigns d = small();
		assertTrue(d.unlockedBetween(10, 49).isEmpty());
		assertEquals(Arrays.asList("GRIP_B"), ids(d.unlockedBetween(9, 10)));
		assertEquals(Arrays.asList("GRIP_B", "RUNE", "WHEELS_RUNE"), ids(d.unlockedBetween(1, 99)));
		assertTrue(d.unlockedBetween(50, 99).isEmpty());
	}

	@Test
	public void wireNamesDropThePartPrefix()
	{
		BoardDesigns d = small();
		assertEquals("B", d.byId("GRIP_B").wireName());
		assertEquals("RUNE", d.byId("RUNE").wireName());
		assertEquals("RUNE", d.byId("WHEELS_RUNE").wireName());
		assertSame(d.byId("GRIP_B"), d.fromWire(DesignPart.GRIP, "B"));
		assertSame(d.byId("RUNE"), d.fromWire(DesignPart.DECK, "RUNE"));
		assertSame(d.byId("WHEELS_RUNE"), d.fromWire(DesignPart.WHEELS, "RUNE"));
		// a full id is understood too; unknown or another part's: the default
		assertSame(d.byId("GRIP_B"), d.fromWire(DesignPart.GRIP, "GRIP_B"));
		assertSame(d.defaultFor(DesignPart.GRIP), d.fromWire(DesignPart.GRIP, "RUNE"));
		assertSame(d.defaultFor(DesignPart.DECK), d.fromWire(DesignPart.DECK, null));
		assertSame(d.defaultFor(DesignPart.DECK), d.fromWire(DesignPart.DECK, "ZZZ"));
	}

	@Test
	public void theBundledManifestKeepsTheOldDeckNames()
	{
		BoardDesigns d = BoardDesigns.bundled();
		assertEquals("GRIP_RUNESKATE", d.defaultFor(DesignPart.GRIP).id);
		assertEquals("DECK_RED_CAMO", d.defaultFor(DesignPart.DECK).id);
		assertEquals("WHEELS_DEATH", d.defaultFor(DesignPart.WHEELS).id);
		// every rung of the old ladder is a deck design under the same name and level, so a saved skateDeck or
		// a party member's deck name keeps working
		for (Deck old : Deck.values())
		{
			if (old == Deck.CLASSIC)
			{
				continue;
			}
			BoardDesign b = d.byId(old.name());
			assertEquals(old.name(), DesignPart.DECK, b.part);
			assertEquals(old.name(), old.level, b.unlock);
			assertEquals(old.name(), old.displayName, b.name);
			// with a grip and wheels of the same name and level: a full set
			for (DesignPart p : new DesignPart[]{DesignPart.GRIP, DesignPart.WHEELS})
			{
				BoardDesign set = d.byId(p.prefix + old.name());
				assertEquals(p + " " + old, old.level, set.unlock);
				assertEquals(old.displayName, set.name);
			}
		}
		// the old starter deck (and decks removed from the ladder long ago) are the default deck now
		assertEquals("DECK_RED_CAMO", d.find(DesignPart.DECK, "CLASSIC").id);
		assertEquals("DECK_RED_CAMO", d.find(DesignPart.DECK, "PARTYHAT_PURPLE").id);
		assertEquals("DECK_TROPICAL", d.find(DesignPart.DECK, "DECK_TROPICAL").id);
		assertEquals("GRIP_BLACK", d.find(DesignPart.GRIP, "GRIP_BLACK").id);
		assertEquals("WHEELS_NATURAL", d.find(DesignPart.WHEELS, "WHEELS_NATURAL").id);
	}

	@Test
	public void bundledIdsAreUniqueAndFitTheWire()
	{
		Set<String> seen = new HashSet<>();
		for (BoardDesign b : BoardDesigns.bundled().all())
		{
			assertTrue(b.id, seen.add(b.id));
			assertTrue(b.id, b.wireName().length() <= BoardDesigns.WIRE_MAX);
		}
	}

	@Test
	public void customDesignsComeAfterTheShippedOnesAndAreAlwaysUnlocked()
	{
		BoardDesigns d = small();
		BoardDesign mine = BoardDesign.custom("CUSTOM_0A1B2C3D", "My grip", DesignPart.GRIP, 1);
		d.setCustom(Arrays.asList(mine, BoardDesign.custom("CUSTOM_00000001", "My deck", DesignPart.DECK, 1)));
		assertEquals(Arrays.asList("GRIP_A", "GRIP_B", "CUSTOM_0A1B2C3D"), ids(d.of(DesignPart.GRIP)));
		assertEquals(Arrays.asList("DECK_A", "RUNE", "CUSTOM_00000001"), ids(d.of(DesignPart.DECK)));
		assertSame(mine, d.byId("CUSTOM_0A1B2C3D"));
		assertSame(mine, d.usable(DesignPart.GRIP, "CUSTOM_0A1B2C3D", 1));
		assertTrue(mine.custom);
		// on another part's key it is the default; the shipped catalogue leaves it out
		assertEquals("DECK_A", d.find(DesignPart.DECK, "CUSTOM_0A1B2C3D").id);
		assertEquals(6, d.all().size());
		assertEquals(2, d.custom().size());
	}

	@Test
	public void aMissingCustomDesignFallsBackToTheDefault()
	{
		BoardDesigns d = small();
		d.setCustom(Arrays.asList(BoardDesign.custom("CUSTOM_0A1B2C3D", "My grip", DesignPart.GRIP, 1)));
		d.setCustom(new ArrayList<>());
		assertEquals("GRIP_A", d.usable(DesignPart.GRIP, "CUSTOM_0A1B2C3D", 99).id);
		assertEquals(Arrays.asList("GRIP_A", "GRIP_B"), ids(d.of(DesignPart.GRIP)));
	}

	@Test
	public void customDesignsNeverResolveFromTheWire()
	{
		BoardDesigns d = small();
		d.setCustom(Arrays.asList(BoardDesign.custom("CUSTOM_0A1B2C3D", "Mine", DesignPart.GRIP, 1)));
		assertEquals("GRIP_A", d.fromWire(DesignPart.GRIP, "CUSTOM_0A1B2C3D").id);
	}

	@Test
	public void onlyCustomDesignsWithCustomIdsAreTaken()
	{
		BoardDesigns d = small();
		d.setCustom(Arrays.asList(new BoardDesign("CUSTOM_0A1B2C3D", "x", DesignPart.GRIP, 1, null),
			BoardDesign.custom("GRIP_A", "shadow", DesignPart.GRIP, 1),
			BoardDesign.custom("CUSTOM_00000002", "ok", DesignPart.WHEELS, 1)));
		assertEquals(Arrays.asList("CUSTOM_00000002"), ids(d.custom()));
		assertEquals("A", d.byId("GRIP_A").name);
		refused("{\"designs\": [{\"id\": \"CUSTOM_1\", \"name\": \"A\", \"part\": \"grip\", \"unlock\": 1}]}");
	}

	@Test
	public void anEditedCustomDesignMakesADifferentLook()
	{
		BoardDesigns d = small();
		BoardLook before = BoardLook.defaults(d).with(BoardDesign.custom("CUSTOM_0A1B2C3D", "Mine", DesignPart.GRIP, 1));
		BoardLook same = BoardLook.defaults(d).with(BoardDesign.custom("CUSTOM_0A1B2C3D", "Renamed", DesignPart.GRIP, 1));
		BoardLook edited = BoardLook.defaults(d).with(BoardDesign.custom("CUSTOM_0A1B2C3D", "Mine", DesignPart.GRIP, 2));
		assertEquals(before, same);
		assertEquals(before.hashCode(), same.hashCode());
		assertFalse(before.equals(edited));
	}

	private static List<String> ids(List<BoardDesign> list)
	{
		List<String> out = new ArrayList<>();
		for (BoardDesign b : list)
		{
			out.add(b.id);
		}
		return out;
	}
}
