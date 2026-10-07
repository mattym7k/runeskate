# Playing RuneSkate with a controller

The plugin has no gamepad code of its own. A free program, **AntiMicroX**, turns each controller button into a key
press or mouse movement, and the plugin's **Controller mode** reads those. What each button does is set by the
**Controller preset** setting, so the profile in AntiMicroX never changes.

## Setup

The side panel's **Controller setup** view walks through this, with a live pad test.

1. Install AntiMicroX from <https://github.com/AntiMicroX/antimicrox/releases> (Windows, Linux; free and open
   source).
2. Plug in your controller and start AntiMicroX. Xbox pads work as they are. PlayStation and other pads use the
   same profile, because AntiMicroX reads every pad as an SDL "game controller" with an Xbox layout.
3. Get the profile: **Controller setup → Save RuneSkate profile…** in the side panel, or `RuneSkate.amgp` from
   this folder (they are the same file). In AntiMicroX, click **Load** and pick it.
4. In RuneLite, open the RuneSkate settings. In the top **RuneSkate** section, turn on **Controller mode** and pick
   a **Controller preset** (Skate 3, Tony Hawk's American Wasteland, or your Custom layout).
5. Start skating (Ctrl+K, or the sidebar button). Keep the game window focused and the mouse cursor over the game.

**Updating from an older RuneSkate:** the old profile sent different keys (Shift, Q, E, F, R, Esc, H, B), so load
the new profile again. Chat reminds you once if Controller mode is on. The old "Pad A key" and "Pad X key"
settings are retired: the pad keys are fixed now.

## The universal profile

Every button sends its own **pad key**: a key that keyboards don't have or that games rarely use, the same on
every keyboard layout, and never typed.

| Pad button | Sends |
| --- | --- |
| A | F13 |
| B | F14 |
| X | F15 |
| Y | F16 |
| LB | F17 |
| RB | F18 |
| LT | F19 |
| RT | F20 |
| Back / View | F21 |
| Start / Menu | F22 |
| L3 (left stick click) | F23 |
| R3 (right stick click) | F24 |
| D-pad up | Insert |
| D-pad down | Delete |
| D-pad left | Home |
| D-pad right | End |
| Left stick | The arrow keys, four-way |
| Right stick | Mouse movement |

The plugin reads the pad keys only while **Controller mode** is on; a keyboard player's F13 or Home is just a
key. If one of your key settings (wheelie, brake, lean, board on/off, a skate mode key with no modifier) is a pad
key, the setting wins and that button does nothing; chat says so when you start skating.

**Why these keys work:** AntiMicroX stores keys as Qt key codes and on Windows sends them with `SendInput` as the
matching virtual key: Qt F13 to F24 as `VK_F13` to `VK_F24` (its key mapper maps Qt::Key_F1 + n to VK_F1 + n for
every F key up to F24), and Insert, Delete, Home and End as `VK_INSERT`, `VK_DELETE`, `VK_HOME` and `VK_END`
with the extended-key flag (AntiMicroX's `scancodeFromVirtualKey` sets it for exactly these navigation keys), so
they arrive as the navigation block, not the number pad, whatever Num Lock says. Java's Windows toolkit delivers
them as `KeyEvent.VK_F13` to `VK_F24`, `VK_INSERT`, `VK_DELETE`, `VK_HOME` and `VK_END` through RuneLite's key
listeners. F13 and F14 were confirmed in game with the earlier profile; the rest follow the same code paths.

## Presets

A preset maps each pad button to an action, by where you are: on the board, in the air (the board action unless
the preset gives one), or on foot. Held buttons are read where you are now, so a button held through a mount or
dismount does its new job without a new press.

### Skate 3 (default)

| Controller | On the board | On foot |
| --- | --- | --- |
| Left stick left / right | Steer; spin in the air | Walk left / right |
| Left stick up / down | Lean: with a grab held in the air, a front / back flip | Walk forward / back |
| A | Push (tap or hold); held as you land on a rail, other grinds | Sprint (hold) |
| X | Push (tap or hold) | Jump |
| B | Brake | |
| Y | Step off and carry the board | Get on, or call a far board back (one press) |
| LB / RB | Shift (hard tricks): hard flips with a flick; tight turn with steer; brake held alone (after a beat). In the air, with the left stick up / down: front / back flip | Sprint (hold) |
| LT / RT | Grab (left / right hand) | Drop the board, or pick it up within 1.5 tiles |
| Right stick | Flick tricks, grab aiming, manuals | (ignored) |
| Start | Stop skating | Stop skating |
| Back | Get up after a bail, or stop | Knocked off: get up |
| D-pad up | Controls card | Controls card |
| L3, R3, d-pad down / left / right | (nothing) | (nothing) |

This is exactly the old pad layout: the plugin's tests drive the old profile's keys through the old input and
the new pad keys through the Skate 3 preset and check they read the same.

### Custom

**Customise controller** in the side panel lists every button with its action on the board and on foot. Start
from Skate 3, your saved layout or a blank one, change what you like, and **Save as my Custom preset** (this also
selects Custom). Actions you can give a button:

- On the board: push, mongo push (a push for now), brake, ollie (hold to crouch, let go to pop), grab left /
  right hand, the hard tricks modifier (Shift), lean forward / back, board on / off, reset, stop skating, the
  controls card, and the button-trick actions: flip trick (+ direction), grab (+ direction), grind (or step off),
  hard tricks + spin faster.
- On foot: sprint, jump, drop / pick up the board, turn the camera (hold, and move the right stick), board on /
  off, reset, stop skating, the controls card.

An action no button does still works from its keyboard key, and the card and Trick Book name that key. A layout
with a flip or grab button plays with button tricks, as Tony Hawk's American Wasteland does: the right stick turns
the camera and the left stick and d-pad give the trick directions (see below).

**Sharing layouts:** **Copy layout code** puts a short code on the clipboard, such as `RSK1:` followed by letters.
**Paste layout code…** reads one: it is checked strictly (its version, every button and action, at most 512
characters), and you see what it changes before it is used.

### Tony Hawk's American Wasteland

Tricks on buttons: hold a direction and press the trick button. The right stick turns the camera.

| Controller | On the board | On foot |
| --- | --- | --- |
| A | Hold to crouch and charge, let go to ollie | Jump |
| X + direction | Flip trick (table below). In the air, or rolling (it pops straight into the flip, as a flick does). Again mid-flip: double, then triple | |
| B + direction | Grab, held (table below); rolling: a grab for style | Drop the board, or pick it up |
| Y | Near a rail (in the air or rolling into it): hold to catch the rail from further away. Rolling with no rail near: step off | Get on, or call a far board back |
| LB / RB | Hard tricks (as Shift: hard flips, tight turn with steer, brake held alone). In the air, hold to spin faster | Sprint (hold) |
| Left stick | Steer; spin in the air. Up: push. The direction for X and B. Quick up then down (within 0.3 s) while rolling: manual; down then up: nose manual | Walk |
| Right stick | Turn the camera; it eases back behind you 1.5 s after you let go | Turn the camera |
| D-pad | A direction for X and B: hold two for a diagonal. D-pad up alone, let go: controls card | D-pad up: controls card |
| Back | Get up after a bail, or stop | Knocked off: get up |
| Start | Stop skating | Stop skating |
| LT, RT, L3, R3 | (nothing) | (nothing) |

**Flip tricks (X + direction):** left kickflip, right heelflip, up impossible, down pop shove-it, up-left varial
kickflip, up-right varial heelflip, down-left 360 flip, down-right hardflip, no direction kickflip. Hold LB / RB
for the hard version (kickflip: hardflip, heelflip: inward heelflip, shove-it: bigspin).

**Grabs (B + direction):** left melon, right indy, up nosegrab, down tailgrab, up-left crail, up-right mute,
down-left stalefish, down-right indy, no direction indy. Held over half a second each turns into its tweak, as
always.

These are the same tricks as the flicks and Q / E: the same names, points, animations, timings and tweaks. Only
the input differs. Notes:

- **Diagonals:** the left stick is four-way (the profile is the same for every preset), so it gives the four
  straight directions. The d-pad gives all eight: hold two d-pad buttons for a diagonal, or one with the stick.
  A trick press waits 50 ms for its direction, so pressing the direction and the button together works.
- **The grind button:** rails still catch by themselves when you land on them, as in every preset. Holding Y
  only widens the catch (from about a third of a tile to the side to over half a tile), so it can only help.
- **Manuals** hold by themselves until you pop, slow down or bail; the other gesture switches between manual and
  nose manual. The stick's up does not push while you manual.
- **Mouse flicks** with a mouse button still work; the keyboard's trick keys too.

**Right stick (Skate 3 and layouts without trick buttons):**

- **Flick tricks:** pull the stick down, then flick it, as on the mouse. No button is needed. Up is an ollie,
  up-left a kickflip, and so on. Push up first and flick down for the nollie versions.
- **Manuals:** tilt the stick a little up and hold it for a quarter of a second to start a manual. Tilt it a
  little down for a nose manual. Let go to end it. A full flick during a manual pops out of it.
- **Grabs:** while a grab button is held, the stick only aims the grab and never does a flick. Up is the nose,
  down the tail, right the toe side and left the heel side (swapped with Mirror flicks).
- **Hard flicks:** hold the hard tricks button (LB or RB in Skate 3) as the stick flicks for the harder version
  (impossible, hardflip, inward heelflip, bigspin).

The controls card, the flick hints, the Trick Book and the side panel show the preset's buttons while Controller
mode is on.

## Steam Input instead of AntiMicroX

Steam Input can do the same: map each button to its key from the table above, one key per button (no macros,
turbo, chords or action sets), the left stick as a four-way d-pad sending the arrow keys, and the right stick as
a joystick mouse. Keep the right stick's speed close to the profile's (below) so flicks and manuals read the same.

## Rules

Every binding in the profile is **one button to one key** (or one mouse direction). There are no macros, no
turbo, no button sequences and no set switching. One button press is one key press, so it is the same as playing
on the keyboard and mouse, within Jagex's rules on input. The plugin itself does not read the controller or
inject any input.

## Profile settings the plugin relies on

AntiMicroX's stick-to-mouse output moves the cursor at a speed set by the tilt. A centred stick leaves the cursor
still. The plugin reads the right stick from that motion. Keep these values if you edit the profile:

- **Right stick:** "Mouse (Normal)" cursor mode (not Spring), mouse speed X and Y 60, **Quadratic** curve, no
  extra acceleration, dead zone 8000, diagonal range 90. With these, full tilt moves the cursor about 1,200
  px/s and a small tilt moves it about 50 to 150 px/s.
- **How the plugin reads it:**
  - A stroke starts when the cursor moves after being still, and ends when it stops for 80 ms.
  - A stroke that stays under about 350 px/s and keeps going up or down for 0.25 s is a manual.
  - Anything faster is a flick, read by the same recogniser and Flick sensitivity setting as the mouse.
- **Left stick:** four-way mode, so a steer is never also a lean.
- **Triggers:** a light pull is ignored (dead zone 4000).

## Not on the pad

Some controls have no button in the Skate 3 preset (or in Tony Hawk's American Wasteland: there the nollies,
laser flip, 360 shove-its and FS shove-its have no button). Use the keyboard (or a mouse flick) for them:

- **S:** the pad has no S, so the 5-0 (hold S as you land on a straight rail) is keyboard only.
- **Space:** the manual key is keyboard only; on the pad, manuals are the right stick's small tilt.
- **Spin and grab-flip together:** the left stick is four-way, so it either steers (spins) or leans, never both
  at once. On the keyboard, A / D and the arrow keys can be held together.

## Troubleshooting

- **Use the pad test first:** side panel → Controller setup → Test your pad. Each button should light up while
  held. If none do, AntiMicroX has not loaded the profile, or the game window is not focused.
- **A button does nothing in game but lights up in the test:** check that Controller mode is on and which preset
  is picked (the Trick Book's Controller section lists what each button does). Chat names a key setting that
  takes a pad key when you start skating.
- **Linux:** many Linux keyboard setups give the F13 to F24 key codes other names (such as XF86Tools), so
  AntiMicroX may not be able to send them. The pad test shows which buttons arrive.

## Tips

- Because the stick moves the cursor, a long manual or a held wind-up slowly drifts it towards the edge of the
  screen. If the cursor reaches the edge, the stroke stops. Nudge the cursor back to the middle now and then. A
  fullscreen or large game window gives the most room. With Tony Hawk's American Wasteland the same goes for
  turning the camera: at the edge of the window it stops turning.
- To play with the keyboard and mouse again, turn Controller mode off. The brake key (B) works either way.
