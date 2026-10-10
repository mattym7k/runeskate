package com.gielinorskate.party;

import java.util.Arrays;

/**
 * Compact text for ghost updates: variable-length numbers in a 64-character alphabet that JSON (and Gson's HTML
 * escaping) leaves alone, and the exact length of an update's JSON as RuneLite's party client sends it, so the
 * sender can fit the optional timeline into the size bound. Pure.
 */
final class GhostWire
{
	/** Indices 0..31 end a number, 32..63 carry 5 more bits of it. None of them is escaped in JSON. */
	static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
	/**
	 * The biggest update, in characters of JSON (GhostWireFormatTest): the busiest update without a timeline
	 * measures at most this already, and the timeline only goes in the room left under it.
	 */
	static final int MAX_UPDATE_CHARS = 284;
	private static final int[] VALUE = new int[128];

	static
	{
		Arrays.fill(VALUE, -1);
		for (int i = 0; i < ALPHABET.length(); i++)
			VALUE[ALPHABET.charAt(i)] = i;
	}

	private GhostWire()
	{
	}

	/** Appends {@code v} (0 or more): low 5-bit groups first, each but the last as a continuation character. */
	static void putUnsigned(StringBuilder sb, int v)
	{
		int u = Math.max(0, v);
		while (u >= 32)
		{
			sb.append(ALPHABET.charAt(32 + (u & 31)));
			u >>>= 5;
		}
		sb.append(ALPHABET.charAt(u));
	}

	/** Appends {@code v} zigzagged, so small negative numbers are short too. */
	static void putSigned(StringBuilder sb, int v)
	{
		putUnsigned(sb, (v << 1) ^ (v >> 31));
	}

	/** Appends the low {@code 6 * digits} bits of {@code v} in exactly {@code digits} characters, high first. */
	static void putFixed(StringBuilder sb, int v, int digits)
	{
		for (int i = digits - 1; i >= 0; i--)
			sb.append(ALPHABET.charAt((v >>> (6 * i)) & 63));
	}

	/** Reads what the put methods wrote; on junk it stops and says so ({@link #ok}), never throws. */
	static final class Reader
	{
		private final String s;
		private int pos;
		private boolean ok = true;

		Reader(String s)
		{
			this.s = s == null ? "" : s;
		}

		boolean ok()
		{
			return ok;
		}

		private int digit()
		{
			if (!ok || pos >= s.length())
			{
				ok = false;
				return 0;
			}
			char c = s.charAt(pos++);
			int v = c < 128 ? VALUE[c] : -1;
			if (v < 0)
			{
				ok = false;
				return 0;
			}
			return v;
		}

		int unsigned()
		{
			long v = 0;
			// A number never takes more characters than this (35 bits): anything longer is junk.
			for (int i = 0; i < 7; i++)
			{
				int d = digit();
				if (!ok)
					return 0;
				v |= (long) (d & 31) << (5 * i);
				if (d < 32)
				{
					if (v > Integer.MAX_VALUE)
					{
						ok = false;
						return 0;
					}
					return (int) v;
				}
			}
			ok = false;
			return 0;
		}

		int signed()
		{
			int u = unsigned();
			return (u >>> 1) ^ -(u & 1);
		}

		int fixed(int digits)
		{
			int v = 0;
			for (int i = 0; i < digits; i++)
				v = (v << 6) | digit();
			return ok ? v : 0;
		}
	}

	/** The JSON {@code "type":"SkateGhostUpdate"} RuneLite's party client puts first, and the braces. */
	private static final int FRAME = "{\"type\":\"SkateGhostUpdate\"}".length();

	/**
	 * Exactly how long {@code m}'s JSON is as RuneLite's party client writes it (Gson with HTML escaping, nulls left
	 * out, the member left out): GhostWireTest checks this against Gson itself.
	 */
	static int jsonLength(SkateGhostUpdate m)
	{
		return FRAME + num("w", m.w) + num("p", m.p) + num("x", m.x) + num("y", m.y) + num("h", m.h) + num("hd", m.hd)
			+ num("tw", m.tw) + num("vx", m.vx) + num("vy", m.vy) + num("vh", m.vh) + num("bf", m.bf)
			+ num("bw", m.bw) + num("ev", m.ev) + num("ft", m.ft) + num("seq", m.seq) + str("st", m.st)
			+ str("hold", m.hold) + str("tr", m.tr) + str("dk", m.dk) + str("gw", m.gw) + str("ob", m.ob) + str("tj", m.tj)
			+ num("bx", m.bx) + num("by", m.by) + num("bh", m.bh) + num("bd", m.bd) + num("cr", m.cr)
			+ num("kd", m.kd);
	}

	/** {@code ,"name":v}, or nothing for null. */
	private static int num(String name, Integer v)
	{
		return v == null ? 0 : 4 + name.length() + digits(v);
	}

	private static int str(String name, String v)
	{
		return v == null ? 0 : 6 + name.length() + v.chars().map(c -> escaped((char) c)).sum();
	}

	/** Characters Gson writes for {@code c} in a string. */
	private static int escaped(char c)
	{
		return "\"\\\t\b\n\r\f".indexOf(c) >= 0 ? 2 : c < 0x20 || "<>&='\u2028\u2029".indexOf(c) >= 0 ? 6 : 1;
	}

	static int digits(int v)
	{
		return Integer.toString(v).length();
	}
}
