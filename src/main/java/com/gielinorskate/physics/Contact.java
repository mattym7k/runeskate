package com.gielinorskate.physics;

/**
 * Result of {@link CollisionWorld#contact}: the deepest overlap between the skater's circle and a blocker.
 * (nx, ny) is the unit push-out normal pointing from the blocker towards the skater; moving the skater by
 * {@code depth} along it separates them. {@code top} is the blocker's absolute up-positive top height.
 * Mutable so a physics step can reuse one instance without allocating.
 */
public final class Contact
{
	public float nx;
	public float ny;
	public float depth;
	public float top;

	/** Sets the overlap. */
	public void set(float nx, float ny, float depth, float top)
	{
		this.nx = nx;
		this.ny = ny;
		this.depth = depth;
		this.top = top;
	}
}
