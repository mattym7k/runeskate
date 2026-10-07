package com.gielinorskate.design;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * The design editor's canvas: the player's image under the part's outline, everything outside the outline dimmed,
 * the truck bolts as dots, and the image's box with eight handles.
 * <ul>
 * <li>Dragging anywhere but a handle moves the image with the cursor.</li>
 * <li>Dragging an edge (anywhere along it) stretches the image that way with the opposite edge fixed; a corner moves
 * both its edges (Shift: keeping the proportions). The handles work on the box as seen, so whatever the turn or
 * mirror the top edge goes up and down ({@link ImagePlacement#resized}).</li>
 * <li>The mouse wheel scales the image about the cursor; the arrow keys nudge it (Shift: further).</li>
 * </ul>
 * Every drag is worked out from where it started (the placement and cursor at the press), so coalesced or dropped
 * mouse events can't add up wrong. Shows one {@link ImagePlacement} and reports each change to its listener. EDT
 * only.
 */
final class DesignCanvas extends JComponent
{
	/** Room around the layout, view pixels. */
	static final int PAD = 24;
	/** Outside the outline: black at this opacity (about 60% darker). */
	static final int DIM_ALPHA = 153;
	static final double WHEEL_STEP = 1.1;
	static final double NUDGE = 1;
	static final double NUDGE_FAR = 10;
	/** How near (view pixels) the cursor must be to an edge or corner of the image's box to grab it. */
	static final int HANDLE_HIT = 6;
	/** The drawn handles' size, view pixels. */
	static final int HANDLE_SIZE = 7;
	private static final Color BACKGROUND = new Color(40, 40, 40);
	private static final Color OUTLINE = new Color(255, 152, 31);
	private static final Color DIM = new Color(0, 0, 0, DIM_ALPHA);
	private static final Color BOX = new Color(255, 255, 255, 170);
	private static final BasicStroke BOX_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
		10f, new float[]{4f, 4f}, 0f);

	private final BufferedImage image;
	private final PartOutline outline;
	private final boolean showNose;
	private final BufferedImage dimOverlay;
	/** The outline's edges as one path in layout pixels, drawn in one go. */
	private final Path2D outlinePath;
	private ImagePlacement placement;
	private Consumer<ImagePlacement> listener = p ->
	{
	};
	/** The placement when the drag started; null when not dragging. */
	private ImagePlacement dragFrom;
	/** The handle dragged ({@link ImagePlacement#resized} edges), 0 for a move. */
	private int dragEdges;
	/** Where the drag started, view pixels. */
	private int dragX;
	private int dragY;

	DesignCanvas(BufferedImage image, PartOutline outline, ImagePlacement start, boolean showNose)
	{
		this.image = image;
		this.outline = outline;
		this.placement = start;
		this.showNose = showNose;
		this.dimOverlay = dimOverlay(outline);
		this.outlinePath = outlinePath(outline);
		setName("editor:canvas");
		setFocusable(true);
		setOpaque(true);
		setPreferredSize(new Dimension(360, 640));
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				requestFocusInWindow();
				if (!SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				dragX = e.getX();
				dragY = e.getY();
				dragEdges = handleAt(dragX, dragY);
				dragFrom = placement;
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					dragFrom = null;
					showCursor(e.getX(), e.getY());
				}
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				showCursor(e.getX(), e.getY());
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (dragFrom == null)
				{
					return;
				}
				double s = viewScale();
				double dx = (e.getX() - dragX) / s;
				double dy = (e.getY() - dragY) / s;
				set(dragEdges == 0 ? dragFrom.moved(dx, dy) : dragFrom.resized(dragEdges, dx, dy, e.isShiftDown()));
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				zoomAt(e.getX(), e.getY(), e.getPreciseWheelRotation());
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
		key(KeyEvent.VK_LEFT, -1, 0);
		key(KeyEvent.VK_RIGHT, 1, 0);
		key(KeyEvent.VK_UP, 0, -1);
		key(KeyEvent.VK_DOWN, 0, 1);
	}

	private void key(int code, int dx, int dy)
	{
		for (boolean far : new boolean[]{false, true})
		{
			String name = "nudge" + code + (far ? "far" : "");
			getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, far ? InputEvent.SHIFT_DOWN_MASK : 0), name);
			getActionMap().put(name, new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					nudge(dx, dy, far);
				}
			});
		}
	}

	private void showCursor(int x, int y)
	{
		setCursor(Cursor.getPredefinedCursor(cursorFor(handleAt(x, y))));
	}

	/** Scales the image about view position (x, y): wheel rotation -1 (up, away) zooms in one step. */
	void zoomAt(int x, int y, double rotation)
	{
		double[] at = toLayout(x, y);
		set(placement.scaledAbout(at[0], at[1], Math.pow(WHEEL_STEP, -rotation)));
	}

	/** Moves the image by one nudge (Shift: a far one) in the direction (dx, dy) of -1, 0 or 1. */
	void nudge(int dx, int dy, boolean far)
	{
		double step = far ? NUDGE_FAR : NUDGE;
		set(placement.moved(dx * step, dy * step));
	}

	void setListener(Consumer<ImagePlacement> listener)
	{
		this.listener = listener;
	}

	ImagePlacement placement()
	{
		return placement;
	}

	/** Shows {@code p} and tells the listener. */
	void set(ImagePlacement p)
	{
		if (p.equals(placement))
		{
			return;
		}
		placement = p;
		repaint();
		listener.accept(p);
	}

	/** View pixels per layout pixel. */
	double viewScale()
	{
		int w = Math.max(1, getWidth() > 0 ? getWidth() : getPreferredSize().width);
		int h = Math.max(1, getHeight() > 0 ? getHeight() : getPreferredSize().height);
		return Math.max(1e-3, Math.min((w - 2.0 * PAD) / outline.width, (h - 2.0 * PAD) / outline.height));
	}

	/** {x, y} of the layout's top-left corner in the view. */
	private double[] origin()
	{
		double s = viewScale();
		int w = getWidth() > 0 ? getWidth() : getPreferredSize().width;
		int h = getHeight() > 0 ? getHeight() : getPreferredSize().height;
		return new double[]{(w - outline.width * s) / 2, (h - outline.height * s) / 2};
	}

	/** The layout position under view position (x, y). */
	double[] toLayout(double x, double y)
	{
		double s = viewScale();
		double[] o = origin();
		return new double[]{(x - o[0]) / s, (y - o[1]) / s};
	}

	/** The view position of layout position (lx, ly). */
	double[] toView(double lx, double ly)
	{
		double s = viewScale();
		double[] o = origin();
		return new double[]{o[0] + lx * s, o[1] + ly * s};
	}

	/**
	 * The handle of the image's box at view position (x, y): its {@link ImagePlacement#resized} edges (two at a
	 * corner, which wins over an edge), or 0 where a drag moves the image.
	 */
	int handleAt(int x, int y)
	{
		double[] b = placement.bounds();
		double[] tl = toView(b[0], b[1]);
		double[] br = toView(b[2], b[3]);
		boolean withinX = x >= tl[0] - HANDLE_HIT && x <= br[0] + HANDLE_HIT;
		boolean withinY = y >= tl[1] - HANDLE_HIT && y <= br[1] + HANDLE_HIT;
		int edges = 0;
		if (withinY)
		{
			// a box narrower than two grabs: the nearer edge
			double dl = Math.abs(x - tl[0]);
			double dr = Math.abs(x - br[0]);
			if (Math.min(dl, dr) <= HANDLE_HIT)
			{
				edges |= dl < dr ? ImagePlacement.LEFT : ImagePlacement.RIGHT;
			}
		}
		if (withinX)
		{
			double dt = Math.abs(y - tl[1]);
			double db = Math.abs(y - br[1]);
			if (Math.min(dt, db) <= HANDLE_HIT)
			{
				edges |= dt < db ? ImagePlacement.TOP : ImagePlacement.BOTTOM;
			}
		}
		return edges;
	}

	/** The cursor over a handle ({@link #handleAt}): a resize arrow its way, else the move cursor. */
	static int cursorFor(int edges)
	{
		boolean l = (edges & ImagePlacement.LEFT) != 0;
		boolean r = (edges & ImagePlacement.RIGHT) != 0;
		if ((edges & ImagePlacement.TOP) != 0)
		{
			return l ? Cursor.NW_RESIZE_CURSOR : r ? Cursor.NE_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR;
		}
		if ((edges & ImagePlacement.BOTTOM) != 0)
		{
			return l ? Cursor.SW_RESIZE_CURSOR : r ? Cursor.SE_RESIZE_CURSOR : Cursor.S_RESIZE_CURSOR;
		}
		return l ? Cursor.W_RESIZE_CURSOR : r ? Cursor.E_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
	}

	/** Black at {@link #DIM_ALPHA} outside the outline, clear inside, at layout size. */
	static BufferedImage dimOverlay(PartOutline outline)
	{
		BufferedImage img = new BufferedImage(outline.width, outline.height, BufferedImage.TYPE_INT_ARGB);
		boolean[] mask = outline.mask();
		int[] px = new int[mask.length];
		for (int i = 0; i < px.length; i++)
		{
			px[i] = mask[i] ? 0 : DIM_ALPHA << 24;
		}
		img.setRGB(0, 0, outline.width, outline.height, px, 0, outline.width);
		return img;
	}

	private static Path2D outlinePath(PartOutline outline)
	{
		Path2D path = new Path2D.Double();
		float[] e = outline.edges();
		for (int i = 0; i < e.length; i += 4)
		{
			path.moveTo(e[i], e[i + 1]);
			path.lineTo(e[i + 2], e[i + 3]);
		}
		return path;
	}

	/** The placement as a transform from image pixels to layout pixels. */
	static AffineTransform transform(ImagePlacement p)
	{
		AffineTransform t = new AffineTransform();
		t.translate(p.cx, p.cy);
		t.quadrantRotate(p.turns);
		if (p.flipped)
		{
			t.scale(-1, 1);
		}
		t.scale(p.scaleX, p.scaleY);
		t.translate(-p.imageWidth / 2.0, -p.imageHeight / 2.0);
		return t;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, getWidth(), getHeight());
			double s = viewScale();
			double[] o = origin();
			AffineTransform view = new AffineTransform();
			view.translate(o[0], o[1]);
			view.scale(s, s);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			AffineTransform imageToView = new AffineTransform(view);
			imageToView.concatenate(transform(placement));
			g.drawImage(image, imageToView, null);
			// dim everything outside the outline: the margin, then the layout outside the part
			Rectangle2D layoutRect = new Rectangle2D.Double(o[0], o[1], outline.width * s, outline.height * s);
			Area margin = new Area(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
			margin.subtract(new Area(layoutRect));
			g.setColor(DIM);
			g.fill(margin);
			g.drawImage(dimOverlay, view, null);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(OUTLINE);
			g.setStroke(new BasicStroke(1.5f));
			g.draw(view.createTransformedShape(outlinePath));
			for (double[] b : outline.bolts())
			{
				double x = o[0] + b[0] * s;
				double y = o[1] + b[1] * s;
				g.setColor(Color.BLACK);
				g.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
				g.setColor(Color.WHITE);
				g.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
			}
			if (showNose)
			{
				double[] b = outline.bounds();
				g.setColor(OUTLINE);
				g.setFont(getFont() != null ? getFont().deriveFont(Font.BOLD) : new Font(Font.SANS_SERIF, Font.BOLD, 12));
				String label = outline.noseUp ? "▲ nose" : "▼ nose";
				int tw = g.getFontMetrics().stringWidth(label);
				double cx = o[0] + (b[0] + b[2]) / 2 * s;
				double y = outline.noseUp ? o[1] + b[1] * s - 6 : o[1] + b[3] * s + g.getFontMetrics().getAscent() + 4;
				g.drawString(label, (float) (cx - tw / 2.0), (float) Math.max(g.getFontMetrics().getAscent(), y));
			}
			paintHandles(g);
		}
		finally
		{
			g.dispose();
		}
	}

	/** The image's box, dashed, with a square handle at each corner and edge middle. */
	private void paintHandles(Graphics2D g)
	{
		double[] b = placement.bounds();
		double[] tl = toView(b[0], b[1]);
		double[] br = toView(b[2], b[3]);
		g.setColor(BOX);
		g.setStroke(BOX_STROKE);
		g.draw(new Rectangle2D.Double(tl[0], tl[1], br[0] - tl[0], br[1] - tl[1]));
		g.setStroke(new BasicStroke(1f));
		double mx = (tl[0] + br[0]) / 2;
		double my = (tl[1] + br[1]) / 2;
		double[][] at = {{tl[0], tl[1]}, {mx, tl[1]}, {br[0], tl[1]}, {br[0], my}, {br[0], br[1]}, {mx, br[1]},
			{tl[0], br[1]}, {tl[0], my}};
		double h = HANDLE_SIZE / 2.0;
		for (double[] p : at)
		{
			Rectangle2D r = new Rectangle2D.Double(p[0] - h, p[1] - h, HANDLE_SIZE, HANDLE_SIZE);
			g.setColor(Color.WHITE);
			g.fill(r);
			g.setColor(Color.BLACK);
			g.draw(r);
		}
	}
}
