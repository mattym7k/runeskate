package com.gielinorskate.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.gielinorskate.physics.Contact;
import com.gielinorskate.physics.SkatePhysicsHitAccess;
import org.junit.Test;

/** ::skateboxes names what the skater ran into: each blocker carries a name, posted once. */
public class HitLogTest
{
	private static final float T = GridCollisionWorld.TILE;

	@Test
	public void labelsNameTheObjectAndItsKind()
	{
		assertEquals("Rocks (game object)", HitLog.label("Rocks", 11, "game object"));
		assertEquals("Object 1234 (wall)", HitLog.label("null", 1234, "wall"));
		assertEquals("Object 7 (ground object)", HitLog.label(null, 7, "ground object"));
	}

	@Test
	public void eachBlockerIsReportedOnceUntilReset()
	{
		HitLog log = new HitLog();
		assertEquals("Hit: Rocks (game object)", log.report(3, "Rocks (game object)"));
		assertNull(log.report(3, "Rocks (game object)"));
		// another rock of the same name is another object
		assertEquals("Hit: Rocks (game object)", log.report(4, "Rocks (game object)"));
		assertEquals("Hit: Steep ground", log.report(Contact.UNKNOWN_BOX, "Steep ground"));
		assertNull(log.report(Contact.UNKNOWN_BOX, "Steep ground"));
		log.reset();
		assertEquals("Hit: Rocks (game object)", log.report(3, "Rocks (game object)"));
	}

	@Test
	public void contactsCarryTheBoxNumberAndName()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.addObjectBlocker(5.5f * T, 5.5f * T, new float[]{-30, 30, -30, 30}, 0, 200f, false, "Rocks (game object)");
		w.addObjectBlocker(2.5f * T, 2.5f * T, new float[]{-30, 30, -30, 30}, 0, 200f, false, "Crate (game object)");
		Contact c = new Contact();
		assertTrue(w.contact(2.5f * T + 35, 2.5f * T, 12f, 0f, 24f, c));
		assertEquals("Crate (game object)", c.label);
		assertEquals(1, c.box);
		assertTrue(w.contact(5.5f * T - 35, 5.5f * T, 12f, 0f, 24f, c));
		assertEquals("Rocks (game object)", c.label);
		assertEquals(0, c.box);
	}

	@Test
	public void wallEdgesAreNamedFromEitherSideAndNumberedAfterTheObjects()
	{
		GridCollisionWorld w = new GridCollisionWorld(10);
		w.addObjectBlocker(8.5f * T, 8.5f * T, new float[]{-30, 30, -30, 30}, 0, 200f, false, "Rocks (game object)");
		// a fence on (5, 5)'s west edge, flagged on both tiles as the game does; only (5, 5) has the wall object
		w.setTile(5, 5, GridCollisionWorld.WALL_W, 300f);
		w.setTile(4, 5, GridCollisionWorld.WALL_E, 300f);
		w.setWallLabel(5, 5, "Fence (wall)");
		Contact c = new Contact();
		assertTrue(w.contact(5f * T + 10, 5.5f * T, 12f, 0f, 24f, c));
		assertEquals("Fence (wall)", c.label);
		assertTrue("numbered after the 1 object box", c.box >= 1);
		Contact other = new Contact();
		assertTrue(w.contact(5f * T - 10, 5.5f * T, 12f, 0f, 24f, other));
		assertEquals("the neighbour's mirrored flag names the same fence", "Fence (wall)", other.label);
		// an edge with no wall object on either side
		w.setTile(1, 1, GridCollisionWorld.WALL_N, 300f);
		assertTrue(w.contact(1.5f * T, 2f * T - 10, 12f, 0f, 24f, c));
		assertEquals(GridCollisionWorld.WALL_EDGE_LABEL, c.label);
	}

	@Test
	public void theSkaterRemembersTheBlockerItRanInto()
	{
		GridCollisionWorld w = new GridCollisionWorld(20);
		w.addObjectBlocker(10.5f * T, 10.5f * T, new float[]{-30, 30, -30, 30}, 0, 300f, false, "Rocks (game object)");
		String hit = SkatePhysicsHitAccess.rideNorthInto(w, 10.5f * T, 10.5f * T - 120);
		assertEquals("Rocks (game object)", hit);
	}
}
