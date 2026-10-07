package com.gielinorskate.controller;

/**
 * Everything a pad button can do. A {@link PadPreset} binds each button to one action per {@link PadContext}. The
 * {@link #id} is what a layout code stores, so ids never change once released (names and labels may).
 * <p>
 * An action that is not {@link #implemented} is declared so a later preset can use it, but no layout can bind it
 * until it works (the editor does not offer it, and a code naming it is refused). Every action works today.
 */
public enum PadAction
{
	NONE(0, "Nothing", true, true, true),

	// ---- on the board
	/** Push: a new press pushes, held keeps pushing (W, or the old pad A / X). */
	PUSH(1, "Push", true, false, true),
	/** A push too; the board does not yet have its own mongo push. */
	MONGO_PUSH(2, "Mongo push", true, false, true),
	/** A powerslide brake straight away (the brake key; with steer, a tight carve). */
	BRAKE(3, "Brake", true, false, true),
	/** Hold to crouch and charge, let go to ollie (keyboard tricks' Space). */
	OLLIE(4, "Ollie (hold, let go)", true, false, true),
	/** Grab with the left / front hand; the right stick aims it (Q). */
	GRAB_LEFT(5, "Grab (left hand)", true, false, true),
	/** Grab with the right / back hand (E). */
	GRAB_RIGHT(6, "Grab (right hand)", true, false, true),
	/** Shift: hard flips with a flick, a tight carve with steer, a brake held alone, a body flip with a lean. */
	HARD_MODIFIER(7, "Hard tricks (Shift)", true, false, true),
	/** The lean keys, for grab-flips. */
	LEAN_FORWARD(8, "Lean forward", true, false, true),
	LEAN_BACK(9, "Lean back", true, false, true),

	// ---- both
	/** Step off and carry the board, get on, or call it back (the board key). */
	BOARD_TOGGLE(10, "Board on / off", true, true, true),
	/** Get up after a bail, or stop (R). */
	RESET(11, "Reset (get up)", true, true, true),
	/** Stop skating (Esc). */
	STOP(12, "Stop skating", true, true, true),
	/** Show or hide the controls card (H). */
	CONTROLS_CARD(13, "Controls card", true, true, true),

	// ---- on foot
	SPRINT(14, "Sprint (hold)", false, true, true),
	JUMP(15, "Jump", false, true, true),
	DROP_PICKUP(16, "Drop / pick up the board", false, true, true),
	/** Hold to turn the camera with the right stick (the middle-mouse drag). */
	CAMERA_ORBIT(17, "Turn the camera (hold, right stick)", false, true, true),

	// ---- button tricks (the Tony Hawk's American Wasteland preset; see com.gielinorskate.input.ButtonTricks)
	/** With a left stick / d-pad direction: a flip trick (kickflip, heelflip, ...); again mid-flip: a double. */
	FLIP_BUTTON(18, "Flip trick (+ direction)", true, false, true),
	/** Held with a left stick / d-pad direction: a grab (melon, indy, ...), the same grabs as Q / E. */
	GRAB_BUTTON(19, "Grab (+ direction)", true, false, true),
	/** Near a rail: held, the rail catches from further away. Rolling with no rail near: step off. */
	GRIND_BUTTON(20, "Grind (or step off)", true, false, true),
	/** The hard tricks modifier (as Shift), and in the air, held, a faster spin. */
	SPIN_ASSIST(21, "Hard tricks + spin faster", true, false, true);

	/** Stored in layout codes; never reused or changed. */
	public final int id;
	public final String label;
	/** Can be bound on the board (and so in the air). */
	public final boolean onBoard;
	/** Can be bound on foot. */
	public final boolean onFoot;
	/** False for a declared action that does nothing yet. */
	public final boolean implemented;

	PadAction(int id, String label, boolean onBoard, boolean onFoot, boolean implemented)
	{
		this.id = id;
		this.label = label;
		this.onBoard = onBoard;
		this.onFoot = onFoot;
		this.implemented = implemented;
	}

	/** True when a layout may bind this action in {@code context}. */
	public boolean allowedIn(PadContext context)
	{
		return implemented && (context == PadContext.FOOT ? onFoot : onBoard);
	}

	/** The action with layout-code id {@code id}, or null. */
	public static PadAction forId(int id)
	{
		for (PadAction a : values())
		{
			if (a.id == id)
			{
				return a;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
