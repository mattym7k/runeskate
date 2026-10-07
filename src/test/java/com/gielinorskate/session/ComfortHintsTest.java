package com.gielinorskate.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComfortHintsTest
{
	/** The client's default idle timeout: 5 minutes of 20 ms client ticks. */
	private static final int TIMEOUT = 15000;

	@Test
	public void idleWarningFiresOnceAMinuteBeforeTheLogout()
	{
		ComfortHints.IdleWarning w = new ComfortHints.IdleWarning();
		assertFalse(w.update(100, TIMEOUT));
		assertFalse(w.isWarning());
		// 4 minutes idle (12000 ticks): one minute (3000 ticks) before the 5 minute logout
		assertFalse(w.update(12000, TIMEOUT));
		assertTrue("crossing: warn once", w.update(12001, TIMEOUT));
		assertTrue(w.isWarning());
		assertFalse("only once", w.update(12500, TIMEOUT));
		assertTrue(w.isWarning());
		// input seen: idle drops, the warning clears and can fire again
		assertFalse(w.update(5, TIMEOUT));
		assertFalse(w.isWarning());
		assertTrue(w.update(13000, TIMEOUT));
	}

	@Test
	public void idleWarningCanBeReset()
	{
		ComfortHints.IdleWarning w = new ComfortHints.IdleWarning();
		assertTrue(w.update(14000, TIMEOUT));
		w.reset();
		assertFalse(w.isWarning());
		assertTrue("a new session warns again", w.update(14000, TIMEOUT));
	}

	@Test
	public void idleMinutesRoundDown()
	{
		assertEquals(4, ComfortHints.idleMinutes(12001));
		assertEquals(4, ComfortHints.idleMinutes(14999));
	}

	@Test
	public void edgeHintFadesInWithinSixTiles()
	{
		assertEquals(0f, ComfortHints.edgeHintAlpha(Float.POSITIVE_INFINITY), 0f);
		assertEquals(0f, ComfortHints.edgeHintAlpha(6f), 0f);
		assertEquals(0.5f, ComfortHints.edgeHintAlpha(4.5f), 1e-6f);
		assertEquals(1f, ComfortHints.edgeHintAlpha(3f), 0f);
		assertEquals(1f, ComfortHints.edgeHintAlpha(-1f), 0f);
	}

	@Test
	public void roomTipBelowTwentyTiles()
	{
		assertTrue(ComfortHints.needsRoomTip(19.9f));
		assertFalse(ComfortHints.needsRoomTip(20f));
		assertFalse(ComfortHints.needsRoomTip(Float.POSITIVE_INFINITY));
	}

	@Test
	public void stumbleFlashHoldsThenFades()
	{
		assertEquals(0f, ComfortHints.stumbleFlashAlpha(-1f), 0f);
		assertEquals(1f, ComfortHints.stumbleFlashAlpha(0f), 0f);
		assertEquals(1f, ComfortHints.stumbleFlashAlpha(0.5f), 0f);
		assertTrue(ComfortHints.stumbleFlashAlpha(0.65f) < 1f);
		assertEquals(0f, ComfortHints.stumbleFlashAlpha(0.8f), 0f);
	}

	@Test
	public void poisonAndVenomHaveTheirOwnMessage()
	{
		String poison = ComfortHints.damageMessage(net.runelite.api.HitsplatID.POISON);
		assertTrue(poison, poison.toLowerCase().contains("poison"));
		String venom = ComfortHints.damageMessage(net.runelite.api.HitsplatID.VENOM);
		assertTrue(venom, venom.toLowerCase().contains("venom"));
		assertEquals("You took damage, so you hop off your board.",
			ComfortHints.damageMessage(net.runelite.api.HitsplatID.DAMAGE_ME));
	}

	@Test
	public void welcomeNamesTheKeyAndTheSidebar()
	{
		String w = ComfortHints.welcome("Ctrl+K");
		assertTrue(w.contains("press Ctrl+K to skate"));
		assertTrue(w.contains("sidebar"));
	}

	@Test
	public void keyboardTricksMoveASpaceManualToC()
	{
		int space = java.awt.event.KeyEvent.VK_SPACE;
		assertEquals("C", ComfortHints.manualKeyLabel("Space", space, true));
		assertEquals("Space", ComfortHints.manualKeyLabel("Space", space, false));
		assertEquals(null, ComfortHints.manualKeyNote("Space", space, false));
		assertEquals("Keyboard tricks use Space, so your wheelie (manual) key is C while skating.",
			ComfortHints.manualKeyNote("Space", space, true));
		assertTrue(ComfortHints.manualKeyNote("W", java.awt.event.KeyEvent.VK_W, false).contains("using Space instead"));
	}

	private static java.util.Map<String, Integer> keys(Object... nameCode)
	{
		java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
		for (int i = 0; i < nameCode.length; i += 2)
		{
			m.put((String) nameCode[i], (Integer) nameCode[i + 1]);
		}
		return m;
	}

	@Test
	public void aManualKeyOnTheBoardKeyNamesBothSettings()
	{
		int f = java.awt.event.KeyEvent.VK_F;
		assertEquals("Your Board on/off key (F) is also your Wheelie (manual) key: F only steps on and off the "
				+ "board while skating. Change one of them in the RuneSkate settings.",
			ComfortHints.boardKeyClashNote("F", f,
				keys("Wheelie (manual) key", f, "Brake key", java.awt.event.KeyEvent.VK_B)));
	}

	@Test
	public void everyClashingSettingIsNamedInOneNote()
	{
		int g = java.awt.event.KeyEvent.VK_G;
		String note = ComfortHints.boardKeyClashNote("G", g,
			keys("Wheelie (manual) key", g, "Brake key", g, "Pad A key", java.awt.event.KeyEvent.VK_OPEN_BRACKET));
		assertTrue(note, note.contains("is also your Wheelie (manual) key and Brake key:"));
		assertFalse(note, note.contains("Pad A key"));
	}

	@Test
	public void noClashOrNoBoardKeyIsNoNote()
	{
		int f = java.awt.event.KeyEvent.VK_F;
		int none = java.awt.event.KeyEvent.VK_UNDEFINED;
		assertEquals(null, ComfortHints.boardKeyClashNote("F", f,
			keys("Wheelie (manual) key", java.awt.event.KeyEvent.VK_SPACE, "Brake key", none)));
		// an unset board key clashes with nothing, even another unset key
		assertEquals(null, ComfortHints.boardKeyClashNote("Not set", none, keys("Brake key", none)));
	}

	@Test
	public void anUnusableBoardKeyIsReplacedByFAndSaysSo()
	{
		int esc = java.awt.event.KeyEvent.VK_ESCAPE;
		int g = java.awt.event.KeyEvent.VK_G;
		int one = java.awt.event.KeyEvent.VK_1;
		assertEquals("G", ComfortHints.boardKeyLabel("G", g, true));
		assertEquals(null, ComfortHints.boardKeyNote("G", g, true));
		assertEquals("F", ComfortHints.boardKeyLabel("Escape", esc, false));
		assertEquals("Your Board on/off key (Escape) is a skate control, so F steps on and off the board instead. "
			+ "Pick another key in the RuneSkate settings.", ComfortHints.boardKeyNote("Escape", esc, false));
		assertEquals("1", ComfortHints.boardKeyLabel("1", one, false));
		assertEquals("Keyboard tricks use 1, so F steps on and off the board instead. "
			+ "Pick another key in the RuneSkate settings.", ComfortHints.boardKeyNote("1", one, true));
		// unset: off, nothing to say
		assertEquals(null, ComfortHints.boardKeyNote("Not set", java.awt.event.KeyEvent.VK_UNDEFINED, false));
	}

	@Test
	public void aKeySettingOnAPadKeySaysWhichButtonItTakes()
	{
		java.util.Map<String, Integer> taken = keys("Wheelie (manual) key", java.awt.event.KeyEvent.VK_SPACE,
			"Brake key", java.awt.event.KeyEvent.VK_HOME);
		assertEquals(java.util.Collections.singletonList("Your Brake key (Home) is the key the controller's D-pad "
				+ "left button sends, so that button does nothing in Controller mode. Pick another key in the RuneSkate "
				+ "settings."), ComfortHints.padKeyNotes(taken));
		assertTrue(ComfortHints.padKeyNotes(keys("Brake key", java.awt.event.KeyEvent.VK_B)).isEmpty());
	}
}
