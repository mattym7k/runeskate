package com.gielinorskate.ui;

import com.gielinorskate.leaderboard.LeaderboardPage;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
* The leaderboard's rows (rank, name, score) as plain small light grey labels in a grid: no HTML, so showing a new
* board is a few hundred {@code setText} calls instead of parsing and laying out a 100-row HTML table on the EDT.
* Labels are made once and reused (three a row; a note row shows only the middle one, across the row); the
* caller's row is bold in {@link #youColor}. Swing only (EDT).
*/
final class LeaderboardTable extends JPanel
{
/** One line of the board: an entry, or a full-width note ("..." or the empty-board message). */
@AllArgsConstructor
static final class Line
{
final String rank;
final String name;
final String score;
final boolean you;
/** Non-null for a full-width note line. */
final String note;

static Line entry(LeaderboardPage.Row r, boolean you)
{
return new Line(r.rank + ".", r.displayName, String.format("%,d", r.score), you, null);
}

static Line note(String text)
{
return new Line(null, null, null, false, text);
}
}

static final String EMPTY_TEXT = "No scores yet. Be the first!";
static final String GAP_TEXT = "...";

/** The top 100, the caller's row marked (and added under the list, after "...", when outside it). */
static List<Line> lines(LeaderboardPage page)
{
List<Line> lines = new ArrayList<>();
if (page.entries.isEmpty())
lines.add(Line.note(EMPTY_TEXT));
boolean youShown = false;
for (LeaderboardPage.Row r : page.entries)
{
boolean you = r.sameAs(page.you);
youShown |= you;
lines.add(Line.entry(r, you));
}
if (page.you != null && !youShown)
{
lines.add(Line.note(GAP_TEXT));
lines.add(Line.entry(page.you, true));
}
return lines;
}

private final Color youColor;
private final GridBagLayout layout = new GridBagLayout();
/** Each row's rank, name and score labels. */
private final List<JLabel[]> rows = new ArrayList<>();

LeaderboardTable(Color youColor)
{
this.youColor = youColor;
setLayout(layout);
setOpaque(false);
}

/** Shows {@code lines}; an empty list (or null) shows nothing. */
void display(List<Line> lines)
{
int y = 0;
for (Line line : lines != null ? lines : List.<Line>of())
{
if (y == rows.size())
{
JLabel[] row = {label(SwingConstants.RIGHT, false), label(SwingConstants.LEFT, true),
label(SwingConstants.RIGHT, false)};
for (JLabel l : row)
add(l);
rows.add(row);
}
boolean note = line.note != null;
String[] texts = {line.rank, note ? line.note : line.name, line.score};
for (int i = 0; i < 3; i++)
{
JLabel l = rows.get(y)[i];
l.setText(texts[i]);
l.setFont(line.you ? FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD)
: FontManager.getRunescapeSmallFont());
l.setForeground(line.you ? youColor : ColorScheme.LIGHT_GRAY_COLOR);
// the name (or a note, across the row) takes what width is left; rank and score sit to the right of
// their cells; cellpadding 1 as the old HTML table's
boolean name = i == 1;
layout.setConstraints(l, new GridBagConstraints(note ? 0 : i, y, note ? 3 : 1, 1, name ? 1.0 : 0.0, 0.0,
name ? GridBagConstraints.WEST : GridBagConstraints.EAST,
name ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE, new Insets(1, 1, 1, 1), 0, 0));
l.setVisible(name || !note);
}
y++;
}
for (JLabel[] row : rows.subList(y, rows.size()))
{
for (JLabel l : row)
l.setVisible(false);
}
revalidate();
repaint();
}

/** A plain label (names are shown as typed, never as HTML); {@code squeeze}: takes what width is left. */
private static JLabel label(int align, boolean squeeze)
{
JLabel l = squeeze ? new JLabel()
{
@Override
public Dimension getPreferredSize()
{
return new Dimension(0, super.getPreferredSize().height);
}

@Override
public Dimension getMinimumSize()
{
return getPreferredSize();
}
} : new JLabel();
l.putClientProperty("html.disable", Boolean.TRUE);
l.setHorizontalAlignment(align);
return l;
}

@Override
public Dimension getPreferredSize()
{
Dimension d = super.getPreferredSize();
return new Dimension(Math.max(Widgets.TEXT_WIDTH, d.width), d.height);
}

@Override
public Dimension getMaximumSize()
{
return getPreferredSize();
}
}
