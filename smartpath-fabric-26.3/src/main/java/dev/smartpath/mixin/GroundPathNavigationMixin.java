package dev.smartpath.mixin;

import dev.smartpath.algorithm.SmartWalkNodeEvaluator;
import dev.smartpath.algorithm.ThetaStarPathFinder;
import dev.smartpath.algorithm.WorkstationApproachResolver;
import dev.smartpath.config.SmartPathConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides vanilla {@link GroundPathNavigation} (used by Villagers, Iron Golems, Zombies, Passive Mobs, etc.):
 * 1. Replaces vanilla {@code PathFinder} + {@code WalkNodeEvaluator} with {@link ThetaStarPathFinder}
 *    and {@link SmartWalkNodeEvaluator}.
 * 2. Fixes indoor solid target resolution (e.g. a Villager pathing to its workstation table under a low roof)
 *    before vanilla's {@code findSurfacePosition} can warp the target onto the building's roof.
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin extends PathNavigation {

    protected GroundPathNavigationMixin(final Mob mob, final Level level) {
        super(mob, level);
    }

    @Inject(method = "createPathFinder", at = @At("HEAD"), cancellable = true)
    private void smartpath$createThetaStarPathFinder(
        final int maxVisitedNodes,
        final CallbackInfoReturnable<PathFinder> cir
    ) {
        this.nodeEvaluator = new SmartWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        cir.setReturnValue(new ThetaStarPathFinder(this.nodeEvaluator, maxVisitedNodes));
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void smartpath$resolveIndoorWorkstationTarget(
        final BlockPos pos,
        final int reachRange,
        final CallbackInfoReturnable<Path> cir
    ) {
        SmartPathConfig config = SmartPathConfig.get();
        if (!config.enabled || !config.enableWorkstationTargetFix) {
            return;
        }

        if (!this.level.hasChunkAt(pos)) {
            cir.setReturnValue(null);
            return;
        }

        BlockPos resolved = WorkstationApproachResolver.resolveTargetPosition(this.mob, this.level, pos, reachRange);
        if (resolved != null) {
            cir.setReturnValue(super.createPath(resolved, reachRange));
        }
    }
}
