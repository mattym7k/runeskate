package com.gielinorskate.progression;

import com.gielinorskate.SkateChat;
import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.duel.DuelRecord;
import com.gielinorskate.feedback.HudAnim;
import com.gielinorskate.feedback.SkateFeedback;
import com.gielinorskate.leaderboard.LeaderboardStore;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.scoring.ScoreClock;
import com.gielinorskate.ui.ProgressState;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;

/**
 * Skate progression for the logged-in account: XP from landed combos, level-ups (chat line in the game's wording,
 * graphic and bells through {@link SkateFeedback}, a RuneLite notification, a HUD banner every ten levels), the
 * board designs, the daily goals, and the side panel's "Skating" section. Client thread.
 */
@Slf4j
@Singleton
public class ProgressionService
{
	private final Client client;
	@Inject
	private SkateChat skateChat;
	private final Notifier notifier;
	private final GielinorSkateConfig config;
	private final SkateFeedback feedback;
	private final ScoreClock clock;
	private final Progression progression;
	private final DailyGoals daily;
	private final SessionGoals goals;
	private final Random random = new Random();
	/** The goal last completed (for the HUD's "Goal complete" line), and when. */
	private String goalFlashText;
	private float goalFlashAt = Float.NEGATIVE_INFINITY;

	/** The level banner showing (every ten levels), and when it started. */
	private String bannerText;
	private float bannerAt = Float.NEGATIVE_INFINITY;

	private Consumer<ProgressState> listener;
	private ProgressState lastState;
	/** Who recolours the board when the designs in use change (the session). */
	private Consumer<BoardLook> lookListener;
	private final BoardDesigns designs = BoardDesigns.bundled();

	@Inject
	ProgressionService(Client client, ConfigManager configManager, Notifier notifier, GielinorSkateConfig config,
		SkateFeedback feedback, ScoreClock clock)
	{
		this.client = client;
		this.notifier = notifier;
		this.config = config;
		this.feedback = feedback;
		this.clock = clock;
		ProfileStore store = new ConfigProfileStore(configManager);
		this.progression = new Progression(store);
		this.daily = new DailyGoals(store);
		this.goals = daily.goals();
	}

	/** The current UTC day: the daily goals turn over at UTC midnight. */
	private static LocalDate today()
	{
		return LocalDate.now(ZoneOffset.UTC);
	}

	/** True for the per-account progress keys saved in the RuneScape profile (they are not settings). */
	public static boolean isProgressKey(String key)
	{
		return Progression.XP_KEY.equals(key) || Progression.DECK_KEY.equals(key)
			|| Progression.GRIP_KEY.equals(key) || Progression.WHEELS_KEY.equals(key)
			|| Progression.DEV_LEVEL_KEY.equals(key) || DailyGoals.DAY_KEY.equals(key)
			|| DailyGoals.STATE_KEY.equals(key) || LeaderboardStore.isKey(key)
			|| DuelRecord.isKey(key);
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
		publish();
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
			skateChat.send("Daily goal complete: " + g.type.text
				+ " (+" + String.format("%,d", SessionGoals.BONUS_XP) + " Skating XP).");
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
		if (!config.showSessionGoals() || goals.goals().isEmpty())
		{
			return null;
		}
		float age = clock.now() - goalFlashAt;
		if (goalFlashText != null && HudAnim.goalFlashAlpha(age) > 0f)
		{
			return goalFlashText;
		}
		SessionGoals.Goal g = goals.current();
		return g == null ? "All daily goals done!" : "Goal: " + g.type.text + " (" + g.progressText() + ")";
	}

	/** True while the goal line names a just-completed goal (drawn highlighted). */
	public boolean isGoalFlashing()
	{
		return goalFlashText != null && HudAnim.goalFlashAlpha(clock.now() - goalFlashAt) > 0f;
	}

	/** Adds Skate XP, with the level-up fanfare when a level is reached. */
	public void addXp(int amount)
	{
		float now = clock.now();
		BoardLook lookBefore = progression.look();
		LevelUp up = progression.addXp(amount, now);
		if (up != null)
		{
			levelUp(up, now);
		}
		lookMaybeChanged(lookBefore);
		publish();
	}

	private void levelUp(LevelUp up, float now)
	{
		String message = up.message();
		skateChat.send(message);
		feedback.onLevelUp(up.to, now);
		// follows the player's RuneLite notification settings (and this setting's own overrides)
		notifier.notify(config.levelUpNotification(), message);
		String banner = up.banner();
		if (banner != null)
		{
			bannerText = banner;
			bannerAt = now;
		}
		String unlocked = unlockMessage(designs.unlockedBetween(up.from, up.to));
		if (unlocked != null)
		{
			skateChat.send(unlocked);
		}
	}

	/**
	 * "New board design unlocked: Rune deck. Pick it in the RuneSkate side panel.", or for several "New board designs
	 * unlocked: Bandos, Armadyl, Guthix, Zamorak and Saradomin grip, deck and wheels. Pick them ...": designs of one
	 * name and level go together, and names of one level with the same parts too. Null for none.
	 */
	static String unlockMessage(List<BoardDesign> unlocked)
	{
		if (unlocked.isEmpty())
		{
			return null;
		}
		// each name (at its level) with its parts, in order of first appearance
		Map<String, EnumSet<DesignPart>> parts = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (BoardDesign d : unlocked)
		{
			String key = d.unlock + ":" + d.name;
			parts.computeIfAbsent(key, k -> EnumSet.noneOf(DesignPart.class)).add(d.part);
			names.put(key, d.name);
			levels.put(key, d.unlock);
		}
		// names of one level and the same parts share one phrase
		Map<String, List<String>> groups = new LinkedHashMap<>();
		Map<String, EnumSet<DesignPart>> groupParts = new LinkedHashMap<>();
		for (Map.Entry<String, EnumSet<DesignPart>> e : parts.entrySet())
		{
			String g = levels.get(e.getKey()) + ":" + e.getValue();
			groups.computeIfAbsent(g, k -> new ArrayList<>()).add(names.get(e.getKey()));
			groupParts.put(g, e.getValue());
		}
		List<String> phrases = new ArrayList<>();
		for (Map.Entry<String, List<String>> g : groups.entrySet())
		{
			List<String> labels = new ArrayList<>();
			for (DesignPart p : groupParts.get(g.getKey()))
			{
				labels.add(p.key);
			}
			phrases.add(andList(g.getValue()) + " " + andList(labels));
		}
		boolean one = unlocked.size() == 1;
		return (one ? "New board design unlocked: " : "New board designs unlocked: ") + String.join("; ", phrases)
			+ (one ? ". Pick it" : ". Pick them") + " in the RuneSkate side panel.";
	}

	/** "a", "a and b", "a, b and c". */
	static String andList(List<String> items)
	{
		if (items.size() <= 1)
		{
			return items.isEmpty() ? "" : items.get(0);
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
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
		publish();
	}

	/**
	 * The player's own designs were loaded, edited or deleted: the board is drawn again in the designs in use (a
	 * saved custom design that is now there, or gone, or in new colours) and the panel told. Client thread.
	 */
	public void designsChanged()
	{
		Consumer<BoardLook> l = lookListener;
		if (l != null)
		{
			// the renderer skips a look it already draws
			l.accept(progression.look());
		}
		publish();
	}

	/** Who recolours the board when the designs in use change. */
	public void setLookListener(Consumer<BoardLook> listener)
	{
		lookListener = listener;
	}

	private void lookMaybeChanged(BoardLook before)
	{
		BoardLook now = progression.look();
		Consumer<BoardLook> l = lookListener;
		if (!now.equals(before) && l != null)
		{
			l.accept(now);
		}
	}

	/**
	 * Developer-mode {@code ::skatelevel [1-99]}: shows the level, or sets the XP to the start of a level (saved,
	 * and the account marked as set by hand) with a plain confirmation, no celebration.
	 */
	public void handleSkateLevelCommand(String[] args)
	{
		SkateLevelCommand cmd = SkateLevelCommand.parse(args);
		String reply;
		switch (cmd.kind)
		{
			case SHOW:
				reply = SkateLevelCommand.describe(progression.level(), progression.xp());
				break;
			case SET:
				if (progression.profile() == null)
				{
					reply = "Log in first: Skating XP is saved per account.";
					break;
				}
				BoardLook before = progression.look();
				progression.setLevelForDev(cmd.level);
				lookMaybeChanged(before);
				publish();
				reply = "Set to " + SkateLevelCommand.describe(progression.level(), progression.xp());
				break;
			default:
				reply = SkateLevelCommand.USAGE;
				break;
		}
		skateChat.send(reply);
	}

	public int level()
	{
		return progression.level();
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

	/** True once ::skatelevel set the loaded account's level by hand. */
	public boolean devLevelSet()
	{
		return progression.devLevelSet();
	}

	/** The level banner ("Skating level 50!") if one is showing, else null. */
	public String getBannerText()
	{
		return bannerText != null && getBannerAge() < HudAnim.BANNER_SECONDS ? bannerText : null;
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
	public void publish()
	{
		int xp = progression.xp();
		List<ProgressState.GoalView> views = new ArrayList<>(SessionGoals.GOALS_PER_SESSION);
		for (SessionGoals.Goal g : goals.goals())
		{
			views.add(new ProgressState.GoalView(g.type.text, g.progressText(), g.isDone()));
		}
		ProgressState s = new ProgressState(progression.level(), xp, SkateLevels.xpToNext(xp),
			SkateLevels.progress(xp), progression.look(), views);
		Consumer<ProgressState> l = listener;
		if (l != null && !s.equals(lastState))
		{
			lastState = s;
			l.accept(s);
		}
	}

	/** The plugin's config group in the logged-in account's RuneScape profile. */
	public static final class ConfigProfileStore implements ProfileStore
	{
		private final ConfigManager configManager;

		public ConfigProfileStore(ConfigManager configManager)
		{
			this.configManager = configManager;
		}

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
			{
				configManager.setRSProfileConfiguration(GielinorSkateConfig.GROUP, key, value);
			}
			else
			{
				// the account changed before the save: into the profile the value was loaded from
				configManager.setConfiguration(GielinorSkateConfig.GROUP, profile, key, value);
			}
		}
	}
}
