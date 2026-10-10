package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The Trick Book: {@link TrickBook}'s sections as plain Swing components (labels, and non-editable wrapped text
 * areas for the longer lines; no HTML), shown in place of the side panel's content with a Back button. Built when
 * opened, and again only when the settings it is worded for change. Swing only: every method runs on the EDT.
 */
class TrickBookView extends JPanel
{
	private static final Color POINTS_COLOR = new Color(200, 168, 90);
	/** A flick picture's size, px. */
	private static final int GLYPH_SIZE = 26;
	/** An entry row's inner padding each side. */
	private static final int ROW_PAD = 4;
	private static final int ICON_GAP = 6;

	private final JPanel body = column();
	/** The settings the body was last built for; null before the first build. */
	private TrickBook.Settings builtFor;

	TrickBookView(Runnable onBack)
	{
		subView(this, "Trick Book", "trickBook:back", onBack);
		add(body, BorderLayout.CENTER);
	}

	/** Shows the book worded for {@code s}, building it if it was built for other settings (or never). */
	void showFor(TrickBook.Settings s)
	{
		if (!s.equals(builtFor))
		{
			builtFor = s;
			body.removeAll();
			for (TrickBook.Section section : TrickBook.build(s))
			{
				body.add(heading(section.title));
				if (section.intro != null)
					body.add(wrapped(section.intro, CONTENT_WIDTH));
				if (!section.table.isEmpty())
					body.add(table(section.table));
				for (TrickBook.Entry e : section.entries)
					body.add(row(e));
			}
			body.revalidate();
			body.repaint();
		}
		scrollRectToVisible(new Rectangle(0, 0, 1, 1));
	}

	private static JComponent table(List<List<String>> rows)
	{
		JPanel t = left(new JPanel(new GridLayout(0, rows.get(0).size(), 4, 1)));
		t.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		t.setBorder(new EmptyBorder(4, 4, 4, 4));
		for (List<String> row : rows)
		{
			for (String cell : row)
				// the first row is the headers
				t.add(label(cell, SMALL_FONT, row == rows.get(0) ? POINTS_COLOR
					: ColorScheme.LIGHT_GRAY_COLOR));
		}
		return stretch(t);
	}

	private static JComponent row(TrickBook.Entry e)
	{
		JPanel row = left(new JPanel(new BorderLayout(ICON_GAP, 0)));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(ROW_PAD, ROW_PAD, ROW_PAD, ROW_PAD)));

		JComponent icon = null;
		if (e.gesture != null)
		{
			icon = new JComponent()
			{
				@Override
				protected void paintComponent(Graphics g)
				{
					// the wind-up grey, the flick light blue
					GestureGlyphPainter.paint((Graphics2D) g, e.gesture, 0, 0, GLYPH_SIZE, new Color(150, 155, 165),
						new Color(120, 200, 255));
				}
			};
			Dimension d = new Dimension(GLYPH_SIZE, GLYPH_SIZE);
			icon.setPreferredSize(d);
			icon.setMinimumSize(d);
			icon.setMaximumSize(d);
		}
		else if (e.pad != null && ControllerGlyphs.hasGlyphs(e.pad))
		{
			icon = new JLabel(new ControllerGlyphIcon(18, SkatePanel.glyphsOf(e.pad)));
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

		JPanel text = column();
		text.setOpaque(false);
		JPanel nameLine = left(new JPanel(new BorderLayout(6, 0)));
		nameLine.setOpaque(false);
		nameLine.add(label(e.name, SMALL_FONT, Color.WHITE), BorderLayout.CENTER);
		if (e.points != null)
			nameLine.add(label(e.points, SMALL_FONT, POINTS_COLOR), BorderLayout.EAST);
		text.add(stretch(nameLine));
		String detail = e.detail;
		if (e.key != null && !detail.equals("Press " + e.key))
			detail = detail.isEmpty() ? "Key: " + e.key : detail + "\nKey: " + e.key;
		if (!detail.isEmpty())
			text.add(wrapped(detail, textWidth));
		row.add(text, BorderLayout.CENTER);
		return stretch(row);
	}
}
