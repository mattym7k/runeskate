package com.gielinorskate.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A controller layout: for each {@link PadButton}, the {@link PadAction} it does on the board, in the air and on
 * foot. Immutable; {@link #with} makes a changed copy. A button with no binding does nothing.
 */
public final class PadPreset
{
	/** One button's actions. {@code air} null: in the air the button does its board action. */
	public static final class Binding
	{
		public final PadAction board;
		public final PadAction foot;
		/** Null when the air action is the board one. */
		public final PadAction air;

		public Binding(PadAction board, PadAction foot, PadAction air)
		{
			this.board = Objects.requireNonNull(board);
			this.foot = Objects.requireNonNull(foot);
			this.air = air == board ? null : air;
		}

		public Binding(PadAction board, PadAction foot)
		{
			this(board, foot, null);
		}

		public PadAction get(PadContext context)
		{
			switch (context)
			{
				case FOOT:
					return foot;
				case AIR:
					return air != null ? air : board;
				default:
					return board;
			}
		}

		boolean isNone()
		{
			return board == PadAction.NONE && foot == PadAction.NONE && air == null;
		}

		/** A copy with {@code context}'s action set to {@code action}. */
		Binding with(PadContext context, PadAction action)
		{
			switch (context)
			{
				case FOOT:
					return new Binding(board, action, air);
				case AIR:
					return new Binding(board, foot, action);
				default:
					return new Binding(action, foot, air);
			}
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Binding))
			{
				return false;
			}
			Binding b = (Binding) o;
			return board == b.board && foot == b.foot && air == b.air;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(board, foot, air);
		}

		@Override
		public String toString()
		{
			return board + " / " + foot + (air != null ? " / air " + air : "");
		}
	}

	private static final Binding NONE = new Binding(PadAction.NONE, PadAction.NONE);

	private static final PadPreset SKATE_3 = new PadPreset()
		.with(PadButton.A, new Binding(PadAction.PUSH, PadAction.SPRINT))
		.with(PadButton.X, new Binding(PadAction.PUSH, PadAction.JUMP))
		.with(PadButton.B, new Binding(PadAction.BRAKE, PadAction.NONE))
		.with(PadButton.Y, new Binding(PadAction.BOARD_TOGGLE, PadAction.BOARD_TOGGLE))
		// LB / RB were Shift: the trick modifier on the board, and Shift's sprint on foot
		.with(PadButton.LB, new Binding(PadAction.HARD_MODIFIER, PadAction.SPRINT))
		.with(PadButton.RB, new Binding(PadAction.HARD_MODIFIER, PadAction.SPRINT))
		// LT / RT were Q / E: grabs on the board, drop or pick up on foot
		.with(PadButton.LT, new Binding(PadAction.GRAB_LEFT, PadAction.DROP_PICKUP))
		.with(PadButton.RT, new Binding(PadAction.GRAB_RIGHT, PadAction.DROP_PICKUP))
		.with(PadButton.BACK, new Binding(PadAction.RESET, PadAction.RESET))
		.with(PadButton.START, new Binding(PadAction.STOP, PadAction.STOP))
		.with(PadButton.DPAD_UP, new Binding(PadAction.CONTROLS_CARD, PadAction.CONTROLS_CARD));

	/**
	 * Tony Hawk's American Wasteland: A ollies (held, let go) and jumps on foot, X + a direction flips, B + a
	 * direction grabs (on foot it drops or picks up the board), Y grinds near a rail and otherwise steps off (on foot:
	 * back on), LB / RB are the hard tricks and spin faster in the air. The right stick turns the camera and a quick
	 * up-then-down on the left stick is a manual (see {@link #buttonTricks}).
	 */
	private static final PadPreset THAW = new PadPreset()
		.with(PadButton.A, new Binding(PadAction.OLLIE, PadAction.JUMP))
		.with(PadButton.X, new Binding(PadAction.FLIP_BUTTON, PadAction.NONE))
		.with(PadButton.B, new Binding(PadAction.GRAB_BUTTON, PadAction.DROP_PICKUP))
		.with(PadButton.Y, new Binding(PadAction.GRIND_BUTTON, PadAction.BOARD_TOGGLE))
		.with(PadButton.LB, new Binding(PadAction.SPIN_ASSIST, PadAction.SPRINT))
		.with(PadButton.RB, new Binding(PadAction.SPIN_ASSIST, PadAction.SPRINT))
		.with(PadButton.BACK, new Binding(PadAction.RESET, PadAction.RESET))
		.with(PadButton.START, new Binding(PadAction.STOP, PadAction.STOP))
		.with(PadButton.DPAD_UP, new Binding(PadAction.CONTROLS_CARD, PadAction.CONTROLS_CARD));

	private final Map<PadButton, Binding> bindings;

	/** An empty layout: every button does nothing. */
	public PadPreset()
	{
		this(new EnumMap<>(PadButton.class));
	}

	private PadPreset(Map<PadButton, Binding> bindings)
	{
		this.bindings = bindings;
	}

	/** Skate 3: exactly the pad layout RuneSkate had before presets. */
	public static PadPreset skate3()
	{
		return SKATE_3;
	}

	/** Tony Hawk's American Wasteland: tricks on buttons, the right stick on the camera. */
	public static PadPreset thaw()
	{
		return THAW;
	}

	/**
	 * True when this layout does tricks with buttons (it binds a flip or grab button): the left stick and the d-pad
	 * then give the trick's direction, a quick up-then-down on the left stick is a manual, and the right stick turns
	 * the camera instead of flicking (flicks and the stick's manual tilt are off; mouse flicks still work).
	 */
	public boolean buttonTricks()
	{
		for (Binding b : bindings.values())
		{
			for (PadContext c : PadContext.values())
			{
				PadAction a = b.get(c);
				if (a == PadAction.FLIP_BUTTON || a == PadAction.GRAB_BUTTON)
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The action {@code button} does in {@code context}; NONE when unbound. */
	public PadAction action(PadButton button, PadContext context)
	{
		return binding(button).get(context);
	}

	public Binding binding(PadButton button)
	{
		Binding b = bindings.get(button);
		return b != null ? b : NONE;
	}

	/** A copy with {@code button} bound to {@code binding}. */
	public PadPreset with(PadButton button, Binding binding)
	{
		Map<PadButton, Binding> copy = new EnumMap<>(PadButton.class);
		copy.putAll(bindings);
		if (binding.isNone())
		{
			copy.remove(button);
		}
		else
		{
			copy.put(button, binding);
		}
		return new PadPreset(copy);
	}

	/** A copy with {@code button}'s action in {@code context} set to {@code action}. */
	public PadPreset with(PadButton button, PadContext context, PadAction action)
	{
		return with(button, binding(button).with(context, action));
	}

	/** The buttons that do {@code action} in {@code context}, in button order. */
	public List<PadButton> buttonsFor(PadAction action, PadContext context)
	{
		List<PadButton> out = new ArrayList<>();
		for (PadButton b : PadButton.values())
		{
			if (action(b, context) == action)
			{
				out.add(b);
			}
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * Why this layout can't be used (an action bound where it can't be, or one not built yet), or null when it
	 * is fine.
	 */
	public String problem()
	{
		for (Map.Entry<PadButton, Binding> e : bindings.entrySet())
		{
			Binding b = e.getValue();
			for (PadContext c : PadContext.values())
			{
				if (c == PadContext.AIR && b.air == null)
				{
					continue;
				}
				PadAction a = b.get(c);
				if (a != PadAction.NONE && !a.allowedIn(c))
				{
					return e.getKey().label + " can't do \"" + a.label + "\" " + c.label.toLowerCase();
				}
			}
		}
		return null;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof PadPreset && bindings.equals(((PadPreset) o).bindings);
	}

	@Override
	public int hashCode()
	{
		return bindings.hashCode();
	}

	@Override
	public String toString()
	{
		return bindings.toString();
	}
}
