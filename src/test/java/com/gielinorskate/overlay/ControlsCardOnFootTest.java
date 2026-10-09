package com.gielinorskate.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.Collectors;
import org.junit.Test;

public class ControlsCardOnFootTest
{
	private static ControlsCardOverlay.Spec spec(boolean controller, boolean onFoot, String boardKey)
	{
		return new ControlsCardOverlay.Spec("Space", false, true, "right", false, false, controller, onFoot,
			boardKey, com.gielinorskate.controller.PadPreset.skate3());
	}

	private static String text(ControlsCardOverlay.Spec spec)
	{
		return ControlsCardOverlay.rows(spec).stream().map(r -> r.text).collect(Collectors.joining("\n"));
	}

	@Test
	public void onFootTheCardListsTheWalkingControls()
	{
		String page = text(spec(false, true, "G"));
		assertTrue(page, page.contains("On foot"));
		assertTrue(page.contains("WASD: walk"));
		assertTrue(page.contains("Shift: sprint"));
		assertTrue(page.contains("Space: jump"));
		assertTrue(page.contains("Q / E: drop the board"));
		assertTrue(page.contains("G: get on"));
		assertTrue(page.contains("Board far away: G calls it back"));
		assertFalse(page.contains("hold G"));
		assertTrue(page.contains("Esc: stop skating"));
		assertTrue(page.contains("H: hide"));
		assertTrue(page.contains("Middle mouse drag: turn the camera"));
		assertFalse(page.contains("flick"));
	}

	@Test
	public void onFootWithAPadTheButtonsAreDrawn()
	{
		String page = text(spec(true, true, "F"));
		assertTrue(page, page.contains("{LS}: walk"));
		assertTrue(page.contains("Hold {A}: sprint"));
		assertTrue(page.contains("{X}: jump"));
		assertTrue(page.contains("{LT} / {RT}: drop the board"));
		assertTrue(page.contains("{Y}: get on"));
		assertTrue(page.contains("{START}: stop skating"));
		assertFalse("no keyboard keys", page.contains("WASD"));
	}

	@Test
	public void theBoardBasicsSayHowToStepOff()
	{
		assertTrue(text(spec(false, false, "G")).contains("G: step off and carry the board"));
		assertTrue(text(spec(true, false, "F")).contains("{Y}: step off and carry the board"));
	}

	@Test
	public void onFootIsPartOfTheSpecsEquality()
	{
		assertNotEquals(spec(false, false, "F"), spec(false, true, "F"));
		assertNotEquals(spec(false, true, "F"), spec(false, true, "G"));
	}

	@Test
	public void theOnFootPageFitsTheFixedModeRegion()
	{
		int[] r = ControlsCardOverlay.region(new HudLayout(4, 4, 512, 334, 765, 503, HudLayout.NO_OBSTACLE));
		Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			for (boolean controller : new boolean[]{false, true})
			{
				for (boolean onFoot : new boolean[]{false, true})
				{
					ControlsCardOverlay.Card card = ControlsCardOverlay.fit(g,
						ControlsCardOverlay.rows(spec(controller, onFoot, "F")), r[2], r[3]);
					assertTrue(card.width <= r[2]);
					assertTrue(card.height <= r[3]);
				}
			}
		}
		finally
		{
			g.dispose();
		}
	}
}
