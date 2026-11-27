package com.techmonkey.leadlines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** A rope tied between two knots, with how much it sags (an index into {@link Ropes#SLACK_SAG}). */
public record Rope(RopeEnd a, RopeEnd b, int slack) {
    public static final Codec<Rope> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RopeEnd.CODEC.fieldOf("a").forGetter(Rope::a),
            RopeEnd.CODEC.fieldOf("b").forGetter(Rope::b),
            Codec.INT.optionalFieldOf("slack", Ropes.DEFAULT_SLACK).forGetter(Rope::slack)
    ).apply(instance, Rope::new));

    public static final StreamCodec<ByteBuf, Rope> STREAM_CODEC = StreamCodec.composite(
            RopeEnd.STREAM_CODEC, Rope::a,
            RopeEnd.STREAM_CODEC, Rope::b,
            ByteBufCodecs.VAR_INT, Rope::slack,
            Rope::new);

    public boolean touches(Vec3 point) {
        return a.point().equals(point) || b.point().equals(point);
    }

    /** The end that isn't at {@code point}. */
    public RopeEnd otherEnd(Vec3 point) {
        return a.point().equals(point) ? b : a;
    }

    public boolean connects(RopeEnd first, RopeEnd second) {
        return (a.samePoint(first) && b.samePoint(second)) || (a.samePoint(second) && b.samePoint(first));
    }

    public Rope withSlack(int newSlack) {
        return new Rope(a, b, newSlack);
    }
}
