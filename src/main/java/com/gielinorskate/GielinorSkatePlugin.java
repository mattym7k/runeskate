package com.gielinorskate;

import static com.gielinorskate.GielinorSkateConfig.GROUP;

import com.gielinorskate.controller.LayoutCode;
import com.gielinorskate.controller.PadPresets;
import com.gielinorskate.input.InputController;
import com.gielinorskate.overlay.*;
import com.gielinorskate.party.*;
import com.gielinorskate.progression.ProgressionService;
import com.gielinorskate.session.*;
import com.gielinorskate.ui.*;
import com.google.inject.Provides;
import java.awt.Component;
import java.io.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.input.*;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.*;

@Slf4j
@PluginDescriptor(
	name = "RuneSkate",
	description = "Skateboard around Gielinor: Ctrl+K to start, flick the mouse for tricks",
	tags = {"skate", "skateboard", "fun", "minigame"},
	internalName = "rune-skate"
)
public class GielinorSkatePlugin extends Plugin
{
	/** The overlays, all singletons (the plugin injector gives the same one to add and remove). */
	private static final List<Class<? extends Overlay>> OVERLAYS = List.of(ScoreOverlay.class,
		GrindEdgesOverlay.class, ControlsCardOverlay.class, GhostLabelOverlay.class, ComfortHintsOverlay.class);
	/** The party messages, registered while the plugin runs. */
	private static final List<Class<? extends PartyMemberMessage>> MESSAGES = List.of(SkateGhostUpdate.class,
		SkateGhostStop.class);

	@Inject
	private Client client;
	@Inject
	private SkateChat skateChat;
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
	private EventBus eventBus;
	@Inject
	private WSClient wsClient;
	@Inject
	private PartyGhostService partyGhosts;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ProgressionService progression;
	@Inject
	private ScheduledExecutorService executor;

	/** The sidebar panel and its button; built in startUp. */
	private SkatePanel panel;
	private NavigationButton navButton;

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
		OVERLAYS.forEach(c -> overlayManager.add(injector.getInstance(c)));
		MESSAGES.forEach(wsClient::registerMessage);
		partyGhosts.startUp();
		eventBus.register(partyGhosts);

		panel = new SkatePanel(() -> clientThread.invoke(session::toggle), this::set,
			design -> clientThread.invoke(() -> progression.selectDesign(design)));
		panel.setControllerActions(new SkatePanel.ControllerActions()
		{
			@Override
			public void saveCustomLayout(String code)
			{
				set("customControllerLayout", code);
				set("controllerPreset", GielinorSkateConfig.ControllerPreset.CUSTOM);
			}

			@Override
			public void openReleases()
			{
				LinkBrowser.browse("https://github.com/AntiMicroX/antimicrox/releases");
			}

			@Override
			public void saveProfile(Component from)
			{
				saveControllerProfile(from);
			}

			@Override
			public void watchKeys(KeyListener l, boolean on)
			{
				if (on)
					keyManager.registerKeyListener(l);
				else
					keyManager.unregisterKeyListener(l);
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
			session.setPanelListener(state -> SwingUtilities.invokeLater(() -> shown.update(state)));
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
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		SkatePanel closing = panel;
		if (closing != null)
			// the pad test's key watcher goes with the panel
			SwingUtilities.invokeLater(closing::dispose);
		panel = null;
		eventBus.unregister(partyGhosts);
		// the party hears that we stopped while the types are registered
		partyGhosts.shutDown();
		MESSAGES.forEach(wsClient::unregisterMessage);
		OVERLAYS.forEach(c -> overlayManager.remove(injector.getInstance(c)));
		clientThread.invoke(() ->
		{
			session.exit(null);
			session.setPanelListener(null);
			progression.setListener(null);
			progression.setLookListener(null);
			progression.flush();
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
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		session.checkPlayerMoved();
		progression.tick();
	}

	/** Skate XP and decks are per account: save the old account's, load the new one's. */
	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		clientThread.invoke(progression::load);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			if (!config.seenIntro())
			{
				set("seenIntro", true);
				skateChat.send(ComfortHints.welcome(config.toggleKey().toString()));
			}
			tellControllerSetup();
			return;
		}
		// on LOADING the scene is rebuilt (the skate world with it): say why skating stopped
		session.exit(state == GameState.LOADING ? Text.get("pl.loading", config.toggleKey()) : null);
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
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
		String key = e.getKey();
		// per-account progress (saved in the RuneScape profile) is not a setting
		if (!GROUP.equals(e.getGroup()) || ProgressionService.isProgressKey(key))
			return;
		clientThread.invoke(() ->
		{
			session.onConfigChanged(key);
			if ("controllerMode".equals(key) && client.getGameState() == GameState.LOGGED_IN)
				tellControllerSetup();
		});
		refreshPanelSettings();
	}

	/**
	 * While skating the camera is detached from the player: any menu action on the game world is cancelled
	 * (Plugin Hub rule: no world interaction from a detached camera). Interface actions still go through.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (session.isActive() && WorldActions.isWorldAction(e.getMenuAction()))
			e.consume();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (session.isActive() && e.getActor() == client.getLocalPlayer())
			// poison and venom get their own message: their damage still ends skating
			session.exit(ComfortHints.damageMessage(e.getHitsplat().getHitsplatType()));
	}

	private void set(String key, Object value)
	{
		configManager.setConfiguration(GROUP, key, value);
	}

	/** The first time Controller mode is on while logged in, the player is told how to set the controller up. */
	private void tellControllerSetup()
	{
		if (config.controllerMode() && !config.controllerSetupHint())
		{
			set("controllerSetupHint", true);
			skateChat.send(Text.get("ch.pad.setup"));
		}
	}

	/** Save the RuneSkate AntiMicroX profile: where to (a save dialog, EDT), then the file written (executor). */
	private void saveControllerProfile(Component from)
	{
		List<Filepath> picked = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save the RuneSkate controller profile")
			.addExtensionFilter("AntiMicroX profile", "amgp")
			.setDefaultExtension("amgp")
			.setFileName("RuneSkate.amgp")
			.showDialog(from);
		if (picked == null || picked.isEmpty())
			return;
		Filepath file = picked.get(0);
		executor.execute(() ->
		{
			try (InputStream in = GielinorSkatePlugin.class.getResourceAsStream("RuneSkate.amgp");
				OutputStream out = file.openOutputStream())
			{
				in.transferTo(out);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(from, Text.get("pl.saved", file.getFileName()),
					"RuneSkate", JOptionPane.INFORMATION_MESSAGE));
			}
			catch (IOException | RuntimeException ex)
			{
				log.warn(Text.get("pl.log.profile"), ex);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(from,
					Text.get("pl.unsaved", ex.getMessage()), "RuneSkate", JOptionPane.WARNING_MESSAGE));
			}
		});
	}

	/** Hands the settings the panel shows (its toggles, keys, trick and controller controls) to Swing. */
	private void refreshPanelSettings()
	{
		SkatePanel p = panel;
		if (p == null)
			return;
		SwingUtilities.invokeLater(() ->
		{
			GielinorSkateConfig.TrickControls controls = config.trickControls();
			GielinorSkateConfig.ControllerPreset which = config.controllerPreset();
			String customCode = config.customControllerLayout();
			LayoutCode.Result saved = LayoutCode.decode(customCode);
			p.setController(PadPresets.resolve(which, customCode), PadPresets.customIsBroken(which, customCode)
				? Text.get("pl.broken") : which.toString(), saved.ok() ? saved.preset : null);
			p.setKeyNames(config.flickButton().toString().toLowerCase(), config.brakeKey().toString(),
				config.leanForwardKey().toString(), config.leanBackKey().toString());
			p.setSettings(config.toggleKey().toString(), ComfortHints.manualKeyLabel(config.manualKey().toString(),
				config.manualKey().getKeyCode(), controls.keyboard()), controls.mouse(), controls.keyboard(),
				config.mirrorFlicks(), config.showGrindEdges(), config.showControlsCard(), config.controllerMode(),
				ComfortHints.boardKeyLabel(config.boardKey().toString(), config.boardKey().getKeyCode(),
				controls.keyboard()));
			p.setShowGoals(config.showSessionGoals());
		});
	}

	@Provides
	GielinorSkateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GielinorSkateConfig.class);
	}
}
