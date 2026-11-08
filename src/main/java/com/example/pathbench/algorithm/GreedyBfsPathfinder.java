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
 * 5. Greedy Best-First Search (Greedy-BFS) Pathfinding Algorithm.
 * Prioritizes nodes strictly by their heuristic distance to the target f(n) = h(n) + costMalus,
 * while tracking actual path cost g(n) for accurate telemetry comparison.
 */
public class GreedyBfsPathfinder implements Pathfinder {

    @Override
    public String getName() {
        return "greedy_bfs";
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
            startNode.h = computeHeuristic(startNode, targetMap.keySet());
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
                        if (neighbor.closed || neighbor.inOpenSet()) {
                            continue;
                        }

                        float stepDist = current.distanceTo(neighbor);
                        float nextWalked = current.walkedDistance + stepDist;
                        if (nextWalked >= maxRange) {
                            continue;
                        }

                        neighbor.walkedDistance = nextWalked;
                        neighbor.cameFrom = current;
                        neighbor.g = current.g + stepDist + neighbor.costMalus;
                        neighbor.h = computeHeuristic(neighbor, targetMap.keySet());
                        neighbor.f = neighbor.h + Math.max(0.0F, neighbor.costMalus);

                        openSet.insert(neighbor);
                    }
                }
            }

            return Pathfinder.buildFinalResult(targetMap, reachRange, capHit, expanded);
        } finally {
            nodeEvaluator.done();
        }
    }

    private static float computeHeuristic(Node node, Set<Target> targets) {
        float minH = Float.MAX_VALUE;
        for (Target target : targets) {
            float h = node.distanceTo(target);
            target.updateBest(h, node);
            if (h < minH) {
                minH = h;
            }
        }
        return minH;
    }
}
