package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.LayoutCode;
import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import org.junit.Test;

/** The side panel's controller views, and the preset-aware words of the Trick Book. */
public class ControllerPresetUiTest
{
	private static final PadPreset CUSTOM = PadPreset.skate3()
		.with(PadButton.B, PadContext.BOARD, PadAction.NONE)
		.with(PadButton.LB, new PadPreset.Binding(PadAction.BRAKE, PadAction.JUMP))
		.with(PadButton.RB, new PadPreset.Binding(PadAction.HARD_MODIFIER, PadAction.SPRINT))
		.with(PadButton.DPAD_DOWN, PadContext.FOOT, PadAction.CAMERA_ORBIT);

	private static List<AbstractButton> buttons(java.awt.Container c, List<AbstractButton> out)
	{
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof AbstractButton)
			{
				out.add((AbstractButton) child);
			}
			if (child instanceof java.awt.Container)
			{
				buttons((java.awt.Container) child, out);
			}
		}
		return out;
	}

	private static AbstractButton named(java.awt.Container c, String name)
	{
		return buttons(c, new ArrayList<>()).stream().filter(b -> name.equals(b.getName())).findFirst().get();
	}

	@Test
	public void theNewGlyphsAreTokensToo()
	{
		assertEquals(Arrays.asList(Glyph.L3, " ", Glyph.DDOWN, " ", Glyph.DLEFT, " ", Glyph.DRIGHT, " ", Glyph.R3),
			ControllerGlyphs.split("{L3} {DDOWN} {DLEFT} {DRIGHT} {R3}"));
		assertEquals("d-pad left", Glyph.DLEFT.spoken);
		for (PadButton b : PadButton.values())
		{
			assertEquals(b, PadButton.values()[b.ordinal()]);
			assertTrue(ControllerGlyphs.hasGlyphs(PadWords.token(b)));
		}
	}

	@Test
	public void placeholdersNameThePresetsButtonsOrTheKey()
	{
		assertEquals("{A} or {X}, tap", PadWords.resolve("{@push}, tap", PadPreset.skate3(), "F"));
		assertEquals("Hold {LB} or {RB}", PadWords.resolve("Hold {@mod}", PadPreset.skate3(), "F"));
		assertEquals("Hold {RB}", PadWords.resolve("Hold {@mod}", CUSTOM, "F"));
		assertEquals("Hold {LB}", PadWords.resolve("Hold {@brake}", CUSTOM, "F"));
		assertEquals("W: push", PadWords.resolve("{@push}: push", new PadPreset(), "F"));
		assertEquals("G: get on", PadWords.resolve("{@board}: get on", new PadPreset(), "G"));
		assertEquals("{LT} / {RT}", PadWords.resolve("{@grabs}", PadPreset.skate3(), "F"));
	}

	@Test
	public void theLayoutListGroupsSameButtonsAndWordsBothPlaces()
	{
		Map<String, String> l = PadWords.layout(PadPreset.skate3());
		assertEquals("Push (tap or hold). On foot: sprint (hold)", l.get("{A}"));
		assertEquals("Push (tap or hold). On foot: jump", l.get("{X}"));
		assertTrue(l.get("{LB} {RB}").startsWith("Shift: hard flips"));
		assertEquals("Controls card", l.get("{DUP}"));
		assertFalse(l.containsKey("{L3}"));
		Map<String, String> c = PadWords.layout(CUSTOM);
		assertEquals("On foot: hold and move {RS} to turn the camera", c.get("{DDOWN}"));
		assertEquals("Brake. On foot: jump", c.get("{LB}"));
	}

	@Test
	public void theTrickBookFollowsThePreset()
	{
		TrickBook.Settings pad = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false, true,
			"B", "Up", "Down", CUSTOM, "Custom");
		List<TrickBook.Section> book = TrickBook.build(pad);
		TrickBook.Section controller = book.stream().filter(s -> s.title.equals("Controller")).findFirst().get();
		assertTrue(controller.intro.startsWith("Preset: Custom."));
		assertTrue(controller.entries.stream().anyMatch(e -> "{DDOWN}".equals(e.pad)));
		TrickBook.Section hard = book.stream().filter(s -> s.title.startsWith("Hard flips")).findFirst().get();
		assertEquals("Hold RB as the trick fires for the harder version.", hard.intro);
		TrickBook.Section basics = book.get(0);
		assertTrue(basics.entries.stream().anyMatch(e -> "Hold {LB}".equals(e.pad)));
		TrickBook.Section frontflip = book.stream().filter(s -> s.title.startsWith("Spins")).findFirst().get();
		assertTrue(frontflip.entries.stream().anyMatch(e -> e.pad != null && e.pad.startsWith("Hold {RB} and {LS} up")));
	}

	@Test
	public void theSkate3TrickBookReadsAsBefore()
	{
		TrickBook.Settings pad = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false, true,
			"B", "Up", "Down", PadPreset.skate3(), "Skate 3");
		List<TrickBook.Section> book = TrickBook.build(pad);
		TrickBook.Section hard = book.stream().filter(s -> s.title.startsWith("Hard flips")).findFirst().get();
		assertEquals("Hold LB or RB as the trick fires for the harder version.", hard.intro);
		TrickBook.Section basics = book.get(0);
		assertTrue(basics.entries.stream().anyMatch(e -> "{A} or {X}, tap or hold".equals(e.pad)));
		assertTrue(basics.entries.stream().anyMatch(e -> "Hold {B}".equals(e.pad)));
		TrickBook.Section off = book.stream().filter(s -> s.title.equals("Off the board")).findFirst().get();
		assertTrue(off.entries.stream().anyMatch(e -> "Hold {A}".equals(e.pad)));
		assertTrue(off.entries.stream().anyMatch(e -> "{LT} / {RT}: drop the board, or pick it up when close"
			.equals(e.pad)));
	}

	@Test
	public void customiseControllerSavesTheEditedLayoutAsACode()
	{
		List<String> saved = new ArrayList<>();
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		panel.setControllerActions(new SkatePanel.ControllerActions()
		{
			@Override
			public void saveCustomLayout(String code)
			{
				saved.add(code);
			}

			@Override
			public void openReleases()
			{
			}

			@Override
			public void saveProfile(java.awt.Component from)
			{
			}
		});
		named(panel, "controller:customise").doClick();
		assertTrue(panel.view == panel.layoutView());
		ControllerLayoutView v = panel.layoutView();
		assertEquals(PadPreset.skate3(), v.editing);
		v.choose(PadButton.LB, PadContext.BOARD, PadAction.BRAKE);
		v.choose(PadButton.L3, PadContext.FOOT, PadAction.CAMERA_ORBIT);
		named(panel, "layout:save").doClick();
		assertEquals(1, saved.size());
		PadPreset expected = PadPreset.skate3().with(PadButton.LB, PadContext.BOARD, PadAction.BRAKE)
			.with(PadButton.L3, PadContext.FOOT, PadAction.CAMERA_ORBIT);
		assertEquals(expected, LayoutCode.decode(saved.get(0)).preset);
		named(panel, "layout:back").doClick();
		assertFalse(panel.view == panel.layoutView());
	}

	@Test
	public void thePresetsAreOfferedOnlyWhereTheyCanBeBound()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		named(panel, "controller:customise").doClick();
		ControllerLayoutView v = panel.layoutView();
		// push on foot is not offered, so choosing it changes nothing
		v.choose(PadButton.A, PadContext.FOOT, PadAction.PUSH);
		assertEquals(PadAction.SPRINT, v.editing.action(PadButton.A, PadContext.FOOT));
		v.choose(PadButton.A, PadContext.FOOT, PadAction.FLIP_BUTTON);
		assertEquals(PadAction.SPRINT, v.editing.action(PadButton.A, PadContext.FOOT));
		// the button tricks are offered on the board
		v.choose(PadButton.A, PadContext.BOARD, PadAction.FLIP_BUTTON);
		assertEquals(PadAction.FLIP_BUTTON, v.editing.action(PadButton.A, PadContext.BOARD));
		assertNull(v.editing.problem());
	}

	@Test
	public void aPastedCodeIsPreviewedThenApplied()
	{
		List<String> saved = new ArrayList<>();
		ControllerLayoutView v = new ControllerLayoutView(() -> { }, saved::add);
		v.showFor(PadPreset.skate3(), null);
		assertNull(v.preview("RSK9:abc"));
		assertTrue(v.status.getText().contains("newer RuneSkate"));
		assertTrue(saved.isEmpty());
		String code = LayoutCode.encode(CUSTOM);
		List<String> changes = v.preview(code);
		assertTrue(changes.contains("LB, on the board: Hard tricks (Shift) to Brake"));
		assertEquals(PadPreset.skate3(), v.editing);
		v.apply(code);
		assertEquals(CUSTOM, v.editing);
		assertEquals(Arrays.asList(code), saved);
		assertTrue(v.preview(code).isEmpty());
	}

	@Test
	public void controllerSetupListensOnlyWhileOpen()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		named(panel, "controller:setup").doClick();
		assertTrue(panel.view == panel.setupView());
		ControllerSetupView v = panel.setupView();
		assertTrue(v.listening);
		// another view closes it
		panel.openTrickBook();
		assertFalse(v.listening);
		assertTrue(panel.view == panel.book);
		panel.openSetup();
		assertFalse(panel.view == panel.book);
		assertTrue(v.listening);
		named(panel, "setup:back").doClick();
		assertFalse(v.listening);
		named(panel, "controller:setup").doClick();
		panel.dispose();
		assertFalse(v.listening);
	}

	@Test
	public void thePadTestSeesPadKeysThroughTheClientKeyListener()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		java.util.List<String> calls = new java.util.ArrayList<>();
		panel.setControllerActions(new SkatePanel.ControllerActions()
		{
			@Override
			public void saveCustomLayout(String code)
			{
			}

			@Override
			public void openReleases()
			{
			}

			@Override
			public void saveProfile(java.awt.Component from)
			{
			}

			@Override
			public void watchKeys(net.runelite.client.input.KeyListener l, boolean on)
			{
				calls.add(on ? "on" : "off");
			}
		});
		named(panel, "controller:setup").doClick();
		ControllerSetupView v = panel.setupView();
		v.watch(KeyEvent.VK_F19, true);
		assertTrue(v.tester().isDown(PadButton.LT));
		v.watch(KeyEvent.VK_F19, false);
		assertFalse(v.tester().isDown(PadButton.LT));
		panel.dispose();
		assertEquals(Arrays.asList("on", "off"), calls);
	}

	@Test
	public void theSteamInputStepsListEveryButtonsKey()
	{
		String s = ControllerSetupView.steamText();
		for (PadButton b : PadButton.values())
		{
			assertTrue(b.label, s.contains(b.label + " " + b.keyName));
		}
	}

	@Test
	public void thePanelBasicsNameThePresetsButtons()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, true, "F");
		panel.setController(CUSTOM, "Custom", CUSTOM);
		List<String> texts = new ArrayList<>();
		collectTexts(panel, texts);
		assertTrue(texts.stream().anyMatch(t -> t.contains("Controller preset: Custom")));
		assertTrue(texts.stream().anyMatch(t -> t.contains("Brake. On foot: jump")));
	}

	private static void collectTexts(java.awt.Container c, List<String> out)
	{
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel && ((javax.swing.JLabel) child).getText() != null)
			{
				out.add(((javax.swing.JLabel) child).getText());
			}
			if (child instanceof java.awt.Container)
			{
				collectTexts((java.awt.Container) child, out);
			}
		}
	}
}
