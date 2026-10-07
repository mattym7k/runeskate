package com.gielinorskate.party;

import com.gielinorskate.design.CustomDesignRules;
import com.gielinorskate.design.SharedDesignImage;
import com.gielinorskate.progression.DesignPart;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;

/**
 * The rules and limits of sharing custom designs with the party, and the messages one shared design goes out as.
 * Pure.
 */
public final class DesignShare
{
	/** Most base64 characters in one chunk. */
	public static final int CHUNK_CHARS = 1000;
	/** Most chunks an offer may announce. */
	public static final int MAX_CHUNKS = 12;
	/** Most base64 characters in one part's picture (8 KB). */
	public static final int MAX_ENCODED = SharedDesignImage.MAX_ENCODED;
	/** An incomplete picture is dropped after this long without progress (a new chunk or its offer again), seconds. */
	public static final float ASSEMBLY_SECONDS = 30f;
	/**
	 * A design whose chunks are still going out this long after its offer is offered again before the rest, so
	 * the receivers keep it (well inside their {@link #ASSEMBLY_SECONDS}), seconds.
	 */
	public static final float RESEND_OFFER_SECONDS = 20f;
	/** Most complete designs a receiver keeps (least recently used go first). */
	public static final int CACHE_SIZE = 20;
	/** A member's ghost appearing sends our designs again at most this often per member, seconds. */
	public static final float REOFFER_SECONDS = 60f;
	/** Shortest and longest hash an offer may carry, hex digits. */
	public static final int MIN_HASH = 8;
	public static final int MAX_HASH = 16;

	private DesignShare()
	{
	}

	/** One of our designs as it goes to the party: its offer and its chunks. Immutable. */
	public static final class Outgoing
	{
		/** The local design's id (CUSTOM_...), so a ghost update can name it. */
		public final String designId;
		public final DesignPart part;
		/** 8 lowercase hex digits. */
		public final String hash;
		public final String name;
		public final int width;
		public final int height;
		public final List<String> chunks;

		Outgoing(String designId, DesignPart part, String hash, String name, int width, int height,
			List<String> chunks)
		{
			this.designId = designId;
			this.part = part;
			this.hash = hash;
			this.name = name;
			this.width = width;
			this.height = height;
			this.chunks = chunks;
		}

		/** The offer, then every chunk in order. */
		public List<PartyMessage> messages()
		{
			List<PartyMessage> out = new ArrayList<>(chunks.size() + 1);
			out.add(offer());
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

		SkateDesignOffer offer()
		{
			SkateDesignOffer o = new SkateDesignOffer();
			o.part = part.key;
			o.hash = hash;
			o.name = name;
			o.totalChunks = chunks.size();
			o.width = width;
			o.height = height;
			return o;
		}
	}

	/**
	 * Our design {@code designId} ready to share as {@code picture}; null when the picture breaks a limit (it never
	 * should: {@link SharedDesignImage#encode} keeps to them).
	 */
	public static Outgoing outgoing(String designId, String name, SharedDesignImage.Encoded picture)
	{
		if (picture == null || picture.base64.length() > MAX_ENCODED
			|| !SharedDesignImage.sizeAllowed(picture.part, picture.width, picture.height))
		{
			return null;
		}
		List<String> chunks = chunks(picture.base64);
		if (chunks.isEmpty() || chunks.size() > MAX_CHUNKS)
		{
			return null;
		}
		return new Outgoing(designId, picture.part, hash(picture.png), cleanName(name), picture.width,
			picture.height, chunks);
	}

	/** {@code base64} in pieces of at most {@link #CHUNK_CHARS}. */
	public static List<String> chunks(String base64)
	{
		List<String> out = new ArrayList<>();
		for (int i = 0; i < base64.length(); i += CHUNK_CHARS)
		{
			out.add(base64.substring(i, Math.min(base64.length(), i + CHUNK_CHARS)));
		}
		return Collections.unmodifiableList(out);
	}

	/** The first 8 hex digits (lowercase) of the SHA-256 of {@code data}. */
	public static String hash(byte[] data)
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
		StringBuilder sb = new StringBuilder(MAX_HASH);
		for (int i = 0; i < MAX_HASH / 2; i++)
		{
			sb.append(Character.forDigit(d[i] >> 4 & 15, 16)).append(Character.forDigit(d[i] & 15, 16));
		}
		return sb.toString();
	}

	/** True for 8 to 16 lowercase hex digits. */
	public static boolean validHash(String h)
	{
		if (h == null || h.length() < MIN_HASH || h.length() > MAX_HASH)
		{
			return false;
		}
		for (int i = 0; i < h.length(); i++)
		{
			char c = h.charAt(i);
			if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f'))
			{
				return false;
			}
		}
		return true;
	}

	/** True for 1..{@link #CHUNK_CHARS} characters of the base64 alphabet (padding included). */
	static boolean validChunk(String data)
	{
		if (data == null || data.isEmpty() || data.length() > CHUNK_CHARS)
		{
			return false;
		}
		for (int i = 0; i < data.length(); i++)
		{
			char c = data.charAt(i);
			if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/'
				|| c == '='))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A design name as sent or kept: only what the editor allows (ASCII letters, digits, spaces and - _ '), so a
	 * name from a party member can never carry tags or markup; at most the editor's length, never null.
	 */
	static String cleanName(String name)
	{
		if (name == null)
		{
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < name.length() && sb.length() < CustomDesignRules.MAX_NAME; i++)
		{
			char c = name.charAt(i);
			if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == ' ' || c == '_'
				|| c == '\'' || c == '-')
			{
				sb.append(c);
			}
		}
		return sb.toString().trim();
	}
}
