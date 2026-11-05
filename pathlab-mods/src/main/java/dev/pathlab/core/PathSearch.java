package dev.pathlab.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * One search loop for all four algorithms. Only two things differ between them:
 *  - the open-set structure (FIFO queue for BFS, priority queue otherwise)
 *  - the priority of a node (none / g / h / g + w*h)
 * Everything else is shared: neighbour generation, step costs, goal test, iteration cap, bookkeeping.
 *
 * The loop follows the order of the game's own PathFinder loop:
 *   ++iterations >= cap -> stop;  pop;  mark closed;  goal test;  stop if goal;
 *   skip if too far from start;  generate neighbours;  for each: walked-distance rule, cost, relax.
 *
 * Conventions (state them in the methodology chapter):
 *  - DIJKSTRA and ASTAR update a node's parent when a cheaper route is found (stale queue entries are
 *    skipped and do not count towards the cap). BFS and GREEDY_BFS keep the parent from first discovery.
 *  - Ties in priority are broken by insertion order (the game's heap breaks ties differently, so paths of
 *    equal cost can differ from the unmodified game).
 */
public final class PathSearch {
    private PathSearch() {}

    private record Entry<N>(N node, double g, double priority, long seq) {}

    public static <N> SearchResult<N> run(SearchProblem<N> p, SearchAlgorithm algo, SearchLimits lim) {
        final long t0 = System.nanoTime();

        final boolean firstDiscoveryOnly = algo == SearchAlgorithm.BFS || algo == SearchAlgorithm.GREEDY_BFS;
        final boolean needH = algo == SearchAlgorithm.GREEDY_BFS || algo == SearchAlgorithm.ASTAR || lim.allowPartial();

        final Comparator<Entry<N>> cmp = (a, b) -> {
            int c = Double.compare(a.priority, b.priority);
            return c != 0 ? c : Long.compare(a.seq, b.seq);
        };
        final Queue<Entry<N>> open = algo == SearchAlgorithm.BFS ? new ArrayDeque<>() : new PriorityQueue<>(cmp);

        final Map<N, Double> bestG = new HashMap<>();
        final Map<N, N> parent = new HashMap<>();
        final List<N> buf = new ArrayList<>(16);

        final N start = p.start();
        long seq = 0;
        int iterations = 0, expanded = 0, generated = 1, peakOpen = 1;
        boolean capHit = false;

        double startH = needH ? p.heuristic(start) : 0.0;
        N bestNode = start;
        double bestNodeH = startH;

        bestG.put(start, 0.0);
        p.onParent(start, null, 0.0);
        open.offer(new Entry<>(start, 0.0, priority(algo, 0.0, startH, lim), seq++));

        N goal = null;

        while (!open.isEmpty()) {
            if (++iterations >= lim.maxExpansions()) { capHit = true; break; }

            Entry<N> e = open.poll();
            N n = e.node();

            // stale entry: a cheaper route to n was found after this entry was queued
            if (!firstDiscoveryOnly && e.g() > bestG.get(n)) { iterations--; continue; }

            expanded++;
            p.onExpand(n);
            if (p.isGoal(n)) { goal = n; break; }
            if (!p.canExpand(n)) continue;

            buf.clear();
            p.neighbors(n, buf);
            for (N m : buf) {
                if (!p.canRelax(n, m)) continue;
                double ng = e.g() + p.stepCost(n, m);

                if (firstDiscoveryOnly) {
                    if (bestG.containsKey(m)) continue;
                } else {
                    Double old = bestG.get(m);
                    if (old != null && ng >= old) continue;
                }

                bestG.put(m, ng);
                parent.put(m, n);
                p.onParent(m, n, ng);
                double hm = needH ? p.heuristic(m) : 0.0;
                if (lim.allowPartial() && hm < bestNodeH) { bestNodeH = hm; bestNode = m; }

                open.offer(new Entry<>(m, ng, priority(algo, ng, hm, lim), seq++));
                generated++;
                if (open.size() > peakOpen) peakOpen = open.size();
            }
        }

        N end = goal != null ? goal : (lim.allowPartial() && bestNode != start ? bestNode : null);
        List<N> path = Collections.emptyList();
        double cost = Double.NaN;
        if (end != null) {
            ArrayList<N> rev = new ArrayList<>();
            for (N cur = end; cur != null; cur = parent.get(cur)) rev.add(cur);
            Collections.reverse(rev);
            path = rev;
            cost = bestG.get(end);
        }
        return new SearchResult<>(path, goal != null, cost, expanded, generated, peakOpen,
                System.nanoTime() - t0, capHit);
    }

    private static double priority(SearchAlgorithm a, double g, double h, SearchLimits lim) {
        return switch (a) {
            case BFS -> 0.0;                                   // unused (FIFO queue)
            case DIJKSTRA -> g;
            case GREEDY_BFS -> h;
            case ASTAR -> g + lim.heuristicWeight() * h;
        };
    }
}
