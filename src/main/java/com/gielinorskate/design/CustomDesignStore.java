package com.gielinorskate.design;

import com.gielinorskate.Text;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.render.DesignColours;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
* Players' own designs on disk, one set of files per design in one folder (the plugin's data folder/designs, through
* RuneLite's {@link Filepath}):
* <pre>
* CUSTOM_XXXXXXXX.json       id, name, part, created, the picked image's size, the placement (format below)
* CUSTOM_XXXXXXXX.high.rgb   the baked colours at High and Normal detail (the shipped designs' colour file format,
* CUSTOM_XXXXXXXX.low.rgb    {@link DesignColours})
* CUSTOM_XXXXXXXX.png        the kept image (at most {@link CustomDesignRules#STORED_MAX_SIDE} on its long side)
* </pre>
* The JSON is written last (and deleted first), so a design without it is never seen. A design whose JSON or image is
* missing or corrupt is skipped with a log line; missing or corrupt colours come back as null (they can be baked
* again from the image). Every call does file IO: never on the client thread or the EDT.
*/
@Slf4j
@RequiredArgsConstructor
public final class CustomDesignStore
{
static final int VERSION = 1;
private static final String JSON = ".json";
private static final String TMP = ".tmp";

private final Filepath dir;

/** A design read back: colours null when their file is missing or broken. */
@RequiredArgsConstructor
public static final class Stored
{
public final CustomDesign design;
public final BufferedImage image;
public final int[] high;
public final int[] low;
}

/**
* Every readable design, oldest first, at most {@link CustomDesignRules#MAX_DESIGNS}, handed over one at a time
* (its image read just before), so only one kept image need be in memory at once. Our temporary files left
* behind by a write that was cut off (the client closed mid-save) are deleted first.
*/
public void loadEach(Consumer<Stored> each)
{
if (!dir.isDirectory())
return;
List<String> names;
try (Stream<Filepath> files = dir.walk(1))
{
names = files.map(Filepath::getFileName).collect(Collectors.toList());
}
catch (IOException | RuntimeException e)
{
log.warn(Text.get("cs.list"), dir, e);
return;
}
List<CustomDesign> found = new ArrayList<>();
for (String name : names)
{
int dot = name.indexOf('.');
String id = dot > 0 ? name.substring(0, dot) : "";
if (!CustomDesignRules.isId(id))
continue;
if (name.endsWith(TMP))
{
try
{
dir.joinSegment(name).deleteIfExists();
}
catch (IOException | RuntimeException e)
{
log.debug(Text.get("cs.tmp"), name, e);
}
}
else if (name.equals(id + JSON))
{
CustomDesign d = design(id);
if (d != null)
found.add(d);
}
}
found.sort(Comparator.comparingLong((CustomDesign d) -> d.created).thenComparing(d -> d.id));
int loaded = 0;
for (CustomDesign d : found)
{
if (loaded == CustomDesignRules.MAX_DESIGNS)
{
log.warn(Text.get("cs.many"), loaded);
return;
}
BufferedImage image = image(d);
if (image == null)
{
log.warn(Text.get("cs.broken"), d.id);
continue;
}
loaded++;
each.accept(new Stored(d, image, colours(d.id + ".high.rgb", d.part), colours(d.id + ".low.rgb", d.part)));
}
}

/** A design's JSON, or null (logged) when it is missing or broken. */
private CustomDesign design(String id)
{
try (Reader r = dir.joinSegment(id + JSON).openBufferedReader())
{
CustomDesign d = parse(r);
if (d.id.equals(id))
return d;
log.warn(Text.get("cs.names"), id, d.id);
}
catch (IOException | RuntimeException e)
{
log.warn(Text.get("cs.unread"), id, e.getMessage());
}
return null;
}

/** Design {@code d}'s kept image, or null when it is missing, broken or not the size its placement is for. */
public BufferedImage image(CustomDesign d)
{
try (InputStream in = dir.joinSegment(d.id + ".png").openInputStream())
{
BufferedImage image = DesignImages.readImage(in);
return image != null && image.getWidth() == d.placement.imageWidth
&& image.getHeight() == d.placement.imageHeight ? image : null;
}
catch (IOException | RuntimeException e)
{
return null;
}
}

private int[] colours(String file, DesignPart part)
{
try (InputStream in = dir.joinSegment(file).openInputStream())
{
return DesignColours.parse(in.readAllBytes(), part.index);
}
catch (IOException | IllegalArgumentException e)
{
log.warn(Text.get("cs.colours"), file, e.getMessage());
return null;
}
}

/**
* Saves a design: its image (unless null: kept as it is), its colours (unless null) and then its JSON.
*
* @throws IOException when it couldn't be written
*/
public void save(CustomDesign d, BufferedImage image, int[] high, int[] low) throws IOException
{
dir.createDirectories();
if (image != null)
write(d.id + ".png", out -> DesignImages.writePng(image, out));
if (high != null)
write(d.id + ".high.rgb", out -> out.write(DesignColours.pack(d.part.index, high)));
if (low != null)
write(d.id + ".low.rgb", out -> out.write(DesignColours.pack(d.part.index, low)));
write(d.id + JSON, out -> out.write(toJson(d).getBytes(StandardCharsets.UTF_8)));
}

/** Deletes a design's files (its JSON first); missing ones are fine. */
public void delete(String id) throws IOException
{
for (String file : new String[]{JSON, ".png", ".high.rgb", ".low.rgb"})
dir.joinSegment(id + file).deleteIfExists();
}

private interface Body
{
void write(OutputStream out) throws IOException;
}

/** Writes a file through a temporary one beside it, then moves it into place. */
private void write(String name, Body body) throws IOException
{
Filepath target = dir.joinSegment(name);
Filepath tmp = dir.joinSegment(name + TMP);
try
{
try (OutputStream out = tmp.openOutputStream())
{
body.write(out);
}
try
{
tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
}
catch (AtomicMoveNotSupportedException e)
{
tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
}
}
catch (IOException | RuntimeException e)
{
try
{
tmp.deleteIfExists();
}
catch (IOException | RuntimeException d)
{
e.addSuppressed(d);
}
throw e;
}
}

static String toJson(CustomDesign d) throws IOException
{
StringWriter sw = new StringWriter();
try (JsonWriter w = new JsonWriter(sw))
{
ImagePlacement p = d.placement;
w.setIndent("  ");
w.beginObject().name("version").value(VERSION).name("id").value(d.id).name("name").value(d.name)
.name("part").value(d.part.key).name("created").value(d.created)
.name("source").beginObject().name("width").value(d.sourceWidth).name("height").value(d.sourceHeight)
.endObject()
.name("placement").beginObject().name("imageWidth").value(p.imageWidth)
.name("imageHeight").value(p.imageHeight).name("cx").value(p.cx).name("cy").value(p.cy)
// one scale as before the stretch handles (older versions read it); a stretch adds both
.name("scale").value(p.meanScale());
if (!p.uniform())
w.name("scaleX").value(p.scaleX).name("scaleY").value(p.scaleY);
w.name("turns").value(p.turns).name("flipped").value(p.flipped).endObject().endObject();
}
return sw.toString();
}

/** A design's JSON; a RuntimeException when it is malformed or has a bad field. */
static CustomDesign parse(Reader reader)
{
JsonObject o = new JsonParser().parse(reader).getAsJsonObject();
JsonObject source = o.getAsJsonObject("source");
JsonObject p = o.getAsJsonObject("placement");
String id = o.get("id").getAsString();
String name = o.get("name").getAsString();
DesignPart part = DesignPart.fromKey(o.get("part").getAsString());
int w = p.get("imageWidth").getAsInt();
int h = p.get("imageHeight").getAsInt();
if (o.get("version").getAsInt() > VERSION || !CustomDesignRules.isId(id) || part == null
|| CustomDesignRules.nameProblem(name) != null || Math.max(w, h) > CustomDesignRules.STORED_MAX_SIDE)
throw new IllegalArgumentException("made by a newer RuneSkate, or a field is bad");
// saved unstretched: one scale both ways
double scale = p.get("scale").getAsDouble();
return new CustomDesign(id, name.trim(), part, o.get("created").getAsLong(), source.get("width").getAsInt(),
source.get("height").getAsInt(), new ImagePlacement(w, h, p.get("cx").getAsDouble(),
p.get("cy").getAsDouble(), p.has("scaleX") ? p.get("scaleX").getAsDouble() : scale,
p.has("scaleY") ? p.get("scaleY").getAsDouble() : scale, p.get("turns").getAsInt(),
p.get("flipped").getAsBoolean()));
}
}
