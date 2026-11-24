package com.techmonkey.leadlines.client;

import net.minecraft.client.renderer.entity.EntityRenderers;

import net.fabricmc.api.ClientModInitializer;

import com.techmonkey.leadlines.LeadLines;

public class LeadLinesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(LeadLines.LEAD_ANCHOR, LeadAnchorRenderer::new);
    }
}
