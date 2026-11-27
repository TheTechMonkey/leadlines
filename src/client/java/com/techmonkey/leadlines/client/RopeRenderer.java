package com.techmonkey.leadlines.client;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.leash.LeashKnotModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.techmonkey.leadlines.PendingRope;
import com.techmonkey.leadlines.Rope;
import com.techmonkey.leadlines.RopeEnd;
import com.techmonkey.leadlines.Ropes;

/**
 * Draws every rope and knot in the level. Ropes use vanilla's lead drawing in short pieces so
 * they hang in a curve (sagging by their slack setting); knots use the vanilla lead knot model.
 * Each end is lit by the air in front of the block it's tied to, not the inside of the block.
 */
public final class RopeRenderer {
    private static final Identifier KNOT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/lead_knot/lead_knot.png");
    /** Pieces a rope is drawn in, so it can hang in a curve. */
    private static final int SEGMENTS = 12;
    /** Don't draw ropes farther away than this. */
    private static final double MAX_RENDER_DISTANCE = 192.0;

    private static LeashKnotModel knotModel;
    private static final EntityRenderState KNOT_STATE = new EntityRenderState();

    private RopeRenderer() {
    }

    public static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = context.levelState().worldPartialTicks;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        double maxDistanceSqr = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

        Set<Vec3> knotsDrawn = new HashSet<>();
        for (Rope rope : Ropes.network(level).ropes()) {
            Vec3 middle = rope.a().point().lerp(rope.b().point(), 0.5);
            if (middle.distanceToSqr(camera) > maxDistanceSqr) {
                continue;
            }
            float sag = Ropes.SLACK_SAG[Math.floorMod(rope.slack(), Ropes.SLACK_SAG.length)];
            submitRope(collector, poseStack, level, camera, rope.a().point(), rope.a().lightProbe(), rope.b().point(), rope.b().lightProbe(), sag);
            for (RopeEnd end : new RopeEnd[] {rope.a(), rope.b()}) {
                if (knotsDrawn.add(end.point())) {
                    submitKnot(collector, poseStack, level, camera, end);
                }
            }
        }

        // Ropes players are stringing run from the knot to their hand, hanging like a held lead.
        for (Player player : level.players()) {
            PendingRope pending = Ropes.pending(player);
            if (pending == null || !pending.dimension().equals(level.dimension())
                    || pending.from().point().distanceToSqr(camera) > maxDistanceSqr) {
                continue;
            }
            RopeEnd from = pending.from();
            Vec3 hand = player.getRopeHoldPosition(partialTicks);
            submitHeldRope(collector, poseStack, level, camera, from, hand, player.getEyePosition(partialTicks));
            if (knotsDrawn.add(from.point())) {
                submitKnot(collector, poseStack, level, camera, from);
            }
        }
    }

    private static void submitRope(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera,
            Vec3 start, Vec3 startProbe, Vec3 end, Vec3 endProbe, float sagFraction) {
        BlockPos startLightPos = BlockPos.containing(startProbe);
        BlockPos endLightPos = BlockPos.containing(endProbe);
        int startBlockLight = level.getBrightness(LightLayer.BLOCK, startLightPos);
        int startSkyLight = level.getBrightness(LightLayer.SKY, startLightPos);
        int endBlockLight = level.getBrightness(LightLayer.BLOCK, endLightPos);
        int endSkyLight = level.getBrightness(LightLayer.SKY, endLightPos);
        double sag = sagFraction * start.distanceTo(end);

        Vec3 previous = start;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            double t0 = (double) (i - 1) / SEGMENTS;
            Vec3 point = start.lerp(end, t).subtract(0.0, sag * 4.0 * t * (1.0 - t), 0.0);
            EntityRenderState.LeashState piece = new EntityRenderState.LeashState();
            piece.start = previous;
            piece.end = point;
            piece.offset = previous.subtract(camera);
            piece.startBlockLight = (int) Math.round(startBlockLight + (endBlockLight - startBlockLight) * t0);
            piece.endBlockLight = (int) Math.round(startBlockLight + (endBlockLight - startBlockLight) * t);
            piece.startSkyLight = (int) Math.round(startSkyLight + (endSkyLight - startSkyLight) * t0);
            piece.endSkyLight = (int) Math.round(startSkyLight + (endSkyLight - startSkyLight) * t);
            piece.slack = false;
            collector.submitLeash(poseStack, piece);
            previous = point;
        }
    }

    private static void submitHeldRope(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera,
            RopeEnd from, Vec3 hand, Vec3 holderEyes) {
        BlockPos startLightPos = BlockPos.containing(from.lightProbe());
        BlockPos endLightPos = BlockPos.containing(holderEyes);
        EntityRenderState.LeashState leash = new EntityRenderState.LeashState();
        leash.start = from.point();
        leash.end = hand;
        leash.offset = from.point().subtract(camera);
        leash.startBlockLight = level.getBrightness(LightLayer.BLOCK, startLightPos);
        leash.startSkyLight = level.getBrightness(LightLayer.SKY, startLightPos);
        leash.endBlockLight = level.getBrightness(LightLayer.BLOCK, endLightPos);
        leash.endSkyLight = level.getBrightness(LightLayer.SKY, endLightPos);
        leash.slack = true;
        collector.submitLeash(poseStack, leash);
    }

    private static void submitKnot(SubmitNodeCollector collector, PoseStack poseStack, ClientLevel level, Vec3 camera, RopeEnd knot) {
        if (knotModel == null) {
            knotModel = new LeashKnotModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.LEASH_KNOT));
        }
        BlockPos lightPos = BlockPos.containing(knot.lightProbe());
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, lightPos), level.getBrightness(LightLayer.SKY, lightPos));
        Vec3 point = knot.point();
        poseStack.pushPose();
        // The knot model is drawn from the knot's base, like the vanilla knot entity.
        poseStack.translate(point.x - camera.x, point.y - 0.25 - camera.y, point.z - camera.z);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(knotModel, KNOT_STATE, poseStack, KNOT_TEXTURE, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
