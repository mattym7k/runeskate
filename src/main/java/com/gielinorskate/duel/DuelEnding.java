package com.gielinorskate.duel;

import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;

/**
* Which cosmetic ending a finished Skate Duel plays on the local skater (and, through the ghost timeline, on its
* party ghost): the loser of a knockout throws a tantrum and snaps their board, the winner of a knockout (or of a
* forfeit: the other side gave up mid-fight) celebrates. A loss by forfeit, timeout or leaving, a draw and a
* cancelled duel play nothing. Never with the "Duel endings" setting off or in a PvP area or instance. Pure.
*/
public enum DuelEnding
{
NONE, TANTRUM, CELEBRATE;

/**
* The ending for a duel that ended {@code outcome} by {@code cause}.
*
* @param enabled the "Duel endings" setting
* @param blocked in a PvP area or instance (or a PvP world)
*/
public static DuelEnding pick(Outcome outcome, Cause cause, boolean enabled, boolean blocked)
{
// a loss only for a real knockout: quitting, timing out or leaving is not a lost fight
return !enabled || blocked ? NONE : outcome == Outcome.LOSS && cause == Cause.KO ? TANTRUM
: outcome == Outcome.WIN && (cause == Cause.KO || cause == Cause.OPPONENT_FORFEIT) ? CELEBRATE : NONE;
}
}
