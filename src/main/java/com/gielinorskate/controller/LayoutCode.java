package com.gielinorskate.controller;

import com.gielinorskate.Text;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
* A controller layout as a short text code to share: {@code RSK1:} then URL-safe base64 (no padding) of four bytes
* per bound button: the button's id character, then the ids of its board, foot and air actions ({@link #SAME_AS_BOARD}
* for no air action of its own). Reading a code is strict: a newer version, an unknown button or action, a button
* twice, an action where it can't be bound, or a code over {@link #MAX_LENGTH} characters is refused with the reason.
*/
public final class LayoutCode
{
public static final String PREFIX = "RSK";
public static final int VERSION = 1;
public static final int MAX_LENGTH = 512;
/** The air byte of a button that does its board action in the air. */
static final int SAME_AS_BOARD = 0xFF;

private static final Pattern HEADER = Pattern.compile("RSK(\\d{1,3}):(.*)", Pattern.DOTALL);
private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]*");

/** A read code: the layout, or why it was refused. */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public static final class Result
{
/** Null when refused. */
public final PadPreset preset;
/** Null when read. */
public final String error;

public boolean ok()
{
return preset != null;
}
}

private LayoutCode()
{
}

/** The code for {@code preset}. */
public static String encode(PadPreset preset)
{
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
for (PadButton b : PadButton.values())
{
PadPreset.Binding binding = preset.binding(b);
if (!binding.isNone())
{
bytes.write(b.id);
bytes.write(binding.board.id);
bytes.write(binding.foot.id);
bytes.write(binding.air == null ? SAME_AS_BOARD : binding.air.id);
}
}
return PREFIX + VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
}

/** Reads {@code code} (surrounding spaces and line breaks are ignored). */
public static Result decode(String code)
{
String c = code == null ? "" : code.trim();
if (c.isEmpty())
return refuse("empty");
if (c.length() > MAX_LENGTH)
return refuse("long", MAX_LENGTH);
Matcher m = HEADER.matcher(c);
if (!m.matches())
return refuse("header", PREFIX + VERSION + ":");
int version = Integer.parseInt(m.group(1));
if (version != VERSION)
return refuse(version > VERSION ? "newer" : "version", version);
String body = m.group(2);
if (!BASE64URL.matcher(body).matches())
return refuse("chars");
byte[] bytes;
try
{
bytes = Base64.getUrlDecoder().decode(body);
if (bytes.length % 4 != 0)
throw new IllegalArgumentException();
}
catch (IllegalArgumentException e)
{
return refuse("missing");
}
PadPreset preset = new PadPreset();
Set<PadButton> seen = EnumSet.noneOf(PadButton.class);
for (int i = 0; i < bytes.length; i += 4)
{
PadButton button = PadButton.forId((char) (bytes[i] & 0xFF));
if (button == null)
return refuse("button");
if (!seen.add(button))
return refuse("twice", button.label);
PadAction board = PadAction.forId(bytes[i + 1] & 0xFF);
PadAction foot = PadAction.forId(bytes[i + 2] & 0xFF);
int airId = bytes[i + 3] & 0xFF;
PadAction air = PadAction.forId(airId);
if (board == null || foot == null || airId != SAME_AS_BOARD && air == null)
return refuse("action");
preset = preset.with(button, new PadPreset.Binding(board, foot, air));
}
String problem = preset.problem();
return problem != null ? refuse("problem", problem) : new Result(preset, null);
}

/** Refused, with the reason under "code." + why in text/overlay.properties. */
private static Result refuse(String why, Object... args)
{
return new Result(null, Text.get("code." + why, args));
}

/**
* What changes going from {@code from} to {@code to}, one line per button and place ("LB, on the board: Hard
* tricks (Shift) to Brake"); empty when they are the same.
*/
public static List<String> changes(PadPreset from, PadPreset to)
{
List<String> out = new ArrayList<>();
for (PadButton b : PadButton.values())
{
for (PadContext c : PadContext.values())
{
if (c == PadContext.AIR && from.binding(b).air == null && to.binding(b).air == null)
// neither has an air action of its own: the board line says it
continue;
PadAction before = from.action(b, c);
PadAction after = to.action(b, c);
if (before != after)
out.add(b.label + ", " + c.label.toLowerCase() + ": " + before.label + " to " + after.label);
}
}
return out;
}
}
