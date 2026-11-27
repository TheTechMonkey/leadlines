package com.techmonkey.leadlines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Clicks. These run on both the client and the server; the client only decides whether the
 * click is ours (so the right thing happens on screen), the server makes the change.
 *
 * <p>Right-click, no sneaking:
 * <ul>
 *   <li>Lead in hand: start a rope at the spot you clicked (or at a knot).</li>
 *   <li>Holding a rope: tie it to the spot or knot you click.</li>
 *   <li>Stick on a knot: change how much its ropes sag.</li>
 *   <li>Shears on a knot: cut its ropes.</li>
 *   <li>Empty hand on a knot: pick its newest rope back up to move it.</li>
 * </ul>
 * Punching a knot cuts its ropes too. On a fence, the knot snaps to the center of the post.
 * Animals on leads are left to vanilla (fence knots).
 */
public final class RopeInteractions {
    private RopeInteractions() {
    }

    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        boolean fence = level.getBlockState(pos).is(BlockTags.FENCES);
        PendingRope pending = Ropes.pending(player);

        RopeEnd knot = Ropes.pickKnot(player, hit.getLocation().distanceToSqr(player.getEyePosition()) + 0.01);
        RopeEnd target = knot;
        if (target == null) {
            target = new RopeEnd(fence ? Ropes.fencePostPoint(pos) : hit.getLocation(), pos, hit.getDirection());
            if (fence) {
                knot = Ropes.knotAt(level, target.point()); // Clicking the rails of a fence post that already has a knot.
                if (knot != null) {
                    target = knot;
                }
            }
        }

        if (hand != InteractionHand.MAIN_HAND) {
            // Everything happens on the main-hand click; just keep the off hand from also acting.
            return pending != null || knot != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (pending != null) {
            if (level instanceof ServerLevel serverLevel) {
                Ropes.finishRope(player, serverLevel, pending, target);
            }
            return InteractionResult.SUCCESS;
        }

        if (knot != null) {
            if (held.is(Items.STICK)) {
                if (level instanceof ServerLevel serverLevel) {
                    Ropes.cycleSlack(player, serverLevel, knot);
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(Items.SHEARS)) {
                if (level instanceof ServerLevel serverLevel) {
                    Ropes.cutKnot(serverLevel, knot, true);
                }
                return InteractionResult.SUCCESS;
            }
            if (held.is(Items.LEAD)) {
                if (level instanceof ServerLevel serverLevel) {
                    Ropes.startRope(player, serverLevel, knot);
                }
                return InteractionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                if (level instanceof ServerLevel serverLevel) {
                    Ropes.pickUp(player, serverLevel, knot);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (held.is(Items.LEAD)) {
            if (fence && holdsAnimals(player)) {
                return InteractionResult.PASS; // Vanilla ties the animals to a fence knot.
            }
            if (level instanceof ServerLevel serverLevel) {
                Ropes.startRope(player, serverLevel, target);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Punching a knot cuts its ropes instead of starting to break the block behind it. */
    public static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        double blockDistanceSqr = player.pick(player.blockInteractionRange(), 1.0F, false)
                .getLocation().distanceToSqr(player.getEyePosition()) + 0.01;
        RopeEnd knot = Ropes.pickKnot(player, blockDistanceSqr);
        if (knot == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            Ropes.cutKnot(serverLevel, knot, !player.hasInfiniteMaterials());
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean holdsAnimals(Player player) {
        return !Leashable.leashableInArea(player, leashable -> leashable.getLeashHolder() == player).isEmpty();
    }
}
