package com.gielinorskate.design;

import com.gielinorskate.Text;
import com.gielinorskate.progression.BoardDesigns;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/** The limits on players' own designs: how many, their ids and names, and which images are taken. Pure. */
public final class CustomDesignRules
{
/** Most custom designs kept (all parts together). */
public static final int MAX_DESIGNS = 50;
public static final int MAX_NAME = 24;
/** Largest image accepted: on either side, and its file (20 MB). */
public static final int MAX_SIDE = 4096;
static final int MAX_FILE_MB = 20;
/** The copy kept for editing again (and baked from) is at most this long on its long side. */
public static final int STORED_MAX_SIDE = 1024;

private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _'\\-]+");
private static final Pattern ID = Pattern.compile(Pattern.quote(BoardDesigns.CUSTOM_PREFIX) + "[0-9A-F]{8}");

private CustomDesignRules()
{
}

/** A new id: CUSTOM_ and 8 random hex digits. */
public static String newId(Random random)
{
return BoardDesigns.CUSTOM_PREFIX + String.format(Locale.ROOT, "%08X", random.nextInt());
}

public static boolean isId(String id)
{
return id != null && ID.matcher(id).matches();
}

/** Why {@code name} can't be a design's name, or null when it can (it is trimmed first). */
public static String nameProblem(String name)
{
String n = name == null ? "" : name.trim();
return n.isEmpty() ? Text.get("cr.noname") : n.length() > MAX_NAME ? Text.get("cr.long", MAX_NAME)
: !NAME.matcher(n).matches() ? Text.get("cr.chars") : null;
}

/** Why a file of {@code bytes} can't be used, or null when its size is fine. */
public static String fileProblem(long bytes)
{
return bytes > MAX_FILE_MB << 20 ? Text.get("cr.file", MAX_FILE_MB) : bytes <= 0 ? "That file is empty." : null;
}

/** Why an image of this size can't be used, or null when it can. */
public static String imageProblem(int width, int height)
{
return width > MAX_SIDE || height > MAX_SIDE ? Text.get("cr.big", width, height, MAX_SIDE)
: width < 1 || height < 1 ? "That image is empty." : null;
}

/** {width, height} of an image of this size once downscaled to fit {@link #STORED_MAX_SIDE} (never enlarged). */
public static int[] storedSize(int width, int height)
{
double f = Math.min(1, STORED_MAX_SIDE / (double) Math.max(width, height));
return new int[]{Math.max(1, (int) Math.round(width * f)), Math.max(1, (int) Math.round(height * f))};
}
}
