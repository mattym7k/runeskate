package com.gielinorskate.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.leaderboard.LeaderboardPage;
import com.gielinorskate.leaderboard.LeaderboardService.Hud;
import com.gielinorskate.leaderboard.LeaderboardService.View.State;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The on-screen leaderboard's model: which rows, your row, collapsed/expanded, board cycling, menu labels. */
public class LeaderboardHudTest
{
	private static String row(int rank, String name, long score)
	{
		return "{\"rank\":" + rank + ",\"displayName\":\"" + name + "\",\"score\":" + score
			+ ",\"updatedAt\":\"2026-10-05T00:00:00Z\"}";
	}

	/** A board with {@code n} entries (rank i, name Pi, score 1000 - i) and the given "you" row JSON. */
	private static LeaderboardPage page(String category, int n, String you)
	{
		StringBuilder entries = new StringBuilder();
		for (int i = 1; i <= n; i++)
		{
			entries.append(i > 1 ? "," : "").append(row(i, "P" + i, 1000 - i));
		}
		return LeaderboardPage.parse(new Gson(), "{\"category\":\"" + category + "\",\"period\":\"week\","
			+ "\"weekStart\":\"2026-10-05\",\"total\":" + n + ",\"entries\":[" + entries + "],\"you\":"
			+ (you == null ? "null" : you) + "}");
	}

	private static Hud on(LeaderboardPage page)
	{
		return new Hud(State.ON, false, "combo", "week", page, null);
	}

	// ---- row selection

	@Test
	public void topFiveOnly()
	{
		List<LeaderboardPage.Row> rows = LeaderboardHud.rows(page("combo", 8, null));
		assertEquals(5, rows.size());
		assertEquals("P1", rows.get(0).displayName);
		assertEquals("P5", rows.get(4).displayName);
	}

	@Test
	public void fewerThanFiveShowsWhatThereIs()
	{
		assertEquals(2, LeaderboardHud.rows(page("combo", 2, null)).size());
		assertTrue(LeaderboardHud.rows(page("combo", 0, null)).isEmpty());
	}

	@Test
	public void yourRowOutsideTheTopFiveIsAppended()
	{
		List<LeaderboardPage.Row> rows = LeaderboardHud.rows(page("combo", 8, row(42, "Me", 12)));
		assertEquals(6, rows.size());
		assertEquals("Me", rows.get(5).displayName);
		assertEquals(42, rows.get(5).rank);
	}

	@Test
	public void yourRowInsideTheTopFiveIsNotRepeated()
	{
		List<LeaderboardPage.Row> rows = LeaderboardHud.rows(page("combo", 8, row(3, "P3", 997)));
		assertEquals(5, rows.size());
	}

	// ---- rendering model

	@Test
	public void expandedShowsTitleBoardAndRowsWithYoursHighlighted()
	{
		LeaderboardHud.Model m = LeaderboardHud.build(on(page("combo", 8, row(42, "Me", 1234))), false);
		assertEquals(LeaderboardHud.TITLE, m.title);
		assertEquals("Best Combo - This week", m.subtitle);
		assertEquals(6, m.lines.size());
		LeaderboardHud.Line first = m.lines.get(0);
		assertEquals("1. P1", first.left);
		assertEquals("999", first.right);
		assertEquals(LeaderboardHud.Kind.ROW, first.kind);
		LeaderboardHud.Line mine = m.lines.get(5);
		assertEquals("42. Me", mine.left);
		assertEquals("1,234", mine.right);
		assertEquals(LeaderboardHud.Kind.YOU, mine.kind);
	}

	@Test
	public void yourRowInTheTopFiveIsHighlightedInPlace()
	{
		LeaderboardHud.Model m = LeaderboardHud.build(on(page("combo", 8, row(2, "P2", 998))), false);
		assertEquals(5, m.lines.size());
		assertEquals(LeaderboardHud.Kind.ROW, m.lines.get(0).kind);
		assertEquals(LeaderboardHud.Kind.YOU, m.lines.get(1).kind);
	}

	@Test
	public void collapsedShowsOnlyTheTitleBar()
	{
		LeaderboardHud.Model m = LeaderboardHud.build(on(page("combo", 8, null)), true);
		assertTrue(m.collapsed);
		assertTrue(m.title.startsWith(LeaderboardHud.TITLE));
		assertTrue(m.title.contains("(collapsed)"));
		assertNull(m.subtitle);
		assertTrue(m.lines.isEmpty());
	}

	@Test
	public void offRendersNothing()
	{
		assertNull(LeaderboardHud.build(new Hud(State.OFF, false, "combo", "week", null, null), false));
		assertNull(LeaderboardHud.build(null, false));
	}

	@Test
	public void statusLinesReplaceTheRows()
	{
		// another world: the status, even with a board in hand
		LeaderboardHud.Model other = LeaderboardHud.build(
			new Hud(State.ON, true, "combo", "week", page("combo", 3, null), null), false);
		assertEquals(1, other.lines.size());
		assertEquals(LeaderboardHud.Kind.STATUS, other.lines.get(0).kind);
		assertEquals(LeaderboardHud.NORMAL_WORLDS_ONLY, other.lines.get(0).left);

		LeaderboardHud.Model loading = LeaderboardHud.build(on(null), false);
		assertEquals(LeaderboardHud.LOADING, loading.lines.get(0).left);

		LeaderboardHud.Model error = LeaderboardHud.build(
			new Hud(State.ON, false, "combo", "week", null, "Couldn't load."), false);
		assertEquals("Couldn't load.", error.lines.get(0).left);

		LeaderboardHud.Model empty = LeaderboardHud.build(on(page("combo", 0, null)), false);
		assertEquals(LeaderboardHud.NO_SCORES, empty.lines.get(0).left);
	}

	@Test
	public void collapsedHidesStatusLinesToo()
	{
		LeaderboardHud.Model m = LeaderboardHud.build(
			new Hud(State.ON, true, "combo", "week", null, null), true);
		assertTrue(m.lines.isEmpty());
	}

	@Test
	public void xpBoardsSayXp()
	{
		Hud xp = new Hud(State.ON, false, "xp", "all", page("xp", 1, null), null);
		LeaderboardHud.Model m = LeaderboardHud.build(xp, false);
		assertEquals("Skating XP - All-time", m.subtitle);
		assertEquals("999 xp", m.lines.get(0).right);
	}

	// ---- boards and menu

	@Test
	public void boardsCycleComboSessionXp()
	{
		assertEquals("session", LeaderboardHud.nextCategory("combo"));
		assertEquals("xp", LeaderboardHud.nextCategory("session"));
		assertEquals("combo", LeaderboardHud.nextCategory("xp"));
		// anything unknown (a hand-edited setting) starts the cycle over
		assertEquals("combo", LeaderboardHud.category("bogus"));
		assertEquals("combo", LeaderboardHud.category(null));
		assertEquals("session", LeaderboardHud.nextCategory("bogus"));
	}

	@Test
	public void periodsToggleAndDefaultToThisWeek()
	{
		assertEquals("week", LeaderboardHud.period(null));
		assertEquals("week", LeaderboardHud.period("bogus"));
		assertEquals("all", LeaderboardHud.period("all"));
		assertEquals("all", LeaderboardHud.otherPeriod("week"));
		assertEquals("week", LeaderboardHud.otherPeriod("all"));
	}

	@Test
	public void menuOffersTheOtherState()
	{
		assertEquals(Arrays.asList("Collapse", "Next board", "All-time"), LeaderboardHud.menuOptions(false, "week"));
		assertEquals(Arrays.asList("Expand", "Next board", "This week"), LeaderboardHud.menuOptions(true, "all"));
	}

	@Test
	public void titleIsAlwaysThere()
	{
		assertFalse(LeaderboardHud.build(on(null), false).title.isEmpty());
	}
}
