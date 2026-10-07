package com.gielinorskate.tricks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.tricks.Grabs.Aim;
import org.junit.Test;

public class GrabsTest
{
	@Test
	public void noAimKeepsTodaysIndyAndMelon()
	{
		assertEquals(Trick.INDY, Grabs.pick(true, null));
		assertEquals(Trick.MELON, Grabs.pick(false, null));
	}

	@Test
	public void leftHandTable()
	{
		assertEquals(Trick.NOSEGRAB, Grabs.pick(true, Aim.NOSE));
		assertEquals(Trick.MUTE, Grabs.pick(true, Aim.TOE));
		assertEquals(Trick.MELON, Grabs.pick(true, Aim.HEEL));
		assertEquals(Trick.TAILGRAB, Grabs.pick(true, Aim.TAIL));
	}

	@Test
	public void rightHandTable()
	{
		assertEquals(Trick.CRAIL, Grabs.pick(false, Aim.NOSE));
		assertEquals(Trick.INDY, Grabs.pick(false, Aim.TOE));
		assertEquals(Trick.STALEFISH, Grabs.pick(false, Aim.HEEL));
		assertEquals(Trick.TAILGRAB, Grabs.pick(false, Aim.TAIL));
	}

	@Test
	public void tweakedNames()
	{
		assertEquals("Method", Grabs.tweaked(Trick.MELON).displayName);
		assertEquals("Tweaked Indy", Grabs.tweaked(Trick.INDY).displayName);
		assertEquals("Japan", Grabs.tweaked(Trick.MUTE).displayName);
		assertEquals("Tweaked Stalefish", Grabs.tweaked(Trick.STALEFISH).displayName);
		assertEquals("Nosebone", Grabs.tweaked(Trick.NOSEGRAB).displayName);
		assertEquals("Tailbone", Grabs.tweaked(Trick.TAILGRAB).displayName);
		assertEquals("Crail Tweak", Grabs.tweaked(Trick.CRAIL).displayName);
	}

	@Test
	public void tweaksScoreHalfAsMuchAgainPerSecondAndDoNotTweakAgain()
	{
		for (Trick t : Trick.values())
		{
			if (t.kind != TrickKind.GRAB || Grabs.isTweaked(t))
			{
				continue;
			}
			Trick tw = Grabs.tweaked(t);
			assertEquals(TrickKind.GRAB, tw.kind);
			assertTrue(Grabs.isTweaked(tw));
			assertEquals(t + " tweak points", Math.round(t.points * 1.5f), tw.points);
			assertNull(Grabs.tweaked(tw));
		}
		assertFalse(Grabs.isTweaked(Trick.INDY));
		assertNull(Grabs.tweaked(Trick.KICKFLIP));
	}

	@Test
	public void everyGrabIsATweakOrHasOne()
	{
		int grabs = 0;
		for (Trick t : Trick.values())
		{
			if (t.kind == TrickKind.GRAB)
			{
				grabs++;
				assertTrue(t.toString(), Grabs.isTweaked(t) || Grabs.tweaked(t) != null);
			}
		}
		assertEquals(14, grabs);
	}
}
