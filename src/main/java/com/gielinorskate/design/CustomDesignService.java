package com.gielinorskate.design;

import com.gielinorskate.Text;
import com.gielinorskate.progression.*;
import com.gielinorskate.render.*;
import com.gielinorskate.ui.SkatePanel;
import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;

/**
* Players' own designs in the running plugin: loads them from the plugin's folder at start, opens the editor for
* "+ Custom" and Edit, bakes and saves on Save, renames and deletes. Threads: file IO and baking on RuneLite's
* executor; Swing (choosers, the editor, prompts) on the EDT; the board's colours change on the client thread
* (through {@link ProgressionService}). The designs map is swapped whole, so any thread may read it.
*/
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CustomDesignService implements SkatePanel.CustomActions
{
/** A revision no two edits share, even across plugin restarts in one client session. */
private static final AtomicLong REVISIONS = new AtomicLong(System.currentTimeMillis());

private final ScheduledExecutorService executor;
private final ClientThread clientThread;
private final ProgressionService progression;
private final BoardDesigns designs = BoardDesigns.bundled();
private final DesignLayout layouts = DesignLayout.bundled();
private final Random random = new SecureRandom();
/** Each part's outline, made once (executor). */
private final Map<DesignPart, PartOutline> outlines = new EnumMap<>(DesignPart.class);

/**
* One loaded design and its catalogue entry (at a new revision). Its kept image is not held (up to 4 MB each):
* it is read again from the store for Edit.
*/
private static final class Entry
{
final CustomDesign design;
final BoardDesign board;
final BufferedImage thumb;
/** Its small picture for party members, or null when it couldn't be made. */
final SharedDesignImage.Encoded shared;

Entry(CustomDesign design, BufferedImage thumb, SharedDesignImage.Encoded shared)
{
this.design = design;
board = BoardDesign.custom(design.id, design.name, design.part, REVISIONS.incrementAndGet());
this.thumb = thumb;
this.shared = shared;
}
}

private volatile Map<String, Entry> entries = Collections.emptyMap();
private volatile CustomDesignStore store;
private volatile Consumer<Map<String, BufferedImage>> thumbsListener;
/** The open editor (EDT). */
private JDialog editor;
private volatile boolean running;

/** The plugin's data folder (RuneLite's Plugin#getPluginDirectory). */
public interface PluginDir
{
Filepath get() throws IOException;
}

/**
* Loads the saved designs (in the background) from {@code pluginDir}/designs, a supplier because finding the
* plugin's folder touches the disk.
*/
public void startUp(PluginDir pluginDir, Consumer<Map<String, BufferedImage>> thumbsListener)
{
running = true;
this.thumbsListener = thumbsListener;
executor.execute(() ->
{
try
{
store = new CustomDesignStore(pluginDir.get().joinSegment("designs"));
Map<String, Entry> loaded = new LinkedHashMap<>();
// one at a time: each kept image is let go once its design is prepared
store.loadEach(s ->
{
Entry e = prepare(s.design, s.image, s.high, s.low);
if (e != null)
loaded.put(e.design.id, e);
});
publish(loaded);
}
catch (IOException | RuntimeException e)
{
log.warn(Text.get("cd.log.load"), e);
}
});
}

public void shutDown()
{
running = false;
thumbsListener = null;
SwingUtilities.invokeLater(this::closeEditor);
Map<String, Entry> old = entries;
entries = Collections.emptyMap();
designs.setCustom(Collections.emptyList());
old.keySet().forEach(DesignColours::unregister);
}

private static BakedBoardGeometry.Mesh mesh(DesignPart part, boolean high)
{
BakedBoardGeometry.Mesh[] parts = BakedBoardGeometry.sharedBoard(high);
return parts == null ? null : Arrays.stream(parts).filter(m -> m.part == part.index).findFirst().orElse(null);
}

/** Executor: the part's outline, made once; null when the board's geometry isn't there. */
private synchronized PartOutline outline(DesignPart part)
{
return outlines.computeIfAbsent(part, p ->
{
BakedBoardGeometry.Mesh m = mesh(p, true);
return m == null ? null : PartOutline.of(m, layouts.of(p), p != DesignPart.WHEELS);
});
}

/** Executor: {high, low} detail colours of {@code d} baked from {@code image}; null without the geometry. */
private int[][] bake(CustomDesign d, BufferedImage image)
{
BakedBoardGeometry.Mesh hi = mesh(d.part, true);
BakedBoardGeometry.Mesh lo = mesh(d.part, false);
if (hi == null || lo == null)
return null;
CustomBake.Source src = CustomBake.source(image, d.part);
DesignLayout.Part layout = layouts.of(d.part);
return new int[][]{CustomBake.bake(hi, layout, d.placement, src, true),
CustomBake.bake(lo, layout, d.placement, src, false)};
}

/**
* Executor: a design ready to draw: its colours (baked again if missing or made for another geometry, and saved),
* registered, its thumbnail and its party picture. {@code image} is only used here, not kept. Null when the
* board's geometry isn't there, or when stopped meanwhile (nothing more is registered).
*/
private Entry prepare(CustomDesign d, BufferedImage image, int[] high, int[] low)
{
BakedBoardGeometry.Mesh hi = mesh(d.part, true);
BakedBoardGeometry.Mesh lo = mesh(d.part, false);
if (hi == null || lo == null || !running)
return null;
if (high == null || high.length != hi.cornerRgb.length || low == null || low.length != lo.cornerRgb.length)
{
int[][] colours = bake(d, image);
high = colours[0];
low = colours[1];
try
{
store.save(d, null, high, low);
}
catch (IOException e)
{
log.warn(Text.get("cd.log.colours"), d.id, e);
}
}
DesignColours.register(d.id, high, low);
SharedDesignImage.Encoded shared = null;
try
{
// made whether or not it is ever shared; null when none fits the size cap
shared = SharedDesignImage.encode(CustomBake.source(image, d.part), d.part, layouts.of(d.part),
d.placement);
}
catch (RuntimeException e)
{
log.warn(Text.get("cd.log.party"), d.id, e);
}
return new Entry(d, ThumbRender.thumbnail(d.part, hi, high), shared);
}

/** Custom design {@code id}'s small picture for party members, or null (unknown, or none could be made). */
public SharedDesignImage.Encoded sharedPicture(String id)
{
Entry e = entries.get(id);
return e == null ? null : e.shared;
}

/**
* Any thread: the catalogue, the panel and the board get these designs. Stopped meanwhile (work that was still
* running at shutdown), their colours are let go instead.
*/
private void publish(Map<String, Entry> now)
{
if (!running)
{
for (String id : now.keySet())
{
DesignColours.unregister(id);
BakedBoardModel.forgetDesign(id);
}
return;
}
entries = Collections.unmodifiableMap(now);
List<BoardDesign> list = new ArrayList<>();
Map<String, BufferedImage> thumbs = new LinkedHashMap<>();
for (Entry e : now.values())
{
list.add(e.board);
thumbs.put(e.design.id, e.thumb);
}
designs.setCustom(list);
Consumer<Map<String, BufferedImage>> l = thumbsListener;
if (l != null)
SwingUtilities.invokeLater(() -> l.accept(thumbs));
clientThread.invoke(progression::designsChanged);
}

/** Executor: the designs with {@code id}'s entry replaced by {@code e} (removed when null), published. */
private void put(String id, Entry e)
{
Map<String, Entry> now = new LinkedHashMap<>(entries);
if (e == null)
now.remove(id);
else
now.put(id, e);
publish(now);
}

// ---- panel actions (EDT) ----

/** "+ Custom": pick an image, then edit it. */
public void add(DesignPart part, Component from)
{
if (store == null)
{
message(from, Text.get("cd.notready"));
return;
}
if (bringEditorToFront())
return;
if (entries.size() >= CustomDesignRules.MAX_DESIGNS)
{
message(from, Text.get("cd.full", CustomDesignRules.MAX_DESIGNS));
return;
}
List<Filepath> picked = new Filepath.Chooser()
.setIsOpen()
.setAcceptsFiles()
.setDialogTitle("Pick an image for your " + part.key)
.addExtensionFilter(Text.get("cd.images"), "png", "jpg", "jpeg", "gif", "bmp")
.showDialog(window(from));
if (picked == null || picked.isEmpty())
return;
executor.execute(() ->
{
DesignImages.Picked img;
try
{
img = DesignImages.read(picked.get(0));
}
catch (DesignImages.Refused r)
{
later(from, r.getMessage());
return;
}
PartOutline outline = outline(part);
if (outline == null)
{
later(from, "The board couldn't be loaded.");
return;
}
// a new design: no id until it is saved
CustomDesign draft = new CustomDesign(null, "My " + part.key, part, 0, img.sourceWidth, img.sourceHeight,
ImagePlacement.initial(img.image.getWidth(), img.image.getHeight(), outline.bounds()));
SwingUtilities.invokeLater(() -> openEditor(from, draft, outline, img.image));
});
}

/** Edit: the kept image read again (executor), then the editor with it and its placement. */
public void edit(BoardDesign design, Component from)
{
Entry e = entries.get(design.id);
if (e == null || bringEditorToFront())
return;
executor.execute(() ->
{
BufferedImage image = store.image(e.design);
PartOutline outline = outline(e.design.part);
if (image == null)
{
log.warn(Text.get("cd.log.read"), e.design.id);
later(from, Text.get("cd.unread"));
}
else if (outline != null)
SwingUtilities.invokeLater(() -> openEditor(from, e.design, outline, image));
});
}

/** Rename: asks for the new name (checked), then saves it. */
public void rename(BoardDesign design, Component from)
{
Entry e = entries.get(design.id);
if (e == null)
return;
String current = e.design.name;
while (true)
{
Object answer = JOptionPane.showInputDialog(window(from), "New name for \"" + current + "\":",
"Rename design", JOptionPane.PLAIN_MESSAGE, null, null, current);
if (answer == null)
return;
String name = answer.toString().trim();
String problem = CustomDesignRules.nameProblem(name);
if (problem == null)
{
executor.execute(() -> renamed(design.id, name, from));
return;
}
message(from, problem);
current = name.substring(0, Math.min(name.length(), CustomDesignRules.MAX_NAME));
}
}

private void renamed(String id, String name, Component from)
{
Entry e = entries.get(id);
if (e == null)
return;
CustomDesign d = e.design.withName(name);
try
{
store.save(d, null, null, null);
}
catch (IOException ex)
{
log.warn(Text.get("cd.log.rename"), id, ex);
later(from, "The design couldn't be renamed: " + ex.getMessage());
return;
}
put(id, new Entry(d, e.thumb, e.shared));
// the old revision's converted colours
BakedBoardModel.forgetDesign(id);
}

/** Delete, after a yes. */
public void delete(BoardDesign design, Component from)
{
Entry e = entries.get(design.id);
if (e == null || JOptionPane.showConfirmDialog(window(from), Text.get("cd.delete", e.design.name), "Delete design",
JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
!= JOptionPane.OK_OPTION)
return;
executor.execute(() ->
{
try
{
store.delete(design.id);
}
catch (IOException ex)
{
log.warn(Text.get("cd.log.delete"), design.id, ex);
later(from, "The design couldn't be deleted: " + ex.getMessage());
return;
}
put(design.id, null);
DesignColours.unregister(design.id);
BakedBoardModel.forgetDesign(design.id);
});
}

// ---- the editor (EDT) ----

private boolean bringEditorToFront()
{
if (editor != null && editor.isDisplayable())
{
editor.toFront();
return true;
}
return false;
}

/** Opens the editor on {@code draft} (a new design when its id is null) and the image it places. */
private void openEditor(Component from, CustomDesign draft, PartOutline outline, BufferedImage image)
{
if (!running || bringEditorToFront())
return;
DesignPart part = draft.part;
DesignEditorPanel panel = new DesignEditorPanel(part, layouts.of(part), mesh(part, true), outline, image,
draft.placement, draft.name, executor, (name, placement) ->
{
closeEditor();
executor.execute(() -> saveDesign(from, draft.withName(name).withPlacement(placement), image));
}, this::closeEditor, () -> saveTemplate(editor, part, outline));
editor = panel.open(window(from), (draft.id == null ? "New " : "Edit ") + part.key + " design");
}

private void closeEditor()
{
if (editor != null)
{
editor.dispose();
editor = null;
}
}

/** Executor: gives a new design its id, bakes both details, saves, shows and selects the design. */
private void saveDesign(Component from, CustomDesign d, BufferedImage image)
{
boolean isNew = d.id == null;
if (isNew)
{
if (entries.size() >= CustomDesignRules.MAX_DESIGNS)
{
later(from, Text.get("cd.already", CustomDesignRules.MAX_DESIGNS));
return;
}
String id;
do
{
id = CustomDesignRules.newId(random);
}
while (entries.containsKey(id) || designs.byId(id) != null);
d = new CustomDesign(id, d.name, d.part, System.currentTimeMillis(), d.sourceWidth, d.sourceHeight,
d.placement);
}
int[][] colours = bake(d, image);
if (colours == null)
return;
try
{
store.save(d, isNew ? image : null, colours[0], colours[1]);
}
catch (IOException ex)
{
log.warn(Text.get("cd.log.save"), d.id, ex);
later(from, "The design couldn't be saved: " + ex.getMessage());
return;
}
Entry e = prepare(d, image, colours[0], colours[1]);
if (e == null)
return;
put(d.id, e);
if (!isNew)
// the old revision's converted colours
BakedBoardModel.forgetDesign(d.id);
clientThread.invoke(() -> progression.selectDesign(e.board));
}

/** Download template: where to save it, then the picture written there (executor). */
private void saveTemplate(Component from, DesignPart part, PartOutline outline)
{
List<Filepath> picked = new Filepath.Chooser()
.setIsSave()
.setAcceptsFiles()
.setDialogTitle("Save the " + part.key + " template")
.addExtensionFilter("PNG image", "png")
.setDefaultExtension("png")
.setFileName("runeskate-" + part.key + "-template.png")
.showDialog(from);
if (picked == null || picked.isEmpty())
return;
executor.execute(() ->
{
try (OutputStream out = picked.get(0).openOutputStream())
{
DesignImages.writePng(DesignTemplate.render(part, outline), out);
}
catch (IOException | RuntimeException e)
{
log.warn(Text.get("cd.log.template"), e);
later(from, "The template couldn't be saved: " + e.getMessage());
}
});
}

private static Window window(Component c)
{
return c == null || c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
}

private static void message(Component from, String text)
{
JOptionPane.showMessageDialog(window(from), text, "RuneSkate", JOptionPane.INFORMATION_MESSAGE);
}

/** {@link #message} from another thread. */
private static void later(Component from, String text)
{
SwingUtilities.invokeLater(() -> message(from, text));
}
}
