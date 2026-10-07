package com.gielinorskate.ui;

import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadTester;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * "Controller setup": where to get AntiMicroX, a button that saves the universal RuneSkate profile, the steps
 * (with Steam Input as another way), and "Test your pad": a drawn controller whose buttons light up as their pad keys
 * arrive and whose sticks show the arrows and the mouse. The test only watches, while this view is open (skating
 * or not): it reads key events through a {@link KeyEventDispatcher} that never consumes them and samples the
 * cursor a few times a second. Swing only: every method runs on the EDT.
 */
class ControllerSetupView extends JPanel
{
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 16;
	/** The cursor is sampled this often for the right stick, ms. */
	private static final int SAMPLE_MS = 33;

	/** What the view's buttons ask of the plugin. */
	interface Actions
	{
		/** Open the AntiMicroX releases page. */
		void openReleases();

		/** Save the RuneSkate profile where the player picks (a save dialog from {@code from}). */
		void saveProfile(Component from);
	}

	private final PadTester tester = new PadTester();
	private final Diagram diagram = new Diagram();
	private final JLabel modeNote = new JLabel();
	private final Timer sampler = new Timer(SAMPLE_MS, e -> sample());
	private final KeyEventDispatcher dispatcher = this::watch;
	private boolean listening;

	ControllerSetupView(Runnable onBack, Actions actions)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton back = new JButton("Back");
		back.setName("setup:back");
		back.setFocusable(false);
		back.addActionListener(e -> onBack.run());
		top.add(back, BorderLayout.WEST);
		JLabel title = new JLabel("Controller setup");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		put(body, heading("1. Get AntiMicroX"));
		put(body, text("A free program (Windows and Linux) that turns each controller button into one key. "
			+ "RuneSkate itself never reads the controller."));
		put(body, button("Get AntiMicroX (opens GitHub)", "setup:antimicrox", e -> actions.openReleases()));
		put(body, heading("2. Load the RuneSkate profile"));
		put(body, text("Save the profile, then in AntiMicroX click Load and pick it. It works with Xbox, "
			+ "PlayStation and other pads. Updated RuneSkate? Load the new profile again: every button now sends "
			+ "its own key."));
		JButton save = button("Save RuneSkate profile...", "setup:saveProfile", null);
		save.addActionListener(e -> actions.saveProfile(save));
		put(body, save);
		put(body, heading("3. Turn it on"));
		put(body, text("In the RuneSkate settings (top section) turn on Controller mode and pick a Controller "
			+ "preset. Start skating, keep the game window focused and the cursor over the game."));
		modeNote.setFont(FontManager.getRunescapeSmallFont());
		modeNote.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(modeNote);
		put(body, heading("Steam Input instead"));
		put(body, text(steamText()));
		put(body, heading("Test your pad"));
		put(body, text("Press each button: it lights up while held, and gets a tick once seen. The left stick "
			+ "shows the arrows, the right stick the mouse. Nothing is pressed for you."));
		diagram.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(diagram);
		add(body, BorderLayout.CENTER);
		sampler.setRepeats(true);
	}

	/** The Steam Input mapping: one key per button, no macros. */
	static String steamText()
	{
		StringBuilder s = new StringBuilder("Steam Input can do what the profile does. Map each button to one "
			+ "key (no macros, turbo or chords): ");
		PadButton[] all = PadButton.values();
		for (int i = 0; i < all.length; i++)
		{
			s.append(all[i].label).append(' ').append(all[i].keyName).append(i < all.length - 1 ? ", " : ". ");
		}
		s.append("Left stick: the arrow keys, as a four-way d-pad. Right stick: the mouse (joystick mouse).");
		return s.toString();
	}

	/** The view is shown: the pad test starts listening. {@code controllerMode}: whether the setting is on. */
	void opened(boolean controllerMode)
	{
		modeNote.setText(controllerMode ? "" : "<html><div style='width:" + (CONTENT_WIDTH - 10) + "px'><font "
			+ "color='#ff9678'>Controller mode is off: the pad test works, but skating reads the pad only with it "
			+ "on.</font></div></html>");
		if (!listening)
		{
			listening = true;
			tester.reset();
			KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
			sampler.start();
		}
	}

	/** The view is hidden (Back, or the plugin stops): the pad test stops listening. */
	void closed()
	{
		if (listening)
		{
			listening = false;
			KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
			sampler.stop();
			tester.reset();
		}
	}

	boolean isListening()
	{
		return listening;
	}

	/** The pad test's state (tests). */
	PadTester tester()
	{
		return tester;
	}

	/** A key event on its way through the window: shown on the pad test, never consumed (false: it goes on). */
	boolean watch(KeyEvent e)
	{
		if (e.getID() == KeyEvent.KEY_PRESSED || e.getID() == KeyEvent.KEY_RELEASED)
		{
			if (tester.key(e.getKeyCode(), e.getID() == KeyEvent.KEY_PRESSED))
			{
				diagram.repaint();
			}
		}
		return false;
	}

	private void sample()
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return;
		}
		if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null)
		{
			// another program has the keyboard: releases would not arrive here
			tester.reset();
		}
		PointerInfo p = MouseInfo.getPointerInfo();
		if (p != null)
		{
			tester.mouse(p.getLocation().x, p.getLocation().y, System.currentTimeMillis());
		}
		diagram.repaint();
	}

	private static void put(JPanel body, JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (c instanceof JButton)
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}
		body.add(c);
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(new EmptyBorder(10, 0, 3, 0));
		return l;
	}

	private static JComponent text(String s)
	{
		return TrickBookView.wrapped(s, CONTENT_WIDTH, FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR);
	}

	private static JButton button(String text, String name, java.awt.event.ActionListener l)
	{
		JButton b = new JButton(text);
		b.setName(name);
		b.setFocusable(false);
		if (l != null)
		{
			b.addActionListener(l);
		}
		return b;
	}

	/** A controller drawn from above: shoulders, the two sticks, the d-pad, Back / Start, the face buttons. */
	private final class Diagram extends JComponent
	{
		private static final int W = CONTENT_WIDTH;
		private static final int H = 150;
		private static final int G = 18;

		Diagram()
		{
			Dimension d = new Dimension(W, H);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(ColorScheme.DARKER_GRAY_COLOR);
				g.fill(new RoundRectangle2D.Float(2, 26, W - 4, H - 30, 40, 40));
				int cx = W / 2;
				button(g, PadButton.LT, 8, 2);
				button(g, PadButton.LB, 40, 2);
				button(g, PadButton.RB, W - 40 - glyphW(PadButton.RB), 2);
				button(g, PadButton.RT, W - 8 - glyphW(PadButton.RT), 2);
				button(g, PadButton.BACK, cx - 6 - glyphW(PadButton.BACK), 36);
				button(g, PadButton.START, cx + 6, 36);
				stick(g, 40, 64, tester.leftX(), tester.leftY(), PadButton.L3);
				stick(g, W - 70, 114, tester.rightX(), tester.rightY(), PadButton.R3);
				// d-pad: a cross of four
				int dx = 66;
				int dy = 106;
				button(g, PadButton.DPAD_UP, dx, dy - G);
				button(g, PadButton.DPAD_DOWN, dx, dy + G);
				button(g, PadButton.DPAD_LEFT, dx - G, dy);
				button(g, PadButton.DPAD_RIGHT, dx + G, dy);
				// face buttons: a diamond
				int fx = W - 42;
				int fy = 60;
				button(g, PadButton.Y, fx, fy - G);
				button(g, PadButton.A, fx, fy + G);
				button(g, PadButton.X, fx - G, fy);
				button(g, PadButton.B, fx + G, fy);
			}
			finally
			{
				g.dispose();
			}
		}

		private int glyphW(PadButton b)
		{
			return ControllerGlyphs.glyphWidth(PadWords.glyph(b), G);
		}

		/** One button: bright while held, dim otherwise, with a green dot once it has been seen. */
		private void button(Graphics2D g, PadButton b, int x, int y)
		{
			Composite old = g.getComposite();
			boolean down = tester.isDown(b);
			if (!down)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			}
			ControllerGlyphs.paint(g, PadWords.glyph(b), x, y, G);
			g.setComposite(old);
			if (down)
			{
				g.setColor(Color.WHITE);
				g.setStroke(new BasicStroke(2f));
				g.draw(new Ellipse2D.Float(x - 2, y - 2, glyphW(b) + 4, G + 4));
			}
			else if (tester.wasSeen(b))
			{
				g.setColor(new Color(0, 200, 50));
				g.fill(new Ellipse2D.Float(x + glyphW(b) - 5, y + G - 5, 6, 6));
			}
		}

		/** A stick well with its knob pushed (x, y in -1..1); clicking it in (L3 / R3) lights the knob. */
		private void stick(Graphics2D g, int x, int y, float tx, float ty, PadButton click)
		{
			int r = 20;
			g.setColor(new Color(48, 52, 60));
			g.fill(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r));
			g.setColor(new Color(150, 156, 170));
			g.draw(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r));
			int k = 9;
			float kx = x + tx * (r - k);
			float ky = y + ty * (r - k);
			boolean moved = tx != 0 || ty != 0;
			g.setColor(tester.isDown(click) ? Color.WHITE : moved ? new Color(120, 200, 255) : new Color(96, 102, 116));
			g.fill(new Ellipse2D.Float(kx - k, ky - k, 2 * k, 2 * k));
			g.setColor(Color.BLACK);
			g.setFont(FontManager.getRunescapeSmallFont());
			String label = click == PadButton.L3 ? "L" : "R";
			g.drawString(label, kx - g.getFontMetrics().stringWidth(label) / 2f, ky + 4);
			if (tester.wasSeen(click))
			{
				g.setColor(new Color(0, 200, 50));
				g.fill(new Ellipse2D.Float(x + r - 6, y + r - 6, 6, 6));
			}
		}
	}
}
