package com.gielinorskate.party;

import com.gielinorskate.design.CustomDesignRules;
import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.AllArgsConstructor;
import net.runelite.client.party.messages.PartyMessage;

/**
* The rules and limits of sharing custom designs with the party, and the messages one shared design goes out as.
* Pure.
*/
final class DesignShare
{
/** Most base64 characters in one chunk. */
static final int CHUNK_CHARS = 1000;
/** Most chunks an offer may announce. */
static final int MAX_CHUNKS = 12;
/** Most base64 characters in one part's picture (8 KB). */
static final int MAX_ENCODED = SharedDesignImage.MAX_ENCODED;
/** An incomplete picture is dropped after this long without progress (a new chunk or its offer again), seconds. */
static final float ASSEMBLY_SECONDS = 30f;
/** Most complete designs a receiver keeps (least recently used go first). */
static final int CACHE_SIZE = 20;
/** Shortest and longest hash an offer may carry, hex digits. */
static final int MIN_HASH = 8;
static final int MAX_HASH = 16;

private DesignShare()
{
}

/** One of our designs as it goes to the party: its offer and its chunks. Immutable. */
@AllArgsConstructor
static final class Outgoing
{
/** The local design's id (CUSTOM_...), so a ghost update can name it. */
final String designId;
final DesignPart part;
/** 8 lowercase hex digits. */
final String hash;
final String name;
final int width;
final int height;
final List<String> chunks;

/** The offer, then every chunk in order. */
List<PartyMessage> messages()
{
List<PartyMessage> out = new ArrayList<>(chunks.size() + 1);
SkateDesignOffer o = new SkateDesignOffer();
o.part = part.key;
o.hash = hash;
o.name = name;
o.totalChunks = chunks.size();
o.width = width;
o.height = height;
out.add(o);
for (int i = 0; i < chunks.size(); i++)
{
SkateDesignChunk c = new SkateDesignChunk();
c.hash = hash;
c.index = i;
c.data = chunks.get(i);
out.add(c);
}
return out;
}
}

/**
* Our design {@code designId} ready to share as {@code picture}; null when the picture breaks a limit (it never
* should: {@link SharedDesignImage#encode} keeps to them).
*/
static Outgoing outgoing(String designId, String name, SharedDesignImage.Encoded picture)
{
if (picture == null || picture.base64.length() > MAX_ENCODED
|| !SharedDesignImage.sizeAllowed(picture.part, picture.width, picture.height))
return null;
List<String> chunks = chunks(picture.base64);
if (chunks.isEmpty() || chunks.size() > MAX_CHUNKS)
return null;
return new Outgoing(designId, picture.part, hash(picture.png), cleanName(name), picture.width,
picture.height, chunks);
}

/** {@code base64} in pieces of at most {@link #CHUNK_CHARS}. */
static List<String> chunks(String base64)
{
List<String> out = new ArrayList<>();
for (int i = 0; i < base64.length(); i += CHUNK_CHARS)
out.add(base64.substring(i, Math.min(base64.length(), i + CHUNK_CHARS)));
return Collections.unmodifiableList(out);
}

/** The first 8 hex digits (lowercase) of the SHA-256 of {@code data}. */
static String hash(byte[] data)
{
return fullHash(data).substring(0, MIN_HASH);
}

/** The first {@link #MAX_HASH} hex digits (lowercase) of the SHA-256 of {@code data}. */
static String fullHash(byte[] data)
{
byte[] d;
try
{
d = MessageDigest.getInstance("SHA-256").digest(data);
}
catch (NoSuchAlgorithmException e)
{
// every Java has SHA-256
throw new IllegalStateException(e);
}
StringBuilder sb = new StringBuilder();
for (int i = 0; i < MAX_HASH / 2; i++)
sb.append(String.format("%02x", d[i]));
return sb.toString();
}

/** True for 8 to 16 lowercase hex digits. */
static boolean validHash(String h)
{
return h != null && h.matches("[0-9a-f]{" + MIN_HASH + "," + MAX_HASH + "}");
}

/** True for 1..{@link #CHUNK_CHARS} characters of the base64 alphabet (padding included). */
static boolean validChunk(String data)
{
return data != null && data.matches("[A-Za-z0-9+/=]{1," + CHUNK_CHARS + "}");
}

/**
* A design name as sent or kept: only what the editor allows (ASCII letters, digits, spaces and - _ '), so a
* name from a party member can never carry tags or markup; at most the editor's length, never null.
*/
static String cleanName(String name)
{
String kept = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _'-]", "");
return kept.substring(0, Math.min(kept.length(), CustomDesignRules.MAX_NAME)).trim();
}
}
