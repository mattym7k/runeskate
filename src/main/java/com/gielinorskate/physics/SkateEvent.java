package com.gielinorskate.physics;

public enum SkateEvent
{
	PUSH,
	POP,
	ROLL_OFF,
	LAND,
	BAIL,
	RESET,
	/** A hard wall hit that did not bail: steering is locked for a moment (SkateTuning.stumbleTime). */
	STUMBLE
}
