package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import org.junit.Test;

/** The panel builds, shows its state and calls back, without a game client. */
public class SkatePanelTest
{
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

	@Test
	public void startStopAndTogglesCallBack()
	{
		int[] toggles = {0};
		List<String> set = new ArrayList<>();
		SkatePanel panel = new SkatePanel(() -> toggles[0]++, (k, v) -> set.add(k + "=" + v));
		panel.setSettings("Ctrl+K", "C", true, true, true, true, false);
		panel.update(new PanelState(true, null, 1200, 900, 4));
		List<AbstractButton> found = buttons(panel, new ArrayList<>());
		AbstractButton start = found.stream().filter(b -> b.getText().endsWith("skating")).findFirst().get();
		assertEquals("Stop skating", start.getText());
		start.doClick();
		assertEquals(1, toggles[0]);
		JCheckBox card = (JCheckBox) found.stream().filter(b -> "Show controls card".equals(b.getText())).findFirst()
			.get();
		card.doClick();
		assertEquals("showControlsCard=true", set.get(0));
	}

	@Test
	public void boardPickersGreyLockedDesignsAndPickUnlockedOnes()
	{
		BoardDesigns designs = BoardDesigns.bundled();
		List<BoardDesign> picked = new ArrayList<>();
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, picked::add);
		BoardLook look = BoardLook.defaults(designs).with(designs.byId("IRON"));
		panel.updateProgress(new ProgressState(50, 101_333, 10_000, 0.1f, look));
		List<AbstractButton> found = buttons(panel, new ArrayList<>());
		AbstractButton rune = byName(found, "design:RUNE");
		AbstractButton dragon = byName(found, "design:DRAGON");
		AbstractButton iron = byName(found, "design:IRON");
		AbstractButton runeGrip = byName(found, "design:GRIP_RUNE");
		assertTrue(rune.isEnabled());
		assertFalse(dragon.isEnabled());
		assertTrue(dragon.getText().contains("Level 60"));
		assertTrue(iron.isSelected());
		assertFalse(rune.isSelected());
		rune.doClick();
		dragon.doClick();
		runeGrip.doClick();
		assertEquals(java.util.Arrays.asList(designs.byId("RUNE"), designs.byId("GRIP_RUNE")), picked);
		// one button per design, in a grid per part
		assertEquals(designs.all().size(), found.stream().filter(b -> b.getName() != null
			&& b.getName().startsWith("design:")).count());
		for (DesignPart part : DesignPart.values())
		{
			for (BoardDesign d : designs.of(part))
			{
				AbstractButton b = byName(found, "design:" + d.id);
				// every design shows its picture
				assertTrue(d.id, b.getIcon() != null && b.getIcon().getIconWidth() > 1);
			}
		}
	}

	@Test
	public void theSelectionFollowsTheLookInUse()
	{
		BoardDesigns designs = BoardDesigns.bundled();
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		panel.updateProgress(new ProgressState(99, 13_034_431, 0, 1f, BoardLook.defaults(designs)));
		assertTrue(byName(buttons(panel, new ArrayList<>()), "design:DECK_RED_CAMO").isSelected());
		panel.updateProgress(new ProgressState(99, 13_034_431, 0, 1f,
			BoardLook.defaults(designs).with(designs.byId("TORVA"))));
		List<AbstractButton> found = buttons(panel, new ArrayList<>());
		assertFalse(byName(found, "design:DECK_RED_CAMO").isSelected());
		assertTrue(byName(found, "design:TORVA").isSelected());
		assertTrue(byName(found, "design:TORVA").isEnabled());
	}

	private static AbstractButton byName(List<AbstractButton> found, String name)
	{
		return found.stream().filter(b -> name.equals(b.getName())).findFirst()
			.orElseThrow(() -> new AssertionError("no button " + name));
	}

	@Test
	public void goalsListTicksDoneOnesAndShowsProgress()
	{
		String html = SkatePanel.goalsHtml(java.util.Arrays.asList(
			new ProgressState.GoalView("Land a body flip", "Done", true),
			new ProgressState.GoalView("Land 3 Shift tricks", "1/3", false)));
		assertTrue(html.contains("&#10004; Land a body flip"));
		assertTrue(html.contains("Land 3 Shift tricks <font color='#c8a85a'>1/3</font>"));
		assertTrue(SkatePanel.goalsHtml(Collections.emptyList()).startsWith("Start skating"));
	}

	private static List<javax.swing.JLabel> labels(java.awt.Container c, List<javax.swing.JLabel> out)
	{
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel)
			{
				out.add((javax.swing.JLabel) child);
			}
			if (child instanceof java.awt.Container)
			{
				labels((java.awt.Container) child, out);
			}
		}
		return out;
	}

	/** The text of every label and wrapped text area under {@code c}. */
	private static List<String> texts(java.awt.Container c, List<String> out)
	{
		for (java.awt.Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel && ((javax.swing.JLabel) child).getText() != null)
			{
				out.add(((javax.swing.JLabel) child).getText());
			}
			if (child instanceof javax.swing.JTextArea)
			{
				out.add(((javax.swing.JTextArea) child).getText());
			}
			if (child instanceof java.awt.Container)
			{
				texts((java.awt.Container) child, out);
			}
		}
		return out;
	}

	private static AbstractButton named(java.awt.Container c, String name)
	{
		return buttons(c, new ArrayList<>()).stream().filter(b -> name.equals(b.getName())).findFirst().get();
	}

	@Test
	public void controllerModeDrawsThePadButtonsInTheBasics()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { });
		List<javax.swing.JLabel> plain = labels(panel, new ArrayList<>());
		assertFalse(plain.stream().anyMatch(l -> l.getIcon() instanceof ControllerGlyphIcon && l.isShowing()));

		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, true);
		List<javax.swing.JLabel> all = labels(panel, new ArrayList<>());
		long icons = all.stream().filter(l -> l.getIcon() instanceof ControllerGlyphIcon).count();
		assertTrue("glyph rows " + icons, icons >= 8);
		assertTrue(all.stream().anyMatch(l -> l.getText() != null && l.getText().contains("Brake")));

		// and back: the keyboard basics return
		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, false);
		assertTrue(labels(panel, new ArrayList<>()).stream().noneMatch(l -> l.getIcon() instanceof ControllerGlyphIcon));
	}

	@Test
	public void theKoFiSupportLinkSitsAtTheBottomOfThePanel()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { });
		AbstractButton support = named(panel, "support:kofi");
		assertTrue(support.getText().contains("Ko-fi"));
		assertEquals("https://ko-fi.com/runeskate_project", SkatePanel.SUPPORT_URL);
		assertEquals(SkatePanel.SUPPORT_URL, support.getToolTipText());
	}

	@Test
	public void theTrickListLivesInTheTrickBookNotThePanel()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { });
		assertFalse(texts(panel, new ArrayList<>()).stream().anyMatch(t -> t.equals("Tailslide")));
		AbstractButton open = named(panel, "trickBook:open");
		assertEquals("Open Trick Book", open.getText());
		open.doClick();
		assertTrue(panel.isTrickBookOpen());
		List<String> book = texts(panel, new ArrayList<>());
		assertTrue(book.contains("Trick Book"));
		assertTrue(book.contains("Tailslide"));
		assertTrue(book.contains("Grabs"));
		assertTrue(book.contains("Pull down, flick up-left"));
		assertFalse("the panel's own content is swapped out", book.stream().anyMatch(t -> t.equals("This session")));

		named(panel, "trickBook:back").doClick();
		assertFalse(panel.isTrickBookOpen());
		List<String> back = texts(panel, new ArrayList<>());
		assertTrue(back.contains("This session"));
		assertFalse(back.contains("Tailslide"));
	}

	@Test
	public void theOpenTrickBookFollowsTheSettings()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { });
		named(panel, "trickBook:open").doClick();
		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, true);
		List<String> pad = texts(panel, new ArrayList<>());
		assertTrue(pad.contains("Hold LT in the air (or RT, aiming right stick to the toe side)"));
		assertTrue(pad.contains("right stick pull down, flick up-left"));
		long icons = labels(panel, new ArrayList<>()).stream().filter(l -> l.getIcon() instanceof ControllerGlyphIcon)
			.count();
		assertTrue("glyph rows " + icons, icons >= 9 + 7);

		panel.setKeyNames("left", "N", "Up", "Down");
		assertTrue(texts(panel, new ArrayList<>()).stream().noneMatch(t -> t.contains("right mouse button")));
		panel.setSettings("Ctrl+K", "Space", true, false, false, false, true, false);
		assertTrue(texts(panel, new ArrayList<>()).stream().anyMatch(t -> t.contains("left mouse button")));
	}

	@Test
	public void glyphsOfATrickLineAreItsDistinctButtons()
	{
		assertEquals(2, SkatePanel.glyphsOf("Hold {LT} in the air (or {RT}, aiming {RS} up)").length);
		assertEquals(ControllerGlyphs.Glyph.RS, SkatePanel.glyphsOf("Hold {RS} tilted up")[0]);
		assertEquals(0, SkatePanel.glyphsOf("Land along a rail").length);
	}

	/** The panel over its own catalogue, so the custom designs don't leak into other tests. */
	private static BoardDesigns ownDesigns() throws java.io.IOException
	{
		try (java.io.InputStream in = SkatePanelTest.class.getResourceAsStream(
			"/com/gielinorskate/designs/designs.json"))
		{
			return BoardDesigns.parse(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	@Test
	public void customDesignsComeAfterTheShippedOnesWithAddTilesAndAMenu() throws Exception
	{
		BoardDesigns designs = ownDesigns();
		BoardDesign mine = BoardDesign.custom("CUSTOM_0A1B2C3D", "Flames", DesignPart.GRIP, 1);
		designs.setCustom(java.util.Collections.singletonList(mine));
		List<BoardDesign> picked = new ArrayList<>();
		List<String> actions = new ArrayList<>();
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, picked::add, designs);
		panel.updateProgress(new ProgressState(1, 0, 83, 0f, BoardLook.defaults(designs)));
		// no "+ Custom" until the plugin gives the actions
		assertFalse(buttons(panel, new ArrayList<>()).stream().anyMatch(b -> "customAdd:grip".equals(b.getName())));
		panel.setCustomActions(new SkatePanel.CustomActions()
		{
			@Override
			public void add(DesignPart part, java.awt.Component from)
			{
				actions.add("add " + part.key);
			}

			@Override
			public void edit(BoardDesign design, java.awt.Component from)
			{
				actions.add("edit " + design.id);
			}

			@Override
			public void rename(BoardDesign design, java.awt.Component from)
			{
				actions.add("rename " + design.id);
			}

			@Override
			public void delete(BoardDesign design, java.awt.Component from)
			{
				actions.add("delete " + design.id);
			}
		});
		java.awt.image.BufferedImage thumb = new java.awt.image.BufferedImage(92, 26,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		panel.setCustomThumbs(java.util.Collections.singletonMap(mine.id, thumb));
		List<AbstractButton> found = buttons(panel, new ArrayList<>());
		List<String> gripOrder = new ArrayList<>();
		for (AbstractButton b : found)
		{
			String n = b.getName();
			if (n != null && (n.startsWith("design:GRIP_") || n.startsWith("design:CUSTOM_") || n.equals("customAdd:grip")))
			{
				gripOrder.add(n);
			}
		}
		// shipped grips, then the custom one, then "+ Custom"
		assertEquals("design:CUSTOM_0A1B2C3D", gripOrder.get(gripOrder.size() - 2));
		assertEquals("customAdd:grip", gripOrder.get(gripOrder.size() - 1));
		assertTrue(gripOrder.get(0).startsWith("design:GRIP_"));
		AbstractButton custom = byName(found, "design:CUSTOM_0A1B2C3D");
		assertTrue(custom.isEnabled());
		assertEquals(92, custom.getIcon().getIconWidth());
		custom.doClick();
		assertEquals(java.util.Collections.singletonList(mine), picked);
		for (java.awt.Component item : custom.getComponentPopupMenu().getComponents())
		{
			((AbstractButton) item).doClick();
		}
		byName(found, "customAdd:deck").doClick();
		byName(found, "customAdd:wheels").doClick();
		assertEquals(java.util.Arrays.asList("edit CUSTOM_0A1B2C3D", "rename CUSTOM_0A1B2C3D",
			"delete CUSTOM_0A1B2C3D", "add deck", "add wheels"), actions);
		// shipped designs have no menu
		assertEquals(null, byName(found, "design:GRIP_RUNE").getComponentPopupMenu());
		// in use: highlighted
		panel.updateProgress(new ProgressState(1, 0, 83, 0f, BoardLook.defaults(designs).with(mine)));
		assertTrue(byName(buttons(panel, new ArrayList<>()), "design:CUSTOM_0A1B2C3D").isSelected());
	}
}
