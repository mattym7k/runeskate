package com.gielinorskate.ui;

import com.gielinorskate.controller.LayoutCode;
import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * "Customise controller": each pad button's action on the board and on foot, starting from a preset, saved as the
 * Custom preset; and layout codes to share (copy, and paste with a preview of what changes). Shown in place of the
 * side panel's content with a Back button. Swing only: every method runs on the EDT.
 */
class ControllerLayoutView extends JPanel
{
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 16;
	private static final Color OK_COLOR = new Color(120, 200, 120);
	private static final Color ERROR_COLOR = new Color(255, 150, 120);

	private final Consumer<String> onSave;
	private final JComboBox<String> startFrom = new JComboBox<>(new String[]{"Skate 3", "Your saved layout",
		"Nothing (blank)", "Tony Hawk's American Wasteland"});
	private final Map<PadButton, JComboBox<PadAction>> board = new EnumMap<>(PadButton.class);
	private final Map<PadButton, JComboBox<PadAction>> foot = new EnumMap<>(PadButton.class);
	private final JLabel status = new JLabel();
	/** The layout in the editor, including any air actions (kept, not edited here). */
	private PadPreset editing = PadPreset.skate3();
	/** The saved Custom layout, for "Your saved layout"; null when there is none. */
	private PadPreset saved;
	/** True while the editor is being filled in, so the combos' events don't count as edits. */
	private boolean loading;

	/** @param onSave the layout code to save as the Custom preset (and select it) */
	ControllerLayoutView(Runnable onBack, Consumer<String> onSave)
	{
		this.onSave = onSave;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton back = new JButton("Back");
		back.setName("layout:back");
		back.setFocusable(false);
		back.addActionListener(e -> onBack.run());
		top.add(back, BorderLayout.WEST);
		JLabel title = new JLabel("Customise controller");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(text("Pick what each button does on the board and on foot. Save makes it your Custom controller "
			+ "preset. The left stick steers and leans; the right one flicks, or with a flip or grab button in the "
			+ "layout it turns the camera (the left stick and d-pad then aim the button tricks)."));

		JPanel from = new JPanel(new BorderLayout(4, 0));
		from.setBackground(ColorScheme.DARK_GRAY_COLOR);
		from.setBorder(new EmptyBorder(6, 0, 4, 0));
		JLabel fromLabel = small("Start from");
		from.add(fromLabel, BorderLayout.WEST);
		startFrom.setName("layout:startFrom");
		startFrom.setFocusable(false);
		from.add(startFrom, BorderLayout.CENTER);
		JButton load = new JButton("Load");
		load.setName("layout:load");
		load.setFocusable(false);
		load.addActionListener(e -> loadStart());
		from.add(load, BorderLayout.EAST);
		row(body, from);

		for (PadButton b : PadButton.values())
		{
			body.add(buttonRows(b));
		}

		JPanel actions = new JPanel(new GridLayout(0, 1, 0, 3));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		actions.setBorder(new EmptyBorder(8, 0, 4, 0));
		actions.add(button("Save as my Custom preset", "layout:save", e -> save()));
		actions.add(button("Copy layout code", "layout:copy", e -> copy()));
		actions.add(button("Paste layout code...", "layout:paste", e -> paste()));
		row(body, actions);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(status);
		add(body, BorderLayout.CENTER);
	}

	/** Shows the editor on {@code current} (the preset in use); {@code saved} is the stored Custom one, or null. */
	void showFor(PadPreset current, PadPreset saved)
	{
		this.saved = saved;
		startFrom.setEnabled(true);
		load(current);
		setStatus(null, null);
	}

	/** The layout in the editor. */
	PadPreset edited()
	{
		return editing;
	}

	/** Sets {@code button}'s action in {@code context} as the player would. */
	void choose(PadButton button, PadContext context, PadAction action)
	{
		(context == PadContext.FOOT ? foot : board).get(button).setSelectedItem(action);
	}

	/** Its layout code. */
	String code()
	{
		return LayoutCode.encode(editing);
	}

	void save()
	{
		onSave.accept(code());
		saved = editing;
		setStatus("Saved: the pad now plays your Custom preset.", OK_COLOR);
	}

	/**
	 * Reads a pasted code: null and the editor unchanged when it is refused (the reason shown), else the changes
	 * it would make (empty when none).
	 */
	List<String> preview(String code)
	{
		LayoutCode.Result r = LayoutCode.decode(code);
		if (!r.ok())
		{
			setStatus(r.error, ERROR_COLOR);
			return null;
		}
		return LayoutCode.changes(editing, r.preset);
	}

	/** Uses a pasted code (already previewed): into the editor and saved. */
	void apply(String code)
	{
		LayoutCode.Result r = LayoutCode.decode(code);
		if (r.ok())
		{
			load(r.preset);
			save();
		}
	}

	private void loadStart()
	{
		int i = startFrom.getSelectedIndex();
		if (i == 1 && saved == null)
		{
			setStatus("You have no saved layout yet.", ERROR_COLOR);
			return;
		}
		load(i == 0 ? PadPreset.skate3() : i == 1 ? saved : i == 3 ? PadPreset.thaw() : new PadPreset());
		setStatus("Loaded. Save to use it.", OK_COLOR);
	}

	private void load(PadPreset p)
	{
		loading = true;
		try
		{
			editing = p;
			for (PadButton b : PadButton.values())
			{
				board.get(b).setSelectedItem(p.action(b, PadContext.BOARD));
				foot.get(b).setSelectedItem(p.action(b, PadContext.FOOT));
			}
		}
		finally
		{
			loading = false;
		}
	}

	private void copy()
	{
		String c = code();
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(c), null);
			setStatus("Copied: " + c, OK_COLOR);
		}
		catch (IllegalStateException e)
		{
			setStatus("The clipboard is busy. Your code: " + c, ERROR_COLOR);
		}
	}

	private void paste()
	{
		String clip = "";
		try
		{
			Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			if (data instanceof String && ((String) data).trim().startsWith(LayoutCode.PREFIX))
			{
				clip = ((String) data).trim();
			}
		}
		catch (IllegalStateException | UnsupportedFlavorException | IOException e)
		{
			// nothing usable on the clipboard: an empty box to paste into
		}
		Object typed = JOptionPane.showInputDialog(this, "Paste a RuneSkate layout code (it starts with "
			+ LayoutCode.PREFIX + LayoutCode.VERSION + ":)", "Paste layout code", JOptionPane.PLAIN_MESSAGE, null,
			null, clip);
		if (!(typed instanceof String))
		{
			return;
		}
		String code = (String) typed;
		List<String> changes = preview(code);
		if (changes == null)
		{
			return;
		}
		if (changes.isEmpty())
		{
			setStatus("That code is the layout you have already.", OK_COLOR);
			return;
		}
		StringBuilder msg = new StringBuilder("This layout changes:\n");
		int shown = 0;
		for (String c : changes)
		{
			if (shown++ == 20)
			{
				msg.append("... and ").append(changes.size() - 20).append(" more\n");
				break;
			}
			msg.append("  ").append(c).append('\n');
		}
		msg.append("\nUse it as your Custom preset?");
		int answer = JOptionPane.showConfirmDialog(this, msg.toString(), "Paste layout code",
			JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION)
		{
			apply(code);
		}
	}

	private JPanel buttonRows(PadButton b)
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 1));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(3, 4, 3, 4));
		JLabel name = new JLabel(b.label + "  (" + b.keyName + ")", new ControllerGlyphIcon(16, PadWords.glyph(b)),
			SwingConstants.LEFT);
		name.setIconTextGap(6);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		p.add(name);
		p.add(choice(b, PadContext.BOARD, board));
		p.add(choice(b, PadContext.FOOT, foot));
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(0, 0, 3, 0));
		wrap.add(p, BorderLayout.CENTER);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
		return wrap;
	}

	private JPanel choice(PadButton b, PadContext context, Map<PadButton, JComboBox<PadAction>> into)
	{
		List<PadAction> allowed = new ArrayList<>();
		for (PadAction a : PadAction.values())
		{
			if (a.allowedIn(context))
			{
				allowed.add(a);
			}
		}
		JComboBox<PadAction> box = new JComboBox<>(allowed.toArray(new PadAction[0]));
		box.setName("layout:" + b.name() + ":" + context.name());
		box.setFocusable(false);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
				boolean focus)
			{
				return super.getListCellRendererComponent(list, value == PadAction.NONE ? "-" : value, index, selected,
					focus);
			}
		});
		box.addActionListener(e ->
		{
			if (!loading && box.getSelectedItem() instanceof PadAction)
			{
				editing = editing.with(b, context, (PadAction) box.getSelectedItem());
				setStatus("Not saved yet.", null);
			}
		});
		into.put(b, box);
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel where = small(context == PadContext.FOOT ? "Foot" : "Board");
		where.setPreferredSize(new Dimension(34, where.getPreferredSize().height));
		row.add(where, BorderLayout.WEST);
		row.add(box, BorderLayout.CENTER);
		return row;
	}

	private void setStatus(String text, Color color)
	{
		status.setText(text == null ? "" : "<html><div style='width:" + (CONTENT_WIDTH - 10) + "px'>"
			+ text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
		status.setForeground(color != null ? color : ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** The status line's text (tests). */
	String statusText()
	{
		return status.getText();
	}

	private static JButton button(String text, String name, java.awt.event.ActionListener l)
	{
		JButton b = new JButton(text);
		b.setName(name);
		b.setFocusable(false);
		b.addActionListener(l);
		return b;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return l;
	}

	private static Component text(String s)
	{
		return TrickBookView.wrapped(s, CONTENT_WIDTH, FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR);
	}

	private static void row(JPanel body, javax.swing.JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		body.add(c);
	}
}
