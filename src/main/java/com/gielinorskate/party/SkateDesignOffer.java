package com.gielinorskate.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
* A party member's custom design for one part is coming: its small picture ({@link SkateDesignChunk}s of the PNG's
* base64) and what it must be. Receivers check everything before reading any chunk (see {@link DesignInbox}).
* Older plugin versions do not know the type and drop it.
*/
public class SkateDesignOffer extends PartyMemberMessage
{
/** The part's key: grip, deck or wheels. */
String part;
/** 8 to 16 lowercase hex digits of the PNG's SHA-256; ghost updates name it by the first 8. */
String hash;
/** The design's name. */
String name;
/** How many chunks follow, 1..{@link DesignShare#MAX_CHUNKS}. */
int totalChunks;
/** The picture's size, within the part's bounds. */
int width;
int height;
}
