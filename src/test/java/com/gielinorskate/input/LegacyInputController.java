package com.gielinorskate.input;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * Test fixture: InputController exactly as it was before controller presets (commit 58c95cd), frozen so the
 * regression tests can drive the old pad profile's keys through it and compare with the new pad keys and the
 * Skate 3 preset. Never change its behaviour.
 */
final class LegacyInputController extends MouseAdapter implements KeyListener, MouseWheelListener
{
	private final GestureRecognizer gesture = new GestureRecognizer();
	/** Controller mode's right stick: button-free mouse motion read as flicks and manuals. */
	private final ControllerStick stick = new ControllerStick(gesture);
	/** Same clock as MouseEvent.getWhen(), so held-still time is measured against drag times. */
	private final LongSupplier clock;
	/** Null in tests. */
	private final GielinorSkateConfig config;
	private final AtomicBoolean push = new AtomicBoolean();
	private final AtomicBoolean reset = new AtomicBoolean();
	private final AtomicBoolean exit = new AtomicBoolean();
	private final AtomicBoolean controlsToggle = new AtomicBoolean();
	/** H is down: the OS's key repeat while it is held does not toggle the card again. */
	private volatile boolean controlsKeyDown;
	/** A plain left click on the world while skating (it is swallowed): the session explains why, once. */
	private final AtomicBoolean plainClick = new AtomicBoolean();
	/** Mouse wheel notches since the last drain; positive = scrolled towards the user (zoom out). */
	private final AtomicInteger zoomNotches = new AtomicInteger();
	/** Sideways pixels of middle-button drag on foot, for orbiting the camera; see {@link #drainOrbitPixels}. */
	private final AtomicInteger orbitPixels = new AtomicInteger();
	/** A middle-button drag on foot is orbiting the camera; its last x. */
	private volatile boolean orbiting;
	private volatile int orbitLastX;

	private volatile boolean enabled;
	private volatile boolean left;
	private volatile boolean right;
	private volatile boolean crouchKey;
	private volatile boolean shift;
	private volatile boolean pushHeld;
	private volatile boolean grabLeftKey;
	private volatile boolean grabRightKey;
	private volatile boolean manualKey;
	/** The lean keys (for grab-flips) and their key codes; Up / Down by default, which still push and crouch too. */
	private volatile boolean leanForward;
	private volatile boolean leanBack;
	private volatile int leanForwardCode = KeyEvent.VK_UP;
	private volatile int leanBackCode = KeyEvent.VK_DOWN;
	/**
	 * Controller mode (AntiMicroX): mouse motion with no button is the right stick, and the real Up / Down arrows
	 * (the left stick) only lean.
	 */
	private volatile boolean controllerMode;
	/** The brake key (B by default; VK_UNDEFINED when unset): a powerslide brake, like Shift alone. */
	private volatile int brakeCode = KeyEvent.VK_B;
	private volatile boolean brakeKey;
	/** The latest mouse position seen while skating; mouseKnown is false until the first one. */
	private volatile int mouseX;
	private volatile int mouseY;
	private volatile boolean mouseKnown;
	/**
	 * Directional grabs: the mouse position when the grab key went down (aimOriginPending while it is not known
	 * yet: the next move sets it), and the aim of the move since (null until it is far enough). The first aim of
	 * a press sticks.
	 */
	private volatile int aimOriginX;
	private volatile int aimOriginY;
	private volatile boolean aimOriginPending;
	private volatile Grabs.Aim grabAim;
	/**
	 * The skater rolls on the ground (set each frame): a grab key held there is a rolling grab, and once its aim window
	 * has passed the right stick flicks again, so a controller can pop out of it.
	 */
	private volatile boolean rolling;
	/** A grab is aimed only this long after its key went down (Grabs.AIM_SECONDS), so a late move never re-aims it. */
	static final long AIM_MS = Math.round(Grabs.AIM_SECONDS * 1000);
	/** Clock time the grab key went down, for the aim window. */
	private volatile long aimPressMs;
	/**
	 * Shift must be held alone (no W, no steer, no flick stroke) this long before it brakes, so Shift used as
	 * the trick modifier never scrubs speed (polish review: a 0.4 s Shift wind-up cost a hardflip 1223 -> 820).
	 */
	static final long BRAKE_DELAY_MS = 250;
	/** Clock time since which Shift has been held alone (reset whenever it is not alone). */
	private volatile long shiftAloneSince;
	/**
	 * Canvas areas (the sidebar and the chatbox) where mouse presses and clicks pass through to the game while
	 * skating; world clicks stay blocked. Refreshed from the client thread each frame.
	 */
	private volatile List<Rectangle> clickThroughAreas = Collections.emptyList();
	/** Key code held for a manual (Space by default). */
	private volatile int manualKeyCode = KeyEvent.VK_SPACE;
	/** The manual key as configured (before the fallback for a clash), so a trick-mode change can re-check it. */
	private volatile int configuredManualKey = KeyEvent.VK_SPACE;
	/** Space and the number keys do tricks ("Keyboard" or "Both" trick controls). */
	private volatile boolean keyboardTricks;
	/** Mouse flicks do tricks ("Mouse flicks" or "Both"). */
	private volatile boolean mouseTricks = true;
	private volatile GielinorSkateConfig.FlickButton flickButton = GielinorSkateConfig.FlickButton.RIGHT;
	/** Mouse flicks are mirrored left to right before they reach physics. */
	private volatile boolean mirrorFlicks;
	/** The trick key held down (charging its pop), or VK_UNDEFINED; it fires when released. */
	private volatile int trickKeyHeld = KeyEvent.VK_UNDEFINED;
	/** Alt was down when the held trick key went down: the nollie version. */
	private volatile boolean trickKeyNollie;
	/** The latest keyboard trick, for the live flick visualizer's sector flash; null before any. */
	private volatile LiveStroke keyFlash;
	/** Keyboard tricks fired (key released) since the last drain. */
	private final Queue<Gesture> keyGestures = new ConcurrentLinkedQueue<>();
	/** The board on/off key (F by default; VK_UNDEFINED when unset): tap and hold detection. */
	private final BoardKey boardKey = new BoardKey();
	/** The board key in use ({@link #effectiveBoardKey}) and as configured, so a trick-mode change re-checks it. */
	private volatile int boardKeyCode = KeyEvent.VK_F;
	private volatile int configuredBoardKey = KeyEvent.VK_F;
	/**
	 * On foot (off the board): Space jumps instead of doing a trick or a manual, and Q / E drop or pick up the
	 * board. Every other key is read as on the board and turned into walking by {@link #drainFoot}.
	 */
	private volatile boolean onFoot;
	private volatile boolean spaceDown;
	private final AtomicBoolean jump = new AtomicBoolean();
	private final AtomicBoolean dropPickup = new AtomicBoolean();
	/**
	 * The pad A and pad X keys in use (the controller profile's A and X buttons, one key each; VK_UNDEFINED when
	 * controller mode is off, or the key is unset or used by another control, see {@link #refreshPadKeys}): on the
	 * board both push, tap or hold; on foot A is a held sprint and X a jump. Their held state is tracked in both
	 * modes, so a button held across a mount or dismount is not a new press.
	 */
	private volatile int padACode = KeyEvent.VK_UNDEFINED;
	private volatile int padXCode = KeyEvent.VK_UNDEFINED;
	/** The pad keys as configured, re-checked whenever another key setting or controller mode changes. */
	private volatile int configuredPadA = KeyEvent.VK_F13;
	private volatile int configuredPadX = KeyEvent.VK_F14;
	/** The skate mode (toggle) key when it has no modifiers, else VK_UNDEFINED: a pad key on it would toggle. */
	private volatile int plainToggleCode = KeyEvent.VK_UNDEFINED;
	private volatile boolean padA;
	private volatile boolean padX;

	LegacyInputController(LongSupplier clock)
	{
		this(clock, null);
	}

	private LegacyInputController(LongSupplier clock, GielinorSkateConfig config)
	{
		this.clock = clock;
		this.config = config;
	}

	/**
	 * True when {@code code} can be the manual key: a real, non-modifier key that no skate control uses
	 * (W/A/S/D and their arrow aliases, H, R, Q, E, Esc, Shift).
	 */
	public static boolean isValidManualKey(int code)
	{
		switch (code)
		{
			case KeyEvent.VK_UNDEFINED:
			case KeyEvent.VK_SHIFT:
			case KeyEvent.VK_CONTROL:
			case KeyEvent.VK_ALT:
			case KeyEvent.VK_ALT_GRAPH:
			case KeyEvent.VK_META:
			case KeyEvent.VK_WINDOWS:
			case KeyEvent.VK_W:
			case KeyEvent.VK_A:
			case KeyEvent.VK_S:
			case KeyEvent.VK_D:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_H:
			case KeyEvent.VK_R:
			case KeyEvent.VK_Q:
			case KeyEvent.VK_E:
			case KeyEvent.VK_ESCAPE:
				return false;
			default:
				return true;
		}
	}

	/** The configured manual key when {@link #isValidManualKey valid}, else Space. */
	public static int effectiveManualKey(int code)
	{
		return isValidManualKey(code) ? code : KeyEvent.VK_SPACE;
	}

	/**
	 * Like {@link #isValidManualKey(int)}; with keyboard tricks on, Space and the number keys are tricks, so they
	 * cannot be the manual key either.
	 */
	public static boolean isValidManualKey(int code, boolean keyboardTricks)
	{
		return isValidManualKey(code) && !(keyboardTricks && KeyboardTricks.isTrickKey(code));
	}

	/**
	 * The manual key in use: the configured one when valid, else Space, or C with keyboard tricks on (Space is the
	 * ollie then).
	 */
	public static int effectiveManualKey(int code, boolean keyboardTricks)
	{
		if (isValidManualKey(code, keyboardTricks))
		{
			return code;
		}
		return keyboardTricks ? KeyEvent.VK_C : KeyEvent.VK_SPACE;
	}

	/**
	 * Sets the manual key code (Space when it is not {@link #isValidManualKey valid}) and the flick sensitivity
	 * percent (read from the config on enable).
	 */
	void configure(int manualKeyCode, int flickSensitivity)
	{
		configuredManualKey = manualKeyCode;
		this.manualKeyCode = effectiveManualKey(manualKeyCode, keyboardTricks);
		gesture.setSensitivity(flickSensitivity);
		refreshPadKeys();
	}

	/** Sets the trick controls, the flick button and flick mirroring (read from the config on enable). */
	void configureTricks(GielinorSkateConfig.TrickControls controls, GielinorSkateConfig.FlickButton button,
		boolean mirror)
	{
		keyboardTricks = controls.keyboard();
		mouseTricks = controls.mouse();
		flickButton = button;
		mirrorFlicks = mirror;
		manualKeyCode = effectiveManualKey(configuredManualKey, keyboardTricks);
		setBoardKeyCode(effectiveBoardKey(configuredBoardKey, keyboardTricks));
		stick.setFlicks(mouseTricks);
		refreshPadKeys();
	}

	/** Re-reads the manual key and flick sensitivity from the config (a setting changed while skating). */
	public void reconfigure()
	{
		if (config != null)
		{
			applyConfig();
		}
	}

	/** Sets the lean forward / lean back key codes (read from the config on enable). */
	void configureLeanKeys(int forward, int back)
	{
		leanForwardCode = forward;
		leanBackCode = back;
		refreshPadKeys();
	}

	/**
	 * Sets controller mode and the brake key (read from the config on enable). A brake key on a skate key (W, Q,
	 * Esc, ...) is ignored.
	 */
	void configureController(boolean on, int brake)
	{
		controllerMode = on;
		brakeCode = isValidManualKey(brake) ? brake : KeyEvent.VK_UNDEFINED;
		stick.reset();
		refreshPadKeys();
	}

	/**
	 * The pad A / pad X key in use: the configured key, or VK_UNDEFINED (off) when it is a hard-coded skate control
	 * (W, Shift, Esc, Space, ...), no real key, a trick key with keyboard tricks on, or one of {@code taken} (the
	 * other key settings in use), so it never changes what another control's key does.
	 */
	public static int effectivePadKey(int code, boolean keyboardTricks, int... taken)
	{
		if (!isValidManualKey(code) || code == KeyEvent.VK_SPACE || (keyboardTricks && KeyboardTricks.isTrickKey(code)))
		{
			return KeyEvent.VK_UNDEFINED;
		}
		for (int t : taken)
		{
			if (t == code)
			{
				return KeyEvent.VK_UNDEFINED;
			}
		}
		return code;
	}

	/** Sets the pad A and pad X key codes (read from the config on enable); see {@link #refreshPadKeys}. */
	void configurePadKeys(int a, int x)
	{
		configuredPadA = a;
		configuredPadX = x;
		refreshPadKeys();
	}

	/** Sets the skate mode (toggle) key, so a pad key never shares it; one with modifiers never clashes. */
	void configureToggleKey(int code, int modifiers)
	{
		plainToggleCode = modifiers == 0 ? code : KeyEvent.VK_UNDEFINED;
		refreshPadKeys();
	}

	/**
	 * The pad keys in use: only in controller mode (a keyboard player's keys are never pad keys), and each off
	 * when another control uses its key ({@link #effectivePadKey}); pad X is off on the same key as pad A.
	 */
	private void refreshPadKeys()
	{
		int a = KeyEvent.VK_UNDEFINED;
		int x = KeyEvent.VK_UNDEFINED;
		if (controllerMode)
		{
			a = effectivePadKey(configuredPadA, keyboardTricks, manualKeyCode, brakeCode, leanForwardCode,
				leanBackCode, boardKeyCode, plainToggleCode);
			x = effectivePadKey(configuredPadX, keyboardTricks, manualKeyCode, brakeCode, leanForwardCode,
				leanBackCode, boardKeyCode, plainToggleCode, configuredPadA);
		}
		// a new code drops the old key's held state: its release would no longer match, leaving it stuck down
		if (a != padACode)
		{
			padA = false;
		}
		if (x != padXCode)
		{
			padX = false;
		}
		padACode = a;
		padXCode = x;
	}

	/**
	 * True when {@code code} can be the board on/off key: a key the {@link #isValidManualKey manual key} could be,
	 * but never Space (the jump on foot) and, with keyboard tricks on, never a trick key.
	 */
	public static boolean isValidBoardKey(int code, boolean keyboardTricks)
	{
		return isValidManualKey(code) && code != KeyEvent.VK_SPACE
			&& !(keyboardTricks && KeyboardTricks.isTrickKey(code));
	}

	/**
	 * The board key in use: the configured one when {@link #isValidBoardKey valid}, VK_UNDEFINED (off) when unset,
	 * else F, so it never takes over Esc, WASD, Shift, Space, Q / E, H, R or a trick key.
	 */
	public static int effectiveBoardKey(int code, boolean keyboardTricks)
	{
		if (code == KeyEvent.VK_UNDEFINED)
		{
			return KeyEvent.VK_UNDEFINED;
		}
		return isValidBoardKey(code, keyboardTricks) ? code : KeyEvent.VK_F;
	}

	/** Sets the board on/off key code (read from the config on enable); see {@link #effectiveBoardKey}. */
	void configureBoardKey(int code)
	{
		configuredBoardKey = code;
		setBoardKeyCode(effectiveBoardKey(code, keyboardTricks));
		refreshPadKeys();
	}

	/** A new board key code drops the old key's held state: its release would no longer match, leaving it stuck. */
	private void setBoardKeyCode(int code)
	{
		if (code != boardKeyCode)
		{
			boardKey.reset();
		}
		boardKeyCode = code;
	}

	/**
	 * Off the board (true) or on it. Switching drops the edges and held trick / manual keys of the other mode, so
	 * nothing pressed on foot fires on the board or the other way round.
	 */
	public void setOnFoot(boolean onFoot)
	{
		this.onFoot = onFoot;
		rolling = false;
		orbiting = false;
		orbitPixels.set(0);
		jump.set(false);
		dropPickup.set(false);
		manualKey = false;
		trickKeyHeld = KeyEvent.VK_UNDEFINED;
		keyGestures.clear();
	}

	private void applyConfig()
	{
		configureBoardKey(config.boardKey().getKeyCode());
		configureToggleKey(config.toggleKey().getKeyCode(), config.toggleKey().getModifiers());
		configurePadKeys(KeyEvent.VK_F13, KeyEvent.VK_F14);
		configureController(config.controllerMode(), config.brakeKey().getKeyCode());
		configureLeanKeys(config.leanForwardKey().getKeyCode(), config.leanBackKey().getKeyCode());
		configureTricks(config.trickControls(), config.flickButton(), config.mirrorFlicks());
		configure(config.manualKey().getKeyCode(), config.flickSensitivity());
	}

	public void setEnabled(boolean enabled)
	{
		if (enabled && config != null)
		{
			// the manual key is matched by key code alone, so it still works with Shift (slide) held
			applyConfig();
		}
		this.enabled = enabled;
		left = right = crouchKey = shift = pushHeld = grabLeftKey = grabRightKey = manualKey = false;
		leanForward = leanBack = mouseKnown = brakeKey = false;
		grabAim = null;
		stick.reset();
		stick.setFlicks(mouseTricks);
		push.set(false);
		trickKeyHeld = KeyEvent.VK_UNDEFINED;
		keyGestures.clear();
		reset.set(false);
		exit.set(false);
		controlsToggle.set(false);
		controlsKeyDown = false;
		plainClick.set(false);
		zoomNotches.set(0);
		orbitPixels.set(0);
		orbiting = false;
		onFoot = false;
		spaceDown = false;
		padA = padX = false;
		jump.set(false);
		dropPickup.set(false);
		boardKey.reset();
		gesture.setModifier(false);
		gesture.end();
		while (gesture.poll() != null)
		{
			// drop any gesture left over from before skating was enabled
		}
	}

	/** Copies held state into `in` and ORs in pending edges. */
	public void drainInto(SkateInput in)
	{
		long now = clock.getAsLong();
		stick.tick(now);
		gesture.tick(now);
		in.steer = (right ? 1f : 0f) - (left ? 1f : 0f);
		boolean keyCharging = trickKeyHeld != KeyEvent.VK_UNDEFINED;
		// a slow stick stroke may still become a manual: its wind-up shows only once it moves fast
		boolean windUpShown = !stick.hidesWindUp();
		// a held trick key winds up like a mouse wind-up: crouched for a regular pop, charging either way
		in.crouch = crouchKey || (windUpShown && gesture.isCrouching()) || (keyCharging && !trickKeyNollie);
		in.charge = crouchKey || (windUpShown && gesture.isCharging()) || keyCharging;
		// S key only: the mouse wind-up also crouches but must not pick a 5-0 on a grind
		in.leanBack = crouchKey;
		// the brake key is only ever a brake (or a tight carve with steer): never Shift+W's front flip
		// the pad's A and X push on the board (on foot they sprint and jump instead)
		boolean pushDown = pushHeld || (!onFoot && (padA || padX));
		boolean brakeAsSlide = brakeKey && !pushDown;
		in.powerslide = shift || brakeAsSlide;
		boolean keysAlone = !pushDown && !left && !right;
		boolean alone = keysAlone && !gesture.isStrokeActive();
		if (!shift || !alone)
		{
			// not Shift alone: the brake waits BRAKE_DELAY_MS from when it is alone again
			shiftAloneSince = now;
		}
		boolean shiftBrakes = shift && now - shiftAloneSince >= BRAKE_DELAY_MS;
		// the brake key is not a trick modifier, so it needs no delay; in controller mode the right stick's
		// stroke in progress does not stop it braking (B is its own button, not a steer)
		boolean brakeAlone = controllerMode ? keysAlone : alone;
		in.brakeBlocked = !(shiftBrakes || (brakeAsSlide && brakeAlone));
		in.pushPressed |= push.getAndSet(false);
		in.pushHeld = pushDown;
		in.resetRequested |= reset.getAndSet(false);
		in.grabLeft = grabLeftKey;
		in.grabRight = grabRightKey;
		in.grabAim = grabLeftKey || grabRightKey ? grabAim : null;
		in.leanForwardKey = leanForward;
		in.leanBackKey = leanBack;
		in.controllerMode = controllerMode;
		ControllerStick.Manual stickManual = controllerMode ? stick.manual() : ControllerStick.Manual.NONE;
		in.manualHeld = manualKey || stickManual != ControllerStick.Manual.NONE;
		in.noseManualHeld = stickManual == ControllerStick.Manual.NOSE;

		Gesture g;
		while ((g = gesture.poll()) != null)
		{
			// physics decides what a gesture does (pop when grounded, flip when airborne)
			in.gestures.add(mirrorFlicks ? KeyboardTricks.mirror(g) : g);
		}
		while ((g = keyGestures.poll()) != null)
		{
			in.gestures.add(g);
		}
	}

	/**
	 * The on-foot reading of the held keys, and the jump and drop / pick-up edges since the last call. W / S
	 * (and Key Remapping's arrows) walk forward and back; in controller mode so do the lean keys (the left
	 * stick's real arrows).
	 */
	public void drainFoot(FootControls out)
	{
		boolean stickLean = controllerMode;
		out.forward = pushHeld || (stickLean && leanForward);
		out.back = crouchKey || (stickLean && leanBack);
		out.left = left;
		out.right = right;
		out.sprint = shift || padA;
		out.jumpPressed |= jump.getAndSet(false);
		out.dropPickupPressed |= dropPickup.getAndSet(false);
	}

	/** The board key's press, tap and hold since the last call. */
	public void pollBoardKey(BoardKey.Edges out)
	{
		boardKey.poll(clock.getAsLong(), out);
	}

	public boolean consumeExit()
	{
		return exit.getAndSet(false);
	}

	/** The latest near-miss flick since the last call (null when none, or with mouse flicks off). */
	public NearMiss consumeNearMiss()
	{
		NearMiss m = gesture.pollNearMiss();
		return mouseTricks ? m : null;
	}

	/**
	 * The flick stroke in progress, for the HUD's live flick visualizer; the latest flick is the newer of the last
	 * mouse flick (as the hand moved, before mirroring) and the last keyboard trick.
	 */
	public LiveStroke getLiveStroke()
	{
		LiveStroke s = gesture.live(clock.getAsLong(), GestureRecognizer.LIVE_TRAIL_MS);
		LiveStroke k = keyFlash;
		if (k != null && (s.firedDirection == null || k.firedMs > s.firedMs))
		{
			s = s.withFired(k.firedDirection, k.firedNollie, k.firedMs);
		}
		return s;
	}

	/** True once after a left click on the world while skating (not a flick, not on the sidebar or chatbox). */
	public boolean consumePlainClick()
	{
		return plainClick.getAndSet(false);
	}

	/** True once, the frame after H is pressed while skating: toggles the controls card. */
	public boolean consumeControlsToggle()
	{
		return controlsToggle.getAndSet(false);
	}

	public int drainZoomNotches()
	{
		return zoomNotches.getAndSet(0);
	}

	/** Sideways pixels the camera was dragged with the middle button on foot since the last call (right = +). */
	public int drainOrbitPixels()
	{
		return orbitPixels.getAndSet(0);
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		if (enabled && !clickThrough(e))
		{
			// skate mode owns the camera distance (the side panel and chatbox still scroll); the game's own zoom would be overwritten anyway
			zoomNotches.addAndGet(e.getWheelRotation());
			e.consume();
		}
		return e;
	}

	@Override
	public void focusLost()
	{
		left = right = crouchKey = shift = pushHeld = grabLeftKey = grabRightKey = manualKey = false;
		leanForward = leanBack = brakeKey = false;
		grabAim = null;
		stick.reset();
		trickKeyHeld = KeyEvent.VK_UNDEFINED;
		gesture.setModifier(false);
		gesture.end();
		spaceDown = false;
		padA = padX = false;
		boardKey.reset();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (enabled)
		{
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (enabled && handleOffBoardKey(e.getKeyCode(), true))
		{
			e.consume();
			return;
		}
		if (enabled && handleTrickKey(e.getKeyCode(), true, e.isAltDown()))
		{
			e.consume();
			return;
		}
		if (!enabled || !handleKey(e.getKeyCode(), e.getKeyChar(), true))
		{
			return;
		}
		e.consume();
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (enabled && handleOffBoardKey(e.getKeyCode(), false))
		{
			e.consume();
			return;
		}
		if (enabled && handleTrickKey(e.getKeyCode(), false, e.isAltDown()))
		{
			e.consume();
			return;
		}
		if (!enabled || !handleKey(e.getKeyCode(), e.getKeyChar(), false))
		{
			return;
		}
		e.consume();
	}

	/**
	 * The board on/off key (in either mode), the pad A / X keys (in either mode), and Space on foot (a jump,
	 * never a trick or manual). Returns false for every other key.
	 */
	private boolean handleOffBoardKey(int code, boolean down)
	{
		if (code != KeyEvent.VK_UNDEFINED && code == boardKeyCode)
		{
			if (down)
			{
				boardKey.press(clock.getAsLong());
			}
			else
			{
				boardKey.release(clock.getAsLong());
			}
			return true;
		}
		if (code != KeyEvent.VK_UNDEFINED && (code == padACode || code == padXCode))
		{
			handlePadKey(code == padACode, down);
			return true;
		}
		if (code == KeyEvent.VK_SPACE)
		{
			// tracked in both modes, so a Space held across a mount or dismount is not a new press
			boolean wasDown = spaceDown;
			spaceDown = down;
			if (onFoot)
			{
				if (down && !wasDown)
				{
					jump.set(true);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * A pad A ({@code isA}) or pad X key went down or up. On the board a new press pushes (an edge, as W does);
	 * on foot a new X press jumps. A's held state is read as the sprint on foot and both as push held on the board.
	 */
	private void handlePadKey(boolean isA, boolean down)
	{
		boolean wasDown = isA ? padA : padX;
		if (isA)
		{
			padA = down;
		}
		else
		{
			padX = down;
		}
		if (!down || wasDown)
		{
			// a release, or a key repeat while held
			return;
		}
		if (!onFoot)
		{
			push.set(true);
		}
		else if (!isA)
		{
			jump.set(true);
		}
	}

	/**
	 * Keyboard tricks: a trick key winds up while held and fires its trick when released, so holding it charges
	 * the pop as a held mouse wind-up does. Key repeats while held are ignored. Returns false when keyboard tricks
	 * are off or {@code code} is not a trick key (or is the manual key).
	 */
	private boolean handleTrickKey(int code, boolean down, boolean alt)
	{
		if (!keyboardTricks || code == manualKeyCode || !KeyboardTricks.isTrickKey(code))
		{
			return false;
		}
		if (down)
		{
			if (trickKeyHeld != code)
			{
				trickKeyHeld = code;
				trickKeyNollie = alt;
			}
		}
		else if (trickKeyHeld == code)
		{
			trickKeyHeld = KeyEvent.VK_UNDEFINED;
			// Shift as the trick fires picks the hard version, as with a mouse flick
			Gesture fired = KeyboardTricks.gestureFor(code, trickKeyNollie, shift);
			keyGestures.add(fired);
			keyFlash = new LiveStroke(false, 0, 0, 0, 0, new float[0], new float[0], new long[0], false, false, 0, 0,
				fired.direction, fired.nollie, clock.getAsLong(), 1f, 0);
		}
		return true;
	}

	/**
	 * @param ch the key's char: CHAR_UNDEFINED for a real arrow key, but a letter for W/S that Key Remapping
	 * rewrote to an arrow (it changes only the key code)
	 */
	private boolean handleKey(int code, char ch, boolean down)
	{
		// the lean keys (grab-flips) also keep whatever else the key does: the default arrows still push / crouch
		boolean lean = false;
		// W / S that Key Remapping rewrote to the arrows push and crouch only: a grab-flip needs the real arrow
		boolean remappedLetter = (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && ch != KeyEvent.CHAR_UNDEFINED;
		if (code != KeyEvent.VK_UNDEFINED && code == leanForwardCode && !remappedLetter)
		{
			leanForward = down;
			lean = true;
		}
		if (code != KeyEvent.VK_UNDEFINED && code == leanBackCode && !remappedLetter)
		{
			leanBack = down;
			lean = true;
		}
		if (code != KeyEvent.VK_UNDEFINED && code == manualKeyCode)
		{
			// consumed so the key (Space by default) does not reach the chatbox or the game
			manualKey = down;
			return true;
		}
		if (lean && controllerMode && ch == KeyEvent.CHAR_UNDEFINED
			&& (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN))
		{
			// controller mode: the left stick's up / down arrives as the real arrow keys and only leans, so it
			// never pushes or crouches (W / S, also as Key Remapping's arrows, still do)
			return true;
		}
		if (code != KeyEvent.VK_UNDEFINED && code == brakeCode)
		{
			brakeKey = down;
			return true;
		}
		switch (code)
		{
			// Key Remapping (core RuneLite plugin) rewrites W/A/S/D to the arrow keys
			// before this listener runs, so each arrow is an alias for its WASD key.
			case KeyEvent.VK_UP:
			case KeyEvent.VK_W:
				if (down && !pushHeld)
				{
					push.set(true);
				}
				pushHeld = down;
				return true;
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_A:
				left = down;
				return true;
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_D:
				right = down;
				return true;
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_S:
				crouchKey = down;
				return true;
			case KeyEvent.VK_SHIFT:
				if (down && !shift)
				{
					shiftAloneSince = clock.getAsLong();
				}
				shift = down;
				// also the trick modifier: a flick fired while Shift is held is a hardflip, impossible, ...
				gesture.setModifier(down);
				return true;
			case KeyEvent.VK_R:
				if (down)
				{
					reset.set(true);
				}
				return true;
			case KeyEvent.VK_H:
				if (down && !controlsKeyDown)
				{
					controlsToggle.set(true);
				}
				controlsKeyDown = down;
				return true;
			case KeyEvent.VK_ESCAPE:
				if (down)
				{
					exit.set(true);
				}
				return true;
			case KeyEvent.VK_Q:
				if (down && !grabLeftKey)
				{
					startAim();
					if (onFoot)
					{
						dropPickup.set(true);
					}
				}
				if (down && !(grabLeftKey && stickFreeWhileGrabbing(clock.getAsLong())))
				{
					// the stick aims the grab now: a stroke in progress is no flick (a key repeat in a rolling grab
					// past the aim window leaves a flick out of it alone)
					stick.block(clock.getAsLong());
				}
				grabLeftKey = down;
				return true;
			case KeyEvent.VK_E:
				if (down && !grabRightKey)
				{
					startAim();
					if (onFoot)
					{
						dropPickup.set(true);
					}
				}
				if (down && !(grabRightKey && stickFreeWhileGrabbing(clock.getAsLong())))
				{
					stick.block(clock.getAsLong());
				}
				grabRightKey = down;
				return true;
			default:
				return lean;
		}
	}

	/**
	 * Sets the canvas areas (sidebar, chatbox) where presses and clicks pass through to the game while skating.
	 * Called on the client thread; read on the AWT thread.
	 */
	public void setClickThroughAreas(List<Rectangle> areas)
	{
		clickThroughAreas = areas;
	}

	private boolean clickThrough(MouseEvent e)
	{
		for (Rectangle r : clickThroughAreas)
		{
			if (r.contains(e.getX(), e.getY()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The flick button: right, or middle, which is what a right press arrives as when the core Camera plugin's
	 * "Right click moves camera" remaps it (with "Middle-button menu" a middle press arrives as right instead).
	 */
	private boolean isFlickButton(int button)
	{
		return isFlickButton(flickButton, button);
	}

	/**
	 * True when {@code button} is the configured flick button. Right also takes middle: the core Camera plugin's
	 * "Right click moves camera" re-sends a right press as a middle one.
	 */
	static boolean isFlickButton(GielinorSkateConfig.FlickButton setting, int button)
	{
		switch (setting)
		{
			case LEFT:
				return button == MouseEvent.BUTTON1;
			case MIDDLE:
				return button == MouseEvent.BUTTON2;
			default:
				return button == MouseEvent.BUTTON3 || button == MouseEvent.BUTTON2;
		}
	}


	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (!enabled)
		{
			return e;
		}
		if (clickThrough(e))
		{
			// the sidebar and chatbox stay usable; only world clicks are blocked while skating
			return e;
		}
		// consuming the press stops the game from taking keyboard focus back (e.g. after using the sidebar)
		e.getComponent().requestFocusInWindow();
		if (onFoot && e.getButton() == MouseEvent.BUTTON2)
		{
			// on foot there are no flicks: the middle button orbits the camera, like the game's own
			orbiting = true;
			orbitLastX = e.getX();
		}
		else if (isFlickButton(e.getButton()))
		{
			// a drag of its own: a stick stroke in progress must not end it
			stick.reset();
			if (mouseTricks)
			{
				gesture.begin(e.getX(), e.getY(), e.getWhen());
			}
		}
		else if (e.getButton() == MouseEvent.BUTTON1)
		{
			plainClick.set(true);
		}
		e.consume(); // no world interaction while skating
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (!enabled)
		{
			return e;
		}
		if (e.getButton() == MouseEvent.BUTTON2)
		{
			orbiting = false;
		}
		if (isFlickButton(e.getButton()))
		{
			gesture.end();
			rebaseAim(e.getX(), e.getY());
		}
		if (!clickThrough(e))
		{
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		if (enabled && !clickThrough(e))
		{
			e.consume();
		}
		return e;
	}

	/** Whether the skater rolls on the ground (not in the air, on a rail or bailed); see {@link #rolling}. */
	public void setRolling(boolean rolling)
	{
		this.rolling = rolling;
	}

	/**
	 * A grab key held while rolling, past its aim window: the stick is free to flick (an ollie out of the rolling grab).
	 * In the air (or within the window) the stick only aims the grab, as always.
	 */
	private boolean stickFreeWhileGrabbing(long ms)
	{
		return rolling && ms - aimPressMs > AIM_MS;
	}

	/** A grab key went down: its aim is measured from where the mouse is now. */
	private void startAim()
	{
		grabAim = null;
		aimPressMs = clock.getAsLong();
		aimOriginPending = !mouseKnown;
		aimOriginX = mouseX;
		aimOriginY = mouseY;
	}

	/**
	 * Mouse movement with no button pressed: with a grab key held it aims the grab (a drag with the flick button
	 * is a flick instead, see {@link #mouseDragged}). Not consumed: the game still sees the mouse move.
	 */
	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		if (!enabled)
		{
			return e;
		}
		mouseX = e.getX();
		mouseY = e.getY();
		mouseKnown = true;
		if (controllerMode)
		{
			// the right stick: with a grab key held it only aims the grab (rolling, only within the aim window)
			if ((grabLeftKey || grabRightKey) && !stickFreeWhileGrabbing(e.getWhen()))
			{
				stick.blockedMove(mouseX, mouseY, e.getWhen());
			}
			else
			{
				stick.move(mouseX, mouseY, e.getWhen());
			}
		}
		if (!(grabLeftKey || grabRightKey) || grabAim != null)
		{
			return e;
		}
		if (e.getWhen() - aimPressMs > AIM_MS)
		{
			// too long after the press (re-centring the mouse with the key held): this press stays unaimed
			return e;
		}
		if (aimOriginPending)
		{
			aimOriginPending = false;
			aimOriginX = mouseX;
			aimOriginY = mouseY;
			return e;
		}
		grabAim = GrabAim.classify(mouseX - aimOriginX, mouseY - aimOriginY, mirrorFlicks);
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		if (enabled)
		{
			mouseX = e.getX();
			mouseY = e.getY();
			mouseKnown = true;
			if (orbiting)
			{
				if (onFoot)
				{
					orbitPixels.addAndGet(e.getX() - orbitLastX);
				}
				orbitLastX = e.getX();
				e.consume();
				return e;
			}
			gesture.move(e.getX(), e.getY(), e.getWhen());
			rebaseAim(e.getX(), e.getY());
			e.consume();
		}
		return e;
	}

	/**
	 * A flick-button drag or release with a grab key held and not aimed yet: the aim is measured from here, so
	 * the flick's own movement never counts towards it (the aim is a move without a button).
	 */
	private void rebaseAim(int x, int y)
	{
		if ((grabLeftKey || grabRightKey) && grabAim == null)
		{
			aimOriginPending = false;
			aimOriginX = x;
			aimOriginY = y;
		}
	}
}
