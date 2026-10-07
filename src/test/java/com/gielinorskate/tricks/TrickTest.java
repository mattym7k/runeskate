package com.gielinorskate.tricks;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrickTest
{
	@Test
	public void ollieIsAFlatPointPop()
	{
		assertEquals(TrickKind.POP, Trick.OLLIE.kind);
		assertEquals(100, Trick.OLLIE.points);
		assertEquals(0f, Trick.OLLIE.rollTurns, 0f);
		assertEquals(0f, Trick.OLLIE.yawTurns, 0f);
		assertEquals(0f, Trick.OLLIE.duration, 0f);
	}

	@Test
	public void treFlipRotatesOnBothAxes()
	{
		assertEquals(TrickKind.FLIP, Trick.TRE_FLIP.kind);
		assertEquals(900, Trick.TRE_FLIP.points);
		assertEquals(1f, Trick.TRE_FLIP.rollTurns, 0f);
		assertEquals(1f, Trick.TRE_FLIP.yawTurns, 0f);
		assertEquals(0.45f, Trick.TRE_FLIP.duration, 0f);
	}

	@Test
	public void grabsAndManualsAndGrindsScorePerSecond()
	{
		assertEquals(TrickKind.GRAB, Trick.INDY.kind);
		assertEquals(TrickKind.MANUAL, Trick.NOSE_MANUAL.kind);
		assertEquals(TrickKind.GRIND, Trick.CROOKED.kind);
	}

	@Test
	public void nollieShoveItsAndNollie360FlipSpinLikeTheirRegularForms()
	{
		assertEquals("Nollie Shove-it", Trick.NOLLIE_SHOVE_IT.displayName);
		assertEquals(TrickKind.FLIP, Trick.NOLLIE_SHOVE_IT.kind);
		assertEquals(300, Trick.NOLLIE_SHOVE_IT.points);
		assertEquals(0.5f, Trick.NOLLIE_SHOVE_IT.yawTurns, 0f);
		assertEquals(0.30f, Trick.NOLLIE_SHOVE_IT.duration, 0f);
		assertEquals("Nollie FS Shove-it", Trick.NOLLIE_FS_SHOVE_IT.displayName);
		assertEquals(-0.5f, Trick.NOLLIE_FS_SHOVE_IT.yawTurns, 0f);
		assertEquals("Nollie 360 Flip", Trick.NOLLIE_TRE_FLIP.displayName);
		assertEquals(1000, Trick.NOLLIE_TRE_FLIP.points);
		assertEquals(1f, Trick.NOLLIE_TRE_FLIP.rollTurns, 0f);
		assertEquals(1f, Trick.NOLLIE_TRE_FLIP.yawTurns, 0f);
		assertEquals(0.45f, Trick.NOLLIE_TRE_FLIP.duration, 0f);
	}

	private static void assertFlip(Trick t, String name, int points, float roll, float yaw, float pitch, float body)
	{
		assertEquals(name, t.displayName);
		assertEquals(TrickKind.FLIP, t.kind);
		assertEquals(points, t.points);
		assertEquals(roll, t.rollTurns, 0f);
		assertEquals(yaw, t.yawTurns, 0f);
		assertEquals(pitch, t.pitchTurns, 0f);
		assertEquals(body, t.bodyYawTurns, 0f);
	}

	@Test
	public void shiftTricksRotateAsTheRealOnesDo()
	{
		// impossible: the board wraps end over end around the back foot (nose up and over)
		assertFlip(Trick.IMPOSSIBLE, "Impossible", 450, 0f, 0f, 1f, 0f);
		// hardflip = kickflip + frontside shove; inward heel = heelflip + backside shove
		assertFlip(Trick.HARDFLIP, "Hardflip", 500, 1f, -0.5f, 0f, 0f);
		assertFlip(Trick.INWARD_HEELFLIP, "Inward Heelflip", 500, -1f, 0.5f, 0f, 0f);
		// bigspin: a 360 shove-it with the body turning 180 the same way
		assertFlip(Trick.BIGSPIN, "Bigspin", 550, 0f, 1f, 0f, 0.5f);
		assertFlip(Trick.FS_BIGSPIN, "FS Bigspin", 550, 0f, -1f, 0f, -0.5f);
		assertFlip(Trick.NOLLIE_HARDFLIP, "Nollie Hardflip", 550, 1f, -0.5f, 0f, 0f);
		assertFlip(Trick.NOLLIE_INWARD_HEELFLIP, "Nollie Inward Heelflip", 550, -1f, 0.5f, 0f, 0f);
	}

	@Test
	public void triplesFlipThreeTimes()
	{
		assertEquals("Triple Kickflip", Trick.TRIPLE_KICKFLIP.displayName);
		assertEquals(TrickKind.FLIP, Trick.TRIPLE_KICKFLIP.kind);
		assertEquals(1300, Trick.TRIPLE_KICKFLIP.points);
		assertEquals(3f, Trick.TRIPLE_KICKFLIP.rollTurns, 0f);
		assertEquals(0.65f, Trick.TRIPLE_KICKFLIP.duration, 0f);
		assertEquals("Triple Heelflip", Trick.TRIPLE_HEELFLIP.displayName);
		assertEquals(1300, Trick.TRIPLE_HEELFLIP.points);
		assertEquals(-3f, Trick.TRIPLE_HEELFLIP.rollTurns, 0f);
		assertEquals(0.65f, Trick.TRIPLE_HEELFLIP.duration, 0f);
	}

	@Test
	public void olderTricksHaveNoPitchOrBodyTurn()
	{
		assertEquals(0f, Trick.KICKFLIP.pitchTurns, 0f);
		assertEquals(0f, Trick.SHOVE_IT_360.bodyYawTurns, 0f);
	}
}
