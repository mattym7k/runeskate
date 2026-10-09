package com.gielinorskate.ui;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.*;

/** The side panel's shared Swing pieces: headings, wrapped text, buttons and the sub-views' Back bar. EDT only. */
final class Widgets
{
/** Wrap width of the panel's HTML labels, px. */
static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 40;
/** Width of the side panel's content (the panel's 8 px border each side). */
static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 16;
static final Font SMALL_FONT = FontManager.getRunescapeSmallFont();

private Widgets()
{
}

/** An orange section heading. */
static JLabel heading(String text)
{
JLabel l = label(text, FontManager.getRunescapeBoldFont(), ColorScheme.BRAND_ORANGE);
l.setBorder(new EmptyBorder(10, 0, 3, 0));
return l;
}

/** A left-aligned label in {@code font} (the default one when null) and {@code color}. */
static JLabel label(String text, Font font, Color color)
{
JLabel l = left(new JLabel(text));
if (font != null)
l.setFont(font);
l.setForeground(color);
return l;
}

/** A light grey label in the default font. */
static JLabel greyLabel(String text)
{
return label(text, null, ColorScheme.LIGHT_GRAY_COLOR);
}

/** A light grey label in the small font. */
static JLabel small(String text)
{
return label(text, SMALL_FONT, ColorScheme.LIGHT_GRAY_COLOR);
}

static String html(String body)
{
return html(body, TEXT_WIDTH);
}

static String html(String body, int width)
{
return "<html><div style='width:" + width + "px'>" + body + "</div></html>";
}

static String esc(String s)
{
return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
}

static <T extends JComponent> T left(T c)
{
c.setAlignmentX(Component.LEFT_ALIGNMENT);
return c;
}

/** {@code c} on the panel's background. */
static <T extends JComponent> T dark(T c)
{
c.setBackground(ColorScheme.DARK_GRAY_COLOR);
return c;
}

/** A left-aligned panel on the panel's background stacking its children top to bottom. */
static JPanel column()
{
JPanel p = left(dark(new JPanel()));
p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
return p;
}

/** {@code c} as tall as it likes and as wide as the panel lets it be. */
static <T extends JComponent> T stretch(T c)
{
return stretch(c, c.getPreferredSize().height);
}

/** {@code c} at most {@code height} px tall and as wide as the panel lets it be. */
static <T extends JComponent> T stretch(T c, int height)
{
c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
return c;
}

/** A button that never takes the keyboard focus from the game; {@code l} may be null. */
static JButton button(String text, String name, ActionListener l)
{
JButton b = new JButton(text);
b.setName(name);
b.setFocusable(false);
if (l != null)
b.addActionListener(l);
return b;
}

/** Lays out a sub-view: a Back button ({@code backName}) and the title on top, the body added after. */
static void subView(JPanel view, String title, String backName, Runnable onBack)
{
view.setLayout(new BorderLayout());
dark(view);
JPanel top = dark(new JPanel(new BorderLayout(8, 0)));
top.add(button("Back", backName, e -> onBack.run()), BorderLayout.WEST);
top.add(label(title, FontManager.getRunescapeBoldFont(), Color.WHITE), BorderLayout.CENTER);
view.add(top, BorderLayout.NORTH);
}

/** Plain small text wrapped at word breaks to {@code width}, sized for that width up front. */
static JTextArea wrapped(String text, int width)
{
JTextArea a = left(new JTextArea(text));
a.setLineWrap(true);
a.setWrapStyleWord(true);
a.setEditable(false);
a.setFocusable(false);
a.setOpaque(false);
a.setHighlighter(null);
a.setBorder(null);
a.setFont(SMALL_FONT);
a.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
// the wrapped height for this width, so the panel lays out right the first time
a.setSize(width, Short.MAX_VALUE);
int h = a.getPreferredSize().height;
a.setPreferredSize(new Dimension(width, h));
a.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
return a;
}
}
