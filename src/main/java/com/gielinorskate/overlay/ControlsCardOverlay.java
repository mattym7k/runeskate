package com.gielinorskate.overlay;

import com.gielinorskate.controller.PadAction;
import com.gielinorskate.controller.PadContext;
import com.gielinorskate.controller.PadPreset;
import com.gielinorskate.input.KeyboardTricks;
import com.gielinorskate.session.SkateSession;
import com.gielinorskate.tricks.Gesture;
import com.gielinorskate.tricks.Gesture.Direction;
import com.gielinorskate.ui.ControllerGlyphs;
import com.gielinorskate.ui.GestureGlyphPainter;
import com.gielinorskate.ui.PadWords;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The controls card, in the same visual style as {@link ScoreOverlay}: text over a translucent dark backing. One
 * card with the essential controls for the current mode (on the board or on foot; keys, flicks or pad buttons),
 * the core flicks drawn as arrows, and a pointer to the side panel's Trick Book for everything else. Shown by
 * itself until the player has pushed and jumped once; H shows and hides it ({@link ControlsCardTimer}, owned and
 * driven by {@link SkateSession}).
 */
@Singleton
public class ControlsCardOverlay extends Overlay
{
	private static final Color BACKING_COLOR = new Color(10, 12, 18, 180);
	private static final Color TEXT_COLOR = Color.WHITE;
	private static final Color TITLE_COLOR = new Color(255, 215, 110);
	private static final Color SHADOW_COLOR = new Color(0, 0, 0, 140);
	private static final Color GLYPH_WIND_UP_COLOR = new Color(170, 180, 200);
	private static final Color GLYPH_FLICK_COLOR = new Color(120, 200, 255);
	private static final int SHADOW_OFFSET = 1;
	/** Font sizes tried, largest first, until the card fits its region. */
	static final int MAX_FONT_SIZE = 16;
	static final int MIN_FONT_SIZE = 9;
	/** Viewport margin as a fraction of its height, clamped to [MIN_MARGIN, MAX_MARGIN] px. */
	private static final float MARGIN_FRACTION = 0.03f;
	private static final int MIN_MARGIN = 6;
	private static final int MAX_MARGIN = 24;
	/** Gap kept between the card and the top of the trick stack, px. */
	private static final int STACK_GAP = 6;

	/** What the card shows: the settings its wording depends on. A value: cached by equality. */
	public static final class Spec
	{
		final String manualKey;
		final boolean keyboardTricks;
		final boolean mouseTricks;
		/** "right", "left" or "middle". */
		final String flickButton;
		final boolean mirror;
		final boolean learned;
		/** Controller mode: the controls are named with drawn controller buttons. */
		final boolean controller;
		/** Off the board: walking controls. */
		final boolean onFoot;
		/** The board on/off key (F by default). */
		final String boardKey;
		/** Controller mode: the layout whose buttons the card names. */
		final PadPreset preset;

		public Spec(String manualKey, boolean keyboardTricks, boolean mouseTricks, String flickButton, boolean mirror,
			boolean learned)
		{
			this(manualKey, keyboardTricks, mouseTricks, flickButton, mirror, learned, false);
		}

		public Spec(String manualKey, boolean keyboardTricks, boolean mouseTricks, String flickButton, boolean mirror,
			boolean learned, boolean controller)
		{
			this(manualKey, keyboardTricks, mouseTricks, flickButton, mirror, learned, controller, false, "F");
		}

		/**
		 * @param onFoot off the board: the card shows the on-foot controls
		 * @param boardKey the board on/off key's name
		 */
		public Spec(String manualKey, boolean keyboardTricks, boolean mouseTricks, String flickButton, boolean mirror,
			boolean learned, boolean controller, boolean onFoot, String boardKey)
		{
			this(manualKey, keyboardTricks, mouseTricks, flickButton, mirror, learned, controller, onFoot, boardKey,
				PadPreset.skate3());
		}

		/** @param preset the controller layout (controller mode) */
		public Spec(String manualKey, boolean keyboardTricks, boolean mouseTricks, String flickButton, boolean mirror,
			boolean learned, boolean controller, boolean onFoot, String boardKey, PadPreset preset)
		{
			this.preset = preset;
			this.onFoot = onFoot;
			this.boardKey = boardKey;
			this.controller = controller;
			this.manualKey = manualKey;
			this.keyboardTricks = keyboardTricks;
			this.mouseTricks = mouseTricks;
			this.flickButton = flickButton;
			this.mirror = mirror;
			this.learned = learned;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Spec))
			{
				return false;
			}
			Spec s = (Spec) o;
			return keyboardTricks == s.keyboardTricks && mouseTricks == s.mouseTricks
				&& mirror == s.mirror && learned == s.learned && controller == s.controller
				&& manualKey.equals(s.manualKey)
				&& flickButton.equals(s.flickButton)
				&& onFoot == s.onFoot && boardKey.equals(s.boardKey) && preset.equals(s.preset);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(manualKey, keyboardTricks, mouseTricks, flickButton, mirror, learned, controller,
				onFoot, boardKey, preset);
		}
	}

	/**
	 * One line of content: text, an optional flick picture in front of it, and whether it is the title. A row of
	 * {@link #cells} is several pictures with a word each on one line (never wrapped); its text is their words,
	 * for searching.
	 */
	static final class Row
	{
		final String text;
		final Gesture glyph;
		final boolean title;
		/** Picture-and-word pairs drawn side by side, or null for a plain row. */
		final List<Cell> cells;

		Row(String text, Gesture glyph, boolean title)
		{
			this(text, glyph, title, null);
		}

		private Row(String text, Gesture glyph, boolean title, List<Cell> cells)
		{
			this.text = text;
			this.glyph = glyph;
			this.title = title;
			this.cells = cells;
		}

		static Row ofCells(List<Cell> cells)
		{
			StringBuilder sb = new StringBuilder();
			for (Cell c : cells)
			{
				sb.append(sb.length() > 0 ? "   " : "").append(c.text);
			}
			return new Row(sb.toString(), null, false, cells);
		}
	}

	/** A flick picture and the word after it, in a row of cells. */
	static final class Cell
	{
		final Gesture glyph;
		final String text;

		Cell(Gesture glyph, String text)
		{
			this.glyph = glyph;
			this.text = text;
		}
	}

	private final Client client;
	private final BooleanSupplier isSkating;
	private final Supplier<Float> alphaSupplier;
	private final Supplier<Spec> specSupplier;

	/** Fitted cards by spec and region, so stepping on and off the board never lays out again. */
	private static final int LAYOUTS_KEPT = 32;
	private final Map<LayoutKey, Card> layouts = new LinkedHashMap<LayoutKey, Card>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<LayoutKey, Card> eldest)
		{
			return size() > LAYOUTS_KEPT;
		}
	};

	/** The card's fonts, {@link #MIN_FONT_SIZE}..{@link #MAX_FONT_SIZE} by size; made once (see {@link #warmFonts}). */
	private static volatile Font[] fonts;

	/** A cached layout's key: what the card shows and the region it was fitted to. */
	private static final class LayoutKey
	{
		final Spec spec;
		final int w;
		final int h;

		LayoutKey(Spec spec, int w, int h)
		{
			this.spec = spec;
			this.w = w;
			this.h = h;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof LayoutKey))
			{
				return false;
			}
			LayoutKey k = (LayoutKey) o;
			return w == k.w && h == k.h && spec.equals(k.spec);
		}

		@Override
		public int hashCode()
		{
			return (spec.hashCode() * 31 + w) * 31 + h;
		}
	}

	/** The card's bold sans-serif font at {@code size} (MIN_FONT_SIZE..MAX_FONT_SIZE), created once. */
	static Font font(int size)
	{
		Font[] f = fonts;
		if (f == null)
		{
			synchronized (ControlsCardOverlay.class)
			{
				f = fonts;
				if (f == null)
				{
					f = new Font[MAX_FONT_SIZE + 1];
					for (int i = MIN_FONT_SIZE; i <= MAX_FONT_SIZE; i++)
					{
						f[i] = new Font(Font.SANS_SERIF, Font.BOLD, i);
					}
					fonts = f;
				}
			}
		}
		return f[size];
	}

	/**
	 * Creates the card's fonts and loads their glyph data now. The first sans-serif font of a JVM is slow (font
	 * configuration, hundreds of ms), so the plugin calls this off the client thread at start-up rather than
	 * letting the first card shown stall a frame. Safe on any thread.
	 */
	public static void warmFonts()
	{
		FontRenderContext frc = new FontRenderContext(null, true, false);
		for (int i = MIN_FONT_SIZE; i <= MAX_FONT_SIZE; i++)
		{
			font(i).getStringBounds("Skating basics: push, jump 0123456789", frc);
		}
	}

	@Inject
	ControlsCardOverlay(Client client, SkateSession session)
	{
		this(client, session::isActive, session::getControlsCardAlpha, session::getControlsCardSpec);
	}

	ControlsCardOverlay(Client client, BooleanSupplier isSkating, Supplier<Float> alphaSupplier,
		Supplier<Spec> specSupplier)
	{
		this.client = client;
		this.isSkating = isSkating;
		this.alphaSupplier = alphaSupplier;
		this.specSupplier = specSupplier;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!isSkating.getAsBoolean())
		{
			return null;
		}
		float alpha = alphaSupplier.get();
		if (alpha <= 0f)
		{
			return null;
		}

		int[] region = region(client.getViewportXOffset(), client.getViewportYOffset(), client.getViewportWidth(),
			client.getViewportHeight(), client.getCanvasWidth(), client.getCanvasHeight());
		Spec spec = specSupplier.get();
		LayoutKey key = new LayoutKey(spec, region[2], region[3]);
		Card cached = layouts.get(key);
		if (cached == null)
		{
			cached = fit(graphics, rows(spec), region[2], region[3]);
			layouts.put(key, cached);
		}

		Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Composite oldComposite = graphics.getComposite();
		try
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			draw(graphics, cached, region[0], region[1]);
		}
		finally
		{
			graphics.setComposite(oldComposite);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				oldAntialiasing == null ? RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT : oldAntialiasing);
		}
		return null;
	}

	/**
	 * The card's region {x, y, maxWidth, maxHeight}: the top-left of the game viewport (so fixed mode never
	 * spills into the side panel), inset by a margin, down to just above the tallest trick stack of
	 * {@link HudLayout}. Falls back to the whole canvas when the viewport is empty.
	 */
	static int[] region(int vx, int vy, int vw, int vh, int canvasW, int canvasH)
	{
		HudLayout hud = HudLayout.of(vx, vy, vw, vh, canvasW, canvasH);
		if (vw <= 0 || vh <= 0)
		{
			vx = 0;
			vy = 0;
			vw = canvasW;
			vh = canvasH;
		}
		int margin = Math.max(MIN_MARGIN, Math.min(MAX_MARGIN, Math.round(vh * MARGIN_FRACTION)));
		int x = vx + margin;
		int y = vy + margin;
		int maxW = Math.max(0, vw - 2 * margin);
		int maxH = Math.max(0, Math.min(vy + vh - margin, hud.stackTopY - STACK_GAP) - y);
		return new int[]{x, y, maxW, maxH};
	}

	/** A laid-out line: its text, the flick picture in front of it (first line of a row only), the title flag. */
	static final class Line
	{
		final String text;
		final Gesture glyph;
		/** Indented past the picture column (a wrapped piece of a picture row). */
		final boolean indented;
		final boolean title;
		/** A row of cells, drawn side by side; null for a text line. */
		final List<Cell> cells;

		Line(String text, Gesture glyph, boolean indented, boolean title, List<Cell> cells)
		{
			this.text = text;
			this.glyph = glyph;
			this.indented = indented;
			this.title = title;
			this.cells = cells;
		}
	}

	/** A laid-out card: font, wrapped lines and the box size (padding included). */
	static final class Card
	{
		final Font font;
		final List<Line> lines;
		final int padding;
		final int lineGap;
		/** Width of the picture column (a line height plus a gap). */
		final int glyphColumn;
		/** Space between the cells of a row of cells. */
		final int cellGap;
		final int width;
		final int height;

		Card(Font font, List<Line> lines, int padding, int lineGap, int glyphColumn, int cellGap, int width,
			int height)
		{
			this.font = font;
			this.lines = lines;
			this.padding = padding;
			this.lineGap = lineGap;
			this.glyphColumn = glyphColumn;
			this.cellGap = cellGap;
			this.width = width;
			this.height = height;
		}
	}

	/**
	 * Lays the rows out at the largest font size from {@link #MAX_FONT_SIZE} down to {@link #MIN_FONT_SIZE} whose
	 * box, with lines word-wrapped to the width, fits maxW x maxH; the smallest size when none does.
	 */
	static Card fit(Graphics2D graphics, List<Row> content, int maxW, int maxH)
	{
		Card card = null;
		for (int size = MAX_FONT_SIZE; size >= MIN_FONT_SIZE; size--)
		{
			Font font = font(size);
			FontMetrics metrics = graphics.getFontMetrics(font);
			int padding = Math.max(4, Math.round(size * 0.55f));
			int lineGap = Math.max(1, size / 5);
			int glyphColumn = metrics.getHeight() + Math.max(3, size / 3);
			int cellGap = size * 2;
			List<Line> lines = new ArrayList<>();
			int textW = 0;
			for (Row row : content)
			{
				if (row.cells != null)
				{
					int w = 0;
					for (Cell c : row.cells)
					{
						w += (w > 0 ? cellGap : 0) + glyphColumn + ControllerGlyphs.width(c.text, metrics);
					}
					lines.add(new Line(row.text, null, false, row.title, row.cells));
					textW = Math.max(textW, w);
					continue;
				}
				int indent = row.glyph != null ? glyphColumn : 0;
				List<String> pieces = new ArrayList<>();
				wrap(row.text, metrics, maxW - 2 * padding - indent, pieces);
				for (int i = 0; i < pieces.size(); i++)
				{
					lines.add(new Line(pieces.get(i), i == 0 ? row.glyph : null, indent > 0, row.title, null));
					textW = Math.max(textW, indent + ControllerGlyphs.width(pieces.get(i), metrics));
				}
			}
			int lineStep = metrics.getHeight() + lineGap;
			int width = textW + padding * 2;
			int height = lineStep * lines.size() - lineGap + padding * 2;
			card = new Card(font, lines, padding, lineGap, glyphColumn, cellGap, width, height);
			if (width <= maxW && height <= maxH)
			{
				break;
			}
		}
		return card;
	}

	/**
	 * Appends {@code line} to {@code out}, broken at spaces into pieces no wider than maxW where possible. Glyph
	 * tokens ("{A}") hold no spaces, so they are never split.
	 */
	private static void wrap(String line, FontMetrics metrics, int maxW, List<String> out)
	{
		String rest = line;
		while (ControllerGlyphs.width(rest, metrics) > maxW)
		{
			int cut = -1;
			for (int i = rest.indexOf(' '); i > 0; i = rest.indexOf(' ', i + 1))
			{
				if (ControllerGlyphs.width(rest.substring(0, i).trim(), metrics) > maxW)
				{
					break;
				}
				cut = i;
			}
			if (cut <= 0)
			{
				break; // one word wider than the box: leave it whole
			}
			out.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}
		out.add(rest);
	}

	private static void draw(Graphics2D graphics, Card card, int x, int y)
	{
		graphics.setFont(card.font);
		FontMetrics metrics = graphics.getFontMetrics();
		int lineStep = metrics.getHeight() + card.lineGap;

		graphics.setColor(BACKING_COLOR);
		graphics.fill(new RoundRectangle2D.Float(x, y, card.width, card.height, 10, 10));

		int lineTop = y + card.padding;
		for (Line line : card.lines)
		{
			int textX = x + card.padding + (line.indented ? card.glyphColumn : 0);
			int baseline = lineTop + metrics.getAscent();
			if (line.cells != null)
			{
				int cellX = x + card.padding;
				for (Cell c : line.cells)
				{
					GestureGlyphPainter.paint(graphics, c.glyph, cellX, lineTop, metrics.getHeight(),
						GLYPH_WIND_UP_COLOR, GLYPH_FLICK_COLOR);
					cellX += card.glyphColumn;
					ControllerGlyphs.drawText(graphics, c.text, cellX, baseline, TEXT_COLOR, SHADOW_COLOR,
						SHADOW_OFFSET);
					cellX += ControllerGlyphs.width(c.text, metrics) + card.cellGap;
				}
				lineTop += lineStep;
				continue;
			}
			if (line.glyph != null)
			{
				GestureGlyphPainter.paint(graphics, line.glyph, x + card.padding, lineTop, metrics.getHeight(),
					GLYPH_WIND_UP_COLOR, GLYPH_FLICK_COLOR);
			}
			ControllerGlyphs.drawText(graphics, line.text, textX, baseline, line.title ? TITLE_COLOR : TEXT_COLOR,
				SHADOW_COLOR, SHADOW_OFFSET);
			lineTop += lineStep;
		}
	}

	private static Row title(String text)
	{
		return new Row(text, null, true);
	}

	private static Row text(String text)
	{
		return new Row(text, null, false);
	}

	/** Points to the full list, on every card. */
	static final String TRICK_BOOK_LINE = "Full trick list: Trick Book in the side panel";
	private static final String LEARN_LINE = "Push and jump once to put this card away";

	/** The card's content for the spec: on foot, controller mode, or keys and mouse. Nine lines at most. */
	static List<Row> rows(Spec spec)
	{
		if (spec.onFoot)
		{
			return onFootRows(spec);
		}
		if (spec.controller)
		{
			return spec.preset.buttonTricks() ? buttonTrickRows(spec) : controllerRows(spec);
		}
		List<Row> rows = new ArrayList<>();
		rows.add(title("Skating controls   H: hide"));
		rows.add(text("W: push   A/D: steer   Shift alone: brake (with A/D: tight turn)   S: crouch"));
		if (spec.mouseTricks)
		{
			rows.add(title("Tricks: hold the " + spec.flickButton + " mouse button, drag down, then flick"));
			rows.add(coreFlicks(spec));
		}
		if (spec.keyboardTricks && spec.mouseTricks)
		{
			// one line for both, so the card still fits a fixed-mode viewport
			rows.add(text("Keys too: Space, 1-4, 5-0 (Alt: nollie, Shift: harder). Again mid-flip: double"));
		}
		else if (spec.keyboardTricks)
		{
			rows.add(title("Trick keys: hold to crouch, let go to pop.  Alt: nollie"));
			rows.add(text("Space: ollie   1: kickflip   2: heelflip   3: shove-it   4: FS shove-it"));
		}
		rows.add(text("In the air: A/D spin   Q/E: grab (rolling too, for style)   Rail: land on it to grind   Hold "
			+ spec.manualKey + ": manual"));
		rows.add(text("R: back on after a bail   " + spec.boardKey + ": step off and carry the board   Esc: stop skating"));
		rows.add(text(TRICK_BOOK_LINE));
		if (!spec.learned)
		{
			rows.add(title(LEARN_LINE));
		}
		return rows;
	}

	/** The four flicks every skater needs first, side by side: ollie, kickflip, heelflip, shove-it. */
	private static Row coreFlicks(Spec spec)
	{
		List<Cell> cells = new ArrayList<>();
		cells.add(cell(spec, Direction.UP, "ollie"));
		cells.add(cell(spec, Direction.UP_LEFT, "kickflip"));
		cells.add(cell(spec, Direction.UP_RIGHT, "heelflip"));
		cells.add(cell(spec, Direction.LEFT, "shove-it"));
		return Row.ofCells(cells);
	}

	/** One picture and its trick; with mirrored flicks the picture is mirrored and the trick stays the same. */
	private static Cell cell(Spec spec, Direction d, String trick)
	{
		Gesture g = new Gesture(d, false, 0f);
		return new Cell(spec.mirror ? KeyboardTricks.mirror(g) : g, trick);
	}

	/**
	 * Controller mode's card, with the preset's buttons drawn in (the universal profile: the left stick the arrows,
	 * the right stick the mouse, each button its own pad key). An action no button does is named by its keyboard
	 * key, which still works, or left out.
	 */
	static List<Row> controllerRows(Spec spec)
	{
		PadPreset p = spec.preset;
		PadContext b = PadContext.BOARD;
		String mod = PadWords.buttons(p, PadAction.HARD_MODIFIER, b, "/");
		String grabs = grabs(p, " / ", "Q / E");
		List<Row> rows = new ArrayList<>();
		rows.add(title("Skating controls   " + PadWords.or(p, PadAction.CONTROLS_CARD, b, "/", "H") + ": hide"));
		List<String> basics = new ArrayList<>();
		basics.add(PadWords.or(p, PadAction.PUSH, b, " or ", "W") + ": push");
		basics.add("{LS} left/right: steer");
		addIf(basics, PadWords.buttons(p, PadAction.BRAKE, b, "/"), ": brake");
		addIf(basics, mod, ": Shift (hard tricks)");
		addIf(basics, PadWords.buttons(p, PadAction.OLLIE, b, "/"), " held, let go: ollie");
		rows.add(text(String.join("    ", basics)));
		if (spec.mouseTricks)
		{
			// with trick keys as well, they share the title so the card still fits a fixed-mode viewport
			rows.add(title("Tricks: {RS} pull down, then flick (no button)." + (mod != null ? "  " + mod
				+ " held: harder" : "") + (spec.keyboardTricks ? ".  Keys too: Space, 1-0" : "")));
			rows.add(coreFlicks(spec));
		}
		else if (spec.keyboardTricks)
		{
			rows.add(text("Trick keys: Space ollie, 1 kickflip, 2 heelflip, 3-0 more (Alt: nollie)"));
		}
		rows.add(text("In the air: {LS} spin   " + grabs + ": grab   Grab" + (mod != null ? " or " + mod : "")
			+ " + {LS} up/down: front/back flip"));
		rows.add(text("Rail: land to grind   {RS} small tilt up, held: manual   Rolling: " + grabs(p, "/", "Q/E")
			+ " grab"));
		rows.add(text(PadWords.or(p, PadAction.RESET, b, "/", "R") + ": get up after a bail   "
			+ PadWords.or(p, PadAction.BOARD_TOGGLE, b, "/", spec.boardKey) + ": step off and carry the board   "
			+ PadWords.or(p, PadAction.STOP, b, "/", "Esc") + ": stop skating"));
		rows.add(text(TRICK_BOOK_LINE));
		if (!spec.learned)
		{
			rows.add(title(LEARN_LINE));
		}
		return rows;
	}

	/**
	 * Controller mode with a button-trick layout (Tony Hawk's American Wasteland): a direction on the left stick or
	 * the d-pad with the flip or grab button, the left stick's manual gesture, and the right stick on the camera.
	 */
	static List<Row> buttonTrickRows(Spec spec)
	{
		PadPreset p = spec.preset;
		String board = spec.boardKey;
		List<Row> rows = new ArrayList<>();
		rows.add(title(PadWords.resolve("Skating controls   {@card}: hide", p, board)));
		rows.add(text(PadWords.resolve("{LS} up: push   {LS} left/right: steer   Hold {@ollie}, let go: ollie   "
			+ "{@modSlash}: hard tricks (alone: brake)", p, board)));
		rows.add(title("Tricks: hold a direction ({LS}, or the d-pad for diagonals) and press"));
		rows.add(text(PadWords.resolve("{@flip}: left kickflip, right heelflip, up impossible, down shove-it. "
			+ "Again mid-flip: double", p, board)));
		rows.add(text(PadWords.resolve("{@grabBtn} held: left melon, right indy, up nosegrab, down tailgrab, none indy",
			p, board)));
		rows.add(text(PadWords.resolve("In the air: {LS} spin ({@modSlash}: faster)   {@grind} near a rail: grind   "
			+ "{LS} up, then down: manual", p, board)));
		rows.add(text(PadWords.resolve("{RS}: camera   {@reset}: get up   {@grind} rolling, no rail: step off   "
			+ "{@stop}: stop", p, board)));
		rows.add(text(TRICK_BOOK_LINE));
		if (!spec.learned)
		{
			rows.add(title(LEARN_LINE));
		}
		return rows;
	}

	/** The grab buttons, left hand then right ("{LT} / {RT}"), or the keys when the pad has neither. */
	private static String grabs(PadPreset p, String joiner, String fallback)
	{
		List<String> both = new ArrayList<>();
		addIf(both, PadWords.buttons(p, PadAction.GRAB_LEFT, PadContext.BOARD, joiner), "");
		addIf(both, PadWords.buttons(p, PadAction.GRAB_RIGHT, PadContext.BOARD, joiner), "");
		return both.isEmpty() ? fallback : String.join(joiner, both);
	}

	private static void addIf(List<String> out, String buttons, String what)
	{
		if (buttons != null)
		{
			out.add(buttons + what);
		}
	}

	/** Off the board: the walking controls (keys, or the pad's buttons drawn in). */
	static List<Row> onFootRows(Spec spec)
	{
		List<Row> rows = new ArrayList<>();
		boolean pad = spec.controller;
		rows.add(title("On foot   " + (pad ? PadWords.or(spec.preset, PadAction.CONTROLS_CARD, PadContext.FOOT, "/",
			"H") : "H") + ": hide"));
		String board = pad ? PadWords.or(spec.preset, PadAction.BOARD_TOGGLE, PadContext.FOOT, "/", spec.boardKey)
			: spec.boardKey;
		if (pad)
		{
			PadPreset p = spec.preset;
			PadContext f = PadContext.FOOT;
			rows.add(text("{LS}: walk (up is away from the camera)    Hold " + PadWords.first(p, PadAction.SPRINT, f,
				"Shift") + ": sprint    " + PadWords.or(p, PadAction.JUMP, f, "/", "Space") + ": jump"));
			rows.add(text(PadWords.or(p, PadAction.DROP_PICKUP, f, " / ", "Q / E")
				+ ": drop the board, or pick it up when close"));
			String camera = PadWords.buttons(p, PadAction.CAMERA_ORBIT, f, "/");
			if (p.buttonTricks())
			{
				rows.add(text("{RS}: turn the camera"));
			}
			else if (camera != null)
			{
				rows.add(text("Hold " + camera + " and move {RS}: turn the camera"));
			}
		}
		else
		{
			rows.add(text("WASD: walk (W is away from the camera)    Hold Shift: sprint    Space: jump"));
			rows.add(text("Q / E: drop the board, or pick it up when close"));
			rows.add(text("Middle mouse drag: turn the camera"));
		}
		rows.add(text(board + ": get on (the board goes under your feet, or step onto it nearby)"));
		rows.add(text("Board far away: " + board + " calls it back to your hands"));
		rows.add(text("Jump onto the board, or sprint and jump carrying it: land rolling"));
		rows.add(text((pad ? PadWords.or(spec.preset, PadAction.STOP, PadContext.FOOT, "/", "Esc") : "Esc")
			+ ": stop skating.  No tricks or points on foot"));
		rows.add(text(TRICK_BOOK_LINE));
		return rows;
	}
}
