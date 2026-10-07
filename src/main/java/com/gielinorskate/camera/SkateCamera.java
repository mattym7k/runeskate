package com.gielinorskate.camera;

import com.gielinorskate.physics.SkaterState;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;

/** Applies {@link CameraRig} to the client's free camera. Call only on the client thread. */
public final class SkateCamera
{
	private static final int FREE_CAMERA = 1;
	private static final int DEFAULT_FREE_CAMERA_SPEED = 12;
	/** Only re-run the zoom script when the zoom changes by more than this. */
	private static final int ZOOM_STEP = 8;
	private static final int ZOOM_PER_NOTCH = 40;
	/** Zoom limits (higher is closer), matching the plugin's camera distance setting. */
	private static final int MIN_ZOOM = 200;
	private static final int MAX_ZOOM = 1400;

	private final Client client;
	private final CameraRig rig = new CameraRig();
	private int savedMode;
	/** The game's own zoom (fixed and resizable) and pitch target before skating, restored on exit. */
	private int savedZoomSmall;
	private int savedZoomBig;
	private int savedPitch;
	private boolean active;
	private int baseZoom;
	private int lastZoom;

	public SkateCamera(Client client)
	{
		this.client = client;
	}

	public void enter(float x, float y, float h, float heading, int zoom)
	{
		savedMode = client.getCameraMode();
		savedZoomSmall = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL);
		savedZoomBig = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
		savedPitch = client.getCameraPitchTarget();
		active = true;
		client.setCameraMode(FREE_CAMERA);
		client.setFreeCameraSpeed(0);
		client.setCameraPitchRelaxerEnabled(true);
		baseZoom = clampZoom(zoom);
		lastZoom = Integer.MIN_VALUE;
		rig.reset(x, y, h, heading);
	}

	/** Mouse wheel while skating: positive notches zoom out, like the game's own wheel zoom. */
	public void adjustZoom(int notches)
	{
		baseZoom = clampZoom(baseZoom - notches * ZOOM_PER_NOTCH);
	}

	/** The chase zoom setting changed while skating. */
	public void setBaseZoom(int zoom)
	{
		baseZoom = clampZoom(zoom);
	}

	/** The chase zoom (wheel included), to save back to the config on exit. */
	public int getBaseZoom()
	{
		return baseZoom;
	}

	/** Where the chase camera looks (radians, 0 = north, clockwise): on foot, W walks this way. */
	public float getYaw()
	{
		return rig.getYaw();
	}

	/** Turns the chase camera by hand (radians, clockwise); on foot it then holds there. */
	public void orbit(float radians)
	{
		rig.orbit(radians);
	}

	private static int clampZoom(int zoom)
	{
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
	}

	/**
	 * @param landedAirtime seconds in the air of a landing on this frame, 0 when none
	 * @param pitchJau14 camera pitch (JAU14, 16384 per turn; smaller is flatter)
	 */
	public void update(float x, float y, float h, float cameraHeading, float speed, SkaterState state, float landedAirtime,
		float dt, int pitchJau14)
	{
		if (!active)
		{
			return;
		}
		rig.update(x, y, h, cameraHeading, speed, state, landedAirtime, dt);

		client.setCameraFocalPointX(rig.getFocusX());
		client.setCameraFocalPointZ(rig.getFocusY());
		client.setCameraFocalPointY(-rig.getFocusHeight());
		client.setCameraYawTarget(rig.getYawJau14());
		client.setCameraPitchTarget(pitchJau14);

		int zoom = Math.round(baseZoom * rig.getZoomFactor());
		if (Math.abs(zoom - lastZoom) > ZOOM_STEP)
		{
			client.runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
			lastZoom = zoom;
		}
	}

	/**
	 * Puts the game's camera back as it was: mode, zoom and pitch target. The client has no getter for the free
	 * camera speed, so it gets the client default back.
	 *
	 * @param pitchRelaxer whether the core Camera plugin's "Vertical camera" (relaxCameraPitch) is on
	 */
	public void exit(boolean pitchRelaxer)
	{
		if (!active)
		{
			return;
		}
		active = false;
		client.setCameraPitchRelaxerEnabled(pitchRelaxer);
		client.setFreeCameraSpeed(DEFAULT_FREE_CAMERA_SPEED);
		client.setCameraMode(savedMode);
		client.setCameraPitchTarget(savedPitch);
		if (savedZoomSmall > 0 && savedZoomBig > 0)
		{
			client.runScript(ScriptID.CAMERA_DO_ZOOM, savedZoomSmall, savedZoomBig);
		}
	}
}
