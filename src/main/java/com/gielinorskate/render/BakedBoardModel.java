package com.gielinorskate.render;

import com.gielinorskate.progression.BoardDesign;
import com.gielinorskate.progression.BoardLook;
import com.gielinorskate.progression.DesignPart;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * The baked board (see {@link BakedBoardGeometry}): image textures shown as a colour at every triangle corner, one
 * model per part (grip, deck, wheels, hardware) so each part has the renderers' whole per-model budget. Every part
 * is posed with the same board transform ({@link BoardPlacement#poseInto}) and drawn by its own
 * {@link BoardController} placed exactly like the others, so together they are one board.
 *
 * <p>Each part is built like {@link BoardModel} from merged single-triangle cache models, but welded: before
 * merging, each triangle's corners are moved to integer positions that name the part vertex they stand for, and the
 * client's merge joins corners at equal positions, so the merged model has exactly the part's shared vertices (the
 * lighting is then smooth across smooth surfaces, like any game model). The real positions are written after.
 *
 * <p>Colours: the client lights a model by scaling each corner's lightness by a light intensity
 * (lightness * intensity / 128, kept within 2..126) and keeps one colour per face before lighting. So every face
 * is given the same probe colour (lightness {@link #PROBE_LIGHTNESS}), the model is lit, the intensity at each
 * corner is read back from the lit probe, and the corner is rewritten as its baked colour lit by that intensity.
 * The rewrite is in place on the freshly lit model's colour arrays, allocation free.
 *
 * <p>Designs ({@link #setLook}): the grip, deck and wheels parts take their design's corner colours
 * ({@link DesignColours}) over the same geometry, so changing designs only swaps each part's baked colours and drops
 * its lit model: the next pose lights it again with the new colours. Nothing is merged again.
 */
public final class BakedBoardModel implements BoardPoser
{
	/** A cache model consisting of one triangle (as {@link BoardModel}). */
	private static final int SINGLE_TRIANGLE_MODEL = 823;
	/** Per-model vertex and face limits of the client's renderers (6500 in the GPU and software renderers). */
	static final int MAX_VERTICES = 6500;
	static final int MAX_FACES = 6500;
	/** Probe colour lightness: half of 128, so lit intensities up to 252 read back unclipped. */
	static final int PROBE_LIGHTNESS = 64;
	static final short PROBE = (short) OsrsColor.pack(0, 0, PROBE_LIGHTNESS);
	/**
	 * Lighting: brighter ambient and a softer contrast than the game default (64, 768), so the image keeps most of
	 * its brightness: the grip top lands near 85% and the underside near 70% instead of 60% and 40%.
	 */
	static final int AMBIENT = 100;
	static final int CONTRAST = 1000;
	/**
	 * A pose this close to the one last lit (radians; model units for the pivot) reuses that lit model: 0.003 rad
	 * moves the tail by about 0.1 units, well under a pixel at any zoom.
	 */
	static final float RELIGHT_ANGLE = 0.003f;
	static final float RELIGHT_PIVOT = 0.25f;

	private final Part[] parts;
	/** The shared meshes the parts were made from (the snap's split is kept per these). */
	private final BakedBoardGeometry.Mesh[] meshes;
	private final boolean high;
	private final double brightness;
	private BoardLook look;

	private BakedBoardModel(Part[] parts, BakedBoardGeometry.Mesh[] meshes, boolean high, double brightness,
		BoardLook look)
	{
		this.parts = parts;
		this.meshes = meshes;
		this.high = high;
		this.brightness = brightness;
		this.look = look;
	}

	/** One part's model data and its last lit model. */
	private static final class Part
	{
		final ModelData data;
		final BakedBoardGeometry.Mesh mesh;
		final float[] baseVertices;
		final float[] posed;
		/** Each corner's baked colour (the part's design), packed HSL; shared, never modified. */
		short[] cornerHsl;
		Model lit;
		float roll;
		float pitch;
		float flip;
		float pivotY;

		Part(ModelData data, BakedBoardGeometry.Mesh mesh, short[] cornerHsl)
		{
			this.data = data;
			this.mesh = mesh;
			this.baseVertices = mesh.vertices;
			this.posed = new float[baseVertices.length];
			this.cornerHsl = cornerHsl;
		}
	}

	/**
	 * The baked board in high or low detail (low for the "Normal" board detail setting and for party ghosts), or
	 * null if it isn't bundled or a cache model can't be loaded or merged. Client thread. The first call per client
	 * and detail merges the parts' templates (thousands of cache models: a noticeable hitch once).
	 */
	public static BakedBoardModel create(Client client, double brightness, boolean high)
	{
		return create(client, brightness, high, null);
	}

	/**
	 * As {@link #create(Client, double, boolean)}, in {@code look}'s designs (null: the geometry's own colours, the
	 * default designs). Each call makes its own model data (a party ghost's board poses on its own); the merged
	 * templates are shared.
	 */
	public static BakedBoardModel create(Client client, double brightness, boolean high, BoardLook look)
	{
		BakedBoardGeometry.Mesh[] meshes = BakedBoardGeometry.sharedBoard(high);
		if (meshes == null || !fitsRenderers(meshes))
		{
			return null;
		}
		Part[] parts = new Part[meshes.length];
		for (int i = 0; i < meshes.length; i++)
		{
			BakedBoardGeometry.Mesh mesh = meshes[i];
			ModelData template = template(client, mesh);
			if (template == null)
			{
				return null;
			}
			ModelData data = template.shallowCopy().cloneVertices().cloneColors();
			short[] colors = data.getFaceColors();
			for (int f = 0; f < mesh.faceCount(); f++)
			{
				colors[f] = PROBE;
			}
			parts[i] = new Part(data, mesh, partHsl(mesh, look, high, brightness));
			writeVertices(parts[i].data, mesh.vertices);
		}
		return new BakedBoardModel(parts, meshes, high, brightness, look);
	}

	/**
	 * Draws the board in {@code look}'s designs: each design part whose colours change gets them and is lit again
	 * at its next pose (its controller must be told to draw again). Nothing else is touched. Client thread.
	 *
	 * @return true if any part's colours changed
	 */
	public boolean setLook(BoardLook look)
	{
		if (look == null || look.equals(this.look))
		{
			return false;
		}
		this.look = look;
		boolean changed = false;
		for (Part p : parts)
		{
			short[] hsl = partHsl(p.mesh, look, high, brightness);
			if (hsl != p.cornerHsl)
			{
				p.cornerHsl = hsl;
				// the lit model carries the old colours: the next pose lights it again with these
				p.lit = null;
				changed = true;
			}
		}
		return changed;
	}

	/** The designs drawn (null: the geometry's own colours). */
	public BoardLook look()
	{
		return look;
	}

	/** The design {@code look} puts on a mesh's part, or null for a part without designs (hardware). */
	static BoardDesign designFor(BakedBoardGeometry.Mesh mesh, BoardLook look)
	{
		if (look == null)
		{
			return null;
		}
		for (DesignPart p : DesignPart.values())
		{
			if (p.index == mesh.part)
			{
				return look.get(p);
			}
		}
		return null;
	}

	/** Design colours as HSL by "ID.detail@brightness". */
	private static final Map<String, short[]> HSL_CACHE = new HashMap<>();
	/** The geometry's own colours as HSL, per mesh: {brightness, hsl}. */
	private static final Map<BakedBoardGeometry.Mesh, Object[]> OWN_HSL = new IdentityHashMap<>();

	/**
	 * A part's corner colours in {@code look}'s design as packed HSL: the design's colour file, or the geometry's
	 * own colours for hardware, a null look or a missing file. Converted once per design, detail and brightness
	 * and shared (never modify it), so switching back and forth costs nothing.
	 */
	static short[] partHsl(BakedBoardGeometry.Mesh mesh, BoardLook look, boolean high, double brightness)
	{
		BoardDesign d = designFor(mesh, look);
		int[] rgb = d == null ? null : DesignColours.colours(d.id, high, mesh);
		synchronized (HSL_CACHE)
		{
			if (rgb == null)
			{
				Object[] own = OWN_HSL.get(mesh);
				if (own == null || (Double) own[0] != brightness)
				{
					own = new Object[]{brightness, BakedBoardGeometry.cornerHsl(mesh, brightness)};
					OWN_HSL.put(mesh, own);
				}
				return (short[]) own[1];
			}
			// an edited custom design has a new revision (and new colours under the same id)
			String key = d.id + "#" + d.revision + (high ? ".high@" : ".low@") + brightness;
			short[] hsl = HSL_CACHE.get(key);
			if (hsl == null)
			{
				hsl = BakedBoardGeometry.cornerHsl(rgb, brightness);
				HSL_CACHE.put(key, hsl);
			}
			return hsl;
		}
	}

	/** Whether converted colours of design {@code id} are kept (tests). */
	static boolean hslHeld(String id)
	{
		String prefix = id + "#";
		synchronized (HSL_CACHE)
		{
			return HSL_CACHE.keySet().stream().anyMatch(k -> k.startsWith(prefix));
		}
	}

	/**
	 * Forgets design {@code id}'s converted colours, every revision (a party member's design that was let go, a
	 * player's design deleted, edited or renamed).
	 */
	public static void forgetDesign(String id)
	{
		String prefix = id + "#";
		synchronized (HSL_CACHE)
		{
			HSL_CACHE.keySet().removeIf(k -> k.startsWith(prefix));
		}
	}

	/** True if every part fits one model of the client's renderers. */
	static boolean fitsRenderers(BakedBoardGeometry.Mesh[] meshes)
	{
		for (BakedBoardGeometry.Mesh m : meshes)
		{
			if (m.vertexCount() > MAX_VERTICES || m.faceCount() > MAX_FACES)
			{
				return false;
			}
		}
		return meshes.length > 0;
	}

	private static Client templateClient;
	private static final Map<BakedBoardGeometry.Mesh, ModelData> TEMPLATES = new IdentityHashMap<>();

	private static ModelData template(Client client, BakedBoardGeometry.Mesh mesh)
	{
		if (templateClient != client)
		{
			TEMPLATES.clear();
			templateClient = client;
		}
		ModelData t = TEMPLATES.get(mesh);
		if (t == null)
		{
			t = mergeTemplate(client, mesh);
			if (t == null)
			{
				return null;
			}
			TEMPLATES.put(mesh, t);
		}
		return t;
	}

	/** One model with the part's faces and exactly its (shared) vertices. Null if the merge didn't weld as planned. */
	private static ModelData mergeTemplate(Client client, BakedBoardGeometry.Mesh mesh)
	{
		int faces = mesh.faceCount();
		int[] f = mesh.faces;
		ModelData[] parts = new ModelData[faces];
		for (int i = 0; i < faces; i++)
		{
			ModelData p = client.loadModelData(SINGLE_TRIANGLE_MODEL);
			if (p == null || p.getVerticesCount() != 3 || p.getFaceCount() != 1)
			{
				return null;
			}
			p = p.cloneColors().cloneVertices();
			float[] x = p.getVerticesX();
			float[] y = p.getVerticesY();
			float[] z = p.getVerticesZ();
			int[] corner = {p.getFaceIndices1()[0], p.getFaceIndices2()[0], p.getFaceIndices3()[0]};
			for (int k = 0; k < 3; k++)
			{
				// position = the part vertex's key; the merge welds equal (integer) positions
				int v = f[i * 3 + k];
				x[corner[k]] = weldKeyX(v);
				y[corner[k]] = weldKeyY(v);
				z[corner[k]] = 0;
			}
			parts[i] = p;
		}
		ModelData merged = client.mergeModels(parts);
		if (merged == null || merged.getFaceCount() != faces || merged.getVerticesCount() != mesh.vertexCount())
		{
			return null;
		}
		merged.cloneVertices().cloneColors();

		int[] f1 = merged.getFaceIndices1();
		int[] f2 = merged.getFaceIndices2();
		int[] f3 = merged.getFaceIndices3();
		for (int i = 0; i < faces; i++)
		{
			f1[i] = f[i * 3];
			f2[i] = f[i * 3 + 1];
			f3[i] = f[i * 3 + 2];
		}
		return merged;
	}

	/** Distinct small integer positions per vertex (x, y within +-128 so they stay well inside model bounds). */
	static int weldKeyX(int v)
	{
		return v % 256 - 128;
	}

	static int weldKeyY(int v)
	{
		return v / 256 - 128;
	}

	@Override
	public int partCount()
	{
		return parts.length;
	}

	@Override
	public Model pose(float roll, float pitch, float flip, float pivotY)
	{
		return pose(0, roll, pitch, flip, pivotY);
	}

	@Override
	public Model pose(int part, float roll, float pitch, float flip, float pivotY)
	{
		if (part < 0 || part >= parts.length)
		{
			return null;
		}
		Part p = parts[part];
		if (p.lit != null && !needsRelight(p.roll, p.pitch, p.flip, p.pivotY, roll, pitch, flip, pivotY))
		{
			return p.lit;
		}
		writeVertices(p.data, BoardPlacement.poseInto(p.baseVertices, roll, pitch, flip, pivotY, p.posed));
		Model m = p.data.light(AMBIENT, CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
		if (m != null)
		{
			shade(m.getFaceColors1(), m.getFaceColors2(), m.getFaceColors3(), p.cornerHsl);
			p.lit = m;
			p.roll = roll;
			p.pitch = pitch;
			p.flip = flip;
			p.pivotY = pivotY;
		}
		return m;
	}

	@Override
	public HalfBoard half(boolean nose)
	{
		BoardSplit.Half h = BoardSplit.of(meshes).half(nose);
		ModelData[] copies = new ModelData[parts.length];
		short[][] hsl = new short[parts.length][];
		for (int i = 0; i < parts.length; i++)
		{
			// its own vertices and colours (the probe colour on every face); the faces are the shared template's
			copies[i] = parts[i].data.shallowCopy().cloneVertices().cloneColors();
			// the designs as they are now: shared, never modified
			hsl[i] = parts[i].cornerHsl;
		}
		return new HalfBoard(copies, h, hsl, AMBIENT, CONTRAST);
	}

	/** True when a pose moved far enough from the one last lit ({@code lit*}) to light the model again. */
	static boolean needsRelight(float litRoll, float litPitch, float litFlip, float litPivotY,
		float roll, float pitch, float flip, float pivotY)
	{
		return Math.abs(roll - litRoll) > RELIGHT_ANGLE || Math.abs(pitch - litPitch) > RELIGHT_ANGLE
			|| Math.abs(flip - litFlip) > RELIGHT_ANGLE || Math.abs(pivotY - litPivotY) > RELIGHT_PIVOT;
	}

	/**
	 * Rewrites lit probe colours as the baked corner colours lit the same: each corner's intensity is read back
	 * from its lit probe (lightness = 64 * intensity / 128, so intensity is about 2 * lightness + 1). Faces the
	 * client drew flat (colour 3 is -1) get their one intensity at all three corners; hidden faces (-2) are left.
	 */
	static void shade(int[] c1, int[] c2, int[] c3, short[] cornerHsl)
	{
		int faces = Math.min(cornerHsl.length / 3, Math.min(c1.length, Math.min(c2.length, c3.length)));
		for (int i = 0; i < faces; i++)
		{
			int third = c3[i];
			if (third == -2)
			{
				continue;
			}
			int i1 = intensity(c1[i]);
			int i2 = third == -1 ? i1 : intensity(c2[i]);
			int i3 = third == -1 ? i1 : intensity(third);
			c1[i] = OsrsColor.light(cornerHsl[i * 3] & 0xffff, i1);
			c2[i] = OsrsColor.light(cornerHsl[i * 3 + 1] & 0xffff, i2);
			c3[i] = OsrsColor.light(cornerHsl[i * 3 + 2] & 0xffff, i3);
		}
	}

	/** The light intensity (128 = unchanged) that lit the probe colour to {@code litProbe}. */
	static int intensity(int litProbe)
	{
		return (litProbe & 127) * 128 / PROBE_LIGHTNESS + 1;
	}

	private static void writeVertices(ModelData data, float[] v)
	{
		float[] vx = data.getVerticesX();
		float[] vy = data.getVerticesY();
		float[] vz = data.getVerticesZ();
		int n = Math.min(v.length / 3, data.getVerticesCount());
		for (int i = 0; i < n; i++)
		{
			vx[i] = v[i * 3];
			vy[i] = v[i * 3 + 1];
			vz[i] = v[i * 3 + 2];
		}
	}
}
