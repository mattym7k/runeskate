package com.gielinorskate.design;

import com.gielinorskate.Text;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
* The design editor's content (shown in its own window, {@link #open}): the canvas, a live preview of the part baked
* at in-game resolution (High detail colours, drawn flat like the panel's thumbnails), the placement buttons (Rotate
* 90°, Flip, Fit, Fill, Stretch to outline, Reset), the name, and Download template / Cancel / Save. The preview
* is baked on {@code worker} (never the EDT), at most one at a time and at most every {@link #PREVIEW_MS}. EDT only.
*/
public final class DesignEditorPanel extends JPanel
{
static final int PREVIEW_MS = 60;
/** The preview of a grip or deck: this long, at 3x the panel thumbnail. */
static final int PREVIEW_LONG = ThumbRender.BOARD_W * 3;
static final int PREVIEW_SHORT = ThumbRender.BOARD_H * 3;
static final int PREVIEW_WHEEL = ThumbRender.WHEEL_SIZE * 4;

private final DesignPart part;
private final DesignLayout.Part layout;
private final BakedBoardGeometry.Mesh mesh;
private final BufferedImage image;
private final PartOutline outline;
private final Executor worker;
private final Runnable onCancel;
private final DesignCanvas canvas;
private final JLabel preview = new JLabel();
private final JTextField name = new JTextField(16);
private final JLabel nameError = new JLabel(" ");
private final JButton save = new JButton("Save");
private final Timer previewTimer = new Timer(PREVIEW_MS, e -> startPreview());
private CustomBake.Source source;
private boolean previewBusy;
private boolean previewDirty;
/** How many previews have been shown (tests). */
private int previewsDone;

/**
* @param mesh the part at High detail (the preview bakes it)
* @param image the kept copy of the player's image
* @param placement where it starts (a new design: {@link ImagePlacement#initial})
* @param worker where previews are baked (not the EDT)
* @param onCancel Cancel, or the window closed with its close button
*/
public DesignEditorPanel(DesignPart part, DesignLayout.Part layout, BakedBoardGeometry.Mesh mesh,
PartOutline outline, BufferedImage image, ImagePlacement placement, String startName, Executor worker,
BiConsumer<String, ImagePlacement> onSave, Runnable onCancel, Runnable onTemplate)
{
super(new BorderLayout(8, 8));
this.part = part;
this.layout = layout;
this.mesh = mesh;
this.image = image;
this.outline = outline;
this.worker = worker;
this.onCancel = onCancel;
setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
canvas = new DesignCanvas(image, outline, placement, part != DesignPart.WHEELS, p -> requestPreview());
add(canvas, BorderLayout.CENTER);

JPanel side = new JPanel();
side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
JLabel previewTitle = new JLabel("In game");
previewTitle.setAlignmentX(CENTER_ALIGNMENT);
side.add(previewTitle);
preview.setName("editor:preview");
preview.setAlignmentX(CENTER_ALIGNMENT);
preview.setHorizontalAlignment(SwingConstants.CENTER);
Dimension pd = part == DesignPart.WHEELS ? new Dimension(PREVIEW_WHEEL, PREVIEW_WHEEL)
: new Dimension(PREVIEW_SHORT, PREVIEW_LONG);
preview.setPreferredSize(pd);
preview.setMinimumSize(pd);
side.add(preview);
add(side, BorderLayout.EAST);

JPanel bottom = new JPanel();
bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
tools.add(button("rotate", () -> canvas.set(canvas.placement().rotated())));
tools.add(button("flip", () -> canvas.set(canvas.placement().flippedHorizontally())));
tools.add(button("fit", () -> canvas.set(canvas.placement().fit(outline.bounds()))));
tools.add(button("fill", () -> canvas.set(canvas.placement().fill(outline.bounds()))));
tools.add(button("stretch", () -> canvas.set(canvas.placement().stretched(outline.bounds()))));
tools.add(button("reset",
() -> canvas.set(ImagePlacement.initial(image.getWidth(), image.getHeight(), outline.bounds()))));
bottom.add(tools);
JLabel help = new JLabel(Text.get("de.help"));
help.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
helpRow.add(help);
bottom.add(helpRow);

JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
nameRow.add(new JLabel("Name:"));
name.setName("editor:name");
name.setText(startName);
name.getDocument().addDocumentListener(new DocumentListener()
{
@Override
public void insertUpdate(DocumentEvent e)
{
checkName();
}

@Override
public void removeUpdate(DocumentEvent e)
{
checkName();
}

@Override
public void changedUpdate(DocumentEvent e)
{
checkName();
}
});
nameRow.add(name);
nameError.setName("editor:nameError");
nameError.setForeground(new Color(255, 150, 120));
nameRow.add(nameError);
bottom.add(nameRow);

JPanel actions = new JPanel(new BorderLayout());
actions.add(button("template", onTemplate), BorderLayout.WEST);
JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
right.add(button("cancel", onCancel));
save.setName("editor:save");
// only enabled with a good name (checkName)
save.addActionListener(e ->
{
previewTimer.stop();
onSave.accept(name.getText().trim(), canvas.placement());
});
right.add(save);
actions.add(right, BorderLayout.EAST);
bottom.add(actions);
add(bottom, BorderLayout.SOUTH);

previewTimer.setRepeats(false);
checkName();
requestPreview();
}

/** Button editor:{@code id}: its label is de.{@code id}, its tooltip de.tip.{@code id} (Cancel has none). */
private JButton button(String id, Runnable action)
{
JButton b = new JButton(Text.get("de." + id));
b.setName("editor:" + id);
b.setFocusable(false);
b.setToolTipText("cancel".equals(id) ? null : Text.get("de.tip." + id, part.key));
b.addActionListener(e -> action.run());
return b;
}

/**
* Shows the editor in its own window: not modal, owned by {@code owner} (the client's frame), sized to fit the
* screen. Closing it with its close button is Cancel.
*/
JDialog open(Window owner, String title)
{
JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.MODELESS);
dialog.setContentPane(this);
dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
dialog.addWindowListener(new WindowAdapter()
{
@Override
public void windowClosing(WindowEvent e)
{
onCancel.run();
}

@Override
public void windowOpened(WindowEvent e)
{
// lets the canvas take the arrow keys
canvas.requestFocusInWindow();
}
});
GraphicsConfiguration gc = (owner != null ? owner : dialog).getGraphicsConfiguration();
Rectangle screen = gc.getBounds();
Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
dialog.pack();
Dimension want = dialog.getPreferredSize();
int h = Math.min(screen.height - in.top - in.bottom - 40, Math.max(want.height, 860));
int w = Math.min(screen.width - in.left - in.right - 40, Math.max(want.width, 620));
dialog.setSize(w, h);
dialog.setMinimumSize(new Dimension(Math.min(w, 480), Math.min(h, 480)));
dialog.setLocationRelativeTo(owner);
dialog.setVisible(true);
return dialog;
}

/** Stops previewing once the window is gone. */
@Override
public void removeNotify()
{
super.removeNotify();
previewTimer.stop();
previewDirty = false;
}

private void checkName()
{
String problem = CustomDesignRules.nameProblem(name.getText());
nameError.setText(problem == null ? " " : problem);
save.setEnabled(problem == null);
}

/** The placement shown. */
ImagePlacement placement()
{
return canvas.placement();
}

DesignCanvas canvas()
{
return canvas;
}

int previewsDone()
{
return previewsDone;
}

/** A preview is wanted: now if none is being baked, else once the one in hand is shown. */
private void requestPreview()
{
previewDirty = true;
if (!previewBusy && !previewTimer.isRunning())
startPreview();
}

private void startPreview()
{
if (previewBusy || !previewDirty)
return;
previewBusy = true;
previewDirty = false;
ImagePlacement p = canvas.placement();
worker.execute(() ->
{
BufferedImage shown = previewImage(p);
SwingUtilities.invokeLater(() ->
{
previewBusy = false;
if (shown != null)
{
preview.setIcon(new ImageIcon(shown));
previewsDone++;
}
if (previewDirty)
previewTimer.restart();
});
});
}

/** Worker thread: the part baked in this placement, drawn as the preview; null if that failed. */
private BufferedImage previewImage(ImagePlacement p)
{
try
{
if (source == null)
source = CustomBake.source(image, part);
int[] colours = CustomBake.bake(mesh, layout, p, source, true);
return part == DesignPart.WHEELS ? ThumbRender.render(part, mesh, colours, PREVIEW_WHEEL, PREVIEW_WHEEL)
// drawn nose to the right, then stood up like the canvas
: standUp(ThumbRender.render(part, mesh, colours, PREVIEW_LONG, PREVIEW_SHORT), outline.noseUp);
}
catch (RuntimeException e)
{
return null;
}
}

/** A picture with the nose to the right turned so the nose points up (counter-clockwise) or down. */
static BufferedImage standUp(BufferedImage img, boolean noseUp)
{
BufferedImage out = new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_ARGB);
Graphics2D g = out.createGraphics();
g.translate(noseUp ? 0 : img.getHeight(), noseUp ? img.getWidth() : 0);
g.rotate(noseUp ? -Math.PI / 2 : Math.PI / 2);
g.drawImage(img, 0, 0, null);
g.dispose();
return out;
}
}
