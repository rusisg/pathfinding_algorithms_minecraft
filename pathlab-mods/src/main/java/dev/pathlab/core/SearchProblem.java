package dev.pathlab.core;

import java.util.List;

/**
 * Everything the search needs to know about the world. The Minecraft adapter implements this on top
 * of the game's own NodeEvaluator, so every algorithm sees exactly the neighbours and costs that the
 * unmodified game sees. The default methods are hooks that let the adapter reproduce the game's own
 * bookkeeping (closed flags, walked-distance limit, parent links) without the core knowing about it.
 *
 * N needs value-based equals/hashCode (the game's Node has them).
 */
public interface SearchProblem<N> {
    N start();

    /** Called once for every node taken from the open set (after onExpand). */
    boolean isGoal(N node);

    /** Append the walkable neighbours of {@code node} to {@code out}. */
    void neighbors(N node, List<N> out);

    /** Cost of the move from {@code from} to the adjacent {@code to}, terrain penalty included. Must be >= 0. */
    double stepCost(N from, N to);

    /** Estimated remaining cost. Called for the start node and for every node that gets a parent. */
    double heuristic(N node);

    /** The game does not expand nodes that are too far from the start. */
    default boolean canExpand(N node) { return true; }

    /** Extra rule checked before a move is considered (the game's walked-distance limit). */
    default boolean canRelax(N from, N to) { return true; }

    /** Called when a node is taken from the open set (the game marks it "closed"). */
    default void onExpand(N node) { }

    /** Called whenever a node gets a (new) parent and cost. */
    default void onParent(N child, N parent, double g) { }
}
