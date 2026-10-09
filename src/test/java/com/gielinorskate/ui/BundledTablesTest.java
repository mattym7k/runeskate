package com.gielinorskate.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.Text;
import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.ui.ControllerGlyphs.Glyph;
import java.awt.Color;
import java.util.Map;
import org.junit.Test;

/** The ui's bundled tables (text/ui.properties) load and name only things that exist. */
public class BundledTablesTest
{
	@Test
	public void theHowTablesNameRealTricks()
	{
		for (String key : new String[]{"guide.how", "guide.padHow", "guide.buttonHow"})
		{
			Map<String, String> table = TrickGuide.table(Text.lines(key));
			assertTrue(key, table.size() > 5);
			table.forEach((name, how) ->
			{
				assertNotNull(Trick.valueOf(name));
				assertTrue(name, !how.isEmpty());
			});
		}
		Map<String, String> diagonals = TrickGuide.table(Text.lines("guide.diagonals"));
		assertEquals(4, diagonals.size());
		diagonals.keySet().forEach(Direction::valueOf);
		assertEquals("{DDOWN}{DRIGHT} (d-pad down-right)", TrickGuide.buttonDirection(Direction.DOWN_RIGHT));
		assertEquals("Land across a rail, turned past square", TrickGuide.how(Trick.LIPSLIDE, null));
		assertEquals("Hold {RS} tilted up a little while rolling", TrickGuide.controllerHow(Trick.MANUAL, null,
			PadPreset.skate3()));
	}

	@Test
	public void thePadTablesNameRealActions()
	{
		TrickGuide.table(Text.lines("pad.does")).keySet()
			.forEach(k -> PadAction.valueOf(k.replace(".foot", "")));
		assertEquals("get on, or call a far board back", PadWords.describe(PadAction.BOARD_TOGGLE, true));
		assertEquals("sprint (hold)", PadWords.describe(PadAction.SPRINT, true));
		assertEquals(PadAction.NONE.label, PadWords.describe(PadAction.NONE, false));
		TrickGuide.table(Text.lines("pad.places")).forEach((name, row) ->
		{
			String[] f = row.split("\\|");
			PadAction.valueOf(f[0]);
			PadContext.valueOf(f[1]);
			// with no buttons bound each falls back to its key
			assertTrue(name, !PadWords.resolve("{@" + name + "}", new PadPreset(), "F").isEmpty());
		});
		assertEquals("the brake key / Q/E / F / Shift", PadWords.resolve("{@brake} / {@grabL}/{@grabR} / {@board} / {@mod}",
			new PadPreset(), "F"));
	}

	@Test
	public void everyGlyphHasItsLettersNameAndColours()
	{
		for (Glyph g : Glyph.values())
		{
			assertNotNull(g.name(), g.label);
			assertTrue(g.name(), !g.spoken.isEmpty());
		}
		assertEquals(new Color(84, 170, 60), Glyph.A.fill);
		assertEquals(Color.WHITE, Glyph.B.letter);
		assertEquals(new Color(212, 58, 48), Glyph.B.fill);
		assertEquals(new Color(40, 112, 214), Glyph.X.fill);
		assertEquals(new Color(236, 190, 30), Glyph.Y.fill);
		assertEquals(new Color(30, 30, 30), Glyph.Y.letter);
		assertNull(Glyph.LB.fill);
		assertEquals("L", Glyph.LS.label);
		assertEquals("R3 (right stick click)", Glyph.R3.spoken);
		assertEquals("", Glyph.DLEFT.label);
		assertEquals("d-pad left", Glyph.DLEFT.spoken);
	}
}
