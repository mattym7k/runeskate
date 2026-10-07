package com.gielinorskate;

import com.gielinorskate.controller.LayoutCode;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.controller.PadPresets;
import com.gielinorskate.design.CustomDesignService;
import com.gielinorskate.duel.DuelService;
import com.gielinorskate.duel.SkateDuelChallenge;
import com.gielinorskate.duel.SkateDuelEnd;
import com.gielinorskate.duel.SkateDuelHit;
import com.gielinorskate.duel.SkateDuelReply;
import com.gielinorskate.input.InputController;
import com.gielinorskate.leaderboard.LeaderboardService;
import com.gielinorskate.leaderboard.RunService;
import com.gielinorskate.overlay.BlockerDebugOverlay;
import com.gielinorskate.overlay.ComfortHintsOverlay;
import com.gielinorskate.overlay.ControlsCardOverlay;
import com.gielinorskate.overlay.DuelOverlay;
import com.gielinorskate.overlay.GestureDebugOverlay;
import com.gielinorskate.overlay.GhostLabelOverlay;
import com.gielinorskate.overlay.GrindDebugOverlay;
import com.gielinorskate.overlay.LeaderboardOverlay;
import com.gielinorskate.overlay.RunOverlay;
import com.gielinorskate.overlay.ScoreOverlay;
import com.gielinorskate.party.PartyGhostService;
import com.gielinorskate.party.SkateDesignChunk;
import com.gielinorskate.party.SkateDesignOffer;
import com.gielinorskate.party.SkateGhostStop;
import com.gielinorskate.party.SkateGhostUpdate;
import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.DesignPart;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.render.BoardModel;
import com.gielinorskate.session.ComfortHints;
import com.gielinorskate.session.SkateSession;
import com.gielinorskate.session.WorldActions;
import com.gielinorskate.ui.DuelSection;
import com.gielinorskate.ui.LeaderboardSection;
import com.gielinorskate.ui.SkatePanel;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Renderable;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "RuneSkate",
	description = "Skateboard around Gielinor: Ctrl+K to start, flick the mouse for tricks",
	tags = {"skate", "skateboard", "fun", "minigame"},
	internalName = "rune-skate",
	legacyDataDirectory = "runeskate"
)
public class GielinorSkatePlugin extends Plugin
{
	static final String ANTIMICROX_RELEASES = "https://github.com/AntiMicroX/antimicrox/releases";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private GielinorSkateConfig config;

	@Inject
	private InputController input;

	@Inject
	private SkateSession session;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ScoreOverlay scoreOverlay;

	@Inject
	private GrindDebugOverlay grindDebugOverlay;

	@Inject
	private BlockerDebugOverlay blockerDebugOverlay;

	@Inject
	private ControlsCardOverlay controlsCardOverlay;

	@Inject
	private GestureDebugOverlay gestureDebugOverlay;

	@Inject
	private GhostLabelOverlay ghostLabelOverlay;

	@Inject
	private ComfortHintsOverlay comfortHintsOverlay;

	@Inject
	private EventBus eventBus;

	@Inject
	private WSClient wsClient;

	@Inject
	private PartyGhostService partyGhosts;

	@Inject
	private ConfigManager configManager;

	@Inject
	private DuelService duel;

	@Inject
	private DuelOverlay duelOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ProgressionService progression;

	@Inject
	private LeaderboardService leaderboard;

	@Inject
	private RunService runs;

	@Inject
	private RunOverlay runOverlay;

	@Inject
	private LeaderboardOverlay leaderboardOverlay;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private CustomDesignService customDesigns;

	/** The sidebar panel and its button; built in startUp. */
	private SkatePanel panel;
	private NavigationButton navButton;

	/** The :: dev commands only work in a developer-mode client (Plugin Hub players use the settings). */
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	private final RenderCallback playerHider = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			return !session.shouldHide(renderable);
		}
	};

	private final HotkeyListener toggleListener = new HotkeyListener(() -> config.toggleKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(session::toggle);
		}
	};

	@Override
	protected void startUp()
	{
		// the first sans-serif font of the JVM is slow to make: not on the client thread when the card first shows
		executor.execute(ControlsCardOverlay::warmFonts);
		renderCallbackManager.register(playerHider);
		keyManager.registerKeyListener(toggleListener);
		keyManager.registerKeyListener(input);
		mouseManager.registerMouseListener(input);
		mouseManager.registerMouseWheelListener(input);
		overlayManager.add(scoreOverlay);
		overlayManager.add(grindDebugOverlay);
		overlayManager.add(blockerDebugOverlay);
		overlayManager.add(gestureDebugOverlay);
		overlayManager.add(controlsCardOverlay);
		overlayManager.add(ghostLabelOverlay);
		overlayManager.add(comfortHintsOverlay);
		overlayManager.add(runOverlay);
		overlayManager.add(leaderboardOverlay);
		overlayManager.add(duelOverlay);
		wsClient.registerMessage(SkateGhostUpdate.class);
		wsClient.registerMessage(SkateGhostStop.class);
		wsClient.registerMessage(SkateDuelChallenge.class);
		wsClient.registerMessage(SkateDuelReply.class);
		wsClient.registerMessage(SkateDuelHit.class);
		wsClient.registerMessage(SkateDuelEnd.class);
		wsClient.registerMessage(SkateDesignOffer.class);
		wsClient.registerMessage(SkateDesignChunk.class);
		partyGhosts.startUp();
		eventBus.register(partyGhosts);
		duel.startUp();
		eventBus.register(duel);

		leaderboard.startUp();
		panel = new SkatePanel(() -> clientThread.invoke(session::toggle),
			(key, value) -> configManager.setConfiguration(GielinorSkateConfig.GROUP, key, value),
			design -> clientThread.invoke(() -> progression.selectDesign(design)));
		LeaderboardSection boards = new LeaderboardSection(
			() -> clientThread.invoke(() -> runs.toggle(session.isActive())),
			(category, period, refresh) -> clientThread.invoke(() -> leaderboard.fetch(category, period, refresh)));
		panel.addSection(boards);
		DuelSection duels = new DuelSection(new DuelSection.Actions()
		{
			@Override
			public void challenge(long memberId)
			{
				clientThread.invoke(() -> duel.challenge(memberId));
			}

			@Override
			public void accept()
			{
				clientThread.invoke(duel::accept);
			}

			@Override
			public void decline()
			{
				clientThread.invoke(duel::decline);
			}

			@Override
			public void withdraw()
			{
				clientThread.invoke(duel::withdraw);
			}
		});
		panel.addSection(duels);
		panel.setCustomActions(new SkatePanel.CustomActions()
		{
			@Override
			public void add(DesignPart part, java.awt.Component from)
			{
				customDesigns.add(part, from);
			}

			@Override
			public void edit(BoardDesign design, java.awt.Component from)
			{
				customDesigns.edit(design, from);
			}

			@Override
			public void rename(BoardDesign design, java.awt.Component from)
			{
				customDesigns.rename(design, from);
			}

			@Override
			public void delete(BoardDesign design, java.awt.Component from)
			{
				customDesigns.delete(design, from);
			}
		});
		// players' own designs load in the background (file IO), then show in the panel and on the board
		customDesigns.startUp(this::getPluginDirectory, panel::setCustomThumbs);
		panel.setOnActivate(() -> boards.request(false));
		panel.setControllerActions(new SkatePanel.ControllerActions()
		{
			@Override
			public void saveCustomLayout(String code)
			{
				configManager.setConfiguration(GielinorSkateConfig.GROUP, "customControllerLayout", code);
				configManager.setConfiguration(GielinorSkateConfig.GROUP, "controllerPreset",
					GielinorSkateConfig.ControllerPreset.CUSTOM);
			}

			@Override
			public void openReleases()
			{
				LinkBrowser.browse(ANTIMICROX_RELEASES);
			}

			@Override
			public void saveProfile(java.awt.Component from)
			{
				saveControllerProfile(from);
			}
		});
		refreshPanelSettings();
		navButton = NavigationButton.builder()
			.tooltip("RuneSkate")
			.icon(ImageUtil.loadImageResource(GielinorSkatePlugin.class, "panel_icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		SkatePanel shown = panel;
		clientThread.invoke(() ->
		{
			// panel state is made on the client thread and handed to Swing
			session.setPanelListener(state -> SwingUtilities.invokeLater(() ->
			{
				shown.update(state);
				boards.setSkating(state.active);
			}));
			runs.setListener(run -> SwingUtilities.invokeLater(() -> boards.setRun(run.running, run.best)));
			duel.setViewListener(view -> SwingUtilities.invokeLater(() -> duels.display(view)));
			leaderboard.setViewListener(boards::display);
			leaderboard.onConfigChanged();
			session.publishPanel();
			progression.setListener(state -> SwingUtilities.invokeLater(() -> shown.updateProgress(state)));
			progression.setLookListener(session::applyLook);
			// the logged-in account's XP (none until a profile is known: RuneScapeProfileChanged loads it then)
			progression.load();
		});
	}

	@Override
	protected void shutDown()
	{
		// nothing more goes to the leaderboard, not even from an answer still on its way
		leaderboard.shutDown();
		customDesigns.shutDown();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		SkatePanel closing = panel;
		if (closing != null)
		{
			// the pad test's key watcher goes with the panel
			SwingUtilities.invokeLater(closing::dispose);
		}
		panel = null;
		eventBus.unregister(duel);
		eventBus.unregister(partyGhosts);
		// a duel is forfeit (queued), then the party hears it and that we stopped, while the types are registered
		duel.shutDown();
		partyGhosts.shutDown();
		wsClient.unregisterMessage(SkateDesignChunk.class);
		wsClient.unregisterMessage(SkateDesignOffer.class);
		wsClient.unregisterMessage(SkateDuelEnd.class);
		wsClient.unregisterMessage(SkateDuelHit.class);
		wsClient.unregisterMessage(SkateDuelReply.class);
		wsClient.unregisterMessage(SkateDuelChallenge.class);
		wsClient.unregisterMessage(SkateGhostStop.class);
		wsClient.unregisterMessage(SkateGhostUpdate.class);
		overlayManager.remove(duelOverlay);
		overlayManager.remove(leaderboardOverlay);
		overlayManager.remove(runOverlay);
		overlayManager.remove(comfortHintsOverlay);
		overlayManager.remove(ghostLabelOverlay);
		overlayManager.remove(controlsCardOverlay);
		overlayManager.remove(gestureDebugOverlay);
		overlayManager.remove(blockerDebugOverlay);
		overlayManager.remove(grindDebugOverlay);
		overlayManager.remove(scoreOverlay);
		clientThread.invoke(() ->
		{
			session.exit(null);
			session.setPanelListener(null);
			progression.setListener(null);
			progression.setLookListener(null);
			progression.flush();
			runs.setListener(null);
			leaderboard.setViewListener(null);
			// stopped above: this drops what is still queued
			leaderboard.onConfigChanged();
		});
		mouseManager.unregisterMouseWheelListener(input);
		mouseManager.unregisterMouseListener(input);
		keyManager.unregisterKeyListener(input);
		keyManager.unregisterKeyListener(toggleListener);
		renderCallbackManager.unregister(playerHider);
	}

	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		session.onFrame();
		duel.tick(session.isActive());
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		session.checkPlayerMoved();
		progression.tick();
		leaderboard.tick();
	}

	/** Skate XP and decks are per account: save the old account's, load the new one's. */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		clientThread.invoke(() ->
		{
			progression.load();
			runs.onProfileChanged();
			duel.onProfileChanged();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN && !config.seenIntro())
		{
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "seenIntro", true);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ComfortHints.welcome(config.toggleKey().toString()), null);
		}
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			tellAboutNewControllerProfile();
		}
		if (e.getGameState() == GameState.LOADING)
		{
			// the scene is rebuilt (the skate world with it): say why skating stopped
			session.exit("Skate mode ended while the area loaded. Press " + config.toggleKey() + " to skate again.");
		}
		else if (e.getGameState() != GameState.LOGGED_IN)
		{
			session.exit(null);
		}
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			// another account's boards (and the on-screen one) are fetched again
			leaderboard.onLoggedIn();
			// merge the board mesh now, not on the first skate entry or party ghost
			BoardModel.warm(client);
		}
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			// the latest XP goes up once; anything still queued is dropped
			leaderboard.onLogout();
			// a duel is forfeit while the party can still hear it
			duel.onLogout();
		}
		if (e.getGameState() == GameState.HOPPING)
		{
			// duels are fought on one world
			duel.onHop();
		}
		if (e.getGameState() == GameState.HOPPING || e.getGameState() == GameState.LOGIN_SCREEN)
		{
			partyGhosts.onSceneLost();
			progression.flush();
		}
	}

	/**
	 * RuneLite does not stop plugins on exit, so {@link #shutDown} is not guaranteed to run before the JVM
	 * closes. {@link ConfigManager#onClientShutdown} only sends what is already set, so any XP still debounced
	 * (up to {@code Progression.MAX_UNSAVED_SECONDS}) would otherwise be lost. Flush it on the client thread and
	 * make the shutdown wait for that to finish; ConfigManager's own handler (priority -100) runs after ours.
	 */
	@Subscribe
	public void onClientShutdown(ClientShutdown e)
	{
		FutureTask<Void> flushed = new FutureTask<>(progression::flush, null);
		clientThread.invoke(flushed);
		e.waitFor(flushed);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		// per-account progress (saved in the RuneScape profile) is not a setting
		if (GielinorSkateConfig.GROUP.equals(e.getGroup()) && !ProgressionService.isProgressKey(e.getKey()))
		{
			String key = e.getKey();
			clientThread.invoke(() -> session.onConfigChanged(key));
			if ("controllerMode".equals(key))
			{
				clientThread.invoke(() ->
				{
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						tellAboutNewControllerProfile();
					}
				});
			}
			if ("submitScores".equals(key) || "leaderboardUrl".equals(key))
			{
				clientThread.invoke(leaderboard::onConfigChanged);
			}
			else if (key.startsWith("leaderboardOverlay") || "showLeaderboardOverlay".equals(key))
			{
				clientThread.invoke(leaderboard::onHudConfigChanged);
			}
			refreshPanelSettings();
		}
	}

	/**
	 * While skating the camera is detached from the player: any menu action on the game world is cancelled
	 * (Plugin Hub rule: no world interaction from a detached camera). Interface actions still go through.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (session.isActive() && WorldActions.isWorldAction(e.getMenuAction()))
		{
			e.consume();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (session.isActive() && e.getActor() == client.getLocalPlayer())
		{
			// poison and venom get their own message: their damage still ends skating
			session.exit(ComfortHints.damageMessage(e.getHitsplat().getHitsplatType()));
		}
	}

	/**
	 * Dev aids: ::skatepose tries different animation IDs live before they're hard-coded;
	 * ::skategrinds toggles drawing the grindable edges; ::skateboxes the collision boxes;
	 * ::skategesture the last right-mouse stroke and the trick it recognized; ::skatelevel [1-99] shows or sets the
	 * Skating level (to try the board designs); ::skaterun starts the timed 2-minute run; ::skateboard runeskate|classic
	 * switches the local skater between the baked image-texture board and the classic one; ::skateghosts prints, per party
	 * ghost, what its body is drawn as and why any fallback. Developer-mode clients only: players
	 * use the settings and the side panel ("Show grindable edges").
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		if (!developerMode)
		{
			return;
		}
		if ("skatepose".equalsIgnoreCase(e.getCommand()))
		{
			clientThread.invoke(() -> session.handleSkatePoseCommand(e.getArguments()));
		}
		else if ("skategrinds".equalsIgnoreCase(e.getCommand()))
		{
			clientThread.invoke(session::handleSkateGrindsCommand);
		}
		else if ("skateboxes".equalsIgnoreCase(e.getCommand()))
		{
			clientThread.invoke(session::handleSkateBoxesCommand);
		}
		else if ("skategesture".equalsIgnoreCase(e.getCommand()))
		{
			clientThread.invoke(session::handleSkateGestureCommand);
		}
		else if ("skatelevel".equalsIgnoreCase(e.getCommand()))
		{
			// testing designs: sets this account's Skating level (and marks it as set by hand)
			clientThread.invoke(() -> progression.handleSkateLevelCommand(e.getArguments()));
		}
		else if ("skateboard".equalsIgnoreCase(e.getCommand()))
		{
			// the baked image-texture (RuneSkate) board or the classic board, for the local skater
			clientThread.invoke(() -> session.handleSkateboardCommand(e.getArguments()));
		}
		else if ("skateghosts".equalsIgnoreCase(e.getCommand()))
		{
			// per party ghost: what its body is drawn as, and why any fallback
			clientThread.invoke(this::showGhostStatus);
		}
		else if ("skaterun".equalsIgnoreCase(e.getCommand()))
		{
			// the timed 2-minute run, as the panel's button starts it
			clientThread.invoke(() -> runs.start(session.isActive()));
		}
	}

	/** ::skateghosts: one chat line per party ghost (debug aid). Client thread. */
	private void showGhostStatus()
	{
		List<String> lines = partyGhosts.ghostStatusLines();
		if (!session.isActive())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Skate ghosts: you are not skating (ghosts are drawn only while you skate).", null);
		}
		if (lines.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Skate ghosts: none (no party member is skating, or none shares with the party).", null);
		}
		for (String line : lines)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Skate ghost " + line, null);
		}
	}

	/**
	 * Once per profile change, a Controller mode player is told to load the new AntiMicroX profile (its buttons send
	 * different keys now). Client thread.
	 */
	private void tellAboutNewControllerProfile()
	{
		if (!config.controllerMode())
		{
			return;
		}
		if (!config.controllerSetupHint())
		{
			// the first time: how to set the controller up (which covers loading the current profile)
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "controllerSetupHint", true);
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "controllerProfileNotice",
				ComfortHints.CONTROLLER_PROFILE_VERSION);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ComfortHints.CONTROLLER_SETUP, null);
			return;
		}
		if (config.controllerProfileNotice() < ComfortHints.CONTROLLER_PROFILE_VERSION)
		{
			configManager.setConfiguration(GielinorSkateConfig.GROUP, "controllerProfileNotice",
				ComfortHints.CONTROLLER_PROFILE_VERSION);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", ComfortHints.NEW_CONTROLLER_PROFILE, null);
		}
	}

	/** Save the RuneSkate AntiMicroX profile: where to (a save dialog, EDT), then the file written (executor). */
	private void saveControllerProfile(java.awt.Component from)
	{
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save the RuneSkate controller profile")
			.addExtensionFilter("AntiMicroX profile", "amgp")
			.setDefaultExtension("amgp")
			.setFileName("RuneSkate.amgp");
		List<Filepath> picked = chooser.showDialog(from);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath file = picked.get(0);
		executor.execute(() ->
		{
			try (InputStream in = GielinorSkatePlugin.class.getResourceAsStream("RuneSkate.amgp"))
			{
				if (in == null)
				{
					throw new IOException("the profile is missing from the plugin");
				}
				byte[] profile = in.readAllBytes();
				try (OutputStream out = file.openOutputStream())
				{
					out.write(profile);
				}
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(from, "Saved. In AntiMicroX, click Load "
					+ "and pick " + file.getFileName() + ".", "RuneSkate", JOptionPane.INFORMATION_MESSAGE));
			}
			catch (IOException | RuntimeException ex)
			{
				log.warn("RuneSkate: controller profile could not be saved", ex);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(from, "The profile couldn't be saved: "
					+ ex.getMessage(), "RuneSkate", JOptionPane.WARNING_MESSAGE));
			}
		});
	}

	/** Hands the settings the panel shows (its toggles, keys and trick controls) to Swing. */
	private void refreshPanelSettings()
	{
		SkatePanel p = panel;
		if (p == null)
		{
			return;
		}
		String toggleKey = config.toggleKey().toString();
		GielinorSkateConfig.TrickControls controls = config.trickControls();
		String manualKey = ComfortHints.manualKeyLabel(config.manualKey().toString(),
			config.manualKey().getKeyCode(), controls.keyboard());
		boolean mirror = config.mirrorFlicks();
		boolean grinds = config.showGrindEdges();
		boolean card = config.showControlsCard();
		boolean goals = config.showSessionGoals();
		boolean controller = config.controllerMode();
		String boardKey = ComfortHints.boardKeyLabel(config.boardKey().toString(), config.boardKey().getKeyCode(),
			controls.keyboard());
		String flickButton = config.flickButton().toString().toLowerCase();
		String brakeKey = config.brakeKey().toString();
		String leanForwardKey = config.leanForwardKey().toString();
		String leanBackKey = config.leanBackKey().toString();
		GielinorSkateConfig.ControllerPreset which = config.controllerPreset();
		String customCode = config.customControllerLayout();
		PadPreset preset = PadPresets.resolve(which, customCode);
		String presetName = PadPresets.customIsBroken(which, customCode) ? "Skate 3 (your Custom layout can't be read)"
			: which.toString();
		LayoutCode.Result saved = LayoutCode.decode(customCode);
		PadPreset savedCustom = saved.ok() ? saved.preset : null;
		SwingUtilities.invokeLater(() ->
		{
			p.setController(preset, presetName, savedCustom);
			p.setKeyNames(flickButton, brakeKey, leanForwardKey, leanBackKey);
			p.setSettings(toggleKey, manualKey, controls.mouse(), controls.keyboard(), mirror, grinds, card, controller,
				boardKey);
			p.setShowGoals(goals);
		});
	}

	@Provides
	GielinorSkateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GielinorSkateConfig.class);
	}
}
