package com.gielinorskate.overlay;

import com.gielinorskate.leaderboard.LeaderboardPage;
import com.gielinorskate.leaderboard.LeaderboardService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What the on-screen leaderboard shows, from the service's snapshot: a title, the board's name and period, the top
 * five and your own row (highlighted; added below when you are not in the top five), or a one-line status. Also the
 * board cycle (combo, session, xp) and the right-click menu's labels. Pure.
 */
final class LeaderboardHud
{
	static final String TITLE = "RuneSkate Leaderboard";
	static final String COLLAPSED_TITLE = TITLE + " (collapsed)";
	static final int TOP = 5;

	static final String NORMAL_WORLDS_ONLY = "Normal worlds only.";
	static final String LOADING = "Loading...";
	static final String NO_SCORES = "No scores yet.";

	static final String COLLAPSE = "Collapse";
	static final String EXPAND = "Expand";
	static final String NEXT_BOARD = "Next board";
	static final String ALL_TIME = "All-time";
	static final String THIS_WEEK = "This week";

	private static final List<String> CATEGORIES = Arrays.asList("combo", "session", "xp");
	private static final List<String> CATEGORY_NAMES = Arrays.asList("Best Combo", "Best Session", "Skating XP");

	enum Kind
	{
		ROW,
		/** Your own row: highlighted. */
		YOU,
		/** A status line instead of rows. */
		STATUS
	}

	static final class Line
	{
		final String left;
		/** The score, or null on a status line. */
		final String right;
		final Kind kind;

		Line(String left, String right, Kind kind)
		{
			this.left = left;
			this.right = right;
			this.kind = kind;
		}
	}

	static final class Model
	{
		final String title;
		/** "Best Combo - This week"; null when collapsed. */
		final String subtitle;
		final List<Line> lines;
		final boolean collapsed;

		Model(String title, String subtitle, List<Line> lines, boolean collapsed)
		{
			this.title = title;
			this.subtitle = subtitle;
			this.lines = Collections.unmodifiableList(lines);
			this.collapsed = collapsed;
		}
	}

	private LeaderboardHud()
	{
	}

	/** A known category key, or "combo". */
	static String category(String key)
	{
		return CATEGORIES.contains(key) ? key : CATEGORIES.get(0);
	}

	/** The board after {@code key}: combo, session, xp, then combo again. */
	static String nextCategory(String key)
	{
		int i = CATEGORIES.indexOf(category(key));
		return CATEGORIES.get((i + 1) % CATEGORIES.size());
	}

	/** "all" or "week" (the default). */
	static String period(String key)
	{
		return "all".equals(key) ? "all" : "week";
	}

	static String otherPeriod(String key)
	{
		return "all".equals(period(key)) ? "week" : "all";
	}

	/** The right-click options, in order: collapse/expand, next board, the other period. */
	static List<String> menuOptions(boolean collapsed, String period)
	{
		return Arrays.asList(collapsed ? EXPAND : COLLAPSE, NEXT_BOARD,
			"all".equals(period(period)) ? THIS_WEEK : ALL_TIME);
	}

	/** The top {@link #TOP} rows, then yours when it is not among them. */
	static List<LeaderboardPage.Row> rows(LeaderboardPage page)
	{
		List<LeaderboardPage.Row> rows = new ArrayList<>(page.entries.subList(0, Math.min(TOP, page.entries.size())));
		if (page.you != null && rows.stream().noneMatch(page.you::sameAs))
		{
			rows.add(page.you);
		}
		return rows;
	}

	/** What to draw, or null when nothing shows (the leaderboard is off). */
	static Model build(LeaderboardService.Hud hud, boolean collapsed)
	{
		if (hud == null || hud.state == LeaderboardService.View.State.OFF)
		{
			return null;
		}
		if (collapsed)
		{
			return new Model(COLLAPSED_TITLE, null, new ArrayList<>(), true);
		}
		String category = category(hud.category);
		String period = period(hud.period);
		String subtitle = CATEGORY_NAMES.get(CATEGORIES.indexOf(category)) + " - "
			+ ("all".equals(period) ? ALL_TIME : THIS_WEEK);
		List<Line> lines = new ArrayList<>();
		String status = status(hud);
		if (status != null)
		{
			lines.add(new Line(status, null, Kind.STATUS));
		}
		else
		{
			LeaderboardPage page = hud.page;
			for (LeaderboardPage.Row r : rows(page))
			{
				boolean you = r.sameAs(page.you);
				lines.add(new Line(r.rank + ". " + r.displayName, score(category, r.score), you ? Kind.YOU : Kind.ROW));
			}
		}
		return new Model(TITLE, subtitle, lines, false);
	}

	/** The line shown instead of rows, or null when there are rows to show. */
	private static String status(LeaderboardService.Hud hud)
	{
		if (hud.otherWorld)
		{
			return NORMAL_WORLDS_ONLY;
		}
		if (hud.page == null)
		{
			return hud.message != null ? hud.message : LOADING;
		}
		return hud.page.entries.isEmpty() && hud.page.you == null ? NO_SCORES : null;
	}

	private static String score(String category, long score)
	{
		return String.format(Locale.ROOT, "%,d", score) + ("xp".equals(category) ? " xp" : "");
	}
}
