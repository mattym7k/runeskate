package com.gielinorskate.duel;

import com.gielinorskate.Text;
import com.gielinorskate.duel.DuelStateMachine.Phase;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/** What the side panel's "Skate Duel" section shows. Immutable; made on the client thread, shown on the EDT. */
@EqualsAndHashCode
@AllArgsConstructor
public final class DuelView
{
/** A party member skating with duels on, on this world. */
@EqualsAndHashCode
public static final class Member
{
public final long id;
public final String name;

public Member(long id, String name)
{
this.id = id;
this.name = DuelLines.name(name);
}
}

/** "Allow duel challenges" is on. */
public final boolean allowed;
public final boolean skating;
public final boolean inParty;
public final List<Member> members;
public final Phase phase;
public final String opponent;
public final int myHp;
public final int oppHp;
public final int wins;
public final int losses;
/** The latest result's chat line while it shows (phase OVER), else null. */
public final String result;
/** In a PvP area or instance: no duels there. */
public final boolean blocked;
/** "Share my skater with my party" is on (duels need it). */
public final boolean sharing;

/**
* Challenge buttons work: duels on, party sharing on, skating, in a party, not in a PvP area or instance and no
* duel or challenge going.
*/
public boolean canChallenge()
{
return allowed && skating && inParty && !blocked && sharing && (phase == Phase.IDLE || phase == Phase.OVER);
}

/** The status line (wording: dv. in the bundled text). */
public String status()
{
if (!allowed)
return Text.get("dv.off");
if (!sharing)
return Text.get("dv.sharing");
if (!inParty)
return Text.get("dv.party");
switch (phase)
{
case CHALLENGING:
case COUNTDOWN:
case FIGHT:
return Text.get("dv." + phase, myHp, oppHp, opponent);
case CHALLENGED:
return Text.get("dv.challenged." + skating, opponent);
default:
return result != null ? result : Text.get("dv.idle."
+ (blocked ? "blocked" : members.isEmpty() ? "alone" : skating ? "ready" : "still"));
}
}
}
