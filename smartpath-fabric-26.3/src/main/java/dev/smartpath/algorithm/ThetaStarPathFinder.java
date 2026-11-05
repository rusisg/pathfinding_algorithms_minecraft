package dev.smartpath.algorithm;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dev.smartpath.config.SmartPathConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Target;
import org.jspecify.annotations.Nullable;

/**
 * Drop-in replacement for vanilla Minecraft's {@link PathFinder} implementing an
 * Any-Angle Lazy Theta* + Adaptive Octile A* hybrid algorithm.
 *
 * <h3>Why this outperforms Vanilla's {@code PathFinder}:</h3>
 * <ol>
 *   <li><b>3D Octile Heuristic + Cross-Product Tie-Breaking:</b> Vanilla uses Euclidean distance
 *       multiplied by an inadmissible {@code FUDGING = 1.5F} constant, which greedily pulls mobs into
 *       dead-end corners and U-shaped walls around villager workstations. We use an exact 8-directional
 *       Octile metric with a tiny cross-product tie-breaker and adaptive weighting that relaxes to 1.0
 *       when navigating complex indoor rooms.</li>
 *   <li><b>True Step-Cost Accounting:</b> Vanilla uses {@code distanceManhattan} for adjacent step costs,
 *       charging {@code 2.0} for a flat diagonal step instead of {@code sqrt(2) ≈ 1.4142}, causing unnatural
 *       stair-stepping. We compute exact octile step costs plus vertical climb/drop and hazard maluses.</li>
 *   <li><b>Lazy Theta* Any-Angle Parent Relaxation:</b> When expanding a node, if its parent has a clear,
 *       collision-free 3D walkable line-of-sight to the neighbor on the same floor, the neighbor connects
 *       directly to the grandparent—eliminating grid zig-zags and workstation corner snagging.</li>
 *   <li><b>Post-Search String-Pulling:</b> Prunes redundant collinear grid nodes while preserving doorway
 *       pivots and elevation transitions so mobs walk smoothly to tables, beds, and job sites.</li>
 * </ol>
 */
public class ThetaStarPathFinder extends PathFinder {
    private static final float SQRT_2 = 1.4142135F;
    private static final float OCTILE_DIAG_DIFF = SQRT_2 - 2.0F; // sqrt(2) - 2
    private static final float VERTICAL_STEP_UP_COST = 1.35F;
    private static final float VERTICAL_STEP_DOWN_COST = 1.15F;
    private static final float TIE_BREAKER_SCALE = 0.0015F;

    private final Node[] neighbors = new Node[32];
    private final int baseMaxVisitedNodes;
    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();

    public ThetaStarPathFinder(final NodeEvaluator nodeEvaluator, final int maxVisitedNodes) {
        super(nodeEvaluator, maxVisitedNodes);
        this.nodeEvaluator = nodeEvaluator;
        this.baseMaxVisitedNodes = maxVisitedNodes;
    }

    @Override
    public @Nullable Path findPath(
        final PathNavigationRegion region,
        final Mob mob,
        final Set<BlockPos> targets,
        final float maxRange,
        final int accuracy,
        final float searchDepthMultiplier
    ) {
        SmartPathConfig config = SmartPathConfig.get();
        if (!config.enabled) {
            return super.findPath(region, mob, targets, maxRange, accuracy, searchDepthMultiplier);
        }

        this.openSet.clear();
        this.nodeEvaluator.prepare(region, mob);
        Node startNode = this.nodeEvaluator.getStart();
        if (startNode == null) {
            this.nodeEvaluator.done();
            return null;
        }

        Map<Target, BlockPos> targetMap = targets.stream()
            .collect(Collectors.toMap(
                pos -> this.nodeEvaluator.getTarget(pos.getX(), pos.getY(), pos.getZ()),
                Function.identity()
            ));

        Path result = this.findPathThetaStar(
            region,
            mob,
            startNode,
            targetMap,
            maxRange,
            accuracy,
            searchDepthMultiplier
        );
        this.nodeEvaluator.done();
        return result;
    }

    private @Nullable Path findPathThetaStar(
        final PathNavigationRegion region,
        final Mob mob,
        final Node startNode,
        final Map<Target, BlockPos> targetMap,
        final float maxRange,
        final int accuracy,
        final float searchDepthMultiplier
    ) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("smartpath_thetastar");

        try {
            SmartPathConfig config = SmartPathConfig.get();
            Set<Target> targets = targetMap.keySet();

            float effectiveMultiplier = searchDepthMultiplier;
            if (mob instanceof Villager) {
                effectiveMultiplier *= config.villagerNodeBudgetMultiplier;
            }
            int maxNodes = Math.max(16, (int) (this.baseMaxVisitedNodes * effectiveMultiplier));

            startNode.g = 0.0F;
            startNode.h = computeHeuristic(startNode, startNode, targets, config.heuristicWeight, 0, maxNodes);
            startNode.f = startNode.h;

            this.openSet.clear();
            this.openSet.insert(startNode);

            Set<Target> reachedTargets = Sets.newHashSetWithExpectedSize(targets.size());
            int visitedCount = 0;

            while (!this.openSet.isEmpty() && visitedCount < maxNodes) {
                visitedCount++;
                Node current = this.openSet.pop();
                current.closed = true;

                // Check if current node satisfies any target within the required accuracy (e.g. accuracy=1 for workstations)
                for (Target target : targets) {
                    if (current.distanceManhattan(target) <= accuracy) {
                        target.setReached();
                        reachedTargets.add(target);
                    }
                }

                if (!reachedTargets.isEmpty()) {
                    break;
                }

                if (current.distanceTo(startNode) >= maxRange) {
                    continue;
                }

                int neighborCount = this.nodeEvaluator.getNeighbors(this.neighbors, current);
                for (int i = 0; i < neighborCount; i++) {
                    Node neighbor = this.neighbors[i];
                    if (neighbor == null || neighbor.closed) {
                        continue;
                    }

                    // Lazy Theta* parent selection: check if current.cameFrom has direct walkable Line-of-Sight to neighbor
                    Node candidateParent = current;
                    float stepDistance = computeStepCost(current, neighbor);

                    if (config.enableThetaStarSmoothing
                        && current.cameFrom != null
                        && canRelaxToGrandparent(region, mob, current.cameFrom, current, neighbor, config.maxLineOfSightSpan)) {
                        candidateParent = current.cameFrom;
                        stepDistance = computeAnyAngleDistance(candidateParent, neighbor);
                    }

                    float tentativeG = candidateParent.g + stepDistance + neighbor.costMalus;

                    if (neighbor.walkedDistance < maxRange && (!neighbor.inOpenSet() || tentativeG < neighbor.g)) {
                        neighbor.cameFrom = candidateParent;
                        neighbor.g = tentativeG;
                        neighbor.walkedDistance = candidateParent.walkedDistance + stepDistance;
                        neighbor.h = computeHeuristic(
                            neighbor,
                            startNode,
                            targets,
                            config.heuristicWeight,
                            visitedCount,
                            maxNodes
                        );

                        if (neighbor.inOpenSet()) {
                            this.openSet.changeCost(neighbor, neighbor.g + neighbor.h);
                        } else {
                            neighbor.f = neighbor.g + neighbor.h;
                            this.openSet.insert(neighbor);
                        }
                    }
                }
            }

            config.recordSearch(visitedCount);

            // Reconstruct the best path (preferring reached targets, otherwise closest fallback)
            Comparator<Path> pathComparator = reachedTargets.isEmpty()
                ? Comparator.comparingInt(Path::getNodeCount)
                : Comparator.comparingDouble(this::evaluatePathTotalCost).thenComparingInt(Path::getNodeCount);

            Set<Target> candidateTargets = reachedTargets.isEmpty() ? targets : reachedTargets;
            return candidateTargets.stream()
                .filter(target -> target.getBestNode() != null)
                .map(target -> reconstructAndSmoothPath(
                    region,
                    mob,
                    target.getBestNode(),
                    targetMap.get(target),
                    !reachedTargets.isEmpty(),
                    config.enableThetaStarSmoothing
                ))
                .min(pathComparator)
                .orElse(null);
        } finally {
            profiler.pop();
        }
    }

    /**
     * Computes the 3D Octile heuristic with cross-product vector tie-breaking and adaptive search relaxation.
     * As visitedCount grows (indicating an indoor wall or maze), the heuristic weight smoothly relaxes
     * toward 1.0 so the search escapes local pockets around villager workstations.
     */
    private float computeHeuristic(
        final Node node,
        final Node startNode,
        final Set<Target> targets,
        final float baseWeight,
        final int visitedCount,
        final int maxNodes
    ) {
        // Adaptive weight: starts at baseWeight (e.g. 1.08) and relaxes to 1.0 if obstacles stall progress
        float progressRatio = Math.min(1.0F, (float) visitedCount / (float) Math.max(1, maxNodes / 2));
        float adaptiveWeight = 1.0F + (baseWeight - 1.0F) * (1.0F - 0.75F * progressRatio);

        float bestH = Float.MAX_VALUE;
        for (Target target : targets) {
            int dx = Math.abs(node.x - target.x);
            int dy = Math.abs(node.y - target.y);
            int dz = Math.abs(node.z - target.z);

            // 2D Octile distance + weighted vertical delta
            float octileXZ = (dx + dz) + OCTILE_DIAG_DIFF * Math.min(dx, dz);
            float rawH = octileXZ + dy * 1.25F;

            // Cross-product tie-breaker in the horizontal plane to break symmetry along straight corridors
            int dxStart = target.x - startNode.x;
            int dzStart = target.z - startNode.z;
            int dxCurr = node.x - target.x;
            int dzCurr = node.z - target.z;
            float cross = Math.abs(dxCurr * dzStart - dxStart * dzCurr);
            float tieBreak = Math.min(0.05F, cross * TIE_BREAKER_SCALE);

            float h = rawH * adaptiveWeight + tieBreak;
            target.updateBest(rawH, node);
            if (h < bestH) {
                bestH = h;
            }
        }
        return bestH;
    }

    /**
     * Exact octile step cost between adjacent nodes (replaces vanilla's Manhattan step cost which over-penalized diagonals).
     */
    @Override
    protected float distance(final Node from, final Node to) {
        return computeStepCost(from, to);
    }

    private static float computeStepCost(final Node from, final Node to) {
        int dx = Math.abs(from.x - to.x);
        int dy = to.y - from.y;
        int dz = Math.abs(from.z - to.z);

        float horizontal = (dx == 1 && dz == 1) ? SQRT_2 : (float) (dx + dz);
        float vertical = 0.0F;
        if (dy > 0) {
            vertical = dy * VERTICAL_STEP_UP_COST;
        } else if (dy < 0) {
            vertical = (-dy) * VERTICAL_STEP_DOWN_COST;
        }
        return horizontal + vertical;
    }

    private static float computeAnyAngleDistance(final Node from, final Node to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Checks if {@code grandparent} has a safe, unobstructed, flat walkable line-of-sight to {@code neighbor}
     * using a 2D supercover Bresenham voxel traversal across the mob's footprint.
     */
    private boolean canRelaxToGrandparent(
        final PathNavigationRegion region,
        final Mob mob,
        final Node grandparent,
        final Node current,
        final Node neighbor,
        final int maxSpan
    ) {
        // Only relax across flat floor segments without special door/ladder/water node transitions
        if (grandparent.y != current.y || current.y != neighbor.y) {
            return false;
        }
        if (neighbor.type != PathType.WALKABLE || current.type != PathType.WALKABLE || grandparent.type != PathType.WALKABLE) {
            return false;
        }
        if (neighbor.costMalus > 0.0F || current.costMalus > 0.0F) {
            return false;
        }

        int dx = Math.abs(neighbor.x - grandparent.x);
        int dz = Math.abs(neighbor.z - grandparent.z);
        if (dx > maxSpan || dz > maxSpan || (dx <= 1 && dz <= 1)) {
            return false;
        }

        return hasSupercoverWalkableLineOfSight(region, mob, grandparent.x, grandparent.y, grandparent.z, neighbor.x, neighbor.z);
    }

    /**
     * Supercover Bresenham line-of-sight check that inspects every grid cell touched by the line segment
     * between (x0, y, z0) and (x1, y, z1), ensuring solid floor support and clear headroom for the mob.
     */
    private boolean hasSupercoverWalkableLineOfSight(
        final PathNavigationRegion region,
        final Mob mob,
        final int x0,
        final int y,
        final int z0,
        final int x1,
        final int z1
    ) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int x = x0;
        int z = z0;
        int n = 1 + dx + dz;
        int xInc = (x1 > x0) ? 1 : -1;
        int zInc = (z1 > z0) ? 1 : -1;
        int error = dx - dz;
        dx *= 2;
        dz *= 2;

        int requiredHeight = Math.max(2, (int) Math.ceil(mob.getBbHeight()));
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (; n > 0; --n) {
            if (!isCellCleanlyWalkable(region, checkPos, x, y, z, requiredHeight)) {
                return false;
            }

            if (error > 0) {
                // Also verify the orthogonal corner cell to prevent clipping diagonal block corners
                if (!isCellCleanlyWalkable(region, checkPos, x + xInc, y, z, requiredHeight)) {
                    return false;
                }
                x += xInc;
                error -= dz;
            } else if (error < 0) {
                if (!isCellCleanlyWalkable(region, checkPos, x, y, z + zInc, requiredHeight)) {
                    return false;
                }
                z += zInc;
                error += dx;
            } else {
                // Exact diagonal crossing: both adjacent orthogonal cells must be clear
                if (!isCellCleanlyWalkable(region, checkPos, x + xInc, y, z, requiredHeight)
                    || !isCellCleanlyWalkable(region, checkPos, x, y, z + zInc, requiredHeight)) {
                    return false;
                }
                x += xInc;
                z += zInc;
                error = error - dz + dx;
                --n;
            }
        }
        return true;
    }

    private static boolean isCellCleanlyWalkable(
        final PathNavigationRegion region,
        final BlockPos.MutableBlockPos pos,
        final int x,
        final int y,
        final int z,
        final int requiredHeight
    ) {
        // 1. Must have a full, non-air supporting block underneath
        pos.set(x, y - 1, z);
        BlockState below = region.getBlockState(pos);
        if (below.isAir() || below.isPathfindable(PathComputationType.LAND)) {
            return false;
        }

        // 2. Headroom column must be completely pathfindable on land and free of fluids/doors
        for (int h = 0; h < requiredHeight; h++) {
            pos.set(x, y + h, z);
            BlockState state = region.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || !state.isPathfindable(PathComputationType.LAND)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reconstructs the {@link Path} from {@code endNode} and performs a fast string-pulling pass
     * to prune collinear intermediate waypoints while keeping doorway and step-up/down pivots intact.
     */
    private Path reconstructAndSmoothPath(
        final PathNavigationRegion region,
        final Mob mob,
        final Node endNode,
        final BlockPos targetPos,
        final boolean reached,
        final boolean enableSmoothing
    ) {
        List<Node> rawNodes = Lists.newArrayList();
        Node cursor = endNode;
        rawNodes.add(cursor);

        while (cursor.cameFrom != null) {
            cursor = cursor.cameFrom;
            rawNodes.addFirst(cursor);
        }

        if (!enableSmoothing || rawNodes.size() <= 2) {
            return new Path(rawNodes, targetPos, reached);
        }

        List<Node> smoothed = Lists.newArrayListWithCapacity(rawNodes.size());
        smoothed.add(rawNodes.getFirst());

        for (int i = 1; i < rawNodes.size() - 1; i++) {
            Node prev = smoothed.getLast();
            Node curr = rawNodes.get(i);
            Node next = rawNodes.get(i + 1);

            // Never prune elevation changes, doors, or non-walkable transition nodes
            if (prev.y != curr.y || curr.y != next.y || curr.type != PathType.WALKABLE) {
                smoothed.add(curr);
                continue;
            }

            // Prune strictly collinear horizontal steps to reduce waypoint jitter
            int dx1 = curr.x - prev.x;
            int dz1 = curr.z - prev.z;
            int dx2 = next.x - curr.x;
            int dz2 = next.z - curr.z;
            if (dx1 * dz2 == dz1 * dx2 && (dx1 * dx2 >= 0) && (dz1 * dz2 >= 0)) {
                int span = Math.max(Math.abs(next.x - prev.x), Math.abs(next.z - prev.z));
                if (span <= 6 && hasSupercoverWalkableLineOfSight(region, mob, prev.x, prev.y, prev.z, next.x, next.z)) {
                    continue;
                }
            }

            smoothed.add(curr);
        }

        smoothed.add(rawNodes.getLast());
        return new Path(smoothed, targetPos, reached);
    }

    private double evaluatePathTotalCost(final Path path) {
        if (path.getNodeCount() == 0) {
            return Double.MAX_VALUE;
        }
        Node last = path.getNode(path.getNodeCount() - 1);
        return last.g + last.h;
    }
}
