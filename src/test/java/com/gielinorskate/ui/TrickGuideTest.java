package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class TrickGuideTest
{
	@Test
	public void everyTrickIsListedOnce()
	{
		Set<Trick> seen = new HashSet<>();
		for (List<TrickGuide.Entry> entries : TrickGuide.groups().values())
		{
			for (TrickGuide.Entry e : entries)
			{
				assertTrue("twice: " + e.trick, seen.add(e.trick));
				assertFalse("no how for " + e.trick, e.how.isEmpty());
			}
		}
		assertEquals(Trick.values().length, seen.size());
	}

	@Test
	public void everyFlickShownReallyDoesTheTrick()
	{
		for (Trick t : Trick.values())
		{
			TrickGuide.Entry e = TrickGuide.entry(t);
			if (e.gesture != null)
			{
				assertEquals(t, TrickCatalog.forGesture(e.gesture));
			}
		}
	}

	@Test
	public void simplestFlickFirst()
	{
		TrickGuide.Entry kickflip = TrickGuide.entry(Trick.KICKFLIP);
		assertEquals(Direction.UP_LEFT, kickflip.gesture.direction);
		assertEquals(0f, kickflip.gesture.turnDegrees, 0f);
		assertFalse(kickflip.gesture.modified);
		assertEquals("Pull down, flick up-left", kickflip.how);
		assertEquals("Push up, flick down", TrickGuide.entry(Trick.NOLLIE).how);
		assertEquals("Hold Shift: pull down, flick up-left", TrickGuide.entry(Trick.HARDFLIP).how);
		assertEquals("Pull down, flick up-left, curling all the way round", TrickGuide.entry(Trick.TRE_FLIP).how);
	}

	@Test
	public void groups()
	{
		Map<TrickGuide.Group, List<TrickGuide.Entry>> g = TrickGuide.groups();
		assertEquals(TrickGuide.Group.BASICS, TrickGuide.entry(Trick.OLLIE).group);
		assertEquals(TrickGuide.Group.FLIPS, TrickGuide.entry(Trick.DOUBLE_KICKFLIP).group);
		assertEquals(TrickGuide.Group.SHIFT, TrickGuide.entry(Trick.BIGSPIN).group);
		assertEquals(TrickGuide.Group.AIR, TrickGuide.entry(Trick.INDY).group);
		assertEquals(TrickGuide.Group.AIR, TrickGuide.entry(Trick.BACKFLIP).group);
		assertEquals(TrickGuide.Group.GRINDS, TrickGuide.entry(Trick.MANUAL).group);
		assertEquals(TrickGuide.Group.GRINDS, TrickGuide.entry(Trick.FIFTY_FIFTY).group);
		assertFalse(g.get(TrickGuide.Group.BASICS).isEmpty());
	}

	@Test
	public void holdsScorePerSecond()
	{
		assertEquals("300", TrickGuide.entry(Trick.KICKFLIP).points());
		assertEquals("200/s", TrickGuide.entry(Trick.FIFTY_FIFTY).points());
		assertNull(TrickGuide.entry(Trick.INDY).gesture);
		assertNotNull(TrickGuide.entry(Trick.INDY).how);
		assertEquals("1", TrickGuide.entry(Trick.KICKFLIP).key);
	}

	@Test
	public void controllerModeNamesThePadsButtons()
	{
		assertEquals("{RS} pull down, flick up-left", TrickGuide.controllerHow(Trick.KICKFLIP,
			TrickGuide.gestureFor(Trick.KICKFLIP)));
		assertEquals("Hold {LT} in the air (or {RT}, aiming {RS} to the toe side)",
			TrickGuide.controllerHow(Trick.INDY, null));
		assertEquals("Hold {RT} in the air, aiming {RS} up", TrickGuide.controllerHow(Trick.CRAIL, null));
		assertEquals("Hold {RS} tilted up a little while rolling", TrickGuide.controllerHow(Trick.MANUAL, null));
		assertEquals("Hold {RS} tilted down a little while rolling",
			TrickGuide.controllerHow(Trick.NOSE_MANUAL, null));
		assertEquals("Land along a rail, holding {A}", TrickGuide.controllerHow(Trick.NOSEGRIND, null));
		assertEquals("Land along a rail, holding S", TrickGuide.controllerHow(Trick.FIVE_O, null));
		assertEquals("Hold {LB} or {RB} and {LS} up in the air (or hold a grab and {LS} up)",
			TrickGuide.controllerHow(Trick.FRONTFLIP, null));
		assertEquals("Hold {LB} or {RB} and {LS} down in the air (or hold a grab and {LS} down)",
			TrickGuide.controllerHow(Trick.BACKFLIP, null));
		assertEquals("Hold {LB} or {RB} and {LS} up through a big air",
			TrickGuide.controllerHow(Trick.DOUBLE_FRONTFLIP, null));
		for (Trick t : Trick.values())
		{
			String how = TrickGuide.controllerHow(t, TrickGuide.gestureFor(t));
			assertFalse(t + ": " + how, how.contains("mouse") || how.contains("wheelie key"));
		}
	}
}
