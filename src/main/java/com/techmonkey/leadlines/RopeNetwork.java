package com.techmonkey.leadlines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * Every rope in one dimension. Saved with the world and synced to the players in it.
 * Immutable: changes make a new network, which is what triggers saving and syncing.
 */
public record RopeNetwork(List<Rope> ropes) {
    public static final RopeNetwork EMPTY = new RopeNetwork(List.of());

    public static final Codec<RopeNetwork> CODEC = Rope.CODEC.listOf().xmap(RopeNetwork::new, RopeNetwork::ropes);

    public static final StreamCodec<ByteBuf, RopeNetwork> STREAM_CODEC =
            Rope.STREAM_CODEC.apply(ByteBufCodecs.list()).map(RopeNetwork::new, RopeNetwork::ropes);

    public RopeNetwork {
        ropes = List.copyOf(ropes);
    }

    /** One entry per knot (rope ends at the same point share a knot). */
    public Map<Vec3, RopeEnd> knots() {
        Map<Vec3, RopeEnd> knots = new LinkedHashMap<>();
        for (Rope rope : ropes) {
            knots.putIfAbsent(rope.a().point(), rope.a());
            knots.putIfAbsent(rope.b().point(), rope.b());
        }
        return knots;
    }

    public List<Rope> ropesAt(Vec3 point) {
        return ropes.stream().filter(rope -> rope.touches(point)).toList();
    }

    public boolean hasRope(RopeEnd first, RopeEnd second) {
        return ropes.stream().anyMatch(rope -> rope.connects(first, second));
    }

    public RopeNetwork with(Rope rope) {
        List<Rope> changed = new ArrayList<>(ropes);
        changed.add(rope);
        return new RopeNetwork(changed);
    }

    public RopeNetwork without(Predicate<Rope> remove) {
        return new RopeNetwork(ropes.stream().filter(remove.negate()).toList());
    }

    public RopeNetwork replace(Rope old, Rope replacement) {
        return new RopeNetwork(ropes.stream().map(rope -> rope == old ? replacement : rope).toList());
    }
}
