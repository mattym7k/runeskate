package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * Keyboard and mouse play is unchanged by controller presets: the same random keys, flicks, clicks and wheel through
 * the input as it was ({@link LegacyInputController}) and as it is read the same and consume the same events, with
 * Controller mode off (where the pad keys are ordinary keys) and on (keyboard keys only).
 */
public class KeyboardMouseRegressionTest
{
	private static final Component SOURCE = new Canvas();
	private static final int[] KEYS = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_UP,
		KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_SHIFT, KeyEvent.VK_SPACE, KeyEvent.VK_Q,
		KeyEvent.VK_E, KeyEvent.VK_R, KeyEvent.VK_H, KeyEvent.VK_ESCAPE, KeyEvent.VK_F, KeyEvent.VK_B, KeyEvent.VK_C,
		KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_7, KeyEvent.VK_J};

	private long now = 1000;
	private final LegacyInputController old = new LegacyInputController(() -> now);
	private final InputController neu = new InputController(() -> now);

	private void key(int code, char ch, boolean down, boolean alt)
	{
		int mods = alt ? KeyEvent.ALT_DOWN_MASK : 0;
		KeyEvent a = new KeyEvent(SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, now, mods, code, ch);
		KeyEvent b = new KeyEvent(SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, now, mods, code, ch);
		if (down)
		{
			old.keyPressed(a);
			neu.keyPressed(b);
		}
		else
		{
			old.keyReleased(a);
			neu.keyReleased(b);
		}
		assertEquals("consumed " + KeyEvent.getKeyText(code), a.isConsumed(), b.isConsumed());
	}

	private void mouse(int id, int x, int y, int button)
	{
		MouseEvent a = new MouseEvent(SOURCE, id, now, 0, x, y, 1, false, button);
		MouseEvent b = new MouseEvent(SOURCE, id, now, 0, x, y, 1, false, button);
		switch (id)
		{
			case MouseEvent.MOUSE_PRESSED:
				old.mousePressed(a);
				neu.mousePressed(b);
				break;
			case MouseEvent.MOUSE_RELEASED:
				old.mouseReleased(a);
				neu.mouseReleased(b);
				break;
			case MouseEvent.MOUSE_DRAGGED:
				old.mouseDragged(a);
				neu.mouseDragged(b);
				break;
			case MouseEvent.MOUSE_CLICKED:
				old.mouseClicked(a);
				neu.mouseClicked(b);
				break;
			default:
				old.mouseMoved(a);
				neu.mouseMoved(b);
				break;
		}
		assertEquals("consumed mouse " + id, a.isConsumed(), b.isConsumed());
	}

	private void wheel(int notches)
	{
		MouseWheelEvent a = new MouseWheelEvent(SOURCE, MouseEvent.MOUSE_WHEEL, now, 0, 10, 10, 0, false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, notches);
		MouseWheelEvent b = new MouseWheelEvent(SOURCE, MouseEvent.MOUSE_WHEEL, now, 0, 10, 10, 0, false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, notches);
		old.mouseWheelMoved(a);
		neu.mouseWheelMoved(b);
		assertEquals(a.isConsumed(), b.isConsumed());
	}

	private static String skate(SkateInput in)
	{
		StringBuilder g = new StringBuilder();
		for (Gesture x : in.gestures)
		{
			g.append(x.direction).append(x.nollie ? "n" : "").append(x.modified ? "m" : "").append('@')
				.append(Math.round(x.turnDegrees)).append(' ');
		}
		return "steer=" + in.steer + " crouch=" + in.crouch + " charge=" + in.charge + " slide=" + in.powerslide
			+ " brakeBlocked=" + in.brakeBlocked + " leanBack=" + in.leanBack + " pushPressed=" + in.pushPressed
			+ " pushHeld=" + in.pushHeld + " reset=" + in.resetRequested + " manual=" + in.manualHeld
			+ " nose=" + in.noseManualHeld + " grabL=" + in.grabLeft + " grabR=" + in.grabRight + " aim=" + in.grabAim
			+ " leanF=" + in.leanForwardKey + " leanB=" + in.leanBackKey + " pad=" + in.controllerMode
			+ " gestures=[" + g + "]";
	}

	private void same(String what)
	{
		SkateInput a = new SkateInput();
		SkateInput b = new SkateInput();
		old.drainInto(a);
		neu.drainInto(b);
		assertEquals(what, skate(a), skate(b));
		FootControls fa = new FootControls();
		FootControls fb = new FootControls();
		old.drainFoot(fa);
		neu.drainFoot(fb);
		assertEquals(what, fa.forward + " " + fa.back + " " + fa.left + " " + fa.right + " " + fa.sprint + " "
			+ fa.jumpPressed + " " + fa.dropPickupPressed, fb.forward + " " + fb.back + " " + fb.left + " " + fb.right
			+ " " + fb.sprint + " " + fb.jumpPressed + " " + fb.dropPickupPressed);
		BoardKey.Edges ea = new BoardKey.Edges();
		BoardKey.Edges eb = new BoardKey.Edges();
		old.pollBoardKey(ea);
		neu.pollBoardKey(eb);
		assertEquals(what, ea.pressed + " " + ea.tapped + " " + ea.held, eb.pressed + " " + eb.tapped + " " + eb.held);
		assertEquals(what, old.consumeExit(), neu.consumeExit());
		assertEquals(what, old.consumeControlsToggle(), neu.consumeControlsToggle());
		assertEquals(what, old.consumePlainClick(), neu.consumePlainClick());
		assertEquals(what, old.drainOrbitPixels(), neu.drainOrbitPixels());
		assertEquals(what, old.drainZoomNotches(), neu.drainZoomNotches());
	}

	@Test
	public void keyboardAndMousePlayIsUnchanged()
	{
		for (boolean controller : new boolean[]{false, true})
		{
			for (GielinorSkateConfig.TrickControls controls : GielinorSkateConfig.TrickControls.values())
			{
				for (GielinorSkateConfig.FlickButton button : GielinorSkateConfig.FlickButton.values())
				{
					for (long seed = 1; seed <= 3; seed++)
					{
						play(controller, controls, button, seed);
					}
				}
			}
		}
	}

	private void play(boolean controller, GielinorSkateConfig.TrickControls controls,
		GielinorSkateConfig.FlickButton button, long seed)
	{
		KeyboardMouseRegressionTest t = new KeyboardMouseRegressionTest();
		t.run(controller, controls, button, new Random(seed * 131 + controls.ordinal() * 17 + button.ordinal() * 3
			+ (controller ? 1 : 0)), (controller ? "pad " : "keys ") + controls + " " + button + " " + seed);
	}

	private void run(boolean controller, GielinorSkateConfig.TrickControls controls,
		GielinorSkateConfig.FlickButton button, Random r, String name)
	{
		for (Object c : new Object[]{old, neu})
		{
			if (c instanceof LegacyInputController)
			{
				LegacyInputController l = (LegacyInputController) c;
				l.configureController(controller, KeyEvent.VK_B);
				l.configureTricks(controls, button, false);
				l.setEnabled(true);
			}
			else
			{
				InputController n = (InputController) c;
				n.configureController(controller, KeyEvent.VK_B);
				n.configureTricks(controls, button, false);
				n.setEnabled(true);
			}
		}
		List<Integer> held = new ArrayList<>();
		int[] buttons = {MouseEvent.BUTTON1, MouseEvent.BUTTON2, MouseEvent.BUTTON3};
		int mouseDown = 0;
		int x = 400;
		int y = 300;
		for (int i = 0; i < 1500; i++)
		{
			int op = r.nextInt(100);
			String what = name + " op " + i;
			if (op < 30)
			{
				int k = KEYS[r.nextInt(KEYS.length)];
				boolean down = !held.contains(k) || r.nextInt(8) == 0;
				if (down && !held.contains(k))
				{
					held.add(k);
				}
				else if (!down)
				{
					held.remove((Integer) k);
				}
				// a W / S that Key Remapping turned into an arrow keeps its letter
				char ch = (k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN) && r.nextInt(3) == 0 ? 'w'
					: KeyEvent.CHAR_UNDEFINED;
				key(k, ch, down, r.nextInt(6) == 0);
			}
			else if (op < 34 && !controller)
			{
				// outside Controller mode the pad keys are ordinary keys
				PadButton p = PadButton.values()[r.nextInt(PadButton.values().length)];
				key(p.keyCode, KeyEvent.CHAR_UNDEFINED, r.nextBoolean(), false);
			}
			else if (op < 44)
			{
				if (mouseDown == 0)
				{
					mouseDown = buttons[r.nextInt(3)];
					mouse(MouseEvent.MOUSE_PRESSED, x, y, mouseDown);
				}
				else
				{
					mouse(MouseEvent.MOUSE_RELEASED, x, y, mouseDown);
					mouse(MouseEvent.MOUSE_CLICKED, x, y, mouseDown);
					mouseDown = 0;
				}
			}
			else if (op < 62)
			{
				int steps = 1 + r.nextInt(8);
				int dx = r.nextInt(81) - 40;
				int dy = r.nextInt(81) - 40;
				for (int s = 0; s < steps; s++)
				{
					now += 5 + r.nextInt(15);
					x = Math.max(0, Math.min(800, x + dx));
					y = Math.max(0, Math.min(600, y + dy));
					mouse(mouseDown != 0 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED, x, y,
						mouseDown != 0 ? mouseDown : MouseEvent.NOBUTTON);
				}
			}
			else if (op < 65)
			{
				wheel(r.nextInt(5) - 2);
			}
			else if (op < 69)
			{
				boolean foot = r.nextInt(3) == 0;
				old.setOnFoot(foot);
				neu.setOnFoot(foot);
			}
			else if (op < 72)
			{
				boolean rolling = r.nextBoolean();
				old.setRolling(rolling);
				neu.setRolling(rolling);
			}
			else if (op < 74)
			{
				old.focusLost();
				neu.focusLost();
				held.clear();
			}
			else if (op < 86)
			{
				now += r.nextInt(400);
			}
			else
			{
				same(what);
			}
		}
		same(name + " end");
	}
}
