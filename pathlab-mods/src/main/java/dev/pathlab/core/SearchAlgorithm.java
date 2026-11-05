package dev.pathlab.core;

/** The four algorithms compared in the study. Only the way the next node is chosen differs. */
public enum SearchAlgorithm {
    /** FIFO queue. Ignores costs when choosing what to expand. Parent fixed at first discovery. */
    BFS,
    /** Priority = g (cost so far). Optimal for non-negative costs. */
    DIJKSTRA,
    /** Priority = h (heuristic only). Fast, no optimality guarantee. Parent fixed at first discovery. */
    GREEDY_BFS,
    /** Priority = g + w*h. w = 1.0 is standard A*. (The unmodified game uses w = 1.5.) */
    ASTAR
}
