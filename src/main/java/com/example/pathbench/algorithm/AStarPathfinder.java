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
 * 2. Standard A* (A-Star) Pathfinding Algorithm.
 * Uses an admissible, uninflated Euclidean heuristic (weight = 1.0F, f(n) = g(n) + h(n))
 * to guarantee shortest-cost paths over the WalkNodeEvaluator graph.
 */
public class AStarPathfinder implements Pathfinder {

    @Override
    public String getName() {
        return "astar";
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
            startNode.h = computeAdmissibleHeuristic(startNode, targetMap.keySet());
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
                        if (neighbor.closed) {
                            continue;
                        }

                        float stepDist = current.distanceTo(neighbor);
                        float nextWalked = current.walkedDistance + stepDist;
                        float tentativeG = current.g + stepDist + neighbor.costMalus;

                        if (nextWalked < maxRange && (!neighbor.inOpenSet() || tentativeG < neighbor.g)) {
                            neighbor.walkedDistance = nextWalked;
                            neighbor.cameFrom = current;
                            neighbor.g = tentativeG;
                            neighbor.h = computeAdmissibleHeuristic(neighbor, targetMap.keySet());

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

    private static float computeAdmissibleHeuristic(Node node, Set<Target> targets) {
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
