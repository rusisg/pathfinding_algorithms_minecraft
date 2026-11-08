package com.example.pathbench.algorithm;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 3. Breadth-First Search (BFS) Pathfinding Algorithm.
 * Explores the walkable voxel graph layer-by-layer using a FIFO queue, finding paths
 * with the minimum number of block hops while tracking cumulative edge cost for profiling.
 */
public class BfsPathfinder implements Pathfinder {

    @Override
    public String getName() {
        return "bfs";
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

        Queue<Node> fifoQueue = new ArrayDeque<>();
        LongSet visited = new LongOpenHashSet();
        Node[] neighbors = new Node[32];

        nodeEvaluator.prepare(region, mob);
        try {
            Node startNode = nodeEvaluator.getStart();
            if (startNode == null) {
                return SearchResult.empty(targets.iterator().next());
            }

            Map<Target, BlockPos> targetMap = Pathfinder.createTargetsMap(nodeEvaluator, targets);

            startNode.g = 0.0F;
            startNode.h = updateTargetsBest(startNode, targetMap.keySet());
            startNode.f = startNode.g;

            fifoQueue.add(startNode);
            visited.add(BlockPos.asLong(startNode.x, startNode.y, startNode.z));

            int expanded = 0;
            boolean capHit = false;

            while (!fifoQueue.isEmpty()) {
                if (++expanded >= maxVisitedNodes) {
                    capHit = true;
                    break;
                }

                Node current = fifoQueue.poll();
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
                        long packedPos = BlockPos.asLong(neighbor.x, neighbor.y, neighbor.z);

                        if (neighbor.closed || visited.contains(packedPos)) {
                            continue;
                        }

                        float stepDist = current.distanceTo(neighbor);
                        float nextWalked = current.walkedDistance + stepDist;
                        if (nextWalked >= maxRange) {
                            continue;
                        }

                        visited.add(packedPos);
                        neighbor.cameFrom = current;
                        neighbor.walkedDistance = nextWalked;
                        neighbor.g = current.g + stepDist + Math.max(0.0F, neighbor.costMalus);
                        neighbor.h = updateTargetsBest(neighbor, targetMap.keySet());
                        neighbor.f = neighbor.g;

                        fifoQueue.add(neighbor);
                    }
                }
            }

            return Pathfinder.buildFinalResult(targetMap, reachRange, capHit, expanded);
        } finally {
            nodeEvaluator.done();
        }
    }

    private static float updateTargetsBest(Node node, Set<Target> targets) {
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
