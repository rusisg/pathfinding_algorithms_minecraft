package dev.pathlab.mixin;

import dev.pathlab.PathLabHook;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces ONLY the search loop of the game's private PathFinder method.
 * Preparing the node evaluator, finding the start node, mapping targets and cleaning up all stay in the
 * game's public method, so nothing else changes.
 *
 * If the game refuses to start with a Mixin error naming one of the two descriptors below, the method
 * signature changed in your Minecraft version: see README, "If the game crashes at start".
 */
@Mixin(PathFinder.class)
public abstract class PathFinderMixin {

    @Shadow @Final private NodeEvaluator nodeEvaluator;
    @Shadow @Final private int maxVisitedNodes;

    /** Remember which mob asked (the private method does not receive it). */
    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"))
    private void pathlab$begin(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange,
                               int accuracy, float depthMultiplier, CallbackInfoReturnable<Path> cir) {
        PathLabHook.begin(mob);
    }

    @Inject(method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("RETURN"))
    private void pathlab$end(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange,
                             int accuracy, float depthMultiplier, CallbackInfoReturnable<Path> cir) {
        PathLabHook.end();
    }

    /** The search loop itself. */
    @Inject(method = "findPath(Lnet/minecraft/world/level/pathfinder/Node;Ljava/util/Map;FIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), cancellable = true)
    private void pathlab$search(Node start, Map<Target, BlockPos> targets, float maxDistanceFromStart,
                                int reachRange, float multiplier, CallbackInfoReturnable<Path> cir) {
        PathLabHook.Result r = PathLabHook.search(this.nodeEvaluator, this.maxVisitedNodes, start, targets,
                maxDistanceFromStart, reachRange, multiplier);
        if (r.handled()) cir.setReturnValue(r.path());
    }
}
