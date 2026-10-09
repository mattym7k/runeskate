package com.gielinorskate.duel;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
* The sender's end of a duel, after all its earlier messages (same seq order). {@link #reason} is a
* {@link DuelStateMachine.EndReason} name: KO (the sender is down), SURVIVED (the sender saw the other's KO and
* is still up), FORFEIT, CANCEL or TIMEOUT (the sender stopped hearing from the receiver and won). Unknown names (a newer version) are ignored.
*/
@NoArgsConstructor
@AllArgsConstructor
public class SkateDuelEnd extends PartyMemberMessage
{
long duelId;
int seq;
long targetMemberId;
String reason;
}
