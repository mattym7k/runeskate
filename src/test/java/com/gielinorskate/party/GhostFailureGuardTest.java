package com.gielinorskate.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GhostFailureGuardTest
{
	private final GhostFailureGuard guard = new GhostFailureGuard();
	private final List<RuntimeException> logged = new ArrayList<>();
	private int frames;
	private int cleanups;

	private void throwingFrame()
	{
		frames++;
		throw new IllegalStateException("ghost boom");
	}

	@Test
	public void healthyFramesRunEveryTime()
	{
		for (int i = 0; i < 3; i++)
		{
			guard.run(() -> frames++, () -> cleanups++, logged::add);
		}
		assertEquals(3, frames);
		assertEquals(0, cleanups);
		assertTrue(logged.isEmpty());
		assertFalse(guard.isDisabled());
	}

	@Test
	public void aThrowingFrameNeverEscapesAndDisablesGhostsForTheSession()
	{
		// would fail the test (and in the session, end skating) if the exception escaped
		guard.run(this::throwingFrame, () -> cleanups++, logged::add);
		guard.run(this::throwingFrame, () -> cleanups++, logged::add);
		guard.run(this::throwingFrame, () -> cleanups++, logged::add);
		assertEquals("later frames are skipped", 1, frames);
		assertEquals("ghosts despawned once", 1, cleanups);
		assertEquals("logged once", 1, logged.size());
		assertTrue(guard.isDisabled());
	}

	@Test
	public void aThrowingCleanupIsSwallowedToo()
	{
		guard.run(this::throwingFrame, () ->
		{
			throw new IllegalStateException("despawn boom");
		}, logged::add);
		assertTrue(guard.isDisabled());
		assertEquals(1, logged.size());
	}

	@Test
	public void theNextSessionReenablesGhosts()
	{
		guard.run(this::throwingFrame, () -> cleanups++, logged::add);
		guard.reset();
		assertFalse(guard.isDisabled());
		guard.run(() -> frames++, () -> cleanups++, logged::add);
		assertEquals(2, frames);
	}
}
