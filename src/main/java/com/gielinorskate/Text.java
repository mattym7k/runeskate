package com.gielinorskate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
* The plugin's bundled words and data tables, kept out of the code in .properties files under
* resources/com/gielinorskate/text (read once, UTF-8). A missing key is a bug: it throws.
*/
public final class Text
{
/** The bundled files, by name; each key lives in exactly one. */
static final List<String> FILES = List.of("ui", "overlay", "session", "data");
private static final Properties ALL = load();

private Text()
{
}

private static Properties load()
{
Properties all = new Properties();
for (String name : FILES)
try (Reader r = new InputStreamReader(Text.class.getResourceAsStream("text/" + name + ".properties"),
StandardCharsets.UTF_8))
{
all.load(r);
}
catch (IOException | NullPointerException e)
{
throw new IllegalStateException("RuneSkate: text/" + name + ".properties could not be read", e);
}
return all;
}

/** The text under {@code key}, with each {N} replaced by args[N]. */
public static String get(String key, Object... args)
{
String v = ALL.getProperty(key);
if (v == null)
throw new IllegalArgumentException("No text " + key);
for (int i = 0; i < args.length; i++)
v = v.replace("{" + i + "}", String.valueOf(args[i]));
return v;
}

/** The lines of the text under {@code key}. */
public static List<String> lines(String key)
{
return List.of(get(key).split("\n"));
}

/** The numbers (separated by commas and/or spaces) under {@code key}. */
public static float[] floats(String key)
{
String[] parts = get(key).trim().split("[,\\s]+");
float[] out = new float[parts.length];
for (int i = 0; i < parts.length; i++)
out[i] = Float.parseFloat(parts[i]);
return out;
}

/** The whole numbers (separated by commas and/or spaces) under {@code key}. */
public static int[] ints(String key)
{
String[] parts = get(key).trim().split("[,\\s]+");
int[] out = new int[parts.length];
for (int i = 0; i < parts.length; i++)
out[i] = Integer.parseInt(parts[i]);
return out;
}

/** Whether {@code key} is bundled. */
static boolean has(String key)
{
return ALL.containsKey(key);
}
}
