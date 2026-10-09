package com.gielinorskate.controller;

import static com.gielinorskate.controller.PadAction.*;
import static com.gielinorskate.controller.PadButton.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.*;

/**
* A controller layout: for each {@link PadButton}, the {@link PadAction} it does on the board, in the air and on
* foot. Immutable; {@link #with} makes a changed copy. A button with no binding does nothing.
*/
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public final class PadPreset
{
/** One button's actions. {@code air} null: in the air the button does its board action. */
@EqualsAndHashCode
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
return board == NONE && foot == NONE && air == null;
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
public String toString()
{
return board + " / " + foot + (air != null ? " / air " + air : "");
}
}

private static final Binding EMPTY = new Binding(NONE, NONE);

private static final PadPreset SKATE_3 = new PadPreset()
.with(A, new Binding(PUSH, SPRINT))
.with(X, new Binding(PUSH, JUMP))
.with(B, new Binding(BRAKE, NONE))
.with(Y, new Binding(BOARD_TOGGLE, BOARD_TOGGLE))
// LB / RB were Shift: the trick modifier on the board, and Shift's sprint on foot
.with(LB, new Binding(HARD_MODIFIER, SPRINT))
.with(RB, new Binding(HARD_MODIFIER, SPRINT))
// LT / RT were Q / E: grabs on the board, drop or pick up on foot
.with(LT, new Binding(GRAB_LEFT, DROP_PICKUP))
.with(RT, new Binding(GRAB_RIGHT, DROP_PICKUP))
.with(BACK, new Binding(RESET, RESET))
.with(START, new Binding(STOP, STOP))
.with(DPAD_UP, new Binding(CONTROLS_CARD, CONTROLS_CARD));

/**
* Tony Hawk's American Wasteland: A ollies (held, let go) and jumps on foot, X + a direction flips, B + a
* direction grabs (on foot it drops or picks up the board), Y grinds near a rail and otherwise steps off (on foot:
* back on), LB / RB are the hard tricks and spin faster in the air. The right stick turns the camera and a quick
* up-then-down on the left stick is a manual (see {@link #buttonTricks}).
*/
private static final PadPreset THAW = new PadPreset()
.with(A, new Binding(OLLIE, JUMP))
.with(X, new Binding(FLIP_BUTTON, NONE))
.with(B, new Binding(GRAB_BUTTON, DROP_PICKUP))
.with(Y, new Binding(GRIND_BUTTON, BOARD_TOGGLE))
.with(LB, new Binding(SPIN_ASSIST, SPRINT))
.with(RB, new Binding(SPIN_ASSIST, SPRINT))
.with(BACK, new Binding(RESET, RESET))
.with(START, new Binding(STOP, STOP))
.with(DPAD_UP, new Binding(CONTROLS_CARD, CONTROLS_CARD));

private final EnumMap<PadButton, Binding> bindings;

/** An empty layout: every button does nothing. */
public PadPreset()
{
this(new EnumMap<>(PadButton.class));
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
return bindings.values().stream().anyMatch(b -> Stream.of(b.board, b.foot, b.air)
.anyMatch(a -> a == FLIP_BUTTON || a == GRAB_BUTTON));
}

/** The action {@code button} does in {@code context}; NONE when unbound. */
public PadAction action(PadButton button, PadContext context)
{
return binding(button).get(context);
}

public Binding binding(PadButton button)
{
return bindings.getOrDefault(button, EMPTY);
}

/** A copy with {@code button} bound to {@code binding}. */
public PadPreset with(PadButton button, Binding binding)
{
EnumMap<PadButton, Binding> copy = new EnumMap<>(bindings);
copy.compute(button, (b, old) -> binding.isNone() ? null : binding);
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
return Collections.unmodifiableList(Stream.of(PadButton.values()).filter(b -> action(b, context) == action)
.collect(Collectors.toList()));
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
continue;
PadAction a = b.get(c);
if (a != NONE && !a.allowedIn(c))
return e.getKey().label + " can't do \"" + a.label + "\" " + c.label.toLowerCase();
}
}
return null;
}
}
