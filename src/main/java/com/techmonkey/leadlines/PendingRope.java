package com.techmonkey.leadlines;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A rope a player is in the middle of stringing: tied at {@code from}, the other end in their
 * hand. Kept on the player (synced so everyone sees the rope to their hand), never saved.
 */
public record PendingRope(RopeEnd from, ResourceKey<Level> dimension) {
    public static final StreamCodec<ByteBuf, PendingRope> STREAM_CODEC = StreamCodec.composite(
            RopeEnd.STREAM_CODEC, PendingRope::from,
            ResourceKey.streamCodec(Registries.DIMENSION), PendingRope::dimension,
            PendingRope::new);
}
