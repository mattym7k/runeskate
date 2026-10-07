package com.gielinorskate.input;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.controller.PadPresets;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/** Captures skate controls while enabled. Runs on AWT threads; read via drainInto on the client thread. */
@Singleton
public class InputController extends MouseAdapter implements KeyListener, MouseWheelListener
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
	 * Controller presets: each pad button sends its own pad key (see {@link PadButton}), and the preset turns it
	 * into an action by where the skater is. The buttons held, as bits by ordinal (written on the AWT thread only),
	 * are read by context when the input is drained, so a button held across a mount or dismount is not a new
	 * press but does what it does in the new place, as the old pad keys did.
	 */
	private volatile int padHeld;
	/** The action each held button was pressed as (its release is paired with it: the board key, the ollie). */
	private final PadAction[] padPressedAs = new PadAction[PadButton.values().length];
	/** The buttons whose pad keys are read: none outside controller mode, nor one on another key setting's key. */
	private volatile int padActive;
	private volatile PadPreset preset = PadPreset.skate3();
	/** In the air (set each frame): a button's air action applies. */
	private volatile boolean airborne;
	/** The skate mode (toggle) key when it has no modifiers, else VK_UNDEFINED: a pad key on it would toggle. */
	private volatile int plainToggleCode = KeyEvent.VK_UNDEFINED;

	// ---- button tricks (a preset with a flip or grab button, see PadPreset.buttonTricks; controller mode only)
	/** The preset does tricks with buttons: the right stick turns the camera, the left stick gives directions. */
	private volatile boolean buttonTricks;
	/** The left stick's up / down (the real arrow keys in controller mode), for trick directions and manuals. */
	private volatile boolean stickUp;
	private volatile boolean stickDown;
	/** The up-then-down (manual) and down-then-up (nose manual) gestures on the left stick. */
	private final StickManual stickManualGesture = new StickManual();
	/**
	 * The manual a stick gesture started: held by itself until the skater leaves the manual (a pop, slowing down,
	 * a bail) or does the other gesture; dropped when it does not start within {@link #MANUAL_START_MS}.
	 */
	private volatile StickManual.Kind manualLatch = StickManual.Kind.NONE;
	private volatile long manualLatchAt;
	/** The latched manual has started (physics is in it). */
	private volatile boolean manualLatchEntered;
	/** A stick manual that has not started this long after its gesture (too slow to manual) is dropped. */
	static final long MANUAL_START_MS = 250;
	/** In a manual (set each frame). */
	private volatile boolean inManual;
	/** A rail is near (set each frame): the grind button grinds instead of stepping off. */
	private volatile boolean railNear;
	/** Guards the flip and grab presses waiting for their direction. */
	private final Object trickLock = new Object();
	/** A flip button press waiting {@link ButtonTricks#DIRECTION_WAIT_MS} for its direction, or null. */
	private PendingTrick pendingFlip;
	/** A grab button press waiting for its direction, or null. */
	private PendingTrick pendingGrab;
	/** The grab button's grab: held (by the button {@link #padGrabButton}), its hand and its aim. */
	private volatile boolean padGrab;
	private volatile boolean padGrabLeft;
	private volatile Grabs.Aim padGrabAim;
	private volatile int padGrabButton = -1;
	/** The buttons held as the grind button (bits by ordinal). */
	private volatile int padGrindHeld;
	/**
	 * D-pad buttons used as a trick direction while held (bits by ordinal): their own press (the controls card on
	 * d-pad up) waits for the release and is dropped when they were (AWT thread only).
	 */
	private int dpadUsed;
	private static final int DPAD_BITS = (1 << PadButton.DPAD_UP.ordinal()) | (1 << PadButton.DPAD_DOWN.ordinal())
		| (1 << PadButton.DPAD_LEFT.ordinal()) | (1 << PadButton.DPAD_RIGHT.ordinal());

	/** A flip or grab button press and the direction held as it went down. */
	private static final class PendingTrick
	{
		final int button;
		final long pressMs;
		final Gesture.Direction atPress;

		PendingTrick(int button, long pressMs, Gesture.Direction atPress)
		{
			this.button = button;
			this.pressMs = pressMs;
			this.atPress = atPress;
		}
	}

	@Inject
	public InputController(GielinorSkateConfig config)
	{
		this(System::currentTimeMillis, config);
	}

	/** For tests: default keys and sensitivity unless {@link #configure} is called. */
	InputController()
	{
		this(System::currentTimeMillis, null);
	}

	InputController(LongSupplier clock)
	{
		this(clock, null);
	}

	private InputController(LongSupplier clock, GielinorSkateConfig config)
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
	 * True when a pad key is read: it is none of {@code taken} (the other key settings in use), so it never changes
	 * what another control's key does. The pad keys are never skate controls or trick keys.
	 */
	public static boolean isPadKeyFree(int code, int... taken)
	{
		for (int t : taken)
		{
			if (t == code)
			{
				return false;
			}
		}
		return true;
	}

	/** Sets the controller preset (read from the config on enable). A change lets go of the buttons held. */
	void configurePreset(PadPreset p)
	{
		if (!p.equals(preset))
		{
			preset = p;
			buttonTricks = p.buttonTricks();
			releasePad();
		}
	}

	/** Controller mode with a button-trick preset. */
	private boolean buttonMode()
	{
		return controllerMode && buttonTricks;
	}

	/** Sets the skate mode (toggle) key, so a pad key never shares it; one with modifiers never clashes. */
	void configureToggleKey(int code, int modifiers)
	{
		plainToggleCode = modifiers == 0 ? code : KeyEvent.VK_UNDEFINED;
		refreshPadKeys();
	}

	/**
	 * The pad keys read: only in controller mode (a keyboard player's keys are never pad keys), and each off when
	 * another key setting uses its key ({@link #isPadKeyFree}).
	 */
	private void refreshPadKeys()
	{
		int active = 0;
		if (controllerMode)
		{
			for (PadButton b : PadButton.values())
			{
				if (isPadKeyFree(b.keyCode, manualKeyCode, brakeCode, leanForwardCode, leanBackCode, boardKeyCode,
					plainToggleCode))
				{
					active |= 1 << b.ordinal();
				}
			}
		}
		// a button no longer read drops its held state: its release would no longer arrive, leaving it stuck down
		int dropped = padHeld & ~active;
		if (dropped != 0)
		{
			padHeld &= active;
			padGrindHeld &= active;
			if (padGrabButton >= 0 && (dropped & (1 << padGrabButton)) != 0)
			{
				padGrab = false;
				padGrabButton = -1;
			}
			for (PadButton b : PadButton.values())
			{
				if ((dropped & (1 << b.ordinal())) != 0)
				{
					padPressedAs[b.ordinal()] = null;
				}
			}
		}
		padActive = active;
	}

	/** Lets go of every pad button without its release doing anything. */
	private void releasePad()
	{
		padHeld = 0;
		Arrays.fill(padPressedAs, null);
		padGrindHeld = 0;
		dpadUsed = 0;
		padGrab = false;
		padGrabButton = -1;
		synchronized (trickLock)
		{
			pendingFlip = null;
			pendingGrab = null;
		}
	}

	/** The board's context: in the air or on the ground. */
	private PadContext boardContext()
	{
		return airborne ? PadContext.AIR : PadContext.BOARD;
	}

	/** Where the skater is now, for what a press does. */
	private PadContext context()
	{
		return onFoot ? PadContext.FOOT : boardContext();
	}

	/** True when a held pad button does {@code action} in {@code context}. */
	private boolean padDoes(PadAction action, PadContext context)
	{
		int held = padHeld;
		if (held == 0)
		{
			return false;
		}
		PadPreset p = preset;
		for (PadButton b : PadButton.values())
		{
			if ((held & (1 << b.ordinal())) != 0 && p.action(b, context) == action)
			{
				return true;
			}
		}
		return false;
	}

	/** Shift, or a pad button held as the hard tricks modifier. */
	private boolean shiftDown()
	{
		return shift || padDoes(PadAction.HARD_MODIFIER, boardContext())
			|| padDoes(PadAction.SPIN_ASSIST, boardContext());
	}

	private boolean grabbingLeft()
	{
		return grabLeftKey || padDoes(PadAction.GRAB_LEFT, boardContext());
	}

	private boolean grabbingRight()
	{
		return grabRightKey || padDoes(PadAction.GRAB_RIGHT, boardContext());
	}

	private boolean grabbing()
	{
		return grabbingLeft() || grabbingRight();
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
		airborne = false;
		orbiting = false;
		orbitPixels.set(0);
		jump.set(false);
		dropPickup.set(false);
		manualKey = false;
		trickKeyHeld = KeyEvent.VK_UNDEFINED;
		keyGestures.clear();
		inManual = false;
		dropStickManual();
		padGrab = false;
		padGrabButton = -1;
		synchronized (trickLock)
		{
			pendingFlip = null;
			pendingGrab = null;
		}
	}

	/** Lets go of the stick manual and any half-done gesture. */
	private void dropStickManual()
	{
		manualLatch = StickManual.Kind.NONE;
		manualLatchEntered = false;
		stickManualGesture.reset();
	}

	private void applyConfig()
	{
		configureBoardKey(config.boardKey().getKeyCode());
		configureToggleKey(config.toggleKey().getKeyCode(), config.toggleKey().getModifiers());
		configurePreset(PadPresets.resolve(config.controllerPreset(), config.customControllerLayout()));
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
		stickUp = stickDown = inManual = railNear = false;
		dropStickManual();
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
		releasePad();
		airborne = false;
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
		resolveTricks(now, -1);
		if (manualLatch != StickManual.Kind.NONE && !manualLatchEntered && now - manualLatchAt > MANUAL_START_MS)
		{
			// too slow to manual: no manual waiting to start later
			manualLatch = StickManual.Kind.NONE;
		}
		in.steer = (right ? 1f : 0f) - (left ? 1f : 0f);
		boolean keyCharging = trickKeyHeld != KeyEvent.VK_UNDEFINED;
		// a slow stick stroke may still become a manual: its wind-up shows only once it moves fast
		boolean windUpShown = !stick.hidesWindUp();
		// a held trick key winds up like a mouse wind-up: crouched for a regular pop, charging either way
		// a pad ollie button held winds up like a held Space trick key
		boolean padCharging = !onFoot && padDoes(PadAction.OLLIE, boardContext());
		in.crouch = crouchKey || (windUpShown && gesture.isCrouching()) || (keyCharging && !trickKeyNollie)
			|| padCharging;
		in.charge = crouchKey || (windUpShown && gesture.isCharging()) || keyCharging || padCharging;
		// S key only: the mouse wind-up also crouches but must not pick a 5-0 on a grind
		in.leanBack = crouchKey;
		// the brake key is only ever a brake (or a tight carve with steer): never Shift+W's front flip
		// the pad's push buttons push on the board (on foot they do their on-foot action instead)
		PadContext board = boardContext();
		// button tricks: the left stick up pushes on the board (not in a stick manual, where it would nose manual)
		boolean pushDown = pushHeld
			|| (!onFoot && (padDoes(PadAction.PUSH, board) || padDoes(PadAction.MONGO_PUSH, board)))
			|| (buttonMode() && !onFoot && stickUp && manualLatch == StickManual.Kind.NONE);
		boolean brakeAsSlide = (brakeKey || padDoes(PadAction.BRAKE, board)) && !pushDown;
		boolean shift = shiftDown();
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
		boolean grabLeft = grabbingLeft();
		boolean grabRight = grabbingRight();
		in.grabLeft = grabLeft;
		in.grabRight = grabRight;
		in.grabAim = grabLeft || grabRight ? grabAim : null;
		if (padGrab && !onFoot && !grabLeft && !grabRight)
		{
			// the grab button: its hand and aim were picked by the direction as it was pressed
			in.grabLeft = padGrabLeft;
			in.grabRight = !padGrabLeft;
			in.grabAim = padGrabAim;
		}
		in.grindHeld = !onFoot && padGrindHeld != 0;
		in.spinFast = !onFoot && airborne && padDoes(PadAction.SPIN_ASSIST, PadContext.AIR);
		in.leanForwardKey = leanForward || padDoes(PadAction.LEAN_FORWARD, board);
		in.leanBackKey = leanBack || padDoes(PadAction.LEAN_BACK, board);
		in.controllerMode = controllerMode;
		// with button tricks the right stick is the camera: its manual tilt is off, the left stick's gesture manuals
		ControllerStick.Manual stickManual = controllerMode && !buttonTricks ? stick.manual()
			: ControllerStick.Manual.NONE;
		StickManual.Kind latched = onFoot ? StickManual.Kind.NONE : manualLatch;
		in.manualHeld = manualKey || stickManual != ControllerStick.Manual.NONE || latched != StickManual.Kind.NONE;
		in.noseManualHeld = stickManual == ControllerStick.Manual.NOSE || latched == StickManual.Kind.NOSE;

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
		out.sprint = shift || padDoes(PadAction.SPRINT, PadContext.FOOT);
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

	/** The last completed right-mouse stroke, for the {@code ::skategesture} dev overlay; null until the first flick. */
	public StrokeSnapshot getLastStrokeSnapshot()
	{
		return gesture.getLastStrokeSnapshot();
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
		stickUp = stickDown = false;
		releasePad();
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
	 * The board on/off key (in either mode), the pad keys (in either mode), and Space on foot (a jump, never a
	 * trick or manual). Returns false for every other key.
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
		PadButton pad = PadButton.forKey(code);
		if (pad != null && (padActive & (1 << pad.ordinal())) != 0)
		{
			handlePad(pad, down);
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
	 * A pad button went down or up. Held actions (push held, sprint, brake, grabs, Shift, lean, the ollie wind-up)
	 * are read from the held buttons when drained; a press does the press of its action where the skater is now
	 * (push, jump, drop or pick up, the board key, reset, stop, the controls card), and its board action's grab
	 * aim or Shift timing starts in either place, as the old keys' did. The release is paired with the press.
	 */
	private void handlePad(PadButton b, boolean down)
	{
		long now = clock.getAsLong();
		int bit = 1 << b.ordinal();
		boolean wasDown = (padHeld & bit) != 0;
		PadPreset p = preset;
		PadAction boardAction = p.action(b, boardContext());
		if (down)
		{
			if (wasDown)
			{
				// a key repeat while held: as Q / E did, a grab key repeat keeps the stick on aiming
				if ((boardAction == PadAction.GRAB_LEFT || boardAction == PadAction.GRAB_RIGHT)
					&& !stickFreeWhileGrabbing(now))
				{
					stick.block(now);
				}
				return;
			}
			boolean shiftBefore = shiftDown();
			boolean grabLeftBefore = grabbingLeft();
			boolean grabRightBefore = grabbingRight();
			padHeld |= bit;
			PadContext where = context();
			PadAction action = p.action(b, where);
			if (action == PadAction.GRIND_BUTTON && where == PadContext.BOARD && !railNear)
			{
				// the grind button with no rail near, on the ground: step off (its release is the board key's)
				action = PadAction.BOARD_TOGGLE;
			}
			padPressedAs[b.ordinal()] = action;
			if (boardAction == PadAction.GRAB_LEFT || boardAction == PadAction.GRAB_RIGHT)
			{
				boolean before = boardAction == PadAction.GRAB_LEFT ? grabLeftBefore : grabRightBefore;
				if (!before)
				{
					startAim();
				}
				if (!(before && stickFreeWhileGrabbing(now)))
				{
					// the stick aims the grab now: a stroke in progress is no flick
					stick.block(now);
				}
			}
			else if (boardAction == PadAction.HARD_MODIFIER || boardAction == PadAction.SPIN_ASSIST)
			{
				if (!shiftBefore)
				{
					shiftAloneSince = now;
				}
				gesture.setModifier(true);
			}
			if (buttonMode() && isDpad(b) && dpadPressWaits(bit, action))
			{
				return;
			}
			trickPress(b, action, now);
			pressEdge(action, now);
			return;
		}
		if (!wasDown)
		{
			return;
		}
		padHeld &= ~bit;
		PadAction pressedAs = padPressedAs[b.ordinal()];
		padPressedAs[b.ordinal()] = null;
		gesture.setModifier(shiftDown());
		if (buttonMode() && isDpad(b))
		{
			boolean used = (dpadUsed & bit) != 0;
			dpadUsed &= ~bit;
			if (!used && dpadEdgeWaits(pressedAs))
			{
				// d-pad up alone, let go: the controls card (it was not a trick's direction)
				pressEdge(pressedAs, now);
			}
		}
		if (pressedAs == PadAction.FLIP_BUTTON || pressedAs == PadAction.GRAB_BUTTON)
		{
			trickRelease(b, now);
		}
		else if (pressedAs == PadAction.GRIND_BUTTON)
		{
			padGrindHeld &= ~bit;
		}
		if (pressedAs == PadAction.BOARD_TOGGLE)
		{
			boardKey.release(now);
		}
		else if (pressedAs == PadAction.OLLIE && !onFoot)
		{
			// the held ollie button pops when let go, as the Space trick key does (Shift held: the hard version)
			Gesture fired = KeyboardTricks.gestureFor(KeyEvent.VK_SPACE, false, shiftDown());
			keyGestures.add(fired);
			keyFlash = new LiveStroke(false, 0, 0, 0, 0, new float[0], new float[0], new long[0], false, false, 0, 0,
				fired.direction, fired.nollie, now, 1f, 0);
		}
	}

	private static boolean isDpad(PadButton b)
	{
		return ((1 << b.ordinal()) & DPAD_BITS) != 0;
	}

	/** The d-pad actions that are a single press: with button tricks they wait for the release. */
	private static boolean dpadEdgeWaits(PadAction a)
	{
		switch (a)
		{
			case CONTROLS_CARD:
			case RESET:
			case STOP:
			case JUMP:
			case DROP_PICKUP:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Button tricks: a d-pad press is also a trick direction. Pressed with another d-pad button (a diagonal) or with
	 * a flip or grab button held it is one, and its own press is dropped; a single press of its own waits for the
	 * release (see {@link #dpadEdgeWaits}). True when the press waits.
	 */
	private boolean dpadPressWaits(int bit, PadAction action)
	{
		if ((padHeld & DPAD_BITS & ~bit) != 0 || trickButtonHeld())
		{
			dpadUsed |= padHeld & DPAD_BITS;
		}
		else
		{
			dpadUsed &= ~bit;
		}
		return dpadEdgeWaits(action);
	}

	/** A held button was pressed as the flip or grab button. */
	private boolean trickButtonHeld()
	{
		int held = padHeld;
		for (PadButton b : PadButton.values())
		{
			PadAction a = padPressedAs[b.ordinal()];
			if ((held & (1 << b.ordinal())) != 0 && (a == PadAction.FLIP_BUTTON || a == PadAction.GRAB_BUTTON))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The direction for a button trick: the left stick (its arrow keys) and the d-pad together, so the d-pad gives
	 * the diagonals the four-way stick can't. Null when none.
	 */
	private Gesture.Direction trickDirection()
	{
		int held = padHeld;
		return ButtonTricks.direction(stickUp || (held & (1 << PadButton.DPAD_UP.ordinal())) != 0,
			stickDown || (held & (1 << PadButton.DPAD_DOWN.ordinal())) != 0,
			left || (held & (1 << PadButton.DPAD_LEFT.ordinal())) != 0,
			right || (held & (1 << PadButton.DPAD_RIGHT.ordinal())) != 0);
	}

	private static boolean diagonal(Gesture.Direction d)
	{
		return d == Gesture.Direction.UP_LEFT || d == Gesture.Direction.UP_RIGHT || d == Gesture.Direction.DOWN_LEFT
			|| d == Gesture.Direction.DOWN_RIGHT;
	}

	/**
	 * A flip, grab or grind button press. A flip or grab waits up to {@link ButtonTricks#DIRECTION_WAIT_MS} for its
	 * direction (a diagonal held already goes at once); a second flip press while one waits fires the first.
	 */
	private void trickPress(PadButton b, PadAction action, long now)
	{
		switch (action)
		{
			case FLIP_BUTTON:
			case GRAB_BUTTON:
				dpadUsed |= padHeld & DPAD_BITS;
				Gesture.Direction d = trickDirection();
				synchronized (trickLock)
				{
					PendingTrick press = new PendingTrick(b.ordinal(), now, d);
					if (action == PadAction.FLIP_BUTTON)
					{
						if (pendingFlip != null)
						{
							fireFlip(pendingFlip, now);
						}
						pendingFlip = press;
					}
					else
					{
						padGrab = false;
						padGrabButton = -1;
						pendingGrab = press;
					}
					if (diagonal(d))
					{
						resolveTricks(now, b.ordinal());
					}
				}
				break;
			case GRIND_BUTTON:
				padGrindHeld |= 1 << b.ordinal();
				break;
			default:
				break;
		}
	}

	/** A flip button let go before its direction wait fires now; a grab button let go lets go of its grab. */
	private void trickRelease(PadButton b, long now)
	{
		int ord = b.ordinal();
		synchronized (trickLock)
		{
			if (pendingFlip != null && pendingFlip.button == ord)
			{
				fireFlip(pendingFlip, now);
				pendingFlip = null;
			}
			if (pendingGrab != null && pendingGrab.button == ord)
			{
				pendingGrab = null;
			}
			if (padGrabButton == ord)
			{
				padGrab = false;
				padGrabButton = -1;
			}
		}
	}

	/**
	 * Fires the flip and starts the grab whose direction wait is over (or, for {@code button}, at once). Called
	 * when the input is drained, so the flip reaches physics as a flick or trick key would.
	 */
	private void resolveTricks(long now, int button)
	{
		synchronized (trickLock)
		{
			if (pendingFlip != null && (pendingFlip.button == button
				|| now - pendingFlip.pressMs >= ButtonTricks.DIRECTION_WAIT_MS))
			{
				fireFlip(pendingFlip, now);
				pendingFlip = null;
			}
			if (pendingGrab != null && (pendingGrab.button == button
				|| now - pendingGrab.pressMs >= ButtonTricks.DIRECTION_WAIT_MS))
			{
				Gesture.Direction d = trickDirection();
				if (d == null)
				{
					d = pendingGrab.atPress;
				}
				padGrabLeft = ButtonTricks.grabLeftHand(d);
				padGrabAim = ButtonTricks.grabAim(d);
				padGrabButton = pendingGrab.button;
				padGrab = true;
				pendingGrab = null;
			}
		}
	}

	/** The flip of a flip button press: its direction now (or as pressed), the hard version with the modifier held. */
	private void fireFlip(PendingTrick press, long now)
	{
		Gesture.Direction d = trickDirection();
		if (d == null)
		{
			d = press.atPress;
		}
		Gesture fired = ButtonTricks.flip(d, shiftDown());
		keyGestures.add(fired);
		keyFlash = new LiveStroke(false, 0, 0, 0, 0, new float[0], new float[0], new long[0], false, false, 0, 0,
			fired.direction, fired.nollie, now, 1f, 0);
	}

	/** What a pad button's new press does, for the action it was pressed as. */
	private void pressEdge(PadAction action, long now)
	{
		switch (action)
		{
			case PUSH:
			case MONGO_PUSH:
				push.set(true);
				break;
			case JUMP:
				jump.set(true);
				break;
			case DROP_PICKUP:
				dropPickup.set(true);
				break;
			case BOARD_TOGGLE:
				boardKey.press(now);
				break;
			case RESET:
				reset.set(true);
				break;
			case STOP:
				exit.set(true);
				break;
			case CONTROLS_CARD:
				controlsToggle.set(true);
				break;
			case CAMERA_ORBIT:
				orbitLastX = mouseX;
				break;
			default:
				break;
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
			Gesture fired = KeyboardTricks.gestureFor(code, trickKeyNollie, shiftDown());
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
		if (controllerMode && ch == KeyEvent.CHAR_UNDEFINED && (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN))
		{
			stickArrow(code == KeyEvent.VK_UP, down);
		}
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
				if (down && !shiftDown())
				{
					shiftAloneSince = clock.getAsLong();
				}
				shift = down;
				// also the trick modifier: a flick fired while Shift is held is a hardflip, impossible, ...
				gesture.setModifier(shiftDown());
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
	 * Whether the skater is in a manual (set each frame). A stick manual holds until the manual it started ends (a
	 * pop, slowing down, a bail).
	 */
	public void setInManual(boolean manual)
	{
		inManual = manual;
		if (manualLatch != StickManual.Kind.NONE)
		{
			if (manual)
			{
				manualLatchEntered = true;
			}
			else if (manualLatchEntered)
			{
				manualLatch = StickManual.Kind.NONE;
			}
		}
	}

	/** Whether a rail is near the skater (set each frame): the grind button grinds there instead of stepping off. */
	public void setRailNear(boolean near)
	{
		railNear = near;
	}

	/**
	 * The left stick's up or down (a real arrow key in controller mode). With button tricks, on the board, a new
	 * up pushes, and up-then-down (down-then-up) within {@link StickManual#WINDOW_MS} while rolling, or in a manual,
	 * is a manual (nose manual) that then holds by itself.
	 */
	private void stickArrow(boolean up, boolean down)
	{
		boolean was = up ? stickUp : stickDown;
		if (up)
		{
			stickUp = down;
		}
		else
		{
			stickDown = down;
		}
		if (!down || was || !buttonMode() || onFoot)
		{
			return;
		}
		long now = clock.getAsLong();
		StickManual.Kind k = up ? stickManualGesture.up(now) : stickManualGesture.down(now);
		if (k != StickManual.Kind.NONE && (rolling || inManual))
		{
			manualLatch = k;
			manualLatchAt = now;
			manualLatchEntered = inManual;
		}
		else if (up && manualLatch == StickManual.Kind.NONE)
		{
			push.set(true);
		}
	}

	/**
	 * Whether the skater is in the air on the board (set each frame): a pad button with an air action of its own
	 * does that there. A change re-reads the Shift a held button gives.
	 */
	public void setAirborne(boolean airborne)
	{
		if (airborne != this.airborne)
		{
			this.airborne = airborne;
			gesture.setModifier(shiftDown());
			if (airborne)
			{
				// popped out of a stick manual (or off a ledge): it is over
				dropStickManual();
			}
		}
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
		if (buttonMode())
		{
			// button tricks: the right stick turns the camera, on foot and on the board (no flicks)
			if (mouseKnown)
			{
				orbitPixels.addAndGet(e.getX() - mouseX);
			}
		}
		else if (onFoot && padDoes(PadAction.CAMERA_ORBIT, PadContext.FOOT))
		{
			// a held camera button: the right stick's sideways motion orbits, like a middle-button drag
			orbitPixels.addAndGet(e.getX() - orbitLastX);
			orbitLastX = e.getX();
		}
		mouseX = e.getX();
		mouseY = e.getY();
		mouseKnown = true;
		if (controllerMode && !buttonTricks)
		{
			// the right stick: with a grab key held it only aims the grab (rolling, only within the aim window)
			if (grabbing() && !stickFreeWhileGrabbing(e.getWhen()))
			{
				stick.blockedMove(mouseX, mouseY, e.getWhen());
			}
			else
			{
				stick.move(mouseX, mouseY, e.getWhen());
			}
		}
		if (!grabbing() || grabAim != null)
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
		if (grabbing() && grabAim == null)
		{
			aimOriginPending = false;
			aimOriginX = x;
			aimOriginY = y;
		}
	}
}
