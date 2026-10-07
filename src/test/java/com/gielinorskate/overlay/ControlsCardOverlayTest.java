package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.tricks.Gesture.Direction;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ControlsCardOverlayTest
{
	private static ControlsCardOverlay.Spec spec()
	{
		return new ControlsCardOverlay.Spec("Space", false, true, "right", false, false);
	}

	private static String text(ControlsCardOverlay.Spec spec)
	{
		return ControlsCardOverlay.rows(spec).stream().map(r -> r.text).collect(Collectors.joining("\n"));
	}

	@Test
	public void theCardSaysHowToPushJumpBrakeAndStop()
	{
		String card = text(spec());
		assertTrue(card.contains("W: push"));
		assertTrue(card.contains("A/D: steer"));
		assertTrue(card.contains("Shift alone: brake"));
		assertTrue(card.contains("with A/D: tight turn"));
		assertTrue(card.contains("S: crouch"));
		assertTrue(card.contains("R: back on after a bail"));
		assertTrue(card.contains("F: step off and carry the board"));
		assertTrue(card.contains("Esc: stop skating"));
		assertTrue(card.contains("Push and jump once"));
		assertTrue(card.contains("H: hide"));
	}

	@Test
	public void theCardHasNoPages()
	{
		for (boolean controller : new boolean[]{false, true})
		{
			for (boolean onFoot : new boolean[]{false, true})
			{
				String card = text(new ControlsCardOverlay.Spec("Space", true, true, "right", false, false, controller,
					onFoot, "F"));
				assertFalse(card, card.contains("next page"));
				assertFalse(card, card.contains("/3)"));
			}
		}
	}

	@Test
	public void theCardPointsToTheTrickBook()
	{
		for (boolean controller : new boolean[]{false, true})
		{
			for (boolean onFoot : new boolean[]{false, true})
			{
				String card = text(new ControlsCardOverlay.Spec("Space", false, true, "right", false, true, controller,
					onFoot, "F"));
				assertTrue(card, card.contains("Full trick list: Trick Book in the side panel"));
			}
		}
	}

	@Test
	public void theCardCoversAirGrabsGrindsAndManuals()
	{
		String card = text(spec());
		assertTrue(card.contains("In the air: A/D spin"));
		assertTrue(card.contains("Q/E: grab"));
		assertTrue(card.contains("grind"));
		assertTrue(card.contains("Hold Space: manual"));
	}

	@Test
	public void theCardMentionsTheRollingGrab()
	{
		assertTrue(text(spec()).contains("Q/E: grab (rolling too, for style)"));
	}

	@Test
	public void onceLearnedTheCardDropsTheReminder()
	{
		ControlsCardOverlay.Spec learned = new ControlsCardOverlay.Spec("Space", false, true, "right", false, true);
		assertFalse(text(learned).contains("Push and jump once"));
	}

	/** The flick picture drawn before {@code trick} in the card's row of pictures. */
	private static Direction flickFor(ControlsCardOverlay.Spec spec, String trick)
	{
		return ControlsCardOverlay.rows(spec).stream().filter(r -> r.cells != null)
			.flatMap(r -> r.cells.stream()).filter(c -> c.text.equals(trick)).findFirst().get().glyph.direction;
	}

	@Test
	public void theCardDrawsAFlickForTheEssentialTricksOnOneLine()
	{
		List<ControlsCardOverlay.Row> rows = ControlsCardOverlay.rows(spec());
		ControlsCardOverlay.Row flicks = rows.stream().filter(r -> r.cells != null).findFirst().get();
		assertEquals(4, flicks.cells.size());
		assertEquals(Direction.UP, flickFor(spec(), "ollie"));
		assertEquals(Direction.UP_LEFT, flickFor(spec(), "kickflip"));
		assertEquals(Direction.UP_RIGHT, flickFor(spec(), "heelflip"));
		assertEquals(Direction.LEFT, flickFor(spec(), "shove-it"));
	}

	@Test
	public void mirroredFlicksSwapThePictureButNotTheTrick()
	{
		ControlsCardOverlay.Spec mirrored = new ControlsCardOverlay.Spec("Space", false, true, "right", true, false);
		assertEquals(Direction.UP_RIGHT, flickFor(mirrored, "kickflip"));
		assertEquals(Direction.RIGHT, flickFor(mirrored, "shove-it"));
	}

	@Test
	public void keyboardOnlyHasNoFlickPictures()
	{
		ControlsCardOverlay.Spec kb = new ControlsCardOverlay.Spec("C", true, false, "right", false, true);
		assertTrue(ControlsCardOverlay.rows(kb).stream().allMatch(r -> r.cells == null && r.glyph == null));
	}

	@Test
	public void keyboardModeExplainsTheKeysAndTheManualKey()
	{
		ControlsCardOverlay.Spec kb = new ControlsCardOverlay.Spec("C", true, false, "right", false, true);
		String card = text(kb);
		assertTrue(card, card.contains("Space: ollie   1: kickflip   2: heelflip"));
		assertTrue(card.contains("Alt: nollie"));
		assertFalse(card.contains("mouse button"));
		assertTrue(card.contains("Hold C: manual"));
		assertTrue(card.contains("H: hide"));
	}

	private static ControlsCardOverlay.Spec pad(boolean mirror)
	{
		return new ControlsCardOverlay.Spec("Space", false, true, "right", mirror, false, true);
	}

	@Test
	public void aCustomPresetsCardNamesItsButtonsAndTheKeysForWhatTheyLeaveOut()
	{
		com.gielinorskate.controller.PadPreset p = new com.gielinorskate.controller.PadPreset()
			.with(com.gielinorskate.controller.PadButton.LB, new com.gielinorskate.controller.PadPreset.Binding(
				com.gielinorskate.controller.PadAction.BRAKE, com.gielinorskate.controller.PadAction.JUMP))
			.with(com.gielinorskate.controller.PadButton.L3, new com.gielinorskate.controller.PadPreset.Binding(
				com.gielinorskate.controller.PadAction.OLLIE, com.gielinorskate.controller.PadAction.CAMERA_ORBIT));
		ControlsCardOverlay.Spec board = new ControlsCardOverlay.Spec("Space", false, true, "right", false, true,
			true, false, "F", p);
		String card = text(board);
		assertTrue(card, card.contains("W: push"));
		assertTrue(card, card.contains("{LB}: brake"));
		assertTrue(card, card.contains("{L3} held, let go: ollie"));
		assertFalse(card, card.contains("Shift (hard tricks)"));
		assertTrue(card, card.contains("Q / E: grab"));
		assertTrue(card, card.contains("H: hide"));
		assertTrue(card, card.contains("R: get up after a bail"));
		ControlsCardOverlay.Spec foot = new ControlsCardOverlay.Spec("Space", false, true, "right", false, true,
			true, true, "F", p);
		String walk = text(foot);
		assertTrue(walk, walk.contains("{LB}: jump"));
		assertTrue(walk, walk.contains("Hold {L3} and move {RS}: turn the camera"));
		assertTrue(walk, ControlsCardOverlay.rows(foot).size() <= 9);
		assertFalse(board.equals(new ControlsCardOverlay.Spec("Space", false, true, "right", false, true, true, false,
			"F")));
	}

	@Test
	public void controllerModeNamesThePadsButtons()
	{
		String card = text(pad(false));
		assertTrue(card.contains("{A} or {X}: push"));
		assertTrue(card.contains("{LS} left/right: steer"));
		assertTrue(card.contains("{RS} pull down, then flick (no button)"));
		assertTrue(card.contains("{B}: brake"));
		assertTrue(card.contains("{LB}/{RB}: Shift (hard tricks)"));
		assertTrue(card.contains("{BACK}: get up"));
		assertTrue(card.contains("{START}: stop skating"));
		assertTrue(card.contains("{DUP}: hide"));
		assertTrue(card.contains("{LT} / {RT}: grab"));
		assertTrue(card.contains("Grab or {LB}/{RB} + {LS} up/down: front/back flip"));
		assertTrue(card.contains("{RS} small tilt up, held: manual"));
		assertEquals(Direction.UP_LEFT, flickFor(pad(false), "kickflip"));
		assertFalse("no keyboard keys", card.contains("W: push"));
		assertEquals(Direction.UP_RIGHT, flickFor(pad(true), "kickflip"));
	}

	@Test
	public void controllerModeIsPartOfTheSpecsEquality()
	{
		assertFalse(pad(false).equals(spec()));
		assertEquals(spec(), new ControlsCardOverlay.Spec("Space", false, true, "right", false, false, false));
		assertEquals(spec().hashCode(),
			new ControlsCardOverlay.Spec("Space", false, true, "right", false, false, false).hashCode());
	}

	@Test
	public void flickButtonIsNamed()
	{
		ControlsCardOverlay.Spec left = new ControlsCardOverlay.Spec("Space", false, true, "left", false, false);
		assertTrue(text(left).contains("hold the left mouse button"));
	}

	@Test
	public void fixedModeRegionStaysInTheViewportAboveTheTrickStack()
	{
		// fixed mode: a 512 x 334 viewport at (4, 4) on a 765 x 503 canvas, the side panel to its right
		int[] r = ControlsCardOverlay.region(4, 4, 512, 334, 765, 503);
		HudLayout hud = HudLayout.of(4, 4, 512, 334, 765, 503);
		assertTrue("left " + r[0], r[0] >= 4);
		assertTrue("top " + r[1], r[1] >= 4);
		assertTrue("right " + (r[0] + r[2]), r[0] + r[2] <= 4 + 512);
		assertTrue("bottom " + (r[1] + r[3]) + " stack top " + hud.stackTopY, r[1] + r[3] <= hud.stackTopY);
	}

	@Test
	public void everyModeIsScaledToFitTheFixedModeRegion()
	{
		int[] r = ControlsCardOverlay.region(4, 4, 512, 334, 765, 503);
		Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			for (boolean keyboard : new boolean[]{false, true})
			{
				for (boolean mouse : new boolean[]{false, true})
				{
					if (!keyboard && !mouse)
					{
						continue;
					}
					for (boolean controller : new boolean[]{false, true})
					{
						ControlsCardOverlay.Spec s = new ControlsCardOverlay.Spec("Space", keyboard, mouse, "right",
							false, false, controller);
						ControlsCardOverlay.Card card = ControlsCardOverlay.fit(g, ControlsCardOverlay.rows(s), r[2],
							r[3]);
						String what = "keyboard " + keyboard + " mouse " + mouse + (controller ? " (controller)" : "");
						assertTrue(what + " width " + card.width + " > " + r[2], card.width <= r[2]);
						assertTrue(what + " height " + card.height + " > " + r[3], card.height <= r[3]);
					}
				}
			}
		}
		finally
		{
			g.dispose();
		}
	}

	@Test
	public void largeViewportUsesTheFullSizeFont()
	{
		int[] r = ControlsCardOverlay.region(0, 0, 1600, 900, 1600, 900);
		Graphics2D g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			ControlsCardOverlay.Card card = ControlsCardOverlay.fit(g, ControlsCardOverlay.rows(spec()), r[2], r[3]);
			assertEquals(ControlsCardOverlay.MAX_FONT_SIZE, card.font.getSize());
		}
		finally
		{
			g.dispose();
		}
	}
}
