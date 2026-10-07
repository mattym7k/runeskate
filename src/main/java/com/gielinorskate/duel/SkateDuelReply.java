package com.gielinorskate.duel;

import net.runelite.client.party.messages.PartyMemberMessage;

/** The challenged member's answer: accepted (the countdown starts) or declined (also sent when busy). */
public class SkateDuelReply extends PartyMemberMessage
{
	long duelId;
	/** The replier's first seq in this duel. */
	int seq;
	/** The challenger. */
	long targetMemberId;
	boolean accepted;
}
