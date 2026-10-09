package com.gielinorskate.duel;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
* "Skate Duel?" from the sender to one party member. Starts a duel with a fresh random {@link #duelId}; its
* {@link #seq} is the sender's first in that duel. No names or account data: the party identifies the sender.
*/
@NoArgsConstructor
@AllArgsConstructor
public class SkateDuelChallenge extends PartyMemberMessage
{
long duelId;
/** The sender's per-duel sequence number; later messages of the same duel count on from it. */
int seq;
/** The challenged party member. */
long targetMemberId;
}
