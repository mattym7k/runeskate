package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import com.gielinorskate.Text;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadTester;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.*;
import net.runelite.client.input.KeyListener;
import net.runelite.client.ui.ColorScheme;

/**
 * "Controller setup": where to get AntiMicroX, a button that saves the universal RuneSkate profile, the steps
 * (with Steam Input as another way), and "Test your pad": a drawn controller whose buttons light up as their pad keys
 * arrive and whose sticks show the arrows and the mouse. The test only watches, while this view is open (skating
 * or not): it registers a key listener with the client (which sees keys while the game canvas has focus and never
 * consumes them) and samples the cursor a few times a second. Swing only: every method runs on the EDT.
 */
class ControllerSetupView extends JPanel implements KeyListener
{
	/** Height of the drawn controller, and of its buttons, px. */
	private static final int H = 150;
	private static final int G = 18;
	/** A button's tick once it has been seen. */
	private static final Color SEEN_COLOR = new Color(0, 200, 50);

	private final PadTester tester = new PadTester();
	private final JComponent diagram = new JComponent()
	{
		@Override
		protected void paintComponent(Graphics g)
		{
			paintPad((Graphics2D) g.create());
		}
	};
	private final JLabel modeNote = left(new JLabel());
	/** Samples the cursor for the right stick, every 33 ms. */
	private final Timer sampler = new Timer(33, e -> sample());
	private final Supplier<SkatePanel.ControllerActions> actions;
	boolean listening;

	ControllerSetupView(Runnable onBack, Supplier<SkatePanel.ControllerActions> actions)
	{
		this.actions = actions;
		subView(this, "Controller setup", "setup:back", onBack);
		JPanel body = column();
		body.add(heading("1. Get AntiMicroX"));
		body.add(text(Text.get("setup.get")));
		body.add(stretch(button("Get AntiMicroX (opens GitHub)", "setup:antimicrox", e -> actions.get().openReleases())));
		body.add(heading("2. Load the RuneSkate profile"));
		body.add(text(Text.get("setup.load")));
		JButton save = stretch(button("Save RuneSkate profile...", "setup:saveProfile", null));
		save.addActionListener(e -> actions.get().saveProfile(save));
		body.add(save);
		body.add(heading("3. Turn it on"));
		body.add(text(Text.get("setup.on")));
		modeNote.setFont(SMALL_FONT);
		body.add(modeNote);
		body.add(heading("Steam Input instead"));
		body.add(text(steamText()));
		body.add(heading("Test your pad"));
		body.add(text(Text.get("setup.test")));
		Dimension d = new Dimension(CONTENT_WIDTH, H);
		diagram.setPreferredSize(d);
		diagram.setMinimumSize(d);
		diagram.setMaximumSize(d);
		body.add(left(diagram));
		add(body, BorderLayout.CENTER);
	}

	/** The Steam Input mapping: one key per button, no macros. */
	static String steamText()
	{
		return Text.get("setup.steam", Arrays.stream(PadButton.values()).map(b -> b.label + " " + b.keyName)
			.collect(Collectors.joining(", ")));
	}

	/** The view is shown: the pad test starts listening. {@code controllerMode}: whether the setting is on. */
	void opened(boolean controllerMode)
	{
		modeNote.setText(controllerMode ? "" : html(Text.get("setup.off"), CONTENT_WIDTH - 10));
		if (!listening)
		{
			listening = true;
			tester.reset();
			actions.get().watchKeys(this, true);
			sampler.start();
		}
	}

	/** The view is hidden (Back, or the plugin stops): the pad test stops listening. */
	void closed()
	{
		if (listening)
		{
			listening = false;
			actions.get().watchKeys(this, false);
			sampler.stop();
			tester.reset();
		}
	}

	/** The pad test's state (tests). */
	PadTester tester()
	{
		return tester;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		SwingUtilities.invokeLater(() -> watch(e.getKeyCode(), true));
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		SwingUtilities.invokeLater(() -> watch(e.getKeyCode(), false));
	}

	/** A key pressed or released in the game window: shown on the pad test (the event itself is never consumed). */
	void watch(int keyCode, boolean down)
	{
		if (listening && tester.key(keyCode, down))
			diagram.repaint();
	}

	private void sample()
	{
		if (GraphicsEnvironment.isHeadless())
			return;
		Window window = SwingUtilities.getWindowAncestor(this);
		if (window != null && !window.isFocused())
			// another program has the keyboard: releases would not arrive here
			tester.reset();
		PointerInfo p = MouseInfo.getPointerInfo();
		if (p != null)
			tester.mouse(p.getLocation().x, p.getLocation().y, System.currentTimeMillis());
		diagram.repaint();
	}

	private static JComponent text(String s)
	{
		return wrapped(s, CONTENT_WIDTH);
	}

	/** A controller drawn from above: shoulders, the two sticks, the d-pad, Back / Start, the face buttons. */
	private void paintPad(Graphics2D g)
	{
		int w = CONTENT_WIDTH;
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fill(new RoundRectangle2D.Float(2, 26, w - 4, H - 30, 40, 40));
			int cx = w / 2;
			padButton(g, PadButton.LT, 8, 2);
			padButton(g, PadButton.LB, 40, 2);
			padButton(g, PadButton.RB, w - 40 - glyphW(PadButton.RB), 2);
			padButton(g, PadButton.RT, w - 8 - glyphW(PadButton.RT), 2);
			padButton(g, PadButton.BACK, cx - 6 - glyphW(PadButton.BACK), 36);
			padButton(g, PadButton.START, cx + 6, 36);
			stick(g, 40, 64, tester.leftX(), tester.leftY(), PadButton.L3);
			stick(g, w - 70, 114, tester.rightX(), tester.rightY(), PadButton.R3);
			// d-pad: a cross of four round (66, 106)
			padButton(g, PadButton.DPAD_UP, 66, 106 - G);
			padButton(g, PadButton.DPAD_DOWN, 66, 106 + G);
			padButton(g, PadButton.DPAD_LEFT, 66 - G, 106);
			padButton(g, PadButton.DPAD_RIGHT, 66 + G, 106);
			// face buttons: a diamond round (w - 42, 60)
			padButton(g, PadButton.Y, w - 42, 60 - G);
			padButton(g, PadButton.A, w - 42, 60 + G);
			padButton(g, PadButton.X, w - 42 - G, 60);
			padButton(g, PadButton.B, w - 42 + G, 60);
		}
		finally
		{
			g.dispose();
		}
	}

	private static int glyphW(PadButton b)
	{
		return ControllerGlyphs.glyphWidth(PadWords.glyph(b), G);
	}

	/** One button: bright while held, dim otherwise, with a green dot once it has been seen. */
	private void padButton(Graphics2D g, PadButton b, int x, int y)
	{
		Composite old = g.getComposite();
		boolean down = tester.isDown(b);
		if (!down)
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
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
			g.setColor(SEEN_COLOR);
			g.fill(new Ellipse2D.Float(x + glyphW(b) - 5, y + G - 5, 6, 6));
		}
	}

	/** A stick well with its knob pushed (x, y in -1..1); clicking it in (L3 / R3) lights the knob. */
	private void stick(Graphics2D g, int x, int y, float tx, float ty, PadButton click)
	{
		int r = 20;
		Ellipse2D well = new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r);
		g.setColor(new Color(48, 52, 60));
		g.fill(well);
		g.setColor(new Color(150, 156, 170));
		g.draw(well);
		int k = 9;
		float kx = x + tx * (r - k);
		float ky = y + ty * (r - k);
		boolean moved = tx != 0 || ty != 0;
		g.setColor(tester.isDown(click) ? Color.WHITE : moved ? new Color(120, 200, 255) : new Color(96, 102, 116));
		g.fill(new Ellipse2D.Float(kx - k, ky - k, 2 * k, 2 * k));
		g.setColor(Color.BLACK);
		g.setFont(SMALL_FONT);
		String label = click == PadButton.L3 ? "L" : "R";
		g.drawString(label, kx - g.getFontMetrics().stringWidth(label) / 2f, ky + 4);
		if (tester.wasSeen(click))
		{
			g.setColor(SEEN_COLOR);
			g.fill(new Ellipse2D.Float(x + r - 6, y + r - 6, 6, 6));
		}
	}
}
