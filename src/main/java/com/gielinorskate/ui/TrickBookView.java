package com.gielinorskate.ui;

import com.gielinorskate.tricks.Gesture;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Trick Book: {@link TrickBook}'s sections as plain Swing components (labels, and non-editable wrapped text
 * areas for the longer lines; no HTML), shown in place of the side panel's content with a Back button. Built when
 * opened, and again only when the settings it is worded for change. Swing only: every method runs on the EDT.
 */
class TrickBookView extends JPanel
{
	private static final Color POINTS_COLOR = new Color(200, 168, 90);
	private static final Color FLICK_COLOR = new Color(120, 200, 255);
	private static final Color WIND_UP_COLOR = new Color(150, 155, 165);
	private static final int GLYPH_SIZE = 26;
	private static final int PAD_GLYPH_SIZE = 18;
	/** Width of the side panel's content (the panel's 8 px border each side). */
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 16;
	/** An entry row's inner padding each side. */
	private static final int ROW_PAD = 4;
	private static final int ICON_GAP = 6;

	private final JPanel body = new JPanel();
	/** The settings the body was last built for; null before the first build. */
	private TrickBook.Settings builtFor;

	TrickBookView(Runnable onBack)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton back = new JButton("Back");
		back.setName("trickBook:back");
		back.setFocusable(false);
		back.addActionListener(e -> onBack.run());
		top.add(back, BorderLayout.WEST);
		JLabel title = new JLabel("Trick Book");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(body, BorderLayout.CENTER);
	}

	/** Shows the book worded for {@code s}, building it if it was built for other settings (or never). */
	void showFor(TrickBook.Settings s)
	{
		if (!s.equals(builtFor))
		{
			builtFor = s;
			build(TrickBook.build(s));
		}
		scrollRectToVisible(new Rectangle(0, 0, 1, 1));
	}

	private void build(List<TrickBook.Section> book)
	{
		body.removeAll();
		for (TrickBook.Section section : book)
		{
			body.add(heading(section.title));
			if (section.intro != null)
			{
				body.add(wrapped(section.intro, CONTENT_WIDTH, FontManager.getRunescapeSmallFont(),
					ColorScheme.LIGHT_GRAY_COLOR));
			}
			if (!section.table.isEmpty())
			{
				body.add(table(section.table));
			}
			for (TrickBook.Entry e : section.entries)
			{
				body.add(row(e));
			}
		}
		body.revalidate();
		body.repaint();
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

	/** Plain text wrapped at word breaks to {@code width}, sized for that width up front. */
	static JTextArea wrapped(String text, int width, Font font, Color color)
	{
		JTextArea a = new JTextArea(text);
		a.setLineWrap(true);
		a.setWrapStyleWord(true);
		a.setEditable(false);
		a.setFocusable(false);
		a.setOpaque(false);
		a.setHighlighter(null);
		a.setBorder(null);
		a.setFont(font);
		a.setForeground(color);
		// the wrapped height for this width, so the panel lays out right the first time
		a.setSize(width, Short.MAX_VALUE);
		int h = a.getPreferredSize().height;
		a.setPreferredSize(new Dimension(width, h));
		a.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		a.setAlignmentX(Component.LEFT_ALIGNMENT);
		return a;
	}

	private static JComponent table(List<List<String>> rows)
	{
		int cols = rows.get(0).size();
		JPanel t = new JPanel(new GridLayout(0, cols, 4, 1));
		t.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		t.setBorder(new EmptyBorder(4, 4, 4, 4));
		for (int r = 0; r < rows.size(); r++)
		{
			for (String cell : rows.get(r))
			{
				JLabel l = new JLabel(cell);
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setForeground(r == 0 ? POINTS_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
				t.add(l);
			}
		}
		t.setAlignmentX(Component.LEFT_ALIGNMENT);
		t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
		return t;
	}

	private static JComponent row(TrickBook.Entry e)
	{
		JPanel row = new JPanel(new BorderLayout(ICON_GAP, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(ROW_PAD, ROW_PAD, ROW_PAD, ROW_PAD)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JComponent icon = null;
		if (e.gesture != null)
		{
			icon = new FlickGlyph(e.gesture);
		}
		else if (e.pad != null && ControllerGlyphs.hasGlyphs(e.pad))
		{
			icon = new JLabel(new ControllerGlyphIcon(PAD_GLYPH_SIZE, SkatePanel.glyphsOf(e.pad)));
			icon.setAlignmentY(Component.TOP_ALIGNMENT);
		}
		int textWidth = CONTENT_WIDTH - 2 * ROW_PAD;
		if (icon != null)
		{
			JPanel west = new JPanel(new BorderLayout());
			west.setOpaque(false);
			west.add(icon, BorderLayout.NORTH);
			row.add(west, BorderLayout.WEST);
			textWidth -= icon.getPreferredSize().width + ICON_GAP;
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JPanel nameLine = new JPanel(new BorderLayout(6, 0));
		nameLine.setOpaque(false);
		nameLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel name = new JLabel(e.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		nameLine.add(name, BorderLayout.CENTER);
		if (e.points != null)
		{
			JLabel points = new JLabel(e.points);
			points.setFont(FontManager.getRunescapeSmallFont());
			points.setForeground(POINTS_COLOR);
			nameLine.add(points, BorderLayout.EAST);
		}
		nameLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameLine.getPreferredSize().height));
		text.add(nameLine);
		String detail = e.detail;
		if (e.key != null && !detail.equals("Press " + e.key))
		{
			detail = detail.isEmpty() ? "Key: " + e.key : detail + "\nKey: " + e.key;
		}
		if (!detail.isEmpty())
		{
			text.add(wrapped(detail, textWidth, FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR));
		}
		row.add(text, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** A trick's flick picture. */
	private static final class FlickGlyph extends JComponent
	{
		private final Gesture gesture;

		FlickGlyph(Gesture gesture)
		{
			this.gesture = gesture;
			Dimension d = new Dimension(GLYPH_SIZE, GLYPH_SIZE);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			GestureGlyphPainter.paint((Graphics2D) g, gesture, 0, 0, GLYPH_SIZE, WIND_UP_COLOR, FLICK_COLOR);
		}
	}
}
