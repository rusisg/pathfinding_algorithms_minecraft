package dev.pathlab;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Target;

/** Stand-in for the game's WalkNodeEvaluator on a flat grid: caches nodes by position, sets costMalus,
 *  and (like the game) never returns closed nodes. 8-directional with a no-corner-cutting rule. */
class GridEvaluator extends NodeEvaluator {
    final boolean[][] wall; final float[][] malus; final int sx, sz;
    private final Map<Long, Node> cache = new HashMap<>();
    GridEvaluator(boolean[][] wall, float[][] malus, int sx, int sz) { this.wall = wall; this.malus = malus; this.sx = sx; this.sz = sz; }
    private Node node(int x, int z) {
        return cache.computeIfAbsent(((long) x << 32) | (z & 0xffffffffL), k -> { Node n = new Node(x, 0, z); n.costMalus = malus[x][z]; return n; });
    }
    private boolean ok(int x, int z) { return x >= 0 && z >= 0 && x < wall.length && z < wall[0].length && !wall[x][z]; }
    @Override public Node getStart() { return node(sx, sz); }
    @Override public Target getTarget(double x, double y, double z) { return new Target(node((int) x, (int) z)); }
    @Override public int getNeighbors(Node[] out, Node p) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            int x = p.x + dx, z = p.z + dz;
            if (!ok(x, z)) continue;
            if (dx != 0 && dz != 0 && (!ok(p.x + dx, p.z) || !ok(p.x, p.z + dz))) continue;
            Node c = node(x, z);
            if (!c.closed) out[n++] = c;
        }
        return n;
    }
}
