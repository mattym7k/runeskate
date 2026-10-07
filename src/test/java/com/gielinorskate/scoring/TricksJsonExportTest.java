package com.gielinorskate.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.gielinorskate.tricks.SpinNames;
import com.gielinorskate.tricks.Trick;
import com.gielinorskate.tricks.TrickKind;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.junit.Test;

/**
 * Keeps the leaderboard backend's trick table ({@code backend/src/tricks.json}, used for its plausibility checks)
 * in step with {@link Trick} and the scoring rules. Fails when the committed file differs from what the enum
 * produces now. To regenerate it, run from {@code plugin/}:
 *
 * <pre>
 * GS_WRITE_TRICKS_JSON=1 ./gradlew cleanTest test --tests com.gielinorskate.scoring.TricksJsonExportTest
 * </pre>
 *
 * Skipped when the backend folder is not next to the plugin (a standalone checkout of the plugin).
 */
public class TricksJsonExportTest
{
	static final String WRITE_ENV = "GS_WRITE_TRICKS_JSON";
	static final String WRITE_PROPERTY = "gs.writeTricksJson";
	private static final String REGENERATE = "Regenerate it from plugin/ with: " + WRITE_ENV
		+ "=1 ./gradlew cleanTest test --tests " + TricksJsonExportTest.class.getName() + " (then commit it).";

	/**
	 * Biggest body spin (half turns either way) the backend accepts on one trick. Not derivable from the enum: air
	 * time has no hard cap (drops off high ground), so this is a conservative bound. At airSpinRate 7 rad/s
	 * (about 2.2 half turns a second) 10 half turns (an 1800) is about 4.5 s of air.
	 */
	static final int MAX_SPIN_HALF_TURNS = 10;
	/**
	 * Longest single grab / manual / grind the backend accepts, seconds. Conservative: nothing in the physics caps
	 * a hold (a long downhill manual or rail could go on), so this is generous.
	 */
	static final int MAX_HOLD_SECONDS = 120;
	/**
	 * Mirrors SkatePhysics.LATE_FLIP_MIN_FRACTION (private there): a late flick is sped up to finish before
	 * touchdown, but never below this fraction of the trick's own duration, so a board flip lasts at least
	 * this fraction of {@link Trick#duration}.
	 */
	static final float LATE_FLIP_MIN_FRACTION = 0.6f;

	@Test
	public void backendTrickTableMatchesTheEnum() throws IOException
	{
		File backendSrc = new File("../backend/src");
		assumeTrue("no backend next to the plugin", backendSrc.isDirectory());
		File json = new File(backendSrc, "tricks.json");
		String expected = generate();
		if ("1".equals(System.getenv(WRITE_ENV)) || Boolean.getBoolean(WRITE_PROPERTY))
		{
			Files.write(json.toPath(), expected.getBytes(StandardCharsets.UTF_8));
			return;
		}
		String actual = json.isFile() ? new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8) : "";
		assertEquals("backend/src/tricks.json is out of date with Trick.java / the scoring rules. " + REGENERATE,
			expected, actual.replace("\r\n", "\n"));
	}

	/** The table: scoring constants, then one entry per {@link Trick} by enum name, then the lone spin. */
	static String generate()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"generatedBy\": \"plugin/src/test/java/com/gielinorskate/scoring/TricksJsonExportTest.java - do not edit by hand\",\n");
		field(sb, "spinPointsPerHalfTurn", SpinNames.POINTS_PER_HALF_TURN);
		field(sb, "maxSpinHalfTurns", MAX_SPIN_HALF_TURNS);
		field(sb, "firstLandingBonus", ComboScorer.FIRST_LANDING_BONUS);
		field(sb, "cleanBonus", ComboScorer.CLEAN_BONUS);
		field(sb, "longComboSeconds", ComboScorer.LONG_COMBO_SECONDS);
		field(sb, "longComboExtraMultiplier", 1);
		field(sb, "minHoldMs", Math.round(ComboScorer.MIN_HOLD_SECONDS * 1000f));
		field(sb, "maxHoldSeconds", MAX_HOLD_SECONDS);
		sb.append("  \"tricks\": {\n");
		int spinMax = SpinNames.bonus(MAX_SPIN_HALF_TURNS);
		for (Trick t : Trick.values())
		{
			boolean hold = t.kind == TrickKind.GRAB || t.kind == TrickKind.MANUAL || t.kind == TrickKind.GRIND;
			// a spin renames (and adds its bonus to) the air's pop or board flip, never a body flip or a hold
			boolean spinnable = (t.kind == TrickKind.POP || t.kind == TrickKind.FLIP) && t.bodyFlipTurns == 0f;
			int maxPoints = hold ? t.points * MAX_HOLD_SECONDS : t.points + (spinnable ? spinMax : 0);
			int minMs;
			if (hold)
			{
				minMs = Math.round(ComboScorer.MIN_HOLD_SECONDS * 1000f);
			}
			else if (t.kind == TrickKind.FLIP && t.duration > 0f)
			{
				minMs = Math.round(LATE_FLIP_MIN_FRACTION * t.duration * 1000f);
			}
			else
			{
				minMs = 0; // pops have no rotation; body flips' rate is assisted, so no safe lower bound
			}
			entry(sb, t.name(), t.displayName, t.kind.name(), t.points, hold, spinnable, maxPoints, minMs, false);
		}
		// a spin landed with no pop or flip of its own in that air ("BS 360"): its own entry, the bonus only
		entry(sb, "SPIN", "Spin", "SPIN", 0, false, true, spinMax, 0, true);
		sb.append("  }\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static void field(StringBuilder sb, String name, int value)
	{
		sb.append("  \"").append(name).append("\": ").append(value).append(",\n");
	}

	private static void field(StringBuilder sb, String name, float value)
	{
		sb.append("  \"").append(name).append("\": ").append(number(value)).append(",\n");
	}

	private static String number(float value)
	{
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%s", value);
	}

	private static void entry(StringBuilder sb, String name, String displayName, String kind, int points, boolean hold,
		boolean spinnable, int maxPoints, int minMs, boolean last)
	{
		sb.append("    \"").append(name).append("\": {\"displayName\": \"").append(displayName)
			.append("\", \"kind\": \"").append(kind)
			.append("\", \"points\": ").append(points)
			.append(", \"hold\": ").append(hold)
			.append(", \"spinnable\": ").append(spinnable)
			.append(", \"maxPoints\": ").append(maxPoints)
			.append(", \"minMs\": ").append(minMs)
			.append(last ? "}\n" : "},\n");
	}
}
