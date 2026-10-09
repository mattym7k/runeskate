package com.gielinorskate.controller;

import com.gielinorskate.Text;
import java.util.stream.Stream;

/**
* Everything a pad button can do. A {@link PadPreset} binds each button to one action per {@link PadContext}. The
* {@link #id} is what a layout code stores, so ids never change once released (names and labels may).
*/
public enum PadAction
{
NONE,

// ---- on the board
/** Push: a new press pushes, held keeps pushing (W, or the old pad A / X). */
PUSH,
/** A push too; the board does not yet have its own mongo push. */
MONGO_PUSH,
/** A powerslide brake straight away (the brake key; with steer, a tight carve). */
BRAKE,
/** Hold to crouch and charge, let go to ollie (keyboard tricks' Space). */
OLLIE,
/** Grab with the left / front hand; the right stick aims it (Q). */
GRAB_LEFT,
/** Grab with the right / back hand (E). */
GRAB_RIGHT,
/** Shift: hard flips with a flick, a tight carve with steer, a brake held alone, a body flip with a lean. */
HARD_MODIFIER,
/** The lean keys, for grab-flips. */
LEAN_FORWARD,
LEAN_BACK,

// ---- both
/** Step off and carry the board, get on, or call it back (the board key). */
BOARD_TOGGLE,
/** Get up after a bail, or stop (R). */
RESET,
/** Stop skating (Esc). */
STOP,
/** Show or hide the controls card (H). */
CONTROLS_CARD,

// ---- on foot
SPRINT,
JUMP,
DROP_PICKUP,
/** Hold to turn the camera with the right stick (the middle-mouse drag). */
CAMERA_ORBIT,

// ---- button tricks (the Tony Hawk's American Wasteland preset; see com.gielinorskate.input.ButtonTricks)
/** With a left stick / d-pad direction: a flip trick (kickflip, heelflip, ...); again mid-flip: a double. */
FLIP_BUTTON,
/** Held with a left stick / d-pad direction: a grab (melon, indy, ...), the same grabs as Q / E. */
GRAB_BUTTON,
/** Near a rail: held, the rail catches from further away. Rolling with no rail near: step off. */
GRIND_BUTTON,
/** The hard tricks modifier (as Shift), and in the air, held, a faster spin. */
SPIN_ASSIST;

/** Stored in layout codes; never reused or changed. */
public final int id;
public final String label;
/** Can be bound on the board (and so in the air). */
public final boolean onBoard;
/** Can be bound on foot. */
public final boolean onFoot;

/** Its id, places and label, from text/overlay.properties ("pad.action." + name). */
PadAction()
{
String[] f = Text.get("pad.action." + name()).split(",", 3);
id = Integer.parseInt(f[0]);
onBoard = f[1].contains("B");
onFoot = f[1].contains("F");
label = f[2];
}

/** True when a layout may bind this action in {@code context}. */
public boolean allowedIn(PadContext context)
{
return context == PadContext.FOOT ? onFoot : onBoard;
}

/** The action with layout-code id {@code id}, or null. */
public static PadAction forId(int id)
{
return Stream.of(values()).filter(a -> a.id == id).findFirst().orElse(null);
}

@Override
public String toString()
{
return label;
}
}
