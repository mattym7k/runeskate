package com.gielinorskate.physics;

/** What happened on a FootPhysics step. */
public enum FootEvent
{
	/** Left the ground with a jump (Space). */
	JUMP,
	/** Walked off an edge taller than a step: falling without a jump. */
	FALL,
	/** Back on the ground after a jump or a fall, always on the feet. */
	LAND
}
