package com.techmonkey.leadlines;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.datafixers.util.Either;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The knot entity Lead Lines 1.x used. Ropes are plain data now, so this only exists to bring
 * old worlds forward: when an old knot loads, its lead becomes a {@link Rope} and the knot
 * removes itself once nothing is tied to it anymore. It's invisible and can't be clicked.
 */
public class LeadAnchorEntity extends Entity implements Leashable {
    /** Center of the old knot above the entity's position. */
    private static final double KNOT_CENTER = 0.25;
    /** How far apart two old knots could be (the 1.x lead length), plus room. */
    private static final double SEARCH_REACH = 104.0;
    /** Give up on a lead whose other end never loads, and drop it as an item, after 5 minutes. */
    private static final int GIVE_UP_TICKS = 6000;
    /** Remove a converted knot once nothing has been tied to it for a minute. */
    private static final int IDLE_TICKS = 1200;

    private BlockPos attachedTo = BlockPos.ZERO;
    private Direction face = Direction.UP;
    private int slack = Ropes.DEFAULT_SLACK;
    private Leashable.@Nullable LeashData leashData;
    private boolean heldByPlayerWhenSaved;
    private int waitingTicks;
    private int idleTicks;

    public LeadAnchorEntity(EntityType<? extends LeadAnchorEntity> type, Level level) {
        super(type, level);
    }

    private RopeEnd ropeEnd() {
        return new RopeEnd(position().add(0.0, KNOT_CENTER, 0.0), attachedTo, face);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (heldByPlayerWhenSaved) {
            // The world was saved while someone was stringing this lead. Give the lead back.
            heldByPlayerWhenSaved = false;
            spawnAtLocation(serverLevel, Items.LEAD);
            leashData = null;
        }

        if (leashData != null) {
            convertLead(serverLevel, leashData);
        }

        if (leashData == null) {
            idleTicks = leashedTo(this).isEmpty() ? idleTicks + 1 : 0;
            if (idleTicks > IDLE_TICKS) {
                discard();
            }
        }
    }

    /** Turns this knot's outgoing lead into a rope, once the other end is loaded. */
    private void convertLead(ServerLevel level, Leashable.LeashData data) {
        Entity holder = data.leashHolder;
        RopeEnd otherEnd = null;
        if (holder == null && data.delayedLeashInfo != null) {
            Either<UUID, BlockPos> waitingFor = data.delayedLeashInfo;
            Optional<UUID> holderId = waitingFor.left();
            Optional<BlockPos> fencePos = waitingFor.right();
            if (holderId.isPresent()) {
                holder = level.getEntity(holderId.get());
            } else if (fencePos.isPresent() && level.isLoaded(fencePos.get())) {
                otherEnd = new RopeEnd(Ropes.fencePostPoint(fencePos.get()), fencePos.get(), Direction.UP);
                LeashFenceKnotEntity.getKnot(level, fencePos.get()).ifPresent(knot -> removeKnotIfUnused(knot));
            }
        }

        if (holder instanceof LeadAnchorEntity otherAnchor) {
            otherEnd = otherAnchor.ropeEnd();
        } else if (holder instanceof LeashFenceKnotEntity fenceKnot) {
            otherEnd = new RopeEnd(Ropes.fencePostPoint(fenceKnot.getPos()), fenceKnot.getPos(), Direction.UP);
        } else if (holder != null) {
            // Tied to a player or something else that isn't a knot: give the lead back.
            spawnAtLocation(level, Items.LEAD);
            leashData = null;
            return;
        }

        if (otherEnd != null) {
            Ropes.addRope(level, new Rope(ropeEnd(), otherEnd, slack));
            leashData = null;
            if (holder instanceof LeashFenceKnotEntity fenceKnot) {
                removeKnotIfUnused(fenceKnot);
            }
        } else if (++waitingTicks > GIVE_UP_TICKS) {
            spawnAtLocation(level, Items.LEAD);
            leashData = null;
        }
    }

    /** A vanilla fence knot that only held this lead would be left empty; remove it. */
    private void removeKnotIfUnused(LeashFenceKnotEntity knot) {
        boolean othersTied = leashedTo(knot).stream().anyMatch(leashable -> leashable != this);
        if (!othersTied) {
            knot.discard();
        }
    }

    /** Leashable things tied to {@code holder}, searched as far as an old lead could reach. */
    private static List<Leashable> leashedTo(Entity holder) {
        AABB area = AABB.ofSize(holder.getBoundingBox().getCenter(), SEARCH_REACH, SEARCH_REACH, SEARCH_REACH);
        return holder.level().getEntitiesOfClass(Entity.class, area,
                        entity -> entity instanceof Leashable leashable && leashable.getLeashHolder() == holder)
                .stream()
                .map(Leashable.class::cast)
                .toList();
    }

    // ----- Leashable (only so old saved leads load) ---------------------------------------

    @Override
    public Leashable.@Nullable LeashData getLeashData() {
        return leashData;
    }

    @Override
    public void setLeashData(Leashable.@Nullable LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public double leashSnapDistance() {
        return SEARCH_REACH;
    }

    @Override
    public double leashElasticDistance() {
        return SEARCH_REACH;
    }

    @Override
    public boolean checkElasticInteractions(Entity leashHolder, Leashable.LeashData data) {
        return false;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, KNOT_CENTER, 0.0);
    }

    // ----- Entity --------------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("attached_to", BlockPos.CODEC, attachedTo);
        output.store("face", Direction.CODEC, face);
        output.putInt("slack", slack);
        writeLeashData(output, leashData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        attachedTo = input.read("attached_to", BlockPos.CODEC).orElse(blockPosition());
        face = input.read("face", Direction.CODEC).orElse(Direction.UP);
        slack = Math.floorMod(input.getIntOr("slack", Ropes.DEFAULT_SLACK), Ropes.SLACK_SAG.length);
        heldByPlayerWhenSaved = input.getBooleanOr("held_by_player", false);
        readLeashData(input);
    }
}
