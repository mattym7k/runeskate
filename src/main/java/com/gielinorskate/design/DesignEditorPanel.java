package com.gielinorskate.design;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The design editor's content (shown in a {@link DesignEditorDialog}): the canvas, a live preview of the part baked at
 * in-game resolution (High detail colours, drawn flat like the panel's thumbnails), the placement buttons (Rotate
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
	private static final Color ERROR = new Color(255, 150, 120);

	/** What the editor ends with. */
	public interface Listener
	{
		void save(String name, ImagePlacement placement);

		void cancel();

		void downloadTemplate();
	}

	private final DesignPart part;
	private final DesignLayout.Part layout;
	private final BakedBoardGeometry.Mesh mesh;
	private final BufferedImage image;
	private final PartOutline outline;
	private final Executor worker;
	private final Listener listener;
	private final DesignCanvas canvas;
	private final JLabel preview = new JLabel();
	private final JTextField name = new JTextField(16);
	private final JLabel nameError = new JLabel(" ");
	private final JButton save = new JButton("Save");
	private final Timer previewTimer;
	private CustomBake.Source source;
	private boolean previewBusy;
	private boolean previewDirty;
	private int previewsDone;

	/**
	 * @param mesh the part at High detail (the preview bakes it)
	 * @param image the kept copy of the player's image
	 * @param placement where it starts (a new design: {@link ImagePlacement#initial})
	 * @param worker where previews are baked (not the EDT)
	 */
	public DesignEditorPanel(DesignPart part, DesignLayout.Part layout, BakedBoardGeometry.Mesh mesh,
		PartOutline outline, BufferedImage image, ImagePlacement placement, String startName, Executor worker,
		Listener listener)
	{
		super(new BorderLayout(8, 8));
		this.part = part;
		this.layout = layout;
		this.mesh = mesh;
		this.image = image;
		this.outline = outline;
		this.worker = worker;
		this.listener = listener;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		canvas = new DesignCanvas(image, outline, placement, part != DesignPart.WHEELS);
		canvas.setListener(p -> requestPreview());
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
		tools.add(button("editor:rotate", "Rotate 90°", "Turn the image a quarter turn clockwise",
			() -> canvas.set(canvas.placement().rotated())));
		tools.add(button("editor:flip", "Flip ↔", "Mirror the image left to right",
			() -> canvas.set(canvas.placement().flippedHorizontally())));
		tools.add(button("editor:fit", "Fit", "Show the whole image inside the outline",
			() -> canvas.set(canvas.placement().fit(outline.bounds()))));
		tools.add(button("editor:fill", "Fill", "Cover the whole outline with the image",
			() -> canvas.set(canvas.placement().fill(outline.bounds()))));
		tools.add(button("editor:stretch", "Stretch to outline",
			"Stretch the image to cover exactly the outline's box (its proportions change)",
			() -> canvas.set(canvas.placement().stretched(outline.bounds()))));
		tools.add(button("editor:reset", "Reset", "Back to the start: unturned, covering the outline",
			() -> canvas.set(ImagePlacement.initial(image.getWidth(), image.getHeight(), outline.bounds()))));
		bottom.add(tools);
		JLabel help = new JLabel("<html>Drag to move, mouse wheel to zoom, arrow keys to nudge (Shift: further).<br>"
			+ "Drag an edge of the box to stretch, a corner to resize (Shift: keep proportions).</html>");
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
		nameError.setForeground(ERROR);
		nameRow.add(nameError);
		bottom.add(nameRow);

		JPanel actions = new JPanel(new BorderLayout());
		actions.add(button("editor:template", "Download template",
			"Save a picture of the " + part.key + "'s outline to paint your design on", listener::downloadTemplate),
			BorderLayout.WEST);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.add(button("editor:cancel", "Cancel", null, listener::cancel));
		save.setName("editor:save");
		save.addActionListener(e -> doSave());
		right.add(save);
		actions.add(right, BorderLayout.EAST);
		bottom.add(actions);
		add(bottom, BorderLayout.SOUTH);

		previewTimer = new Timer(PREVIEW_MS, e -> startPreview());
		previewTimer.setRepeats(false);
		checkName();
		requestPreview();
	}

	private static JButton button(String id, String text, String tip, Runnable action)
	{
		JButton b = new JButton(text);
		b.setName(id);
		b.setFocusable(false);
		if (tip != null)
		{
			b.setToolTipText(tip);
		}
		b.addActionListener(e -> action.run());
		return b;
	}

	private void checkName()
	{
		String problem = CustomDesignRules.nameProblem(name.getText());
		nameError.setText(problem == null ? " " : problem);
		save.setEnabled(problem == null);
	}

	private void doSave()
	{
		if (CustomDesignRules.nameProblem(name.getText()) == null)
		{
			previewTimer.stop();
			listener.save(name.getText().trim(), canvas.placement());
		}
	}

	/** The placement shown. */
	public ImagePlacement placement()
	{
		return canvas.placement();
	}

	DesignCanvas canvas()
	{
		return canvas;
	}

	/** How many previews have been shown (tests). */
	int previewsDone()
	{
		return previewsDone;
	}

	/** Lets the canvas take the arrow keys. */
	public void focusCanvas()
	{
		canvas.requestFocusInWindow();
	}

	/** Stops previewing (the dialog closed). */
	public void dispose()
	{
		previewTimer.stop();
		previewDirty = false;
	}

	/** A preview is wanted: now if none is being baked, else once the one in hand is shown. */
	private void requestPreview()
	{
		previewDirty = true;
		if (!previewBusy && !previewTimer.isRunning())
		{
			startPreview();
		}
	}

	private void startPreview()
	{
		if (previewBusy || !previewDirty)
		{
			return;
		}
		previewBusy = true;
		previewDirty = false;
		ImagePlacement p = canvas.placement();
		worker.execute(() ->
		{
			BufferedImage shown;
			try
			{
				shown = previewImage(p);
			}
			catch (RuntimeException e)
			{
				shown = null;
			}
			BufferedImage result = shown;
			SwingUtilities.invokeLater(() ->
			{
				previewBusy = false;
				if (result != null)
				{
					preview.setIcon(new ImageIcon(result));
					previewsDone++;
				}
				if (previewDirty)
				{
					previewTimer.restart();
				}
			});
		});
	}

	/** Worker thread: the part baked in this placement, drawn as the preview. */
	private BufferedImage previewImage(ImagePlacement p)
	{
		if (source == null)
		{
			int w = image.getWidth();
			int h = image.getHeight();
			source = new CustomBake.Source(image.getRGB(0, 0, w, h, null, 0, w), w, h, backdrop(part));
		}
		int[] colours = CustomBake.bake(mesh, layout, p, source, true);
		if (part == DesignPart.WHEELS)
		{
			return ThumbRender.render(part, mesh, colours, PREVIEW_WHEEL, PREVIEW_WHEEL);
		}
		// drawn nose to the right, then stood up like the canvas
		return standUp(ThumbRender.render(part, mesh, colours, PREVIEW_LONG, PREVIEW_SHORT), outline.noseUp);
	}

	/** A picture with the nose to the right turned so the nose points up (or down). */
	static BufferedImage standUp(BufferedImage img, boolean noseUp)
	{
		BufferedImage out = new BufferedImage(img.getHeight(), img.getWidth(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try
		{
			if (noseUp)
			{
				// counter-clockwise: right goes up
				g.translate(0, img.getWidth());
				g.rotate(-Math.PI / 2);
			}
			else
			{
				g.translate(img.getHeight(), 0);
				g.rotate(Math.PI / 2);
			}
			g.drawImage(img, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	/** The colour shown through a design's transparent pixels: the part's plain colour. */
	public static int backdrop(DesignPart part)
	{
		switch (part)
		{
			case GRIP:
				return 0x202020;
			case DECK:
				return 0xD9C49F;
			default:
				return 0xEDEBE3;
		}
	}
}
