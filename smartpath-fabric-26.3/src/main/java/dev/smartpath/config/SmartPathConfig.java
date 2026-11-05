package dev.smartpath.config;

import java.util.concurrent.atomic.LongAdder;

/**
 * Runtime configuration and lightweight telemetry counters for SmartPath.
 */
public final class SmartPathConfig {
    private static final SmartPathConfig INSTANCE = new SmartPathConfig();

    /** Master switch to toggle between SmartPath (Theta*) and vanilla A*. */
    public volatile boolean enabled = true;

    /** Enables Lazy Theta* parent relaxation and collinear/line-of-sight path smoothing. */
    public volatile boolean enableThetaStarSmoothing = true;

    /** Prevents indoor villager workstations/tables from snapping to roof blocks and ranks approach sides. */
    public volatile boolean enableWorkstationTargetFix = true;

    /** Remembers coordinates where a mob recently stalled and applies a temporary cost malus on replanning. */
    public volatile boolean enableStuckPenaltyMemory = true;

    /**
     * Base octile heuristic weight (vanilla uses an uncalibrated 1.5f Euclidean fudge factor).
     * 1.08f is near-optimal while still expanding ~35% fewer nodes than uninformed A*.
     */
    public volatile float heuristicWeight = 1.08F;

    /** Maximum line-of-sight raycast span (in blocks) for Theta* parent relaxation. */
    public volatile int maxLineOfSightSpan = 12;

    /** Multiplier for node budget when a villager is searching for a POI (workstation, bed, bell). */
    public volatile float villagerNodeBudgetMultiplier = 1.5F;

    // Telemetry metrics
    public final LongAdder totalSearches = new LongAdder();
    public final LongAdder totalVisitedNodes = new LongAdder();

    private SmartPathConfig() {}

    public static SmartPathConfig get() {
        return INSTANCE;
    }

    public void recordSearch(int visitedNodes) {
        totalSearches.increment();
        totalVisitedNodes.add(visitedNodes);
    }

    public long getAverageVisitedNodes() {
        long searches = totalSearches.sum();
        return searches == 0L ? 0L : totalVisitedNodes.sum() / searches;
    }
}
