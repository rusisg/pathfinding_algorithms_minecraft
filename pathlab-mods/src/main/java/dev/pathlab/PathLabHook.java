package dev.pathlab;

import dev.pathlab.core.PathSearch;
import dev.pathlab.core.SearchLimits;
import dev.pathlab.core.SearchResult;
import dev.pathlab.mc.VanillaNodeProblem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;

/**
 * Called by the Mixin in place of the game's private search. Everything around the search (preparing the
 * node evaluator, finding the start node, mapping targets, cleaning up) stays in the game's own code.
 * The result is turned into a Path the same way the game does it, so only the search itself differs.
 */
public final class PathLabHook {
    private PathLabHook() {}

    /** handled == false means: let the game's own search run. */
    public record Result(boolean handled, Path path) {
        static final Result NOT_HANDLED = new Result(false, null);
    }

    private static final ThreadLocal<Mob> CURRENT_MOB = new ThreadLocal<>();
    private static boolean broken = false;

    public static void begin(Mob mob) { CURRENT_MOB.set(mob); }
    public static void end() { CURRENT_MOB.remove(); }

    /** Entry point used by the Mixin. */
    public static Result search(NodeEvaluator evaluator, int maxVisitedNodes, Node start,
                                Map<Target, BlockPos> targets, float maxDistanceFromStart,
                                int reachRange, float multiplier) {
        PathLabConfig.Settings s = PathLabConfig.ACTIVE;
        if (broken || !s.enabled()) return Result.NOT_HANDLED;
        String label = PathLabConfig.label(s, CURRENT_MOB.get());
        if (label == null) return Result.NOT_HANDLED;
        try {
            return run(s, label, evaluator, maxVisitedNodes, start, targets, maxDistanceFromStart, reachRange, multiplier);
        } catch (Throwable t) {
            broken = true;     // do not crash the game; fall back to vanilla, but make the failure impossible to miss
            PathLog.say("!!!!! DISABLED after an error. The game is using its own pathfinding again. !!!!!");
            t.printStackTrace();
            return Result.NOT_HANDLED;
        }
    }

    /** The actual work; also used directly by the stand-in tests. */
    public static Result run(PathLabConfig.Settings s, String label, NodeEvaluator evaluator, int maxVisitedNodes,
                             Node start, Map<Target, BlockPos> targets, float maxDistanceFromStart,
                             int reachRange, float multiplier) {
        int cap = (int) (maxVisitedNodes * multiplier);                 // same formula as the game
        VanillaNodeProblem problem = new VanillaNodeProblem(evaluator, start, targets.keySet(), reachRange, maxDistanceFromStart);
        SearchLimits limits = new SearchLimits(cap, s.weight(), true);

        SearchResult<Node> r = PathSearch.run(problem, s.algorithm(), limits);

        // Choose and build the path exactly like the game: among reached targets the one with the fewest
        // nodes; if none was reached, the target whose best node is closest, then fewest nodes.
        Path best;
        Node bestEnd;
        if (!problem.reached().isEmpty()) {
            best = null; bestEnd = null;
            for (Target t : problem.reached()) {
                Path p = build(t.getBestNode(), targets.get(t), true);
                if (best == null || p.getNodeCount() < best.getNodeCount()) { best = p; bestEnd = t.getBestNode(); }
            }
        } else {
            best = null; bestEnd = null;
            Comparator<Path> cmp = Comparator.comparingDouble((Path p) -> p.getDistToTarget()).thenComparingInt(Path::getNodeCount);
            for (Map.Entry<Target, BlockPos> e : targets.entrySet()) {
                Path p = build(e.getKey().getBestNode(), e.getValue(), false);
                if (best == null || cmp.compare(p, best) < 0) { best = p; bestEnd = e.getKey().getBestNode(); }
            }
        }

        double cost = bestEnd != null ? bestEnd.g : Double.NaN;
        boolean reached = !problem.reached().isEmpty();
        PathLog.write(label, start.x, start.y, start.z, reached, best != null, cost,
                best != null ? best.getNodeCount() : 0, r.expanded(), r.generated(), r.peakOpen(), r.capHit(), r.nanos());
        return new Result(true, best);
    }

    /** Same as the game's private reconstructPath: follow cameFrom links back to the start. */
    private static Path build(Node end, BlockPos target, boolean reached) {
        List<Node> nodes = new ArrayList<>();
        Node cur = end;
        nodes.add(0, cur);
        while (cur.cameFrom != null) {
            cur = cur.cameFrom;
            nodes.add(0, cur);
        }
        return new Path(nodes, target, reached);
    }
}
