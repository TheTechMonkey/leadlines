package com.techmonkey.leadlines;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.datafixers.util.Either;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A lead knot that can sit on any block, at the exact spot you clicked.
 *
 * <p>An anchor can hold one lead going out (to another anchor, a fence knot, or the player
 * while you're stringing it) and any number of leads coming in. It never moves, so leads
 * between anchors hang still like ropes or power lines.
 *
 * <p>Unlike vanilla leashes, a lead between anchors survives the other end's chunk being
 * unloaded: the anchor just waits for it to load again instead of snapping (MC-300654).
 */
public class LeadAnchorEntity extends Entity implements Leashable {
    /** Center of the knot above the entity's position (the knot model is 0.5 tall). */
    private static final double KNOT_CENTER = 0.25;
    private static final double MAX_LEAD_LENGTH = 16.0;
    private static final int BLOCK_CHECK_INTERVAL = 10;

    /** The side of the block the knot is on. Synced so the client can light the knot from outside the block. */
    private static final EntityDataAccessor<Direction> FACE = SynchedEntityData.defineId(LeadAnchorEntity.class, EntityDataSerializers.DIRECTION);

    private BlockPos attachedTo = BlockPos.ZERO;
    private Leashable.@Nullable LeashData leashData;
    private boolean heldByPlayerWhenSaved;

    public LeadAnchorEntity(EntityType<? extends LeadAnchorEntity> type, Level level) {
        super(type, level);
    }

    /** Creates an anchor whose knot is centered on {@code point}, attached to the block at {@code attachedTo}. */
    public static LeadAnchorEntity create(Level level, BlockPos attachedTo, Direction face, Vec3 point) {
        LeadAnchorEntity anchor = new LeadAnchorEntity(LeadLines.LEAD_ANCHOR, level);
        anchor.attachedTo = attachedTo.immutable();
        anchor.entityData.set(FACE, face);
        anchor.setPos(point.x, point.y - KNOT_CENTER, point.z);
        return anchor;
    }

    public BlockPos getAttachedTo() {
        return attachedTo;
    }

    public Direction getFace() {
        return entityData.get(FACE);
    }

    /** Light the knot by the air in front of the block face, not the inside of the block. */
    @Override
    public Vec3 getLightProbePosition(float partialTickTime) {
        Direction face = getFace();
        return getPosition(partialTickTime).add(face.getStepX() * 0.6, KNOT_CENTER + face.getStepY() * 0.6, face.getStepZ() * 0.6);
    }

    // ----- Leashable -------------------------------------------------------------------

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
        return MAX_LEAD_LENGTH;
    }

    @Override
    public double leashElasticDistance() {
        return MAX_LEAD_LENGTH;
    }

    /** Anchors never get pulled around by their lead. */
    @Override
    public boolean checkElasticInteractions(Entity leashHolder, Leashable.LeashData data) {
        return false;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, KNOT_CENTER, 0.0);
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTickTime) {
        return getPosition(partialTickTime).add(0.0, KNOT_CENTER, 0.0);
    }

    @Override
    public void notifyLeasheeRemoved(Leashable entity) {
        removeIfUnused();
    }

    // ----- Ticking ---------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (tickCount % BLOCK_CHECK_INTERVAL == 0) {
            BlockState state = serverLevel.getBlockState(attachedTo);
            if (state.isAir() || state.canBeReplaced()) {
                breakAnchor(serverLevel, true);
                return;
            }
        }

        if (heldByPlayerWhenSaved) {
            // The world was saved while someone was stringing this lead. Give the lead back.
            heldByPlayerWhenSaved = false;
            spawnAtLocation(serverLevel, Items.LEAD);
            setLeashData(null);
            removeIfUnused();
            return;
        }

        tickLead(serverLevel);

        if (tickCount % 20 == 0) {
            removeIfUnused();
        }
    }

    private void tickLead(ServerLevel serverLevel) {
        Leashable.LeashData data = leashData;
        if (data == null) {
            return;
        }

        // The other end unloaded with its chunk: remember it and wait, don't snap.
        Entity holder = data.leashHolder;
        if (holder != null && holder.isRemoved() && holder.getRemovalReason() != null && !holder.getRemovalReason().shouldDestroy()) {
            data.leashHolder = null;
            data.delayedLeashInfo = holder instanceof LeashFenceKnotEntity knot
                    ? Either.right(knot.getPos())
                    : Either.left(holder.getUUID());
            return;
        }

        // Waiting for the other end to load (after a restart or a chunk reload).
        if (data.leashHolder == null) {
            Either<UUID, BlockPos> waitingFor = data.delayedLeashInfo;
            if (waitingFor == null) {
                setLeashData(null);
                return;
            }
            Optional<UUID> holderId = waitingFor.left();
            Optional<BlockPos> knotPos = waitingFor.right();
            if (holderId.isPresent()) {
                Entity loaded = serverLevel.getEntity(holderId.get());
                if (loaded != null) {
                    setLeashedTo(loaded, true);
                }
            } else if (knotPos.isPresent() && serverLevel.isLoaded(knotPos.get())) {
                setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(serverLevel, knotPos.get()), true);
            }
            return;
        }

        // Connected: vanilla handles snapping when stretched too far, untying, etc.
        Leashable.tickLeash(serverLevel, this);
    }

    /** Removes this anchor if no lead goes out of it or into it anymore. */
    private void removeIfUnused() {
        if (isRemoved() || level().isClientSide()) {
            return;
        }
        if (leashData == null && Leashable.leashableLeashedTo(this).isEmpty()) {
            discard();
        }
    }

    /** Drops every lead tied to this anchor as items and removes it. */
    public void breakAnchor(ServerLevel serverLevel, boolean dropLeads) {
        if (isRemoved()) {
            return;
        }
        if (isLeashed()) {
            if (dropLeads) {
                dropLeash();
            } else {
                removeLeash();
            }
        }
        setLeashData(null);
        for (Leashable tiedHere : Leashable.leashableLeashedTo(this)) {
            if (dropLeads) {
                tiedHere.dropLeash();
            } else {
                tiedHere.removeLeash();
            }
        }
        playSound(SoundEvents.LEAD_UNTIED, 1.0F, 1.0F);
        discard();
    }

    // ----- Player interaction ----------------------------------------------------------

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.SHEARS)) {
            return super.interact(player, hand, location);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Tie whatever the player is holding on a lead to this anchor.
        boolean tied = false;
        for (Leashable leashable : LeadConnections.heldBy(player)) {
            if (leashable != this && leashable.canHaveALeashAttachedTo(this)) {
                leashable.setLeashedTo(this, true);
                tied = true;
            }
        }
        if (tied) {
            playSound(SoundEvents.LEAD_TIED, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // Start a new lead from this anchor.
        if (held.is(Items.LEAD)) {
            LeadConnections.startLead(player, held, level(), attachedTo, getFace(), getRopeHoldPosition(1.0F), this);
            return InteractionResult.SUCCESS;
        }

        // Empty hand: pick the leads tied here back up, like a vanilla fence knot.
        boolean pickedUp = false;
        List<Leashable> tiedHere = Leashable.leashableLeashedTo(this);
        for (Leashable leashable : tiedHere) {
            if (leashable.canHaveALeashAttachedTo(player)) {
                leashable.setLeashedTo(player, true);
                pickedUp = true;
            }
        }
        if (pickedUp) {
            playSound(SoundEvents.LEAD_UNTIED, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float damage) {
        if (isInvulnerableToBase(source)) {
            return false;
        }
        breakAnchor(serverLevel, true);
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }

    // ----- Saving ----------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(FACE, Direction.UP);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("attached_to", BlockPos.CODEC, attachedTo);
        output.store("face", Direction.CODEC, getFace());
        if (leashData != null && leashData.leashHolder instanceof Player) {
            output.putBoolean("held_by_player", true);
        } else {
            writeLeashData(output, leashData);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        attachedTo = input.read("attached_to", BlockPos.CODEC).orElse(blockPosition());
        entityData.set(FACE, input.read("face", Direction.CODEC).orElse(Direction.UP));
        heldByPlayerWhenSaved = input.getBooleanOr("held_by_player", false);
        readLeashData(input);
    }
}
