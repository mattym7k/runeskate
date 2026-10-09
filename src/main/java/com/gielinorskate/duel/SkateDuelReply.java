package com.gielinorskate.duel;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/** The challenged member's answer: accepted (the countdown starts) or declined (also sent when busy). */
@NoArgsConstructor
@AllArgsConstructor
public class SkateDuelReply extends PartyMemberMessage
{
long duelId;
/** The replier's first seq in this duel. */
int seq;
/** The challenger. */
long targetMemberId;
boolean accepted;
}
