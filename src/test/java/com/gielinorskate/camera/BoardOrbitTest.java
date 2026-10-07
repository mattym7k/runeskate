package com.gielinorskate.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gielinorskate.physics.Angles;
import com.gielinorskate.physics.SkaterState;
import org.junit.Test;

/** The board camera turned by hand, and its ease back behind the skater once the stick is let go. */
public class BoardOrbitTest
{
	private static final float DT = 1f / 50f;

	@Test
	public void theTurnHoldsWhileTheStickMovesAndGoesAfterIdle()
	{
		BoardOrbit o = new BoardOrbit();
		assertEquals(0f, o.offset(), 0f);
		o.turn(0.5f);
		o.update(DT);
		assertEquals(0.5f, o.offset(), 1e-6f);
		// held still just short of the idle time: still turned
		for (int i = 0; i < Math.round(BoardOrbit.IDLE_SECONDS / DT) - 3; i++)
		{
			o.update(DT);
		}
		assertEquals(0.5f, o.offset(), 1e-6f);
		// a new turn restarts the idle time
		o.turn(0.25f);
		for (int i = 0; i < 50; i++)
		{
			o.update(DT);
		}
		assertEquals(0.75f, o.offset(), 1e-6f);
		for (int i = 0; i < Math.round(BoardOrbit.IDLE_SECONDS / DT); i++)
		{
			o.update(DT);
		}
		assertEquals(0f, o.offset(), 0f);
	}

	@Test
	public void resetDropsTheTurn()
	{
		BoardOrbit o = new BoardOrbit();
		o.turn(1f);
		o.reset();
		assertEquals(0f, o.offset(), 0f);
		o.turn(0f);
		o.update(DT);
		assertEquals(0f, o.offset(), 0f);
	}

	@Test
	public void theChaseCameraEasesBackBehindTheSkater()
	{
		// as the session drives it: the stick turns the rig and the offset, the rig sits behind heading + offset
		CameraRig rig = new CameraRig();
		BoardOrbit o = new BoardOrbit();
		float heading = 0f;
		rig.reset(0, 0, 0, heading);
		o.turn(1f);
		rig.orbit(1f);
		for (int i = 0; i < 50; i++)
		{
			o.update(DT);
			rig.update(0, 0, 0, heading + o.offset(), 300f, SkaterState.ROLLING, 0f, DT);
		}
		assertEquals("held where the stick put it", 1f, rig.getYaw(), 1e-3f);
		float last = rig.getYaw();
		boolean eased = false;
		for (int i = 0; i < 150; i++)
		{
			o.update(DT);
			rig.update(0, 0, 0, heading + o.offset(), 300f, SkaterState.ROLLING, 0f, DT);
			float step = Math.abs(Angles.wrap(rig.getYaw() - last));
			assertTrue("no snap: " + step, step <= CameraRig.MAX_YAW_RATE * DT + 1e-4f);
			eased |= step > 0f && step < 0.5f;
			last = rig.getYaw();
		}
		assertTrue(eased);
		assertEquals("back behind the skater", heading, rig.getYaw(), 1e-2f);
	}
}
