package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.awt.event.KeyEvent;
import org.junit.Test;

/** The button-trick tables: flip button + direction, grab button + direction. */
public class ButtonTricksTest
{
	@Test
	public void directionsCombineAndOppositesCancel()
	{
		assertNull(ButtonTricks.direction(false, false, false, false));
		assertNull(ButtonTricks.direction(true, true, true, true));
		assertEquals(Direction.UP, ButtonTricks.direction(true, false, false, false));
		assertEquals(Direction.DOWN, ButtonTricks.direction(false, true, true, true));
		assertEquals(Direction.LEFT, ButtonTricks.direction(false, false, true, false));
		assertEquals(Direction.RIGHT, ButtonTricks.direction(true, true, false, true));
		assertEquals(Direction.UP_LEFT, ButtonTricks.direction(true, false, true, false));
		assertEquals(Direction.UP_RIGHT, ButtonTricks.direction(true, false, false, true));
		assertEquals(Direction.DOWN_LEFT, ButtonTricks.direction(false, true, true, false));
		assertEquals(Direction.DOWN_RIGHT, ButtonTricks.direction(false, true, false, true));
	}

	@Test
	public void flipButtonTable()
	{
		assertEquals(Trick.KICKFLIP, ButtonTricks.flipTrick(Direction.LEFT, false));
		assertEquals(Trick.HEELFLIP, ButtonTricks.flipTrick(Direction.RIGHT, false));
		assertEquals(Trick.IMPOSSIBLE, ButtonTricks.flipTrick(Direction.UP, false));
		assertEquals(Trick.POP_SHOVE_IT, ButtonTricks.flipTrick(Direction.DOWN, false));
		assertEquals(Trick.VARIAL_KICKFLIP, ButtonTricks.flipTrick(Direction.UP_LEFT, false));
		assertEquals(Trick.VARIAL_HEELFLIP, ButtonTricks.flipTrick(Direction.UP_RIGHT, false));
		assertEquals(Trick.TRE_FLIP, ButtonTricks.flipTrick(Direction.DOWN_LEFT, false));
		assertEquals(Trick.HARDFLIP, ButtonTricks.flipTrick(Direction.DOWN_RIGHT, false));
		assertEquals(Trick.KICKFLIP, ButtonTricks.flipTrick(null, false));
	}

	@Test
	public void theHardModifierGivesTheHardVersionAsAShiftFlickDoes()
	{
		assertEquals(Trick.HARDFLIP, ButtonTricks.flipTrick(Direction.LEFT, true));
		assertEquals(Trick.INWARD_HEELFLIP, ButtonTricks.flipTrick(Direction.RIGHT, true));
		assertEquals(Trick.BIGSPIN, ButtonTricks.flipTrick(Direction.DOWN, true));
		assertEquals(Trick.IMPOSSIBLE, ButtonTricks.flipTrick(Direction.UP, true));
		assertEquals(Trick.HARDFLIP, ButtonTricks.flipTrick(null, true));
	}

	@Test
	public void eachFlipIsTheSameGestureAsItsTrickKey()
	{
		// so physics, scoring and the animations cannot tell a button trick from the key (or flick) for it
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_1, false, false), ButtonTricks.flip(Direction.LEFT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_2, false, false), ButtonTricks.flip(Direction.RIGHT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_3, false, false), ButtonTricks.flip(Direction.DOWN, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_5, false, false),
			ButtonTricks.flip(Direction.UP_LEFT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_6, false, false),
			ButtonTricks.flip(Direction.UP_RIGHT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_7, false, false),
			ButtonTricks.flip(Direction.DOWN_LEFT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_1, false, true),
			ButtonTricks.flip(Direction.DOWN_RIGHT, false));
		assertEquals(KeyboardTricks.gestureFor(KeyEvent.VK_SPACE, false, true), ButtonTricks.flip(Direction.UP, false));
	}

	@Test
	public void aSecondPressMidFlipDoublesThenTriples()
	{
		Gesture again = ButtonTricks.flip(Direction.LEFT, false);
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, again));
		assertEquals(Trick.TRIPLE_KICKFLIP, TrickCatalog.upgrade(Trick.DOUBLE_KICKFLIP, again));
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, ButtonTricks.flip(null, false)));
		Gesture heel = ButtonTricks.flip(Direction.RIGHT, false);
		assertEquals(Trick.DOUBLE_HEELFLIP, TrickCatalog.upgrade(Trick.HEELFLIP, heel));
		assertEquals(Trick.TRIPLE_HEELFLIP, TrickCatalog.upgrade(Trick.DOUBLE_HEELFLIP, heel));
		// a different flip mid-flip upgrades nothing, as a different flick
		assertNull(TrickCatalog.upgrade(Trick.KICKFLIP, heel));
	}

	@Test
	public void grabButtonTable()
	{
		assertEquals(Trick.MELON, ButtonTricks.grab(Direction.LEFT));
		assertEquals(Trick.INDY, ButtonTricks.grab(Direction.RIGHT));
		assertEquals(Trick.NOSEGRAB, ButtonTricks.grab(Direction.UP));
		assertEquals(Trick.TAILGRAB, ButtonTricks.grab(Direction.DOWN));
		assertEquals(Trick.CRAIL, ButtonTricks.grab(Direction.UP_LEFT));
		assertEquals(Trick.MUTE, ButtonTricks.grab(Direction.UP_RIGHT));
		assertEquals(Trick.STALEFISH, ButtonTricks.grab(Direction.DOWN_LEFT));
		assertEquals(Trick.INDY, ButtonTricks.grab(Direction.DOWN_RIGHT));
		assertEquals(Trick.INDY, ButtonTricks.grab(null));
	}

	@Test
	public void grabsAreTheGrabTablesHandsAndAims()
	{
		for (Direction d : Direction.values())
		{
			assertEquals(d.name(), Grabs.pick(ButtonTricks.grabLeftHand(d), ButtonTricks.grabAim(d)),
				ButtonTricks.grab(d));
		}
		// the default is the unaimed left-hand grab (the Q key's)
		assertTrue(ButtonTricks.grabLeftHand(null));
		assertNull(ButtonTricks.grabAim(null));
	}
}
