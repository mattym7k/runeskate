package com.gielinorskate.party;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.util.*;
import lombok.RequiredArgsConstructor;

/**
* Party members' design pictures on their way in: each offer is checked against the limits before any of its
* chunks is kept, chunks are kept per (member, hash) and only for a known offer, and a picture that made no
* progress (a new chunk, or its offer sent again) for {@link DesignShare#ASSEMBLY_SECONDS} is dropped. A complete picture comes out as PNG bytes,
* at most {@link SharedDesignImage#MAX_BYTES} and matching the offer's hash; nothing here decodes an image.
* Anything else is dropped. Pure; one thread (the client thread).
*/
final class DesignInbox
{
static final int MAX_PENDING = 24;

/** A picture on its way and, once complete and checked, its PNG bytes (not yet decoded). */
@RequiredArgsConstructor
static final class Assembled
{
final long member;
final DesignPart part;
/** As offered (8..16 hex). */
final String offered;
final String name;
final int width;
final int height;
final String[] chunks;
/** The first 8 hex digits of its hash: how ghost updates name it. */
final String hash;
/** When it last made progress: its offer, a repeat of it, or a new chunk. */
float touched;
int received;
int chars;
byte[] png;
}

/** By "member:hash8", oldest first. */
final Map<String, Assembled> pending = new LinkedHashMap<>();

/** An offer from party member {@code member}: true if it is kept (its chunks are then awaited). */
boolean offer(long member, SkateDesignOffer o, float now)
{
expire(now);
DesignPart part = o == null ? null : DesignPart.fromKey(o.part);
// more chunks than 8 KB could fill is a lie too
if (part == null || !DesignShare.validHash(o.hash) || o.totalChunks < 1
|| o.totalChunks > DesignShare.MAX_CHUNKS || (o.totalChunks - 1) * DesignShare.CHUNK_CHARS >= DesignShare.MAX_ENCODED
|| !SharedDesignImage.sizeAllowed(part, o.width, o.height))
return false;
String key = member + ":" + o.hash.substring(0, DesignShare.MIN_HASH);
Assembled p = pending.get(key);
if (p != null && p.offered.equals(o.hash) && p.part == part && p.width == o.width && p.height == o.height
&& p.chunks.length == o.totalChunks)
{
// sent again: the chunks already here still count, and the sender is still at it
p.touched = now;
return true;
}
pending.remove(key);
// a newer design for the same part replaces the one on its way
pending.values().removeIf(q -> q.member == member && q.part == part);
// at most 4 pictures on their way per member (one per part, and a newer one), and MAX_PENDING in all
if (pending.values().stream().filter(q -> q.member == member).count() >= 4)
removeOldest(member);
if (pending.size() >= MAX_PENDING)
removeOldest(null);
p = new Assembled(member, part, o.hash, DesignShare.cleanName(o.name), o.width, o.height,
new String[o.totalChunks], o.hash.substring(0, DesignShare.MIN_HASH));
p.touched = now;
pending.put(key, p);
return true;
}

/** A chunk from party member {@code member}: the picture when this one completes it, else null. */
Assembled chunk(long member, SkateDesignChunk c, float now)
{
expire(now);
if (c == null || !DesignShare.validHash(c.hash))
return null;
String key = member + ":" + c.hash.substring(0, DesignShare.MIN_HASH);
Assembled p = pending.get(key);
// a repeat (the offer was sent again): the first copy stays
if (p == null || !p.offered.equals(c.hash) || c.index < 0 || c.index >= p.chunks.length
|| !DesignShare.validChunk(c.data) || p.chunks[c.index] != null)
return null;
if (p.chars + c.data.length() > DesignShare.MAX_ENCODED)
{
pending.remove(key);
return null;
}
p.chunks[c.index] = c.data;
p.chars += c.data.length();
p.received++;
p.touched = now;
if (p.received < p.chunks.length)
return null;
pending.remove(key);
try
{
p.png = Base64.getDecoder().decode(String.join("", p.chunks));
}
catch (IllegalArgumentException e)
{
// not base64
return null;
}
return p.png.length <= SharedDesignImage.MAX_BYTES && DesignShare.fullHash(p.png).startsWith(p.offered) ? p
: null;
}

/** Drops pictures that made no progress for {@link DesignShare#ASSEMBLY_SECONDS}. */
void expire(float now)
{
pending.values().removeIf(p -> now - p.touched > DesignShare.ASSEMBLY_SECONDS);
}

/** A member left: their pictures on the way are dropped. */
void forget(long member)
{
pending.values().removeIf(p -> p.member == member);
}

void clear()
{
pending.clear();
}

/** Removes the oldest picture on its way (of {@code member}, or of anyone when null). */
private void removeOldest(Long member)
{
for (Iterator<Assembled> it = pending.values().iterator(); it.hasNext(); )
{
long m = it.next().member;
if (member == null || m == member)
{
it.remove();
return;
}
}
}
}
