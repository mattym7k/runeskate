package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.render.BufferProbe;
import com.gielinorskate.render.LayeredBody;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class GhostBodyStatusTest
{
	private final GhostBodyStatus s = new GhostBodyStatus();

	@Test
	public void theModeFollowsTheDrawsPath()
	{
		assertEquals(GhostBodyStatus.Mode.NONE, GhostBodyStatus.mode(false, false, LayeredBody.Outcome.LAYERED, true));
		assertEquals(GhostBodyStatus.Mode.STATIC, GhostBodyStatus.mode(true, true, LayeredBody.Outcome.LAYERED, true));
		assertEquals(GhostBodyStatus.Mode.STATIC,
			GhostBodyStatus.mode(true, false, LayeredBody.Outcome.NOT_PROVEN, true));
		assertEquals(GhostBodyStatus.Mode.STATIC,
			GhostBodyStatus.mode(true, false, LayeredBody.Outcome.CANVAS_CHANGED, true));
		assertEquals(GhostBodyStatus.Mode.PROCEDURAL, GhostBodyStatus.mode(true, false, LayeredBody.Outcome.LIVE, true));
		assertEquals(GhostBodyStatus.Mode.STATIC, GhostBodyStatus.mode(true, false, LayeredBody.Outcome.LIVE, false));
		assertEquals(GhostBodyStatus.Mode.PROCEDURAL,
			GhostBodyStatus.mode(true, false, LayeredBody.Outcome.NOT_IN_PLACE, true));
		assertEquals(GhostBodyStatus.Mode.ANIM_PROCEDURAL,
			GhostBodyStatus.mode(true, false, LayeredBody.Outcome.LAYERED, true));
		assertEquals(GhostBodyStatus.Mode.ANIM, GhostBodyStatus.mode(true, false, LayeredBody.Outcome.LAYERED, false));
	}

	@Test
	public void aFullyWorkingGhostSaysSoInOneLine()
	{
		s.detail(true, 0, 3 * 128f);
		s.drawn(false, null, BufferProbe.Fail.NONE, LayeredBody.Outcome.LAYERED);
		s.layer(GhostAnim.STANCE, AnimationID.HUMAN_SKI_IDLE, GhostBodyStatus.Layer.OK, null);
		assertEquals("Bob: 3 tiles, full detail (nearest #1), body anim+procedural (STANCE)", s.line("Bob"));
	}

	@Test
	public void aFarGhostSaysWhyItGetsNoPose()
	{
		s.detail(false, 4, 10 * 128f);
		s.drawn(false, null, BufferProbe.Fail.NONE, LayeredBody.Outcome.LAYERED);
		s.layer(GhostAnim.WALK, AnimationID.HUMAN_WALK_F, GhostBodyStatus.Layer.OK, null);
		assertTrue(s.line("Bob"), s.line("Bob").contains("animation only (nearest #5: only the nearest 4 within 32 tiles get the pose)"));
		assertTrue(s.line("Bob").contains("body anim (WALK)"));
	}

	@Test
	public void eachFallbackNamesItsReason()
	{
		s.detail(true, 0, 0f);
		s.noBody(GhostBodyStatus.Body.NOT_IN_SCENE);
		assertTrue(s.line("Bob"), s.line("Bob").contains("body none: their real character is not in your player list"));

		s.drawn(false, null, BufferProbe.Fail.NOT_REBUILT, LayeredBody.Outcome.NOT_PROVEN);
		assertTrue(s.line("Bob"), s.line("Bob").contains("body static: not proven a per-request buffer (a cached model"));

		s.drawn(false, null, BufferProbe.Fail.NONE, LayeredBody.Outcome.LIVE);
		s.idle(808, 819, 808, -1);
		assertTrue(s.line("Bob"),
			s.line("Bob").contains("no snapshot yet: their character is not in plain idle (anim 808, pose 819, idle 808)"));
		s.idle(-1, 808, 808, 2690);
		assertTrue(s.line("Bob"), s.line("Bob").contains("transformed into NPC 2690"));

		s.drawn(true, "IllegalStateException: boom", BufferProbe.Fail.NONE, LayeredBody.Outcome.NOT_PROVEN);
		assertTrue(s.line("Bob"), s.line("Bob").contains("body static: off after an error (IllegalStateException: boom)"));

		s.drawn(false, null, BufferProbe.Fail.NONE, LayeredBody.Outcome.SNAPSHOT);
		s.layer(GhostAnim.WALK, 819, GhostBodyStatus.Layer.WEAPON_IDLE, null);
		s.idle(-1, 1832, 1832, -1);
		assertTrue(s.line("Bob"), s.line("Bob").contains("anim off: their idle stance 1832 is not the plain 808"));

		s.layer(GhostAnim.STANCE, 9000, GhostBodyStatus.Layer.MAYA_FAILED, "IllegalArgumentException");
		assertTrue(s.line("Bob"), s.line("Bob").contains(
			"anim off: Maya animation 9000 failed (IllegalArgumentException); classic ones still play"));
	}

	@Test
	public void namesAreEscapedForTheChatbox()
	{
		s.noBody(GhostBodyStatus.Body.NO_NAME);
		assertTrue(s.line("<col=ff0000>x").startsWith("<lt>col=ff0000<gt>x: "));
		assertTrue(s.line(null).startsWith("(unknown): "));
	}

	@Test
	public void theSignatureChangesOnlyWithTheState()
	{
		s.detail(true, 0, 0f);
		s.drawn(false, null, BufferProbe.Fail.NONE, LayeredBody.Outcome.LAYERED);
		s.layer(GhostAnim.STANCE, 1, GhostBodyStatus.Layer.OK, null);
		int a = s.signature();
		s.detail(true, 1, 500f);
		assertEquals(a, s.signature());
		assertFalse(s.changed(a));
		s.detail(false, 5, 9000f);
		assertNotEquals(a, s.signature());
		assertTrue(s.changed(a));
	}

	@Test
	public void aGhostNotDrawnSaysWhy()
	{
		assertEquals("Bob: not drawn: ghosts are hidden now (Show party skaters off, a PvP area or an instance)",
			GhostBodyStatus.notDrawn("Bob", false, true, true, false));
		assertEquals("Bob: not drawn: the board model could not be built",
			GhostBodyStatus.notDrawn("Bob", true, true, true, true));
		assertEquals("Bob: not drawn: on another world or plane",
			GhostBodyStatus.notDrawn("Bob", true, false, true, false));
		assertEquals("Bob: not drawn: outside your loaded scene",
			GhostBodyStatus.notDrawn("Bob", true, true, false, false));
		assertEquals("Bob: not drawn yet", GhostBodyStatus.notDrawn("Bob", true, true, true, false));
	}
}
