package com.gielinorskate.ui;

import com.gielinorskate.leaderboard.LeaderboardPage;
import com.gielinorskate.leaderboard.LeaderboardService;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

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

	static final String[] CATEGORY_NAMES = {"Best Combo", "Best Session", "Skating Level"};
	static final String[] CATEGORY_KEYS = {"combo", "session", "xp"};
	private static final Color YOU_COLOR = new Color(255, 152, 31);
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 40;

	private final JButton runButton = new JButton("Start 2-minute run");
	private final JLabel runText = new JLabel();
	private final JLabel status = new JLabel();
	private final JComboBox<String> category = new JComboBox<>(CATEGORY_NAMES);
	private final JToggleButton allTime = new JToggleButton("All-time");
	private final JToggleButton thisWeek = new JToggleButton("This week");
	private final JButton refresh = new JButton("Refresh");
	private final JPanel controls = new JPanel();
	private final LeaderboardTable board = new LeaderboardTable(TEXT_WIDTH, FontManager.getRunescapeSmallFont(),
		ColorScheme.LIGHT_GRAY_COLOR, YOU_COLOR);
	private final BoardRequest onRequest;
	private boolean skating;
	private boolean running;
	/** The state last shown (to fetch a board as soon as the feature turns on). */
	private LeaderboardService.View.State shownState;
	/** The board last put in the table (pages are immutable, so the same object means the same rows). */
	private LeaderboardPage shownPage;

	public LeaderboardSection(Runnable onRunToggle, BoardRequest onRequest)
	{
		this.onRequest = onRequest;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(heading("Timed run"));
		runText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(left(runText));
		runButton.setFocusable(false);
		runButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		runButton.addActionListener(e -> onRunToggle.run());
		add(left(runButton));

		add(heading("Leaderboards"));
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(left(status));
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		category.setFocusable(false);
		category.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		category.addActionListener(e -> request(false));
		controls.add(left(category));
		JPanel periods = new JPanel(new GridLayout(1, 3, 4, 0));
		periods.setBackground(ColorScheme.DARK_GRAY_COLOR);
		ButtonGroup group = new ButtonGroup();
		for (JToggleButton b : new JToggleButton[]{allTime, thisWeek})
		{
			b.setFocusable(false);
			group.add(b);
			periods.add(b);
			b.addActionListener(e -> request(false));
		}
		allTime.setSelected(true);
		refresh.setFocusable(false);
		refresh.setToolTipText("Fetch the board again (at most every 30 seconds)");
		refresh.addActionListener(e -> request(true));
		periods.add(refresh);
		periods.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		controls.add(left(periods));
		board.setBorder(new EmptyBorder(4, 0, 0, 0));
		controls.add(left(board));
		add(left(controls));

		setSkating(false);
		setRun(false, 0);
		display(null);
	}

	private static JComponent left(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(new EmptyBorder(10, 0, 3, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static String html(String body)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + body + "</div></html>";
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	String categoryKey()
	{
		int i = category.getSelectedIndex();
		return CATEGORY_KEYS[i < 0 ? 0 : i];
	}

	String periodKey()
	{
		return thisWeek.isSelected() ? "week" : "all";
	}

	/** Asks for the board the controls show. */
	public void request(boolean force)
	{
		onRequest.request(categoryKey(), periodKey(), force);
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
		runText.setText(html("Land as much as you can in 2 minutes. Bails don't end it."
			+ (best > 0 ? "<br>Your best: <font color='#c8a85a'>" + String.format("%,d", best) + "</font>" : "")));
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
		if (view != null && view.state != LeaderboardService.View.State.ON)
		{
			shownState = view.state;
		}
		if (view == null || view.state == LeaderboardService.View.State.OFF)
		{
			status.setText(html("Turn on \"Submit scores to the leaderboard\" in the plugin's settings (RuneSkate "
				+ "section) to see the leaderboards and put your scores on them."));
			controls.setVisible(false);
			return;
		}
		controls.setVisible(true);
		boolean turnedOn = shownState != LeaderboardService.View.State.ON;
		shownState = view.state;
		if (turnedOn && view.page == null && view.message == null && isShowing())
		{
			request(false);
		}
		String updated = view.fetchedAt > 0 ? "Updated " + new SimpleDateFormat("HH:mm").format(new Date(view.fetchedAt))
			: "";
		String line = view.message != null ? esc(view.message) : updated;
		status.setText(html(line.isEmpty() ? "&nbsp;" : line));
		if (view.page != shownPage)
		{
			// a new board: an unchanged one (a status-only update) keeps its rows as they are
			shownPage = view.page;
			board.display(view.page == null ? null : LeaderboardTable.lines(view.page));
		}
	}
}
