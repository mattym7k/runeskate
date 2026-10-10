package com.gielinorskate.progression;

import com.gielinorskate.*;
import com.gielinorskate.duel.DuelRecord;
import com.gielinorskate.feedback.HudAnim;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.leaderboard.LeaderboardStore;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.ui.ProgressState;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;

/**
* Skate progression for the logged-in account: XP from landed combos, level-ups (chat line in the game's wording,
* graphic and bells through {@link SkateFeedback}, a RuneLite notification, a HUD banner every ten levels), the
* board designs, the daily goals, and the side panel's "Skating" section. Client thread.
*/
@Singleton
public class ProgressionService
{
@Inject
private SkateChat skateChat;
private final Notifier notifier;
private final GielinorSkateConfig config;
private final SkateFeedback feedback;
private final ScoreClock clock;
private final Progression progression;
private final DailyGoals daily;
private final Random random = new Random();
/** The goal last completed (for the HUD's "Goal complete" line), and when. */
private String goalFlashText;
private float goalFlashAt = Float.NEGATIVE_INFINITY;

/** The level banner showing (every ten levels), and when it started. */
private String bannerText;
private float bannerAt = Float.NEGATIVE_INFINITY;

/** Who gets the panel's progress section when it changes (the panel, through invokeLater), and what it got last. */
private Consumer<ProgressState> listener;
private ProgressState lastState;
/** Who recolours the board when the designs in use change (the session). */
private Consumer<BoardLook> lookListener;

@Inject
ProgressionService(ConfigManager configManager, Notifier notifier, GielinorSkateConfig config,
SkateFeedback feedback, ScoreClock clock)
{
this.notifier = notifier;
this.config = config;
this.feedback = feedback;
this.clock = clock;
ProfileStore store = new ConfigProfileStore(configManager);
progression = new Progression(store);
daily = new DailyGoals(store);
}

/** The current UTC day: the daily goals turn over at UTC midnight. */
private static LocalDate today()
{
return LocalDate.now(ZoneOffset.UTC);
}

/** True for the per-account progress keys saved in the RuneScape profile (they are not settings). */
public static boolean isProgressKey(String key)
{
return Progression.XP_KEY.equals(key) || Progression.DESIGN_KEYS.contains(key) || DailyGoals.DAY_KEY.equals(key)
|| DailyGoals.STATE_KEY.equals(key) || LeaderboardStore.isKey(key) || DuelRecord.isKey(key);
}

/**
* The account (RuneScape profile) changed or the plugin started: save the old one's XP, load the new one's XP
* and its goals for the day.
*/
public void load()
{
BoardLook before = progression.look();
progression.load();
daily.load(today(), random);
goalFlashText = null;
lookMaybeChanged(before);
}

/** Saves pending XP when it's due (debounced). Called every game tick. */
public void tick()
{
progression.tick(clock.now());
}

/** Saves pending XP now (skating stopped, logout, plugin stop). */
public void flush()
{
progression.flush();
}

/** Skating started: the day's goals carry on (new ones only once the UTC day has turned). */
public void startSession()
{
daily.startSession(today(), random);
goalFlashText = null;
publish();
}

/** A combo landed: its XP, then any session goals it completed (each worth bonus XP). */
public void onComboLanded(ComboScorer.Summary combo)
{
addXp(SkateLevels.xpForCombo(combo.value));
for (SessionGoals.Goal g : daily.onLanded(combo, today(), random))
{
float now = clock.now();
skateChat.send(Text.get("ps.daily", String.format("%,d", SessionGoals.BONUS_XP), g.type.text));
feedback.onGoalComplete(now);
goalFlashText = "Goal complete: " + g.type.text;
goalFlashAt = now;
addXp(SessionGoals.BONUS_XP);
}
publish();
}

/** The HUD's goal line ("Goal: Land 5 different flips 3/5"), or null when hidden or there are none. */
public String getGoalLine()
{
SessionGoals goals = daily.goals();
if (!config.showSessionGoals() || goals.goals().isEmpty())
return null;
SessionGoals.Goal g = goals.current();
return isGoalFlashing() ? goalFlashText : g == null ? "All daily goals done!"
: "Goal: " + g.type.text + " (" + g.progressText() + ")";
}

/** True while the goal line names a just-completed goal (drawn highlighted). */
public boolean isGoalFlashing()
{
return goalFlashText != null && HudAnim.goalFlashAlpha(clock.now() - goalFlashAt) > 0f;
}

/** Adds Skate XP, with the level-up fanfare when a level is reached. */
private void addXp(int amount)
{
float now = clock.now();
BoardLook lookBefore = progression.look();
LevelUp up = progression.addXp(amount, now);
if (up != null)
{
String message = up.message();
skateChat.send(message);
feedback.onLevelUp(up.to, now);
// follows the player's RuneLite notification settings (and this setting's own overrides)
notifier.notify(config.levelUpNotification(), message);
if (up.banner() != null)
{
bannerText = up.banner();
bannerAt = now;
}
// the chat drops a null line
skateChat.send(unlockMessage(BoardDesigns.bundled().unlockedBetween(up.from, up.to)));
}
lookMaybeChanged(lookBefore);
}

/**
* "New board design unlocked: Rune deck. Pick it in the RuneSkate side panel.", or for several "New board designs
* unlocked: Bandos, Armadyl, Guthix, Zamorak and Saradomin grip, deck and wheels. Pick them ...": designs of one
* name and level go together, and names of one level with the same parts too. Null for none.
*/
static String unlockMessage(List<BoardDesign> unlocked)
{
if (unlocked.isEmpty())
return null;
// each name (keyed "level:name") with its parts, in order of first appearance
Map<String, EnumSet<DesignPart>> parts = new LinkedHashMap<>();
for (BoardDesign d : unlocked)
parts.computeIfAbsent(d.unlock + ":" + d.name, k -> EnumSet.noneOf(DesignPart.class)).add(d.part);
// names of one level and the same parts share one phrase, keyed "level:their parts' words"
Map<String, List<String>> groups = new LinkedHashMap<>();
parts.forEach((key, set) -> groups.computeIfAbsent(key.substring(0, key.indexOf(':')) + ":"
+ andList(set.stream().map(p -> p.key).collect(Collectors.toList())), k -> new ArrayList<>())
.add(key.substring(key.indexOf(':') + 1)));
return Text.get("ps.unlock." + (unlocked.size() == 1), groups.entrySet().stream()
.map(g -> andList(g.getValue()) + " " + g.getKey().substring(g.getKey().indexOf(':') + 1))
.collect(Collectors.joining("; ")));
}

/** "a", "a and b", "a, b and c". */
static String andList(List<String> items)
{
int last = items.size() - 1;
return last < 1 ? String.join("", items) : String.join(", ", items.subList(0, last)) + " and " + items.get(last);
}

/** The board's designs in use: the chosen ones, or each part's default while none is chosen (or it is locked). */
public BoardLook look()
{
return progression.look();
}

/** The panel picked a design: saved for this account and put on the board (a locked one is ignored). */
public void selectDesign(BoardDesign design)
{
BoardLook before = progression.look();
progression.select(design);
lookMaybeChanged(before);
}

/** Who recolours the board when the designs in use change. */
public void setLookListener(Consumer<BoardLook> listener)
{
lookListener = listener;
}

/** Tells the look listener when the look differs from {@code before}, and the panel when its state changed. */
private void lookMaybeChanged(BoardLook before)
{
BoardLook now = progression.look();
Consumer<BoardLook> l = lookListener;
if (!now.equals(before) && l != null)
l.accept(now);
publish();
}

/** The loaded account's Skate XP (0 when none is loaded). */
public int xp()
{
return progression.xp();
}

/** The RuneScape profile the XP was loaded from, or null. */
public String profile()
{
return progression.profile();
}

/** The level banner ("Skating level 50!") if one is showing, else null. */
public String getBannerText()
{
return getBannerAge() < HudAnim.BANNER_SECONDS ? bannerText : null;
}

/** Seconds since the banner started. */
public float getBannerAge()
{
return clock.now() - bannerAt;
}

/** Who gets the panel's progress section when it changes (the panel, through invokeLater). */
public void setListener(Consumer<ProgressState> listener)
{
this.listener = listener;
lastState = null;
}

/** Sends the panel its progress state if it changed. */
private void publish()
{
int xp = progression.xp();
ProgressState s = new ProgressState(progression.level(), xp, SkateLevels.xpToNext(xp), SkateLevels.progress(xp),
progression.look(), daily.goals().goals().stream().map(g -> new ProgressState.GoalView(g.type.text,
g.progressText(), g.isDone())).collect(Collectors.toList()));
Consumer<ProgressState> l = listener;
if (l != null && !s.equals(lastState))
{
lastState = s;
l.accept(s);
}
}

/** The plugin's config group in the logged-in account's RuneScape profile. */
@RequiredArgsConstructor
public static final class ConfigProfileStore implements ProfileStore
{
private final ConfigManager configManager;

@Override
public String profile()
{
return configManager.getRSProfileKey();
}

@Override
public String get(String profile, String key)
{
return configManager.getConfiguration(GielinorSkateConfig.GROUP, profile, key);
}

@Override
public void set(String profile, String key, String value)
{
if (profile.equals(configManager.getRSProfileKey()))
configManager.setRSProfileConfiguration(GielinorSkateConfig.GROUP, key, value);
else
// the account changed before the save: into the profile the value was loaded from
configManager.setConfiguration(GielinorSkateConfig.GROUP, profile, key, value);
}
}
}
