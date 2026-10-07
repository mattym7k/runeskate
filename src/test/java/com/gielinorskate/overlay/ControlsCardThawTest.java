package com.gielinorskate.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.PadPreset;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.Collectors;
import org.junit.Test;

/** The controls card with the Tony Hawk's American Wasteland preset selected. */
public class ControlsCardThawTest
{
	private static ControlsCardOverlay.Spec spec(boolean learned, boolean onFoot)
	{
		return new ControlsCardOverlay.Spec("Space", false, true, "right", false, learned, true, onFoot, "F",
			PadPreset.thaw());
	}

	private static String text(ControlsCardOverlay.Spec spec)
	{
		return ControlsCardOverlay.rows(spec).stream().map(r -> r.text).collect(Collectors.joining("\n"));
	}

	@Test
	public void theCardShowsTheButtonTricks()
	{
		String card = text(spec(false, false));
		assertTrue(card, card.contains("{DUP}: hide"));
		assertTrue(card, card.contains("{LS} up: push"));
		assertTrue(card, card.contains("Hold {A}, let go: ollie"));
		assertTrue(card, card.contains("{LB}/{RB}: hard tricks"));
		assertTrue(card, card.contains("{X}: left kickflip, right heelflip"));
		assertTrue(card, card.contains("{B} held: left melon"));
		assertTrue(card, card.contains("{Y} near a rail: grind"));
		assertTrue(card, card.contains("{LS} up, then down: manual"));
		assertTrue(card, card.contains("{RS}: camera"));
		assertTrue(card, card.contains("{BACK}: get up"));
		assertTrue(card, card.contains("{START}: stop"));
		assertFalse(card, card.contains("flick"));
		assertTrue(card, card.contains(ControlsCardOverlay.TRICK_BOOK_LINE));
		assertTrue(ControlsCardOverlay.rows(spec(false, false)).size() <= 9);
	}

	@Test
	public void onFootTheRightStickTurnsTheCamera()
	{
		String card = text(spec(true, true));
		assertTrue(card, card.contains("{RS}: turn the camera"));
		assertTrue(card, card.contains("{A}: jump"));
		assertTrue(card, card.contains("{B}: drop the board"));
		assertTrue(card, card.contains("{Y}: get on"));
	}

	@Test
	public void theCardFitsTheFixedModeRegion()
	{
		int[] r = ControlsCardOverlay.region(4, 4, 512, 334, 765, 503);
		Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			for (boolean learned : new boolean[]{false, true})
			{
				for (boolean onFoot : new boolean[]{false, true})
				{
					ControlsCardOverlay.Card card = ControlsCardOverlay.fit(g, ControlsCardOverlay.rows(
						spec(learned, onFoot)), r[2], r[3]);
					String what = "learned " + learned + " on foot " + onFoot;
					assertTrue(what + " width " + card.width + " > " + r[2], card.width <= r[2]);
					assertTrue(what + " height " + card.height + " > " + r[3], card.height <= r[3]);
				}
			}
		}
		finally
		{
			g.dispose();
		}
	}
}
