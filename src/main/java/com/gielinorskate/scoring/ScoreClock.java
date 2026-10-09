package com.gielinorskate.scoring;

import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
* Score timing in seconds since this clock was created (plugin start), shared by the session and
* the HUD. Counting from a recent origin keeps float seconds precise: raw System.nanoTime() / 1e9
* as a float only resolves 0.25 s steps after ~24 days of uptime (2^21 s; float has a 24-bit
* mantissa), while 2^21 s of plugin uptime would be needed here for the same loss.
*/
@Singleton
public class ScoreClock
{
private final LongSupplier nanos;
private final long startNanos;

@Inject
public ScoreClock()
{
this(System::nanoTime);
}

ScoreClock(LongSupplier nanos)
{
this.nanos = nanos;
this.startNanos = nanos.getAsLong();
}

public float now()
{
return (nanos.getAsLong() - startNanos) / 1_000_000_000f;
}
}
