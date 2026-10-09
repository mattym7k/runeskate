package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClutterNamesTest
{
	@Test
	public void plantsAndCropsAreRiddenThrough()
	{
		for (String n : new String[]{"Plant", "Flowers", "Bush", "Bushes", "Fern", "Long grass", "Shrub", "Weeds",
			"Mushrooms", "Crops", "Hay bales", "Reeds", "Daisies", "Cabbage", "Flax", "Potatoes", "Sapling",
			"Tree roots", "Thistle", "Nettles", "Red flower"})
		{
			assertTrue(n, ObjectNames.passByName(n));
		}
	}

	@Test
	public void solidThingsAndLookalikeWordsAreNot()
	{
		for (String n : new String[]{"Rocks", "Boulder", "Tree", "Crate", "Fence", "Planter", "Flowerpot stand",
			"Grassland sign", "Haystack", "Weedkiller", "", null})
		{
			assertFalse(String.valueOf(n), ObjectNames.passByName(n));
		}
	}

	@Test
	public void compoundAndMoreVegetationNamesAreRiddenThrough()
	{
		// whole-word matching missed these: the plant word is glued into a longer word, or was not listed
		for (String n : new String[]{"Rosebush", "Tea bush", "Shrubbery", "Thick vines", "Vines", "Leafy plant",
			"Fallen leaves", "Cactus", "Tall grass", "Ivy", "Lily", "Lilypad", "Bamboo", "Sunflowers", "Tulips",
			"Poppies", "Daisy", "Bramble", "Brambles", "Fungus", "Seaweed", "Kelp", "Foliage", "Herbs", "Onions",
			"Wheat", "Sweetcorn", "Marigolds", "Undergrowth", "Bluebells", "Mossy roots", "Treeroot", "Bullrushes",
			"Wildflowers", "Hay bales", "Jungle bush", "Dead plant", "Small fern", "Snape grass", "Cabbages",
			"Lavender", "Thornbush", "Creeper", "Flowerbed", "Vegetable patch", "Herb patch"})
		{
			assertTrue(n, ObjectNames.passByName(n));
		}
	}

	@Test
	public void treesHedgesFurnitureAndLookalikesStaySolid()
	{
		for (String n : new String[]{"Oak tree", "Bushy tree", "Tree stump", "Hedge", "Rose hedge", "Planter",
			"Plant pot", "Potted plant", "Flowerpot", "Rosewood chair", "Flower stall", "Vine-covered wall",
			"Ivy-covered wall", "Infernal altar", "Ravine", "Divine statue", "Vinegar barrel", "Herblore table",
			"Herbiboar", "Street lamp", "Corner shelf", "Ambush point", "Mushroom stall", "Bamboo desk",
			"Fungus-covered cavern wall", "Rose trellis", "Sign", "Vase of flowers", "Mossy rocks", "Shayzien banner",
			"Wooden table", "Willow", "Yew"})
		{
			assertFalse(n, ObjectNames.passByName(n));
		}
	}

	@Test
	public void theVegetationToggleOffMakesPlantsCollideButOpenDoorsStillPass()
	{
		assertTrue(ObjectNames.passThrough("Rosebush", null, true));
		assertTrue(ObjectNames.passThrough("Thick vines", new String[]{"Cut"}, true));
		assertFalse(ObjectNames.passThrough("Rosebush", null, false));
		assertFalse(ObjectNames.passThrough("Thick vines", new String[]{"Cut"}, false));
		assertTrue(ObjectNames.passThrough("Door", new String[]{"Close"}, false));
		assertFalse(ObjectNames.passThrough("Oak tree", null, true));
	}

	@Test
	public void openDoorsAndGatesPassClosedOnesDoNot()
	{
		assertTrue(ObjectNames.isOpenDoor("Door", new String[]{"Close", null, null}));
		assertTrue(ObjectNames.isOpenDoor("Large door", new String[]{null, "close"}));
		assertTrue(ObjectNames.isOpenDoor("Gate", new String[]{"Close"}));
		assertFalse(ObjectNames.isOpenDoor("Door", new String[]{"Open"}));
		assertFalse(ObjectNames.isOpenDoor("Gate", null));
		assertFalse(ObjectNames.isOpenDoor("Chest", new String[]{"Close"}));
		assertFalse(ObjectNames.isOpenDoor(null, new String[]{"Close"}));
	}

	@Test
	public void passThroughCombinesPlantsAndOpenDoors()
	{
		assertTrue(ObjectNames.passThrough("Bush", null, true));
		assertTrue(ObjectNames.passThrough("Door", new String[]{"Close"}, true));
		assertFalse(ObjectNames.passThrough("Door", new String[]{"Open"}, true));
		assertFalse(ObjectNames.passThrough("Rocks", new String[]{"Mine"}, true));
	}

	/** Stand-in for an object definition with an optional impostor. */
	private static final class Def
	{
		final String name;
		final Def impostor;
		final boolean hasImpostors;

		Def(String name, boolean hasImpostors, Def impostor)
		{
			this.name = name;
			this.hasImpostors = hasImpostors;
			this.impostor = impostor;
		}
	}

	@Test
	public void impostorsAreResolvedBeforeReadingTheName()
	{
		Def flowers = new Def("Flowers", false, null);
		Def wrapper = new Def("null", true, flowers);
		Def resolved = ObjectNames.resolve(wrapper, d -> d.hasImpostors, d -> d.impostor);
		assertEquals("Flowers", resolved.name);
		assertTrue(ObjectNames.passByName(resolved.name));
		assertFalse("the wrapper's own name says nothing", ObjectNames.passByName(wrapper.name));
		Def plain = new Def("Rocks", false, null);
		assertEquals("Rocks", ObjectNames.resolve(plain, d -> d.hasImpostors, d -> d.impostor).name);
		// a varbit-driven object currently showing nothing resolves to nothing
		assertNull(ObjectNames.resolve(new Def("null", true, null), d -> d.hasImpostors, d -> d.impostor));
		assertNull(ObjectNames.resolve((Def) null, d -> d.hasImpostors, d -> d.impostor));
	}
}
