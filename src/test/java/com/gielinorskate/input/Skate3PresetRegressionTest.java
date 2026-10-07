package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

/**
 * The Skate 3 preset is exactly the old pad: the same button presses, sent as the old profile's keys (F13 / F14 for
 * A / X, B, F, Shift, Q / E, R, Esc, H; A / D and the arrows on the left stick) through the input as it was before
 * presets ({@link LegacyInputController}), and as the universal profile's pad keys through the new input with the
 * Skate 3 preset, give the same skate and on-foot input after every step: push and sprint by mode, jump, brake, the
 * board key, grabs and their aim, Shift's hard flicks, tight carve and late brake, body flips with a lean, reset,
 * stop and the controls card.
 */
public class Skate3PresetRegressionTest
{
	private static final Component SOURCE = new Canvas();
	private static final double FULL = 1.2;

	/** The old profile's key for each button (the buttons it had no binding for are absent). */
	private static final Map<PadButton, Integer> OLD_KEYS = new EnumMap<>(PadButton.class);

	static
	{
		OLD_KEYS.put(PadButton.A, KeyEvent.VK_F13);
		OLD_KEYS.put(PadButton.X, KeyEvent.VK_F14);
		OLD_KEYS.put(PadButton.B, KeyEvent.VK_B);
		OLD_KEYS.put(PadButton.Y, KeyEvent.VK_F);
		OLD_KEYS.put(PadButton.LB, KeyEvent.VK_SHIFT);
		OLD_KEYS.put(PadButton.RB, KeyEvent.VK_SHIFT);
		OLD_KEYS.put(PadButton.LT, KeyEvent.VK_Q);
		OLD_KEYS.put(PadButton.RT, KeyEvent.VK_E);
		OLD_KEYS.put(PadButton.BACK, KeyEvent.VK_R);
		OLD_KEYS.put(PadButton.START, KeyEvent.VK_ESCAPE);
		OLD_KEYS.put(PadButton.DPAD_UP, KeyEvent.VK_H);
	}

	enum Stick
	{
		UP(KeyEvent.VK_UP, KeyEvent.VK_UP),
		DOWN(KeyEvent.VK_DOWN, KeyEvent.VK_DOWN),
		LEFT(KeyEvent.VK_A, KeyEvent.VK_LEFT),
		RIGHT(KeyEvent.VK_D, KeyEvent.VK_RIGHT);

		final int oldKey;
		final int newKey;

		Stick(int oldKey, int newKey)
		{
			this.oldKey = oldKey;
			this.newKey = newKey;
		}
	}

	private long now = 1000;
	private final LegacyInputController old = new LegacyInputController(() -> now);
	private final InputController neu = new InputController(() -> now);
	private int step;

	private void setUp(GielinorSkateConfig.TrickControls controls, boolean mirror)
	{
		old.configureController(true, KeyEvent.VK_B);
		neu.configureController(true, KeyEvent.VK_B);
		old.configureTricks(controls, GielinorSkateConfig.FlickButton.RIGHT, mirror);
		neu.configureTricks(controls, GielinorSkateConfig.FlickButton.RIGHT, mirror);
		neu.configurePreset(PadPreset.skate3());
		old.setEnabled(true);
		neu.setEnabled(true);
	}

	private static KeyEvent key(int code, boolean down)
	{
		return new KeyEvent(SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, 0, 0, code,
			KeyEvent.CHAR_UNDEFINED);
	}

	private void oldKey(int code, boolean down)
	{
		KeyEvent e = key(code, down);
		if (down)
		{
			old.keyPressed(e);
		}
		else
		{
			old.keyReleased(e);
		}
	}

	private boolean newKey(int code, boolean down)
	{
		KeyEvent e = key(code, down);
		if (down)
		{
			neu.keyPressed(e);
		}
		else
		{
			neu.keyReleased(e);
		}
		return e.isConsumed();
	}

	private void button(PadButton b, boolean down)
	{
		Integer k = OLD_KEYS.get(b);
		if (k != null)
		{
			oldKey(k, down);
		}
		newKey(b.keyCode, down);
	}

	private void stick(Stick s, boolean down)
	{
		oldKey(s.oldKey, down);
		newKey(s.newKey, down);
	}

	/** Both see the same keyboard key (W, S, Space, a trick key). */
	private void keyboard(int code, boolean down)
	{
		oldKey(code, down);
		newKey(code, down);
	}

	private void moved(int x, int y, long ms)
	{
		old.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, ms, 0, x, y, 0, false, MouseEvent.NOBUTTON));
		neu.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, ms, 0, x, y, 0, false, MouseEvent.NOBUTTON));
	}

	private StickSim rightStick(int x, int y)
	{
		return new StickSim(this::moved, x, y, now);
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

	private static String foot(FootControls f)
	{
		return "fwd=" + f.forward + " back=" + f.back + " left=" + f.left + " right=" + f.right + " sprint=" + f.sprint
			+ " jump=" + f.jumpPressed + " dropPickup=" + f.dropPickupPressed;
	}

	private static String board(BoardKey.Edges e)
	{
		return "pressed=" + e.pressed + " tapped=" + e.tapped + " held=" + e.held;
	}

	/** Drains both the same way and checks they read the same. */
	private void same(String what)
	{
		step++;
		SkateInput a = new SkateInput();
		SkateInput b = new SkateInput();
		old.drainInto(a);
		neu.drainInto(b);
		assertEquals(what + " (step " + step + ") skate", skate(a), skate(b));
		FootControls fa = new FootControls();
		FootControls fb = new FootControls();
		old.drainFoot(fa);
		neu.drainFoot(fb);
		assertEquals(what + " (step " + step + ") foot", foot(fa), foot(fb));
		BoardKey.Edges ea = new BoardKey.Edges();
		BoardKey.Edges eb = new BoardKey.Edges();
		old.pollBoardKey(ea);
		neu.pollBoardKey(eb);
		assertEquals(what + " (step " + step + ") board key", board(ea), board(eb));
		assertEquals(what + " (step " + step + ") exit", old.consumeExit(), neu.consumeExit());
		assertEquals(what + " (step " + step + ") card", old.consumeControlsToggle(), neu.consumeControlsToggle());
		assertEquals(what + " (step " + step + ") orbit", old.drainOrbitPixels(), neu.drainOrbitPixels());
	}

	private void onFoot(boolean f)
	{
		old.setOnFoot(f);
		neu.setOnFoot(f);
	}

	private void rolling(boolean r)
	{
		old.setRolling(r);
		neu.setRolling(r);
	}

	@Test
	public void pushAndSprintByMode()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		button(PadButton.A, true);
		same("A pushes");
		now += 300;
		same("A held keeps pushing");
		button(PadButton.X, true);
		button(PadButton.A, false);
		same("X takes over the push");
		button(PadButton.X, false);
		same("released");
		onFoot(true);
		button(PadButton.A, true);
		same("A sprints on foot");
		onFoot(false);
		same("A held across a mount pushes");
		button(PadButton.A, false);
		button(PadButton.LB, true);
		onFoot(true);
		same("LB sprints on foot, as Shift did");
		button(PadButton.LB, false);
		same("LB up");
	}

	@Test
	public void jumpDropAndPickUpOnFoot()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		onFoot(true);
		button(PadButton.X, true);
		same("X jumps");
		button(PadButton.X, false);
		button(PadButton.LT, true);
		same("LT drops or picks up");
		button(PadButton.LT, false);
		button(PadButton.RT, true);
		same("RT drops or picks up");
		button(PadButton.RT, false);
		same("released");
		button(PadButton.X, true);
		onFoot(false);
		onFoot(true);
		same("X held across a mount is no jump");
	}

	@Test
	public void brakeTightCarveAndLateShiftBrake()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		button(PadButton.B, true);
		same("B brakes at once");
		stick(Stick.LEFT, true);
		same("B with steer carves");
		stick(Stick.LEFT, false);
		button(PadButton.A, true);
		same("B with push neither brakes nor flips");
		button(PadButton.A, false);
		button(PadButton.B, false);
		button(PadButton.LB, true);
		same("LB alone does not brake yet");
		now += 200;
		same("still not");
		now += 60;
		same("brakes after a quarter second");
		stick(Stick.RIGHT, true);
		same("LB with steer: tight carve");
		stick(Stick.RIGHT, false);
		now += 100;
		same("alone again: waits");
		now += 300;
		same("brakes again");
		button(PadButton.LB, false);
		button(PadButton.RB, true);
		now += 300;
		same("RB alone brakes too");
		button(PadButton.RB, false);
		same("released");
	}

	@Test
	public void hardFlicksAndBodyFlipsWithLBOrRB()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		for (PadButton mod : new PadButton[]{PadButton.LB, PadButton.RB})
		{
			button(mod, true);
			StickSim s = rightStick(400, 300).tilt(0, FULL, 80).tilt(-FULL, -FULL, 80).rest(100);
			now = s.ms;
			same(mod + " held: the stick flick is the hard version");
			stick(Stick.UP, true);
			same(mod + " and a lean up: front flip input");
			stick(Stick.UP, false);
			stick(Stick.DOWN, true);
			same(mod + " and a lean down: back flip input");
			stick(Stick.DOWN, false);
			button(mod, false);
			same("released");
			now += 500;
		}
	}

	@Test
	public void grabsAimWithTheStickAndFlipWithALean()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		for (PadButton grab : new PadButton[]{PadButton.LT, PadButton.RT})
		{
			button(grab, true);
			StickSim s = rightStick(400, 300).tilt(0, -FULL, 60).rest(100);
			now = s.ms;
			same(grab + " aimed up, no flick");
			stick(Stick.UP, true);
			same(grab + " with a lean");
			stick(Stick.UP, false);
			button(grab, false);
			same("released");
			now += 500;
		}
		rolling(true);
		button(PadButton.LT, true);
		now += 400;
		StickSim s = rightStick(400, 300).tilt(0, FULL, 80).tilt(0, -FULL, 80).rest(100);
		now = s.ms;
		same("rolling grab past the aim window: the stick pops out of it");
		button(PadButton.LT, false);
		same("released");
	}

	@Test
	public void boardKeyResetStopAndControlsCard()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		button(PadButton.Y, true);
		same("Y down");
		now += 100;
		button(PadButton.Y, false);
		same("Y tapped");
		button(PadButton.Y, true);
		now += 2000;
		same("Y held");
		button(PadButton.Y, false);
		same("Y up");
		button(PadButton.BACK, true);
		same("Back resets");
		button(PadButton.BACK, false);
		button(PadButton.START, true);
		same("Start stops");
		button(PadButton.START, false);
		button(PadButton.DPAD_UP, true);
		same("d-pad up toggles the card");
		button(PadButton.DPAD_UP, false);
		button(PadButton.DPAD_UP, true);
		same("again");
		button(PadButton.DPAD_UP, false);
		onFoot(true);
		button(PadButton.BACK, true);
		button(PadButton.Y, true);
		same("on foot: reset and the board key");
	}

	@Test
	public void theUnusedButtonsDoNothingButAreNotTyped()
	{
		setUp(GielinorSkateConfig.TrickControls.MOUSE, false);
		for (PadButton b : EnumSet.of(PadButton.L3, PadButton.R3, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT,
			PadButton.DPAD_RIGHT))
		{
			assertEquals(b.label, true, newKey(b.keyCode, true));
			same(b + " down");
			newKey(b.keyCode, false);
			same(b + " up");
		}
	}

	@Test
	public void keyboardTricksWithLBAreTheHardVersion()
	{
		setUp(GielinorSkateConfig.TrickControls.BOTH, false);
		button(PadButton.LB, true);
		keyboard(KeyEvent.VK_1, true);
		same("trick key held with LB");
		keyboard(KeyEvent.VK_1, false);
		same("fires hard");
		button(PadButton.LB, false);
		keyboard(KeyEvent.VK_SPACE, true);
		same("Space charges");
		keyboard(KeyEvent.VK_SPACE, false);
		same("ollie");
	}

	@Test
	public void randomPlayIsIdenticalForEveryTrickSetting()
	{
		for (GielinorSkateConfig.TrickControls controls : GielinorSkateConfig.TrickControls.values())
		{
			for (boolean mirror : new boolean[]{false, true})
			{
				for (long seed = 1; seed <= 6; seed++)
				{
					fuzz(controls, mirror, seed);
				}
			}
		}
	}

	/** Random presses of the old profile's buttons, stick moves, keys, time, mounts and drains. */
	private void fuzz(GielinorSkateConfig.TrickControls controls, boolean mirror, long seed)
	{
		new Fuzz(controls, mirror, seed).run(new Random(seed * 31 + controls.ordinal() * 7 + (mirror ? 1 : 0)));
	}

	/** One random session on a fresh pair of inputs. */
	private final class Fuzz
	{
		private final Skate3PresetRegressionTest t = new Skate3PresetRegressionTest();
		private final String name;
		private final Set<PadButton> held = EnumSet.noneOf(PadButton.class);
		private final List<PadButton> buttons = new ArrayList<>(OLD_KEYS.keySet());
		private Stick stickHeld;
		private final List<Integer> keysHeld = new ArrayList<>();
		private int x = 400;
		private int y = 300;

		Fuzz(GielinorSkateConfig.TrickControls controls, boolean mirror, long seed)
		{
			name = controls + (mirror ? " mirrored" : "") + " seed " + seed;
			t.setUp(controls, mirror);
		}

		void run(Random r)
		{
			for (int i = 0; i < 1500; i++)
			{
				int op = r.nextInt(100);
				String what = name + " op " + i;
				if (op < 30)
				{
					PadButton b = buttons.get(r.nextInt(buttons.size()));
					// LB and RB both sent Shift: the old profile could not hold one and let go of the other
					boolean shiftClash = (b == PadButton.LB && held.contains(PadButton.RB))
						|| (b == PadButton.RB && held.contains(PadButton.LB));
					if (held.contains(b))
					{
						held.remove(b);
						t.button(b, false);
					}
					else if (!shiftClash)
					{
						held.add(b);
						t.button(b, true);
					}
				}
				else if (op < 42)
				{
					// four-way left stick: one direction at a time
					if (stickHeld != null)
					{
						t.stick(stickHeld, false);
						stickHeld = null;
					}
					else
					{
						stickHeld = Stick.values()[r.nextInt(4)];
						t.stick(stickHeld, true);
					}
				}
				else if (op < 60)
				{
					double vx = (r.nextInt(5) - 2) * FULL / 2;
					double vy = (r.nextInt(5) - 2) * FULL / 2;
					StickSim s = new StickSim(t::moved, x, y, t.now).tilt(vx, vy, 20 + r.nextInt(120));
					t.now = s.ms;
					x = clamp((int) Math.round(x + vx * 60));
					y = clamp((int) Math.round(y + vy * 60));
					t.moved(x, y, t.now);
				}
				else if (op < 66)
				{
					int[] keys = {KeyEvent.VK_W, KeyEvent.VK_S, KeyEvent.VK_SPACE, KeyEvent.VK_1, KeyEvent.VK_C};
					int k = keys[r.nextInt(keys.length)];
					boolean down = !keysHeld.contains(k);
					if (down)
					{
						keysHeld.add(k);
					}
					else
					{
						keysHeld.remove((Integer) k);
					}
					t.keyboard(k, down);
				}
				else if (op < 70)
				{
					t.onFoot(r.nextInt(3) == 0);
				}
				else if (op < 74)
				{
					t.rolling(r.nextBoolean());
				}
				else if (op < 88)
				{
					t.now += r.nextInt(400);
				}
				else
				{
					t.same(what);
				}
			}
			t.same(name + " end");
		}

		private int clamp(int v)
		{
			return Math.max(50, Math.min(750, v));
		}
	}
}
