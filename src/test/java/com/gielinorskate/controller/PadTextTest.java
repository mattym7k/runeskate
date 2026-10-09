package com.gielinorskate.controller;

import static org.junit.Assert.*;
import java.awt.event.KeyEvent;
import java.util.*;
import org.junit.Test;

/** The pad tables and layout-code refusals read from text/overlay.properties. */
public class PadTextTest
{
	@Test
	public void everyActionLoadsWithAUniqueIdAndAPlace()
	{
		Set<Integer> ids = new HashSet<>();
		for (PadAction a : PadAction.values())
		{
			assertTrue(a.name(), ids.add(a.id));
			assertFalse(a.name(), a.label.isEmpty());
			assertTrue(a.name(), a.onBoard || a.onFoot);
		}
		assertEquals(0, PadAction.NONE.id);
		assertEquals("Ollie (hold, let go)", PadAction.OLLIE.label);
		assertTrue(PadAction.STOP.onBoard && PadAction.STOP.onFoot);
		assertFalse(PadAction.SPRINT.onBoard);
		assertEquals(PadAction.SPIN_ASSIST, PadAction.forId(21));
	}

	@Test
	public void everyButtonLoadsWithAUniqueIdAndPadKey()
	{
		Set<Character> ids = new HashSet<>();
		Set<Integer> keys = new HashSet<>();
		for (PadButton b : PadButton.values())
		{
			assertTrue(b.name(), ids.add(b.id));
			assertTrue(b.name(), keys.add(b.keyCode));
			assertFalse(b.name(), b.label.isEmpty());
		}
		assertEquals(KeyEvent.VK_F13, PadButton.A.keyCode);
		assertEquals("F13", PadButton.A.keyName);
		assertEquals(KeyEvent.VK_F24, PadButton.R3.keyCode);
		assertEquals('4', PadButton.R3.id);
		assertEquals("Back", PadButton.BACK.label);
		assertEquals(KeyEvent.VK_INSERT, PadButton.DPAD_UP.keyCode);
		assertEquals("Insert", PadButton.DPAD_UP.keyName);
		assertEquals("D-pad up", PadButton.DPAD_UP.label);
	}

	private static String code(int... bytes)
	{
		byte[] b = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++)
			b[i] = (byte) bytes[i];
		return "RSK1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	@Test
	public void everyRefusalReadsInFull()
	{
		char[] big = new char[600];
		Arrays.fill(big, 'A');
		String[][] cases = {
			{"", "There is no layout code to read."},
			{new String(big), "That code is too long to be a RuneSkate layout (over 512 characters)."},
			{"XYZ", "That isn't a RuneSkate layout code: they start with RSK1:"},
			{"RSK2:AA", "That layout code is from a newer RuneSkate. Update the plugin to use it."},
			{"RSK0:AA", "That layout code's version (0) isn't one RuneSkate knows."},
			{"RSK1:!!", "That layout code is damaged: it has characters a code never has."},
			{"RSK1:AAA", "That layout code is damaged: part of it is missing."},
			{code('z', 1, 1, 1), "That layout code names a controller button RuneSkate doesn't know."},
			{code('a', 1, 1, 255, 'a', 1, 1, 255), "That layout code sets A twice."},
			{code('a', 99, 1, 255), "That layout code has an action this version of RuneSkate doesn't know. Update the "
				+ "plugin to use it."},
			{code('a', 1, 1, 255), "That layout code can't be used: A can't do \"Push\" on foot."},
		};
		for (String[] c : cases)
			assertEquals(c[1], LayoutCode.decode(c[0]).error);
	}
}
