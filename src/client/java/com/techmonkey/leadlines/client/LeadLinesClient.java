package com.techmonkey.leadlines.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.techmonkey.leadlines.LeadLines;

public class LeadLinesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Old 1.x knot entities are invisible while they convert themselves into rope data.
        EntityRenderers.register(LeadLines.LEAD_ANCHOR, NoopRenderer::new);
        LevelRenderEvents.COLLECT_SUBMITS.register(RopeRenderer::render);
    }
}
