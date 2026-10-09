package com.gielinorskate.render;

import static org.junit.Assert.assertEquals;

import com.gielinorskate.tricks.Trick;
import org.junit.Test;

/** The bundled grab pose table (grabpose in text/data.properties) loads each plain grab's row exactly. */
public class GrabPoseTableTest
{
	@Test
	public void everyGrabLoadsItsRow()
	{
		row(Trick.INDY, -0.1f, -0.18f, 0f, 0.25f, -6f, -13f, -14f);
		row(Trick.MELON, 0.1f, 0.18f, 0f, -0.25f, 6f, 13f, -14f);
		row(Trick.MUTE, 0.15f, -0.2f, -0.08f, 0.22f, 18f, -13f, -14f);
		row(Trick.STALEFISH, -0.15f, 0.22f, 0.08f, -0.22f, -18f, 13f, -14f);
		row(Trick.NOSEGRAB, 0.45f, 0f, -0.3f, 0f, 43f, 0f, -20f);
		row(Trick.TAILGRAB, -0.45f, 0f, 0.3f, 0f, -43f, 0f, -20f);
		row(Trick.CRAIL, 0.4f, -0.1f, -0.25f, 0.12f, 40f, -6f, -20f);
	}

	private static void row(Trick g, float lean, float roll, float pitch, float boardRoll, float along, float across,
		float boardY)
	{
		assertEquals(lean, GrabPose.torsoLean(g), 0f);
		assertEquals(roll, GrabPose.bodyRoll(g), 0f);
		assertEquals(pitch, GrabPose.boardPitch(g), 0f);
		assertEquals(boardRoll, GrabPose.boardRoll(g), 0f);
		assertEquals(along, GrabPose.grabAlong(g), 0f);
		assertEquals(across, GrabPose.grabAcross(g), 0f);
		assertEquals(boardY, GrabPose.grabBoardY(g), 0f);
	}
}
