package dev.pathlab.core;

import java.util.List;

/**
 * @param path       start..end of the path the core found (the goal, or the node with the lowest h)
 * @param reached    true if a goal node was reached
 * @param cost       total step cost of {@code path} (NaN if empty)
 * @param expanded   nodes taken from the open set and expanded
 * @param generated  nodes inserted into the open set (including re-insertions after a cheaper route)
 * @param peakOpen   largest size of the open set (memory proxy)
 * @param nanos      wall time of the search, including the game's neighbour generation
 * @param capHit     true if the search stopped because the iteration cap was reached
 */
public record SearchResult<N>(List<N> path, boolean reached, double cost,
                              int expanded, int generated, int peakOpen,
                              long nanos, boolean capHit) {
    public boolean hasPath() { return !path.isEmpty(); }
}
