package dev.smartpath.algorithm;

import dev.smartpath.config.SmartPathConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Enhanced WalkNodeEvaluator that improves upon vanilla's neighbor evaluation for ground mobs and villagers:
 * - Applies anti-stuck memory penalties so mobs route around coordinates where they recently stalled.
 * - Penalizes trapdoor/carpet lip traps and tight corner squeezes that frequently snag villager hitboxes.
 * - Rewards open doorways and clear walkways when villagers navigate indoors to their job-site tables.
 */
public class SmartWalkNodeEvaluator extends WalkNodeEvaluator {
    private long currentGameTime;
    private boolean isVillager;

    @Override
    public void prepare(final PathNavigationRegion level, final Mob entity) {
        super.prepare(level, entity);
        this.currentGameTime = entity.level().getGameTime();
        this.isVillager = entity instanceof Villager;
    }

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int count = super.getNeighbors(neighbors, pos);
        SmartPathConfig config = SmartPathConfig.get();
        if (!config.enabled || this.mob == null) {
            return count;
        }

        for (int i = 0; i < count; i++) {
            Node neighbor = neighbors[i];
            if (neighbor == null || neighbor.closed) {
                continue;
            }

            // 1. Apply temporary stuck penalty if the mob recently stalled at this block
            if (config.enableStuckPenaltyMemory) {
                float stuckPenalty = StuckPenaltyTracker.getExtraMalus(
                    this.mob, neighbor.x, neighbor.y, neighbor.z, this.currentGameTime
                );
                if (stuckPenalty > 0.0F) {
                    neighbor.costMalus += stuckPenalty;
                }
            }

            // 2. Villager-specific indoor & workstation navigation refinements
            if (this.isVillager && this.currentContext != null) {
                refineVillagerNodeCost(neighbor, pos);
            }
        }

        return count;
    }

    private void refineVillagerNodeCost(final Node neighbor, final Node from) {
        BlockPos footPos = new BlockPos(neighbor.x, neighbor.y, neighbor.z);
        BlockState footState = this.currentContext.getBlockState(footPos);
        BlockState headState = this.currentContext.getBlockState(footPos.above());

        // Trapdoors at head level in trading halls or decorative builds often block 1.95m-tall villagers
        if (headState.getBlock() instanceof TrapDoorBlock) {
            neighbor.costMalus += 8.0F;
        }

        // Stepping up onto a carpet from a lower block (dy > 0) has a 1.0625m collision height that can snag
        if (neighbor.y > from.y && footState.getBlock() instanceof CarpetBlock) {
            neighbor.costMalus += 2.5F;
        }

        // Villagers cannot open fence gates; discourage rubbing against closed fence gates
        if (footState.getBlock() instanceof FenceGateBlock && !footState.getValue(FenceGateBlock.OPEN)) {
            neighbor.costMalus += 12.0F;
        }

        // Slightly reduce malus on valid wooden doors so villagers cleanly enter/exit workstation rooms
        if (neighbor.type == PathType.DOOR_WOOD_CLOSED || footState.getBlock() instanceof DoorBlock) {
            neighbor.costMalus = Math.max(0.0F, neighbor.costMalus - 0.5F);
        }
    }
}
