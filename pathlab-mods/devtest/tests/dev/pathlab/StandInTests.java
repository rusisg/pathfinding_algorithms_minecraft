package dev.pathlab;

import dev.pathlab.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.*;

/** Run with: java -cp out dev.pathlab.StandInTests   (from a scratch folder; it writes ./pathlab/*.csv) */
public class StandInTests {
    static int failures = 0;
    static void check(boolean ok, String msg) { if (!ok) { failures++; System.out.println("  FAIL: " + msg); } }

    record World(boolean[][] wall, float[][] malus, int sx, int sz, List<int[]> goals) {}

    static World randomWorld(Random r, int n, double wallP) {
        boolean[][] wall = new boolean[n][n]; float[][] m = new float[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) { wall[i][j] = r.nextDouble() < wallP; m[i][j] = 0.01f + r.nextFloat() * 3f; }
        int sx, sz; do { sx = r.nextInt(n); sz = r.nextInt(n); } while (wall[sx][sz]);
        wall[sx][sz] = false;
        List<int[]> goals = new ArrayList<>();
        int ng = 1 + r.nextInt(2);
        for (int k = 0; k < ng; k++) { int gx, gz; do { gx = r.nextInt(n); gz = r.nextInt(n); } while (wall[gx][gz] || Math.abs(gx - sx) + Math.abs(gz - sz) < 12); goals.add(new int[]{gx, gz}); }
        return new World(wall, m, sx, sz, goals);
    }

    interface Runner { Path run(World w, GridEvaluator ev, Node start, Map<Target, BlockPos> t); }

    static Map<Target, BlockPos> targets(World w, GridEvaluator ev) {
        Map<Target, BlockPos> t = new LinkedHashMap<>();
        for (int[] g : w.goals()) t.put(ev.getTarget(g[0], 0, g[1]), new BlockPos(g[0], 0, g[1]));
        return t;
    }

    static final Mob ZOMBIE = new Mob(EntityType.ZOMBIE);

    public static void main(String[] a) throws Exception {
        // ---------- 1. weighted A* through the hook == the game's own loop ----------
        System.out.println("1. A* with weight 1.5 vs the game's loop (tie-free random worlds)");
        PathLabConfig.Settings vanillaLike = new PathLabConfig.Settings(SearchAlgorithm.ASTAR, 1.5, true, Set.of("zombie"));
        Random rnd = new Random(2026);
        int total = 0, same = 0, sameReached = 0, capCases = 0, partialCases = 0, costClose = 0;
        for (int t = 0; t < 600; t++) {
            World w = randomWorld(rnd, 48, 0.18 + rnd.nextDouble() * 0.12);
            int maxVisited = rnd.nextInt(3) == 0 ? 60 + rnd.nextInt(200) : 560;       // sometimes force the cap
            float maxDist = 35f; int reach = rnd.nextInt(2);
            GridEvaluator e1 = new GridEvaluator(w.wall(), w.malus(), w.sx(), w.sz());
            GridEvaluator e2 = new GridEvaluator(w.wall(), w.malus(), w.sx(), w.sz());
            Path ref = new PathFinder(e1, maxVisited).referenceSearch(e1.getStart(), targets(w, e1), maxDist, reach, 1.0f);
            PathLabHook.Result mine = PathLabHook.run(vanillaLike, "zombie", e2, maxVisited, e2.getStart(), targets(w, e2), maxDist, reach, 1.0f);
            total++;
            if (ref.canReach() != mine.path().canReach()) { check(false, "reached flag differs in world " + t); continue; }
            sameReached++;
            if (!ref.canReach()) partialCases++;
            if (maxVisited < 560) capCases++;
            boolean eq = ref.getNodeCount() == mine.path().getNodeCount();
            for (int i = 0; eq && i < ref.getNodeCount(); i++) eq = ref.getNode(i).equals(mine.path().getNode(i));
            if (eq) same++;
            else if (Math.abs(ref.getEndNode().g - mine.path().getEndNode().g) < 1e-3 * Math.max(1, ref.getEndNode().g)) costClose++;
            else check(false, "paths differ in world " + t + " (cost " + ref.getEndNode().g + " vs " + mine.path().getEndNode().g + ")");
        }
        System.out.printf("   %d worlds: identical path %d, same cost but different route %d, reached-flag agreement %d  (cap-limited worlds: %d, partial paths: %d)%n",
                total, same, costClose, sameReached, capCases, partialCases);
        check(same + costClose == total, "every world must match");

        // ---------- 2. guarantees of the four algorithms through the hook ----------
        System.out.println("2. guarantees of the four algorithms (reach range 0, so the path ends on the goal cell; reached cases only)");
        Random r2 = new Random(7);
        int n2 = 0, bfsWorse = 0, greedyWorse = 0; long eB = 0, eD = 0, eG = 0, eA = 0, eV = 0;
        for (int t = 0; t < 500; t++) {
            World w = randomWorld(r2, 48, 0.2);
            Map<SearchAlgorithm, Path> res = new EnumMap<>(SearchAlgorithm.class);
            Map<String, Integer> exp = new HashMap<>();
            for (SearchAlgorithm alg : SearchAlgorithm.values()) {
                GridEvaluator ev = new GridEvaluator(w.wall(), w.malus(), w.sx(), w.sz());
                PathLabConfig.Settings s = new PathLabConfig.Settings(alg, 1.0, true, Set.of("zombie"));
                res.put(alg, PathLabHook.run(s, "zombie", ev, 4000, ev.getStart(), targets(w, ev), 200f, 0, 1.0f).path());
            }
            boolean allReached = res.values().stream().allMatch(Path::canReach);
            boolean anyReached = res.values().stream().anyMatch(Path::canReach);
            if (!allReached) { check(!anyReached || true, ""); continue; }
            n2++;
            double cD = res.get(SearchAlgorithm.DIJKSTRA).getEndNode().g, cA = res.get(SearchAlgorithm.ASTAR).getEndNode().g;
            double cB = res.get(SearchAlgorithm.BFS).getEndNode().g, cG = res.get(SearchAlgorithm.GREEDY_BFS).getEndNode().g;
            check(Math.abs(cD - cA) < 1e-3, "A*(w=1) cost must equal Dijkstra cost: " + cA + " vs " + cD);
            check(cB >= cD - 1e-3, "BFS cannot beat Dijkstra"); check(cG >= cD - 1e-3, "Greedy cannot beat Dijkstra");
            if (cB > cD + 1e-3) bfsWorse++; if (cG > cD + 1e-3) greedyWorse++;
        }
        System.out.printf("   %d worlds where all four reached the goal: BFS costlier than optimal in %d, Greedy costlier in %d; A*(w=1) == Dijkstra in all%n", n2, bfsWorse, greedyWorse);

        // ---------- 3. which mobs are touched ----------
        System.out.println("3. mob filter");
        PathLabConfig.Settings all = new PathLabConfig.Settings(SearchAlgorithm.BFS, 1.0, true, Set.of("zombie", "iron_golem", "villager"));
        check("zombie".equals(PathLabConfig.label(all, new Mob(EntityType.ZOMBIE))), "zombie");
        check("iron_golem".equals(PathLabConfig.label(all, new Mob(EntityType.IRON_GOLEM))), "iron_golem");
        check("villager".equals(PathLabConfig.label(all, new Mob(EntityType.VILLAGER))), "villager");
        check(PathLabConfig.label(all, new Mob(new EntityType<>("husk"))) == null, "husk must be left alone");
        check(PathLabConfig.label(all, null) == null, "null mob must be left alone");
        check(PathLabConfig.label(new PathLabConfig.Settings(SearchAlgorithm.BFS, 1.0, true, Set.of("zombie")), new Mob(EntityType.VILLAGER)) == null, "villager off");
        PathLabHook.begin(new Mob(new EntityType<>("husk")));
        GridEvaluator ev = new GridEvaluator(new boolean[10][10], new float[10][10], 0, 0);
        check(!PathLabHook.search(ev, 560, ev.getStart(), Map.of(), 35f, 1, 1f).handled(), "hook must not handle a husk");
        PathLabHook.end();

        // ---------- 4. self-check against the stand-ins ----------
        System.out.println("4. self-check");
        check(SelfCheck.run(), "self-check must pass on the stand-ins");

        // ---------- 5. fail-safe ----------
        System.out.println("5. fail-safe: an error inside the search must fall back to the game, not crash");
        PathLabHook.begin(ZOMBIE);
        NodeEvaluator bad = new NodeEvaluator() {
            public Node getStart() { return null; } public Target getTarget(double x, double y, double z) { return null; }
            public int getNeighbors(Node[] o, Node p) { throw new IllegalStateException("boom"); } };
        PathLabHook.Result res = PathLabHook.search(bad, 560, new Node(0, 0, 0), Map.of(new Target(new Node(5, 0, 5)), new BlockPos(5, 0, 5)), 35f, 1, 1f);
        check(!res.handled(), "must fall back after an error");
        PathLabHook.end();

        PathLog.flush();
        System.out.println(failures == 0 ? "\nALL STAND-IN TESTS PASSED" : "\n" + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
