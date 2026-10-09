package com.gielinorskate.render;

import com.gielinorskate.physics.BoardTransition;
import lombok.Getter;

/**
* Getting on or off the board without a snap: when a mount or dismount starts, the body and board placements
* drawn just before are kept, and for the transition's 0.25 s (BoardSwap.TRANSITION_SECONDS) each frame's
* placements are drawn eased from those toward where they now belong (a live target, so a walker or a rolling
* skater keeps moving). Getting on also hops the body up a little in the middle, so stepping onto a dropped
* board reads as a step up onto it. The same blend moves a board alone (dropped or picked up). Pure.
*/
public final class SwapBlend
{

private final Placement fromBody = new Placement();
private final Placement fromBoard = new Placement();
private boolean hop;
@Getter
private boolean active;

/** A transition {@code t} at {@code progress} is new since the last frame's ({@code lastT}, {@code lastProgress}). */
public static boolean starts(BoardTransition lastT, float lastProgress, BoardTransition t, float progress)
{
return t != BoardTransition.NONE && progress < 1f && (t != lastT || progress < lastProgress);
}

/**
* Starts a blend from what was drawn last: either placement may be null or not valid (nothing drawn), and
* that object is then not blended. With neither, there is no blend.
*
* @param hop the body hops (getting on)
*/
public void begin(Placement body, Placement board, boolean hop)
{
fromBody.invalidate();
fromBoard.invalidate();
if (body != null)
fromBody.copyFrom(body);
if (board != null)
fromBoard.copyFrom(board);
this.hop = hop;
active = fromBody.valid || fromBoard.valid;
}

public void cancel()
{
active = false;
}

/**
* Blends {@code body} and {@code board} (this frame's targets, overwritten in place) from the start, at
* {@code progress} 0..1 of the transition. At 1 the targets are left as they are and the blend ends.
*/
public void apply(float progress, Placement body, Placement board)
{
active &= progress < 1f;
if (!active)
return;
float w = ease(progress);
if (fromBody.valid && body != null)
{
lerp(fromBody, body, w);
// up 16 units at the middle of a mount (RuneLite z grows downward)
body.z -= hop ? 16f * 4f * w * (1f - w) : 0f;
}
if (fromBoard.valid && board != null)
lerp(fromBoard, board, w);
}

/** Smoothstep of {@code t} clamped to 0..1: starts and ends at rest. */
public static float ease(float t)
{
return PushCycle.smoothstep(0f, 1f, t);
}

/** {@code t} of the way from orientation {@code a} to {@code b} (JAU) the short way round, in 0..2048. */
public static float lerpJau(float a, float b, float t)
{
float d = (b - a) % 2048f;
d += d > 1024f ? -2048f : d <= -1024f ? 2048f : 0f;
float r = (a + d * t) % 2048f;
return r < 0f ? r + 2048f : r;
}

/** {@code to} becomes {@code w} of the way from {@code from} to it. */
public static void lerp(Placement from, Placement to, float w)
{
to.set(RenderPose.mix(from.x, to.x, w), RenderPose.mix(from.y, to.y, w), RenderPose.mix(from.z, to.z, w),
lerpJau(from.jau, to.jau, w), RenderPose.mix(from.roll, to.roll, w), RenderPose.mix(from.pitch, to.pitch, w));
}
}
