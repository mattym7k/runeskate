package com.gielinorskate.overlay;

import com.gielinorskate.GielinorSkateConfig;
import com.gielinorskate.GielinorSkatePlugin;
import com.gielinorskate.leaderboard.LeaderboardService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The on-screen leaderboard (top left, movable): shown whenever leaderboards and "Show leaderboard on screen" are
 * on, skating or not. It draws the service's snapshot ({@link LeaderboardService#hud}) and never fetches itself.
 * Its right-click menu collapses it, cycles the board and switches the period; those choices are saved settings,
 * and the service fetches the new board when told of the change. RuneLite overlay menu only: nothing reaches the
 * game world.
 */
@Singleton
public class LeaderboardOverlay extends OverlayPanel
{
	private static final Color TITLE_COLOR = new Color(255, 200, 40);
	private static final Color SUBTITLE_COLOR = new Color(190, 190, 190);
	private static final Color ROW_COLOR = Color.WHITE;
	private static final Color YOU_COLOR = new Color(255, 152, 31);
	private static final Color STATUS_COLOR = new Color(170, 170, 170);
	private static final int WIDTH = 170;
	/** The panel's border inside its edge, on each side. */
	private static final int PADDING = 6;
	private static final String TARGET = LeaderboardHud.TITLE;

	private final GielinorSkateConfig config;
	private final ConfigManager configManager;
	private final LeaderboardService service;
	/** The menu options added last, so they are swapped only when the labels change. */
	private List<String> menuOptions = new ArrayList<>();

	@Inject
	LeaderboardOverlay(GielinorSkatePlugin plugin, GielinorSkateConfig config, ConfigManager configManager,
		LeaderboardService service)
	{
		super(plugin);
		this.config = config;
		this.configManager = configManager;
		this.service = service;
		setPosition(OverlayPosition.TOP_LEFT);
		syncMenu(false, "week");
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.submitScores() || !config.showLeaderboardOverlay())
		{
			return null;
		}
		boolean collapsed = config.leaderboardOverlayCollapsed();
		LeaderboardHud.Model m = LeaderboardHud.build(service.hud(), collapsed);
		if (m == null)
		{
			return null;
		}
		syncMenu(collapsed, config.leaderboardOverlayPeriod());
		// wide enough for the title in the overlay font (the collapsed one is longer)
		int titleWidth = g.getFontMetrics().stringWidth(m.title) + 2 * PADDING;
		panelComponent.setPreferredSize(new Dimension(Math.max(WIDTH, titleWidth), 0));
		panelComponent.getChildren().add(TitleComponent.builder().text(m.title).color(TITLE_COLOR).build());
		if (m.subtitle != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(m.subtitle).leftColor(SUBTITLE_COLOR)
				.build());
		}
		for (LeaderboardHud.Line line : m.lines)
		{
			Color color = line.kind == LeaderboardHud.Kind.YOU ? YOU_COLOR
				: line.kind == LeaderboardHud.Kind.STATUS ? STATUS_COLOR : ROW_COLOR;
			LineComponent.LineComponentBuilder b = LineComponent.builder().left(line.left).leftColor(color);
			if (line.right != null)
			{
				b.right(line.right).rightColor(color);
			}
			panelComponent.getChildren().add(b.build());
		}
		return super.render(g);
	}

	/** Puts the right-click options for this state on the overlay (only when they changed). */
	private void syncMenu(boolean collapsed, String period)
	{
		List<String> options = LeaderboardHud.menuOptions(collapsed, period);
		if (options.equals(menuOptions))
		{
			return;
		}
		for (String option : menuOptions)
		{
			removeMenuEntry(MenuAction.RUNELITE_OVERLAY, option, TARGET);
		}
		menuOptions = options;
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, options.get(0), TARGET, e -> set("leaderboardOverlayCollapsed",
			!config.leaderboardOverlayCollapsed()));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, options.get(1), TARGET, e -> set("leaderboardOverlayBoard",
			LeaderboardHud.nextCategory(config.leaderboardOverlayBoard())));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, options.get(2), TARGET, e -> set("leaderboardOverlayPeriod",
			LeaderboardHud.otherPeriod(config.leaderboardOverlayPeriod())));
	}

	private void set(String key, Object value)
	{
		configManager.setConfiguration(GielinorSkateConfig.GROUP, key, value);
	}
}
