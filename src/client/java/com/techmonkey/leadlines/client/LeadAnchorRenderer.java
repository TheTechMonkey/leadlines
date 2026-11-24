package com.techmonkey.leadlines.client;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import com.techmonkey.leadlines.LeadAnchorEntity;

/** Draws an anchor as a vanilla lead knot. The lead itself is drawn by EntityRenderer. */
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
    @Override
    public void extractRenderState(LeadAnchorEntity entity, EntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state.leashStates == null) {
            return;
        }
        Level level = entity.level();
        BlockPos startPos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        int startBlockLight = level.getBrightness(LightLayer.BLOCK, startPos);
        int startSkyLight = level.getBrightness(LightLayer.SKY, startPos);
        Entity holder = entity.getLeashHolder();
        BlockPos endPos = holder instanceof LeadAnchorEntity other ? BlockPos.containing(other.getLightProbePosition(partialTicks)) : null;
        for (EntityRenderState.LeashState leash : state.leashStates) {
            leash.startBlockLight = startBlockLight;
            leash.startSkyLight = startSkyLight;
            if (endPos != null) {
                leash.endBlockLight = level.getBrightness(LightLayer.BLOCK, endPos);
                leash.endSkyLight = level.getBrightness(LightLayer.SKY, endPos);
            }
        }
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
