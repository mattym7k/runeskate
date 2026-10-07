package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.GielinorSkateConfig;
import java.awt.event.KeyEvent;
import com.gielinorskate.controller.PadButton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The shipped AntiMicroX profile: well-formed, every binding exactly one key or mouse direction (no macros, turbo,
 * set changes or multi-slot sequences), every button sends its own pad key ({@link PadButton}), the left stick the
 * arrows and the right stick the mouse.
 */
public class AntiMicroXProfileTest
{
	/** AntiMicroX stores keys as Qt key codes. */
	private static final String CRLF = "\r\n";
	private static final String LF = "\n";
	private static final int QT_UP = 0x1000013;
	private static final int QT_DOWN = 0x1000015;
	private static final int QT_LEFT = 0x1000012;
	private static final int QT_RIGHT = 0x1000014;
	/** Qt::Key_F1; F2 to F35 follow it. */
	private static final int QT_F1 = 0x1000030;

	/**
	 * The Qt key code AntiMicroX stores for a Java key code: letters, digits and B / F / ... are the same; the
	 * function keys are Qt::Key_F1 + n (on Windows AntiMicroX sends them as VK_F1 + n, which Java reads as
	 * KeyEvent.VK_F1 + n for F1 to F12 and VK_F13 + n from F13).
	 */
	private static int qt(int javaCode)
	{
		if (javaCode >= KeyEvent.VK_F13 && javaCode <= KeyEvent.VK_F24)
		{
			return QT_F1 + 12 + (javaCode - KeyEvent.VK_F13);
		}
		if (javaCode >= KeyEvent.VK_F1 && javaCode <= KeyEvent.VK_F12)
		{
			return QT_F1 + (javaCode - KeyEvent.VK_F1);
		}
		assertTrue("a plain ASCII key: " + javaCode, (javaCode >= KeyEvent.VK_0 && javaCode <= KeyEvent.VK_9)
			|| (javaCode >= KeyEvent.VK_A && javaCode <= KeyEvent.VK_Z));
		return javaCode;
	}

	private static Document profile() throws Exception
	{
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setExpandEntityReferences(false);
		f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		return f.newDocumentBuilder().parse(new File("controller/RuneSkate.amgp"));
	}

	private static String text(Element e, String child)
	{
		NodeList l = e.getElementsByTagName(child);
		return l.getLength() == 0 ? null : l.item(0).getTextContent().trim();
	}

	/** "stick1/3", "button2", "trigger5", "dpad1/1" -> the one slot's code (keyboard) or mouse direction. */
	private static Map<String, String> bindings(Document d)
	{
		Map<String, String> out = new HashMap<>();
		NodeList slotLists = d.getElementsByTagName("slots");
		for (int i = 0; i < slotLists.getLength(); i++)
		{
			Element slots = (Element) slotLists.item(i);
			Element owner = (Element) slots.getParentNode();
			NodeList slotNodes = slots.getElementsByTagName("slot");
			assertEquals("one slot per binding: " + owner.getTagName(), 1, slotNodes.getLength());
			Element slot = (Element) slotNodes.item(0);
			String mode = text(slot, "mode");
			assertTrue(mode, mode.equals("keyboard") || mode.equals("mousemovement"));
			Element parent = (Element) owner.getParentNode();
			String key;
			switch (owner.getTagName())
			{
				case "stickbutton":
					key = "stick" + parent.getAttribute("index") + "/" + owner.getAttribute("index");
					break;
				case "dpadbutton":
					key = "dpad" + parent.getAttribute("index") + "/" + owner.getAttribute("index");
					break;
				case "triggerbutton":
					assertEquals("2", owner.getAttribute("index"));
					key = "trigger" + parent.getAttribute("index");
					break;
				default:
					assertEquals("button", owner.getTagName());
					key = "button" + owner.getAttribute("index");
					break;
			}
			assertFalse("bound twice: " + key, out.containsKey(key));
			out.put(key, mode.charAt(0) + ":" + Integer.decode(text(slot, "code")));
		}
		return out;
	}

	private static String key(int code)
	{
		return "k:" + code;
	}

	@Test
	public void theProfileIsAGameControllerProfileWithOneSet() throws Exception
	{
		Document d = profile();
		assertEquals("gamecontroller", d.getDocumentElement().getTagName());
		assertEquals(1, d.getElementsByTagName("set").getLength());
	}

	@Test
	public void everyBindingIsOneToOneWithNoMacrosOrTurbo() throws Exception
	{
		Document d = profile();
		for (String banned : new String[]{"useturbo", "turbointerval", "turbomode", "setselect", "cycleresetactive",
			"toggle"})
		{
			assertEquals(banned, 0, d.getElementsByTagName(banned).getLength());
		}
		NodeList modes = d.getElementsByTagName("mode");
		for (int i = 0; i < modes.getLength(); i++)
		{
			String m = modes.item(i).getTextContent().trim();
			assertFalse(m, m.equals("mix") || m.equals("macro") || m.equals("pause") || m.equals("hold")
				|| m.equals("cycle") || m.equals("distance") || m.equals("delay") || m.equals("execute")
				|| m.equals("textentry") || m.equals("setchange"));
		}
		bindings(d);
	}

	/** Where each pad button sits in an AntiMicroX game controller profile. */
	private static final Map<PadButton, String> SLOTS = new EnumMap<>(PadButton.class);

	static
	{
		// SDL buttons, 1-based: A, B, X, Y, Back, (Guide), Start, L3, R3, LB, RB
		SLOTS.put(PadButton.A, "button1");
		SLOTS.put(PadButton.B, "button2");
		SLOTS.put(PadButton.X, "button3");
		SLOTS.put(PadButton.Y, "button4");
		SLOTS.put(PadButton.BACK, "button5");
		SLOTS.put(PadButton.START, "button7");
		SLOTS.put(PadButton.L3, "button8");
		SLOTS.put(PadButton.R3, "button9");
		SLOTS.put(PadButton.LB, "button10");
		SLOTS.put(PadButton.RB, "button11");
		// triggers: SDL axes 4 / 5, 1-based
		SLOTS.put(PadButton.LT, "trigger5");
		SLOTS.put(PadButton.RT, "trigger6");
		// the d-pad's directions: up 1, right 2, down 4, left 8
		SLOTS.put(PadButton.DPAD_UP, "dpad1/1");
		SLOTS.put(PadButton.DPAD_RIGHT, "dpad1/2");
		SLOTS.put(PadButton.DPAD_DOWN, "dpad1/4");
		SLOTS.put(PadButton.DPAD_LEFT, "dpad1/8");
	}

	@Test
	public void everyButtonSendsItsOwnPadKeyAndTheSticksAreArrowsAndMouse() throws Exception
	{
		Map<String, String> b = bindings(profile());
		assertEquals(PadButton.values().length, SLOTS.size());
		for (PadButton p : PadButton.values())
		{
			assertEquals(p.label, key(p.qtCode), b.get(SLOTS.get(p)));
		}
		// the F keys are Qt::Key_F1 + n, which AntiMicroX sends on Windows as VK_F1 + n
		assertEquals(key(qt(KeyEvent.VK_F13)), b.get("button1"));
		assertEquals(key(qt(KeyEvent.VK_F24)), b.get("button9"));
		// left stick: four-way arrow keys
		assertEquals(key(QT_UP), b.get("stick1/1"));
		assertEquals(key(QT_RIGHT), b.get("stick1/3"));
		assertEquals(key(QT_DOWN), b.get("stick1/5"));
		assertEquals(key(QT_LEFT), b.get("stick1/7"));
		// right stick: mouse up / down / left / right (AntiMicroX directions 1-4)
		assertEquals("m:1", b.get("stick2/1"));
		assertEquals("m:2", b.get("stick2/5"));
		assertEquals("m:3", b.get("stick2/7"));
		assertEquals("m:4", b.get("stick2/3"));
		assertEquals(16 + 4 + 4, b.size());
	}

	@Test
	public void theLeftStickIsFourWaySoASteerIsNeverALean() throws Exception
	{
		Document d = profile();
		NodeList sticks = d.getElementsByTagName("stick");
		for (int i = 0; i < sticks.getLength(); i++)
		{
			Element s = (Element) sticks.item(i);
			if ("1".equals(s.getAttribute("index")))
			{
				assertEquals("four-way", text(s, "mode"));
				return;
			}
		}
		throw new AssertionError("no left stick");
	}

	@Test
	public void theArrowsAreTheDefaultLeanKeysAndSteerAsAAndD()
	{
		GielinorSkateConfig defaults = new GielinorSkateConfig()
		{
		};
		assertEquals(KeyEvent.VK_UP, defaults.leanForwardKey().getKeyCode());
		assertEquals(KeyEvent.VK_DOWN, defaults.leanBackKey().getKeyCode());
	}

	@Test
	public void noPadKeyIsADefaultKeySettingOrASkateControl()
	{
		GielinorSkateConfig defaults = new GielinorSkateConfig()
		{
		};
		for (PadButton p : PadButton.values())
		{
			assertTrue(p.label, InputController.isPadKeyFree(p.keyCode, defaults.toggleKey().getKeyCode(),
				defaults.manualKey().getKeyCode(), defaults.leanForwardKey().getKeyCode(),
				defaults.leanBackKey().getKeyCode(), defaults.brakeKey().getKeyCode(), defaults.boardKey().getKeyCode(),
				KeyEvent.VK_C));
			assertFalse(p.label, KeyboardTricks.isTrickKey(p.keyCode));
			// a real key nothing in skate mode reads by itself
			assertTrue(p.label, InputController.isValidManualKey(p.keyCode));
			assertFalse(p.label, p.keyCode == KeyEvent.VK_SPACE);
		}
	}

	@Test
	public void thePanelSavesTheSameProfileAsTheControllerFolder() throws Exception
	{
		String folder = new String(Files.readAllBytes(Paths.get("controller/RuneSkate.amgp")), StandardCharsets.UTF_8);
		String resource = new String(Files.readAllBytes(
			Paths.get("src/main/resources/com/gielinorskate/RuneSkate.amgp")), StandardCharsets.UTF_8);
		assertEquals(folder.replace(CRLF, LF), resource.replace(CRLF, LF));
	}

	@Test
	public void theRightStickMatchesTheSpeedsTheDetectionIsTunedFor() throws Exception
	{
		Document d = profile();
		NodeList sticks = d.getElementsByTagName("stick");
		Element right = null;
		for (int i = 0; i < sticks.getLength(); i++)
		{
			Element s = (Element) sticks.item(i);
			if ("2".equals(s.getAttribute("index")))
			{
				right = s;
			}
		}
		NodeList buttons = right.getElementsByTagName("stickbutton");
		assertEquals(8, buttons.getLength());
		for (int i = 0; i < buttons.getLength(); i++)
		{
			Element sb = (Element) buttons.item(i);
			assertEquals("60", text(sb, "mousespeedx"));
			assertEquals("60", text(sb, "mousespeedy"));
			assertEquals("cursor", text(sb, "mousemode"));
			assertEquals("quadratic", text(sb, "mouseacceleration"));
		}
		// full tilt: speed x AntiMicroX's 20 px/s per unit = 1.2 px/ms, comfortably a flick (0.5) and fast (0.35)
		float fullTilt = 60 * 20 / 1000f;
		assertTrue(fullTilt >= 2 * GestureRecognizer.FLICK_MIN_SPEED);
		// a small tilt (a third of the way, quadratic) stays slow enough to be a manual
		assertTrue(fullTilt * (1 / 3f) * (1 / 3f) < ControllerStick.FAST_SPEED);
	}
}
