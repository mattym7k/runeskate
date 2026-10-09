package com.gielinorskate.party;

import java.util.function.Consumer;

/**
* Keeps party ghosts from ever ending local skating: the first exception out of a ghost frame is reported once,
* the ghosts are cleaned up, and ghosts stay off until {@link #reset} at the start of the next skate session.
* Not thread safe; client thread only.
*/
public final class GhostFailureGuard
{
boolean disabled;

/**
* Runs {@code frame} unless ghosts are disabled for this session. Never throws a RuntimeException.
*
* @param cleanup despawns every ghost; runs once, on the first failure
* @param report receives the first failure (to log it); runs once
*/
public void run(Runnable frame, Runnable cleanup, Consumer<RuntimeException> report)
{
if (disabled)
return;
try
{
frame.run();
}
catch (RuntimeException e)
{
disabled = true;
report.accept(e);
try
{
cleanup.run();
}
catch (RuntimeException ignored)
{
// already reported the cause; ghosts are off for the rest of the session either way
}
}
}

/** A new skate session: ghosts may run again. */
public void reset()
{
disabled = false;
}
}
