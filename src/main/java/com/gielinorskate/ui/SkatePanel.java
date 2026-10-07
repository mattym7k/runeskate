package com.gielinorskate.ui;

import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.progression.SessionGoals;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar panel: a Start / Stop button (with the reason when skating can't start), the Open Trick Book button,
 * the basics, the Skating level with its XP bar, the session goals, the board designs (grip, deck and wheels
 * pickers), session stats and two display toggles. The Trick Book ({@link TrickBookView}, every move), Customise
 * controller ({@link ControllerLayoutView}) and Controller setup ({@link ControllerSetupView}, with the pad test)
 * each replace this content while open, with a Back button.
 * Swing only: every method runs on the EDT. It talks to the plugin through the callbacks it is given, and is fed
 * client-thread data through {@link #update}.
 */
public class SkatePanel extends PluginPanel
{
	/** The project's optional Ko-fi page, opened by the button at the bottom of the panel. */
	static final String SUPPORT_URL = "https://ko-fi.com/runeskate_project";

	private static final Color REFUSAL_COLOR = new Color(255, 150, 120);
	/** The XP bar's fill: the green of the game's skill progress bars. */
	private static final Color XP_BAR_COLOR = new Color(0, 160, 50);
	/** Height of the drawn controller buttons. */
	private static final int PAD_GLYPH_SIZE = 18;
	/** Wrap width of the panel's HTML labels, px. */
	private static final int TEXT_WIDTH = PANEL_WIDTH - 40;

	private final JButton startStop = new JButton("Start skating");
	private final JButton openBook = new JButton("Open Trick Book");
	private final JButton openLayout = new JButton("Customise controller");
	private final JButton openSetup = new JButton("Controller setup");
	/** The panel's own content, swapped out for a sub-view (the Trick Book, ...) while one is open. */
	private final JPanel content = new JPanel();
	private final TrickBookView book = new TrickBookView(this::closeTrickBook);
	private final ControllerLayoutView layoutView = new ControllerLayoutView(this::closeView, this::saveLayout);
	private final ControllerSetupView setupView = new ControllerSetupView(this::closeView,
		new ControllerSetupView.Actions()
		{
			@Override
			public void openReleases()
			{
				controllerActions.openReleases();
			}

			@Override
			public void saveProfile(Component from)
			{
				controllerActions.saveProfile(from);
			}
		});
	/** The sub-view shown in place of {@link #content}, or null. */
	private JComponent view;
	private boolean bookOpen;
	/** The controller preset in use, its name, and the saved Custom layout (null when none). */
	private PadPreset preset = PadPreset.skate3();
	private String presetName = "Skate 3";
	private PadPreset savedCustom;
	/** What the controller views ask of the plugin; nothing until it sets them. */
	private ControllerActions controllerActions = new ControllerActions()
	{
		@Override
		public void saveCustomLayout(String code)
		{
		}

		@Override
		public void openReleases()
		{
		}

		@Override
		public void saveProfile(Component from)
		{
		}
	};
	private final JLabel refusal = new JLabel();
	private final JLabel stats = new JLabel();
	private final JLabel skillLine = new JLabel();
	private final ProgressBar xpBar = new ProgressBar();
	/** The Board section: the preview strip, then a thumbnail grid per part. */
	private final JPanel boardSection = new JPanel();
	private final JLabel goalsHeading = heading("Daily goals");
	private final JLabel goalsText = new JLabel();
	private final Consumer<BoardDesign> onDesign;
	private final BoardDesigns designs;
	/** What the "+ Custom" tiles and the custom designs' menu do; none until the plugin sets them. */
	private CustomActions customActions;
	private final DesignThumbs thumbs = new DesignThumbs();
	/** The level and designs the Board section was last built for. */
	private int designLevel = -1;
	private BoardLook lookInUse;
	private final JCheckBox grindEdges = new JCheckBox("Show grindable edges");
	private final JCheckBox controlsCard = new JCheckBox("Show controls card");
	private final JLabel basics = new JLabel();
	/** Controller mode's basics: one row per pad control, its buttons drawn in front. */
	private final JPanel padBasics = new JPanel();
	private boolean controller;
	private String toggleKey = "Ctrl+K";
	private String boardKey = "F";
	private boolean mouseTricks = true;
	private boolean keyboardTricks;
	private boolean mirror;
	private String manualKey = "Space";
	private String flickButton = "right";
	private String brakeKey = "B";
	private String leanForwardKey = "Up";
	private String leanBackKey = "Down";
	/** Sections the plugin adds (the timed run and leaderboards), under the session stats. */
	private final JPanel extraSections = new JPanel();
	/** Runs when the panel is opened. */
	private Runnable onActivate = () ->
	{
	};

	/**
	 * @param onStartStop runs when Start / Stop is pressed (the plugin hops to the client thread)
	 * @param setOption sets a boolean setting by key ("showGrindEdges", "showControlsCard")
	 */
	public SkatePanel(Runnable onStartStop, BiConsumer<String, Boolean> setOption)
	{
		this(onStartStop, setOption, d ->
		{
		});
	}

	/** @param onDesign runs when an unlocked design is picked (the plugin hops to the client thread) */
	public SkatePanel(Runnable onStartStop, BiConsumer<String, Boolean> setOption, Consumer<BoardDesign> onDesign)
	{
		this(onStartStop, setOption, onDesign, BoardDesigns.bundled());
	}

	/** As above, over {@code designs} (tests). */
	SkatePanel(Runnable onStartStop, BiConsumer<String, Boolean> setOption, Consumer<BoardDesign> onDesign,
		BoardDesigns designs)
	{
		super();
		this.onDesign = onDesign;
		this.designs = designs;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("RuneSkate");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		addRow(content, title);

		startStop.setFocusable(false);
		startStop.addActionListener(e -> onStartStop.run());
		startStop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		addRow(content, startStop);
		refusal.setForeground(REFUSAL_COLOR);
		addRow(content, refusal);
		openBook.setName("trickBook:open");
		openBook.setFocusable(false);
		openBook.setToolTipText("Every trick and control, with how to do it and its points");
		openBook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		openBook.addActionListener(e -> openTrickBook());
		JPanel bookGap = new JPanel(new BorderLayout());
		bookGap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bookGap.setBorder(new EmptyBorder(6, 0, 0, 0));
		bookGap.add(openBook, BorderLayout.CENTER);
		bookGap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		addRow(content, bookGap);
		JPanel pad = new JPanel(new GridLayout(1, 2, 4, 0));
		pad.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pad.setBorder(new EmptyBorder(4, 0, 0, 0));
		for (JButton b : new JButton[]{openLayout, openSetup})
		{
			b.setFocusable(false);
			b.setFont(FontManager.getRunescapeSmallFont());
			b.setMargin(new Insets(2, 2, 2, 2));
			pad.add(b);
		}
		openLayout.setName("controller:customise");
		openLayout.setToolTipText("Choose what each controller button does; share layouts as codes");
		openLayout.addActionListener(e -> openLayout());
		openSetup.setName("controller:setup");
		openSetup.setToolTipText("AntiMicroX, the RuneSkate profile, and a live pad test");
		openSetup.addActionListener(e -> openSetup());
		pad.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		addRow(content, pad);

		// the board designer sits near the top, where players look for it
		addRow(content, heading("Board"));
		boardSection.setLayout(new BoxLayout(boardSection, BoxLayout.Y_AXIS));
		boardSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addRow(content, boardSection);

		addRow(content, heading("The basics"));
		basics.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		addRow(content, basics);
		padBasics.setLayout(new BoxLayout(padBasics, BoxLayout.Y_AXIS));
		padBasics.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addRow(content, padBasics);

		addRow(content, heading("Skating"));
		skillLine.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		addRow(content, skillLine);
		xpBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		xpBar.setPreferredSize(new Dimension(TEXT_WIDTH, 16));
		xpBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		xpBar.setForeground(XP_BAR_COLOR);
		addRow(content, xpBar);

		addRow(content, goalsHeading);
		goalsText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		addRow(content, goalsText);

		addRow(content, heading("This session"));
		stats.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		addRow(content, stats);

		extraSections.setLayout(new BoxLayout(extraSections, BoxLayout.Y_AXIS));
		extraSections.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addRow(content, extraSections);

		addRow(content, heading("Show"));
		for (JCheckBox box : new JCheckBox[]{grindEdges, controlsCard})
		{
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			box.setFocusable(false);
			addRow(content, box);
		}
		grindEdges.addActionListener(e -> setOption.accept("showGrindEdges", grindEdges.isSelected()));
		controlsCard.addActionListener(e -> setOption.accept("showControlsCard", controlsCard.isSelected()));

		// a small, optional support link at the very bottom; nothing in the plugin depends on it
		JButton support = new JButton("Support RuneSkate on Ko-fi");
		support.setName("support:kofi");
		support.setFocusable(false);
		support.setToolTipText(SUPPORT_URL);
		support.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		support.addActionListener(e -> LinkBrowser.browse(SUPPORT_URL));
		JPanel supportGap = new JPanel(new BorderLayout());
		supportGap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		supportGap.setBorder(new EmptyBorder(12, 0, 0, 0));
		supportGap.add(support, BorderLayout.CENTER);
		supportGap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		addRow(content, supportGap);

		add(content, BorderLayout.NORTH);
		update(new PanelState(false, null, 0, 0, 0));
		updateProgress(new ProgressState(1, 0, 83, 0f, BoardLook.defaults(designs)));
		rebuild();
	}

	/** What the controller sub-views ask of the plugin (they run on the EDT). */
	public interface ControllerActions
	{
		/** Save {@code code} as the Custom layout and select the Custom preset. */
		void saveCustomLayout(String code);

		/** Open the AntiMicroX releases page. */
		void openReleases();

		/** Save the RuneSkate profile where the player picks. */
		void saveProfile(Component from);
	}

	public void setControllerActions(ControllerActions actions)
	{
		controllerActions = actions;
	}

	/** Shows {@code v} in place of the panel's content (or of the sub-view open now). */
	private void show(JComponent v)
	{
		if (view != v)
		{
			if (view == setupView)
			{
				setupView.closed();
			}
			remove(view != null ? view : content);
			add(v, BorderLayout.NORTH);
			view = v;
		}
		bookOpen = v == book;
		revalidate();
		repaint();
		v.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1));
	}

	/** Back from a sub-view to the panel's content. */
	void closeView()
	{
		if (view != null)
		{
			if (view == setupView)
			{
				setupView.closed();
			}
			remove(view);
			view = null;
			bookOpen = false;
			add(content, BorderLayout.NORTH);
			revalidate();
			repaint();
			content.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1));
		}
	}

	/** Shows the Trick Book in place of the panel's content, worded for the current settings. */
	void openTrickBook()
	{
		book.showFor(bookSettings());
		show(book);
	}

	/** Back from the Trick Book to the panel's content. */
	void closeTrickBook()
	{
		closeView();
	}

	/** Customise controller, starting from the preset in use. */
	void openLayout()
	{
		layoutView.showFor(preset, savedCustom);
		show(layoutView);
	}

	/** Controller setup; its pad test listens while it is open. */
	void openSetup()
	{
		show(setupView);
		setupView.opened(controller);
	}

	boolean isLayoutOpen()
	{
		return view == layoutView;
	}

	boolean isSetupOpen()
	{
		return view == setupView;
	}

	ControllerLayoutView layoutView()
	{
		return layoutView;
	}

	ControllerSetupView setupView()
	{
		return setupView;
	}

	/** The plugin stops: the pad test stops listening. */
	public void dispose()
	{
		setupView.closed();
	}

	private void saveLayout(String code)
	{
		controllerActions.saveCustomLayout(code);
	}

	/**
	 * The controller preset in use and its name ("Skate 3", "Custom"), and the saved Custom layout (null when none):
	 * the basics, the Trick Book and Customise controller name its buttons.
	 */
	public void setController(PadPreset preset, String presetName, PadPreset savedCustom)
	{
		this.savedCustom = savedCustom;
		if (preset.equals(this.preset) && presetName.equals(this.presetName))
		{
			return;
		}
		this.preset = preset;
		this.presetName = presetName;
		rebuild();
	}

	boolean isTrickBookOpen()
	{
		return bookOpen;
	}

	private TrickBook.Settings bookSettings()
	{
		return new TrickBook.Settings(toggleKey, boardKey, manualKey, flickButton, mirror, mouseTricks,
			keyboardTricks, controller, brakeKey, leanForwardKey, leanBackKey, preset, presetName);
	}

	/**
	 * The other key names the Trick Book is worded with.
	 *
	 * @param flickButton "right", "left" or "middle"
	 * @param brakeKey the extra brake key's name ("Not set" when none)
	 */
	public void setKeyNames(String flickButton, String brakeKey, String leanForwardKey, String leanBackKey)
	{
		this.flickButton = flickButton;
		this.brakeKey = brakeKey;
		this.leanForwardKey = leanForwardKey;
		this.leanBackKey = leanBackKey;
		if (bookOpen)
		{
			book.showFor(bookSettings());
		}
	}

	/** Adds a section under the session stats. */
	public void addSection(JComponent section)
	{
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		extraSections.add(section);
		extraSections.revalidate();
	}

	/** Runs {@code r} whenever the panel is opened. */
	public void setOnActivate(Runnable r)
	{
		onActivate = r;
	}

	@Override
	public void onActivate()
	{
		onActivate.run();
	}

	private static void addRow(JPanel content, JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(c);
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(ColorScheme.BRAND_ORANGE);
		l.setBorder(new EmptyBorder(10, 0, 3, 0));
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

	/** Client-thread data, handed over with SwingUtilities.invokeLater. */
	public void update(PanelState s)
	{
		startStop.setText(s.active ? "Stop skating" : "Start skating");
		refusal.setText(s.refusal == null || s.active ? "" : html(esc(s.refusal)));
		refusal.setVisible(s.refusal != null && !s.active);
		stats.setText(html("Total: " + String.format("%,d", s.total)
			+ "<br>Best combo: " + String.format("%,d", s.bestCombo)
			+ "<br>Tricks landed: " + String.format("%,d", s.tricksLanded)));
	}

	/** The account's Skating level and XP, handed over with SwingUtilities.invokeLater. */
	public void updateProgress(ProgressState s)
	{
		String next = s.xpToNext > 0
			? "<br>" + String.format("%,d", s.xpToNext) + " XP to level " + (s.level + 1) : "<br>Maxed!";
		skillLine.setText(html("<b>Level " + s.level + "</b> &nbsp; " + String.format("%,d", s.xp) + " XP" + next));
		xpBar.setMaximumValue(1000);
		xpBar.setValue(Math.round(Math.max(0f, Math.min(1f, s.progress)) * 1000));
		xpBar.setLeftLabel("Lvl " + s.level);
		xpBar.setRightLabel(s.xpToNext > 0 ? "Lvl " + (s.level + 1) : "");
		xpBar.setCenterLabel(Math.round(s.progress * 100) + "%");
		goalsText.setText(html(goalsHtml(s.goals)));
		if (s.level != designLevel || !s.look.equals(lookInUse))
		{
			designLevel = s.level;
			lookInUse = s.look;
			rebuildBoard();
		}
	}

	/** What players' own designs do in the Board section: "+ Custom" per part, and a custom design's menu. */
	public interface CustomActions
	{
		/** "+ Custom": make a new design of {@code part}. */
		void add(DesignPart part, Component from);

		void edit(BoardDesign design, Component from);

		void rename(BoardDesign design, Component from);

		void delete(BoardDesign design, Component from);
	}

	/** Turns on the "+ Custom" tiles and the custom designs' menus. */
	public void setCustomActions(CustomActions actions)
	{
		customActions = actions;
		if (lookInUse != null)
		{
			rebuildBoard();
		}
	}

	/**
	 * Players' own designs changed (they are in the design catalogue already): their thumbnails, by id. The Board
	 * section is drawn again.
	 */
	public void setCustomThumbs(Map<String, BufferedImage> customThumbs)
	{
		// every custom design is in the map: any other's thumbnail is a deleted design's
		thumbs.retainCustom(customThumbs.keySet());
		for (Map.Entry<String, BufferedImage> e : customThumbs.entrySet())
		{
			thumbs.put(e.getKey(), e.getValue());
		}
		if (lookInUse != null)
		{
			rebuildBoard();
		}
	}

	/** Each goal on its own line: done ones ticked in green, the rest with their progress. */
	static String goalsHtml(List<ProgressState.GoalView> goals)
	{
		if (goals.isEmpty())
		{
			return "Start skating to get three goals. Each one done is worth "
				+ String.format("%,d", SessionGoals.BONUS_XP) + " Skating XP.";
		}
		StringBuilder sb = new StringBuilder();
		for (ProgressState.GoalView g : goals)
		{
			if (sb.length() > 0)
			{
				sb.append("<br>");
			}
			if (g.done)
			{
				sb.append("<font color='#00c832'>&#10004; ").append(esc(g.text)).append("</font>");
			}
			else
			{
				sb.append("&#9744; ").append(esc(g.text)).append(" <font color='#c8a85a'>").append(esc(g.progress))
					.append("</font>");
			}
		}
		return sb.toString();
	}

	/** Shows or hides the session goals ("Show session goals"). */
	public void setShowGoals(boolean show)
	{
		goalsHeading.setVisible(show);
		goalsText.setVisible(show);
	}

	/**
	 * The Board section: a strip with the designs in use, then for grip, deck and wheels a grid of thumbnails with
	 * names. A pick applies at once; locked designs are greyed with their level; the one in use is highlighted.
	 */
	private void rebuildBoard()
	{
		boardSection.removeAll();
		JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		strip.setName("board:preview");
		strip.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (DesignPart part : new DesignPart[]{DesignPart.GRIP, DesignPart.DECK, DesignPart.WHEELS})
		{
			BoardDesign d = lookInUse.get(part);
			JLabel l = new JLabel(new ImageIcon(thumbs.get(d.id)));
			l.setToolTipText(part.label + ": " + d.name);
			strip.add(l);
		}
		strip.setAlignmentX(Component.LEFT_ALIGNMENT);
		strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		boardSection.add(strip);
		JLabel riding = new JLabel(html("<font color='#c8c8c8'>" + esc(lookInUse.grip.name) + " grip, "
			+ esc(lookInUse.deck.name) + " deck, " + esc(lookInUse.wheels.name) + " wheels</font>"));
		riding.setFont(FontManager.getRunescapeSmallFont());
		riding.setBorder(new EmptyBorder(2, 0, 2, 0));
		riding.setAlignmentX(Component.LEFT_ALIGNMENT);
		boardSection.add(riding);
		for (DesignPart part : new DesignPart[]{DesignPart.GRIP, DesignPart.DECK, DesignPart.WHEELS})
		{
			JLabel sub = new JLabel(part.label);
			sub.setFont(FontManager.getRunescapeSmallFont());
			sub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			sub.setBorder(new EmptyBorder(6, 0, 2, 0));
			sub.setAlignmentX(Component.LEFT_ALIGNMENT);
			boardSection.add(sub);
			JPanel grid = new JPanel(new GridLayout(0, part == DesignPart.WHEELS ? 3 : 2, 3, 3));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
			for (BoardDesign d : designs.of(part))
			{
				grid.add(designButton(d));
			}
			if (customActions != null)
			{
				grid.add(customTile(part));
			}
			grid.setAlignmentX(Component.LEFT_ALIGNMENT);
			boardSection.add(grid);
		}
		boardSection.revalidate();
		boardSection.repaint();
	}

	/** One design's cell: its picture over its name, greyed with "Level N" while locked, highlighted when in use. */
	private JButton designButton(BoardDesign d)
	{
		boolean locked = !d.isUnlocked(designLevel);
		boolean inUse = d.id.equals(lookInUse.get(d.part).id);
		String color = inUse ? "#ff981f" : locked ? "#8a8a8a" : "#dcdcdc";
		String text = "<html><center><font color='" + color + "'>" + esc(d.name) + "</font>"
			+ (locked ? "<br><font color='#8a8a8a'>Level " + d.unlock + "</font>" : "") + "</center></html>";
		JButton b = new JButton(text, new ImageIcon(locked ? thumbs.locked(d.id) : thumbs.get(d.id)));
		b.setName("design:" + d.id);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setVerticalTextPosition(SwingConstants.BOTTOM);
		b.setHorizontalTextPosition(SwingConstants.CENTER);
		b.setIconTextGap(1);
		b.setMargin(new Insets(2, 1, 2, 1));
		b.setFocusable(false);
		// disabled buttons draw their icon greyed by the look and feel: the locked picture is greyed already
		b.setDisabledIcon(b.getIcon());
		b.setEnabled(!locked);
		b.setSelected(inUse);
		b.setToolTipText(locked ? d.name + " " + d.part.key + ": unlocks at Skating level " + d.unlock
			: inUse ? d.name + " " + d.part.key + " (in use)" : "Skate with the " + d.name + " " + d.part.key);
		b.setBackground(inUse ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(inUse ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR, 1),
			new EmptyBorder(2, 1, 2, 1)));
		b.addActionListener(e -> onDesign.accept(d));
		if (d.custom)
		{
			b.setToolTipText(b.getToolTipText() + ". Right-click to edit, rename or delete it");
			b.setComponentPopupMenu(customMenu(d, b));
		}
		return b;
	}

	/** A custom design's right-click menu. */
	private JPopupMenu customMenu(BoardDesign d, JComponent from)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem edit = new JMenuItem("Edit");
		edit.setName("customMenu:edit");
		edit.addActionListener(e -> customActions.edit(d, from));
		JMenuItem rename = new JMenuItem("Rename");
		rename.setName("customMenu:rename");
		rename.addActionListener(e -> customActions.rename(d, from));
		JMenuItem delete = new JMenuItem("Delete");
		delete.setName("customMenu:delete");
		delete.addActionListener(e -> customActions.delete(d, from));
		menu.add(edit);
		menu.add(rename);
		menu.add(delete);
		return menu;
	}

	/** The "+ Custom" tile after a part's designs: make your own from an image. */
	private JButton customTile(DesignPart part)
	{
		JButton b = new JButton("<html><center><font color='#dcdcdc'>+ Custom</font></center></html>");
		b.setName("customAdd:" + part.key);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setMargin(new Insets(2, 1, 2, 1));
		b.setFocusable(false);
		b.setToolTipText("Make your own " + part.key + " design from an image");
		b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createDashedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 3, 2),
			new EmptyBorder(2, 1, 2, 1)));
		b.addActionListener(e -> customActions.add(part, b));
		return b;
	}

	/** The current settings the panel's wording and toggles depend on. */
	public void setSettings(String toggleKey, String manualKey, boolean mouseTricks, boolean keyboardTricks,
		boolean mirror, boolean showGrindEdges, boolean showControlsCard)
	{
		setSettings(toggleKey, manualKey, mouseTricks, keyboardTricks, mirror, showGrindEdges, showControlsCard, false);
	}

	/** As above; {@code controller}: controller mode names the pad's buttons, drawn. */
	public void setSettings(String toggleKey, String manualKey, boolean mouseTricks, boolean keyboardTricks,
		boolean mirror, boolean showGrindEdges, boolean showControlsCard, boolean controller)
	{
		setSettings(toggleKey, manualKey, mouseTricks, keyboardTricks, mirror, showGrindEdges, showControlsCard,
			controller, boardKey);
	}

	/** As above; {@code boardKey}: the board on/off key's name, for the on-foot basics. */
	public void setSettings(String toggleKey, String manualKey, boolean mouseTricks, boolean keyboardTricks,
		boolean mirror, boolean showGrindEdges, boolean showControlsCard, boolean controller, String boardKey)
	{
		grindEdges.setSelected(showGrindEdges);
		controlsCard.setSelected(showControlsCard);
		if (toggleKey.equals(this.toggleKey) && manualKey.equals(this.manualKey) && mouseTricks == this.mouseTricks
			&& keyboardTricks == this.keyboardTricks && mirror == this.mirror && controller == this.controller
			&& boardKey.equals(this.boardKey))
		{
			return;
		}
		this.boardKey = boardKey;
		this.controller = controller;
		this.toggleKey = toggleKey;
		this.manualKey = manualKey;
		this.mouseTricks = mouseTricks;
		this.keyboardTricks = keyboardTricks;
		this.mirror = mirror;
		rebuild();
	}

	private void rebuild()
	{
		startStop.setToolTipText("Or press " + toggleKey + ". Stand still first.");
		String jump = mouseTricks ? "Jump: hold the flick mouse button, drag down, then flick up"
			: "Jump: hold Space, then let go";
		basics.setText(html("Stand still, then press " + esc(toggleKey) + " (or the button above)."
			+ "<br>W: push &nbsp; A/D: steer"
			+ "<br>" + jump
			+ "<br>Shift alone: brake &nbsp; R: back on after a bail"
			+ "<br>H: controls card &nbsp; Esc: stop"
			+ "<br>Every trick: Open Trick Book above"
			+ "<br><br>" + esc(boardKey) + ": step off and carry the board, or get back on"
			+ "<br>On foot: WASD walk &nbsp; Shift sprint &nbsp; Space jump"
			+ "<br>Q/E: drop the board, or pick it up"
			+ "<br>Board far away: " + esc(boardKey) + " calls it back"));
		basics.setVisible(!controller);
		padBasics.setVisible(controller);
		padBasics.removeAll();
		if (controller)
		{
			JLabel which = new JLabel(html(esc("Controller preset: " + presetName + ". Start with " + toggleKey
				+ " or the button above.")));
			which.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			which.setAlignmentX(Component.LEFT_ALIGNMENT);
			padBasics.add(which);
			padRow(new Glyph[]{Glyph.LS}, "Steer; in the air, spin. Up / down with a grab: flip. On foot: walk");
			padRow(new Glyph[]{Glyph.RS}, mouseTricks ? "Pull down, flick: tricks (no button). Small tilt up / down, "
				+ "held: manual / nose manual" : "Small tilt up / down, held: manual / nose manual");
			for (Map.Entry<String, String> b : PadWords.layout(preset).entrySet())
			{
				padRow(glyphsOf(b.getKey()), ControllerGlyphs.plain(b.getValue()));
			}
		}
		padBasics.revalidate();
		if (bookOpen)
		{
			book.showFor(bookSettings());
		}
	}

	/** One controller basics row: the buttons, then what they do. */
	private void padRow(Glyph[] glyphs, String text)
	{
		JLabel l = new JLabel(html(esc(text)).replace("width:" + TEXT_WIDTH, "width:" + (TEXT_WIDTH - 50)),
			new ControllerGlyphIcon(PAD_GLYPH_SIZE, glyphs), SwingConstants.LEFT);
		l.setIconTextGap(6);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(new EmptyBorder(1, 0, 1, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		padBasics.add(l);
	}

	/** The distinct glyphs in a line, in order, at most two. */
	static Glyph[] glyphsOf(String text)
	{
		List<Glyph> out = new ArrayList<>();
		for (Object piece : ControllerGlyphs.split(text))
		{
			if (piece instanceof Glyph && !out.contains(piece) && out.size() < 2)
			{
				out.add((Glyph) piece);
			}
		}
		return out.toArray(new Glyph[0]);
	}
}
