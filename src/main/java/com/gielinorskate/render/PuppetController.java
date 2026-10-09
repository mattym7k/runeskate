package com.gielinorskate.render;

import com.gielinorskate.Text;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

/**
* Draws the local player's current (animated) model at the controller's position, with the procedural
* {@link BodyPose} applied to its vertices when {@link BufferProbe} proves this frame that the model is the client's
* per-request animation buffer (so the edit never reaches the cached base model). Otherwise, or after any exception
* (which switches deformation off for this puppet), the plain model is drawn. Client thread only; no allocation per
* frame.
*/
@Slf4j
public final class PuppetController extends RuneLiteObjectController
{
/** The client's model as a mesh (party ghost bodies read it too). */
public static final LayeredBody.Mesh<Model> MESH = new LayeredBody.Mesh<Model>()
{
@Override
public int faces(Model m)
{
return m.getFaceCount();
}

@Override
public int count(Model m)
{
return m.getVerticesCount();
}

@Override
public float[] xs(Model m)
{
return m.getVerticesX();
}

@Override
public float[] ys(Model m)
{
return m.getVerticesY();
}

@Override
public float[] zs(Model m)
{
return m.getVerticesZ();
}

@Override
public void deformed(Model m)
{
m.calculateBoundsCylinder();
}
};

private final BufferProbe.Source<Model> source;
private final BufferProbe.Probe<Model> probe = new BufferProbe.Probe<>();
private final BodyPose pose;
private boolean disabled;
/** Reads the drawn mesh's right hand for the carried board; null after a failure. */
private HandAnchor hand;
/** Told whether each draw had a model (null: none). */
@Setter
private DrawnModelReport drawnReport;

public PuppetController(Client client, BodyPose pose, HandAnchor hand)
{
this.source = () ->
{
Player p = client.getLocalPlayer();
return p == null ? null : p.getModel();
};
this.pose = pose;
this.hand = hand;
}

/** False once deformation failed and was switched off: the skater then cannot flip with the board. */
public boolean canDeform()
{
return !disabled;
}

@Override
public Model getModel()
{
Model m;
if (disabled)
m = source.get();
else
{
try
{
m = BufferProbe.drawable(source, MESH, pose, probe);
}
catch (RuntimeException ex)
{
disabled = true;
log.warn(Text.get("puppet.deformFailed"), ex);
m = source.get();
}
}
// reads (never writes) the drawn mesh's hand; a failure stops the reading: the board then hangs at its fixed spot
if (hand != null && m != null && hand.isActive())
{
try
{
hand.sample(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount());
}
catch (RuntimeException ex)
{
hand = null;
log.warn(Text.get("puppet.handFailed"), ex);
}
}
if (drawnReport != null)
drawnReport.report(m != null);
return m;
}
}
