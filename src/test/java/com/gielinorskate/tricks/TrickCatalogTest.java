package com.gielinorskate.tricks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gielinorskate.tricks.Gesture.Direction;
import org.junit.Test;

public class TrickCatalogTest
{
	private static Gesture g(Direction d, boolean nollie, float turn)
	{
		return new Gesture(d, nollie, turn);
	}

	@Test
	public void upIsOllie()
	{
		assertEquals(Trick.OLLIE, TrickCatalog.forGesture(g(Direction.UP, false, 0f)));
	}

	@Test
	public void upLeftEscalatesWithTurnDegrees()
	{
		assertEquals(Trick.KICKFLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 0f)));
		assertEquals(Trick.KICKFLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 59.9f)));
		assertEquals(Trick.VARIAL_KICKFLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 60f)));
		assertEquals(Trick.VARIAL_KICKFLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 149.9f)));
		assertEquals(Trick.TRE_FLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 150f)));
		assertEquals(Trick.TRE_FLIP, TrickCatalog.forGesture(g(Direction.UP_LEFT, false, 400f)));
	}

	@Test
	public void upRightEscalatesWithTurnDegrees()
	{
		assertEquals(Trick.HEELFLIP, TrickCatalog.forGesture(g(Direction.UP_RIGHT, false, 0f)));
		assertEquals(Trick.VARIAL_HEELFLIP, TrickCatalog.forGesture(g(Direction.UP_RIGHT, false, 60f)));
		assertEquals(Trick.VARIAL_HEELFLIP, TrickCatalog.forGesture(g(Direction.UP_RIGHT, false, 149.9f)));
		assertEquals(Trick.LASER_FLIP, TrickCatalog.forGesture(g(Direction.UP_RIGHT, false, 150f)));
	}

	@Test
	public void leftEscalatesWithTurnDegrees()
	{
		assertEquals(Trick.POP_SHOVE_IT, TrickCatalog.forGesture(g(Direction.LEFT, false, 0f)));
		assertEquals(Trick.POP_SHOVE_IT, TrickCatalog.forGesture(g(Direction.LEFT, false, 149.9f)));
		assertEquals(Trick.SHOVE_IT_360, TrickCatalog.forGesture(g(Direction.LEFT, false, 150f)));
	}

	@Test
	public void rightEscalatesWithTurnDegrees()
	{
		assertEquals(Trick.FS_POP_SHOVE_IT, TrickCatalog.forGesture(g(Direction.RIGHT, false, 0f)));
		assertEquals(Trick.FS_POP_SHOVE_IT, TrickCatalog.forGesture(g(Direction.RIGHT, false, 149.9f)));
		assertEquals(Trick.FS_SHOVE_IT_360, TrickCatalog.forGesture(g(Direction.RIGHT, false, 150f)));
	}

	@Test
	public void nollieDownDirectionsAreTheNollieFamily()
	{
		assertEquals(Trick.NOLLIE, TrickCatalog.forGesture(g(Direction.DOWN, true, 0f)));
		assertEquals(Trick.NOLLIE_KICKFLIP, TrickCatalog.forGesture(g(Direction.DOWN_LEFT, true, 0f)));
		assertEquals(Trick.NOLLIE_HEELFLIP, TrickCatalog.forGesture(g(Direction.DOWN_RIGHT, true, 0f)));
	}

	@Test
	public void nollieSidewaysFlicksAreNollieShoveIts()
	{
		assertEquals(Trick.NOLLIE_SHOVE_IT, TrickCatalog.forGesture(g(Direction.LEFT, true, 0f)));
		assertEquals(Trick.NOLLIE_SHOVE_IT, TrickCatalog.forGesture(g(Direction.LEFT, true, 200f)));
		assertEquals(Trick.NOLLIE_FS_SHOVE_IT, TrickCatalog.forGesture(g(Direction.RIGHT, true, 0f)));
	}

	@Test
	public void curvedNollieKickflipIsANollie360Flip()
	{
		assertEquals(Trick.NOLLIE_KICKFLIP, TrickCatalog.forGesture(g(Direction.DOWN_LEFT, true, 149.9f)));
		assertEquals(Trick.NOLLIE_TRE_FLIP, TrickCatalog.forGesture(g(Direction.DOWN_LEFT, true, 150f)));
		assertEquals(Trick.NOLLIE_HEELFLIP, TrickCatalog.forGesture(g(Direction.DOWN_RIGHT, true, 200f)));
	}

	@Test
	public void unmappedGesturesReturnNull()
	{
		assertNull(TrickCatalog.forGesture(g(Direction.DOWN, false, 0f)));
		assertNull(TrickCatalog.forGesture(g(Direction.UP, true, 0f)));
		assertNull(TrickCatalog.forGesture(g(Direction.UP_LEFT, true, 0f)));
	}

	@Test
	public void reFlickUpgradesKickflipAndHeelflip()
	{
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, g(Direction.UP_LEFT, false, 0f)));
		assertEquals(Trick.DOUBLE_HEELFLIP, TrickCatalog.upgrade(Trick.HEELFLIP, g(Direction.UP_RIGHT, false, 0f)));
	}

	@Test
	public void reFlickOnlyUpgradesMatchingDirection()
	{
		assertNull(TrickCatalog.upgrade(Trick.KICKFLIP, g(Direction.UP_RIGHT, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.HEELFLIP, g(Direction.UP_LEFT, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.OLLIE, g(Direction.UP, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.DOUBLE_KICKFLIP, g(Direction.UP_RIGHT, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.DOUBLE_HEELFLIP, g(Direction.UP_LEFT, false, 0f)));
	}

	@Test
	public void reFlickingADoubleMakesATriple()
	{
		assertEquals(Trick.TRIPLE_KICKFLIP, TrickCatalog.upgrade(Trick.DOUBLE_KICKFLIP, g(Direction.UP_LEFT, false, 0f)));
		assertEquals(Trick.TRIPLE_HEELFLIP, TrickCatalog.upgrade(Trick.DOUBLE_HEELFLIP, g(Direction.UP_RIGHT, false, 0f)));
		// a triple is as far as it goes
		assertNull(TrickCatalog.upgrade(Trick.TRIPLE_KICKFLIP, g(Direction.UP_LEFT, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.TRIPLE_HEELFLIP, g(Direction.UP_RIGHT, false, 0f)));
	}

	private static Gesture shift(Direction d, boolean nollie, float turn)
	{
		return new Gesture(d, nollie, turn, true);
	}

	@Test
	public void shiftTurnsAnOllieIntoAnImpossible()
	{
		assertEquals(Trick.IMPOSSIBLE, TrickCatalog.forGesture(shift(Direction.UP, false, 0f)));
	}

	@Test
	public void shiftTurnsKickflipsIntoHardflips()
	{
		assertEquals(Trick.HARDFLIP, TrickCatalog.forGesture(shift(Direction.UP_LEFT, false, 0f)));
		// the varial and 360 forms are hardflips too with Shift held
		assertEquals(Trick.HARDFLIP, TrickCatalog.forGesture(shift(Direction.UP_LEFT, false, 90f)));
		assertEquals(Trick.HARDFLIP, TrickCatalog.forGesture(shift(Direction.UP_LEFT, false, 200f)));
	}

	@Test
	public void shiftTurnsHeelflipsIntoInwardHeelflips()
	{
		assertEquals(Trick.INWARD_HEELFLIP, TrickCatalog.forGesture(shift(Direction.UP_RIGHT, false, 0f)));
		assertEquals(Trick.INWARD_HEELFLIP, TrickCatalog.forGesture(shift(Direction.UP_RIGHT, false, 200f)));
	}

	@Test
	public void shiftTurnsShoveItsIntoBigspins()
	{
		assertEquals(Trick.BIGSPIN, TrickCatalog.forGesture(shift(Direction.LEFT, false, 0f)));
		assertEquals(Trick.BIGSPIN, TrickCatalog.forGesture(shift(Direction.LEFT, false, 200f)));
		assertEquals(Trick.FS_BIGSPIN, TrickCatalog.forGesture(shift(Direction.RIGHT, false, 0f)));
		assertEquals(Trick.FS_BIGSPIN, TrickCatalog.forGesture(shift(Direction.RIGHT, false, 200f)));
	}

	@Test
	public void shiftNollieFlipsAreNollieHardflipsAndInwardHeels()
	{
		assertEquals(Trick.NOLLIE_HARDFLIP, TrickCatalog.forGesture(shift(Direction.DOWN_LEFT, true, 0f)));
		assertEquals(Trick.NOLLIE_HARDFLIP, TrickCatalog.forGesture(shift(Direction.DOWN_LEFT, true, 200f)));
		assertEquals(Trick.NOLLIE_INWARD_HEELFLIP, TrickCatalog.forGesture(shift(Direction.DOWN_RIGHT, true, 0f)));
		// no Shift form: the plain nollie trick
		assertEquals(Trick.NOLLIE, TrickCatalog.forGesture(shift(Direction.DOWN, true, 0f)));
		assertEquals(Trick.NOLLIE_SHOVE_IT, TrickCatalog.forGesture(shift(Direction.LEFT, true, 0f)));
		assertNull(TrickCatalog.forGesture(shift(Direction.DOWN, false, 0f)));
	}

	@Test
	public void theThreeArgumentGestureIsUnmodified()
	{
		assertEquals(false, g(Direction.UP, false, 0f).modified);
		assertEquals(Trick.OLLIE, TrickCatalog.forGesture(g(Direction.UP, false, 0f)));
	}

	@Test
	public void aShiftReflickStillUpgradesAKickflip()
	{
		assertEquals(Trick.DOUBLE_KICKFLIP, TrickCatalog.upgrade(Trick.KICKFLIP, shift(Direction.UP_LEFT, false, 0f)));
		assertNull(TrickCatalog.upgrade(Trick.HARDFLIP, shift(Direction.UP_LEFT, false, 0f)));
	}
}
