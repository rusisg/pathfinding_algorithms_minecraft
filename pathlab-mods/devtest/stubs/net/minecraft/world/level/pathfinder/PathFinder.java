package net.minecraft.world.level.pathfinder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;

/** Stand-in for the game's PathFinder. The private method is the game's search loop as documented in
 *  the 2026 ITB paper (g = parent.g + distance + costMalus, h = 1.5 * nearest Euclidean distance, closed flags,
 *  walked-distance limit, iteration cap, best-h node for partial paths). Used as the reference in tests. */
public class PathFinder {
    private final NodeEvaluator nodeEvaluator; private final int maxVisitedNodes;
    private final Node[] neighbors = new Node[32];
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
    public PathFinder(NodeEvaluator e, int max) { this.nodeEvaluator = e; this.maxVisitedNodes = max; }

    public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float maxRange, int accuracy, float mult) {
        nodeEvaluator.prepare(region, mob);
        Node start = nodeEvaluator.getStart();
        if (start == null) return null;
        Map<Target, BlockPos> map = new HashMap<>();
        for (BlockPos p : targets) map.put(nodeEvaluator.getTarget(p.getX(), p.getY(), p.getZ()), p);
        Path path = findPath(start, map, maxRange, accuracy, mult);
        nodeEvaluator.done();
        return path;
    }

    private float getBestH(Node node, Set<Target> targets) {
        float best = Float.MAX_VALUE;
        for (Target t : targets) { float d = node.distanceTo(t); t.updateBest(d, node); best = Math.min(d, best); }
        return best;
    }
    private void insert(Node n) { n.heapIdx = 0; open.add(n); }
    private Node pop() { Node n = open.poll(); n.heapIdx = -1; return n; }
    private void changeCost(Node n, float f) { open.remove(n); n.f = f; open.add(n); }

    private Path findPath(Node start, Map<Target, BlockPos> targets, float maxDistanceFromStart, int reachRange, float mult) {
        start.g = 0; start.h = getBestH(start, targets.keySet()); start.f = start.h;
        open.clear(); insert(start);
        int count = 0; Set<Target> reached = new HashSet<>();
        int maxAdj = (int) (maxVisitedNodes * mult);
        while (!open.isEmpty()) {
            if (++count >= maxAdj) break;
            Node cur = pop(); cur.closed = true;
            for (Target t : targets.keySet()) if (cur.distanceManhattan(t) <= reachRange) { t.setReached(); reached.add(t); }
            if (!reached.isEmpty()) break;
            if (!(cur.distanceTo(start) >= maxDistanceFromStart)) {
                int n = nodeEvaluator.getNeighbors(neighbors, cur);
                for (int i = 0; i < n; i++) {
                    Node nb = neighbors[i];
                    float d = cur.distanceTo(nb);
                    nb.walkedDistance = cur.walkedDistance + d;
                    float tg = cur.g + d + nb.costMalus;
                    if (nb.walkedDistance < maxDistanceFromStart && (!nb.inOpenSet() || tg < nb.g)) {
                        nb.cameFrom = cur; nb.g = tg; nb.h = getBestH(nb, targets.keySet()) * 1.5F;
                        if (nb.inOpenSet()) changeCost(nb, nb.g + nb.h); else { nb.f = nb.g + nb.h; insert(nb); }
                    }
                }
            }
        }
        Path best = null;
        if (!reached.isEmpty()) {
            for (Target t : reached) { Path p = reconstruct(t.getBestNode(), targets.get(t), true); if (best == null || p.getNodeCount() < best.getNodeCount()) best = p; }
        } else {
            for (Map.Entry<Target, BlockPos> e : targets.entrySet()) {
                Path p = reconstruct(e.getKey().getBestNode(), e.getValue(), false);
                if (best == null || p.getDistToTarget() < best.getDistToTarget()
                        || (p.getDistToTarget() == best.getDistToTarget() && p.getNodeCount() < best.getNodeCount())) best = p;
            }
        }
        return best;
    }
    private Path reconstruct(Node end, BlockPos target, boolean reached) {
        List<Node> l = new ArrayList<>(); Node c = end; l.add(0, c);
        while (c.cameFrom != null) { c = c.cameFrom; l.add(0, c); }
        return new Path(l, target, reached);
    }
    /** For tests: run the reference loop directly. */
    public Path referenceSearch(Node start, Map<Target, BlockPos> targets, float maxDist, int reach, float mult) { return findPath(start, targets, maxDist, reach, mult); }
}
