package dev.pathlab.mc;

import dev.pathlab.core.SearchProblem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;

/**
 * Adapts the game's own world model to SearchProblem so every algorithm sees what vanilla sees.
 * Each method mirrors a line of the game's PathFinder loop:
 *
 *   neighbors   -> nodeEvaluator.getNeighbors(neighbors, current)   (closed nodes are filtered by the evaluator)
 *   stepCost    -> current.distanceTo(neighbor) + neighbor.costMalus
 *   heuristic   -> getBestH(): nearest target by Euclidean distance, and target.updateBest(d, node)
 *   isGoal      -> current.distanceManhattan(target) <= reachRange, then target.setReached()
 *   canExpand   -> current.distanceTo(start) < maxDistanceFromStart
 *   canRelax    -> neighbor.walkedDistance = current.walkedDistance + distance; walked < maxDistanceFromStart
 *   onExpand    -> current.closed = true
 *   onParent    -> neighbor.cameFrom = current; neighbor.g = ...
 */
public final class VanillaNodeProblem implements SearchProblem<Node> {
    private final NodeEvaluator evaluator;
    private final Node start;
    private final Collection<Target> targets;
    private final int reachRange;
    private final float maxDistanceFromStart;
    private final Node[] scratch = new Node[32];       // the game's neighbour array has 32 slots
    private final List<Target> reached = new ArrayList<>();

    public VanillaNodeProblem(NodeEvaluator evaluator, Node start, Collection<Target> targets,
                              int reachRange, float maxDistanceFromStart) {
        this.evaluator = evaluator;
        this.start = start;
        this.targets = targets;
        this.reachRange = reachRange;
        this.maxDistanceFromStart = maxDistanceFromStart;
    }

    /** Targets that were reached (the game's "reached" set). */
    public List<Target> reached() { return reached; }

    @Override public Node start() { return start; }

    @Override public boolean isGoal(Node node) {
        boolean any = false;
        for (Target t : targets) {
            if (node.distanceManhattan(t) <= reachRange) {
                t.setReached();
                if (!reached.contains(t)) reached.add(t);
                any = true;
            }
        }
        return any;
    }

    @Override public boolean canExpand(Node node) {
        return !(node.distanceTo(start) >= maxDistanceFromStart);
    }

    @Override public void neighbors(Node node, List<Node> out) {
        int count = evaluator.getNeighbors(scratch, node);
        for (int i = 0; i < count; i++) out.add(scratch[i]);
    }

    @Override public boolean canRelax(Node from, Node to) {
        to.walkedDistance = from.walkedDistance + from.distanceTo(to);
        return to.walkedDistance < maxDistanceFromStart;
    }

    @Override public double stepCost(Node from, Node to) {
        return from.distanceTo(to) + to.costMalus;
    }

    @Override public double heuristic(Node node) {
        float best = Float.MAX_VALUE;
        for (Target t : targets) {
            float d = node.distanceTo(t);
            t.updateBest(d, node);
            if (d < best) best = d;
        }
        return best;
    }

    @Override public void onExpand(Node node) { node.closed = true; }

    @Override public void onParent(Node child, Node parent, double g) {
        child.cameFrom = parent;
        child.g = (float) g;
    }
}
