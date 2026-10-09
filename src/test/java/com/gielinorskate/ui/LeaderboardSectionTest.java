package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.leaderboard.LeaderboardPage;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import org.junit.Test;

/** The panel's timed run and leaderboard sections, without a game client. */
public class LeaderboardSectionTest
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

	private static AbstractButton button(LeaderboardSection s, String text)
	{
		return buttons(s, new ArrayList<>()).stream().filter(b -> text.equals(b.getText())).findFirst().get();
	}

	@Test
	public void runButtonOnlyWorksWhileSkatingOrToCancel()
	{
		int[] toggles = {0};
		LeaderboardSection s = new LeaderboardSection(() -> toggles[0]++, (c, p, r) -> { });
		AbstractButton run = button(s, "Start 2-minute run");
		assertFalse(run.isEnabled());
		s.setSkating(true);
		assertTrue(run.isEnabled());
		run.doClick();
		assertEquals(1, toggles[0]);
		s.setRun(true, 1234);
		assertEquals("Cancel run", run.getText());
		s.setSkating(false);
		assertTrue(run.isEnabled());
	}

	@Test
	public void tabsAndRefreshAskForBoards()
	{
		List<String> asked = new ArrayList<>();
		LeaderboardSection s = new LeaderboardSection(() -> { }, (c, p, r) -> asked.add(c + "/" + p + (r ? "!" : "")));
		button(s, "This week").doClick();
		button(s, "Refresh").doClick();
		assertEquals("combo/week", asked.get(0));
		assertEquals("combo/week!", asked.get(1));
	}

	@Test
	public void boardHighlightsTheCallerAndAddsThemBelowWhenOutsideTheTop()
	{
		Gson gson = new Gson();
		LeaderboardPage inside = LeaderboardPage.parse(gson, "{\"category\":\"combo\",\"period\":\"all\",\"total\":2,"
			+ "\"entries\":[{\"rank\":1,\"displayName\":\"A<b>\",\"score\":9000,\"updatedAt\":\"x\"},"
			+ "{\"rank\":2,\"displayName\":\"Me\",\"score\":100,\"updatedAt\":\"x\"}],"
			+ "\"you\":{\"rank\":2,\"displayName\":\"Me\",\"score\":100,\"updatedAt\":\"x\"}}");
		List<LeaderboardTable.Line> in = LeaderboardTable.lines(inside);
		assertEquals(2, in.size());
		assertEquals("1.", in.get(0).rank);
		assertEquals("A<b>", in.get(0).name);
		assertEquals("9,000", in.get(0).score);
		assertFalse(in.get(0).you);
		assertEquals("Me", in.get(1).name);
		assertTrue(in.get(1).you);
		assertTrue(in.stream().allMatch(l -> l.note == null));

		LeaderboardPage outside = LeaderboardPage.parse(gson, "{\"category\":\"combo\",\"period\":\"all\",\"total\":2,"
			+ "\"entries\":[{\"rank\":1,\"displayName\":\"A\",\"score\":9000,\"updatedAt\":\"x\"}],"
			+ "\"you\":{\"rank\":101,\"displayName\":\"Me\",\"score\":1,\"updatedAt\":\"x\"}}");
		List<LeaderboardTable.Line> out = LeaderboardTable.lines(outside);
		assertEquals(3, out.size());
		assertEquals(LeaderboardTable.GAP_TEXT, out.get(1).note);
		assertEquals("101.", out.get(2).rank);
		assertTrue(out.get(2).you);

		LeaderboardPage empty = LeaderboardPage.parse(gson, "{\"category\":\"combo\",\"period\":\"all\",\"total\":0,"
			+ "\"entries\":[]}");
		assertEquals(LeaderboardTable.EMPTY_TEXT, LeaderboardTable.lines(empty).get(0).note);
	}

	@Test
	public void tableReusesItsLabelsAndNeverRendersNamesAsHtml()
	{
		LeaderboardTable t = new LeaderboardTable(java.awt.Color.ORANGE);
		Gson gson = new Gson();
		StringBuilder rows = new StringBuilder();
		for (int i = 1; i <= 100; i++)
		{
			rows.append(i > 1 ? "," : "").append("{\"rank\":").append(i).append(",\"displayName\":\"<html>P").append(i)
				.append("\",\"score\":").append(1000 - i).append(",\"updatedAt\":\"x\"}");
		}
		LeaderboardPage page = LeaderboardPage.parse(gson, "{\"category\":\"combo\",\"period\":\"all\",\"total\":100,"
			+ "\"entries\":[" + rows + "]}");
		t.display(LeaderboardTable.lines(page));
		int made = t.getComponentCount();
		assertEquals(300, made);
		t.display(LeaderboardTable.lines(page));
		t.display(null);
		t.display(LeaderboardTable.lines(page));
		assertEquals(made, t.getComponentCount());
		for (java.awt.Component c : t.getComponents())
		{
			assertEquals(Boolean.TRUE, ((javax.swing.JLabel) c).getClientProperty("html.disable"));
		}
		assertEquals(Widgets.TEXT_WIDTH, t.getPreferredSize().width);
	}

	@Test
	public void panelTakesTheSection()
	{
		SkatePanel panel = new SkatePanel(() -> { }, (k, v) -> { }, d -> { });
		LeaderboardSection s = new LeaderboardSection(() -> { }, (c, p, r) -> { });
		panel.addSection(s);
		assertTrue(buttons(panel, new ArrayList<>()).stream().anyMatch(b -> "Start 2-minute run".equals(b.getText())));
	}
}
