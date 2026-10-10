package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import com.gielinorskate.Text;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.progression.*;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.input.KeyListener;
import net.runelite.client.ui.*;
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
/** The project's optional Ko-fi page, opened by the button at the top of the panel. */
static final String SUPPORT_URL = "https://ko-fi.com/runeskate_project";
/** The support button's text and outline. */
private static final Color SUPPORT_COLOR = new Color(255, 200, 40);

private final JButton startStop = button("Start skating", null, null);
/** What the controller views ask of the plugin; nothing until it sets them. */
private ControllerActions controllerActions = new ControllerActions()
{
};
/** The panel's own content, swapped out for a sub-view (the Trick Book, ...) while one is open. */
private final JPanel content = column();
/** Back from a sub-view to the panel's content. */
private final Runnable back = () -> show(content);
final TrickBookView book = new TrickBookView(back);
private final ControllerLayoutView layoutView = new ControllerLayoutView(back,
code -> controllerActions.saveCustomLayout(code));
private final ControllerSetupView setupView = new ControllerSetupView(back, () -> controllerActions);
/** What is shown: {@link #content}, or a sub-view in its place. */
JComponent view = content;
/** The settings the basics and the Trick Book are worded for, with the controller preset in use. */
private TrickBook.Settings settings = new TrickBook.Settings("Ctrl+K", "F", "Space", "right", false, true, false,
false, "B", "Up", "Down", PadPreset.skate3(), "Skate 3");
/** The saved Custom layout (null when none). */
private PadPreset savedCustom;
private final JLabel refusal = label(null, null, new Color(255, 150, 120));
private final JLabel stats = greyLabel(null);
private final JLabel skillLine = greyLabel(null);
private final ProgressBar xpBar = new ProgressBar();
/** The Board section: the preview strip, then a thumbnail grid per part. */
private final JPanel boardSection = column();
private final JLabel goalsHeading = heading("Daily goals");
private final JLabel goalsText = greyLabel(null);
private final Consumer<BoardDesign> onDesign;
private final BoardDesigns designs;
private final DesignThumbs thumbs = new DesignThumbs();
/** The level and designs the Board section was last built for. */
private int designLevel = -1;
private BoardLook lookInUse;
private final JCheckBox grindEdges = new JCheckBox("Show grindable edges");
private final JCheckBox controlsCard = new JCheckBox("Show controls card");
private final JLabel basics = greyLabel(null);
/** Controller mode's basics: one row per pad control, its buttons drawn in front. */
private final JPanel padBasics = column();
/** Sections the plugin adds (the timed run and leaderboards), under the session stats. */
private final JPanel extraSections = column();
/** Runs when the panel is opened. */
private Runnable onActivate = () ->
{
};

/**
* @param onStartStop runs when Start / Stop is pressed (the plugin hops to the client thread)
* @param setOption sets a boolean setting by key ("showGrindEdges", "showControlsCard")
* @param onDesign runs when an unlocked design is picked (the plugin hops to the client thread)
*/
public SkatePanel(Runnable onStartStop, BiConsumer<String, Boolean> setOption, Consumer<BoardDesign> onDesign)
{
this(onStartStop, setOption, onDesign, BoardDesigns.bundled());
}

/** As above, over {@code designs} (tests). */
SkatePanel(Runnable onStartStop, BiConsumer<String, Boolean> setOption, Consumer<BoardDesign> onDesign,
BoardDesigns designs)
{
this.onDesign = onDesign;
this.designs = designs;
setLayout(new BorderLayout());
setBorder(new EmptyBorder(8, 8, 8, 8));
dark(this);

// an optional support link at the very top, picked out in yellow; nothing in the plugin depends on it
content.add(supportRow());
content.add(label("RuneSkate", FontManager.getRunescapeBoldFont(), Color.WHITE));
startStop.addActionListener(e -> onStartStop.run());
stretch(startStop, 32);
content.add(left(startStop));
content.add(refusal);
JButton openBook = button("Open Trick Book", "trickBook:open", e -> openTrickBook());
openBook.setToolTipText(Text.get("panel.bookTip"));
content.add(row(openBook, 6, 34));
JPanel pad = left(dark(new JPanel(new GridLayout(1, 2, 4, 0))));
pad.setBorder(new EmptyBorder(4, 0, 0, 0));
pad.add(padButton("Customise controller", "controller:customise", Text.get("panel.layoutTip"),
e -> openLayout()));
pad.add(padButton("Controller setup", "controller:setup", Text.get("panel.setupTip"), e -> openSetup()));
stretch(pad, 30);
content.add(pad);

// the board designer sits near the top, where players look for it
content.add(heading("Board"));
content.add(boardSection);

content.add(heading("The basics"));
content.add(basics);
content.add(padBasics);

content.add(heading("Skating"));
content.add(skillLine);
stretch(xpBar, 16);
xpBar.setPreferredSize(new Dimension(TEXT_WIDTH, 16));
xpBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
// the green of the game's skill progress bars
xpBar.setForeground(new Color(0, 160, 50));
content.add(left(xpBar));

content.add(goalsHeading);
content.add(goalsText);

content.add(heading("This session"));
content.add(stats);
content.add(extraSections);

content.add(heading("Show"));
for (JCheckBox box : new JCheckBox[]{grindEdges, controlsCard})
{
box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
box.setFocusable(false);
content.add(left(dark(box)));
}
grindEdges.addActionListener(e -> setOption.accept("showGrindEdges", grindEdges.isSelected()));
controlsCard.addActionListener(e -> setOption.accept("showControlsCard", controlsCard.isSelected()));

add(content, BorderLayout.NORTH);
update(new PanelState(false, null, 0, 0, 0));
updateProgress(new ProgressState(1, 0, 83, 0f, BoardLook.defaults(designs), List.of()));
rebuild();
}

/** What the controller sub-views ask of the plugin (they run on the EDT). */
public interface ControllerActions
{
/** Save {@code code} as the Custom layout and select the Custom preset. */
default void saveCustomLayout(String code)
{
}

/** Open the AntiMicroX releases page. */
default void openReleases()
{
}

/** Save the RuneSkate profile where the player picks. */
default void saveProfile(Component from)
{
}

/** Start ({@code on}) or stop sending the game window's key events to {@code l}. */
default void watchKeys(KeyListener l, boolean on)
{
}
}

public void setControllerActions(ControllerActions actions)
{
controllerActions = actions;
}

/** Shows {@code v} in place of what is shown now: the panel's content or a sub-view. */
private void show(JComponent v)
{
if (view != v)
{
// the pad test stops listening when its view goes
setupView.closed();
remove(view);
add(v, BorderLayout.NORTH);
view = v;
}
revalidate();
repaint();
v.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
}

/** Shows the Trick Book in place of the panel's content, worded for the current settings. */
void openTrickBook()
{
book.showFor(settings);
show(book);
}

/** Customise controller, starting from the preset in use. */
void openLayout()
{
layoutView.showFor(settings.preset, savedCustom);
show(layoutView);
}

/** Controller setup; its pad test listens while it is open. */
void openSetup()
{
show(setupView);
setupView.opened(settings.controller);
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

/**
* The controller preset in use and its name ("Skate 3", "Custom"), and the saved Custom layout (null when none):
* the basics, the Trick Book and Customise controller name its buttons.
*/
public void setController(PadPreset preset, String presetName, PadPreset savedCustom)
{
this.savedCustom = savedCustom;
apply(settings.withPreset(preset).withPresetName(presetName));
}

/**
* The other key names the Trick Book is worded with.
*
* @param flickButton "right", "left" or "middle"
* @param brakeKey the extra brake key's name ("Not set" when none)
*/
public void setKeyNames(String flickButton, String brakeKey, String leanForwardKey, String leanBackKey)
{
apply(settings.withFlickButton(flickButton).withBrakeKey(brakeKey).withLeanForwardKey(leanForwardKey)
.withLeanBackKey(leanBackKey));
}

/**
* The current settings the panel's wording and toggles depend on. {@code controller}: controller mode names the
* pad's buttons, drawn; {@code boardKey}: the board on/off key's name, for the on-foot basics.
*/
public void setSettings(String toggleKey, String manualKey, boolean mouseTricks, boolean keyboardTricks,
boolean mirror, boolean showGrindEdges, boolean showControlsCard, boolean controller, String boardKey)
{
grindEdges.setSelected(showGrindEdges);
controlsCard.setSelected(showControlsCard);
apply(settings.withToggleKey(toggleKey).withBoardKey(boardKey).withManualKey(manualKey).withMirror(mirror)
.withMouseTricks(mouseTricks).withKeyboardTricks(keyboardTricks).withController(controller));
}

/** Rewords the basics (and the Trick Book while it is open) when the settings change. */
private void apply(TrickBook.Settings s)
{
if (!s.equals(settings))
{
settings = s;
rebuild();
}
}

/** Adds a section under the session stats. */
public void addSection(JComponent section)
{
extraSections.add(left(section));
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

/** The Ko-fi button in its row: yellow text and outline, a dark yellow fill under the mouse. */
private static JPanel supportRow()
{
JButton support = button("Support RuneSkate on Ko-fi", "support:kofi", e -> LinkBrowser.browse(SUPPORT_URL));
support.setToolTipText(SUPPORT_URL);
support.setForeground(SUPPORT_COLOR);
support.setBackground(ColorScheme.DARKER_GRAY_COLOR);
support.setContentAreaFilled(false);
support.setOpaque(true);
support.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SUPPORT_COLOR, 2),
new EmptyBorder(4, 6, 4, 6)));
support.addMouseListener(new MouseAdapter()
{
@Override
public void mouseEntered(MouseEvent e)
{
support.setBackground(new Color(90, 72, 20));
}

@Override
public void mouseExited(MouseEvent e)
{
support.setBackground(ColorScheme.DARKER_GRAY_COLOR);
}
});
JPanel row = row(support, 0, 38);
row.setBorder(new EmptyBorder(0, 0, 8, 0));
return row;
}

/** {@code c} filling a full-width row {@code height} px tall, {@code gap} px under what is above. */
private static JPanel row(JComponent c, int gap, int height)
{
JPanel row = left(dark(new JPanel(new BorderLayout())));
row.setBorder(new EmptyBorder(gap, 0, 0, 0));
row.add(c);
row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
return row;
}

private static JButton padButton(String text, String name, String tip, ActionListener l)
{
JButton b = button(text, name, l);
b.setFont(SMALL_FONT);
b.setMargin(new Insets(2, 2, 2, 2));
b.setToolTipText(tip);
return b;
}

/** Client-thread data, handed over with SwingUtilities.invokeLater. */
public void update(PanelState s)
{
boolean refused = s.refusal != null && !s.active;
startStop.setText(s.active ? "Stop skating" : "Start skating");
refusal.setText(refused ? html(esc(s.refusal)) : "");
refusal.setVisible(refused);
stats.setText(html(String.format(Text.get("panel.stats"), s.total, s.bestCombo, s.tricksLanded)));
}

/** The account's Skating level and XP, handed over with SwingUtilities.invokeLater. */
public void updateProgress(ProgressState s)
{
boolean maxed = s.xpToNext <= 0;
skillLine.setText(html(Text.get("panel.level" + (maxed ? ".max" : ""), s.level, String.format("%,d", s.xp),
String.format("%,d", s.xpToNext), s.level + 1)));
xpBar.setMaximumValue(1000);
xpBar.setValue(Math.round(Math.max(0f, Math.min(1f, s.progress)) * 1000));
xpBar.setLeftLabel("Lvl " + s.level);
xpBar.setRightLabel(maxed ? "" : "Lvl " + (s.level + 1));
xpBar.setCenterLabel(Math.round(s.progress * 100) + "%");
goalsText.setText(html(goalsHtml(s.goals)));
if (s.level != designLevel || !s.look.equals(lookInUse))
{
designLevel = s.level;
lookInUse = s.look;
rebuildBoard();
}
}

/** Each goal on its own line: done ones ticked in green, the rest with their progress. */
static String goalsHtml(List<ProgressState.GoalView> goals)
{
if (goals.isEmpty())
return Text.get("panel.noGoals", String.format("%,d", SessionGoals.BONUS_XP));
return goals.stream().map(g -> g.done ? "<font color='#00c832'>&#10004; " + esc(g.text) + "</font>"
: "&#9744; " + esc(g.text) + " <font color='#c8a85a'>" + esc(g.progress) + "</font>")
.collect(Collectors.joining("<br>"));
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
JPanel strip = left(dark(new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0))));
strip.setName("board:preview");
for (DesignPart part : DesignPart.values())
{
BoardDesign d = lookInUse.get(part);
JLabel l = new JLabel(new ImageIcon(thumbs.get(d.id)));
l.setToolTipText(part.label + ": " + d.name);
strip.add(l);
}
stretch(strip, 44);
boardSection.add(strip);
JLabel riding = left(new JLabel(html("<font color='#c8c8c8'>" + esc(lookInUse.grip.name) + " grip, "
+ esc(lookInUse.deck.name) + " deck, " + esc(lookInUse.wheels.name) + " wheels</font>")));
riding.setFont(SMALL_FONT);
riding.setBorder(new EmptyBorder(2, 0, 2, 0));
boardSection.add(riding);
for (DesignPart part : DesignPart.values())
{
JLabel sub = small(part.label);
sub.setBorder(new EmptyBorder(6, 0, 2, 0));
boardSection.add(sub);
JPanel grid = left(dark(new JPanel(new GridLayout(0, part == DesignPart.WHEELS ? 3 : 2, 3, 3))));
for (BoardDesign d : designs.of(part))
grid.add(designButton(d));
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
JButton b = tile("<font color='" + color + "'>" + esc(d.name) + "</font>"
+ (locked ? "<br><font color='#8a8a8a'>Level " + d.unlock + "</font>" : ""), "design:" + d.id,
BorderFactory.createLineBorder(inUse ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR, 1),
e -> onDesign.accept(d));
b.setIcon(new ImageIcon(locked ? thumbs.locked(d.id) : thumbs.get(d.id)));
b.setVerticalTextPosition(SwingConstants.BOTTOM);
b.setHorizontalTextPosition(SwingConstants.CENTER);
b.setIconTextGap(1);
// disabled buttons draw their icon greyed by the look and feel: the locked picture is greyed already
b.setDisabledIcon(b.getIcon());
b.setEnabled(!locked);
b.setSelected(inUse);
// the design's name goes in last: nothing is filled in after it
b.setToolTipText(Text.get("panel.design." + (locked ? "locked" : inUse ? "inUse" : "pick"), d.part.key, d.unlock,
d.name));
if (inUse)
b.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
return b;
}

/** A Board section cell: centred small text in {@code edge}. */
private static JButton tile(String text, String name, javax.swing.border.Border edge, ActionListener l)
{
JButton b = button("<html><center>" + text + "</center></html>", name, l);
b.setFont(SMALL_FONT);
b.setMargin(new Insets(2, 1, 2, 1));
b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
b.setBorder(BorderFactory.createCompoundBorder(edge, new EmptyBorder(2, 1, 2, 1)));
return b;
}

private void rebuild()
{
TrickBook.Settings s = settings;
startStop.setToolTipText(Text.get("panel.startTip", s.toggleKey));
basics.setText(html(Text.get("panel.basics." + (s.mouseTricks ? "mouse" : "keys"), esc(s.toggleKey),
esc(s.boardKey))));
basics.setVisible(!s.controller);
padBasics.setVisible(s.controller);
padBasics.removeAll();
if (s.controller)
{
padBasics.add(greyLabel(html(esc(Text.get("panel.preset", s.toggleKey, s.presetName)))));
List<String> sticks = Text.lines("panel.sticks" + (s.mouseTricks ? ".mouse" : ""));
padRow("{LS}", sticks.get(0));
padRow("{RS}", sticks.get(1));
PadWords.layout(s.preset).forEach(this::padRow);
}
padBasics.revalidate();
if (view == book)
book.showFor(s);
}

/** One controller basics row: the buttons (glyph tokens), then what they do. */
private void padRow(String buttons, String does)
{
JLabel l = greyLabel(html(esc(ControllerGlyphs.plain(does)), TEXT_WIDTH - 50));
l.setIcon(new ControllerGlyphIcon(18, glyphsOf(buttons)));
l.setIconTextGap(6);
l.setBorder(new EmptyBorder(1, 0, 1, 0));
padBasics.add(l);
}

/** The distinct glyphs in a line, in order, at most two. */
static Glyph[] glyphsOf(String text)
{
return ControllerGlyphs.split(text).stream().filter(p -> p instanceof Glyph).distinct().limit(2)
.toArray(Glyph[]::new);
}
}
