package com.techmonkey.leadlines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * One end of a rope: the knot's center, the block it's tied to, and the side of that block it
 * sits on (used to light the knot from the air in front of the block, not from inside it).
 * Two ends at the same point share one knot.
 */
public record RopeEnd(Vec3 point, BlockPos block, Direction face) {
    public static final Codec<RopeEnd> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("point").forGetter(RopeEnd::point),
            BlockPos.CODEC.fieldOf("block").forGetter(RopeEnd::block),
            Direction.CODEC.fieldOf("face").forGetter(RopeEnd::face)
    ).apply(instance, RopeEnd::new));

    public static final StreamCodec<ByteBuf, RopeEnd> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, RopeEnd::point,
            BlockPos.STREAM_CODEC, RopeEnd::block,
            Direction.STREAM_CODEC, RopeEnd::face,
            RopeEnd::new);

    /** Where to sample light for this knot: just outside the block face. */
    public Vec3 lightProbe() {
        return point.add(face.getStepX() * 0.6, face.getStepY() * 0.6, face.getStepZ() * 0.6);
    }

    public boolean samePoint(RopeEnd other) {
        return point.equals(other.point);
    }
}
