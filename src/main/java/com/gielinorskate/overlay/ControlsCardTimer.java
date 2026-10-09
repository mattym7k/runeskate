package com.gielinorskate.overlay;

/**
* Pure show/hide for the controls card. While the player has not yet learned the basics (pushed and jumped once),
* starting to skate shows the card and keeps it up; 2 s after the first push-and-jump it fades over 1 s. H shows
* it when hidden and hides it when shown. No client dependency.
*/
public final class ControlsCardTimer
{
private boolean pushed;
private boolean jumped;
/** The card is up by itself (not by H), waiting for the basics. */
private boolean autoShowing;
/** When the auto-shown card starts fading (after the basics were learned), or -1. */
private float fadeStart = -1f;
/** Shown by H; overrides the auto-show. */
private boolean manualOn;

/**
* Call each time skating starts. {@code autoShow}: the "Show controls card" setting is on and the basics were
* not learned in an earlier session.
*/
public void onSkateStart(boolean autoShow)
{
manualOn = false;
autoShowing = autoShow && !learnedBasics();
if (autoShowing)
fadeStart = -1f;
}

/** The skater pushed. */
public void onPush(float now)
{
pushed = true;
checkLearned(now);
}

/** The skater popped (an ollie or any flip). */
public void onJump(float now)
{
jumped = true;
checkLearned(now);
}

private void checkLearned(float now)
{
if (learnedBasics() && autoShowing && fadeStart < 0f)
// lingers 2 s
fadeStart = now + 2f;
}

/** True once the skater has both pushed and jumped (in this client session). */
public boolean learnedBasics()
{
return pushed && jumped;
}

/** Treats the basics as learned already (an earlier session learned them). */
public void markLearned()
{
pushed = true;
jumped = true;
}

/** H pressed while skating: show the card when it is hidden, hide it when it is up (or fading). */
public void toggle(float now)
{
manualOn = alpha(now) <= 0f;
// H takes over from the auto-show for good this session
autoShowing = false;
}

/** Opacity in [0, 1] at {@code now}; 0 means the card is not drawn. */
public float alpha(float now)
{
if (manualOn || autoShowing && (fadeStart < 0f || now < fadeStart))
return 1f;
// fading over 1 s
return autoShowing ? Math.max(0f, 1f - (now - fadeStart)) : 0f;
}
}
