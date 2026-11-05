package net.minecraft.world.level.pathfinder;
import net.minecraft.core.BlockPos;
public class Node {
    public final int x, y, z; private final int hash;
    public int heapIdx = -1; public float g, h, f; public Node cameFrom; public boolean closed;
    public float walkedDistance; public float costMalus;
    public Node(int x, int y, int z) { this.x = x; this.y = y; this.z = z; this.hash = (y & 255) | ((x & 32767) << 8) | ((z & 32767) << 24); }
    public float distanceTo(Node n) { float a = n.x - x, b = n.y - y, c = n.z - z; return (float) Math.sqrt(a*a + b*b + c*c); }
    public float distanceManhattan(Node n) { return Math.abs(n.x - x) + Math.abs(n.y - y) + Math.abs(n.z - z); }
    public float distanceManhattan(BlockPos p) { return Math.abs(p.getX() - x) + Math.abs(p.getY() - y) + Math.abs(p.getZ() - z); }
    public boolean inOpenSet() { return heapIdx >= 0; }
    @Override public boolean equals(Object o) { return o instanceof Node n && hash == n.hash && x == n.x && y == n.y && z == n.z; }
    @Override public int hashCode() { return hash; }
}
