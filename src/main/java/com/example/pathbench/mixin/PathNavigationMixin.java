package com.example.pathbench.mixin;

import com.example.pathbench.VillagerPathBenchMod;
import com.example.pathbench.algorithm.Pathfinder;
import com.example.pathbench.profiler.PathfindingProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/**
 * Injects into Minecraft's PathNavigation#createPath(Set, int, boolean, int, float)
 * to intercept Villager workstation (POI) navigation, swap in the active Pathfinder
 * algorithm, and export structured profiling telemetry to CSV.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected NodeEvaluator nodeEvaluator;

    @Shadow
    private PathFinder pathFinder;

    @Shadow
    private float maxVisitedNodesMultiplier;

    @Shadow
    public abstract float getMaxPathLength();

    @Shadow
    protected abstract boolean canUpdatePath();

    @Inject(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onVillagerJobSitePathfind(
            Set<BlockPos> targets,
            int regionOffset,
            boolean offsetUpward,
            int reachRange,
            float maxPathLength,
            CallbackInfoReturnable<Path> cir
    ) {
        if (targets.isEmpty() || !(this.mob instanceof Villager villager)) {
            return;
        }

        if (this.mob.getY() < this.level.getMinY() || !this.canUpdatePath()) {
            return;
        }

        if (!isTargetingWorkstationPoi(villager, targets)) {
            return;
        }

        // Construct the PathNavigationRegion identical to vanilla PathNavigation#createPath
        float followRange = maxPathLength > 0.0F ? maxPathLength : this.getMaxPathLength();
        BlockPos startPos = offsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
        int radius = (int) (followRange + (float) regionOffset);
        PathNavigationRegion region = new PathNavigationRegion(
                this.level,
                startPos.offset(-radius, -radius, -radius),
                startPos.offset(radius, radius, radius)
        );

        int baseMaxNodes = ((PathFinderAccessor) this.pathFinder).getMaxVisitedNodes();
        int maxVisitedNodes = Math.max(1, Mth.floor((float) baseMaxNodes * this.maxVisitedNodesMultiplier));

        Pathfinder activeAlgorithm = VillagerPathBenchMod.getActivePathfinder();

        // Capture pre-execution memory and timing metrics
        long heapBefore = PathfindingProfiler.captureUsedHeapBytes();
        long threadAllocBefore = PathfindingProfiler.captureThreadAllocatedBytes();
        long startNanos = System.nanoTime();

        Pathfinder.SearchResult result = activeAlgorithm.findPath(
                region,
                this.mob,
                targets,
                followRange,
                reachRange,
                maxVisitedNodes,
                this.nodeEvaluator
        );

        long elapsedNanos = System.nanoTime() - startNanos;
        long heapAfter = PathfindingProfiler.captureUsedHeapBytes();
        long threadAllocAfter = PathfindingProfiler.captureThreadAllocatedBytes();

        // Export structured benchmark row to config/villager-pathbench/pathfinding_metrics.csv
        PathfindingProfiler.recordEvent(
                activeAlgorithm.getName(),
                this.mob,
                startPos,
                targets,
                result,
                elapsedNanos,
                heapBefore,
                heapAfter,
                threadAllocBefore,
                threadAllocAfter
        );

        VillagerPathBenchMod.LOGGER.info(
                "[PathBench] Logged {} path for Villager from ({},{},{}) -> reached={}, expanded={}, cost={}, nanos={}",
                activeAlgorithm.getName(),
                startPos.getX(), startPos.getY(), startPos.getZ(),
                result.reached(), result.expanded(), result.cost(), elapsedNanos
        );

        cir.setReturnValue(result.path());
    }

    /**
     * Checks whether the Villager's current pathfinding targets correspond to its
     * assigned JOB_SITE, POTENTIAL_JOB_SITE, or a candidate workstation block.
     * Note: GroundPathNavigation#createPath(BlockPos, int) shifts solid blocks (like Composters,
     * Barrels, and Lecterns) vertically to the air block above them before calling createPath(Set, ...),
     * so we check both exact equality and within a 3-block vertical tolerance, plus block state POI types.
     */
    @Unique
    private boolean isTargetingWorkstationPoi(Villager villager, Set<BlockPos> targets) {
        Brain<Villager> brain = villager.getBrain();

        Optional<GlobalPos> jobSite = brain.getMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isPresent() && matchesAnyWithVerticalAdjust(jobSite.get().pos(), targets)) {
            return true;
        }

        Optional<GlobalPos> potentialJobSite = brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        if (potentialJobSite.isPresent() && matchesAnyWithVerticalAdjust(potentialJobSite.get().pos(), targets)) {
            return true;
        }

        // Check if the Villager's active WALK_TARGET points to its job site or potential job site
        Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (walkTarget.isPresent()) {
            BlockPos walkPos = walkTarget.get().getTarget().currentBlockPosition();
            if (jobSite.isPresent() && jobSite.get().pos().closerThan(walkPos, 2.5)) {
                return true;
            }
            if (potentialJobSite.isPresent() && potentialJobSite.get().pos().closerThan(walkPos, 2.5)) {
                return true;
            }
        }

        // Also detect AcquirePoi candidate workstation scans (checking both target and target.below()
        // in case GroundPathNavigation shifted the target position above a solid workstation block)
        if (this.level instanceof ServerLevel serverLevel) {
            PoiManager poiManager = serverLevel.getPoiManager();
            var profession = villager.getVillagerData().profession().value();

            for (BlockPos target : targets) {
                for (int dy = 0; dy >= -2; dy--) {
                    BlockPos checkPos = (dy == 0) ? target : target.offset(0, dy, 0);

                    Optional<Holder<PoiType>> poiType = poiManager.getType(checkPos);
                    if (poiType.isPresent()) {
                        Holder<PoiType> holder = poiType.get();
                        if (profession.heldJobSite().test(holder) || holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)) {
                            return true;
                        }
                    }

                    BlockState state = serverLevel.getBlockState(checkPos);
                    Optional<Holder<PoiType>> statePoi = PoiTypes.forState(state);
                    if (statePoi.isPresent()) {
                        Holder<PoiType> holder = statePoi.get();
                        if (profession.heldJobSite().test(holder) || holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Unique
    private static boolean matchesAnyWithVerticalAdjust(BlockPos poiPos, Set<BlockPos> targets) {
        if (targets.contains(poiPos)) {
            return true;
        }
        for (BlockPos target : targets) {
            if (target.getX() == poiPos.getX()
                    && target.getZ() == poiPos.getZ()
                    && Math.abs(target.getY() - poiPos.getY()) <= 3) {
                return true;
            }
        }
        return false;
    }
}
