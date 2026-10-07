package com.gielinorskate.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Near misses: strokes that look like a trick attempt but did not make one say why, once per stroke. */
public class GestureNearMissTest
{
	@Test
	public void aCleanFlickHasNoNearMiss()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50);
		r.move(100, 195, 120);
		r.end();
		assertNotNull(r.poll());
		assertNull(r.pollNearMiss());
	}

	@Test
	public void aSlowDriftBackAfterTheWindUpIsTooSlow()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 25, 10); // wound up
		// creeping back up long after the hand left the extreme: the missed-flick release
		r.move(0, 20, 200);
		r.move(0, 15, 400);
		r.move(0, 10, 600);
		assertNull(r.poll());
		assertEquals(NearMiss.TOO_SLOW, r.pollNearMiss());
		assertNull("polled once", r.pollNearMiss());
	}

	@Test
	public void farButSlowThenReleasedIsTooSlow()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(0, 0, 0);
		r.move(0, 25, 10);
		r.move(40, 25, 500); // sideways 40 px, far too slowly to flick
		r.end();
		assertNull(r.poll());
		assertEquals(NearMiss.TOO_SLOW, r.pollNearMiss());
	}

	@Test
	public void aShortFlickThenReleaseIsTooShort()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50); // wound up
		r.move(100, 215, 70); // 15 px up, less than the 30 px flick
		r.end();
		assertNull(r.poll());
		assertEquals(NearMiss.TOO_SHORT, r.pollNearMiss());
	}

	@Test
	public void holdingTheWindUpAndLettingGoIsJustACrouch()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 200, 0);
		r.move(100, 230, 50);
		r.move(101, 232, 300);
		r.end();
		assertNull(r.pollNearMiss());
	}

	@Test
	public void aSidewaysFlickWithNoPullDownSaysPullDownFirst()
	{
		GestureRecognizer r = new GestureRecognizer();
		r.begin(100, 100, 0);
		r.move(120, 102, 20);
		r.move(140, 101, 40);
		r.move(170, 103, 60);
		r.end();
		assertNull(r.poll());
		assertEquals(NearMiss.NO_WIND_UP, r.pollNearMiss());
	}
}
