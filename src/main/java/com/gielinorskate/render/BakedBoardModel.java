package com.gielinorskate.render;

import com.gielinorskate.progression.*;
import java.util.*;
import lombok.AllArgsConstructor;
import net.runelite.api.*;

/**
* The baked board (see {@link BakedBoardGeometry}): image textures shown as a colour at every triangle corner, one
* model per part (grip, deck, wheels, hardware) so each part has the renderers' whole per-model budget. Every part
* is posed with the same board transform ({@link BoardPlacement#poseInto}) and drawn by its own
* {@link BoardController} placed exactly like the others, so together they are one board.
*
* <p>Each part is built from merged single-triangle cache models, welded: before
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
*
* <p>A board snapped in two by a duel tantrum ({@link #half}) is two more of these, one per half
* ({@link BoardSplit}): copies of the board's model data (the merged templates are shared) turned about the half's
* own centre, with the other half's faces hidden after lighting, in the designs the board had when it snapped.
*/
@AllArgsConstructor
public final class BakedBoardModel
{
/** Per-model vertex and face limits of the client's renderers (6500 in the GPU and software renderers). */
static final int MAX_VERTICES = 6500;
/** Probe colour lightness: half of 128, so lit intensities up to 252 read back unclipped. */
static final int PROBE_LIGHTNESS = 64;
static final short PROBE = (short) OsrsColor.pack(0, 0, PROBE_LIGHTNESS);
/**
* Lighting: brighter ambient (100) and a softer contrast (1000) than the game default (64, 768), so the image
* keeps most of its brightness: the grip top lands near 85% and the underside near 70% instead of 60% and 40%.
*/
private static final int AMBIENT = 100;
private static final int CONTRAST = 1000;

private final Part[] parts;
/** The shared meshes the parts were made from (the snap's split is kept per these). */
private final BakedBoardGeometry.Mesh[] meshes;
private final boolean high;
private final double brightness;
private BoardLook look;
/** The snapped half this draws, or null for the whole board. */
private final BoardSplit.Half half;

/** One part's model data and its last lit model. */
private static final class Part
{
final ModelData data;
final BakedBoardGeometry.Mesh mesh;
final float[] base;
final float[] posed;
/** Per face: drawn (a half's own faces); null for every face. */
final boolean[] in;
/** Each corner's baked colour (the part's design), packed HSL; shared, never modified. */
short[] cornerHsl;
Model lit;
float roll;
float pitch;
float flip;
float pivotY;

Part(ModelData data, BakedBoardGeometry.Mesh mesh, float[] base, short[] cornerHsl, boolean[] in)
{
this.data = data;
this.mesh = mesh;
this.base = base;
this.posed = new float[base.length];
this.cornerHsl = cornerHsl;
this.in = in;
}
}

/**
* The baked board in high or low detail (low for the "Normal" board detail setting and for party ghosts), or
* null if it isn't bundled or a cache model can't be loaded or merged. Client thread. The first call per client
* and detail merges the parts' templates (thousands of cache models: a noticeable hitch once). In {@code look}'s
* designs (null: the geometry's own colours, the default designs). Each call makes its own model data (a party
* ghost's board poses on its own); the merged templates are shared.
*/
public static BakedBoardModel create(Client client, boolean high, BoardLook look)
{
double brightness = client.getTextureProvider() != null ? client.getTextureProvider().getBrightness()
: OsrsColor.DEFAULT_BRIGHTNESS;
BakedBoardGeometry.Mesh[] meshes = BakedBoardGeometry.sharedBoard(high);
if (meshes == null || !fitsRenderers(meshes))
return null;
if (templateClient != client)
{
TEMPLATES.clear();
templateClient = client;
}
Part[] parts = new Part[meshes.length];
for (int i = 0; i < meshes.length; i++)
{
BakedBoardGeometry.Mesh mesh = meshes[i];
ModelData template = TEMPLATES.computeIfAbsent(mesh, m -> mergeTemplate(client, m));
if (template == null)
return null;
ModelData data = template.shallowCopy().cloneVertices().cloneColors();
Arrays.fill(data.getFaceColors(), 0, mesh.faceCount(), PROBE);
parts[i] = new Part(data, mesh, mesh.vertices, partHsl(mesh, look, high, brightness), null);
writeVertices(data, mesh.vertices);
}
return new BakedBoardModel(parts, meshes, high, brightness, look, null);
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
return false;
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
for (DesignPart p : DesignPart.values())
{
if (look != null && p.index == mesh.part)
return look.get(p);
}
return null;
}

/** Design colours as HSL by "ID#revision.detail@brightness". */
static final Map<String, short[]> HSL_CACHE = new HashMap<>();
/** The geometry's own colours as HSL, by (mesh, brightness). */
private static final Map<List<Object>, short[]> OWN_HSL = new HashMap<>();

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
// an edited custom design has a new revision (and new colours under the same id)
return rgb == null
? OWN_HSL.computeIfAbsent(List.of(mesh, brightness), k -> BakedBoardGeometry.cornerHsl(mesh.cornerRgb, brightness))
: HSL_CACHE.computeIfAbsent(d.id + "#" + d.revision + (high ? ".high@" : ".low@") + brightness,
k -> BakedBoardGeometry.cornerHsl(rgb, brightness));
}
}

/**
* Forgets design {@code id}'s converted colours, every revision (a party member's design that was let go, a
* player's design deleted, edited or renamed).
*/
public static void forgetDesign(String id)
{
synchronized (HSL_CACHE)
{
HSL_CACHE.keySet().removeIf(k -> k.startsWith(id + "#"));
}
}

/** True if every part fits one model of the client's renderers. */
static boolean fitsRenderers(BakedBoardGeometry.Mesh[] meshes)
{
return meshes.length > 0
&& Arrays.stream(meshes).allMatch(m -> m.vertexCount() <= MAX_VERTICES && m.faceCount() <= MAX_VERTICES);
}

private static Client templateClient;
private static final Map<BakedBoardGeometry.Mesh, ModelData> TEMPLATES = new IdentityHashMap<>();

/** One model with the part's faces and exactly its (shared) vertices. Null if the merge didn't weld as planned. */
private static ModelData mergeTemplate(Client client, BakedBoardGeometry.Mesh mesh)
{
int faces = mesh.faceCount();
int[] f = mesh.faces;
ModelData[] parts = new ModelData[faces];
for (int i = 0; i < faces; i++)
{
// a cache model consisting of one triangle
ModelData p = client.loadModelData(823);
if (p == null || p.getVerticesCount() != 3 || p.getFaceCount() != 1)
return null;
p = p.cloneColors().cloneVertices();
int[] corner = {p.getFaceIndices1()[0], p.getFaceIndices2()[0], p.getFaceIndices3()[0]};
for (int k = 0; k < 3; k++)
{
// position = the part vertex's key; the merge welds equal (integer) positions
int v = f[i * 3 + k];
p.getVerticesX()[corner[k]] = weldKeyX(v);
p.getVerticesY()[corner[k]] = weldKeyY(v);
p.getVerticesZ()[corner[k]] = 0;
}
parts[i] = p;
}
ModelData merged = client.mergeModels(parts);
if (merged == null || merged.getFaceCount() != faces || merged.getVerticesCount() != mesh.vertexCount())
return null;
merged.cloneVertices().cloneColors();
for (int i = 0; i < faces; i++)
{
merged.getFaceIndices1()[i] = f[i * 3];
merged.getFaceIndices2()[i] = f[i * 3 + 1];
merged.getFaceIndices3()[i] = f[i * 3 + 2];
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

/** How many models (parts) the board is drawn as. */
public int partCount()
{
return parts.length;
}

/** Units from a half's centre (its model origin) down to its lowest point. */
public float bottom()
{
return half.bottom();
}

/**
* Part {@code part} (0 .. {@link #partCount()} - 1) of the board, lit, rotated by roll (around its long axis) and
* pitch (about the contact truck), then turned by {@code flip} about the skater's centre of mass at
* {@code pivotY}; a half rolls and pitches about its own centre (it flies on its own: no flip). Client thread.
*/
public Model pose(int part, float roll, float pitch, float flip, float pivotY)
{
Part p = parts[part];
if (p.lit != null && !needsRelight(p.roll, p.pitch, p.flip, p.pivotY, roll, pitch, flip, pivotY))
return p.lit;
writeVertices(p.data, half != null ? BoardGeometry.rotateInto(p.base, roll, pitch, p.posed)
: BoardPlacement.poseInto(p.base, roll, pitch, flip, pivotY, p.posed));
Model m = p.data.light(AMBIENT, CONTRAST, ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
if (m != null)
{
shade(m.getFaceColors1(), m.getFaceColors2(), m.getFaceColors3(), p.cornerHsl, p.in);
p.lit = m;
p.roll = roll;
p.pitch = pitch;
p.flip = flip;
p.pivotY = pivotY;
}
return m;
}

/**
* One half of this board snapped across the middle of the deck ({@link BoardSplit}): the nose half or the tail
* half, in this board's colours (and designs) now, posed on its own. Client thread.
*/
public BakedBoardModel half(boolean nose)
{
BoardSplit.Half h = BoardSplit.of(meshes).half(nose);
Part[] copies = new Part[parts.length];
for (int i = 0; i < parts.length; i++)
// its own vertices and colours (the probe colour on every face); the faces are the shared template's
copies[i] = new Part(parts[i].data.shallowCopy().cloneVertices().cloneColors(), parts[i].mesh,
h.vertices[i], parts[i].cornerHsl, h.faceIn[i]);
return new BakedBoardModel(copies, meshes, high, brightness, look, h);
}

/**
* True when a pose moved far enough from the one last lit ({@code lit*}) to light the model again: 0.003 rad
* moves the tail by about 0.1 units, well under a pixel at any zoom (0.25 units for the pivot).
*/
static boolean needsRelight(float litRoll, float litPitch, float litFlip, float litPivotY,
float roll, float pitch, float flip, float pivotY)
{
return Math.abs(roll - litRoll) > 0.003f || Math.abs(pitch - litPitch) > 0.003f
|| Math.abs(flip - litFlip) > 0.003f || Math.abs(pivotY - litPivotY) > 0.25f;
}

/**
* Rewrites lit probe colours as the baked corner colours lit the same: each corner's intensity is read back
* from its lit probe (lightness = 64 * intensity / 128, so intensity is about 2 * lightness + 1). Faces the
* client drew flat (colour 3 is -1) get their one intensity at all three corners; hidden faces (-2) are left.
* With {@code in}, a face not in it is hidden (colour 3 = -2, which the client's renderers skip).
*/
static void shade(int[] c1, int[] c2, int[] c3, short[] cornerHsl, boolean[] in)
{
int faces = Math.min(cornerHsl.length / 3, c3.length);
for (int i = 0; i < c3.length; i++)
{
int third = c3[i];
if (in != null && !(i < in.length && in[i]))
c3[i] = -2;
else if (i < faces && third != -2)
{
int i1 = intensity(c1[i]);
c1[i] = OsrsColor.light(cornerHsl[i * 3] & 0xffff, i1);
c2[i] = OsrsColor.light(cornerHsl[i * 3 + 1] & 0xffff, third == -1 ? i1 : intensity(c2[i]));
c3[i] = OsrsColor.light(cornerHsl[i * 3 + 2] & 0xffff, third == -1 ? i1 : intensity(third));
}
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
for (int i = 0; i < v.length / 3; i++)
{
vx[i] = v[i * 3];
vy[i] = v[i * 3 + 1];
vz[i] = v[i * 3 + 2];
}
}
}
