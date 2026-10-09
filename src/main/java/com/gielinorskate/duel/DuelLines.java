package com.gielinorskate.duel;

import com.gielinorskate.Text;
import com.gielinorskate.duel.DuelStateMachine.Cause;
import com.gielinorskate.duel.DuelStateMachine.Outcome;

/** The Skate Duel's chat lines and banner text (the wording is under dl. in the bundled text). Pure. */
public final class DuelLines
{
/** The game's "Oh dear, you are dead!", for skaters. */
public static final String LOSS = Text.get("dl.loss");
public static final String DRAW = Text.get("dl.draw");

/** A display name, or a stand-in when the party has none. */
public static String name(String name)
{
return name == null || name.isEmpty() ? "your opponent" : name;
}

/**
* A display name for a chat line (or the stand-in), with its tags escaped: a name holding {@code <col>} or
* {@code <img>} shows as text in the chatbox instead of being drawn.
*/
public static String chatName(String name)
{
return net.runelite.client.util.Text.escapeJagex(name(name));
}

public static String challenged(String from)
{
return Text.get("dl.challenged", Math.round(DuelStateMachine.CHALLENGE_EXPIRY), name(from));
}

public static String challengeSent(String to)
{
return Text.get("dl.sent", name(to));
}

public static String declined(String by)
{
return Text.get("dl.declined", name(by));
}

public static String expired(String other, boolean mine)
{
return Text.get("dl.expired." + mine, name(other));
}

public static String countdown(String opponent)
{
return Text.get("dl.countdown", DuelStateMachine.BAIL_DAMAGE, name(opponent));
}

/** The big banner line at the end. */
public static String banner(Outcome outcome, String opponent)
{
return outcome == Outcome.WIN ? Text.get("dl.win", name(opponent)) : outcome == Outcome.LOSS ? LOSS
: outcome == Outcome.DRAW ? DRAW : "Skate Duel called off.";
}

/** The smaller line under the banner (why), or null when the banner says it all. */
public static String reason(Outcome outcome, Cause cause, String opponent)
{
switch (cause)
{
// this client's own reason for quitting: a forfeit, or (in the countdown) just why it was called off
case LEFT:
case BLOCKED:
case HOPPED:
case DUELS_OFF:
case SHARING_OFF:
String own = Text.get("dl.own." + cause);
return outcome == Outcome.CANCELLED ? Character.toUpperCase(own.charAt(0)) + own.substring(1)
: "You forfeit: " + own;
case SHUTDOWN:
return outcome == Outcome.CANCELLED ? null : "You forfeit.";
case STOPPED_SKATING:
case TIMED_OUT:
case BOTH_QUIT:
case OPPONENT_FORFEIT:
case OPPONENT_SILENT:
case OPPONENT_LEFT:
case CANCELLED:
return Text.get("dl.why." + cause, name(opponent));
default:
return null;
}
}

/** The chat line for a finished (or called-off) duel. */
public static String over(Outcome outcome, Cause cause, String opponent)
{
String why = reason(outcome, cause, opponent);
String head = outcome == Outcome.CANCELLED ? Text.get("dl.off", name(opponent)) : banner(outcome, opponent);
return why == null ? head : head + " " + why;
}

/** The panel's record line. */
public static String record(int wins, int losses)
{
return Text.get("dl.record", wins, losses);
}
}
