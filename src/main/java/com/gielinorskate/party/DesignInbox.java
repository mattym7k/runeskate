package com.gielinorskate.party;

import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Party members' design pictures on their way in: each offer is checked against the limits before any of its
 * chunks is kept, chunks are kept per (member, hash) and only for a known offer, and a picture that made no
 * progress (a new chunk, or its offer sent again) for {@link DesignShare#ASSEMBLY_SECONDS} is dropped. A complete picture comes out as PNG bytes,
 * at most {@link SharedDesignImage#MAX_BYTES} and matching the offer's hash; nothing here decodes an image.
 * Anything else is dropped with a debug line. Pure; one thread (the client thread).
 */
@Slf4j
public final class DesignInbox
{
	/** At most this many pictures on their way per member (one per part, and a newer one) and in all. */
	static final int MAX_PENDING_PER_MEMBER = 4;
	static final int MAX_PENDING = 24;

	/** A complete, checked picture: not yet decoded. */
	public static final class Assembled
	{
		public final long member;
		public final DesignPart part;
		/** The first 8 hex digits of its hash: how ghost updates name it. */
		public final String hash;
		public final String name;
		public final int width;
		public final int height;
		public final byte[] png;

		Assembled(long member, DesignPart part, String hash, String name, int width, int height, byte[] png)
		{
			this.member = member;
			this.part = part;
			this.hash = hash;
			this.name = name;
			this.width = width;
			this.height = height;
			this.png = png;
		}
	}

	private static final class Pending
	{
		final long member;
		final DesignPart part;
		/** As offered (8..16 hex). */
		final String hash;
		final String name;
		final int width;
		final int height;
		final String[] chunks;
		/** When it last made progress: its offer, a repeat of it, or a new chunk. */
		float touched;
		int received;
		int chars;

		Pending(long member, DesignPart part, String hash, String name, int width, int height, int total,
			float started)
		{
			this.member = member;
			this.part = part;
			this.hash = hash;
			this.name = name;
			this.width = width;
			this.height = height;
			this.chunks = new String[total];
			this.touched = started;
		}

		boolean sameOffer(DesignPart p, int w, int h, int total)
		{
			return part == p && width == w && height == h && chunks.length == total;
		}
	}

	/** By "member:hash8", oldest first. */
	private final Map<String, Pending> pending = new LinkedHashMap<>();

	static String key(long member, String hash)
	{
		return member + ":" + hash.substring(0, DesignShare.MIN_HASH);
	}

	/** An offer from party member {@code member}: true if it is kept (its chunks are then awaited). */
	public boolean offer(long member, SkateDesignOffer o, float now)
	{
		expire(now);
		DesignPart part = o == null ? null : DesignPart.fromKey(o.part);
		if (part == null)
		{
			return drop(member, "unknown part");
		}
		if (!DesignShare.validHash(o.hash))
		{
			return drop(member, "bad hash");
		}
		if (o.totalChunks < 1 || o.totalChunks > DesignShare.MAX_CHUNKS)
		{
			return drop(member, "bad chunk count " + o.totalChunks);
		}
		// more chunks than 8 KB could fill is a lie too
		if ((o.totalChunks - 1) * DesignShare.CHUNK_CHARS >= DesignShare.MAX_ENCODED)
		{
			return drop(member, "too many chunks for the size cap");
		}
		if (!SharedDesignImage.sizeAllowed(part, o.width, o.height))
		{
			return drop(member, "size " + o.width + "x" + o.height + " outside the " + part.key + "'s bounds");
		}
		String key = key(member, o.hash);
		Pending p = pending.get(key);
		if (p != null && p.hash.equals(o.hash) && p.sameOffer(part, o.width, o.height, o.totalChunks))
		{
			// sent again: the chunks already here still count, and the sender is still at it
			p.touched = now;
			return true;
		}
		pending.remove(key);
		// a newer design for the same part replaces the one on its way
		int ofMember = 0;
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); )
		{
			Pending q = it.next();
			if (q.member == member && q.part == part)
			{
				it.remove();
			}
			else if (q.member == member)
			{
				ofMember++;
			}
		}
		if (ofMember >= MAX_PENDING_PER_MEMBER)
		{
			removeOldest(member);
		}
		if (pending.size() >= MAX_PENDING)
		{
			removeOldest(null);
		}
		pending.put(key, new Pending(member, part, o.hash, DesignShare.cleanName(o.name), o.width, o.height,
			o.totalChunks, now));
		return true;
	}

	/** A chunk from party member {@code member}: the picture when this one completes it, else null. */
	public Assembled chunk(long member, SkateDesignChunk c, float now)
	{
		expire(now);
		if (c == null || !DesignShare.validHash(c.hash))
		{
			drop(member, "chunk with a bad hash");
			return null;
		}
		String key = key(member, c.hash);
		Pending p = pending.get(key);
		if (p == null || !p.hash.equals(c.hash))
		{
			drop(member, "chunk for no known offer");
			return null;
		}
		if (c.index < 0 || c.index >= p.chunks.length)
		{
			drop(member, "chunk index " + c.index + " out of range");
			return null;
		}
		if (!DesignShare.validChunk(c.data))
		{
			drop(member, "chunk that is not base64 or too long");
			return null;
		}
		if (p.chunks[c.index] != null)
		{
			// a repeat (the offer was sent again): the first copy stays
			return null;
		}
		if (p.chars + c.data.length() > DesignShare.MAX_ENCODED)
		{
			pending.remove(key);
			drop(member, "picture over the size cap");
			return null;
		}
		p.chunks[c.index] = c.data;
		p.chars += c.data.length();
		p.received++;
		p.touched = now;
		if (p.received < p.chunks.length)
		{
			return null;
		}
		pending.remove(key);
		StringBuilder all = new StringBuilder(p.chars);
		for (String s : p.chunks)
		{
			all.append(s);
		}
		byte[] png;
		try
		{
			png = Base64.getDecoder().decode(all.toString());
		}
		catch (IllegalArgumentException e)
		{
			drop(member, "picture is not base64");
			return null;
		}
		if (png.length > SharedDesignImage.MAX_BYTES)
		{
			drop(member, "picture over the size cap");
			return null;
		}
		if (!DesignShare.fullHash(png).startsWith(p.hash))
		{
			drop(member, "picture does not match its hash");
			return null;
		}
		return new Assembled(member, p.part, p.hash.substring(0, DesignShare.MIN_HASH), p.name, p.width, p.height,
			png);
	}

	/** Drops pictures that made no progress for {@link DesignShare#ASSEMBLY_SECONDS}. */
	public void expire(float now)
	{
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); )
		{
			Pending p = it.next();
			if (now - p.touched > DesignShare.ASSEMBLY_SECONDS)
			{
				it.remove();
				log.debug("RuneSkate: design {} from member {} expired incomplete", p.hash, p.member);
			}
		}
	}

	/** A member left: their pictures on the way are dropped. */
	public void forget(long member)
	{
		pending.values().removeIf(p -> p.member == member);
	}

	public void clear()
	{
		pending.clear();
	}

	/** How many pictures are on their way. */
	public int pendingCount()
	{
		return pending.size();
	}

	/** Removes the oldest picture on its way (of {@code member}, or of anyone when null). */
	private void removeOldest(Long member)
	{
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); )
		{
			Pending q = it.next();
			if (member == null || q.member == member)
			{
				it.remove();
				return;
			}
		}
	}

	private static boolean drop(long member, String why)
	{
		log.debug("RuneSkate: design message from member {} dropped: {}", member, why);
		return false;
	}
}
