package com.gielinorskate.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.junit.Test;

/** Layout codes: short, round-trip, and strict about what they accept. */
public class LayoutCodeTest
{
	private static String code(int... bytes)
	{
		byte[] b = new byte[bytes.length];
		for (int i = 0; i < bytes.length; i++)
		{
			b[i] = (byte) bytes[i];
		}
		return "RSK1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static String refusal(String code)
	{
		LayoutCode.Result r = LayoutCode.decode(code);
		assertFalse(code, r.ok());
		assertNull(r.preset);
		return r.error;
	}

	@Test
	public void skate3RoundTripsInAShortVersionedCode()
	{
		String c = LayoutCode.encode(PadPreset.skate3());
		assertTrue(c, c.startsWith("RSK1:"));
		assertTrue(c, c.length() < 80);
		LayoutCode.Result r = LayoutCode.decode(c);
		assertTrue(r.error, r.ok());
		assertNull(r.error);
		assertEquals(PadPreset.skate3(), r.preset);
	}

	@Test
	public void aCustomLayoutWithAnAirActionRoundTrips()
	{
		PadPreset p = PadPreset.skate3()
			.with(PadButton.L3, PadContext.BOARD, PadAction.OLLIE)
			.with(PadButton.DPAD_DOWN, PadContext.FOOT, PadAction.CAMERA_ORBIT)
			.with(PadButton.LB, PadContext.AIR, PadAction.LEAN_FORWARD)
			.with(PadButton.B, PadContext.BOARD, PadAction.NONE);
		assertEquals(p, LayoutCode.decode(LayoutCode.encode(p)).preset);
		assertEquals(new PadPreset(), LayoutCode.decode(LayoutCode.encode(new PadPreset())).preset);
	}

	@Test
	public void spacesAndLineBreaksAroundTheCodeAreIgnored()
	{
		String c = LayoutCode.encode(PadPreset.skate3());
		assertEquals(PadPreset.skate3(), LayoutCode.decode("  " + c + "\r\n").preset);
	}

	@Test
	public void somethingElseIsNotACode()
	{
		assertTrue(refusal("").contains("no layout code"));
		assertTrue(refusal(null).contains("no layout code"));
		assertTrue(refusal("hello").contains("isn't a RuneSkate layout code"));
		assertTrue(refusal("RSK:abc").contains("isn't a RuneSkate layout code"));
	}

	@Test
	public void aNewerVersionIsRefused()
	{
		assertTrue(refusal("RSK2:YQEOAA").contains("newer RuneSkate"));
		assertTrue(refusal("RSK0:YQEOAA").contains("version"));
	}

	@Test
	public void aCodeOverTheSizeLimitIsRefused()
	{
		String c = "RSK1:" + String.join("", Collections.nCopies(600, "A"));
		assertTrue(refusal(c).contains("too long"));
	}

	@Test
	public void damagedCodesAreRefused()
	{
		assertTrue(refusal("RSK1:ab+/").contains("damaged"));
		assertTrue(refusal("RSK1:a b").contains("damaged"));
		// three bytes: not a whole button
		assertTrue(refusal(code('a', 1, 14)).contains("damaged"));
		// a single base64 character is no byte at all
		assertTrue(refusal("RSK1:Y").contains("damaged"));
	}

	@Test
	public void unknownButtonsAndActionsAreRefused()
	{
		assertTrue(refusal(code('Z', 1, 14, 0xFF)).contains("button"));
		assertTrue(refusal(code('a', 99, 14, 0xFF)).contains("doesn't know"));
		assertTrue(refusal(code('a', 1, 99, 0xFF)).contains("doesn't know"));
		assertTrue(refusal(code('a', 1, 14, 99)).contains("doesn't know"));
	}

	@Test
	public void aButtonTwiceIsRefused()
	{
		assertTrue(refusal(code('a', 1, 14, 0xFF, 'a', 3, 0, 0xFF)).contains("A twice"));
	}

	@Test
	public void anActionWhereItCannotBeBoundIsRefused()
	{
		// push on foot, jump on the board, a flip button on foot
		assertTrue(refusal(code('a', 1, 1, 0xFF)).contains("can't be used"));
		assertTrue(refusal(code('a', 15, 0, 0xFF)).contains("can't be used"));
		assertTrue(refusal(code('a', 0, PadAction.FLIP_BUTTON.id, 0xFF)).contains("can't be used"));
		assertTrue(refusal(code('a', 1, 0, 15)).contains("can't be used"));
	}

	@Test
	public void changesListEachButtonAndPlaceThatDiffers()
	{
		assertTrue(LayoutCode.changes(PadPreset.skate3(), PadPreset.skate3()).isEmpty());
		PadPreset p = PadPreset.skate3()
			.with(PadButton.LB, PadContext.BOARD, PadAction.BRAKE)
			.with(PadButton.DPAD_DOWN, PadContext.FOOT, PadAction.JUMP);
		assertEquals(Arrays.asList("LB, on the board: Hard tricks (Shift) to Brake",
			"D-pad down, on foot: Nothing to Jump"), LayoutCode.changes(PadPreset.skate3(), p));
		PadPreset air = PadPreset.skate3().with(PadButton.LT, PadContext.AIR, PadAction.OLLIE);
		assertEquals(Collections.singletonList("LT, in the air: Grab (left hand) to Ollie (hold, let go)"),
			LayoutCode.changes(PadPreset.skate3(), air));
	}
}
