package com.gielinorskate.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The pad keys, the actions and the Skate 3 preset. */
public class PadPresetTest
{
	@Test
	public void everyButtonHasItsOwnPadKeyFromTheUniversalTable()
	{
		int[] expected = {KeyEvent.VK_F13, KeyEvent.VK_F14, KeyEvent.VK_F15, KeyEvent.VK_F16, KeyEvent.VK_F17,
			KeyEvent.VK_F18, KeyEvent.VK_F19, KeyEvent.VK_F20, KeyEvent.VK_F21, KeyEvent.VK_F22, KeyEvent.VK_F23,
			KeyEvent.VK_F24, KeyEvent.VK_INSERT, KeyEvent.VK_DELETE, KeyEvent.VK_HOME, KeyEvent.VK_END};
		PadButton[] order = {PadButton.A, PadButton.B, PadButton.X, PadButton.Y, PadButton.LB, PadButton.RB,
			PadButton.LT, PadButton.RT, PadButton.BACK, PadButton.START, PadButton.L3, PadButton.R3,
			PadButton.DPAD_UP, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT};
		assertEquals(16, PadButton.values().length);
		for (int i = 0; i < order.length; i++)
		{
			assertEquals(order[i].label, expected[i], order[i].keyCode);
			assertEquals(order[i], PadButton.forKey(expected[i]));
		}
		assertNull(PadButton.forKey(KeyEvent.VK_W));
		assertNull(PadButton.forKey(KeyEvent.VK_SHIFT));
		assertNull(PadButton.forKey(KeyEvent.VK_UP));
	}

	@Test
	public void theQtCodesAreTheOnesAntiMicroXStoresForThosePadKeys()
	{
		// Qt::Key_F13 = 0x100003c ... Qt::Key_F24 = 0x1000047; Insert / Delete / Home / End from qnamespace.h
		assertEquals(0x100003c, PadButton.A.qtCode);
		assertEquals(0x100003d, PadButton.B.qtCode);
		assertEquals(0x1000047, PadButton.R3.qtCode);
		assertEquals(0x1000006, PadButton.DPAD_UP.qtCode);
		assertEquals(0x1000007, PadButton.DPAD_DOWN.qtCode);
		assertEquals(0x1000010, PadButton.DPAD_LEFT.qtCode);
		assertEquals(0x1000011, PadButton.DPAD_RIGHT.qtCode);
		for (PadButton b : PadButton.values())
		{
			if (b.keyCode >= KeyEvent.VK_F13 && b.keyCode <= KeyEvent.VK_F24)
			{
				assertEquals(b.label, 0x1000030 + 12 + (b.keyCode - KeyEvent.VK_F13), b.qtCode);
				assertEquals("F" + (13 + b.keyCode - KeyEvent.VK_F13), b.keyName);
			}
		}
	}

	@Test
	public void idsAreUniqueAndFindTheirOwner()
	{
		Set<Character> buttonIds = new HashSet<>();
		for (PadButton b : PadButton.values())
		{
			assertTrue(buttonIds.add(b.id));
			assertEquals(b, PadButton.forId(b.id));
			assertTrue("printable", b.id > ' ' && b.id < 127);
		}
		Set<Integer> actionIds = new HashSet<>();
		for (PadAction a : PadAction.values())
		{
			assertTrue(actionIds.add(a.id));
			assertEquals(a, PadAction.forId(a.id));
			assertTrue(a.id >= 0 && a.id < LayoutCode.SAME_AS_BOARD);
		}
		assertNull(PadAction.forId(200));
	}

	@Test
	public void theButtonTrickActionsAreBoardOnly()
	{
		for (PadAction a : Arrays.asList(PadAction.FLIP_BUTTON, PadAction.GRAB_BUTTON, PadAction.GRIND_BUTTON,
			PadAction.SPIN_ASSIST))
		{
			assertTrue(a.implemented);
			assertTrue(a.allowedIn(PadContext.BOARD));
			assertTrue(a.allowedIn(PadContext.AIR));
			assertFalse(a.allowedIn(PadContext.FOOT));
		}
		// their layout-code ids never change
		assertEquals(18, PadAction.FLIP_BUTTON.id);
		assertEquals(19, PadAction.GRAB_BUTTON.id);
		assertEquals(20, PadAction.GRIND_BUTTON.id);
		assertEquals(21, PadAction.SPIN_ASSIST.id);
		assertTrue(PadAction.NONE.allowedIn(PadContext.FOOT));
		assertTrue(PadAction.PUSH.allowedIn(PadContext.AIR));
		assertFalse(PadAction.PUSH.allowedIn(PadContext.FOOT));
		assertFalse(PadAction.JUMP.allowedIn(PadContext.BOARD));
		assertTrue(PadAction.BOARD_TOGGLE.allowedIn(PadContext.FOOT));
	}

	@Test
	public void skate3IsTheOldPadLayout()
	{
		PadPreset p = PadPreset.skate3();
		assertEquals(PadAction.PUSH, p.action(PadButton.A, PadContext.BOARD));
		assertEquals(PadAction.SPRINT, p.action(PadButton.A, PadContext.FOOT));
		assertEquals(PadAction.PUSH, p.action(PadButton.X, PadContext.BOARD));
		assertEquals(PadAction.JUMP, p.action(PadButton.X, PadContext.FOOT));
		assertEquals(PadAction.BRAKE, p.action(PadButton.B, PadContext.BOARD));
		assertEquals(PadAction.NONE, p.action(PadButton.B, PadContext.FOOT));
		assertEquals(PadAction.BOARD_TOGGLE, p.action(PadButton.Y, PadContext.BOARD));
		assertEquals(PadAction.BOARD_TOGGLE, p.action(PadButton.Y, PadContext.FOOT));
		for (PadButton b : new PadButton[]{PadButton.LB, PadButton.RB})
		{
			assertEquals(PadAction.HARD_MODIFIER, p.action(b, PadContext.BOARD));
			assertEquals(PadAction.SPRINT, p.action(b, PadContext.FOOT));
		}
		assertEquals(PadAction.GRAB_LEFT, p.action(PadButton.LT, PadContext.BOARD));
		assertEquals(PadAction.GRAB_RIGHT, p.action(PadButton.RT, PadContext.BOARD));
		assertEquals(PadAction.DROP_PICKUP, p.action(PadButton.LT, PadContext.FOOT));
		assertEquals(PadAction.DROP_PICKUP, p.action(PadButton.RT, PadContext.FOOT));
		assertEquals(PadAction.RESET, p.action(PadButton.BACK, PadContext.FOOT));
		assertEquals(PadAction.STOP, p.action(PadButton.START, PadContext.BOARD));
		assertEquals(PadAction.CONTROLS_CARD, p.action(PadButton.DPAD_UP, PadContext.FOOT));
		for (PadButton b : new PadButton[]{PadButton.L3, PadButton.R3, PadButton.DPAD_DOWN, PadButton.DPAD_LEFT,
			PadButton.DPAD_RIGHT})
		{
			for (PadContext c : PadContext.values())
			{
				assertEquals(PadAction.NONE, p.action(b, c));
			}
		}
		assertNull(p.problem());
	}

	@Test
	public void inTheAirAButtonDoesItsBoardActionUnlessItHasItsOwn()
	{
		PadPreset p = PadPreset.skate3();
		assertEquals(PadAction.GRAB_LEFT, p.action(PadButton.LT, PadContext.AIR));
		PadPreset q = p.with(PadButton.LT, PadContext.AIR, PadAction.OLLIE);
		assertEquals(PadAction.OLLIE, q.action(PadButton.LT, PadContext.AIR));
		assertEquals(PadAction.GRAB_LEFT, q.action(PadButton.LT, PadContext.BOARD));
		// setting the air action back to the board one drops it
		assertEquals(p, q.with(PadButton.LT, PadContext.AIR, PadAction.GRAB_LEFT));
	}

	@Test
	public void withMakesAChangedCopyAndBindingNothingRemovesTheButton()
	{
		PadPreset p = PadPreset.skate3();
		PadPreset q = p.with(PadButton.LB, PadContext.BOARD, PadAction.BRAKE);
		assertEquals(PadAction.HARD_MODIFIER, p.action(PadButton.LB, PadContext.BOARD));
		assertEquals(PadAction.BRAKE, q.action(PadButton.LB, PadContext.BOARD));
		assertNotEquals(p, q);
		PadPreset cleared = new PadPreset().with(PadButton.A, PadContext.BOARD, PadAction.PUSH)
			.with(PadButton.A, PadContext.BOARD, PadAction.NONE);
		assertEquals(new PadPreset(), cleared);
	}

	@Test
	public void buttonsForListsEveryButtonOfAnAction()
	{
		PadPreset p = PadPreset.skate3();
		assertEquals(Arrays.asList(PadButton.A, PadButton.X), p.buttonsFor(PadAction.PUSH, PadContext.BOARD));
		assertEquals(Arrays.asList(PadButton.A, PadButton.LB, PadButton.RB),
			p.buttonsFor(PadAction.SPRINT, PadContext.FOOT));
		assertTrue(p.buttonsFor(PadAction.OLLIE, PadContext.BOARD).isEmpty());
	}

	@Test
	public void aBindingInTheWrongPlaceIsAProblem()
	{
		assertNotNull(new PadPreset().with(PadButton.A, PadContext.FOOT, PadAction.PUSH).problem());
		assertNotNull(new PadPreset().with(PadButton.A, PadContext.BOARD, PadAction.JUMP).problem());
		assertNotNull(new PadPreset().with(PadButton.A, PadContext.FOOT, PadAction.FLIP_BUTTON).problem());
		assertNull(new PadPreset().with(PadButton.A, PadContext.BOARD, PadAction.FLIP_BUTTON).problem());
		assertNull(new PadPreset().with(PadButton.A, PadContext.FOOT, PadAction.CAMERA_ORBIT).problem());
	}

	@Test
	public void thawIsTheTonyHawkLayout()
	{
		PadPreset p = PadPreset.thaw();
		assertNull(p.problem());
		assertTrue(p.buttonTricks());
		assertFalse(PadPreset.skate3().buttonTricks());
		assertFalse(new PadPreset().buttonTricks());
		assertEquals(PadAction.OLLIE, p.action(PadButton.A, PadContext.BOARD));
		assertEquals(PadAction.OLLIE, p.action(PadButton.A, PadContext.AIR));
		assertEquals(PadAction.JUMP, p.action(PadButton.A, PadContext.FOOT));
		assertEquals(PadAction.FLIP_BUTTON, p.action(PadButton.X, PadContext.AIR));
		assertEquals(PadAction.FLIP_BUTTON, p.action(PadButton.X, PadContext.BOARD));
		assertEquals(PadAction.NONE, p.action(PadButton.X, PadContext.FOOT));
		assertEquals(PadAction.GRAB_BUTTON, p.action(PadButton.B, PadContext.AIR));
		assertEquals(PadAction.DROP_PICKUP, p.action(PadButton.B, PadContext.FOOT));
		assertEquals(PadAction.GRIND_BUTTON, p.action(PadButton.Y, PadContext.BOARD));
		assertEquals(PadAction.GRIND_BUTTON, p.action(PadButton.Y, PadContext.AIR));
		assertEquals(PadAction.BOARD_TOGGLE, p.action(PadButton.Y, PadContext.FOOT));
		for (PadButton b : Arrays.asList(PadButton.LB, PadButton.RB))
		{
			assertEquals(PadAction.SPIN_ASSIST, p.action(b, PadContext.BOARD));
			assertEquals(PadAction.SPIN_ASSIST, p.action(b, PadContext.AIR));
			assertEquals(PadAction.SPRINT, p.action(b, PadContext.FOOT));
		}
		assertEquals(PadAction.RESET, p.action(PadButton.BACK, PadContext.BOARD));
		assertEquals(PadAction.STOP, p.action(PadButton.START, PadContext.FOOT));
		assertEquals(PadAction.CONTROLS_CARD, p.action(PadButton.DPAD_UP, PadContext.BOARD));
		assertEquals(PadAction.NONE, p.action(PadButton.LT, PadContext.BOARD));
		// a layout code round-trips it, and a custom layout with a grab button plays with button tricks
		assertEquals(p, LayoutCode.decode(LayoutCode.encode(p)).preset);
		assertTrue(new PadPreset().with(PadButton.B, PadContext.BOARD, PadAction.GRAB_BUTTON).buttonTricks());
	}

	@Test
	public void thePresetSettingPicksTheLayout()
	{
		assertEquals(PadPreset.thaw(), PadPresets.resolve(com.gielinorskate.GielinorSkateConfig.ControllerPreset.THAW,
			""));
		assertEquals(PadPreset.skate3(), PadPresets.resolve(
			com.gielinorskate.GielinorSkateConfig.ControllerPreset.SKATE_3, LayoutCode.encode(PadPreset.thaw())));
		assertFalse(PadPresets.customIsBroken(com.gielinorskate.GielinorSkateConfig.ControllerPreset.THAW, "junk"));
	}
}
