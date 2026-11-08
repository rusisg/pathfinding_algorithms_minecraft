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
 * 4. Dijkstra's Algorithm (Uniform-Cost Search).
 * Orders the priority queue strictly by cumulative path cost f(n) = g(n) with h(n) = 0,
 * accounting for Euclidean step distances and terrain/danger costMalus values.
 */
public class DijkstraPathfinder implements Pathfinder {

    @Override
    public String getName() {
        return "dijkstra";
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
            startNode.h = 0.0F;
            startNode.f = 0.0F;
            updateTargetsBest(startNode, targetMap.keySet());

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
                            neighbor.h = 0.0F;
                            updateTargetsBest(neighbor, targetMap.keySet());

                            if (neighbor.inOpenSet()) {
                                openSet.changeCost(neighbor, neighbor.g);
                            } else {
                                neighbor.f = neighbor.g;
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

    private static void updateTargetsBest(Node node, Set<Target> targets) {
        for (Target target : targets) {
            target.updateBest(node.distanceTo(target), node);
        }
    }
}
