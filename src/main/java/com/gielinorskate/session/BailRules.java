package com.gielinorskate.session;

import com.gielinorskate.Text;
import com.gielinorskate.GielinorSkateConfig.AfterBail;

/** What a bail does, and R during a Skate Duel (countdown or fight). Pure. */
final class BailRules
{
static final String R_DISABLED_HINT = Text.get("br.r");

private BailRules()
{
}

/**
* A bail knocks the skater off the board ({@link Knockdown}) with "Get knocked off", the default, and always in a
* Skate Duel (so it is the same for both sides); otherwise ("Hop straight back on") it is the quick fall and
* straight back on.
*/
static boolean knocksOff(AfterBail setting, boolean dueling)
{
return dueling || setting != AfterBail.HOP_BACK_ON;
}

/**
* R as a quick reset (back on the board after a bail, skipping the knockdown, standing still): off during a duel,
* where pressing it says so once per duel. The automatic recovery never goes through here, so it works in duels.
*/
static final class ResetGate
{
private boolean hintGiven;
private boolean hintPending;

/**
* @param pressed R went down this frame
* @param dueling in a duel's countdown or fight
* @return whether the press counts
*/
boolean allow(boolean pressed, boolean dueling)
{
if (!dueling)
{
// the duel is over (or there was none): the next one says it again
hintGiven = false;
return pressed;
}
if (pressed && !hintGiven)
{
hintGiven = true;
hintPending = true;
}
return false;
}

/** True once after R was first ignored in a duel: say {@link #R_DISABLED_HINT}. */
boolean takeHint()
{
boolean h = hintPending;
hintPending = false;
return h;
}
}
}
