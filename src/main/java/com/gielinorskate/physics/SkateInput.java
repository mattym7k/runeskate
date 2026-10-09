package com.gielinorskate.physics;

import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Grabs;
import java.util.ArrayList;
import java.util.List;

/** Input for physics steps. Held values persist; edge flags are cleared after each step. */
public final class SkateInput
{
/** -1 = full left, +1 = full right. */
public float steer;
public boolean crouch;
/**
* Held value: true while a pop is being wound up (the S key, or a mouse wind-up either way). Builds the
* pop charge like crouch, but a nollie wind-up charges without the crouch pose or blocking pushes.
*/
public boolean charge;
public boolean powerslide;
/**
* Held value: true while Shift (powerslide) must not brake: it is being used as the trick modifier (W held, a
* flick wind-up) or has not yet been held alone for long enough. Tight carves and body flips ignore it.
*/
public boolean brakeBlocked;
/** Held value: true while the S key itself is held (not the mouse wind-up); picks 5-0 on a straight grind. */
public boolean leanBack;

public boolean pushPressed;
/** Held value: true while the push key is held down; persists across steps, not cleared by clearEdges. */
public boolean pushHeld;
public boolean resetRequested;

/** Gestures recognized since the last step; cleared after each step. */
public final List<Gesture> gestures = new ArrayList<>();
/** Held value: true while the manual key (Space) is held; with pushHeld (W) it is a nose manual. */
public boolean manualHeld;
/** Held value: with manualHeld, a nose manual without W (controller mode: a small right-stick tilt down). */
public boolean noseManualHeld;
/** Held value: true while Q is held down. */
public boolean grabLeft;
/** Held value: true while E is held down. */
public boolean grabRight;
/**
* Held value: where the mouse (no button pressed) has moved since the grab key went down, relative to the board
* and the stance; null when it has not moved far enough (or no grab key is held). Picks a directional grab.
*/
public Grabs.Aim grabAim;
/** Held values: the lean forward / lean back keys (Up / Down arrows by default), for grab-flips. */
public boolean leanForwardKey;
public boolean leanBackKey;
/**
* Controller mode: the left stick sends the lean keys instead of W / S, so Shift + lean up / down also does
* the front / back body flip in the air (Shift + W / S stays keyboard-only; see SkatePhysics.updateBodyFlip).
*/
public boolean controllerMode;
/**
* Held value: the grind button is held (button tricks): a rail catches from further to the side
* ({@link com.gielinorskate.world.GrindMap#ASSIST_SNAP_DISTANCE}). Rails still catch without it.
*/
public boolean grindHeld;
/** Held value: the spin assist is held (button tricks): in the air, steering spins faster. */
public boolean spinFast;

void clearEdges()
{
pushPressed = false;
resetRequested = false;
gestures.clear();
}
}
