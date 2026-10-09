package com.gielinorskate.overlay;

import com.gielinorskate.Text;
import com.gielinorskate.leaderboard.LeaderboardPage;
import com.gielinorskate.leaderboard.LeaderboardService;
import java.util.*;
import lombok.AllArgsConstructor;

/**
* What the on-screen leaderboard shows, from the service's snapshot: a title, the board's name and period, the top
* five and your own row (highlighted; added below when you are not in the top five), or a one-line status. Also the
* board cycle (combo, session, xp) and the right-click menu's labels. Pure.
*/
final class LeaderboardHud
{
static final String TITLE = "RuneSkate Leaderboard";
private static final List<String> CATEGORIES = Arrays.asList("combo", "session", "xp");

enum Kind
{
ROW,
/** Your own row: highlighted. */
YOU,
/** A status line instead of rows. */
STATUS
}

@AllArgsConstructor
static final class Line
{
final String left;
/** The score, or null on a status line. */
final String right;
final Kind kind;
}

@AllArgsConstructor
static final class Model
{
final String title;
/** "Best Combo - This week"; null when collapsed. */
final String subtitle;
final List<Line> lines;
}

/** A known category key, or "combo". */
static String category(String key)
{
return CATEGORIES.contains(key) ? key : CATEGORIES.get(0);
}

/** The board after {@code key}: combo, session, xp, then combo again. */
static String nextCategory(String key)
{
return CATEGORIES.get((CATEGORIES.indexOf(category(key)) + 1) % CATEGORIES.size());
}

/** "all" or "week" (the default). */
static String period(String key)
{
return "all".equals(key) ? "all" : "week";
}

static String otherPeriod(String key)
{
return "all".equals(key) ? "week" : "all";
}

/** The right-click options, in order: collapse/expand, next board, the other period. */
static List<String> menuOptions(boolean collapsed, String period)
{
return Arrays.asList(collapsed ? "Expand" : "Collapse", "Next board", "all".equals(period) ? "This week"
: "All-time");
}

/** The top five rows, then yours when it is not among them. */
static List<LeaderboardPage.Row> rows(LeaderboardPage page)
{
List<LeaderboardPage.Row> rows = new ArrayList<>(page.entries.subList(0, Math.min(5, page.entries.size())));
if (page.you != null && rows.stream().noneMatch(page.you::sameAs))
rows.add(page.you);
return rows;
}

/** What to draw, or null when nothing shows (the leaderboard is off). */
static Model build(LeaderboardService.Hud hud, boolean collapsed)
{
if (hud == null || hud.state == LeaderboardService.View.State.OFF)
return null;
List<Line> lines = new ArrayList<>();
if (collapsed)
return new Model(TITLE + " (collapsed)", null, lines);
String category = category(hud.category);
String subtitle = Text.get("board." + category)
+ " - " + ("all".equals(hud.period) ? "All-time" : "This week");
LeaderboardPage page = hud.page;
// the line shown instead of rows, or null when there are rows to show
String status = hud.otherWorld ? "Normal worlds only." : page == null ? hud.message != null ? hud.message
: "Loading..." : page.entries.isEmpty() && page.you == null ? "No scores yet." : null;
if (status != null)
lines.add(new Line(status, null, Kind.STATUS));
else
{
for (LeaderboardPage.Row r : rows(page))
lines.add(new Line(r.rank + ". " + r.displayName, String.format(Locale.ROOT, "%,d", r.score)
+ ("xp".equals(category) ? " xp" : ""), r.sameAs(page.you) ? Kind.YOU : Kind.ROW));
}
return new Model(TITLE, subtitle, lines);
}
}
