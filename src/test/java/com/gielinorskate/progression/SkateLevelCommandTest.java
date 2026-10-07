package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SkateLevelCommandTest
{
	private static SkateLevelCommand parse(String... args)
	{
		return SkateLevelCommand.parse(args);
	}

	@Test
	public void noArgumentsShowsTheLevel()
	{
		assertEquals(SkateLevelCommand.Kind.SHOW, parse().kind);
		assertEquals(SkateLevelCommand.Kind.SHOW, SkateLevelCommand.parse(null).kind);
	}

	@Test
	public void aLevelFromOneTo99SetsIt()
	{
		assertEquals(SkateLevelCommand.Kind.SET, parse("1").kind);
		assertEquals(1, parse("1").level);
		assertEquals(70, parse(" 70 ").level);
		assertEquals(99, parse("99").level);
	}

	@Test
	public void anythingElseIsInvalid()
	{
		for (String bad : new String[]{"0", "100", "-5", "ten", "", "7.5", "99999999999"})
		{
			assertEquals(bad, SkateLevelCommand.Kind.INVALID, parse(bad).kind);
		}
		assertEquals(SkateLevelCommand.Kind.INVALID, parse("5", "6").kind);
	}

	@Test
	public void describeNamesLevelAndXp()
	{
		assertEquals("Skating level 70 (737,627 XP).", SkateLevelCommand.describe(70, 737_627));
	}
}
