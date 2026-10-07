package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.controller.PadButton;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.physics.SkateInput;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickCatalog;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * The Tony Hawk's American Wasteland preset through the input: each button where the skater is, flips and grabs
 * with a direction (the left stick or the d-pad), the stick manual gesture, the right stick on the camera, Y's
 * contexts and the spin assist.
 */
public class InputControllerThawTest
{
	private static final Component SOURCE = new Canvas();

	private long now = 1000;
	private final InputController c = new InputController(() -> now);

	@Before
	public void thaw()
	{
		c.configureController(true, KeyEvent.VK_B);
		c.configurePreset(PadPreset.thaw());
		c.setEnabled(true);
	}

	private void key(int code, boolean down)
	{
		KeyEvent e = new KeyEvent(SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED, 0, 0, code,
			KeyEvent.CHAR_UNDEFINED);
		if (down)
		{
			c.keyPressed(e);
		}
		else
		{
			c.keyReleased(e);
		}
		assertTrue("consumed " + KeyEvent.getKeyText(code), e.isConsumed());
	}

	private void press(PadButton b)
	{
		key(b.keyCode, true);
	}

	private void release(PadButton b)
	{
		key(b.keyCode, false);
	}

	private void tap(PadButton b)
	{
		press(b);
		release(b);
	}

	private SkateInput skate()
	{
		SkateInput in = new SkateInput();
		c.drainInto(in);
		return in;
	}

	private FootControls foot()
	{
		FootControls f = new FootControls();
		c.drainFoot(f);
		return f;
	}

	private BoardKey.Edges boardKey()
	{
		BoardKey.Edges e = new BoardKey.Edges();
		c.pollBoardKey(e);
		return e;
	}

	private void air()
	{
		c.setRolling(false);
		c.setAirborne(true);
	}

	private void rollingOnGround()
	{
		c.setAirborne(false);
		c.setRolling(true);
	}

	/** The tricks a flip press with {@code dirs} held makes, once its direction wait is over. */
	private List<Trick> flip(int... dirs)
	{
		for (int d : dirs)
		{
			key(d, true);
		}
		press(PadButton.X);
		now += ButtonTricks.DIRECTION_WAIT_MS;
		List<Trick> out = tricks(skate());
		release(PadButton.X);
		for (int d : dirs)
		{
			key(d, false);
		}
		out.addAll(tricks(skate()));
		return out;
	}

	private static List<Trick> tricks(SkateInput in)
	{
		List<Trick> out = new ArrayList<>();
		for (Gesture g : in.gestures)
		{
			out.add(TrickCatalog.forGesture(g));
		}
		return out;
	}

	private static List<Trick> list(Trick t)
	{
		List<Trick> l = new ArrayList<>();
		l.add(t);
		return l;
	}

	// ---- A

	@Test
	public void aHeldChargesAndLetGoOllies()
	{
		rollingOnGround();
		press(PadButton.A);
		SkateInput in = skate();
		assertTrue(in.crouch);
		assertTrue(in.charge);
		assertTrue(in.gestures.isEmpty());
		assertFalse("A is not a push here", in.pushPressed);
		release(PadButton.A);
		in = skate();
		assertEquals(list(Trick.OLLIE), tricks(in));
		assertFalse(in.crouch);
	}

	@Test
	public void aOnFootJumps()
	{
		c.setOnFoot(true);
		press(PadButton.A);
		assertTrue(foot().jumpPressed);
		release(PadButton.A);
		assertTrue(skate().gestures.isEmpty());
	}

	// ---- X: flips

	@Test
	public void flipButtonWithTheLeftStick()
	{
		air();
		assertEquals(list(Trick.KICKFLIP), flip(KeyEvent.VK_LEFT));
		assertEquals(list(Trick.HEELFLIP), flip(KeyEvent.VK_RIGHT));
		assertEquals(list(Trick.IMPOSSIBLE), flip(KeyEvent.VK_UP));
		assertEquals(list(Trick.POP_SHOVE_IT), flip(KeyEvent.VK_DOWN));
		assertEquals(list(Trick.KICKFLIP), flip());
	}

	@Test
	public void flipButtonDiagonalsWithTheDpad()
	{
		air();
		int up = PadButton.DPAD_UP.keyCode;
		int down = PadButton.DPAD_DOWN.keyCode;
		int left = PadButton.DPAD_LEFT.keyCode;
		int right = PadButton.DPAD_RIGHT.keyCode;
		assertEquals(list(Trick.VARIAL_KICKFLIP), flip(up, left));
		assertEquals(list(Trick.VARIAL_HEELFLIP), flip(up, right));
		assertEquals(list(Trick.TRE_FLIP), flip(down, left));
		assertEquals(list(Trick.HARDFLIP), flip(down, right));
		assertEquals(list(Trick.KICKFLIP), flip(left));
		// the stick and the d-pad together make a diagonal too
		assertEquals(list(Trick.VARIAL_KICKFLIP), flip(KeyEvent.VK_LEFT, up));
		// using the d-pad as a direction never toggled the controls card
		assertFalse(c.consumeControlsToggle());
	}

	@Test
	public void aDiagonalHeldAtThePressFlipsAtOnce()
	{
		air();
		key(PadButton.DPAD_UP.keyCode, true);
		key(PadButton.DPAD_RIGHT.keyCode, true);
		press(PadButton.X);
		assertEquals(list(Trick.VARIAL_HEELFLIP), tricks(skate()));
		release(PadButton.X);
		assertTrue(skate().gestures.isEmpty());
	}

	@Test
	public void theDirectionMayComeJustAfterThePress()
	{
		air();
		press(PadButton.X);
		now += 20;
		assertTrue("waits for its direction", skate().gestures.isEmpty());
		key(PadButton.DPAD_DOWN.keyCode, true);
		now += 10;
		key(PadButton.DPAD_LEFT.keyCode, true);
		now += ButtonTricks.DIRECTION_WAIT_MS;
		assertEquals(list(Trick.TRE_FLIP), tricks(skate()));
	}

	@Test
	public void aQuickTapFlipsOnRelease()
	{
		air();
		key(KeyEvent.VK_RIGHT, true);
		tap(PadButton.X);
		key(KeyEvent.VK_RIGHT, false);
		assertEquals(list(Trick.HEELFLIP), tricks(skate()));
	}

	@Test
	public void aSecondPressMidFlipIsAReFlick()
	{
		air();
		key(KeyEvent.VK_LEFT, true);
		tap(PadButton.X);
		now += 100;
		tap(PadButton.X);
		now += 100;
		tap(PadButton.X);
		SkateInput in = skate();
		// three kickflip flicks: physics makes them a kickflip, a double and a triple (see ButtonTricksTest)
		assertEquals(3, in.gestures.size());
		Trick t = TrickCatalog.forGesture(in.gestures.get(0));
		assertEquals(Trick.KICKFLIP, t);
		t = TrickCatalog.upgrade(t, in.gestures.get(1));
		assertEquals(Trick.DOUBLE_KICKFLIP, t);
		assertEquals(Trick.TRIPLE_KICKFLIP, TrickCatalog.upgrade(t, in.gestures.get(2)));
	}

	@Test
	public void theHardModifierMakesTheHardFlip()
	{
		air();
		press(PadButton.LB);
		assertEquals(list(Trick.HARDFLIP), flip(KeyEvent.VK_LEFT));
		assertEquals(list(Trick.INWARD_HEELFLIP), flip(KeyEvent.VK_RIGHT));
		release(PadButton.LB);
		assertEquals(list(Trick.KICKFLIP), flip(KeyEvent.VK_LEFT));
	}

	@Test
	public void flipButtonOnTheGroundPopsTheFlipAsAFlickWould()
	{
		rollingOnGround();
		assertEquals(list(Trick.KICKFLIP), flip(KeyEvent.VK_LEFT));
	}

	@Test
	public void xDoesNothingOnFoot()
	{
		c.setOnFoot(true);
		tap(PadButton.X);
		now += 100;
		assertTrue(skate().gestures.isEmpty());
		FootControls f = foot();
		assertFalse(f.jumpPressed);
		assertFalse(f.dropPickupPressed);
	}

	// ---- B: grabs

	/** The grab a grab press with {@code dirs} held makes, held past the wait. */
	private Trick grab(int... dirs)
	{
		for (int d : dirs)
		{
			key(d, true);
		}
		press(PadButton.B);
		now += ButtonTricks.DIRECTION_WAIT_MS;
		SkateInput in = skate();
		assertTrue(in.grabLeft != in.grabRight);
		Trick t = Grabs.pick(in.grabLeft, in.grabAim);
		release(PadButton.B);
		for (int d : dirs)
		{
			key(d, false);
		}
		in = skate();
		assertFalse(in.grabLeft);
		assertFalse(in.grabRight);
		return t;
	}

	@Test
	public void grabButtonWithADirection()
	{
		air();
		assertEquals(Trick.MELON, grab(KeyEvent.VK_LEFT));
		assertEquals(Trick.INDY, grab(KeyEvent.VK_RIGHT));
		assertEquals(Trick.NOSEGRAB, grab(KeyEvent.VK_UP));
		assertEquals(Trick.TAILGRAB, grab(KeyEvent.VK_DOWN));
		assertEquals(Trick.INDY, grab());
		assertEquals(Trick.CRAIL, grab(PadButton.DPAD_UP.keyCode, PadButton.DPAD_LEFT.keyCode));
		assertEquals(Trick.MUTE, grab(PadButton.DPAD_UP.keyCode, PadButton.DPAD_RIGHT.keyCode));
		assertEquals(Trick.STALEFISH, grab(PadButton.DPAD_DOWN.keyCode, PadButton.DPAD_LEFT.keyCode));
		assertEquals(Trick.INDY, grab(PadButton.DPAD_DOWN.keyCode, PadButton.DPAD_RIGHT.keyCode));
	}

	@Test
	public void theGrabsAimNeverMovesAfterThePress()
	{
		air();
		key(KeyEvent.VK_UP, true);
		press(PadButton.B);
		now += ButtonTricks.DIRECTION_WAIT_MS;
		assertEquals(Trick.NOSEGRAB, Grabs.pick(skate().grabLeft, skate().grabAim));
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_LEFT, true);
		now += 200;
		SkateInput in = skate();
		assertTrue(in.grabLeft);
		assertEquals(Grabs.Aim.NOSE, in.grabAim);
		// the mouse (the right stick) does not aim it either: it turns the camera
		c.mouseMoved(move(10, 10));
		c.mouseMoved(move(200, 10));
		assertEquals(Grabs.Aim.NOSE, skate().grabAim);
	}

	@Test
	public void bOnFootDropsOrPicksUp()
	{
		c.setOnFoot(true);
		press(PadButton.B);
		assertTrue(foot().dropPickupPressed);
		now += 100;
		assertFalse(skate().grabLeft);
		release(PadButton.B);
	}

	// ---- Y

	@Test
	public void yNearARailHoldsTheGrindCatch()
	{
		air();
		c.setRailNear(true);
		press(PadButton.Y);
		assertTrue(skate().grindHeld);
		assertFalse(boardKey().pressed);
		release(PadButton.Y);
		assertFalse(skate().grindHeld);
		// in the air with no rail near it still helps the catch, never steps off
		c.setRailNear(false);
		press(PadButton.Y);
		assertTrue(skate().grindHeld);
		assertFalse(boardKey().pressed);
		release(PadButton.Y);
	}

	@Test
	public void yRollingIntoARailHoldsTheGrindCatch()
	{
		rollingOnGround();
		c.setRailNear(true);
		press(PadButton.Y);
		assertTrue(skate().grindHeld);
		// held into the pop and onto the rail
		air();
		assertTrue(skate().grindHeld);
		release(PadButton.Y);
		assertFalse(boardKey().pressed);
	}

	@Test
	public void yRollingWithNoRailNearStepsOff()
	{
		rollingOnGround();
		c.setRailNear(false);
		press(PadButton.Y);
		assertFalse(skate().grindHeld);
		assertTrue(boardKey().pressed);
		release(PadButton.Y);
		assertTrue(boardKey().tapped);
	}

	@Test
	public void yOnFootGetsBackOn()
	{
		c.setOnFoot(true);
		press(PadButton.Y);
		assertTrue(boardKey().pressed);
		release(PadButton.Y);
		assertTrue(boardKey().tapped);
	}

	// ---- LB / RB

	@Test
	public void lbIsTheHardModifierAndSpinsFasterInTheAir()
	{
		rollingOnGround();
		press(PadButton.RB);
		SkateInput in = skate();
		assertTrue(in.powerslide);
		assertFalse("no faster spin on the ground", in.spinFast);
		air();
		in = skate();
		assertTrue(in.spinFast);
		assertTrue(in.powerslide);
		release(PadButton.RB);
		in = skate();
		assertFalse(in.spinFast);
		assertFalse(in.powerslide);
		c.setOnFoot(true);
		press(PadButton.LB);
		assertTrue(foot().sprint);
		assertFalse(skate().spinFast);
	}

	// ---- the rest

	@Test
	public void backResetsStartStopsDpadUpShowsTheCard()
	{
		rollingOnGround();
		tap(PadButton.BACK);
		assertTrue(skate().resetRequested);
		tap(PadButton.START);
		assertTrue(c.consumeExit());
		press(PadButton.DPAD_UP);
		assertFalse("waits for the release", c.consumeControlsToggle());
		release(PadButton.DPAD_UP);
		assertTrue(c.consumeControlsToggle());
		// used as a direction it never shows the card
		press(PadButton.DPAD_UP);
		tap(PadButton.X);
		release(PadButton.DPAD_UP);
		assertFalse(c.consumeControlsToggle());
		press(PadButton.DPAD_UP);
		tap(PadButton.DPAD_LEFT);
		release(PadButton.DPAD_UP);
		assertFalse(c.consumeControlsToggle());
	}

	@Test
	public void theLeftStickSteersAndUpPushes()
	{
		rollingOnGround();
		key(KeyEvent.VK_LEFT, true);
		assertEquals(-1f, skate().steer, 0f);
		key(KeyEvent.VK_LEFT, false);
		key(KeyEvent.VK_UP, true);
		SkateInput in = skate();
		assertTrue(in.pushPressed);
		assertTrue(in.pushHeld);
		key(KeyEvent.VK_UP, false);
		assertFalse(skate().pushHeld);
	}

	// ---- manuals

	@Test
	public void upThenDownWhileRollingIsAManualThatHolds()
	{
		rollingOnGround();
		key(KeyEvent.VK_UP, true);
		now += 100;
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_DOWN, true);
		now += 50;
		key(KeyEvent.VK_DOWN, false);
		SkateInput in = skate();
		assertTrue(in.manualHeld);
		assertFalse(in.noseManualHeld);
		assertFalse("no push in the manual", in.pushHeld);
		c.setRolling(false);
		c.setInManual(true);
		now += 2000;
		assertTrue("holds by itself", skate().manualHeld);
		// the other gesture switches to a nose manual
		key(KeyEvent.VK_DOWN, true);
		now += 100;
		key(KeyEvent.VK_DOWN, false);
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		in = skate();
		assertTrue(in.manualHeld);
		assertTrue(in.noseManualHeld);
		// an ollie out of it ends it
		c.setInManual(false);
		air();
		assertFalse(skate().manualHeld);
	}

	@Test
	public void downThenUpIsANoseManual()
	{
		rollingOnGround();
		key(KeyEvent.VK_DOWN, true);
		key(KeyEvent.VK_DOWN, false);
		now += StickManual.WINDOW_MS;
		key(KeyEvent.VK_UP, true);
		SkateInput in = skate();
		assertTrue(in.manualHeld);
		assertTrue(in.noseManualHeld);
	}

	@Test
	public void aManualEndsWhenThePhysicsLeavesIt()
	{
		rollingOnGround();
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_DOWN, true);
		assertTrue(skate().manualHeld);
		c.setInManual(true);
		// slowed down: physics rolls on
		c.setInManual(false);
		assertFalse(skate().manualHeld);
	}

	@Test
	public void tooSlowAGestureIsNoManual()
	{
		rollingOnGround();
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		now += StickManual.WINDOW_MS + 1;
		key(KeyEvent.VK_DOWN, true);
		assertFalse(skate().manualHeld);
	}

	@Test
	public void aManualThatCannotStartIsDropped()
	{
		rollingOnGround();
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_DOWN, true);
		assertTrue(skate().manualHeld);
		// too slow to manual: physics never enters it
		c.setInManual(false);
		now += InputController.MANUAL_START_MS + 1;
		assertFalse(skate().manualHeld);
		now += 5000;
		assertFalse(skate().manualHeld);
	}

	@Test
	public void theGestureInTheAirIsNoManual()
	{
		air();
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_DOWN, true);
		assertFalse(skate().manualHeld);
	}

	// ---- the right stick

	private static MouseEvent move(int x, int y)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false, MouseEvent.NOBUTTON);
	}

	@Test
	public void theRightStickTurnsTheCameraOnTheBoardAndNeverFlicks()
	{
		rollingOnGround();
		c.mouseMoved(move(300, 300));
		assertEquals(0, c.drainOrbitPixels());
		// a pull down and a flick up-left, which Skate 3's stick reads as a kickflip
		long t = 0;
		for (int i = 1; i <= 10; i++)
		{
			c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, t += 10, 0, 300, 300 + i * 8, 0, false,
				MouseEvent.NOBUTTON));
		}
		for (int i = 1; i <= 10; i++)
		{
			c.mouseMoved(new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, t += 10, 0, 300 - i * 15, 380 - i * 20, 0,
				false, MouseEvent.NOBUTTON));
		}
		now += 500;
		SkateInput in = skate();
		assertTrue(in.gestures.isEmpty());
		assertFalse(in.manualHeld);
		assertEquals(-150, c.drainOrbitPixels());
		c.mouseMoved(move(170, 200));
		assertEquals(20, c.drainOrbitPixels());
	}

	@Test
	public void theRightStickTurnsTheCameraOnFootWithNoButton()
	{
		c.setOnFoot(true);
		c.mouseMoved(move(100, 100));
		c.mouseMoved(move(140, 90));
		assertEquals(40, c.drainOrbitPixels());
	}

	@Test
	public void outsideControllerModeNothingChanges()
	{
		c.configureController(false, KeyEvent.VK_B);
		rollingOnGround();
		c.mouseMoved(move(100, 100));
		c.mouseMoved(move(140, 90));
		assertEquals(0, c.drainOrbitPixels());
		key(KeyEvent.VK_UP, true);
		key(KeyEvent.VK_UP, false);
		key(KeyEvent.VK_DOWN, true);
		assertFalse(skate().manualHeld);
		assertNull(skate().grabAim);
	}
}
