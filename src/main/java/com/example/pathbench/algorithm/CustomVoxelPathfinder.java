package com.example.pathbench.algorithm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;

import java.util.Map;
import java.util.Set;

/**
 * 6. Custom Voxel Algorithm (Skeleton Class & Pluggable VoxelHeuristic Interface).
 * Provides a ready-to-extend voxel search framework where custom 3D heuristics, vertical step
 * penalties, and block-state terrain weights can be plugged in without rewriting graph boilerplate.
 */
public class CustomVoxelPathfinder implements Pathfinder {

    /**
     * Functional interface for custom voxel heuristic evaluation.
     * Implement this interface or edit {@link #computeCustomHeuristic} to test custom heuristics.
     */
    @FunctionalInterface
    public interface VoxelHeuristic {
        /**
         * Default 3D Octile voxel heuristic with vertical climb/drop weighting.
         */
        VoxelHeuristic OCTILE_3D = (region, mob, current, target) -> {
            int dx = Math.abs(current.x - target.x);
            int dy = Math.abs(current.y - target.y);
            int dz = Math.abs(current.z - target.z);
            int minXZ = Math.min(dx, dz);
            int maxXZ = Math.max(dx, dz);
            float planarOctile = (maxXZ - minXZ) + 1.4142135F * minXZ;
            // Weight vertical elevation changes slightly higher in voxel terrain
            return planarOctile + (dy * 1.25F);
        };

        float estimateCost(PathNavigationRegion region, Mob mob, Node current, Target target);
    }

    private final VoxelHeuristic heuristic;

    public CustomVoxelPathfinder(VoxelHeuristic heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public String getName() {
        return "custom_voxel";
    }

    @Override
    public SearchResult findPath(
            PathNavigationRegion region,
            Mob mob,
            Set<BlockPos> targets,
            float maxRange,
            int reachRange,
            int maxVisitedNodes,
            NodeEvaluator nodeEvaluator
    ) {
        if (targets.isEmpty()) {
            return SearchResult.empty(mob.blockPosition());
        }

        BinaryHeap openSet = new BinaryHeap();
        Node[] neighbors = new Node[32];

        nodeEvaluator.prepare(region, mob);
        try {
            Node startNode = nodeEvaluator.getStart();
            if (startNode == null) {
                return SearchResult.empty(targets.iterator().next());
            }

            Map<Target, BlockPos> targetMap = Pathfinder.createTargetsMap(nodeEvaluator, targets);

            startNode.g = 0.0F;
            startNode.h = evaluateAllTargets(region, mob, startNode, targetMap.keySet());
            startNode.f = startNode.g + startNode.h;

            openSet.clear();
            openSet.insert(startNode);

            int expanded = 0;
            boolean capHit = false;

            while (!openSet.isEmpty()) {
                if (++expanded >= maxVisitedNodes) {
                    capHit = true;
                    break;
                }

                Node current = openSet.pop();
                current.closed = true;

                boolean reachedAny = false;
                for (Target target : targetMap.keySet()) {
                    if (current.distanceManhattan(target) <= reachRange) {
                        target.setReached();
                        reachedAny = true;
                    }
                }

                if (reachedAny) {
                    break;
                }

                if (current.distanceTo(startNode) < maxRange) {
                    int neighborCount = nodeEvaluator.getNeighbors(neighbors, current);
                    for (int i = 0; i < neighborCount; i++) {
                        Node neighbor = neighbors[i];
                        if (!isVoxelTransitionAllowed(region, mob, current, neighbor)) {
                            continue;
                        }

                        float stepCost = computeCustomTransitionCost(region, mob, current, neighbor);
                        float nextWalked = current.walkedDistance + current.distanceTo(neighbor);
                        float tentativeG = current.g + stepCost;

                        if (nextWalked < maxRange && (!neighbor.inOpenSet() || tentativeG < neighbor.g)) {
                            neighbor.walkedDistance = nextWalked;
                            neighbor.cameFrom = current;
                            neighbor.g = tentativeG;
                            neighbor.h = evaluateAllTargets(region, mob, neighbor, targetMap.keySet());

                            if (neighbor.inOpenSet()) {
                                openSet.changeCost(neighbor, neighbor.g + neighbor.h);
                            } else {
                                neighbor.f = neighbor.g + neighbor.h;
                                openSet.insert(neighbor);
                            }
                        }
                    }
                }
            }

            return Pathfinder.buildFinalResult(targetMap, reachRange, capHit, expanded);
        } finally {
            nodeEvaluator.done();
        }
    }

    /**
     * TODO: Customize this method to experiment with your own voxel heuristic logic
     * (e.g., Jump Point Search pruning, tie-breaking cross-product, or heightmap awareness).
     */
    protected float computeCustomHeuristic(
            PathNavigationRegion region,
            Mob mob,
            Node current,
            Target target
    ) {
        return this.heuristic.estimateCost(region, mob, current, target);
    }

    /**
     * TODO: Customize transition edge costs between adjacent voxels (e.g., favoring dirt paths,
     * penalizing stairs/vertical jumps, or incorporating light-level / village road bonuses).
     */
    protected float computeCustomTransitionCost(
            PathNavigationRegion region,
            Mob mob,
            Node from,
            Node to
    ) {
        float baseDistance = from.distanceTo(to);
        float verticalPenalty = (to.y > from.y) ? 0.35F : 0.0F;
        BlockState groundState = region.getBlockState(new BlockPos(to.x, to.y - 1, to.z));
        float roadBonus = groundState.getBlock().getDescriptionId().contains("dirt_path") ? -0.15F : 0.0F;
        return Math.max(0.1F, baseDistance + verticalPenalty + roadBonus + to.costMalus);
    }

    /**
     * TODO: Override or modify to add custom voxel clearance or diagonal corner-cutting rules.
     */
    protected boolean isVoxelTransitionAllowed(
            PathNavigationRegion region,
            Mob mob,
            Node from,
            Node to
    ) {
        return !to.closed;
    }

    private float evaluateAllTargets(
            PathNavigationRegion region,
            Mob mob,
            Node node,
            Set<Target> targets
    ) {
        float minH = Float.MAX_VALUE;
        for (Target target : targets) {
            float h = computeCustomHeuristic(region, mob, node, target);
            target.updateBest(h, node);
            if (h < minH) {
                minH = h;
            }
        }
        return minH;
    }
}
