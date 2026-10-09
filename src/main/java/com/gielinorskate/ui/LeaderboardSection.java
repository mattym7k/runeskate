package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import com.gielinorskate.Text;
import com.gielinorskate.leaderboard.LeaderboardPage;
import com.gielinorskate.leaderboard.LeaderboardService;
import com.gielinorskate.leaderboard.LeaderboardService.View.State;
import java.awt.Color;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
* The side panel's "Timed run" and "Leaderboards" sections. Swing only (EDT): it is told what to show
* ({@link #setSkating}, {@link #setRun}, {@link #display}) and asks for boards and runs through its callbacks.
*/
public class LeaderboardSection extends JPanel
{
/** Asks for a board: category combo/session/xp, period all/week, refresh or cache. */
public interface BoardRequest
{
void request(String category, String period, boolean refresh);
}

private final JButton runButton = button(null, null, null);
private final JLabel runText = greyLabel(null);
private final JLabel status = greyLabel(null);
/** The categories, in the order of their keys "combo", "session", "xp". */
private final JComboBox<String> category = new JComboBox<>(new String[]{"Best Combo", "Best Session",
"Skating Level"});
private final JToggleButton thisWeek = new JToggleButton("This week");
private final JPanel controls = column();
/** The rows; the caller's in orange. */
private final LeaderboardTable board = new LeaderboardTable(new Color(255, 152, 31));
private final BoardRequest onRequest;
private boolean skating;
private boolean running;
/** The state last shown (to fetch a board as soon as the feature turns on). */
private State shownState;
/** The board last put in the table (pages are immutable, so the same object means the same rows). */
private LeaderboardPage shownPage;

public LeaderboardSection(Runnable onRunToggle, BoardRequest onRequest)
{
this.onRequest = onRequest;
setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
dark(this);

add(heading("Timed run"));
add(runText);
stretch(runButton, 28);
runButton.addActionListener(e -> onRunToggle.run());
add(left(runButton));

add(heading("Leaderboards"));
add(status);
category.setFocusable(false);
stretch(category, 26);
category.addActionListener(e -> request(false));
controls.add(left(category));
JPanel periods = left(dark(new JPanel(new GridLayout(1, 3, 4, 0))));
ButtonGroup group = new ButtonGroup();
JToggleButton allTime = new JToggleButton("All-time", true);
for (JToggleButton b : new JToggleButton[]{allTime, thisWeek})
{
b.setFocusable(false);
group.add(b);
periods.add(b);
b.addActionListener(e -> request(false));
}
JButton refresh = button("Refresh", null, e -> request(true));
refresh.setToolTipText(Text.get("board.refreshTip"));
periods.add(refresh);
stretch(periods, 26);
controls.add(periods);
board.setBorder(new EmptyBorder(4, 0, 0, 0));
controls.add(left(board));
add(controls);

setSkating(false);
setRun(false, 0);
display(null);
}

/** Asks for the board the controls show. */
public void request(boolean force)
{
int i = category.getSelectedIndex();
onRequest.request(new String[]{"combo", "session", "xp"}[Math.max(i, 0)], thisWeek.isSelected() ? "week" : "all",
force);
}

/** Skating or not: a run can only start while skating. */
public void setSkating(boolean skating)
{
this.skating = skating;
updateRunButton();
}

/** The timed run: going or not, and this account's best. */
public void setRun(boolean running, long best)
{
this.running = running;
runText.setText(html(Text.get("board.run" + (best > 0 ? ".best" : ""), String.format("%,d", best))));
updateRunButton();
}

private void updateRunButton()
{
runButton.setText(running ? "Cancel run" : "Start 2-minute run");
runButton.setEnabled(running || skating);
runButton.setToolTipText(running || skating ? null : "Start skating first");
}

/** What the leaderboard service says to show; null before anything is known. */
public void display(LeaderboardService.View view)
{
if (view != null && view.state != State.ON)
shownState = view.state;
if (view == null || view.state == State.OFF)
{
status.setText(html(Text.get("board.off")));
controls.setVisible(false);
return;
}
controls.setVisible(true);
boolean turnedOn = shownState != State.ON;
shownState = view.state;
if (turnedOn && view.page == null && view.message == null && isShowing())
request(false);
String line = view.message != null ? esc(view.message) : view.fetchedAt > 0
? "Updated " + new SimpleDateFormat("HH:mm").format(new Date(view.fetchedAt)) : "&nbsp;";
status.setText(html(line.isEmpty() ? "&nbsp;" : line));
if (view.page != shownPage)
{
// a new board: an unchanged one (a status-only update) keeps its rows as they are
shownPage = view.page;
board.display(view.page == null ? null : LeaderboardTable.lines(view.page));
}
}
}
