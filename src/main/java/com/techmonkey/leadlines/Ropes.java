package com.techmonkey.leadlines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rope rules shared by the server and the client. Ropes and knots are plain data (no
 * entities), so they don't tick, don't show on minimaps, and can't snap when chunks unload.
 */
public final class Ropes {
    public static final double MAX_LENGTH = 50.0;
    /** How much each slack level lets a rope sag, as a fraction of its length. Level 0 is pulled tight. */
    public static final float[] SLACK_SAG = {0.0F, 0.03F, 0.06F, 0.1F, 0.15F};
    public static final int DEFAULT_SLACK = 2;
    /** Knot size, matching the vanilla lead knot (6 x 8 x 6 pixels). */
    private static final double KNOT_HALF_WIDTH = 0.1875;
    private static final double KNOT_HALF_HEIGHT = 0.25;

    private Ropes() {
    }

    // ----- Lookups (server and client) ---------------------------------------------------

    public static RopeNetwork network(Level level) {
        return level.getAttachedOrElse(LeadLines.ROPES, RopeNetwork.EMPTY);
    }

    public static @Nullable PendingRope pending(Player player) {
        return player.getAttached(LeadLines.PENDING_ROPE);
    }

    /**
     * Where a knot sits on a fence: the center of the post, at the same height as a vanilla
     * fence knot, so it wraps the post no matter which part of the fence was clicked.
     */
    public static Vec3 fencePostPoint(BlockPos fencePos) {
        return new Vec3(fencePos.getX() + 0.5, fencePos.getY() + 0.625, fencePos.getZ() + 0.5);
    }

    public static AABB knotBox(Vec3 point) {
        return new AABB(point.x - KNOT_HALF_WIDTH, point.y - KNOT_HALF_HEIGHT, point.z - KNOT_HALF_WIDTH,
                point.x + KNOT_HALF_WIDTH, point.y + KNOT_HALF_HEIGHT, point.z + KNOT_HALF_WIDTH);
    }

    /** Every knot in the level: rope ends, plus the knots players are stringing ropes from. */
    public static Map<Vec3, RopeEnd> knots(Level level) {
        Map<Vec3, RopeEnd> knots = new LinkedHashMap<>(network(level).knots());
        for (Player player : level.players()) {
            PendingRope pending = pending(player);
            if (pending != null && pending.dimension().equals(level.dimension())) {
                knots.putIfAbsent(pending.from().point(), pending.from());
            }
        }
        return knots;
    }

    public static @Nullable RopeEnd knotAt(Level level, Vec3 point) {
        return knots(level).get(point);
    }

    /**
     * The knot the player is looking at, if one is within reach and no farther away than
     * {@code maxDistanceSqr} (the distance to the block they're looking at).
     */
    public static @Nullable RopeEnd pickKnot(Player player, double maxDistanceSqr) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        RopeEnd best = null;
        double bestDistanceSqr = Math.min(maxDistanceSqr, reach * reach);
        for (RopeEnd knot : knots(player.level()).values()) {
            if (knot.point().distanceToSqr(eye) > (reach + 1.0) * (reach + 1.0)) {
                continue;
            }
            AABB box = knotBox(knot.point());
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isPresent()) {
                double distanceSqr = hit.get().distanceToSqr(eye);
                if (distanceSqr <= bestDistanceSqr) {
                    best = knot;
                    bestDistanceSqr = distanceSqr;
                }
            }
        }
        return best;
    }

    // ----- Changes (server only) ---------------------------------------------------------

    private static void setNetwork(ServerLevel level, RopeNetwork network) {
        level.setAttached(LeadLines.ROPES, network.ropes().isEmpty() ? null : network);
    }

    public static void addRope(ServerLevel level, Rope rope) {
        RopeNetwork network = network(level);
        if (!rope.a().samePoint(rope.b()) && !network.hasRope(rope.a(), rope.b())) {
            setNetwork(level, network.with(rope));
        }
    }

    /** Starts stringing a rope from {@code from}. The lead is only used up once the rope is tied. */
    public static void startRope(Player player, ServerLevel level, RopeEnd from) {
        player.setAttached(LeadLines.PENDING_ROPE, new PendingRope(from, level.dimension()));
        playSound(level, from.point(), SoundEvents.LEAD_TIED, 1.0F);
        level.gameEvent(GameEvent.BLOCK_ATTACH, from.block(), GameEvent.Context.of(player));
    }

    public static void cancelRope(Player player) {
        player.removeAttached(LeadLines.PENDING_ROPE);
    }

    /** Ties the rope the player is holding to {@code to}. */
    public static void finishRope(Player player, ServerLevel level, PendingRope pending, RopeEnd to) {
        RopeEnd from = pending.from();
        if (from.samePoint(to)) {
            // Clicked the knot it started from: put it down.
            cancelRope(player);
            playSound(level, to.point(), SoundEvents.LEAD_UNTIED, 1.0F);
            return;
        }
        if (from.point().distanceTo(to.point()) > MAX_LENGTH) {
            player.sendOverlayMessage(Component.translatable("message.leadlines.too_far"));
            return;
        }
        if (network(level).hasRope(from, to)) {
            cancelRope(player);
            return;
        }
        if (!player.hasInfiniteMaterials() && !useOneLead(player)) {
            player.sendOverlayMessage(Component.translatable("message.leadlines.need_lead"));
            cancelRope(player);
            return;
        }
        addRope(level, new Rope(from, to, DEFAULT_SLACK));
        cancelRope(player);
        playSound(level, to.point(), SoundEvents.LEAD_TIED, 1.0F);
        level.gameEvent(GameEvent.BLOCK_ATTACH, to.block(), GameEvent.Context.of(player));
    }

    /** Uses one lead from the player's hands or inventory. */
    private static boolean useOneLead(Player player) {
        for (ItemStack hand : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (hand.is(Items.LEAD)) {
                hand.shrink(1);
                return true;
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.LEAD)) {
                inventory.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    /** Cuts every rope at a knot. Their leads drop at the knot unless {@code dropLeads} is false. */
    public static void cutKnot(ServerLevel level, RopeEnd knot, boolean dropLeads) {
        Vec3 point = knot.point();
        int ropeCount = network(level).ropesAt(point).size();
        if (ropeCount > 0) {
            setNetwork(level, network(level).without(rope -> rope.touches(point)));
            if (dropLeads) {
                dropLeads(level, point, ropeCount);
            }
        }
        for (Player player : level.players()) {
            PendingRope pending = pending(player);
            if (pending != null && pending.from().samePoint(knot)) {
                cancelRope(player);
            }
        }
        playSound(level, point, SoundEvents.LEAD_UNTIED, 1.0F);
    }

    /** Stick on a knot: steps how much its ropes sag (tight, slight, medium, loose, very loose). */
    public static void cycleSlack(Player player, ServerLevel level, RopeEnd knot) {
        List<Rope> ropes = network(level).ropesAt(knot.point());
        if (ropes.isEmpty()) {
            return;
        }
        int next = Math.floorMod(ropes.getFirst().slack() + 1, SLACK_SAG.length);
        RopeNetwork network = network(level);
        for (Rope rope : ropes) {
            network = network.replace(rope, rope.withSlack(next));
        }
        setNetwork(level, network);
        playSound(level, knot.point(), SoundEvents.LEAD_TIED, 0.8F + 0.1F * next);
        player.sendOverlayMessage(Component.translatable("message.leadlines.slack." + next));
    }

    /**
     * Empty hand on a knot: unties the newest rope there and puts it back in your hand, still
     * tied at its other end, so you can move it. Gives the lead back until it's tied again.
     */
    public static boolean pickUp(Player player, ServerLevel level, RopeEnd knot) {
        List<Rope> ropes = network(level).ropesAt(knot.point());
        if (ropes.isEmpty()) {
            return false;
        }
        Rope rope = ropes.getLast();
        RopeEnd otherEnd = rope.otherEnd(knot.point());
        setNetwork(level, network(level).without(existing -> existing == rope));
        if (!player.hasInfiniteMaterials()) {
            giveLead(player, level);
        }
        player.setAttached(LeadLines.PENDING_ROPE, new PendingRope(otherEnd, level.dimension()));
        playSound(level, knot.point(), SoundEvents.LEAD_UNTIED, 1.0F);
        return true;
    }

    private static void giveLead(Player player, ServerLevel level) {
        ItemStack lead = new ItemStack(Items.LEAD);
        if (!player.getInventory().add(lead)) {
            player.spawnAtLocation(level, lead);
        }
    }

    /** Cuts the ropes at knots whose block was broken or replaced. Call now and then. */
    public static void checkSupports(ServerLevel level) {
        List<RopeEnd> unsupported = new ArrayList<>();
        for (RopeEnd knot : network(level).knots().values()) {
            if (level.isLoaded(knot.block())) {
                var state = level.getBlockState(knot.block());
                if (state.isAir() || state.canBeReplaced()) {
                    unsupported.add(knot);
                }
            }
        }
        for (RopeEnd knot : unsupported) {
            cutKnot(level, knot, true);
        }
    }

    /** Drops a player's half-strung rope if they wander too far or change dimension. Call every tick. */
    public static void checkPending(ServerLevel level) {
        for (Player player : level.players()) {
            PendingRope pending = pending(player);
            if (pending == null) {
                continue;
            }
            if (!player.isAlive() || !pending.dimension().equals(level.dimension())) {
                cancelRope(player);
            } else if (player.position().distanceTo(pending.from().point()) > MAX_LENGTH) {
                cancelRope(player);
                playSound(level, pending.from().point(), SoundEvents.LEAD_BREAK, 1.0F);
                player.sendOverlayMessage(Component.translatable("message.leadlines.too_far"));
            }
        }
    }

    private static void dropLeads(ServerLevel level, Vec3 point, int count) {
        ItemEntity item = new ItemEntity(level, point.x, point.y, point.z, new ItemStack(Items.LEAD, count));
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static void playSound(ServerLevel level, Vec3 point, SoundEvent sound, float pitch) {
        level.playSound(null, point.x, point.y, point.z, sound, SoundSource.BLOCKS, 1.0F, pitch);
    }
}
