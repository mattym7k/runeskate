package com.gielinorskate.progression;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.render.BoardGeometry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.JagexColor;
import org.junit.Test;

/** Deck unlock levels, saved-name handling and palettes. */
public class DeckTest
{
	@Test
	public void unlockLevelsFollowTheTiers()
	{
		assertEquals(1, Deck.BRONZE.level);
		assertEquals(10, Deck.IRON.level);
		assertEquals(20, Deck.STEEL.level);
		assertEquals(30, Deck.MITHRIL.level);
		assertEquals(40, Deck.ADAMANT.level);
		assertEquals(50, Deck.RUNE.level);
		assertEquals(60, Deck.DRAGON.level);
		for (Deck d : new Deck[]{Deck.BANDOS, Deck.ARMADYL, Deck.GUTHIX, Deck.ZAMORAK, Deck.SARADOMIN})
		{
			assertEquals(70, d.level);
			assertEquals("Gods", d.tier);
		}
		assertEquals(99, Deck.TORVA.level);
	}

	@Test
	public void theLadderInOrder()
	{
		assertEquals(Arrays.asList(Deck.CLASSIC, Deck.BRONZE, Deck.IRON, Deck.STEEL, Deck.MITHRIL, Deck.ADAMANT,
			Deck.RUNE, Deck.DRAGON, Deck.BANDOS, Deck.ARMADYL, Deck.GUTHIX, Deck.ZAMORAK, Deck.SARADOMIN, Deck.TORVA),
			Arrays.asList(Deck.values()));
		assertEquals(Deck.CLASSIC, Deck.DEFAULT);
		assertEquals(1, Deck.CLASSIC.level);
		for (int i = 1; i < Deck.values().length; i++)
		{
			assertTrue(Deck.values()[i].level >= Deck.values()[i - 1].level);
		}
	}

	@Test
	public void removedDecksFallBackToClassic()
	{
		// saved by an earlier version, or sent by a party member running one
		for (String old : new String[]{"AHRIM", "DHAROK", "GUTHAN", "KARIL", "TORAG", "VERAC", "PARTYHAT_RED",
			"PARTYHAT_YELLOW", "PARTYHAT_GREEN", "PARTYHAT_BLUE", "PARTYHAT_PURPLE", "PARTYHAT_WHITE", "MAX_CAPE"})
		{
			assertEquals(old, Deck.CLASSIC, Deck.fromName(old));
			assertEquals(old, Deck.CLASSIC, Deck.usable(old, 99));
		}
	}

	@Test
	public void unlockedAtTheirLevelNotBefore()
	{
		assertTrue(Deck.CLASSIC.isUnlocked(1));
		assertTrue(Deck.BRONZE.isUnlocked(1));
		assertFalse(Deck.RUNE.isUnlocked(49));
		assertTrue(Deck.RUNE.isUnlocked(50));
		assertFalse(Deck.BANDOS.isUnlocked(69));
		assertTrue(Deck.SARADOMIN.isUnlocked(70));
		assertFalse(Deck.TORVA.isUnlocked(98));
		assertTrue(Deck.TORVA.isUnlocked(99));
	}

	@Test
	public void unknownOrMissingNamesAreTheDefault()
	{
		assertEquals(Deck.DEFAULT, Deck.fromName(null));
		assertEquals(Deck.DEFAULT, Deck.fromName(""));
		assertEquals(Deck.DEFAULT, Deck.fromName("GOLDEN_GNOME"));
		assertEquals(Deck.DEFAULT, Deck.fromName("rune"));
		assertEquals(Deck.RUNE, Deck.fromName("RUNE"));
	}

	@Test
	public void aLockedSavedDeckFallsBackToTheDefault()
	{
		assertEquals(Deck.RUNE, Deck.usable("RUNE", 50));
		assertEquals(Deck.DEFAULT, Deck.usable("RUNE", 49));
		assertEquals(Deck.DEFAULT, Deck.usable(null, 99));
	}

	@Test
	public void levelUpsNameTheDecksTheyUnlock()
	{
		assertTrue(Deck.unlockedBetween(1, 9).isEmpty());
		assertEquals(Arrays.asList(Deck.IRON), Deck.unlockedBetween(9, 10));
		assertEquals(Arrays.asList(Deck.IRON, Deck.STEEL), Deck.unlockedBetween(5, 25));
		assertEquals(Arrays.asList(Deck.BANDOS, Deck.ARMADYL, Deck.GUTHIX, Deck.ZAMORAK, Deck.SARADOMIN),
			Deck.unlockedBetween(69, 70));
		assertTrue(Deck.unlockedBetween(70, 98).isEmpty());
		assertEquals(Arrays.asList(Deck.TORVA), Deck.unlockedBetween(98, 99));
	}

	@Test
	public void everyDeckHasFiveColoursAndLooksDifferent()
	{
		Set<String> seen = new HashSet<>();
		for (Deck d : Deck.values())
		{
			short[] p = d.palette();
			assertEquals(Deck.COLOURS, p.length);
			for (short c : p)
			{
				assertTrue(JagexColor.unpackHue(c) <= JagexColor.HUE_MAX);
				assertTrue(JagexColor.unpackLuminance(c) <= JagexColor.LUMINANCE_MAX);
			}
			assertTrue("same palette as another deck: " + d, seen.add(Arrays.toString(p)));
		}
	}

	@Test
	public void classicIsTheOriginalBoard()
	{
		assertArrayEquals(new short[]{
			JagexColor.packHSL(0, 0, 18), JagexColor.packHSL(7, 3, 55), JagexColor.packHSL(10, 2, 105),
			JagexColor.packHSL(0, 7, 45), JagexColor.packHSL(0, 0, 80)}, Deck.CLASSIC.palette());
	}

	@Test
	public void paletteIsACopy()
	{
		Deck.RUNE.palette()[0] = 0;
		assertEquals(JagexColor.packHSL(0, 0, 15), Deck.RUNE.palette()[0]);
	}

	@Test
	public void faceColoursFollowTheBoardsColourGroups()
	{
		BoardGeometry.Mesh mesh = BoardGeometry.defaultBoard();
		short[] palette = Deck.RUNE.palette();
		short[] faces = Deck.faceColors(mesh.colorIndex, palette);
		assertEquals(mesh.faceCount(), faces.length);
		Set<Integer> groups = new HashSet<>();
		for (int i = 0; i < faces.length; i++)
		{
			assertTrue(mesh.colorIndex[i] >= 0 && mesh.colorIndex[i] < Deck.COLOURS);
			assertEquals(palette[mesh.colorIndex[i]], faces[i]);
			groups.add(mesh.colorIndex[i]);
		}
		// the imported board uses every colour group, so every deck colour shows
		assertEquals(Deck.COLOURS, groups.size());
		assertEquals(palette[Deck.DECK], Deck.faceColors(new int[]{9}, palette)[0]);
	}

	@Test
	public void namesFitAPartyMessage()
	{
		for (Deck d : Deck.values())
		{
			assertTrue(d.name().length() <= 16);
		}
	}
}
