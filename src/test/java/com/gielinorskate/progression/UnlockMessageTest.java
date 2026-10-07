package com.gielinorskate.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Level-up lines naming the designs just unlocked, worded from the manifest. */
public class UnlockMessageTest
{
	private static final BoardDesigns DESIGNS = BoardDesigns.bundled();

	@Test
	public void namesTheNewDesigns()
	{
		assertNull(ProgressionService.unlockMessage(Collections.emptyList()));
		assertEquals("New board design unlocked: Rune deck. Pick it in the RuneSkate side panel.",
			ProgressionService.unlockMessage(Arrays.asList(DESIGNS.byId("RUNE"))));
		assertEquals("New board designs unlocked: Rune grip, deck and wheels. Pick them in the RuneSkate side panel.",
			ProgressionService.unlockMessage(DESIGNS.unlockedBetween(49, 50)));
		assertEquals("New board designs unlocked: Bandos, Armadyl, Guthix, Zamorak and Saradomin grip, deck and "
			+ "wheels. Pick them in the RuneSkate side panel.",
			ProgressionService.unlockMessage(DESIGNS.unlockedBetween(69, 70)));
		assertEquals("New board designs unlocked: Torva grip, deck and wheels. Pick them in the RuneSkate side panel.",
			ProgressionService.unlockMessage(DESIGNS.unlockedBetween(98, 99)));
	}

	@Test
	public void severalLevelsAtOnceNameEachSet()
	{
		assertEquals("New board designs unlocked: Iron grip, deck and wheels; Steel grip, deck and wheels. Pick them "
			+ "in the RuneSkate side panel.", ProgressionService.unlockMessage(DESIGNS.unlockedBetween(5, 25)));
	}

	@Test
	public void mixedPartsAreNamedAsTheyAre()
	{
		assertEquals("New board designs unlocked: Rune deck; Dragon wheels. Pick them in the RuneSkate side panel.",
			ProgressionService.unlockMessage(Arrays.asList(DESIGNS.byId("RUNE"), DESIGNS.byId("WHEELS_DRAGON"))));
	}
}
