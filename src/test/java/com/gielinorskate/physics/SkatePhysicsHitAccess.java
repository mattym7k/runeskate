package com.gielinorskate.physics;

/** Test helper: rides a skater into a world and reports what it hit. */
public final class SkatePhysicsHitAccess
{
	private SkatePhysicsHitAccess()
	{
	}

	/** Rolls north at 300 u/s from (x, y) for up to 2 s; the label of the first blocker hit, or null. */
	public static String rideNorthInto(CollisionWorld w, float x, float y)
	{
		SkatePhysics p = new SkatePhysics(new SkateTuning(), w, x, y, 0f);
		p.setSpeed(300f);
		SkateInput in = new SkateInput();
		for (int i = 0; i < 100 && !p.hasHit(); i++)
		{
			p.step(0.02f, in);
		}
		String label = p.hasHit() ? p.getHitLabel() : null;
		p.clearHit();
		return label;
	}
}
