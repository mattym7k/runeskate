# RuneSkate

Skateboard around Gielinor: push, carve, ollie, flip and grind with a chase camera. Everything is
client-side: only you (and party members who also use the plugin) see your skater.

## Getting started

1. Stand still somewhere with room to roll.
2. Press **Ctrl+K**, or open the **skateboard button** in the sidebar and press **Start skating**.
3. Press **Esc** (or Ctrl+K again) to stop. Press **F** to step off the board and walk.

The controls card shows the basics until you have pushed and jumped once. Press **H** while skating to bring it
back and to page through the flick tricks and the air & grind controls. The sidebar panel lists every trick
with a drawing of its mouse flick.

## Controls

| Key | What it does |
| --- | --- |
| W | Push (tap or hold) |
| A / D | Steer (arrow keys work too) |
| Shift + A / D | Tight turn |
| Shift alone | Brake |
| B | Brake straight away (rebindable brake key) |
| S | Crouch |
| Space | Wheelie (manual); with W a nose wheelie. Rebindable. |
| Q / E | Grab the board in the air (Indy / Melon). Move the mouse, no button, as you grab to aim it: up nose, down tail, right toe side, left heel side (swapped with Mirror flicks). Hold over half a second to tweak it. While rolling, hold Q / E to crouch and grab the board on the ground: style only (no points, no pushing while held); pop with it held to grab in the air, land with it held to keep grabbing |
| Up / Down arrow with a grab | Front flip / back flip (rebindable lean keys) |
| A / D in the air | Spin (180, 360, ...) |
| Shift + W / Shift + S in the air | Front flip / back flip |
| F | Step off and carry the board (see On foot). Rebindable. |
| R | After a bail: straight back on the board (skips the knockdown), or stop. Off in a Skate Duel |
| H | Controls card: next page / hide |
| Esc | Stop skating |

Chat typing and world clicks are paused while you skate; the sidebar and chatbox tabs stay clickable.

### Bails

A bail knocks you off the board: you tumble along the way you were going, hit the ground, lie still for half a
second and get up on foot, about a second and a half in all (never over two). The board flies off on its own and
lands nearby. A move key or Space while you are down gets you up at once; **F** while down gets your board back
straight away; **R** puts you straight back on the board where you fell. The combo is lost and a Skate Duel bail
costs 4 HP, as always. In a Skate Duel the whole knockdown plays and R does nothing, so it is the same for both
of you. Prefer a quick fall and straight back on? Set **After a bail** (Gameplay) to **Hop straight back
on**.

### On foot

Press **F** (the rebindable **Board on/off key**) while rolling or in a manual to step off. You walk with the
board under your arm; the combo in progress is banked first. Walking scores nothing.

| Key | What it does |
| --- | --- |
| W / A / S / D | Walk, from the camera: W walks away from it |
| Shift (hold) | Sprint |
| Space | Jump |
| Q / E | Drop the board at your feet, or pick it up within 1.5 tiles |
| F | Get on: the board goes under your feet, or step onto the dropped board within 1.5 tiles. Further away, one press calls the board back to your hands (no need to hold) |

Jump onto the dropped board (a jump that starts or lands within a tile of it), or jump while sprinting with the
board carried: that is a bigger hop, the board comes under your feet on the way down, and you land on it rolling
fast (at least 65% of push top speed from a sprint, 35% from a walk). F while walking or running keeps your speed.
Your skater walks with your own character's walk and run animations, played faster to match its brisker walk and
sprint, and the carried board stays in your hand as it swings. Getting on and off is a short blend, never a jump
cut. Party members see you walking, carrying the board or leaving it on the ground.

### Flick tricks (mouse)

Hold the **right mouse button**, drag **down**, then **flick** quickly:

- up: ollie
- up-left: kickflip, up-right: heelflip
- left: shove-it, right: frontside shove-it
- curve the flick for a varial, curl it all the way round for a 360 flip
- flick again mid-flip for a double (and a triple)
- push **up** first and flick down for the nollie versions
- hold **Shift** while flicking for the harder versions (impossible, hardflip, inward heelflip, bigspin)

The ring in the HUD shows your flick live. If a flick almost worked, a short hint says why ("Flick faster",
"Pull down first, then flick").

### Keyboard tricks

Set **Trick controls** to *Keyboard* or *Both*: Space ollie, 1 kickflip, 2 heelflip, 3 shove-it,
4 frontside shove-it, 5/6 varials, 7/8 360 flip / laser flip, 9/0 360 shove-its. Hold a key to crouch and let
go to pop; hold Alt for the nollie version and Shift for the harder version. With keyboard tricks on, the
wheelie key moves from Space to C.

### Controller

Play with a gamepad through the free AntiMicroX program, which turns each button into one key or mouse
movement. In the side panel, **Controller setup** links AntiMicroX, saves the RuneSkate profile (or use
`controller/RuneSkate.amgp`), lists the steps (Steam Input works too) and has a live **pad test**. Load the
profile in AntiMicroX, then turn on **Controller mode** and pick a **Controller preset** (RuneSkate settings, top
section).

The profile is universal: every button sends its own pad key (A F13, B F14, X F15, Y F16, LB F17, RB F18,
LT F19, RT F20, Back F21, Start F22, L3 F23, R3 F24, d-pad Insert / Delete / Home / End), the left stick the
arrow keys and the right stick the mouse. The plugin reads the pad keys in Controller mode only and turns each
into an action through the preset, by where you are (on the board, in the air, on foot). The **Skate 3** preset:

- Left stick: steer, and lean for grab-flips.
- A / X: push. B: brake. LB / RB: Shift (hard flips with a flick, tight turn with steer, brake held alone; in
  the air, hold with the left stick up / down for a front / back flip). LT / RT: grabs (in the air, or rolling
  for style).
- Right stick: flick tricks with no button, and a small held tilt up / down for a manual / nose manual.
- Y: board on / off. Start: stop. Back: get up. D-pad up: controls card.
- On foot: left stick walks, A (or LB / RB) held sprints, X jumps, LT / RT drop or pick up the board.

The **Tony Hawk's American Wasteland** preset puts the tricks on buttons:

- A: hold to crouch, let go to ollie (on foot: jump). Left stick: steer, spin in the air, up to push.
- X + a direction: flip tricks (left kickflip, right heelflip, up impossible, down pop shove-it, up-left / up-right
  varial kickflip / heelflip, down-left 360 flip, down-right hardflip, none kickflip); again mid-flip for a double.
- B + a direction, held: grabs (left melon, right indy, up nosegrab, down tailgrab, diagonals crail, mute,
  stalefish; none indy). On foot: drop or pick up the board.
- Y: near a rail, hold to catch it from further away (rails still catch without it); rolling with no rail near,
  step off; on foot, get back on.
- LB / RB: hard tricks, and spin faster in the air. Quick left stick up-then-down while rolling: manual
  (down-then-up: nose manual).
- Right stick: turns the camera, which eases back behind you after 1.5 s. The d-pad gives the diagonals (hold
  two); d-pad up alone is the controls card. Back: get up. Start: stop.

Same tricks, points and animations as the flicks; only the input differs.

**Customise controller** (side panel) sets each button's action on the board and on foot and saves it as your
**Custom** preset (start from Skate 3, Tony Hawk's American Wasteland, your saved layout or a blank one). **Copy layout code** gives a short code (`RSK1:...`) to share; **Paste layout code** checks a
code and shows what it changes before using it.

The card, hints, Trick Book and panel then show the preset's buttons. Every binding is one button to one key,
with no macros or turbo. Setup and details: [controller/README.md](controller/README.md).

### Grinds

Land on a rail, fence or ledge edge to grind. Hold W or S as you land (or during the grind) for other grinds;
land across the rail for slides.

## Skating level, board designs and goals

- Every landed combo gives Skating XP (its value / 10) on the game's XP table, saved per account. Level-ups get
  a RuneSkate chat line, the level-up graphic, bells and a RuneLite notification; every ten levels a banner shows.
- Your board is image-textured: pick its **grip**, **deck** and **wheels** designs separately under **Board** in
  the side panel (thumbnails; locked ones show their level). Changes show at once and are saved per account;
  party members see them on your skater.
- From the start: Classic black grip, Tropical deck and Natural wheels (the defaults) and the Bronze set. Then a set (grip, deck graphic and wheels) per level: Iron (10), Steel
  (20), Mithril (30), Adamant (40), Rune (50), Dragon (60), the gods (70: Bandos, Armadyl, Guthix, Zamorak and
  Saradomin) and Torva (99). Mix them freely. A level-up names the designs it unlocks.
- **Your own designs**: the **+ Custom** tile after each part's designs makes one from an image (PNG, JPG, GIF or
  BMP, up to 4096 px a side and 20 MB). Place it under the part's outline (drag, mouse wheel, arrow keys, Rotate,
  Flip, Fit, Fill), watch the in-game preview, name it and Save: it goes on your board at once. Right-click a
  custom design to edit, rename or delete it. Up to 50 are kept on this computer (in RuneLite's plugin data
  folder). Party members who turn on **Show party members' custom designs** see them on your skater (a small
  picture of each goes through RuneLite's party; they show the default until it arrives, and keep it in memory
  only). **Download template** saves the part's outline to paint on.
- Each session gets three random goals ("Land 5 different flips"), each worth 2,000 Skating XP.
- Variety pays: the first landing of each trick in a session scores 25% more, and a combo longer than 8 seconds
  gets one more multiplier.

## Settings

- **RuneSkate**: the skate mode key, **Controller mode**, **Controller preset** (Skate 3, Tony Hawk's American Wasteland or Custom), **Submit
  scores to the leaderboard** (opt-in) and showing the leaderboard on screen.
- **Play together**: to skate with friends, join a RuneLite Party (Party plugin, Create/Join party) on the same
  world. Then share your skater and see theirs, share your custom designs, and allow Skate Duel challenges (all
  on by default; sharing designs needs sharing your skater). **Show party members' custom designs** is off by
  default: turn it on to see the images your party members made for their boards. Friends are played back
  smoothly a little under a second behind (every update carries their last few positions and when their tricks
  happened, so jumps, flips and pushes line up with the path).
- **Gameplay**: trick controls (mouse flicks, keyboard or both), **Easy mode** (fewer bails) and **After a bail**
  (get knocked off and walk back to your board, or hop straight back on).
- **Camera & effects**: zoom (the mouse wheel changes it while skating), height (High by default), sounds and
  particles.
- **Advanced** (collapsed): speed and jump height, riding through plants, skating in PvP areas (off by
  default), every key rebind (wheelie, lean, brake, board on/off), flick button, mirrored flicks
  and sensitivity, the controls card, trick hints and flick visualizer, sound volume, bail jokes, level-up
  notifications, daily goals, **Board model** (RuneSkate or Classic), **Board detail** (party members' boards are
  always Normal), smooth motion and showing grindable edges.

## Safety

Skating is blocked in combat, in instances and PvP minigames, and (unless you opt in) in the Wilderness and on
PvP worlds. Taking damage, moving your real character or a loading screen ends skating. Your real character
stands still while you skate.

## Leaderboards

The online leaderboards (best combo, timed 2-minute run and Skating XP, weekly and all time) are opt-in: turn on
**Submit scores to the leaderboard** in the RuneSkate settings. This sends your scores and your RuneScape name to
the RuneSkate leaderboard server, a 3rd-party server not controlled or verified by RuneLite developers, which also
sees your IP address. Scores are only sent from normal worlds. With the setting off, nothing is sent.

## Chat messages

Every chat line from the plugin (level-ups, goals, duel calls, bail jokes, hints) starts with **[RuneSkate]**, so
it is never mistaken for a game message.

## Development

`./gradlew run` launches a developer RuneLite client with the plugin loaded. In developer mode the
`::skatepose`, `::skategrinds`, `::skateboxes` and `::skategesture` commands turn on debug views, and
`::skatelevel [1-99]` shows or sets your Skating level to try the designs (the account is marked as set by hand);
`::skateboard runeskate|classic` switches your board model until the setting changes.
`::skateghosts` prints one line per party ghost: distance, detail level, what its body is drawn as, the reason for
any fallback, and how it moves.

The board model and the bundled board designs ship pre-baked in the plugin's resources.
