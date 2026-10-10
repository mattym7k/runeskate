package com.gielinorskate.camera;

import com.gielinorskate.physics.SkaterState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;

/** Applies {@link CameraRig} to the client's free camera. Call only on the client thread. */
@RequiredArgsConstructor
public final class SkateCamera
{
	private final Client client;
	private final CameraRig rig = new CameraRig();
	private int savedMode;
	/** The game's own zoom (fixed and resizable) and pitch target before skating, restored on exit. */
	private int savedZoomSmall, savedZoomBig, savedPitch;
	private boolean active;
	/** The chase zoom (wheel included), to save back to the config on exit. */
	@Getter
	private int baseZoom;
	private int lastZoom;

	public void enter(float x, float y, float h, float heading, int zoom)
	{
		savedMode = client.getCameraMode();
		savedZoomSmall = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL);
		savedZoomBig = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
		savedPitch = client.getCameraPitchTarget();
		active = true;
		client.setCameraMode(1); // the free camera
		client.setFreeCameraSpeed(0);
		client.setCameraPitchRelaxerEnabled(true);
		setBaseZoom(zoom);
		lastZoom = Integer.MIN_VALUE;
		rig.reset(x, y, h, heading);
	}

	/** Mouse wheel while skating: positive notches zoom out (40 a notch), like the game's own wheel zoom. */
	public void adjustZoom(int notches)
	{
		setBaseZoom(baseZoom - notches * 40);
	}

	/** The chase zoom setting changed while skating; kept within 200-1400 (higher is closer), as the setting is. */
	public void setBaseZoom(int zoom)
	{
		baseZoom = Math.max(200, Math.min(1400, zoom));
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

	/**
	 * @param landedAirtime seconds in the air of a landing on this frame, 0 when none
	 * @param pitchJau14 camera pitch (JAU14, 16384 per turn; smaller is flatter)
	 */
	public void update(float x, float y, float h, float cameraHeading, float speed, SkaterState state, float landedAirtime,
		float dt, int pitchJau14)
	{
		if (!active)
			return;
		rig.update(x, y, h, cameraHeading, speed, state, landedAirtime, dt);

		client.setCameraFocalPointX(rig.getFocusX());
		client.setCameraFocalPointZ(rig.getFocusY());
		client.setCameraFocalPointY(-rig.getFocusHeight());
		client.setCameraYawTarget(CameraRig.yawJau14(rig.getYaw()));
		client.setCameraPitchTarget(pitchJau14);

		int zoom = Math.round(baseZoom * rig.getZoomFactor());
		// only re-run the zoom script when the zoom changes by more than 8
		if (Math.abs(zoom - lastZoom) > 8)
		{
			client.runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
			lastZoom = zoom;
		}
	}

	/**
	 * Puts the game's camera back as it was: mode, zoom and pitch target. The client has no getter for the free
	 * camera speed, so it gets the client default (12) back.
	 *
	 * @param pitchRelaxer whether the core Camera plugin's "Vertical camera" (relaxCameraPitch) is on
	 */
	public void exit(boolean pitchRelaxer)
	{
		if (!active)
			return;
		active = false;
		client.setCameraPitchRelaxerEnabled(pitchRelaxer);
		client.setFreeCameraSpeed(12);
		client.setCameraMode(savedMode);
		client.setCameraPitchTarget(savedPitch);
		if (savedZoomSmall > 0 && savedZoomBig > 0)
			client.runScript(ScriptID.CAMERA_DO_ZOOM, savedZoomSmall, savedZoomBig);
	}
}
