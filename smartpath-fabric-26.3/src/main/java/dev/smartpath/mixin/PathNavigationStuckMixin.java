package dev.smartpath.mixin;

import dev.smartpath.algorithm.StuckPenaltyTracker;
import dev.smartpath.config.SmartPathConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enhances {@link PathNavigation#doStuckDetection(Vec3)} so that when a villager or ground mob
 * stalls against a block edge, carpet, trapdoor, or crowded workstation for more than 35 ticks (~1.75s),
 * the obstructing waypoint coordinate is recorded in {@link StuckPenaltyTracker} and the path is
 * immediately recalculated around the obstacle instead of waiting 100+ ticks.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationStuckMixin {

    @Shadow @Final protected Mob mob;
    @Shadow @Final protected Level level;
    @Shadow protected @Nullable Path path;
    @Shadow protected int tick;
    @Shadow protected int lastStuckCheck;
    @Shadow protected Vec3 lastStuckCheckPos;

    @Shadow public abstract void recomputePath();
    @Shadow public abstract boolean isDone();

    @Unique
    private int smartpath$stallTicks = 0;

    @Unique
    private Vec3 smartpath$lastTickPos = Vec3.ZERO;

    @Inject(method = "doStuckDetection", at = @At("HEAD"))
    private void smartpath$fastStuckRecovery(final Vec3 mobPos, final CallbackInfo ci) {
        SmartPathConfig config = SmartPathConfig.get();
        if (!config.enabled || !config.enableStuckPenaltyMemory || this.isDone() || this.path == null) {
            this.smartpath$stallTicks = 0;
            this.smartpath$lastTickPos = mobPos;
            return;
        }

        double movedSq = mobPos.distanceToSqr(this.smartpath$lastTickPos);
        this.smartpath$lastTickPos = mobPos;

        // If the mob barely moved horizontally while trying to follow an active path
        if (movedSq < 0.0025D) {
            this.smartpath$stallTicks++;
            if (this.smartpath$stallTicks >= 35) {
                this.smartpath$stallTicks = 0;
                int nextIdx = this.path.getNextNodeIndex();
                if (nextIdx < this.path.getNodeCount()) {
                    Node blockingNode = this.path.getNode(nextIdx);
                    StuckPenaltyTracker.recordStuckPosition(
                        this.mob,
                        new BlockPos(blockingNode.x, blockingNode.y, blockingNode.z),
                        this.level.getGameTime()
                    );
                } else {
                    Vec3i targetNode = this.path.getNextNodePos();
                    StuckPenaltyTracker.recordStuckPosition(
                        this.mob,
                        new BlockPos(targetNode.getX(), targetNode.getY(), targetNode.getZ()),
                        this.level.getGameTime()
                    );
                }
                this.recomputePath();
            }
        } else {
            this.smartpath$stallTicks = Math.max(0, this.smartpath$stallTicks - 2);
        }
    }
}
