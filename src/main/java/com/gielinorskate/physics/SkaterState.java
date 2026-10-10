package com.gielinorskate.physics;

public enum SkaterState
{
	ROLLING,
	AIRBORNE,
	BAILED,
	/** Balancing on two wheels; moves exactly like ROLLING. */
	MANUAL,
	/** Locked onto a rail, fence or ledge (see {@code GrindMap}); moves along it, W does not push. */
	GRINDING
}
