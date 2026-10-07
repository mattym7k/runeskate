package com.gielinorskate.design;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.WindowConstants;

/** The design editor's window: not modal, owned by the client's frame, sized to fit the screen. EDT only. */
public final class DesignEditorDialog extends JDialog
{
	private final DesignEditorPanel panel;

	/**
	 * @param onClose runs when the window is closed with its close button (as Cancel)
	 */
	public DesignEditorDialog(Window owner, String title, DesignEditorPanel panel, Runnable onClose)
	{
		super(owner, title, ModalityType.MODELESS);
		this.panel = panel;
		setContentPane(panel);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				onClose.run();
			}

			@Override
			public void windowOpened(WindowEvent e)
			{
				panel.focusCanvas();
			}
		});
		fitScreen(owner);
	}

	private void fitScreen(Window owner)
	{
		GraphicsConfiguration gc = owner != null ? owner.getGraphicsConfiguration() : getGraphicsConfiguration();
		Rectangle screen = gc.getBounds();
		Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
		int maxW = screen.width - in.left - in.right - 40;
		int maxH = screen.height - in.top - in.bottom - 40;
		pack();
		Dimension want = getPreferredSize();
		int h = Math.min(maxH, Math.max(want.height, 860));
		int w = Math.min(maxW, Math.max(want.width, 620));
		setSize(w, h);
		setMinimumSize(new Dimension(Math.min(w, 480), Math.min(h, 480)));
		setLocationRelativeTo(owner);
	}

	/** Closes the window and stops its preview. */
	public void close()
	{
		panel.dispose();
		dispose();
	}
}
