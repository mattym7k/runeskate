package com.gielinorskate.physics;

/**
 * Result of {@link CollisionWorld#contact}: the deepest overlap between the skater's circle and a blocker.
 * (nx, ny) is the unit push-out normal pointing from the blocker towards the skater; moving the skater by
 * {@code depth} along it separates them. {@code top} is the blocker's absolute up-positive top height.
 * Mutable so a physics step can reuse one instance without allocating.
 */
public final class Contact
{
	/** {@link #box} when the world does not say which blocker it was. */
	public static final int UNKNOWN_BOX = -1;

	public float nx;
	public float ny;
	public float depth;
	public float top;
	/** Which blocker (an index unique within the world), or {@link #UNKNOWN_BOX}; for the debug view. */
	public int box = UNKNOWN_BOX;
	/** What the blocker is ("Rocks (game object)"), or null; for the debug view. */
	public String label;

	/** Sets the overlap; the blocker is unknown until a world names it. */
	public void set(float nx, float ny, float depth, float top)
	{
		this.nx = nx;
		this.ny = ny;
		this.depth = depth;
		this.top = top;
		this.box = UNKNOWN_BOX;
		this.label = null;
	}
}
