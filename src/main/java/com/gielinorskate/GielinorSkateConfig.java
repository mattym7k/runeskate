package com.gielinorskate;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.*;

/**
* Settings. Display names may change freely, but a keyName never changes without a migration (renaming one
* silently resets the player's saved value).
*/
@ConfigGroup(GielinorSkateConfig.GROUP)
public interface GielinorSkateConfig extends Config
{
String GROUP = "gielinorskate";

@ConfigSection(name = "RuneSkate", description = "The skate key, controller and leaderboards", position = 0)
String GENERAL = "general";

@ConfigSection(name = "Play together",
description = "Join a RuneLite Party with your friends (Party plugin → Create/Join party) on the same world "
+ "to see each other skate, share custom boards and Skate Duel.",
position = 1)
String PARTY = "party";

@ConfigSection(name = "Gameplay", description = "Tricks and bails", position = 2)
String GAMEPLAY = "gameplay";

@ConfigSection(name = "Camera & effects", description = "The chase camera, sounds and particles", position = 3)
String CAMERA = "camera";

@ConfigSection(name = "Advanced", description = "Key rebinds, fine tuning and everything else",
position = 4, closedByDefault = true)
String ADVANCED = "advanced";

/** RuneLite's required wording for a feature that talks to a third-party server, verbatim. */
String THIRD_PARTY_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or "
+ "verified by RuneLite developers";

/** The leaderboard setting's description: what it does, the required warning, and what else it sends. */
String SUBMIT_SCORES_DESCRIPTION = "Sends your best combos, timed 2-minute runs and Skating XP to the RuneSkate "
+ "leaderboard, and shows the leaderboards in the side panel and on screen. " + THIRD_PARTY_WARNING
+ ". Your RuneScape name is sent with your scores.";

/** Which controller layout turns the pad's buttons into actions (see com.gielinorskate.controller). */
@RequiredArgsConstructor
enum ControllerPreset
{
SKATE_3("Skate 3"),
THAW("Tony Hawk's American Wasteland"),
CUSTOM("Custom");

private final String label;

@Override
public String toString()
{
return label;
}
}

/** How tricks are triggered: mouse flicks, keys, or both. */
@RequiredArgsConstructor
enum TrickControls
{
MOUSE("Mouse flicks"),
KEYBOARD("Keyboard"),
BOTH("Both");

private final String label;

/** True when Space and the number keys do tricks. */
public boolean keyboard()
{
return this != MOUSE;
}

/** True when mouse flicks do tricks. */
public boolean mouse()
{
return this != KEYBOARD;
}

@Override
public String toString()
{
return label;
}
}

/** The mouse button held for a flick. */
@RequiredArgsConstructor
enum FlickButton
{
RIGHT("Right"),
LEFT("Left"),
MIDDLE("Middle");

private final String label;

@Override
public String toString()
{
return label;
}
}

/** What a bail does after the fall: knocked off the board (walk back to it), or straight back on. */
@RequiredArgsConstructor
enum AfterBail
{
KNOCKED_OFF("Get knocked off (walk back to your board)"),
HOP_BACK_ON("Hop straight back on");

private final String label;

@Override
public String toString()
{
return label;
}
}

/** How detailed the baked board is drawn for you (party skaters' boards always use the lighter variant). */
@RequiredArgsConstructor
enum BoardDetail
{
HIGH("High"),
NORMAL("Normal");

private final String label;

@Override
public String toString()
{
return label;
}
}

@RequiredArgsConstructor
enum CameraHeight
{
LOW("Low", 6),
MEDIUM("Medium", 14),
HIGH("High", 22);

private final String label;
private final int degrees;

/** Camera pitch in JAU14 (16384 per turn). */
public int pitchJau14()
{
return Math.round(degrees * 16384f / 360f);
}

@Override
public String toString()
{
return label;
}
}

// ---- RuneSkate

@ConfigItem(
keyName = "toggleKey",
name = "Skate mode key",
description = "Starts or stops skating (the skateboard button in the sidebar does too).",
position = 0,
section = GENERAL
)
default Keybind toggleKey()
{
return new Keybind(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
}

@ConfigItem(
keyName = "controllerMode",
name = "Controller mode",
description = "For a gamepad through AntiMicroX (side panel: Controller setup): the pad's buttons do the "
+ "Controller preset's actions, the right stick flicks with no button, and the card and panel show pad "
+ "buttons.",
position = 1,
section = GENERAL
)
default boolean controllerMode()
{
return false;
}

@ConfigItem(
keyName = "controllerPreset",
name = "Controller preset",
description = "What the pad's buttons do in Controller mode: Skate 3 (flick tricks on the right stick), Tony "
+ "Hawk's American Wasteland (tricks on buttons, the right stick turns the camera), or your own layout "
+ "(side panel: Customise controller).",
position = 2,
section = GENERAL
)
default ControllerPreset controllerPreset()
{
return ControllerPreset.SKATE_3;
}

@ConfigItem(
keyName = "submitScores",
name = "Submit scores to the leaderboard",
description = SUBMIT_SCORES_DESCRIPTION,
warning = THIRD_PARTY_WARNING,
position = 3,
section = GENERAL
)
default boolean submitScores()
{
return false;
}

@ConfigItem(
keyName = "showLeaderboardOverlay",
name = "Show leaderboard on screen",
description = "While leaderboards are on, a small movable box shows the top 5 and your rank (right-click it "
+ "for options); scores are submitted either way.",
position = 4,
section = GENERAL
)
default boolean showLeaderboardOverlay()
{
return true;
}

// ---- Play together

@ConfigItem(
keyName = "shareWithParty",
name = "Share my skater with my party",
description = "Party members who are also skating see your skater (never in PvP areas).",
position = 0,
section = PARTY
)
default boolean shareWithParty()
{
return true;
}

@ConfigItem(
keyName = "showPartySkaters",
name = "Show party skaters",
description = "While you skate, shows the skaters of party members who share theirs.",
position = 1,
section = PARTY
)
default boolean showPartySkaters()
{
return true;
}

@ConfigItem(
keyName = "shareCustomDesigns",
name = "Share my custom designs",
description = "Party members who see your skater also see the board designs you made, if they turned on "
+ "\"Show party members' custom designs\" (needs \"Share my skater with my party\").",
position = 2,
section = PARTY
)
default boolean shareCustomDesigns()
{
return true;
}

@ConfigItem(
keyName = "showPartyCustomDesigns",
name = "Show party members' custom designs",
description = "Off by default. When on, shows the images your party members drew or picked for their own "
+ "board designs (kept in memory only); when off, their custom parts use the default design.",
position = 3,
section = PARTY
)
default boolean showPartyCustomDesigns()
{
return false;
}

@ConfigItem(
keyName = "allowDuelChallenges",
name = "Allow duel challenges",
description = "Lets skating party members challenge you to a Skate Duel, and you them, from the side panel: "
+ "own Skate HP, nothing at stake, and turning it off forfeits a duel in progress.",
position = 4,
section = PARTY
)
default boolean allowDuelChallenges()
{
return true;
}

@ConfigItem(
keyName = "duelEndings",
name = "Duel endings (tantrum & celebration)",
description = "When a Skate Duel ends in a knockout, the loser throws a tantrum and snaps their board and "
+ "the winner celebrates (any key skips the celebration; Esc always stops skating). Party members see "
+ "yours too. Just for show: nothing in the game changes.",
position = 5,
section = PARTY
)
default boolean duelEndings()
{
return true;
}

// ---- Gameplay

@ConfigItem(
keyName = "trickControls",
name = "Trick controls",
description = "Mouse flicks, Keyboard (Space: ollie, 1-0: flip tricks, Shift: hard version, Alt: nollie), "
+ "or Both.",
position = 0,
section = GAMEPLAY
)
default TrickControls trickControls()
{
return TrickControls.MOUSE;
}

@ConfigItem(
keyName = "forgivingCollisions",
name = "Easy mode",
description = "A smaller hitbox around your skater, and only harder hits make you bail.",
position = 1,
section = GAMEPLAY
)
default boolean forgivingCollisions()
{
return false;
}

@ConfigItem(
keyName = "afterBail",
name = "After a bail",
description = "Get knocked off (F gets your board back, R gets straight back on) or hop straight back on; "
+ "a Skate Duel always knocks you off.",
position = 2,
section = GAMEPLAY
)
default AfterBail afterBail()
{
return AfterBail.KNOCKED_OFF;
}

// ---- Camera & effects

@Range(min = 200, max = 1400)
@ConfigItem(
keyName = "cameraZoom",
name = "Camera zoom",
description = "Chase camera zoom, higher is closer; the mouse wheel changes it while skating.",
position = 0,
section = CAMERA
)
default int cameraZoom()
{
return 700;
}

@ConfigItem(
keyName = "cameraHeight",
name = "Camera height",
description = "How far above the skater the chase camera looks down from.",
position = 1,
section = CAMERA
)
default CameraHeight cameraHeight()
{
return CameraHeight.HIGH;
}

@ConfigItem(
keyName = "soundEffects",
name = "Sound effects",
description = "Pops, landings, grinds and bails, following your in-game sound effect volume.",
position = 2,
section = CAMERA
)
default boolean soundEffects()
{
return true;
}

@ConfigItem(
keyName = "particleEffects",
name = "Particle effects",
description = "Dust on big landings, sparks on rails and smoke on bails (only you see them).",
position = 3,
section = CAMERA
)
default boolean particleEffects()
{
return true;
}

// ---- Advanced

@Range(min = 75, max = 150)
@ConfigItem(
keyName = "speedScale",
name = "Speed %",
description = "How hard you push and how fast you can go.",
position = 0,
section = ADVANCED
)
default int speedScale()
{
return 100;
}

@Range(min = 75, max = 150)
@ConfigItem(
keyName = "popScale",
name = "Jump height %",
description = "How high an ollie goes.",
position = 1,
section = ADVANCED
)
default int popScale()
{
return 100;
}

@ConfigItem(
keyName = "passThroughVegetation",
name = "Pass through vegetation",
description = "Ride straight through plants, bushes and crops instead of colliding with them.",
position = 2,
section = ADVANCED
)
default boolean passThroughVegetation()
{
return true;
}

@ConfigItem(
keyName = "allowPvpAreas",
name = "Allow skating in PvP areas",
description = "Skate in the Wilderness and on PvP worlds: other players are hidden while you skate there, "
+ "and skating is still blocked in combat and ends when you take damage.",
warning = "Your real character stands still and unattended while you skate. Other players are hidden "
+ "while you skate there, but things you'd see in normal play (minimap dots, pets, other plugins' "
+ "overlays) may still show. Only enable this if you're carrying nothing you'd mind losing. Continue?",
position = 3,
section = ADVANCED
)
default boolean allowPvpAreas()
{
return false;
}

@ConfigItem(
keyName = "manualKey",
name = "Wheelie (manual) key",
description = "Hold while rolling for a manual, with W for a nose manual (with keyboard tricks on, a Space "
+ "setting uses C instead).",
position = 4,
section = ADVANCED
)
default Keybind manualKey()
{
return new Keybind(KeyEvent.VK_SPACE, 0);
}

@ConfigItem(
keyName = "leanForwardKey",
name = "Lean forward key",
description = "Hold with a grab (Q / E) in the air for a front flip; the Up arrow still pushes too.",
position = 5,
section = ADVANCED
)
default Keybind leanForwardKey()
{
return new Keybind(KeyEvent.VK_UP, 0);
}

@ConfigItem(
keyName = "leanBackKey",
name = "Lean back key",
description = "Hold with a grab (Q / E) in the air for a back flip; the Down arrow still crouches too.",
position = 6,
section = ADVANCED
)
default Keybind leanBackKey()
{
return new Keybind(KeyEvent.VK_DOWN, 0);
}

@ConfigItem(
keyName = "brakeKey",
name = "Brake key",
description = "Hold to powerslide brake like Shift alone; pick a key no other control uses.",
position = 7,
section = ADVANCED
)
default Keybind brakeKey()
{
return new Keybind(KeyEvent.VK_B, 0);
}

@ConfigItem(
keyName = "boardKey",
name = "Board on/off key",
description = "Step off and carry the board, step back on, or call a dropped board back (a key a skate "
+ "control already uses falls back to F).",
position = 8,
section = ADVANCED
)
default Keybind boardKey()
{
return new Keybind(KeyEvent.VK_F, 0);
}

@ConfigItem(
keyName = "flickButton",
name = "Flick button",
description = "The mouse button you hold to wind up and flick tricks.",
position = 11,
section = ADVANCED
)
default FlickButton flickButton()
{
return FlickButton.RIGHT;
}

@ConfigItem(
keyName = "mirrorFlicks",
name = "Mirror flicks",
description = "Swaps left and right flicks: up-left becomes a heelflip, left a FS shove-it, and so on.",
position = 12,
section = ADVANCED
)
default boolean mirrorFlicks()
{
return false;
}

@Range(min = 70, max = 150)
@ConfigItem(
keyName = "flickSensitivity",
name = "Flick sensitivity %",
description = "Higher means smaller or slower mouse flicks still count as tricks.",
position = 13,
section = ADVANCED
)
default int flickSensitivity()
{
return 100;
}

@ConfigItem(
keyName = "showControlsCard",
name = "Show controls card",
description = "Shows the controls card when you start skating until you've pushed and jumped once (H "
+ "toggles it any time).",
position = 14,
section = ADVANCED
)
default boolean showControlsCard()
{
return true;
}

@ConfigItem(
keyName = "showTrickHints",
name = "Show trick hints",
description = "A short tip when a mouse flick almost became a trick.",
position = 15,
section = ADVANCED
)
default boolean showTrickHints()
{
return true;
}

@ConfigItem(
keyName = "showFlickVisualizer",
name = "Show flick visualizer",
description = "While you hold the flick button, the HUD ring shows your mouse path and each flick it "
+ "recognises.",
position = 16,
section = ADVANCED
)
default boolean showFlickVisualizer()
{
return true;
}

@Range(min = 0, max = 100)
@ConfigItem(
keyName = "soundVolume",
name = "Sound volume",
description = "Skate sounds as a percentage of your in-game sound effect volume.",
position = 17,
section = ADVANCED
)
default int soundVolume()
{
return 100;
}

@ConfigItem(
keyName = "funnyBailMessages",
name = "Funny bail messages",
description = "A joke chat line when you bail, at most one every 20 seconds.",
position = 18,
section = ADVANCED
)
default boolean funnyBailMessages()
{
return true;
}

@ConfigItem(
keyName = "levelUpNotification",
name = "Level-up notifications",
description = "A RuneLite notification when your Skating level goes up (the chat message always shows).",
position = 19,
section = ADVANCED
)
default Notification levelUpNotification()
{
return Notification.ON;
}

@ConfigItem(
keyName = "showSessionGoals",
name = "Show daily goals",
description = "Shows today's three goals in the side panel and on the HUD (they still count when hidden).",
position = 20,
section = ADVANCED
)
default boolean showSessionGoals()
{
return true;
}

@ConfigItem(
keyName = "boardDetail",
name = "Board detail",
description = "How finely your RuneSkate board is drawn: Normal is lighter on slower computers.",
position = 22,
section = ADVANCED
)
default BoardDetail boardDetail()
{
return BoardDetail.HIGH;
}

@ConfigItem(
keyName = "smoothMotion",
name = "Smooth motion",
description = "Draws the skater and camera between physics steps, so motion stays smooth at any frame rate.",
position = 23,
section = ADVANCED
)
default boolean smoothMotion()
{
return true;
}

@ConfigItem(
keyName = "showGrindEdges",
name = "Show grindable edges",
description = "While skating, draws the edges and rails you can grind.",
position = 24,
section = ADVANCED
)
default boolean showGrindEdges()
{
return false;
}

/** The on-screen leaderboard is collapsed to its title bar (its right-click menu). */
@ConfigItem(
keyName = "leaderboardOverlayCollapsed",
name = "",
description = "",
hidden = true
)
default boolean leaderboardOverlayCollapsed()
{
return false;
}

/** The on-screen leaderboard's board: combo, session or xp. */
@ConfigItem(
keyName = "leaderboardOverlayBoard",
name = "",
description = "",
hidden = true
)
default String leaderboardOverlayBoard()
{
return "combo";
}

/** The on-screen leaderboard's period: week or all. */
@ConfigItem(
keyName = "leaderboardOverlayPeriod",
name = "",
description = "",
hidden = true
)
default String leaderboardOverlayPeriod()
{
return "week";
}

/** The Custom controller preset, as a layout code (com.gielinorskate.controller.LayoutCode); empty: none. */
@ConfigItem(
keyName = "customControllerLayout",
name = "",
description = "",
hidden = true
)
default String customControllerLayout()
{
return "";
}

/** Set once a Controller mode player has been shown how to set the controller up. */
@ConfigItem(
keyName = "controllerSetupHint",
name = "",
description = "",
hidden = true
)
default boolean controllerSetupHint()
{
return false;
}

/** Set once the first-run welcome line has been shown. */
@ConfigItem(
keyName = "seenIntro",
name = "",
description = "",
hidden = true
)
default boolean seenIntro()
{
return false;
}

/** Set once the player has pushed and jumped: the basics card no longer shows by itself. */
@ConfigItem(
keyName = "learnedBasics",
name = "",
description = "",
hidden = true
)
default boolean learnedBasics()
{
return false;
}
}
