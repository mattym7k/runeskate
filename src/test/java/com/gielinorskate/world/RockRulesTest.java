package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Contact;
import org.junit.Test;

/**
 * Small decorative rocks are ridden through and never grinded; mining rocks and big boulders still block. The
 * fixtures are the objects as the scene builder sees them: (impostor-resolved) name, actions, model height and the
 * model's footprint extents.
 */
public class RockRulesTest
{
	private static final float T = GridCollisionWorld.TILE;
	private static final String[] NO_ACTIONS = {null, null, null, null, null};
	private static final float[] PEBBLE_FOOTPRINT = {-30, 30, -24, 24};
	private static final float[] ROCK_FOOTPRINT = {-56, 56, -48, 48};

	@Test
	public void rockWordsAreMatchedAsWholeWords()
	{
		for (String name : new String[]{"Rock", "Rocks", "rocks", "Mossy rocks", "Pebbles", "Stones", "Stone",
			"Boulder", "Small boulders", "Rubble", "Rocky outcrop", "Gravel"})
		{
			assertTrue(name, RockRules.isRockName(name));
		}
		for (String name : new String[]{null, "", "Gravestone", "Stone wall", "Stone bench", "Stone pillar",
			"Stone table", "Stone statue", "Rock wall", "Crate", "Rockslide", "Stone steps", "Stone archway",
			"Stone circle altar", "Bush", "Stone fence", "Brimstone"})
		{
			assertFalse(String.valueOf(name), RockRules.isRockName(name));
		}
	}

	@Test
	public void aSmallRockWithNothingToDoOnItIsDecoration()
	{
		assertTrue(RockRules.isSmallDecorativeRock("Rocks", NO_ACTIONS, 40f, ROCK_FOOTPRINT));
		assertTrue(RockRules.isSmallDecorativeRock("Pebbles", null, 20f, PEBBLE_FOOTPRINT));
		assertTrue(RockRules.isSmallDecorativeRock("Stones", new String[0], RockRules.SMALL_ROCK_MAX_HEIGHT,
			ROCK_FOOTPRINT));
		assertTrue("a small boulder", RockRules.isSmallDecorativeRock("Boulder", NO_ACTIONS, 70f, ROCK_FOOTPRINT));
	}

	@Test
	public void miningRocksAndOtherRocksYouCanUseStaySolid()
	{
		assertFalse("ore", RockRules.isSmallDecorativeRock("Rocks", new String[]{"Mine", "Prospect", null, null, null},
			40f, ROCK_FOOTPRINT));
		assertFalse("agility", RockRules.isSmallDecorativeRock("Rocks", new String[]{null, "Climb", null, null, null},
			40f, ROCK_FOOTPRINT));
		assertFalse("searchable", RockRules.isSmallDecorativeRock("Rock", new String[]{"Search"}, 40f,
			ROCK_FOOTPRINT));
	}

	@Test
	public void bigBouldersAndNonRocksStaySolid()
	{
		assertFalse("tall", RockRules.isSmallDecorativeRock("Boulder", NO_ACTIONS,
			RockRules.SMALL_ROCK_MAX_HEIGHT + 1f, ROCK_FOOTPRINT));
		assertFalse("wide", RockRules.isSmallDecorativeRock("Rocks", NO_ACTIONS, 60f,
			new float[]{-120, 120, -40, 40}));
		assertFalse("not a rock", RockRules.isSmallDecorativeRock("Crate", NO_ACTIONS, 40f, ROCK_FOOTPRINT));
		assertFalse("no model", RockRules.isSmallDecorativeRock("Rocks", NO_ACTIONS, 40f, null));
	}

	/** A 6x6 flat world with the FULL-flagged rock tile (2, 2), shaped by the rock's box as the builder does. */
	private static GridCollisionWorld rockTile(float height, boolean pass, boolean ledges)
	{
		GridCollisionWorld w = new GridCollisionWorld(6);
		w.setTile(2, 2, GridCollisionWorld.FULL, height);
		w.addObjectBlocker(2.5f * T, 2.5f * T, ROCK_FOOTPRINT, 0, height, pass, ledges, "Rocks (game object)");
		w.markShaped(2, 2);
		w.rebuildBlockers();
		w.rebuildGrinds();
		return w;
	}

	@Test
	public void aRockBoxWasALandableLedgeAllRound()
	{
		// evidence: a 64-tall rock with a 112 x 96 footprint classified LOW, and LOW boxes' top edges are grinds
		assertEquals(BlockerSet.LOW, ClutterRules.classify(false, 64f, ClutterRules.minSide(ROCK_FOOTPRINT)));
		GridCollisionWorld w = rockTile(64f, false, true);
		assertEquals(4, w.getGrinds().segments().size());
	}

	@Test
	public void aSmallDecorativeRockIsRiddenThroughWithNoGrind()
	{
		GridCollisionWorld w = rockTile(64f, true, false);
		assertTrue(w.getGrinds().segments().isEmpty());
		assertEquals(Float.NEGATIVE_INFINITY, w.blockerTop(1.5f * T, 2.5f * T, 3.5f * T, 2.5f * T), 0f);
		assertEquals("no step up onto it", 0f, w.groundHeight(2.5f * T, 2.5f * T), 1e-3f);
		Contact c = new Contact();
		assertFalse(w.contact(2.5f * T, 2.5f * T, 12f, 0f, 24f, c));
	}

	@Test
	public void aRockTooBigToRideThroughStillBlocksButIsNoRail()
	{
		GridCollisionWorld w = rockTile(100f, false, false);
		assertTrue(w.getGrinds().segments().isEmpty());
		// still a box you can land on
		assertEquals(100f, w.groundHeight(2.5f * T, 2.5f * T), 1e-3f);
		assertEquals(BlockerSet.LOW, w.getBlockers().kind(0));
	}

	@Test
	public void ledgesStayForOtherLowBoxes()
	{
		GridCollisionWorld w = new GridCollisionWorld(6);
		w.setTile(2, 2, GridCollisionWorld.FULL, 64f);
		assertEquals(BlockerSet.LOW, w.addObjectBlocker(2.5f * T, 2.5f * T, ROCK_FOOTPRINT, 0, 64f, false));
		w.markShaped(2, 2);
		w.rebuildGrinds();
		assertEquals(4, w.getGrinds().segments().size());
	}
}
