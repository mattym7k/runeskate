package com.gielinorskate.ui;

import static com.gielinorskate.ui.Widgets.*;

import com.gielinorskate.Text;
import com.gielinorskate.controller.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
* "Customise controller": each pad button's action on the board and on foot, starting from a preset, saved as the
* Custom preset; and layout codes to share (copy, and paste with a preview of what changes). Shown in place of the
* side panel's content with a Back button. Swing only: every method runs on the EDT.
*/
class ControllerLayoutView extends JPanel
{
private static final Color OK_COLOR = new Color(120, 200, 120);
private static final Color ERROR_COLOR = new Color(255, 150, 120);

private final Consumer<String> onSave;
private final JComboBox<String> startFrom = new JComboBox<>(Text.lines("layout.from").toArray(new String[0]));
private final Map<PadButton, JComboBox<PadAction>> board = new EnumMap<>(PadButton.class);
private final Map<PadButton, JComboBox<PadAction>> foot = new EnumMap<>(PadButton.class);
final JLabel status = left(new JLabel());
/** The layout in the editor, including any air actions (kept, not edited here). */
PadPreset editing = PadPreset.skate3();
/** The saved Custom layout, for "Your saved layout"; null when there is none. */
private PadPreset saved;
/** True while the editor is being filled in, so the combos' events don't count as edits. */
private boolean loading;

/** @param onSave the layout code to save as the Custom preset (and select it) */
ControllerLayoutView(Runnable onBack, Consumer<String> onSave)
{
this.onSave = onSave;
subView(this, "Customise controller", "layout:back", onBack);
JPanel body = column();
body.add(wrapped(Text.get("layout.intro"), CONTENT_WIDTH));

JPanel from = left(dark(new JPanel(new BorderLayout(4, 0))));
from.setBorder(new EmptyBorder(6, 0, 4, 0));
from.add(small("Start from"), BorderLayout.WEST);
startFrom.setName("layout:startFrom");
startFrom.setFocusable(false);
from.add(startFrom, BorderLayout.CENTER);
from.add(button("Load", "layout:load", e -> loadStart()), BorderLayout.EAST);
body.add(stretch(from));

for (PadButton b : PadButton.values())
body.add(buttonRows(b));

JPanel actions = left(dark(new JPanel(new GridLayout(0, 1, 0, 3))));
actions.setBorder(new EmptyBorder(8, 0, 4, 0));
actions.add(button("Save as my Custom preset", "layout:save", e -> save()));
actions.add(button("Copy layout code", "layout:copy", e -> copy()));
actions.add(button("Paste layout code...", "layout:paste", e -> paste()));
body.add(stretch(actions));
status.setFont(SMALL_FONT);
body.add(status);
add(body, BorderLayout.CENTER);
}

/** Shows the editor on {@code current} (the preset in use); {@code saved} is the stored Custom one, or null. */
void showFor(PadPreset current, PadPreset saved)
{
this.saved = saved;
load(current);
setStatus(null, null);
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
setStatus(Text.get("layout.saved"), OK_COLOR);
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
setStatus(Text.get("layout.busy", c), ERROR_COLOR);
}
}

private void paste()
{
String clip = "";
try
{
Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
if (data instanceof String && ((String) data).trim().startsWith(LayoutCode.PREFIX))
clip = ((String) data).trim();
}
catch (IllegalStateException | UnsupportedFlavorException | IOException e)
{
// nothing usable on the clipboard: an empty box to paste into
}
Object typed = JOptionPane.showInputDialog(this, Text.get("layout.paste", LayoutCode.PREFIX + LayoutCode.VERSION),
"Paste layout code", JOptionPane.PLAIN_MESSAGE, null, null, clip);
if (!(typed instanceof String))
return;
String code = (String) typed;
List<String> changes = preview(code);
if (changes == null)
return;
if (changes.isEmpty())
{
setStatus(Text.get("layout.same"), OK_COLOR);
return;
}
// at most 20 changes are listed
StringBuilder msg = new StringBuilder("This layout changes:\n");
changes.stream().limit(20).forEach(c -> msg.append("  ").append(c).append('\n'));
if (changes.size() > 20)
msg.append("... and ").append(changes.size() - 20).append(" more\n");
if (JOptionPane.showConfirmDialog(this, msg + "\nUse it as your Custom preset?", "Paste layout code",
JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
apply(code);
}

private JPanel buttonRows(PadButton b)
{
JPanel p = new JPanel(new GridLayout(0, 1, 0, 1));
p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
p.setBorder(new EmptyBorder(3, 4, 3, 4));
JLabel name = label(b.label + "  (" + b.keyName + ")", SMALL_FONT, Color.WHITE);
name.setIcon(new ControllerGlyphIcon(16, PadWords.glyph(b)));
name.setIconTextGap(6);
p.add(name);
p.add(choice(b, PadContext.BOARD, board));
p.add(choice(b, PadContext.FOOT, foot));
JPanel wrap = left(dark(new JPanel(new BorderLayout())));
wrap.setBorder(new EmptyBorder(0, 0, 3, 0));
wrap.add(p);
return stretch(wrap);
}

private JPanel choice(PadButton b, PadContext context, Map<PadButton, JComboBox<PadAction>> into)
{
JComboBox<PadAction> box = new JComboBox<>(Arrays.stream(PadAction.values()).filter(a -> a.allowedIn(context))
.toArray(PadAction[]::new));
box.setName("layout:" + b.name() + ":" + context.name());
box.setFocusable(false);
box.setFont(SMALL_FONT);
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
status.setText(text == null ? "" : html(esc(text), CONTENT_WIDTH - 10));
status.setForeground(color != null ? color : ColorScheme.LIGHT_GRAY_COLOR);
}
}
