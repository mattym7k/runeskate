package com.gielinorskate.input;

import com.gielinorskate.tricks.Grabs.Aim;

/**
* Turns the mouse movement (no button pressed) since a grab key went down into the part of the board the grab
* aims at. Screen terms, y down: up is the nose and down the tail; sideways, regular stance faces the right of
* the screen (the camera rides behind the board), so right is the toe side. "Mirror flicks" swaps left and right,
* as it does for flicks. Pure.
*/
public final class GrabAim
{
/** The mouse must move at least this far (pixels) from where it was at the press to aim. */
public static final int AIM_PX = 20;

private GrabAim()
{
}

/** The aim for a move of (dx, dy) pixels, or null when it is shorter than {@link #AIM_PX}. */
public static Aim classify(int dx, int dy, boolean mirror)
{
if ((long) dx * dx + (long) dy * dy < (long) AIM_PX * AIM_PX)
return null;
if (Math.abs(dy) >= Math.abs(dx))
return dy < 0 ? Aim.NOSE : Aim.TAIL;
return (dx > 0) != mirror ? Aim.TOE : Aim.HEEL;
}
}
