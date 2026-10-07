package com.gielinorskate.render;

import com.gielinorskate.progression.Deck;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

/**
 * Builds a runtime mesh by merging single-triangle cache models and overwriting their geometry
 * (the approach used by the BSD-2 Creator's Kit plugin, reimplemented here).
 */
public final class BoardModel implements BoardPoser
{
	/** A cache model consisting of one triangle. */
	private static final int SINGLE_TRIANGLE_MODEL = 823;

	private final ModelData data;
	/** The shared mesh this board was made from (the snap's split is kept per it). */
	private final BoardGeometry.Mesh mesh;
	private final float[] baseTris;
	/** Each triangle's colour group (grip, deck, wheels, graphic, metal: the deck palette's indices). */
	private final int[] colorIndex;
	/** Reused pose output, so posing allocates nothing but the lit model. */
	private final float[] posed;

	private BoardModel(ModelData data, BoardGeometry.Mesh mesh, float[] baseTris, int[] colorIndex)
	{
		this.data = data;
		this.mesh = mesh;
		this.baseTris = baseTris;
		this.colorIndex = colorIndex;
		this.posed = new float[baseTris.length];
	}

	/** The classic board. Must be called on the client thread. Returns null if the cache model can't be loaded. */
	public static BoardModel create(Client client)
	{
		return create(client, Deck.DEFAULT.palette());
	}

	/**
	 * A board in a deck's colours ({@link Deck#palette()}). Must be called on the client thread. Returns null if the
	 * cache model can't be loaded. Each board is its own instance (its own vertices and colours, so ghosts never
	 * draw each other's poses), copied from a merged template built once per client.
	 */
	public static BoardModel create(Client client, short[] palette)
	{
		BoardGeometry.Mesh mesh = BoardGeometry.sharedDefaultBoard();
		ModelData template = template(client, mesh);
		if (template == null)
		{
			return null;
		}
		// shares the template's (fixed) faces; vertices and colours are this board's own
		ModelData data = template.shallowCopy().cloneVertices().cloneColors();
		BoardModel board = new BoardModel(data, mesh, mesh.tris, mesh.colorIndex);
		board.setPalette(palette);
		board.writeVertices(mesh.tris);
		return board;
	}

	/**
	 * Builds the shared template now (client thread) so the first skate entry or party ghost doesn't pay for
	 * merging the cache models. Does nothing if it is already built or can't be.
	 */
	public static void warm(Client client)
	{
		template(client, BoardGeometry.sharedDefaultBoard());
	}

	/** The client the cached template was merged by, and the template itself (client thread only). */
	private static Client templateClient;
	private static ModelData template;

	private static ModelData template(Client client, BoardGeometry.Mesh mesh)
	{
		if (template == null || templateClient != client)
		{
			ModelData t = mergeTemplate(client, mesh.faceCount());
			if (t == null)
			{
				return null;
			}
			template = t;
			templateClient = client;
		}
		return template;
	}

	/**
	 * One model of {@code faces} unwelded triangles (face i uses vertices 3i..3i+2). Never lit or posed: boards
	 * are shallow copies of it. Returns null if the cache model can't be loaded.
	 */
	private static ModelData mergeTemplate(Client client, int faces)
	{
		ModelData[] parts = new ModelData[faces];
		for (int i = 0; i < faces; i++)
		{
			ModelData p = client.loadModelData(SINGLE_TRIANGLE_MODEL);
			if (p == null)
			{
				return null;
			}
			// unique offsets stop the merge from welding vertices together
			parts[i] = p.cloneColors().cloneVertices().translate(i + 1, i + 1, i + 1);
		}
		ModelData merged = client.mergeModels(parts);
		if (merged == null || merged.getFaceCount() < faces || merged.getVerticesCount() < faces * 3)
		{
			return null;
		}
		merged.cloneVertices().cloneColors();

		int[] f1 = merged.getFaceIndices1();
		int[] f2 = merged.getFaceIndices2();
		int[] f3 = merged.getFaceIndices3();
		for (int i = 0; i < faces; i++)
		{
			f1[i] = i * 3;
			f2[i] = i * 3 + 1;
			f3[i] = i * 3 + 2;
		}
		return merged;
	}

	/** Recolours the board (a deck change); the next {@link #pose} lights the new colours. Client thread. */
	public void setPalette(short[] palette)
	{
		short[] colors = data.getFaceColors();
		short[] faces = Deck.faceColors(colorIndex, palette);
		System.arraycopy(faces, 0, colors, 0, Math.min(faces.length, colors.length));
	}

	/** Returns a lit model of the board rotated by roll (around its long axis) and pitch (about the contact truck). */
	public Model pose(float roll, float pitch)
	{
		return pose(roll, pitch, 0f, 0f);
	}

	/** As {@link #pose(float, float)}, then turned by {@code flip} about the skater's centre of mass at {@code pivotY}. */
	@Override
	public Model pose(float roll, float pitch, float flip, float pivotY)
	{
		writeVertices(BoardPlacement.poseInto(baseTris, roll, pitch, flip, pivotY, posed));
		return data.light(ModelData.DEFAULT_AMBIENT, ModelData.DEFAULT_CONTRAST,
			ModelData.DEFAULT_X, ModelData.DEFAULT_Y, ModelData.DEFAULT_Z);
	}

	@Override
	public HalfBoard half(boolean nose)
	{
		// its own vertices and colours (the deck's, as they are now); the faces are the shared template's
		ModelData copy = data.shallowCopy().cloneVertices().cloneColors();
		return new HalfBoard(new ModelData[]{copy}, BoardSplit.of(mesh).half(nose), new short[1][],
			ModelData.DEFAULT_AMBIENT, ModelData.DEFAULT_CONTRAST);
	}

	private void writeVertices(float[] tris)
	{
		float[] vx = data.getVerticesX();
		float[] vy = data.getVerticesY();
		float[] vz = data.getVerticesZ();
		int n = tris.length / 3;
		for (int i = 0; i < n; i++)
		{
			vx[i] = tris[i * 3];
			vy[i] = tris[i * 3 + 1];
			vz[i] = tris[i * 3 + 2];
		}
		// park leftover merged vertices at the origin so stray faces are degenerate
		for (int i = n; i < data.getVerticesCount(); i++)
		{
			vx[i] = 0;
			vy[i] = 0;
			vz[i] = 0;
		}
	}
}
