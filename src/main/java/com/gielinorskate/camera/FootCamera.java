package com.gielinorskate.camera;

import com.gielinorskate.physics.Angles;

/**
* Which way the chase camera turns while on foot. Walking is camera-relative, so a camera that always swung
* behind the body would spin round when walking toward it (S) and circle on a sidestep (A / D). It follows the
* body only while the walk goes roughly away from it (within {@link #FOLLOW_CONE}: W and its diagonals, which
* then steer like a turn); otherwise it holds still. Pure.
*/
public final class FootCamera
{
/** The walk turns the camera only within this angle of where it looks (radians). */
public static final float FOLLOW_CONE = (float) Math.toRadians(60);

private FootCamera()
{
}

/**
* The heading the camera should sit behind this frame.
*
* @param yaw where the camera looks now
* @param walkHeading the walker's body heading
* @param moving the walker is being moved (a direction key held)
*/
public static float targetHeading(float yaw, float walkHeading, boolean moving)
{
return moving && Angles.absDiff(walkHeading, yaw) <= FOLLOW_CONE + 1e-4f ? walkHeading : yaw;
}
}
