package com.techmonkey.leadlines;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

public class LeadLines implements ModInitializer {
    public static final String MOD_ID = "leadlines";
    private static final int SUPPORT_CHECK_INTERVAL = 20;

    /** Every rope in a dimension, saved with the world and synced to the players in it. */
    public static final AttachmentType<RopeNetwork> ROPES = AttachmentRegistry.<RopeNetwork>builder()
            .persistent(RopeNetwork.CODEC)
            .syncWith(RopeNetwork.STREAM_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(id("ropes"));

    /** The rope a player is stringing. Synced so everyone sees it run to their hand; not saved. */
    public static final AttachmentType<PendingRope> PENDING_ROPE = AttachmentRegistry.<PendingRope>builder()
            .syncWith(PendingRope.STREAM_CODEC, AttachmentSyncPredicate.all())
            .buildAndRegister(id("pending_rope"));

    private static final ResourceKey<EntityType<?>> LEAD_ANCHOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("lead_anchor"));

    /** Knot entity from Lead Lines 1.x. Kept only so old worlds load; each one converts itself to rope data. */
    public static final EntityType<LeadAnchorEntity> LEAD_ANCHOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            LEAD_ANCHOR_KEY,
            EntityType.Builder.<LeadAnchorEntity>of(LeadAnchorEntity::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.375F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(LEAD_ANCHOR_KEY));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        UseBlockCallback.EVENT.register(RopeInteractions::onUseBlock);
        AttackBlockCallback.EVENT.register(RopeInteractions::onAttackBlock);
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            Ropes.checkPending(level);
            if (level.getGameTime() % SUPPORT_CHECK_INTERVAL == 0) {
                Ropes.checkSupports(level);
            }
        });
    }
}
