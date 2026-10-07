package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardDesigns;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import org.junit.Test;

/** Custom designs in ghost updates: "C:" and the shared image's 8-hex hash, only while sharing them. */
public class GhostDesignRefTest
{
	private final BoardDesigns designs = BoardDesigns.bundled();
	private final BoardDesign grip = BoardDesign.custom("CUSTOM_0A1B2C3D", "Mine", DesignPart.GRIP, 1);
	private final BoardDesign deck = BoardDesign.custom("CUSTOM_0A1B2C3E", "Mine", DesignPart.DECK, 1);
	private final BoardDesign wheels = BoardDesign.custom("CUSTOM_0A1B2C3F", "Mine", DesignPart.WHEELS, 1);
	private final BoardLook mine = new BoardLook(grip, deck, wheels);

	/** Sharing: each custom design has the hash of its shared image. */
	private static String hashOf(BoardDesign d)
	{
		switch (d.part)
		{
			case GRIP:
				return "0123abcd";
			case DECK:
				return "4567ef01";
			default:
				return "89abcdef";
		}
	}

	@Test
	public void sharedCustomDesignsGoAsTheirHash()
	{
		assertEquals("C:4567ef01", GhostCodec.deckWire(mine, GhostDesignRefTest::hashOf));
		assertEquals("C:0123abcd.C:89abcdef", GhostCodec.lookWire(mine, GhostDesignRefTest::hashOf));
	}

	@Test
	public void withoutSharingTheyGoAsTheDefaults()
	{
		assertNull(GhostCodec.deckWire(mine, null));
		assertEquals(GhostCodec.lookWire(BoardLook.defaults(designs)), GhostCodec.lookWire(mine, null));
		// a design whose shared image isn't ready yet (or failed) goes as the default
		assertNull(GhostCodec.deckWire(mine, d -> null));
		assertEquals(GhostCodec.lookWire(BoardLook.defaults(designs)), GhostCodec.lookWire(mine, d -> null));
		// junk from the hash source never reaches the wire
		assertNull(GhostCodec.deckWire(mine, d -> "not-a-hash"));
	}

	@Test
	public void shippedDesignsAreUnchangedWhileSharing()
	{
		BoardLook sara = new BoardLook(designs.byId("GRIP_SARADOMIN"), designs.byId("SARADOMIN"),
			designs.byId("WHEELS_SARADOMIN"));
		assertEquals("SARADOMIN", GhostCodec.deckWire(sara, GhostDesignRefTest::hashOf));
		assertEquals("SARADOMIN.SARADOMIN", GhostCodec.lookWire(sara, GhostDesignRefTest::hashOf));
		assertNull(GhostCodec.deckWire(BoardLook.defaults(designs), GhostDesignRefTest::hashOf));
	}

	@Test
	public void receiversResolveCompleteDesignsAndShowDefaultsOtherwise()
	{
		BoardDesign theirs = BoardDesign.custom("PARTY_1_4567ef01", "Theirs", DesignPart.DECK, 0);
		GhostCodec.CustomDesigns ready = (part, hash) -> part == DesignPart.DECK && hash.equals("4567ef01")
			? theirs : null;
		BoardLook got = GhostCodec.decodeLook("C:4567ef01", "C:0123abcd.C:89abcdef", null, designs, ready);
		assertSame(theirs, got.deck);
		// not complete (or not wanted): the part's default
		assertEquals(designs.defaultFor(DesignPart.GRIP), got.grip);
		assertEquals(designs.defaultFor(DesignPart.WHEELS), got.wheels);
		// a resolver answering with another part's design is not believed
		GhostCodec.CustomDesigns wrong = (part, hash) -> theirs;
		assertEquals(designs.defaultFor(DesignPart.GRIP),
			GhostCodec.decodeLook(null, "C:0123abcd.C:89abcdef", null, designs, wrong).grip);
	}

	@Test
	public void junkReferencesAreDefaults()
	{
		GhostCodec.CustomDesigns any = (part, hash) -> BoardDesign.custom("PARTY_X", "x", part, 0);
		for (String junk : new String[]{"C:", "C:0123abc", "C:0123abcde", "C:0123ABCD", "C:0123abcg", "c:0123abcd"})
		{
			assertEquals(junk, designs.defaultFor(DesignPart.DECK),
				GhostCodec.decodeLook(junk, null, null, designs, any).deck);
		}
		assertNull(GhostCodec.customHash("C:0123ABCD"));
		assertEquals("0123abcd", GhostCodec.customHash("C:0123abcd"));
	}

	@Test
	public void olderClientsSeeUnknownNamesAndDrawDefaults()
	{
		// an older client resolves names only through BoardDesigns.fromWire: a custom reference is unknown there
		for (DesignPart p : DesignPart.values())
		{
			assertEquals(designs.defaultFor(p), designs.fromWire(p, "C:0123abcd"));
		}
		// and its grip-and-wheels limit (two shipped names and a dot) is below a fully custom field, which it then
		// reads as junk: both defaults
		int oldLimit = 2 * BoardDesigns.WIRE_MAX + 1;
		assertTrue(GhostCodec.lookWire(mine, GhostDesignRefTest::hashOf).length() > oldLimit);
		// with one custom part the field may fit (then the part is unknown there) or not (then both are defaults)
		String half = GhostCodec.lookWire(mine.with(designs.defaultFor(DesignPart.WHEELS)), GhostDesignRefTest::hashOf);
		assertEquals(designs.defaultFor(DesignPart.GRIP), designs.fromWire(DesignPart.GRIP, half.substring(0,
			half.indexOf('.'))));
		// this version without a resolver (as when showing others' designs is off) draws defaults too
		assertEquals(BoardLook.defaults(designs), GhostCodec.decodeLook("C:4567ef01", "C:0123abcd.C:89abcdef", null,
			designs, null));
	}
}
