package dev.smartpath.algorithm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Resolves two classic vanilla villager pathfinding bugs when approaching a job-site table, bed, or bell:
 *
 * 1. Roof-Snap Bug: Vanilla GroundPathNavigation#findSurfacePosition scans straight UP through solid blocks
 *    when a target block is solid (like a Smithing Table or Lectern with a block/lantern/low roof above it),
 *    teleporting the path target onto the roof of the building.
 * 2. Blocked Table Side: Evaluates the 4 cardinal sides (plus 1-block step-up/step-down ledges) around a
 *    solid target block and picks the most accessible standable approach tile on the same room floor.
 */
public final class WorkstationApproachResolver {
    private WorkstationApproachResolver() {}

    /**
     * Given a requested target BlockPos, returns either a clean indoor/same-floor standable position
     * adjacent to the block (when reachRange == 0) or keeps the original block coordinate without
     * snapping it through solid ceilings onto the roof.
     */
    public static BlockPos resolveTargetPosition(Mob mob, Level level, BlockPos targetPos, int reachRange) {
        BlockState targetState = level.getBlockState(targetPos);

        // If the target block itself is solid (e.g. a Villager workstation table, lectern, barrel, stonecutter)
        if (!targetState.isPathfindable(PathComputationType.LAND)) {
            // Check if there is a standable space right above the block with 2 blocks of headroom
            // (and NOT scanning through a solid ceiling/roof).
            BlockPos above = targetPos.above();
            if (hasStandableHeadroom(level, above, mob)) {
                // Only use 'above' if the mob is already above or if all 4 sides are blocked
                BlockPos bestSide = findBestAdjacentStandablePos(mob, level, targetPos);
                if (bestSide != null) {
                    return reachRange == 0 ? bestSide : targetPos;
                }
                return above;
            }

            // Find the best standable adjacent approach block on the same floor
            BlockPos bestSide = findBestAdjacentStandablePos(mob, level, targetPos);
            if (bestSide != null) {
                // When reachRange >= 1 (e.g. AcquirePoi with PoiType.validRange() == 1),
                // keeping targetPos (instead of snapping it to the roof via vanilla findSurfacePosition)
                // lets AcquirePoi match path.getTarget() against PoiManager while ending adjacent to the table.
                return reachRange == 0 ? bestSide : targetPos;
            }
        }

        return null; // Fall back to vanilla surface finder for open air columns
    }

    /**
     * Scores all cardinal neighbors around a workstation or target block and returns the lowest-cost
     * standable approach position.
     */
    public static BlockPos findBestAdjacentStandablePos(Mob mob, Level level, BlockPos workstationPos) {
        BlockPos bestPos = null;
        double bestScore = Double.POSITIVE_INFINITY;

        // Check same-level cardinal neighbors first, then -1 (step down) and +1 (step up)
        int[] yOffsets = {0, -1, 1};

        for (int dy : yOffsets) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = workstationPos.offset(dir.getStepX(), dy, dir.getStepZ());
                if (!isStandablePosition(level, candidate, mob)) {
                    continue;
                }

                double score = scoreCandidate(mob, level, candidate, dy);
                if (score < bestScore) {
                    bestScore = score;
                    bestPos = candidate;
                }
            }
        }

        return bestPos;
    }

    private static boolean isStandablePosition(Level level, BlockPos footPos, Mob mob) {
        BlockPos groundPos = footPos.below();
        BlockState groundState = level.getBlockState(groundPos);
        if (groundState.isAir() || groundState.getCollisionShape(level, groundPos).isEmpty()) {
            return false;
        }
        return hasStandableHeadroom(level, footPos, mob);
    }

    private static boolean hasStandableHeadroom(Level level, BlockPos footPos, Mob mob) {
        int requiredHeight = Math.max(1, (int) Math.ceil(mob.getBbHeight()));
        for (int h = 0; h < requiredHeight; h++) {
            BlockPos checkPos = footPos.above(h);
            BlockState state = level.getBlockState(checkPos);
            // Allow wooden doors that villagers can open, but reject solid blocks and closed trapdoors at head height
            if (state.getBlock() instanceof DoorBlock) {
                continue;
            }
            if (state.getBlock() instanceof TrapDoorBlock && h > 0) {
                return false;
            }
            if (!state.isPathfindable(PathComputationType.LAND) && !state.getCollisionShape(level, checkPos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static double scoreCandidate(Mob mob, Level level, BlockPos candidate, int dy) {
        double distSq = mob.distanceToSqr(
            candidate.getX() + 0.5,
            candidate.getY(),
            candidate.getZ() + 0.5
        );
        double score = Math.sqrt(distSq);

        // Prefer standing level with the workstation over standing 1 block higher/lower
        score += Math.abs(dy) * 2.5;

        // Count open horizontal neighbors around candidate so villagers prefer open aisle tiles over tight corners
        int openSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = candidate.relative(dir);
            if (level.getBlockState(adj).isPathfindable(PathComputationType.LAND)) {
                openSides++;
            }
        }
        score -= openSides * 0.4;

        // Add any dynamic stuck penalty if the mob previously stalled on this tile
        score += StuckPenaltyTracker.getExtraMalus(
            mob,
            candidate.getX(),
            candidate.getY(),
            candidate.getZ(),
            level.getGameTime()
        );

        return score;
    }
}
