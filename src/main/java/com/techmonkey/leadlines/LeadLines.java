package com.techmonkey.leadlines;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class LeadLines implements ModInitializer {
    public static final String MOD_ID = "leadlines";

    private static final ResourceKey<EntityType<?>> LEAD_ANCHOR_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "lead_anchor"));

    public static final EntityType<LeadAnchorEntity> LEAD_ANCHOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LEAD_ANCHOR_KEY,
            EntityType.Builder.<LeadAnchorEntity>of(LeadAnchorEntity::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.375F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(LEAD_ANCHOR_KEY));

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register(LeadConnections::onUseBlock);
    }
}
