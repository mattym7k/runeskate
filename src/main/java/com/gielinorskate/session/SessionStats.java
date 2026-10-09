package com.gielinorskate.session;

import com.gielinorskate.scoring.ComboScorer;

/**
* Best landed combo and tricks landed since the client started, for the side panel. Fed the scorer's latest result
* every frame; a result counts once (by the scorer's result sequence number). Pure.
*/
public final class SessionStats
{
/** The scorer starts at sequence 0 with no result. */
private int seenSequence;
private int bestCombo;
private int tricksLanded;

/** Takes in the scorer's latest result if it is new; returns true when the stats changed. */
public boolean update(int resultSequence, ComboScorer.Result result, int trickCount)
{
if (resultSequence == seenSequence)
return false;
seenSequence = resultSequence;
if (result == null || !result.isLanded())
return false;
bestCombo = Math.max(bestCombo, result.value);
tricksLanded += trickCount;
return true;
}

public int bestCombo()
{
return bestCombo;
}

public int tricksLanded()
{
return tricksLanded;
}
}
