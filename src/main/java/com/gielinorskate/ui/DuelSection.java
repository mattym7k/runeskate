package com.gielinorskate.ui;

import com.gielinorskate.duel.DuelLines;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import com.gielinorskate.duel.DuelView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel's "Skate Duel" section: this account's win / loss record, what is going on, Accept / Decline
 * for a challenge received, and the party members skating with duels on, each with a Challenge button. Swing
 * only (EDT): it is told what to show ({@link #display}) and asks for actions through its callbacks.
 */
public class DuelSection extends JPanel
{
	/** What the buttons ask for (the plugin hops to the client thread). */
	public interface Actions
	{
		void challenge(long memberId);

		void accept();

		void decline();

		void withdraw();
	}

	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 40;

	private final Actions actions;
	private final JLabel record = new JLabel();
	private final JLabel status = new JLabel();
	private final JPanel answer = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JButton accept = new JButton("Accept");
	private final JButton decline = new JButton("Decline");
	private final JButton withdraw = new JButton("Take back challenge");
	private final JPanel list = new JPanel();
	/** The members whose rows are showing, and whether their buttons were enabled. */
	private List<DuelView.Member> shownMembers = Collections.emptyList();
	private boolean shownCanChallenge;

	public DuelSection(Actions actions)
	{
		this.actions = actions;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(heading("Skate Duel"));
		record.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(left(record));
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setBorder(new EmptyBorder(2, 0, 4, 0));
		add(left(status));

		answer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (JButton b : new JButton[]{accept, decline})
		{
			b.setFocusable(false);
			answer.add(b);
		}
		accept.addActionListener(e -> actions.accept());
		decline.addActionListener(e -> actions.decline());
		answer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		add(left(answer));

		withdraw.setFocusable(false);
		withdraw.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		withdraw.addActionListener(e -> actions.withdraw());
		add(left(withdraw));

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(left(list));

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

	/** Shows {@code v}; null before anything is known. */
	public void display(DuelView v)
	{
		if (v == null)
		{
			record.setText(DuelLines.record(0, 0));
			status.setText(html("Join a RuneLite party to duel party members who are skating."));
			answer.setVisible(false);
			withdraw.setVisible(false);
			showMembers(Collections.emptyList(), false);
			return;
		}
		record.setText(DuelLines.record(v.wins, v.losses));
		status.setText(html(esc(v.status())));
		boolean challenged = v.allowed && v.phase == Phase.CHALLENGED;
		answer.setVisible(challenged);
		accept.setEnabled(challenged && v.skating);
		accept.setToolTipText(v.skating ? null : "Start skating first");
		withdraw.setVisible(v.phase == Phase.CHALLENGING);
		boolean listed = v.allowed && v.inParty && (v.phase == Phase.IDLE || v.phase == Phase.OVER);
		showMembers(listed ? v.members : Collections.emptyList(), v.canChallenge());
	}

	/** Rebuilds the member rows only when they change. */
	private void showMembers(List<DuelView.Member> members, boolean canChallenge)
	{
		if (members.equals(shownMembers) && canChallenge == shownCanChallenge)
		{
			return;
		}
		shownMembers = members;
		shownCanChallenge = canChallenge;
		list.removeAll();
		for (DuelView.Member m : members)
		{
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(3, 6, 3, 3));
			JLabel name = new JLabel(m.name);
			name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			row.add(name, BorderLayout.CENTER);
			JButton b = new JButton("Challenge");
			b.setFocusable(false);
			b.setEnabled(canChallenge);
			b.setToolTipText(canChallenge ? "Challenge " + m.name + " to a Skate Duel" : "Start skating first");
			long id = m.id;
			b.addActionListener(e -> actions.challenge(id));
			row.add(b, BorderLayout.EAST);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(row);
		}
		list.revalidate();
		list.repaint();
	}

	/** The challenge buttons showing, for tests. */
	List<JButton> challengeButtons()
	{
		List<JButton> out = new ArrayList<>();
		for (Component row : list.getComponents())
		{
			for (Component c : ((JPanel) row).getComponents())
			{
				if (c instanceof JButton)
				{
					out.add((JButton) c);
				}
			}
		}
		return out;
	}

	boolean answerShowing()
	{
		return answer.isVisible();
	}

	JButton acceptButton()
	{
		return accept;
	}

	JButton declineButton()
	{
		return decline;
	}
}
