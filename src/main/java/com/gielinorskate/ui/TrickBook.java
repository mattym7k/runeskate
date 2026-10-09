package com.gielinorskate.ui;

import static com.gielinorskate.ui.ControllerGlyphs.plain;

import com.gielinorskate.Text;
import com.gielinorskate.camera.BoardOrbit;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.input.ButtonTricks;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.leaderboard.TimedRun;
import com.gielinorskate.progression.*;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.*;
import com.gielinorskate.tricks.Gesture.Direction;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.*;

/**
* The Trick Book's content: every move, grouped into sections, worded for the player's settings (keys, flick
* button, mirrored flicks, keyboard tricks, controller mode). Built from the sources of truth, so it cannot drift:
* the {@link Trick} catalogue through {@link TrickGuide} (flicks, "how" lines, points), {@link KeyboardTricks}
* (keys), {@link Grabs} (aims and tweaks), {@link SpinNames}, the scoring and progression constants and the
* board designs' level ladder (designs.json). Pure.
*/
public final class TrickBook
{
/** The settings the wording depends on. A value. */
@AllArgsConstructor
@EqualsAndHashCode
@With
public static final class Settings
{
final String toggleKey;
final String boardKey;
final String manualKey;
/** "right", "left" or "middle". */
final String flickButton;
final boolean mirror;
final boolean mouseTricks;
final boolean keyboardTricks;
final boolean controller;
/** The extra brake key's name, or null / "Not set" when there is none. */
final String brakeKey;
final String leanForwardKey;
final String leanBackKey;
/** The controller layout whose buttons the book names (and lists in its Controller section). */
final PadPreset preset;
/** The preset's name ("Skate 3", "Custom"). */
final String presetName;
}

/** One move or fact: a name, what to do, and optionally its points, flick picture, key and pad buttons. */
@AllArgsConstructor
public static final class Entry
{
/** The catalogue trick this entry is, or null (a control, a spin, a rule). */
public final Trick trick;
public final String name;
/** What to do, plain text. */
public final String detail;
/** "300", "200/s", "+150"; null when it scores nothing by itself. */
public final String points;
/** The flick to draw (already mirrored), or null. */
public final Gesture gesture;
/** Its keyboard-mode key ("1", "Shift+2"), or null. */
public final String key;
/** The pad buttons as glyph tokens ("{LT}"), to draw, or null. */
public final String pad;
}

/** A titled group of entries, with an optional intro line and an optional table (first row: headers). */
@AllArgsConstructor
public static final class Section
{
public final String title;
/** A line under the title, or null. */
public final String intro;
public final List<Entry> entries;
/** Rows of cells, the first the headers; empty when the section has no table. */
public final List<List<String>> table;

Section(String title, String intro, List<Entry> entries)
{
this(title, intro, entries, List.of());
}
}

private TrickBook()
{
}

/** The whole book for {@code s}, in reading order. */
public static List<Section> build(Settings s)
{
// the wording is bundled under book.*
boolean bt = buttons(s);
return List.of(basics(s),
new Section("Flip tricks", Text.get("book.flips." + (bt ? "bt" : s.controller ? "pad" : s.mouseTricks
? "mouse" : "keys"), pad(s, "{@flip}"), s.flickButton), group(s, TrickGuide.Group.FLIPS)),
new Section("Hard flips (Shift)", Text.get("book.shift", s.controller ? pad(s, "{@mod}") : "Shift"),
group(s, TrickGuide.Group.SHIFT)),
spins(s),
grabs(s),
new Section("Manuals", Text.get("book.manuals"), tricks(s, t -> t.kind == TrickKind.MANUAL)),
new Section("Grinds", Text.get("book.grinds" + (bt ? ".bt" : ""), pad(s, "{@grind}")),
tricks(s, t -> t.kind == TrickKind.GRIND)),
offTheBoard(s),
controller(s),
scoring());
}

/** Controller mode with a button-trick layout (Tony Hawk's American Wasteland): tricks on buttons. */
private static boolean buttons(Settings s)
{
return s.controller && s.preset.buttonTricks();
}

private static Entry control(String name, String detail)
{
return new Entry(null, name, detail, null, null, null, null);
}

/**
* A control, worded with keys, or with pad buttons in controller mode ({@code pad}'s action placeholders filled
* in from the preset, see {@link PadWords#resolve}).
*/
private static Entry control(Settings s, String name, String keys, String pad)
{
if (s.controller)
{
String text = PadWords.resolve(pad, s.preset, s.boardKey);
return new Entry(null, name, plain(text), null, null, null, text);
}
return control(name, keys);
}

/**
* The controls in a bundled list, one a line: "name|keys|pad", with the button tricks' pad wording after a
* fourth "|"; "name|detail" for one worded the same everywhere.
*/
private static List<Entry> controls(Settings s, String list)
{
List<Entry> e = new ArrayList<>();
for (String line : list.split("\n"))
{
String[] f = line.split("\\|");
e.add(f.length < 3 ? control(f[0], f[1]) : control(s, f[0], f[1], f[buttons(s) && f.length > 3 ? 3 : 2]));
}
return e;
}

/** The preset's buttons in a pad text, spelt out. */
private static String pad(Settings s, String text)
{
return plain(PadWords.resolve(text, s.preset, s.boardKey));
}

/** "Up arrow" for the arrow keys, the key's own name otherwise. */
static String keyName(String key)
{
return key.matches("Up|Down|Left|Right") ? key + " arrow" : key;
}

private static Section basics(Settings s)
{
String brake = s.brakeKey == null || s.brakeKey.trim().isEmpty() || "Not set".equalsIgnoreCase(s.brakeKey.trim())
? "" : ", or " + s.brakeKey;
List<Entry> e = controls(s, Text.get("book.basics", s.toggleKey, brake));
e.add(trick(s, Trick.OLLIE));
e.add(trick(s, Trick.NOLLIE));
e.addAll(controls(s, Text.get("book.basics2")));
return new Section("Basics", null, e);
}

/** A catalogue trick's entry, worded for the settings. */
static Entry trick(Settings s, Trick t)
{
TrickGuide.Entry g = TrickGuide.entry(t);
// with button tricks the flick pictures would mislead (the right stick turns the camera)
Gesture gesture = g.gesture == null || !s.mouseTricks || buttons(s) ? null
: s.mirror ? KeyboardTricks.mirror(g.gesture) : g.gesture;
String key = s.keyboardTricks ? g.key : null;
String pad = null;
String detail = g.how;
if (s.controller)
{
String how = TrickGuide.controllerHow(t, g.gesture, s.preset);
detail = plain(how);
pad = ControllerGlyphs.hasGlyphs(how) ? how : null;
}
if (!s.mouseTricks && !buttons(s) && (g.gesture != null || key != null))
{
// a flick trick (or a double) without flicks: its key is the way to do it
detail = key != null ? "Press " + key : "";
pad = null;
}
if (t.kind == TrickKind.MANUAL)
detail = detail.replace("the wheelie key", s.manualKey);
if (gesture != null && s.mirror)
detail = detail.replace(ButtonTricks.directionName(g.gesture.direction),
ButtonTricks.directionName(gesture.direction));
return new Entry(t, t.displayName, detail, g.points(), gesture, key, pad);
}

/** The entries of the catalogue tricks {@code which} picks, in catalogue order. */
private static List<Entry> tricks(Settings s, Predicate<Trick> which)
{
return Arrays.stream(Trick.values()).filter(which).map(t -> trick(s, t)).collect(Collectors.toList());
}

private static List<Entry> group(Settings s, TrickGuide.Group group)
{
return tricks(s, t -> TrickGuide.entry(t).group == group);
}

private static Section spins(Settings s)
{
String how = Text.get("book.spins." + (buttons(s) ? "bt" : s.controller ? "pad" : "keys"), pad(s, "{@mod}"));
List<Entry> e = new ArrayList<>();
for (int n = 1; n <= 4; n++)
e.add(new Entry(null, SpinNames.label(n) + " / " + SpinNames.label(-n), how, "+" + SpinNames.bonus(n),
null, null, null));
e.addAll(tricks(s, t -> t.bodyFlipTurns != 0f));
return new Section("Spins & body flips", Text.get("book.spins"), e);
}

private static Section grabs(Settings s)
{
List<Entry> e = tricks(s, t -> t.kind == TrickKind.GRAB);
List<List<String>> table = new ArrayList<>();
if (buttons(s))
{
// with the grab button and a direction
String b = pad(s, "{@grabBtn}");
table.add(List.of("Direction", b + " grab"));
table.add(List.of("None", ButtonTricks.grab(null).displayName));
for (Direction d : TrickGuide.BUTTON_DIRECTIONS)
{
String name = ButtonTricks.directionName(d);
table.add(List.of(Character.toUpperCase(name.charAt(0)) + name.substring(1),
ButtonTricks.grab(d).displayName));
}
return new Section("Grabs", Text.get("book.grabs.bt", b, Grabs.TWEAK_SECONDS, pad(s, "{@mod}")), e, table);
}
String q = s.controller ? pad(s, "{@grabL}") : "Q";
String e2 = s.controller ? pad(s, "{@grabR}") : "E";
String toe = s.mirror ? "Left" : "Right";
String heel = s.mirror ? "Right" : "Left";
table.add(List.of(s.controller ? "Aim (stick)" : "Aim (mouse)", q + " (left)", e2 + " (right)"));
Object[][] aims = {{"None", null}, {"Up (nose)", Grabs.Aim.NOSE}, {"Down (tail)", Grabs.Aim.TAIL},
{toe + " (toe)", Grabs.Aim.TOE}, {heel + " (heel)", Grabs.Aim.HEEL}};
for (Object[] a : aims)
{
Grabs.Aim aim = (Grabs.Aim) a[1];
table.add(List.of((String) a[0], Grabs.pick(true, aim).displayName, Grabs.pick(false, aim).displayName));
}
return new Section("Grabs", Text.get("book.grabs." + (s.controller ? "pad" : "mouse"), q, e2,
Grabs.TWEAK_SECONDS, pad(s, "{@mod}"), keyName(s.leanForwardKey), keyName(s.leanBackKey)), e, table);
}

private static Section offTheBoard(Settings s)
{
List<Entry> e = controls(s, Text.get("book.off", s.boardKey));
e.add(buttons(s) ? control(s, "Turn the camera", "Middle mouse drag", "{RS}")
: control("Turn the camera", "Middle mouse drag"));
return new Section("Off the board", "No tricks or points on foot.", e);
}

/**
* The pad layout of the controller preset (universal AntiMicroX profile), shown whether or not controller mode
* is on: the sticks, then each bound button with what it does.
*/
private static Section controller(Settings s)
{
List<Entry> e = new ArrayList<>();
String[] sticks = Text.get("book.sticks" + (buttons(s) ? ".bt" : ""), BoardOrbit.IDLE_SECONDS).split("\n");
e.add(pad("{LS}", sticks[0]));
e.add(pad("{RS}", sticks[1]));
PadWords.layout(s.preset).forEach((buttons, does) -> e.add(pad(buttons, does)));
return new Section("Controller", Text.get("book.controller", s.presetName), e);
}

/** A pad layout line: its buttons named, then what they do. */
private static Entry pad(String tokens, String detail)
{
String names = ControllerGlyphs.split(tokens).stream().filter(p -> p instanceof ControllerGlyphs.Glyph)
.map(p -> ((ControllerGlyphs.Glyph) p).spoken).collect(Collectors.joining(" / "));
return new Entry(null, names, detail, null, null, null, tokens);
}

private static String percent(float fraction)
{
return Math.round(fraction * 100f) + "%";
}

private static Section scoring()
{
int minutes = Math.round(TimedRun.RUN_SECONDS / 60f);
// the design ladder goes in last: nothing is filled in after it
return new Section("Scoring & levels", null, controls(null, Text.get("book.scoring",
percent(1f - ComboScorer.DECAY_PER_REPEAT), percent(ComboScorer.DECAY_FLOOR),
percent(ComboScorer.FIRST_LANDING_BONUS), Math.round(ComboScorer.LONG_COMBO_SECONDS),
percent(ComboScorer.CLEAN_BONUS), SkateLevels.COMBO_VALUE_PER_XP, SkateLevels.MAX_LEVEL,
SessionGoals.GOALS_PER_SESSION, String.format("%,d", SessionGoals.BONUS_XP), minutes,
designLadder(BoardDesigns.bundled()))));
}

/** "Level 1: RuneSkate, Classic black, ... Level 99: Torva": each design name once, at its level, in order. */
static String designLadder(BoardDesigns designs)
{
Map<Integer, Set<String>> ladder = new TreeMap<>();
for (BoardDesign d : designs.all())
ladder.computeIfAbsent(d.unlock, k -> new LinkedHashSet<>()).add(d.name);
return ladder.entrySet().stream().map(rung -> "Level " + rung.getKey() + ": " + String.join(", ", rung.getValue()))
.collect(Collectors.joining(". "));
}
}
