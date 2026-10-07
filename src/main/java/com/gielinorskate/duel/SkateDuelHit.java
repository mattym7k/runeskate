package com.gielinorskate.duel;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Damage the sender deals: to the other duellist for a landed combo, or to itself for a bail
 * ({@link #selfInflicted}, target = the sender). Each client is the authority for its own hits and bails; both
 * apply a sender's hits in its seq order, so both see the same HP.
 */
public class SkateDuelHit extends PartyMemberMessage
{
	long duelId;
	int seq;
	long targetMemberId;
	/** 1..{@link DuelDamage#MAX}. */
	int damage;
	/** The banked combo value behind the hit (0 for a bail). */
	int comboValue;
	int trickCount;
	boolean selfInflicted;
}
