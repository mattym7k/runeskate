package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

public class GhostVisibilityTest
{
	private static GhostState at(int world, int plane)
	{
		return new GhostState(world, plane, 0f, 0f, 0f, 0f, 0f, 0f, 0f, SkaterState.ROLLING, null, 0, null, 0f, 1);
	}

	@Test
	public void ghostsShowOnlyWhileSkatingWithTheSettingOnOutsidePvp()
	{
		assertTrue(GhostVisibility.showGhosts(true, true, false, false));
		assertFalse("not skating", GhostVisibility.showGhosts(false, true, false, false));
		assertFalse("setting off", GhostVisibility.showGhosts(true, false, false, false));
		assertFalse("PvP area", GhostVisibility.showGhosts(true, true, true, false));
		assertFalse("instance", GhostVisibility.showGhosts(true, true, false, true));
	}

	@Test
	public void sameWorldAndPlaneOnly()
	{
		assertTrue(GhostVisibility.sameSpace(420, 0, at(420, 0)));
		assertFalse(GhostVisibility.sameSpace(420, 0, at(421, 0)));
		assertFalse(GhostVisibility.sameSpace(420, 0, at(420, 1)));
		assertFalse(GhostVisibility.sameSpace(420, 0, null));
	}

	@Test
	public void insideTheLoadedSceneOnly()
	{
		// a 104 x 104 tile scene is 13312 local units across
		assertTrue(GhostVisibility.inScene(64f, 64f, 104, 104));
		assertTrue(GhostVisibility.inScene(13311f, 13311f, 104, 104));
		assertFalse(GhostVisibility.inScene(-1f, 64f, 104, 104));
		assertFalse(GhostVisibility.inScene(64f, 13312f, 104, 104));
	}

	@Test
	public void labelStaysThenFades()
	{
		assertEquals(1f, GhostVisibility.labelAlpha(0f), 0f);
		assertEquals(1f, GhostVisibility.labelAlpha(1f), 0f);
		assertEquals(0.5f, GhostVisibility.labelAlpha(1.25f), 1e-5f);
		assertEquals(0f, GhostVisibility.labelAlpha(1.5f), 0f);
		assertEquals(0f, GhostVisibility.labelAlpha(9f), 0f);
	}

	@Test
	public void namesMatchIgnoringCaseAndSpaceStyle()
	{
		assertTrue(GhostVisibility.sameName("Skater Bob", "skater bob"));
		assertTrue(GhostVisibility.sameName("Skater_Bob", "Skater Bob"));
		assertFalse(GhostVisibility.sameName("Skater Bob", "Skater Rob"));
		assertFalse(GhostVisibility.sameName(null, "Skater Bob"));
		assertFalse(GhostVisibility.sameName("Skater Bob", null));
	}
}
