package com.techmonkey.leadlines.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.leash.LeashKnotModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import com.techmonkey.leadlines.LeadAnchorEntity;

/** Draws an anchor as a vanilla lead knot, and its lead as a hanging curve. */
public class LeadAnchorRenderer extends EntityRenderer<LeadAnchorEntity, EntityRenderState> {
    private static final Identifier KNOT_LOCATION = Identifier.withDefaultNamespace("textures/entity/lead_knot/lead_knot.png");
    private final LeashKnotModel model;

    public LeadAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new LeashKnotModel(context.bakeLayer(ModelLayers.LEASH_KNOT));
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        submitNodeCollector.submitModel(model, state, poseStack, KNOT_LOCATION, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    /**
     * Vanilla lights a lead by the light at each end's eye position, which for a knot sitting
     * on a block face is inside the block (pitch black). Use the light in front of the face.
     */
    /** Segments a lead is drawn in, so it can hang in a curve. */
    private static final int SEGMENTS = 12;

    /**
     * Vanilla draws a lead between two knots at the same height as a straight line, and
     * lights it by the inside of the block a knot sits on (pitch black). This redraws the
     * lead as a hanging curve, sagging by this knot's slack setting, lit by the air in front
     * of each knot.
     */
    @Override
    public void extractRenderState(LeadAnchorEntity entity, EntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state.leashStates == null || state.leashStates.isEmpty()) {
            return;
        }
        EntityRenderState.LeashState vanilla = state.leashStates.getFirst();
        Vec3 start = vanilla.start;
        Vec3 end = vanilla.end;
        Vec3 origin = start.subtract(vanilla.offset);

        Level level = entity.level();
        BlockPos startPos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        int startBlockLight = level.getBrightness(LightLayer.BLOCK, startPos);
        int startSkyLight = level.getBrightness(LightLayer.SKY, startPos);
        int endBlockLight = vanilla.endBlockLight;
        int endSkyLight = vanilla.endSkyLight;
        if (entity.getLeashHolder() instanceof LeadAnchorEntity other) {
            BlockPos endPos = BlockPos.containing(other.getLightProbePosition(partialTicks));
            endBlockLight = level.getBrightness(LightLayer.BLOCK, endPos);
            endSkyLight = level.getBrightness(LightLayer.SKY, endPos);
        }

        float sagFraction = LeadAnchorEntity.SLACK_SAG[Math.floorMod(entity.getSlack(), LeadAnchorEntity.SLACK_SAG.length)];
        double sag = sagFraction * start.distanceTo(end);

        List<EntityRenderState.LeashState> pieces = new ArrayList<>(SEGMENTS);
        Vec3 previous = start;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            Vec3 point = start.lerp(end, t).subtract(0.0, sag * 4.0 * t * (1.0 - t), 0.0);
            double t0 = (double) (i - 1) / SEGMENTS;
            EntityRenderState.LeashState piece = new EntityRenderState.LeashState();
            piece.start = previous;
            piece.end = point;
            piece.offset = previous.subtract(origin);
            piece.startBlockLight = (int) Math.round(startBlockLight + (endBlockLight - startBlockLight) * t0);
            piece.endBlockLight = (int) Math.round(startBlockLight + (endBlockLight - startBlockLight) * t);
            piece.startSkyLight = (int) Math.round(startSkyLight + (endSkyLight - startSkyLight) * t0);
            piece.endSkyLight = (int) Math.round(startSkyLight + (endSkyLight - startSkyLight) * t);
            piece.slack = false;
            pieces.add(piece);
            previous = point;
        }
        state.leashStates = pieces;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
