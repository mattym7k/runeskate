package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.awt.event.KeyEvent;
import org.junit.Test;

public class KeyboardTricksTest
{
	private static Trick trick(int code, boolean alt, boolean shift)
	{
		return TrickCatalog.forGesture(KeyboardTricks.gestureFor(code, alt, shift));
	}

	@Test
	public void plainKeysGiveTheBasicFlips()
	{
		assertEquals(Trick.OLLIE, trick(KeyEvent.VK_SPACE, false, false));
		assertEquals(Trick.KICKFLIP, trick(KeyEvent.VK_1, false, false));
		assertEquals(Trick.HEELFLIP, trick(KeyEvent.VK_2, false, false));
		assertEquals(Trick.POP_SHOVE_IT, trick(KeyEvent.VK_3, false, false));
		assertEquals(Trick.FS_POP_SHOVE_IT, trick(KeyEvent.VK_4, false, false));
		assertEquals(Trick.VARIAL_KICKFLIP, trick(KeyEvent.VK_5, false, false));
		assertEquals(Trick.VARIAL_HEELFLIP, trick(KeyEvent.VK_6, false, false));
		assertEquals(Trick.TRE_FLIP, trick(KeyEvent.VK_7, false, false));
		assertEquals(Trick.LASER_FLIP, trick(KeyEvent.VK_8, false, false));
		assertEquals(Trick.SHOVE_IT_360, trick(KeyEvent.VK_9, false, false));
		assertEquals(Trick.FS_SHOVE_IT_360, trick(KeyEvent.VK_0, false, false));
		assertEquals(Trick.KICKFLIP, trick(KeyEvent.VK_NUMPAD1, false, false));
	}

	@Test
	public void shiftGivesTheHardVersion()
	{
		assertEquals(Trick.IMPOSSIBLE, trick(KeyEvent.VK_SPACE, false, true));
		assertEquals(Trick.HARDFLIP, trick(KeyEvent.VK_1, false, true));
		assertEquals(Trick.INWARD_HEELFLIP, trick(KeyEvent.VK_2, false, true));
		assertEquals(Trick.BIGSPIN, trick(KeyEvent.VK_3, false, true));
		assertEquals(Trick.FS_BIGSPIN, trick(KeyEvent.VK_4, false, true));
	}

	@Test
	public void altGivesTheNollieVersion()
	{
		assertEquals(Trick.NOLLIE, trick(KeyEvent.VK_SPACE, true, false));
		assertEquals(Trick.NOLLIE_KICKFLIP, trick(KeyEvent.VK_1, true, false));
		assertEquals(Trick.NOLLIE_HEELFLIP, trick(KeyEvent.VK_2, true, false));
		assertEquals(Trick.NOLLIE_SHOVE_IT, trick(KeyEvent.VK_3, true, false));
		assertEquals(Trick.NOLLIE_FS_SHOVE_IT, trick(KeyEvent.VK_4, true, false));
		assertEquals(Trick.NOLLIE_TRE_FLIP, trick(KeyEvent.VK_7, true, false));
		assertEquals(Trick.NOLLIE_HARDFLIP, trick(KeyEvent.VK_1, true, true));
		assertEquals(Trick.NOLLIE_INWARD_HEELFLIP, trick(KeyEvent.VK_2, true, true));
	}

	@Test
	public void theSameKeyAgainMidFlipUpgradesToADouble()
	{
		Gesture again = KeyboardTricks.gestureFor(KeyEvent.VK_1, false, false);
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, again));
		assertEquals(Trick.DOUBLE_HEELFLIP,
			TrickCatalog.upgrade(Trick.HEELFLIP, KeyboardTricks.gestureFor(KeyEvent.VK_2, false, false)));
	}

	@Test
	public void otherKeysAreNotTrickKeys()
	{
		assertNull(KeyboardTricks.gestureFor(KeyEvent.VK_W, false, false));
		assertFalse(KeyboardTricks.isTrickKey(KeyEvent.VK_C));
		assertTrue(KeyboardTricks.isTrickKey(KeyEvent.VK_SPACE));
	}

	@Test
	public void everyKeyLabelPerformsItsTrick()
	{
		for (Trick t : Trick.values())
		{
			String label = KeyboardTricks.keyFor(t);
			if (label == null || label.contains(","))
			{
				continue;
			}
			boolean shift = label.contains("Shift+");
			boolean alt = label.contains("Alt+");
			String key = label.substring(label.lastIndexOf('+') + 1);
			int code = "Space".equals(key) ? KeyEvent.VK_SPACE : KeyEvent.VK_0 + Integer.parseInt(key);
			assertEquals(label, t, trick(code, alt, shift));
		}
	}

	@Test
	public void mirrorSwapsLeftAndRightOnly()
	{
		assertEquals(Direction.UP_RIGHT, KeyboardTricks.mirror(Direction.UP_LEFT));
		assertEquals(Direction.LEFT, KeyboardTricks.mirror(Direction.RIGHT));
		assertEquals(Direction.DOWN_LEFT, KeyboardTricks.mirror(Direction.DOWN_RIGHT));
		assertEquals(Direction.UP, KeyboardTricks.mirror(Direction.UP));
		Gesture g = new Gesture(Direction.UP_LEFT, false, 170f, true);
		assertEquals(new Gesture(Direction.UP_RIGHT, false, 170f, true), KeyboardTricks.mirror(g));
	}
}
