package dev.pathlab.core;

/**
 * @param maxExpansions   hard cap on loop iterations, same formula and meaning as the game's
 *                        maxVisitedNodes cap: the loop stops when ++iterations >= maxExpansions
 * @param heuristicWeight w in f = g + w*h (used by ASTAR only); 1.0 = standard A*, 1.5 = the game's own
 * @param allowPartial    when true, h is computed for every discovered node by ALL algorithms (as the game
 *                        does), and the core also tracks the discovered node with the lowest h
 */
public record SearchLimits(int maxExpansions, double heuristicWeight, boolean allowPartial) {
    public static SearchLimits of(int maxExpansions) {
        return new SearchLimits(maxExpansions, 1.0, true);
    }
}
