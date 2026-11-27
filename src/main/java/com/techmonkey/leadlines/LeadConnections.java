package com.techmonkey.leadlines;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click handling. No sneaking needed:
 * <ul>
 *   <li>Holding a lead and nothing tied to you: right-click any block to tie a lead there. The
 *       other end follows you.</li>
 *   <li>Something tied to you (that lead, or animals): right-click any block to tie it there.</li>
 * </ul>
 * On any other block the knot goes exactly where you clicked. On a fence it snaps to the
 * center of the post, wrapped around it like a vanilla fence knot, wherever on the fence you
 * clicked. Animals alone tied to a fence still use the vanilla fence knot.
 */
public final class LeadConnections {
    private LeadConnections() {
    }

    public static List<Leashable> heldBy(Player player) {
        return LeadAnchorEntity.leashedTo(player);
    }

    /** Fabric {@code UseBlockCallback}: runs on both client and server before vanilla block use. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean holdingSomething = !heldBy(player).isEmpty();

        boolean fence = state.is(BlockTags.FENCES);
        Vec3 point = fence ? fencePostPoint(pos) : hit.getLocation();

        if (holdingSomething) {
            if (fence && heldBy(player).stream().noneMatch(LeadAnchorEntity.class::isInstance)) {
                return InteractionResult.PASS; // Only animals: vanilla ties them to a fence knot.
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS; // Only tie once per click.
            }
            if (!level.mayInteract(player, pos)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel) {
                tieToNewAnchor(player, serverLevel, pos, hit.getDirection(), point);
            }
            return InteractionResult.SUCCESS;
        }

        if (held.is(Items.LEAD)) {
            if (!level.mayInteract(player, pos)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel) {
                startLead(player, held, level, pos, hit.getDirection(), point, LeadAnchorEntity.findAt(level, point));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Ties a new lead at {@code point}: the anchor there is leashed to the player, so the lead
     * runs from the block to the player's hand until they tie the other end somewhere.
     *
     * @param existing an anchor already at that point (when the player clicked a knot), or null
     */
    public static void startLead(Player player, ItemStack leadStack, Level level, BlockPos attachedTo, Direction face, Vec3 point, @Nullable LeadAnchorEntity existing) {
        LeadAnchorEntity start;
        if (existing != null && !existing.isLeashed()) {
            start = existing;
        } else {
            start = LeadAnchorEntity.create(level, attachedTo, face, point);
            level.addFreshEntity(start);
        }
        start.setLeashedTo(player, true);
        leadStack.consume(1, player);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(GameEvent.BLOCK_ATTACH, attachedTo, GameEvent.Context.of(player));
    }

    /**
     * Where a knot sits on a fence: the center of the post, at the same height as a vanilla
     * fence knot, so it wraps the post no matter which part of the fence was clicked.
     */
    public static Vec3 fencePostPoint(BlockPos fencePos) {
        return new Vec3(fencePos.getX() + 0.5, fencePos.getY() + 0.625, fencePos.getZ() + 0.5);
    }

    private static void tieToNewAnchor(Player player, ServerLevel level, BlockPos attachedTo, Direction face, Vec3 point) {
        // Reuse a knot already at this spot (another lead on the same fence post) instead of stacking a second one.
        LeadAnchorEntity existing = LeadAnchorEntity.findAt(level, point);
        LeadAnchorEntity anchor = existing != null ? existing : LeadAnchorEntity.create(level, attachedTo, face, point);
        boolean tied = false;
        for (Leashable leashable : heldBy(player)) {
            if (leashable != anchor && leashable.canHaveALeashAttachedTo(anchor)) {
                if (!tied && existing == null) {
                    level.addFreshEntity(anchor);
                }
                tied = true;
                leashable.setLeashedTo(anchor, true);
            }
        }
        if (tied) {
            level.playSound(null, point.x, point.y, point.z, SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_ATTACH, attachedTo, GameEvent.Context.of(player));
        }
    }
}
