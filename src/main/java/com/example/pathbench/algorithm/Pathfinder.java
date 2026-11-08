package com.example.pathbench.algorithm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modular Pathfinder interface implemented by all 6 pathfinding algorithms:
 * 1. Vanilla Minecraft Pathfinding (vanilla)
 * 2. Standard A* (astar)
 * 3. Breadth-First Search (bfs)
 * 4. Dijkstra's Algorithm (dijkstra)
 * 5. Greedy Best-First Search (greedy_bfs)
 * 6. Custom Voxel Algorithm (custom_voxel)
 */
public interface Pathfinder {

    /**
     * @return The canonical identifier used by /pathfind mode <algorithm_name> and CSV logs.
     */
    String getName();

    /**
     * Computes a path from the mob's current position to one of the target POI positions
     * and returns structured telemetry for benchmarking.
     *
     * @param region           The cached chunk navigation region around the mob.
     * @param mob              The entity (Villager) performing the pathfinding.
     * @param targets          Set of candidate target block positions (e.g., job site POI blocks).
     * @param maxRange         Maximum pathfinding range (follow range).
     * @param reachRange       Acceptable Manhattan distance to consider a target reached.
     * @param maxVisitedNodes  Computation cap (maximum number of nodes expanded before aborting).
     * @param nodeEvaluator    The mob's WalkNodeEvaluator for voxel collision and malus checks.
     * @return SearchResult containing the Minecraft Path and profiling metrics.
     */
    SearchResult findPath(
            PathNavigationRegion region,
            Mob mob,
            Set<BlockPos> targets,
            float maxRange,
            int reachRange,
            int maxVisitedNodes,
            NodeEvaluator nodeEvaluator
    );

    /**
     * Encapsulates the constructed Minecraft Path and exact search metrics for CSV logging.
     */
    record SearchResult(
            @Nullable Path path,
            @Nullable BlockPos targetPos,
            boolean reached,
            boolean partial,
            boolean capHit,
            int expanded,
            float cost
    ) {
        public static SearchResult empty(BlockPos fallbackTarget) {
            return new SearchResult(null, fallbackTarget, false, false, false, 0, 0.0F);
        }
    }

    /**
     * Shared helper to map target BlockPos instances to NodeEvaluator Target nodes.
     */
    static Map<Target, BlockPos> createTargetsMap(NodeEvaluator evaluator, Set<BlockPos> targets) {
        Map<Target, BlockPos> map = new LinkedHashMap<>(targets.size());
        for (BlockPos pos : targets) {
            map.put(evaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()), pos);
        }
        return map;
    }

    /**
     * Reconstructs a vanilla Minecraft {@link Path} by backtracking parent pointers (`cameFrom`)
     * from the terminal or best partial {@link Node} back to the start node.
     */
    static Path reconstructPath(Node endNode, BlockPos targetPos, boolean reached) {
        List<Node> nodes = new ArrayList<>();
        Node curr = endNode;
        nodes.add(curr);
        while (curr.cameFrom != null) {
            curr = curr.cameFrom;
            nodes.add(curr);
        }
        Collections.reverse(nodes);
        return new Path(nodes, targetPos, reached);
    }

    /**
     * Selects the best candidate Target after search termination (either reached or best partial fallback)
     * and builds a {@link SearchResult}.
     */
    static SearchResult buildFinalResult(
            Map<Target, BlockPos> targetMap,
            int reachRange,
            boolean capHit,
            int expandedCount
    ) {
        List<Target> reachedTargets = new ArrayList<>();
        for (Target target : targetMap.keySet()) {
            if (target.isReached()) {
                reachedTargets.add(target);
            }
        }

        if (!reachedTargets.isEmpty()) {
            // Choose the reached target with minimum path cost (g)
            Target bestReached = Collections.min(reachedTargets,
                    Comparator.comparingDouble(t -> t.getBestNode() != null ? t.getBestNode().g : Double.MAX_VALUE));
            Node endNode = bestReached.getBestNode();
            BlockPos targetPos = targetMap.get(bestReached);
            Path path = reconstructPath(endNode, targetPos, true);
            return new SearchResult(path, targetPos, true, false, capHit, expandedCount, endNode.g);
        }

        // Otherwise, construct the best partial path (closest heuristic approach to any target)
        Target bestPartial = null;
        float bestHeuristic = Float.MAX_VALUE;
        for (Target target : targetMap.keySet()) {
            Node candidate = target.getBestNode();
            if (candidate != null) {
                float dist = candidate.distanceManhattan(target);
                if (dist < bestHeuristic) {
                    bestHeuristic = dist;
                    bestPartial = target;
                }
            }
        }

        if (bestPartial != null && bestPartial.getBestNode() != null) {
            Node endNode = bestPartial.getBestNode();
            BlockPos targetPos = targetMap.get(bestPartial);
            boolean withinReach = endNode.distanceManhattan(bestPartial) <= reachRange;
            Path path = reconstructPath(endNode, targetPos, withinReach);
            return new SearchResult(
                    path,
                    targetPos,
                    withinReach,
                    !withinReach && path.getNodeCount() > 1,
                    capHit,
                    expandedCount,
                    endNode.g
            );
        }

        BlockPos fallback = targetMap.values().stream().findFirst().orElse(BlockPos.ZERO);
        return SearchResult.empty(fallback);
    }
}
