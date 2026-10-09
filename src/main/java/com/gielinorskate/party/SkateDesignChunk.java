package com.gielinorskate.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/** One piece of an offered design's picture: up to {@link DesignShare#CHUNK_CHARS} base64 characters. */
public class SkateDesignChunk extends PartyMemberMessage
{
/** The offer's hash. */
String hash;
/** 0-based. */
int index;
String data;
}
