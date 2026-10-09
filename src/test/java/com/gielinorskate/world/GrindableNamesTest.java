package com.gielinorskate.world;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GrindableNamesTest
{
	@Test
	public void fencesRailsAndFurnitureLookGrindable()
	{
		String[] yes = {
			"Wooden fence", "Fence", "Railing", "Railings", "Bench", "Market stall", "Table", "Crate", "Crates",
			"Low wall", "Wall", "Banister", "Balustrade", "Barrier", "Ledge", "Planter", "Log", "Logs", "Trough",
			"Counter", "Barricade", "Bollard", "Pipe", "Beam", "Plank", "Gate", "Hurdle", "Picket fence",
			"Fences", "Benches", "Tables", "WOODEN FENCE", "Rail", "Bank table", "Fence-gate",
		};
		for (String n : yes)
		{
			assertTrue(n, ObjectNames.looksGrindable(n));
		}
	}

	@Test
	public void stairsDoorsTreesRocksAndNonWordsDoNot()
	{
		String[] no = {
			"Staircase", "Stairs", "Steps", "Ladder", "Door", "Oak tree", "Rocks", "Rock", "Bush", "Tree",
			"null", "NULL", "", "   ", null, "Trellis", "Tablet", "Fenceless",
			"Stone steps", "Wall ladder", "Railing door", "Fallen tree log", "Crate of rocks", "Bookcase",
		};
		for (String n : no)
		{
			assertFalse(String.valueOf(n), ObjectNames.looksGrindable(n));
		}
	}
}
