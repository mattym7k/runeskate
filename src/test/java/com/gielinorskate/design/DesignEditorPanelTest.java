package com.gielinorskate.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.BakedBoardGeometry;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;

/** The editor's controls, headless: no window is opened. */
public class DesignEditorPanelTest
{
	private final List<String> calls = new ArrayList<>();
	private ImagePlacement saved;
	private String savedName;
	private DesignEditorPanel editor;
	private PartOutline outline;
	private BufferedImage image;

	@Before
	public void setUp() throws Exception
	{
		BakedBoardGeometry.Mesh grip = BakedBoardGeometry.sharedBoard(true)[DesignPart.GRIP.index];
		DesignLayout.Part layout = DesignLayout.bundled().of(DesignPart.GRIP);
		outline = PartOutline.of(grip, layout, true);
		image = new BufferedImage(60, 120, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 120; y++)
		{
			for (int x = 0; x < 60; x++)
			{
				image.setRGB(x, y, y < 60 ? 0xFFFF0000 : 0xFF0000FF);
			}
		}
		ImagePlacement start = ImagePlacement.initial(60, 120, outline.bounds());
		// previews baked right here (the plugin bakes them on its executor)
		editor = new DesignEditorPanel(DesignPart.GRIP, layout, grip, outline, image, start, "My grip", Runnable::run,
			(name, placement) ->
			{
				savedName = name;
				saved = placement;
			}, () -> calls.add("cancel"), () -> calls.add("template"));
		flush();
	}

	private static void flush() throws Exception
	{
		for (int i = 0; i < 3; i++)
		{
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
	}

	private static Component named(Container c, String name)
	{
		for (Component child : c.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container) child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private void click(String name)
	{
		((AbstractButton) named(editor, name)).doClick();
	}

	@Test
	public void startsCoveringTheOutlineWithAPreview()
	{
		ImagePlacement p = editor.placement();
		double[] b = outline.bounds();
		assertTrue(p.placedWidth() >= b[2] - b[0] - 1e-6);
		assertTrue(p.placedHeight() >= b[3] - b[1] - 1e-6);
		assertTrue(editor.previewsDone() >= 1);
		Icon icon = ((JLabel) named(editor, "editor:preview")).getIcon();
		assertNotNull(icon);
		// a grip preview stands up like the canvas
		assertEquals(DesignEditorPanel.PREVIEW_SHORT, icon.getIconWidth());
		assertEquals(DesignEditorPanel.PREVIEW_LONG, icon.getIconHeight());
	}

	@Test
	public void buttonsTurnFlipFitFillAndReset() throws Exception
	{
		ImagePlacement start = editor.placement();
		click("editor:rotate");
		assertEquals(1, editor.placement().turns);
		click("editor:flip");
		assertTrue(editor.placement().flipped);
		click("editor:fit");
		double[] b = outline.bounds();
		assertTrue(editor.placement().placedWidth() <= b[2] - b[0] + 1e-6);
		assertTrue(editor.placement().placedHeight() <= b[3] - b[1] + 1e-6);
		click("editor:fill");
		assertTrue(editor.placement().placedWidth() >= b[2] - b[0] - 1e-6);
		click("editor:reset");
		assertEquals(start, editor.placement());
		flush();
		assertTrue(editor.previewsDone() >= 2);
	}

	@Test
	public void dragMovesWheelScalesAndArrowsNudge()
	{
		DesignCanvas canvas = editor.canvas();
		ImagePlacement p0 = editor.placement();
		double s = canvas.viewScale();
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 100, 100,
			1, false, MouseEvent.BUTTON1));
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, 130, 90,
			1, false, MouseEvent.BUTTON1));
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 130, 90, 1, false,
			MouseEvent.BUTTON1));
		ImagePlacement p1 = editor.placement();
		assertEquals(p0.cx + 30 / s, p1.cx, 1e-9);
		assertEquals(p0.cy - 10 / s, p1.cy, 1e-9);
		// wheel up zooms in about the cursor
		double[] under = p1.toImage(canvas.toLayout(150, 200)[0], canvas.toLayout(150, 200)[1]);
		canvas.zoomAt(150, 200, -1);
		ImagePlacement p2 = editor.placement();
		assertEquals(p1.scaleX * DesignCanvas.WHEEL_STEP, p2.scaleX, 1e-9);
		double[] l = canvas.toLayout(150, 200);
		double[] still = p2.toImage(l[0], l[1]);
		assertEquals(under[0], still[0], 1e-6);
		assertEquals(under[1], still[1], 1e-6);
		// arrows: one layout pixel, Shift: ten
		press(canvas, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
		assertEquals(p2.cx + DesignCanvas.NUDGE, editor.placement().cx, 1e-9);
		press(canvas, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK));
		assertEquals(p2.cy - DesignCanvas.NUDGE_FAR, editor.placement().cy, 1e-9);
	}

	private static void mouse(DesignCanvas canvas, int id, int x, int y, int modifiers)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, id, 0, modifiers, x, y, id == MouseEvent.MOUSE_PRESSED ? 1 : 0,
			false, id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
	}

	/** Drags from (x0, y0) through each point of {@code path} {x, y, ...} and lets go at the last. */
	private static void drag(DesignCanvas canvas, int x0, int y0, int modifiers, int... path)
	{
		mouse(canvas, MouseEvent.MOUSE_PRESSED, x0, y0, InputEvent.BUTTON1_DOWN_MASK | modifiers);
		for (int i = 0; i < path.length; i += 2)
		{
			mouse(canvas, MouseEvent.MOUSE_DRAGGED, path[i], path[i + 1], InputEvent.BUTTON1_DOWN_MASK | modifiers);
		}
		mouse(canvas, MouseEvent.MOUSE_RELEASED, path[path.length - 2], path[path.length - 1], modifiers);
	}

	@Test
	public void aLongDragFollowsTheCursorOneToOneAtAnyZoomTurnAndFlip()
	{
		DesignCanvas canvas = editor.canvas();
		for (int[] size : new int[][]{{360, 640}, {700, 1300}, {250, 400}})
		{
			canvas.setSize(size[0], size[1]);
			for (int turns = 0; turns < 4; turns++)
			{
				for (boolean flip : new boolean[]{false, true})
				{
					ImagePlacement start = new ImagePlacement(60, 120, 180, 500, 1.3, turns, flip);
					canvas.set(start);
					double s = canvas.viewScale();
					// a wiggly drag: many small steps, some back, ending 37 right and 52 up; pressed in the dim margin
					drag(canvas, 5, 5, 0, 8, 7, 12, 3, 20, -10, 15, -20, 30, -40, 42, -47);
					ImagePlacement p = canvas.placement();
					String at = "size " + size[0] + " turns " + turns + " flip " + flip;
					assertEquals(at, start.cx + 37 / s, p.cx, 1e-9);
					assertEquals(at, start.cy - 52 / s, p.cy, 1e-9);
					// the image point under the cursor when pressed is under it when let go
					double[] l0 = canvas.toLayout(5, 5);
					double[] l1 = canvas.toLayout(42, -47);
					assertArrayEquals(at, start.toImage(l0[0], l0[1]), p.toImage(l1[0], l1[1]), 1e-6);
				}
			}
		}
	}

	/** View position {x, y} of layout position (lx, ly), rounded to whole pixels like mouse events. */
	private static int[] view(DesignCanvas canvas, double lx, double ly)
	{
		double[] v = canvas.toView(lx, ly);
		return new int[]{(int) Math.round(v[0]), (int) Math.round(v[1])};
	}

	/** A stretched, turned image well inside a 400 x 800 canvas. */
	private static ImagePlacement inView(DesignCanvas canvas, int turns, boolean flip)
	{
		canvas.setSize(400, 800);
		ImagePlacement p = new ImagePlacement(60, 120, 180, 500, 1.2, 2.5, turns, flip);
		canvas.set(p);
		return p;
	}

	@Test
	public void handlesAreFoundOnTheImagesBoxEdgesAndCorners()
	{
		DesignCanvas canvas = editor.canvas();
		ImagePlacement p = inView(canvas, 1, true);
		double[] b = p.bounds();
		double mx = (b[0] + b[2]) / 2;
		double my = (b[1] + b[3]) / 2;
		int l = ImagePlacement.LEFT;
		int t = ImagePlacement.TOP;
		int r = ImagePlacement.RIGHT;
		int d = ImagePlacement.BOTTOM;
		Object[][] cases = {
			{b[0], b[1], t | l}, {b[2], b[1], t | r}, {b[0], b[3], d | l}, {b[2], b[3], d | r},
			{mx, b[1], t}, {mx, b[3], d}, {b[0], my, l}, {b[2], my, r},
			// anywhere along an edge, not only at its middle
			{b[0] + (b[2] - b[0]) * 0.3, b[1], t}, {b[2], b[1] + (b[3] - b[1]) * 0.8, r},
			// inside moves, far outside moves too
			{mx, my, 0}, {b[0] + 20, b[1] + 20, 0}};
		for (Object[] c : cases)
		{
			int[] v = view(canvas, (Double) c[0], (Double) c[1]);
			assertEquals(java.util.Arrays.toString(c), (int) (Integer) c[2], canvas.handleAt(v[0], v[1]));
			// a few pixels off still grabs it
			assertEquals(java.util.Arrays.toString(c), (int) (Integer) c[2], canvas.handleAt(v[0] + 3, v[1] - 3));
		}
		assertEquals(0, canvas.handleAt(2, 2));
	}

	@Test
	public void theCursorShowsWhatADragWouldDo()
	{
		DesignCanvas canvas = editor.canvas();
		ImagePlacement p = inView(canvas, 0, false);
		double[] b = p.bounds();
		double mx = (b[0] + b[2]) / 2;
		double my = (b[1] + b[3]) / 2;
		Object[][] cases = {
			{mx, b[1], java.awt.Cursor.N_RESIZE_CURSOR}, {mx, b[3], java.awt.Cursor.S_RESIZE_CURSOR},
			{b[0], my, java.awt.Cursor.W_RESIZE_CURSOR}, {b[2], my, java.awt.Cursor.E_RESIZE_CURSOR},
			{b[0], b[1], java.awt.Cursor.NW_RESIZE_CURSOR}, {b[2], b[1], java.awt.Cursor.NE_RESIZE_CURSOR},
			{b[0], b[3], java.awt.Cursor.SW_RESIZE_CURSOR}, {b[2], b[3], java.awt.Cursor.SE_RESIZE_CURSOR},
			{mx, my, java.awt.Cursor.MOVE_CURSOR}};
		for (Object[] c : cases)
		{
			int[] v = view(canvas, (Double) c[0], (Double) c[1]);
			mouse(canvas, MouseEvent.MOUSE_MOVED, v[0], v[1], 0);
			assertEquals(java.util.Arrays.toString(c), (int) (Integer) c[2], canvas.getCursor().getType());
		}
	}

	@Test
	public void draggingAnEdgeStretchesThatWayAndKeepsTheOppositeEdge()
	{
		DesignCanvas canvas = editor.canvas();
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = inView(canvas, turns, flip);
				double s = canvas.viewScale();
				double[] b = p.bounds();
				int[] top = view(canvas, (b[0] + b[2]) / 2, b[1]);
				// up 30 (and a sideways wobble, ignored on an edge)
				drag(canvas, top[0], top[1], 0, top[0] + 4, top[1] - 10, top[0] + 9, top[1] - 30);
				double[] nb = canvas.placement().bounds();
				String at = "turns " + turns + " flip " + flip;
				assertEquals(at, b[1] - 30 / s, nb[1], 1e-6);
				assertEquals(at, b[3], nb[3], 1e-6);
				assertEquals(at, b[0], nb[0], 1e-6);
				assertEquals(at, b[2], nb[2], 1e-6);
				assertEquals(at, turns, canvas.placement().turns);
				assertEquals(at, flip, canvas.placement().flipped);

				p = inView(canvas, turns, flip);
				b = p.bounds();
				int[] right = view(canvas, b[2], (b[1] + b[3]) / 2);
				drag(canvas, right[0], right[1], 0, right[0] - 20, right[1] + 7);
				nb = canvas.placement().bounds();
				assertEquals(at, b[2] - 20 / s, nb[2], 1e-6);
				assertEquals(at, b[0], nb[0], 1e-6);
				assertEquals(at, b[1], nb[1], 1e-6);
				assertEquals(at, b[3], nb[3], 1e-6);
			}
		}
	}

	@Test
	public void draggingACornerScalesBothWaysAndShiftKeepsTheProportions()
	{
		DesignCanvas canvas = editor.canvas();
		ImagePlacement p = inView(canvas, 3, false);
		double s = canvas.viewScale();
		double[] b = p.bounds();
		int[] corner = view(canvas, b[2], b[3]);
		drag(canvas, corner[0], corner[1], 0, corner[0] + 10, corner[1] + 40);
		double[] nb = canvas.placement().bounds();
		assertEquals(b[0], nb[0], 1e-6);
		assertEquals(b[1], nb[1], 1e-6);
		assertEquals(b[2] + 10 / s, nb[2], 1e-6);
		assertEquals(b[3] + 40 / s, nb[3], 1e-6);

		p = inView(canvas, 3, false);
		drag(canvas, corner[0], corner[1], InputEvent.SHIFT_DOWN_MASK, corner[0] + 10, corner[1] + 40);
		nb = canvas.placement().bounds();
		assertEquals(b[0], nb[0], 1e-6);
		assertEquals(b[1], nb[1], 1e-6);
		assertEquals((b[2] - b[0]) / (b[3] - b[1]), (nb[2] - nb[0]) / (nb[3] - nb[1]), 1e-9);
		assertEquals(b[3] + 40 / s, nb[3], 1e-6);
	}

	@Test
	public void draggingInsideTheBoxStillMoves()
	{
		DesignCanvas canvas = editor.canvas();
		ImagePlacement p = inView(canvas, 2, true);
		double s = canvas.viewScale();
		int[] mid = view(canvas, p.cx, p.cy);
		drag(canvas, mid[0], mid[1], 0, mid[0] + 25, mid[1] - 5);
		assertEquals(p.cx + 25 / s, canvas.placement().cx, 1e-9);
		assertEquals(p.cy - 5 / s, canvas.placement().cy, 1e-9);
		assertEquals(p.scaleX, canvas.placement().scaleX, 1e-12);
		assertEquals(p.scaleY, canvas.placement().scaleY, 1e-12);
	}

	@Test
	public void stretchToOutlineCoversTheOutlinesBoxExactly()
	{
		click("editor:rotate");
		click("editor:stretch");
		assertArrayEquals(outline.bounds(), editor.placement().bounds(), 1e-9);
		assertEquals(1, editor.placement().turns);
		// Fill goes back to the image's own proportions
		click("editor:fill");
		assertTrue(editor.placement().uniform());
	}

	@Test
	public void previewsDuringADragAreBakedOneAtATimeOffTheDragAndNeverMoveTheImage() throws Exception
	{
		List<Runnable> jobs = new ArrayList<>();
		BakedBoardGeometry.Mesh grip = BakedBoardGeometry.sharedBoard(true)[DesignPart.GRIP.index];
		DesignLayout.Part layout = DesignLayout.bundled().of(DesignPart.GRIP);
		DesignEditorPanel slow = new DesignEditorPanel(DesignPart.GRIP, layout, grip, outline, image,
			ImagePlacement.initial(60, 120, outline.bounds()), "My grip", jobs::add, (name, placement) ->
			{
			}, () ->
			{
			}, () ->
			{
			});
		DesignCanvas canvas = slow.canvas();
		assertEquals(1, jobs.size());
		int[] path = new int[60];
		for (int i = 0; i < path.length; i += 2)
		{
			path[i] = 100 + i;
			path[i + 1] = 300 - i;
		}
		ImagePlacement start = canvas.placement();
		double s = canvas.viewScale();
		drag(canvas, 100, 300, 0, path);
		// the first preview is still being baked: the drag queued nothing more
		assertEquals(1, jobs.size());
		ImagePlacement end = canvas.placement();
		assertEquals(start.cx + 58 / s, end.cx, 1e-9);
		jobs.remove(0).run();
		flush();
		assertEquals(end, canvas.placement());
		// once it is shown, one more (of where the image is now) follows after the throttle
		long until = System.currentTimeMillis() + 2000;
		while (jobs.isEmpty() && System.currentTimeMillis() < until)
		{
			flush();
		}
		assertEquals(1, jobs.size());
		jobs.remove(0).run();
		flush();
		assertEquals(2, slow.previewsDone());
		assertEquals(end, canvas.placement());
		slow.removeNotify();
	}

	private static void press(DesignCanvas canvas, KeyStroke k)
	{
		Object name = canvas.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(k);
		assertNotNull(k.toString(), name);
		canvas.getActionMap().get(name).actionPerformed(new ActionEvent(canvas, ActionEvent.ACTION_PERFORMED, ""));
	}

	@Test
	public void nameIsCheckedBeforeSaving()
	{
		JTextField name = (JTextField) named(editor, "editor:name");
		AbstractButton save = (AbstractButton) named(editor, "editor:save");
		JLabel error = (JLabel) named(editor, "editor:nameError");
		assertTrue(save.isEnabled());
		name.setText("   ");
		assertFalse(save.isEnabled());
		assertNotEquals(" ", error.getText());
		name.setText("way too long a name for a grip");
		assertFalse(save.isEnabled());
		name.setText("Flames <3");
		assertFalse(save.isEnabled());
		name.setText("  Flames  ");
		assertTrue(save.isEnabled());
		click("editor:rotate");
		click("editor:save");
		assertEquals("Flames", savedName);
		assertEquals(editor.placement(), saved);
	}

	@Test
	public void cancelAndTemplateGoToTheListener()
	{
		click("editor:template");
		click("editor:cancel");
		assertEquals(java.util.Arrays.asList("template", "cancel"), calls);
	}

	@Test
	public void standUpTurnsTheNoseToTheTop()
	{
		BufferedImage flat = new BufferedImage(10, 4, BufferedImage.TYPE_INT_ARGB);
		// a red nose at the right
		flat.setRGB(9, 1, 0xFFFF0000);
		BufferedImage up = DesignEditorPanel.standUp(flat, true);
		assertEquals(4, up.getWidth());
		assertEquals(10, up.getHeight());
		assertEquals(0xFFFF0000, up.getRGB(1, 0));
		BufferedImage down = DesignEditorPanel.standUp(flat, false);
		assertEquals(0xFFFF0000, down.getRGB(2, 9));
	}

	@Test
	public void theCanvasDrawsTheImageWhereThePlacementPutsIt()
	{
		for (int turns = 0; turns < 4; turns++)
		{
			for (boolean flip : new boolean[]{false, true})
			{
				ImagePlacement p = new ImagePlacement(40, 25, 100, 300, 2.5, 0.8, turns, flip);
				// the canvas draws through toLayout(); the bake samples through toImage(): one is the other's inverse
				java.awt.geom.Point2D q = p.toLayout().transform(new java.awt.geom.Point2D.Double(7, 19), null);
				double[] back = p.toImage(q.getX(), q.getY());
				assertEquals(7, back[0], 1e-9);
				assertEquals(19, back[1], 1e-9);
			}
		}
	}
}
