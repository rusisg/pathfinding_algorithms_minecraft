package com.example.pathbench.algorithm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;

import java.util.Map;
import java.util.Set;

/**
 * 1. Vanilla Minecraft Pathfinding (Baseline).
 * Replicates Mojang's exact PathFinder implementation, including the 1.5x heuristic
 * multiplier (FUDGING = 1.5F), BinaryHeap open set, and maxVisitedNodes cap, while
 * exposing exact expanded-node and capHit telemetry for CSV benchmarking.
 */
public class VanillaPathfinder implements Pathfinder {

    private static final float FUDGING = 1.5F;

    @Override
    public String getName() {
        return "vanilla";
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
            startNode.h = computeVanillaHeuristic(startNode, targetMap.keySet());
            startNode.f = startNode.h;

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

                for (Target target : targetMap.keySet()) {
                    if (current.distanceManhattan(target) <= reachRange) {
                        target.setReached();
                    }
                }

                if (anyReached(targetMap.keySet())) {
                    break;
                }

                if (current.distanceTo(startNode) < maxRange) {
                    int neighborCount = nodeEvaluator.getNeighbors(neighbors, current);
                    for (int i = 0; i < neighborCount; i++) {
                        Node neighbor = neighbors[i];
                        float stepDist = current.distanceTo(neighbor);
                        neighbor.walkedDistance = current.walkedDistance + stepDist;
                        float tentativeG = current.g + stepDist + neighbor.costMalus;

                        if (neighbor.walkedDistance < maxRange && (!neighbor.inOpenSet() || tentativeG < neighbor.g)) {
                            neighbor.cameFrom = current;
                            neighbor.g = tentativeG;
                            neighbor.h = computeVanillaHeuristic(neighbor, targetMap.keySet()) * FUDGING;

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

    private static boolean anyReached(Set<Target> targets) {
        for (Target target : targets) {
            if (target.isReached()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the minimum Euclidean distance from the node to any target and updates
     * each Target's best fallback node, matching vanilla PathFinder#getBestH.
     */
    private static float computeVanillaHeuristic(Node node, Set<Target> targets) {
        float minH = Float.MAX_VALUE;
        for (Target target : targets) {
            float h = node.distanceTo(target);
            target.updateBest(h, node);
            minH = Math.min(h, minH);
        }
        return minH;
    }
}
