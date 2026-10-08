package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** XP gains, level-ups and the debounced per-account saving, against a fake profile store. */
public class ProgressionTest
{
	/** Profile config in memory; counts writes. */
	static final class FakeStore implements ProfileStore
	{
		final Map<String, String> values = new HashMap<>();
		final List<String> writes = new ArrayList<>();
		String current;

		@Override
		public String profile()
		{
			return current;
		}

		@Override
		public String get(String profile, String key)
		{
			return values.get(profile + "." + key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			values.put(profile + "." + key, value);
			writes.add(profile + "." + key + "=" + value);
		}
	}

	private FakeStore store;
	private Progression progression;

	@Before
	public void setUp()
	{
		store = new FakeStore();
		store.current = "rsprofile.a";
		progression = new Progression(store);
	}

	@Test
	public void loadsTheAccountsXp()
	{
		store.values.put("rsprofile.a.skateXp", "1154");
		progression.load();
		assertEquals(1154, progression.xp());
		assertEquals(10, progression.level());
	}

	@Test
	public void aNewAccountStartsAtLevelOne()
	{
		progression.load();
		assertEquals(0, progression.xp());
		assertEquals(1, progression.level());
	}

	@Test
	public void gainsAreSavedAfterAQuietSpellNotEveryTime()
	{
		progression.load();
		progression.addXp(10, 0f);
		progression.tick(1f);
		progression.addXp(10, 2f);
		progression.tick(4f);
		assertTrue(store.writes.isEmpty());
		progression.tick(2f + Progression.SAVE_DELAY_SECONDS);
		assertEquals(1, store.writes.size());
		assertEquals("20", store.values.get("rsprofile.a.skateXp"));
		// nothing new: no more writes
		progression.tick(100f);
		assertEquals(1, store.writes.size());
	}

	@Test
	public void aSteadyStreamOfGainsStillSavesNowAndThen()
	{
		progression.load();
		float t = 0f;
		while (t < Progression.MAX_UNSAVED_SECONDS + 1f)
		{
			progression.addXp(1, t);
			progression.tick(t);
			t += 1f;
		}
		assertEquals(1, store.writes.size());
	}

	@Test
	public void flushSavesAtOnceAndOnlyWhenChanged()
	{
		progression.load();
		progression.flush();
		assertTrue(store.writes.isEmpty());
		progression.addXp(50, 0f);
		progression.flush();
		assertEquals("50", store.values.get("rsprofile.a.skateXp"));
		progression.flush();
		assertEquals(1, store.writes.size());
	}

	@Test
	public void switchingAccountsSavesToTheOldOneThenLoadsTheNew()
	{
		store.values.put("rsprofile.b.skateXp", "83");
		progression.load();
		progression.addXp(40, 0f);
		store.current = "rsprofile.b";
		progression.load();
		assertEquals("40", store.values.get("rsprofile.a.skateXp"));
		assertEquals(83, progression.xp());
		assertEquals(2, progression.level());
	}

	@Test
	public void withoutAProfileNothingIsSaved()
	{
		store.current = null;
		progression.load();
		progression.addXp(500, 0f);
		progression.flush();
		assertTrue(store.writes.isEmpty());
		assertEquals(500, progression.xp());
	}

	@Test
	public void levelUpReportsTheFinalLevelOnce()
	{
		progression.load();
		assertNull(progression.addXp(82, 0f));
		LevelUp up = progression.addXp(1_100, 0f);
		assertNotNull(up);
		assertEquals(1, up.from);
		assertEquals(10, up.to);
		assertNull(progression.addXp(0, 0f));
	}

	@Test
	public void noLevelUpPast99()
	{
		store.values.put("rsprofile.a.skateXp", "13034431");
		progression.load();
		assertNull(progression.addXp(1_000_000, 0f));
		assertEquals(99, progression.level());
	}

	private static final BoardDesigns DESIGNS = BoardDesigns.bundled();

	private static BoardDesign design(String id)
	{
		return DESIGNS.byId(id);
	}

	@Test
	public void theChosenDesignsAreSavedPerAccount()
	{
		store.values.put("rsprofile.a.skateXp", "101333");
		progression.load();
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		assertTrue(progression.select(design("RUNE")));
		assertTrue(progression.select(design("GRIP_RUNE")));
		assertTrue(progression.select(design("WHEELS_NATURAL")));
		assertEquals("RUNE", store.values.get("rsprofile.a.skateDeck"));
		assertEquals("GRIP_RUNE", store.values.get("rsprofile.a.skateGrip"));
		assertEquals("WHEELS_NATURAL", store.values.get("rsprofile.a.skateWheels"));
		store.current = "rsprofile.b";
		progression.load();
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		store.current = "rsprofile.a";
		progression.load();
		assertEquals("GRIP_RUNE/RUNE/WHEELS_NATURAL", progression.look().toString());
		assertEquals("RUNE", progression.design(DesignPart.DECK).id);
	}

	@Test
	public void aLockedDesignCannotBeChosen()
	{
		progression.load();
		assertFalse(progression.select(design("DRAGON")));
		assertFalse(progression.select(design("WHEELS_TORVA")));
		assertFalse(progression.select(null));
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		assertTrue(store.writes.isEmpty());
		assertTrue(progression.select(design("BRONZE")));
		assertTrue(progression.select(design("GRIP_BLACK")));
	}

	@Test
	public void aSavedDesignAboveTheLevelShowsTheDefault()
	{
		store.values.put("rsprofile.a.skateDeck", "TORVA");
		store.values.put("rsprofile.a.skateGrip", "GRIP_TORVA");
		store.values.put("rsprofile.a.skateXp", "500");
		progression.load();
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		progression.setLevelForDev(99);
		assertEquals("GRIP_TORVA/TORVA/WHEELS_NATURAL", progression.look().toString());
	}

	@Test
	public void oldAndUnknownSavedNamesShowTheDefaults()
	{
		// CLASSIC was the old starter deck; MAX_CAPE an old removed one; a grip id saved as the wheels
		store.values.put("rsprofile.a.skateDeck", "CLASSIC");
		store.values.put("rsprofile.a.skateGrip", "MAX_CAPE");
		store.values.put("rsprofile.a.skateWheels", "GRIP_RUNE");
		progression.load();
		progression.setLevelForDev(99);
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		store.values.put("rsprofile.a.skateDeck", "MAX_CAPE");
		progression.load();
		assertEquals("DECK_TROPICAL", progression.design(DesignPart.DECK).id);
	}

	@Test
	public void savedRemovedTestDesignsShowTheDefaults()
	{
		store.values.put("rsprofile.a.skateGrip", "GRIP_RUNESKATE");
		store.values.put("rsprofile.a.skateDeck", "DECK_RED_CAMO");
		store.values.put("rsprofile.a.skateWheels", "WHEELS_DEATH");
		progression.load();
		progression.setLevelForDev(99);
		assertEquals(BoardLook.defaults(DESIGNS), progression.look());
		assertEquals("GRIP_BLACK/DECK_TROPICAL/WHEELS_NATURAL", progression.look().toString());
	}

	@Test
	public void theOldLadderDeckNamesStillWork()
	{
		store.values.put("rsprofile.a.skateDeck", "SARADOMIN");
		progression.load();
		progression.setLevelForDev(70);
		assertEquals("SARADOMIN", progression.design(DesignPart.DECK).id);
	}

	@Test
	public void theDesignKeysArePerAccountProgress()
	{
		for (String k : new String[]{"skateDeck", "skateGrip", "skateWheels"})
		{
			assertTrue(k, ProgressionService.isProgressKey(k));
		}
	}

	@Test
	public void devLevelSetsExactXpSavesAndMarksTheAccount()
	{
		progression.load();
		progression.setLevelForDev(70);
		assertEquals(70, progression.level());
		assertEquals(737_627, progression.xp());
		assertEquals("737627", store.values.get("rsprofile.a.skateXp"));
		assertEquals("true", store.values.get("rsprofile.a.devLevelSet"));
		progression.setLevelForDev(1);
		assertEquals(0, progression.xp());
		// nothing pending afterwards
		int writes = store.writes.size();
		progression.flush();
		assertEquals(writes, store.writes.size());
	}
}
