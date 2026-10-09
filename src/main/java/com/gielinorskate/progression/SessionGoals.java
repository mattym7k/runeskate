package com.gielinorskate.progression;

import com.gielinorskate.Text;
import com.gielinorskate.scoring.ComboScorer;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import com.gielinorskate.ui.TrickGuide;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
* Three goals picked at random from a pool ("Land 5 different flips"), counted from the combos that land; they
* last the UTC day per account ({@link DailyGoals}). Completing one is worth {@link #BONUS_XP} Skating XP. Pure.
*/
public final class SessionGoals
{
public static final int GOALS_PER_SESSION = 3;
public static final int BONUS_XP = 2_000;

/** The pool. Each goal counts something from landed combos up to its target (wording: sg. in the bundled text). */
public enum Type
{
DIFFERENT_FLIPS(5),
GRIND_SECONDS(10),
BIG_COMBO(20_000),
BODY_FLIP(1),
SHIFT_TRICKS(3),
TOTAL_POINTS(50_000),
MANUAL_SECONDS(5),
SPIN_360(1),
CLEAN_LANDINGS(5),
MULTIPLIER(5),
DIFFERENT_GRINDS(3),
GRABS(3);

public final String text;
public final int target;

Type(int target)
{
this.target = target;
text = Text.get("sg." + name());
}
}

/** One goal of this session and how far it has got (uncapped, as saved by {@link DailyGoals}). */
public static final class Goal
{
public final Type type;
float progress;
boolean done;
/** Tricks already counted, for the "different" goals. */
final Set<Trick> seen = EnumSet.noneOf(Trick.class);

Goal(Type type, float progress, boolean done, Set<Trick> seen)
{
this.type = type;
this.progress = progress;
this.done = done;
this.seen.addAll(seen);
}

public boolean isDone()
{
return done;
}

/** "3/5", "4.2/10 s", "12,300/20,000"; "Done" once complete. */
public String progressText()
{
if (done)
return "Done";
float p = Math.min(progress, type.target);
switch (type)
{
case GRIND_SECONDS:
case MANUAL_SECONDS:
return String.format("%.1f/%d s", Math.floor(p * 10f) / 10f, type.target);
case BIG_COMBO:
case TOTAL_POINTS:
return String.format("%,d/%,d", (int) p, type.target);
default:
return (int) p + "/" + type.target;
}
}
}

/** Shift tricks (Shift held as the flick fires), as the trick guide groups them. */
static final Set<Trick> SHIFT = Arrays.stream(Trick.values())
.filter(t -> TrickGuide.entry(t).group == TrickGuide.Group.SHIFT).collect(Collectors.toSet());

private final List<Goal> goals = new ArrayList<>(GOALS_PER_SESSION);

/** A new session: three different goals from the pool. */
public void start(Random random)
{
List<Type> pool = new ArrayList<>(Arrays.asList(Type.values()));
Collections.shuffle(pool, random);
start(pool.subList(0, GOALS_PER_SESSION));
}

/** A new session with these goals. */
public void start(List<Type> types)
{
restore(types.stream().map(t -> new Goal(t, 0f, false, Collections.emptySet())).collect(Collectors.toList()));
}

/** Goals restored from saved state. */
void restore(List<Goal> restored)
{
goals.clear();
goals.addAll(restored);
}

public List<Goal> goals()
{
return Collections.unmodifiableList(goals);
}

/** The first goal not yet done, or null when all are (or there are none). */
public Goal current()
{
return goals.stream().filter(g -> !g.done).findFirst().orElse(null);
}

/** Counts a landed combo; returns the goals it completed. */
public List<Goal> onLanded(ComboScorer.Summary combo)
{
List<Goal> completed = new ArrayList<>(1);
for (Goal g : goals)
{
if (!g.done)
{
count(g, combo);
if (g.progress >= g.type.target)
{
g.done = true;
completed.add(g);
}
}
}
return completed;
}

private static void count(Goal g, ComboScorer.Summary combo)
{
switch (g.type)
{
case DIFFERENT_FLIPS:
g.progress = seen(g, combo, t -> t.kind == TrickKind.FLIP && t.bodyFlipTurns == 0f);
break;
case DIFFERENT_GRINDS:
g.progress = seen(g, combo, t -> t.kind == TrickKind.GRIND);
break;
case GRIND_SECONDS:
g.progress += combo.grindSeconds;
break;
case MANUAL_SECONDS:
g.progress += combo.manualSeconds;
break;
case BIG_COMBO:
g.progress = Math.max(g.progress, combo.value);
break;
case TOTAL_POINTS:
g.progress += combo.value;
break;
case BODY_FLIP:
g.progress += combo.tricks.stream().filter(t -> t.bodyFlipTurns != 0f).count();
break;
case SHIFT_TRICKS:
g.progress += combo.tricks.stream().filter(SHIFT::contains).count();
break;
case GRABS:
g.progress += combo.tricks.stream().filter(t -> t.kind == TrickKind.GRAB).count();
break;
case SPIN_360:
g.progress = combo.maxSpinHalfTurns >= 2 ? 1 : g.progress;
break;
case CLEAN_LANDINGS:
g.progress += combo.clean ? 1 : 0;
break;
default:
g.progress = Math.max(g.progress, combo.multiplier);
break;
}
}

/** Adds the combo's tricks passing {@code test} to the goal's seen ones; how many it has seen. */
private static int seen(Goal g, ComboScorer.Summary combo, Predicate<Trick> test)
{
combo.tricks.stream().filter(test).forEach(g.seen::add);
return g.seen.size();
}
}
