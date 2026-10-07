package com.gielinorskate.ui;

import com.gielinorskate.leaderboard.LeaderboardPage;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The leaderboard's rows (rank, name, score) as plain labels in a grid: no HTML, so showing a new board is a few
 * hundred {@code setText} calls instead of parsing and laying out a 100-row HTML table on the EDT. Labels are
 * made once and reused; the caller's row is bold in {@link #youColor}. Swing only (EDT).
 */
final class LeaderboardTable extends JPanel
{
	/** One line of the board: an entry, or a full-width note ("..." or the empty-board message). */
	static final class Line
	{
		final String rank;
		final String name;
		final String score;
		final boolean you;
		/** Non-null for a full-width note line. */
		final String note;

		private Line(String rank, String name, String score, boolean you, String note)
		{
			this.rank = rank;
			this.name = name;
			this.score = score;
			this.you = you;
			this.note = note;
		}

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
		List<Line> lines = new ArrayList<>(page.entries.size() + 2);
		if (page.entries.isEmpty())
		{
			lines.add(Line.note(EMPTY_TEXT));
		}
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

	/** Matches the old HTML table's cellpadding of 1. */
	private static final Insets CELL = new Insets(1, 1, 1, 1);

	private final int width;
	private final Color textColor;
	private final Color youColor;
	private final Font font;
	private final Font youFont;
	private final GridBagLayout layout = new GridBagLayout();
	private final List<JLabel[]> slots = new ArrayList<>();
	private final List<JLabel> notes = new ArrayList<>();
	private int shownSlots;
	private int shownNotes;

	LeaderboardTable(int width, Font font, Color textColor, Color youColor)
	{
		this.width = width;
		this.font = font;
		this.youFont = font.deriveFont(Font.BOLD);
		this.textColor = textColor;
		this.youColor = youColor;
		setLayout(layout);
		setOpaque(false);
	}

	/** Shows {@code lines}; an empty list (or null) shows nothing. */
	void display(List<Line> lines)
	{
		int slot = 0;
		int note = 0;
		int y = 0;
		if (lines != null)
		{
			for (Line line : lines)
			{
				if (line.note != null)
				{
					JLabel l = note(note++);
					l.setText(line.note);
					place(l, 0, y, 3, 1.0, GridBagConstraints.WEST);
				}
				else
				{
					JLabel[] s = slot(slot++);
					Font f = line.you ? youFont : font;
					Color c = line.you ? youColor : textColor;
					set(s[0], line.rank, f, c);
					set(s[1], line.name, f, c);
					set(s[2], line.score, f, c);
					place(s[0], 0, y, 1, 0.0, GridBagConstraints.EAST);
					place(s[1], 1, y, 1, 1.0, GridBagConstraints.WEST);
					place(s[2], 2, y, 1, 0.0, GridBagConstraints.EAST);
				}
				y++;
			}
		}
		for (int i = slot; i < shownSlots; i++)
		{
			for (JLabel l : slots.get(i))
			{
				l.setVisible(false);
			}
		}
		for (int i = note; i < shownNotes; i++)
		{
			notes.get(i).setVisible(false);
		}
		shownSlots = slot;
		shownNotes = note;
		revalidate();
		repaint();
	}

	private static void set(JLabel l, String text, Font f, Color c)
	{
		l.setText(text);
		l.setFont(f);
		l.setForeground(c);
	}

	private void place(JLabel l, int x, int y, int span, double weight, int anchor)
	{
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = x;
		g.gridy = y;
		g.gridwidth = span;
		g.weightx = weight;
		g.anchor = anchor;
		g.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		g.insets = CELL;
		layout.setConstraints(l, g);
		l.setVisible(true);
	}

	private JLabel[] slot(int i)
	{
		while (slots.size() <= i)
		{
			JLabel[] s = {label(SwingConstants.RIGHT, false), label(SwingConstants.LEFT, true),
				label(SwingConstants.RIGHT, false)};
			for (JLabel l : s)
			{
				add(l);
			}
			slots.add(s);
		}
		return slots.get(i);
	}

	private JLabel note(int i)
	{
		while (notes.size() <= i)
		{
			JLabel l = label(SwingConstants.LEFT, true);
			l.setFont(font);
			l.setForeground(textColor);
			add(l);
			notes.add(l);
		}
		return notes.get(i);
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
		l.setVisible(false);
		return l;
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension d = super.getPreferredSize();
		return new Dimension(Math.max(width, d.width), d.height);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}
}
